package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private const val DefaultHandoffTtlMillis = 90_000L
private const val DefaultPresentationTtlMillis = 120_000L
private val handoffTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

@Serializable
@JvmInline
value class StudyFlowActivityId(val value: String) {
    init {
        require(handoffTokenPattern.matches(value)) { "Activity ID must be a bounded wire token" }
    }
}

@Serializable
@JvmInline
value class StudyFlowHandoffId(val value: String) {
    init {
        require(handoffTokenPattern.matches(value)) { "Handoff ID must be a bounded wire token" }
    }
}

@Serializable
enum class StudyFlowActivityKind {
    @SerialName("dashboard")
    DASHBOARD,

    @SerialName("plan")
    PLAN,

    @SerialName("schedule")
    SCHEDULE,

    @SerialName("study_session")
    STUDY_SESSION,

    @SerialName("practice_question")
    PRACTICE_QUESTION,

    @SerialName("review_item")
    REVIEW_ITEM,

    @SerialName("library_material")
    LIBRARY_MATERIAL,

    @SerialName("document_view")
    DOCUMENT_VIEW,

    @SerialName("writing_draft")
    WRITING_DRAFT,

    @SerialName("presentation_projection")
    PRESENTATION_PROJECTION,
}

@Serializable
enum class StudyFlowActivityContinuityMode {
    @SerialName("snapshot")
    SNAPSHOT,

    @SerialName("live_control")
    LIVE_CONTROL,

    @SerialName("presentation_sink")
    PRESENTATION_SINK,
}

@Serializable
data class StudyFlowActivityCursor(
    val positionToken: String,
    val scrollRatio: Double? = null,
    val questionIndex: Int? = null,
    val elapsedMillis: Long? = null,
) {
    init {
        require(positionToken.isNotBlank()) { "Activity cursor token must not be blank" }
        require(scrollRatio == null || scrollRatio in 0.0..1.0) { "Scroll ratio must be normalized" }
        require(questionIndex == null || questionIndex >= 0) { "Question index must not be negative" }
        require(elapsedMillis == null || elapsedMillis >= 0L) { "Elapsed time must not be negative" }
    }
}

@Serializable
data class StudyFlowActivitySnapshot(
    val activityId: StudyFlowActivityId,
    val workspaceId: StudyFlowWorkspaceId,
    val kind: StudyFlowActivityKind,
    val entityId: StudyFlowEntityId? = null,
    val entityRevision: StudyFlowEntityRevision? = null,
    val cursor: StudyFlowActivityCursor,
    val continuityMode: StudyFlowActivityContinuityMode,
    val requiredCapabilities: List<StudyFlowLinkCapability>,
    val payloadHash: String,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(requiredCapabilities.isNotEmpty()) { "A handoff must declare required capabilities" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Required capabilities must be unique"
        }
        require(payloadHash.matches(Regex("[a-f0-9]{64}"))) { "Payload hash must be lowercase SHA-256 hex" }
    }
}

@Serializable
data class StudyFlowHandoffOffer(
    val handoffId: StudyFlowHandoffId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val snapshot: StudyFlowActivitySnapshot,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultHandoffTtlMillis,
    val oneTimeNonceHex: String,
) {
    init {
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Handoff offer must expire after creation" }
        require(oneTimeNonceHex.matches(Regex("[a-f0-9]{32,128}"))) { "Handoff nonce must be hex" }
        require(sourceDevice.deviceId != targetDeviceId) { "A device cannot hand off to itself" }
        require(snapshot.workspaceId.value.isNotBlank()) { "Handoff snapshot must carry a workspace" }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis
}

@Serializable
data class StudyFlowHandoffAccept(
    val handoffId: StudyFlowHandoffId,
    val acceptedBy: StudyFlowLinkPeerDescriptor,
    val acceptedAtEpochMillis: Long,
    val acceptedCursor: StudyFlowActivityCursor,
) {
    init {
        require(acceptedAtEpochMillis >= 0L) { "Accepted timestamp must not be negative" }
    }
}

@Serializable
data class StudyFlowHandoffResume(
    val handoffId: StudyFlowHandoffId,
    val resumedBy: StudyFlowDeviceId,
    val workspaceId: StudyFlowWorkspaceId,
    val activityId: StudyFlowActivityId,
    val resumedAtEpochMillis: Long,
    val cursor: StudyFlowActivityCursor,
) {
    init {
        require(resumedAtEpochMillis >= 0L) { "Resume timestamp must not be negative" }
    }
}

@Serializable
data class StudyFlowHandoffRejected(
    val handoffId: StudyFlowHandoffId,
    val rejectedBy: StudyFlowDeviceId,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "Handoff rejection reason must not be blank" }
    }
}

