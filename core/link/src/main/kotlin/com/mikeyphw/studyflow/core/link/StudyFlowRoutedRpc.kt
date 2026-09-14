package com.mikeyphw.studyflow.core.link

import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A production transport receipt for a domain RPC accepted by the remote SFLP runtime. */
@Serializable
data class StudyFlowRoutedRpcAccepted(
    val requestMessageId: StudyFlowMessageId,
    val requestName: String,
    val receivingDeviceId: StudyFlowDeviceId,
    val acceptedAtEpochMillis: Long,
)

data class StudyFlowRoutedRpcMessage(
    val authenticatedPeerDeviceId: StudyFlowDeviceId,
    val envelope: StudyFlowLinkEnvelope,
    val receivedAtEpochMillis: Long,
)

fun interface StudyFlowRoutedRpcHandler {
    fun handle(message: StudyFlowRoutedRpcMessage)
}

/**
 * Runtime message bus for SFLP14-SFLP23 domain traffic.
 *
 * The server publishes only authenticated requests into this bus. Platform/domain integrations can
 * subscribe without owning sockets, and the bounded in-memory inbox gives deterministic delivery
 * evidence and a hand-off point when a surface has not yet attached its reducer.
 */
class StudyFlowRoutedRpcBus(
    private val inboxLimit: Int = 256,
) {
    init { require(inboxLimit in 1..4096) { "Routed RPC inbox limit is out of range" } }

    private val handlers = CopyOnWriteArrayList<StudyFlowRoutedRpcHandler>()
    private val inbox = ArrayDeque<StudyFlowRoutedRpcMessage>()

    fun subscribe(handler: StudyFlowRoutedRpcHandler): Closeable {
        handlers += handler
        return Closeable { handlers -= handler }
    }

    fun publish(message: StudyFlowRoutedRpcMessage) {
        synchronized(inbox) {
            while (inbox.size >= inboxLimit) inbox.removeFirst()
            inbox.addLast(message)
        }
        handlers.forEach { handler -> handler.handle(message) }
    }

    fun snapshot(): List<StudyFlowRoutedRpcMessage> = synchronized(inbox) { inbox.toList() }

    fun drain(): List<StudyFlowRoutedRpcMessage> = synchronized(inbox) {
        buildList(inbox.size) {
            while (inbox.isNotEmpty()) add(inbox.removeFirst())
        }
    }
}

/** Canonical message families that must traverse the production peer server rather than a fake client. */
object StudyFlowRoutedRpcContracts {
    val RequestNames: Set<String> = linkedSetOf(
        StudyFlowActivityHandoffMessageNames.Offer,
        StudyFlowActivityHandoffMessageNames.Accept,
        StudyFlowActivityHandoffMessageNames.Resume,
        StudyFlowActivityHandoffMessageNames.Reject,
        StudyFlowActivityHandoffMessageNames.WebTvReceiver,
        StudyFlowActivityHandoffMessageNames.RemoteInput,
        StudyFlowLiveSessionMessageNames.Start,
        StudyFlowLiveSessionMessageNames.Join,
        StudyFlowLiveSessionMessageNames.Heartbeat,
        StudyFlowLiveSessionMessageNames.Cursor,
        StudyFlowLiveSessionMessageNames.Pause,
        StudyFlowLiveSessionMessageNames.Resume,
        StudyFlowLiveSessionMessageNames.Complete,
        StudyFlowPresentationProjectionMessageNames.Offer,
        StudyFlowPresentationProjectionMessageNames.Accept,
        StudyFlowPresentationProjectionMessageNames.Frame,
        StudyFlowPresentationProjectionMessageNames.Ack,
        StudyFlowPresentationProjectionMessageNames.Control,
        StudyFlowPresentationProjectionMessageNames.End,
        StudyFlowControlMessageNames.LeaseGrant,
        StudyFlowControlMessageNames.Command,
        StudyFlowControlMessageNames.CommandAck,
        StudyFlowControlMessageNames.InputOffer,
        StudyFlowControlMessageNames.InputEvent,
        StudyFlowControlMessageNames.InputAck,
        StudyFlowNotificationMediaMessageNames.ScheduleChange,
        StudyFlowNotificationMediaMessageNames.MaterialOffer,
        StudyFlowNotificationMediaMessageNames.MaterialChunk,
        StudyFlowNotificationMediaMessageNames.MaterialChunkAck,
        StudyFlowShareImportWireNames.ShareImportOffer,
        StudyFlowShareImportWireNames.ShareImportAck,
        StudyFlowShareImportWireNames.ConflictReviewOpen,
        StudyFlowShareImportWireNames.ConflictResolve,
        StudyFlowShareImportWireNames.ConflictResolutionAck,
        StudyFlowAccountBackupMessageNames.AccountHandoffOffer,
        StudyFlowAccountBackupMessageNames.AccountHandoffAccept,
        StudyFlowAccountBackupMessageNames.AccountHandoffReject,
        StudyFlowAccountBackupMessageNames.BackupBundleOffer,
        StudyFlowAccountBackupMessageNames.BackupRestorePlan,
        StudyFlowAccountBackupMessageNames.BackupRestoreAck,
        StudyFlowAccountBackupMessageNames.ReleaseValidationPlan,
        StudyFlowAccountBackupMessageNames.ReleaseValidationResult,
    )

    fun supports(name: String): Boolean = name in RequestNames
}

internal fun StudyFlowRoutedRpcAccepted.toPayload(): JsonObject =
    StudyFlowLinkCodec.encodePayload(StudyFlowRoutedRpcAccepted.serializer(), this)
