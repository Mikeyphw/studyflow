package com.mikeyphw.studyflow.core.link

import java.security.MessageDigest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class StudyFlowWebTvConnectivitySession(
    private val receiverIdentity: StudyFlowLinkPeerDescriptor,
    private val workspaceId: StudyFlowWorkspaceId,
    private val endpointHints: List<String>,
    private val clock: StudyFlowPeerClock,
) {
    init {
        require(receiverIdentity.clientKind == StudyFlowLinkClientKind.WEB_TV) {
            "SFLP12 receiver must be a web_tv client"
        }
        require(StudyFlowLinkCapabilities.DevicePresentationSink in receiverIdentity.capabilities) {
            "SFLP12 receiver must be a presentation sink"
        }
    }

    fun receiverOffer(pairingCode: String): StudyFlowWebTvReceiverOffer {
        val created = clock.nowEpochMillis()
        val payload = mapOf(
            "type" to "studyflow.sflp.web_tv.receiver.v1",
            "workspaceId" to workspaceId.value,
            "deviceId" to receiverIdentity.deviceId.value,
            "pairingCode" to pairingCode,
            "endpointHints" to endpointHints,
        )
        return StudyFlowWebTvReceiverOffer(
            receiver = receiverIdentity,
            workspaceId = workspaceId,
            pairingCode = pairingCode,
            qrPayload = payload.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                val encodedValue = when (value) {
                    is List<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> "\"$item\"" }
                    else -> "\"$value\""
                }
                "\"$key\":$encodedValue"
            },
            endpointHints = endpointHints,
            createdAtEpochMillis = created,
        )
    }

    fun projectionSnapshot(
        activityId: StudyFlowActivityId,
        entityId: StudyFlowEntityId,
        cursor: StudyFlowActivityCursor,
        payload: JsonObject = JsonObject(emptyMap()),
    ): StudyFlowActivitySnapshot = StudyFlowActivitySnapshot(
        activityId = activityId,
        workspaceId = workspaceId,
        kind = StudyFlowActivityKind.PRESENTATION_PROJECTION,
        entityId = entityId,
        entityRevision = StudyFlowEntityRevision(0),
        cursor = cursor,
        continuityMode = StudyFlowActivityContinuityMode.PRESENTATION_SINK,
        requiredCapabilities = listOf(
            StudyFlowLinkCapabilities.WorkspaceRead,
            StudyFlowLinkCapabilities.WorkspaceSync,
            StudyFlowLinkCapabilities.DevicePresentationSink,
            StudyFlowLinkCapabilities.DpadInput,
            StudyFlowLinkCapabilities.AudioOutput,
            StudyFlowLinkCapabilities.VideoOutput,
        ),
        payloadHash = payload.sha256Hex(),
        payload = payload,
    )

    fun acceptsRemoteInput(command: StudyFlowRemoteInputCommand): Boolean =
        command.targetDeviceId == receiverIdentity.deviceId &&
            command.kind in setOf(
                StudyFlowRemoteInputKind.DPAD_UP,
                StudyFlowRemoteInputKind.DPAD_DOWN,
                StudyFlowRemoteInputKind.DPAD_LEFT,
                StudyFlowRemoteInputKind.DPAD_RIGHT,
                StudyFlowRemoteInputKind.SELECT,
                StudyFlowRemoteInputKind.BACK,
                StudyFlowRemoteInputKind.TEXT,
            )
}

class StudyFlowActivityHandoffCoordinator(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val registry: StudyFlowActivityHandoffRegistry,
    private val clock: StudyFlowPeerClock,
) {
    fun offerTo(
        target: StudyFlowLinkPeerDescriptor?,
        snapshot: StudyFlowActivitySnapshot,
        handoffId: StudyFlowHandoffId,
        nonceHex: String,
    ): StudyFlowHandoffOffer = registry.issue(
        StudyFlowHandoffOffer(
            handoffId = handoffId,
            sourceDevice = localDevice,
            targetDeviceId = target?.deviceId,
            snapshot = snapshot,
            createdAtEpochMillis = clock.nowEpochMillis(),
            oneTimeNonceHex = nonceHex,
        ),
    )

    fun accept(
        offer: StudyFlowHandoffOffer,
        target: StudyFlowLinkPeerDescriptor,
    ): StudyFlowHandoffAccept {
        registry.issue(offer)
        val decision = registry.consume(offer.handoffId, target)
        require(decision is StudyFlowHandoffDecision.Accepted) {
            "Handoff rejected: ${(decision as StudyFlowHandoffDecision.Rejected).reason}"
        }
        return StudyFlowHandoffAccept(
            handoffId = offer.handoffId,
            acceptedBy = target,
            acceptedAtEpochMillis = clock.nowEpochMillis(),
            acceptedCursor = offer.snapshot.cursor,
        )
    }
}

private fun JsonObject.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

fun sampleStudyFlowProjectionPayload(title: String): JsonObject = JsonObject(
    mapOf(
        "title" to JsonPrimitive(title),
        "format" to JsonPrimitive("studyflow.presentation.snapshot.v1"),
    ),
)