sealed class StudyFlowHandoffDecision {
    data class Accepted(val offer: StudyFlowHandoffOffer) : StudyFlowHandoffDecision()
    data class Rejected(val reason: String) : StudyFlowHandoffDecision()
}

object StudyFlowHandoffPolicy {
    fun evaluate(
        offer: StudyFlowHandoffOffer,
        target: StudyFlowLinkPeerDescriptor,
        nowEpochMillis: Long,
    ): StudyFlowHandoffDecision {
        if (offer.isExpired(nowEpochMillis)) return StudyFlowHandoffDecision.Rejected("expired")
        if (offer.sourceDevice.deviceId == target.deviceId) return StudyFlowHandoffDecision.Rejected("self_target")
        if (offer.targetDeviceId != null && offer.targetDeviceId != target.deviceId) {
            return StudyFlowHandoffDecision.Rejected("wrong_target")
        }
        val targetCapabilities = target.capabilities.toSet()
        val missing = offer.snapshot.requiredCapabilities.filter { it !in targetCapabilities }
        if (missing.isNotEmpty()) return StudyFlowHandoffDecision.Rejected("missing_capability")
        if (target.clientKind == StudyFlowLinkClientKind.ANDROID_TV || target.clientKind == StudyFlowLinkClientKind.WEB_TV) {
            val writeCapabilities = setOf(
                StudyFlowLinkCapabilities.WorkspaceWrite,
                StudyFlowLinkCapabilities.PlanWrite,
                StudyFlowLinkCapabilities.ScheduleWrite,
                StudyFlowLinkCapabilities.QuestionAuthor,
                StudyFlowLinkCapabilities.ReviewEdit,
                StudyFlowLinkCapabilities.GoalEdit,
                StudyFlowLinkCapabilities.LibraryImport,
                StudyFlowLinkCapabilities.WritingEdit,
                StudyFlowLinkCapabilities.BackupImport,
            )
            if (offer.snapshot.requiredCapabilities.any { it in writeCapabilities }) {
                return StudyFlowHandoffDecision.Rejected("tv_write_capability")
            }
        }
        return StudyFlowHandoffDecision.Accepted(offer)
    }
}

class StudyFlowActivityHandoffRegistry(
    private val clock: StudyFlowPeerClock,
) {
    private val pending = linkedMapOf<StudyFlowHandoffId, StudyFlowHandoffOffer>()
    private val consumed = linkedSetOf<StudyFlowHandoffId>()

    fun issue(offer: StudyFlowHandoffOffer): StudyFlowHandoffOffer = synchronized(this) {
        require(!offer.isExpired(clock.nowEpochMillis())) { "Cannot issue an already expired handoff" }
        require(offer.handoffId !in consumed) { "Cannot re-issue a consumed handoff" }
        pending[offer.handoffId] = offer
        offer
    }

    fun pendingOffers(nowEpochMillis: Long = clock.nowEpochMillis()): List<StudyFlowHandoffOffer> = synchronized(this) {
        expireLocked(nowEpochMillis)
        pending.values.sortedBy { it.handoffId.value }
    }

    fun consume(
        handoffId: StudyFlowHandoffId,
        target: StudyFlowLinkPeerDescriptor,
    ): StudyFlowHandoffDecision = synchronized(this) {
        val now = clock.nowEpochMillis()
        expireLocked(now)
        val offer = pending[handoffId] ?: return StudyFlowHandoffDecision.Rejected("missing_or_consumed")
        val decision = StudyFlowHandoffPolicy.evaluate(offer, target, now)
        if (decision is StudyFlowHandoffDecision.Accepted) {
            pending.remove(handoffId)
            consumed += handoffId
        }
        decision
    }

    fun consumed(handoffId: StudyFlowHandoffId): Boolean = synchronized(this) { handoffId in consumed }

    private fun expireLocked(nowEpochMillis: Long) {
        pending.entries.removeIf { (_, offer) -> offer.isExpired(nowEpochMillis) }
    }
}

@Serializable
data class StudyFlowWebTvReceiverOffer(
    val receiver: StudyFlowLinkPeerDescriptor,
    val workspaceId: StudyFlowWorkspaceId,
    val pairingCode: String,
    val qrPayload: String,
    val endpointHints: List<String>,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultPresentationTtlMillis,
) {
    init {
        require(receiver.clientKind == StudyFlowLinkClientKind.WEB_TV) {
            "Web TV receiver offers must advertise client kind web_tv"
        }
        require(StudyFlowLinkCapabilities.DevicePresentationSink in receiver.capabilities) {
            "Web TV receiver must advertise presentation sink capability"
        }
        require(pairingCode.matches(Regex("[0-9]{6}"))) { "Web TV pairing code must be six digits" }
        require(qrPayload.contains("studyflow.sflp.web_tv.receiver.v1")) {
            "Web TV QR payload must carry the receiver marker"
        }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Web TV offer must expire after creation" }
    }
}

@Serializable
enum class StudyFlowRemoteInputKind {
    @SerialName("dpad_up")
    DPAD_UP,

    @SerialName("dpad_down")
    DPAD_DOWN,

    @SerialName("dpad_left")
    DPAD_LEFT,

    @SerialName("dpad_right")
    DPAD_RIGHT,

    @SerialName("select")
    SELECT,

    @SerialName("back")
    BACK,

    @SerialName("text")
    TEXT,
}

@Serializable
data class StudyFlowRemoteInputCommand(
    val commandId: StudyFlowMessageId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val kind: StudyFlowRemoteInputKind,
    val issuedAtEpochMillis: Long,
    val text: String? = null,
) {
    init {
        require(sourceDeviceId != targetDeviceId) { "Remote input must target another device" }
        require(issuedAtEpochMillis >= 0L) { "Remote input timestamp must not be negative" }
        if (kind == StudyFlowRemoteInputKind.TEXT) require(!text.isNullOrBlank()) { "Text input requires text" }
        if (kind != StudyFlowRemoteInputKind.TEXT) require(text == null) { "Only text input may carry text" }
    }
}

object StudyFlowActivityHandoffMessageNames {
    const val Offer = "activity.handoff_offer"
    const val Accept = "activity.handoff_accept"
    const val Resume = "activity.handoff_resume"
    const val Reject = "activity.handoff_reject"
    const val WebTvReceiver = "presentation.web_tv_receiver"
    const val RemoteInput = "device.remote_input"
}

object StudyFlowActivityHandoffWire {
    fun offer(
        messageId: StudyFlowMessageId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        offer: StudyFlowHandoffOffer,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = offer.snapshot.workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.REQUEST,
        name = StudyFlowActivityHandoffMessageNames.Offer,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowHandoffOffer.serializer(), offer),
    )

    fun accept(
        messageId: StudyFlowMessageId,
        requestMessageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        accept: StudyFlowHandoffAccept,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.RESPONSE,
        name = StudyFlowActivityHandoffMessageNames.Accept,
        sentAtEpochMillis = sentAtEpochMillis,
        correlationId = requestMessageId,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowHandoffAccept.serializer(), accept),
    )

    fun resumeEvent(
        messageId: StudyFlowMessageId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        resume: StudyFlowHandoffResume,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = resume.workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = StudyFlowActivityHandoffMessageNames.Resume,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowHandoffResume.serializer(), resume),
    )

    fun remoteInput(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        command: StudyFlowRemoteInputCommand,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = StudyFlowActivityHandoffMessageNames.RemoteInput,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowRemoteInputCommand.serializer(), command),
    )
}
