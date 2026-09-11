package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DefaultLiveSessionTtlMillis = 30_000L
private const val DefaultProjectionOfferTtlMillis = 120_000L
private val continuityWireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
private val sha256HexPattern = Regex("[a-f0-9]{64}")

@Serializable
@JvmInline
value class StudyFlowLiveSessionId(val value: String) {
    init {
        require(continuityWireTokenPattern.matches(value)) { "Live session ID must be a bounded wire token" }
    }
}

@Serializable
@JvmInline
value class StudyFlowProjectionId(val value: String) {
    init {
        require(continuityWireTokenPattern.matches(value)) { "Projection ID must be a bounded wire token" }
    }
}

@Serializable
enum class StudyFlowLiveSessionState {
    @SerialName("running")
    RUNNING,

    @SerialName("paused")
    PAUSED,

    @SerialName("completed")
    COMPLETED,
}

@Serializable
enum class StudyFlowLiveSessionRole {
    @SerialName("executor")
    EXECUTOR,

    @SerialName("controller")
    CONTROLLER,

    @SerialName("observer")
    OBSERVER,

    @SerialName("presenter")
    PRESENTER,
}

@Serializable
enum class StudyFlowLiveSessionEventKind {
    @SerialName("started")
    STARTED,

    @SerialName("joined")
    JOINED,

    @SerialName("heartbeat")
    HEARTBEAT,

    @SerialName("cursor")
    CURSOR,

    @SerialName("paused")
    PAUSED,

    @SerialName("resumed")
    RESUMED,

    @SerialName("answer_recorded")
    ANSWER_RECORDED,

    @SerialName("completed")
    COMPLETED,
}

@Serializable
data class StudyFlowLiveSessionCursor(
    val activityId: StudyFlowActivityId,
    val positionToken: String,
    val itemIndex: Int? = null,
    val elapsedMillis: Long = 0L,
    val answeredCount: Int = 0,
    val payloadHash: String? = null,
) {
    init {
        require(positionToken.isNotBlank()) { "Live session cursor token must not be blank" }
        require(itemIndex == null || itemIndex >= 0) { "Live session item index must not be negative" }
        require(elapsedMillis >= 0L) { "Live session elapsed time must not be negative" }
        require(answeredCount >= 0) { "Live session answer count must not be negative" }
        require(payloadHash == null || sha256HexPattern.matches(payloadHash)) {
            "Live session payload hash must be lowercase SHA-256 hex"
        }
    }
}

@Serializable
data class StudyFlowLiveSessionParticipant(
    val device: StudyFlowLinkPeerDescriptor,
    val role: StudyFlowLiveSessionRole,
    val joinedAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long = joinedAtEpochMillis,
) {
    init {
        require(joinedAtEpochMillis >= 0L) { "Join time must not be negative" }
        require(lastSeenAtEpochMillis >= joinedAtEpochMillis) { "Last-seen time must not predate join time" }
    }
}

@Serializable
data class StudyFlowLiveSessionSnapshot(
    val sessionId: StudyFlowLiveSessionId,
    val workspaceId: StudyFlowWorkspaceId,
    val activityId: StudyFlowActivityId,
    val state: StudyFlowLiveSessionState,
    val activeExecutorDeviceId: StudyFlowDeviceId,
    val fencingToken: String,
    val sessionEpoch: Long,
    val lastSequence: Long,
    val cursor: StudyFlowLiveSessionCursor,
    val participants: List<StudyFlowLiveSessionParticipant>,
    val completedByDeviceId: StudyFlowDeviceId? = null,
    val completionOperationId: StudyFlowOperationId? = null,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(fencingToken.isNotBlank()) { "Live session fencing token must not be blank" }
        require(sessionEpoch >= 1L) { "Live session epoch must be at least 1" }
        require(lastSequence >= 0L) { "Live session sequence must not be negative" }
        require(participants.map { it.device.deviceId }.distinct().size == participants.size) {
            "Live session participants must be unique per device"
        }
        if (state == StudyFlowLiveSessionState.COMPLETED) {
            requireNotNull(completionOperationId) { "Completed sessions require a completion operation ID" }
            requireNotNull(completedByDeviceId) { "Completed sessions require a completing device" }
        }
        require(updatedAtEpochMillis >= 0L) { "Live session update time must not be negative" }
    }
}

@Serializable
data class StudyFlowLiveSessionEvent(
    val eventId: StudyFlowMessageId,
    val sessionId: StudyFlowLiveSessionId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val sessionEpoch: Long,
    val sequence: Long,
    val kind: StudyFlowLiveSessionEventKind,
    val cursor: StudyFlowLiveSessionCursor,
    val participant: StudyFlowLiveSessionParticipant? = null,
    val completionOperationId: StudyFlowOperationId? = null,
    val occurredAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sessionEpoch >= 1L) { "Live session event epoch must be at least 1" }
        require(sequence >= 1L) { "Live session event sequence must be at least 1" }
        require(occurredAtEpochMillis >= 0L) { "Live session event time must not be negative" }
        if (kind == StudyFlowLiveSessionEventKind.COMPLETED) {
            requireNotNull(completionOperationId) { "Completion events require a completion operation ID" }
        }
    }

    fun toDomainOperation(
        entityRevision: StudyFlowEntityRevision,
        originSequence: Long,
        logicalTimestamp: Long,
    ): StudyFlowDomainOperation {
        val operationType = when (kind) {
            StudyFlowLiveSessionEventKind.STARTED -> StudyFlowDomainOperationTypes.SessionStarted
            StudyFlowLiveSessionEventKind.PAUSED -> StudyFlowDomainOperationTypes.SessionPaused
            StudyFlowLiveSessionEventKind.COMPLETED -> StudyFlowDomainOperationTypes.SessionCompleted
            else -> "session.live_${kind.name.lowercase()}"
        }
        return StudyFlowDomainOperation(
            operationId = completionOperationId ?: StudyFlowOperationId("op-${eventId.value}"),
            workspaceId = workspaceId,
            originDeviceId = sourceDeviceId,
            originSequence = originSequence,
            entityId = StudyFlowEntityId(sessionId.value),
            entityRevision = entityRevision,
            logicalTimestamp = logicalTimestamp,
            operationType = operationType,
            occurredAtEpochMillis = occurredAtEpochMillis,
            payload = JsonObject(
                mapOf(
                    "sessionId" to JsonPrimitive(sessionId.value),
                    "eventKind" to JsonPrimitive(kind.name.lowercase()),
                    "fencing" to JsonPrimitive("live-session-continuity"),
                ),
            ),
        )
    }
}

sealed class StudyFlowLiveSessionApplyResult {
    data class Applied(val snapshot: StudyFlowLiveSessionSnapshot) : StudyFlowLiveSessionApplyResult()
    data class Rejected(val reason: String) : StudyFlowLiveSessionApplyResult()
    object Duplicate : StudyFlowLiveSessionApplyResult()
}

class StudyFlowLiveSessionReducer(initialSnapshot: StudyFlowLiveSessionSnapshot) {
    private var current = initialSnapshot
    private val appliedEvents = linkedSetOf<StudyFlowMessageId>()

    fun snapshot(): StudyFlowLiveSessionSnapshot = synchronized(this) { current }

    fun apply(event: StudyFlowLiveSessionEvent): StudyFlowLiveSessionApplyResult = synchronized(this) {
        if (event.eventId in appliedEvents) return StudyFlowLiveSessionApplyResult.Duplicate
        if (event.workspaceId != current.workspaceId) {
            return StudyFlowLiveSessionApplyResult.Rejected("workspace_mismatch")
        }
        if (event.sessionId != current.sessionId) return StudyFlowLiveSessionApplyResult.Rejected("session_mismatch")
        if (event.sessionEpoch < current.sessionEpoch) return StudyFlowLiveSessionApplyResult.Rejected("stale_epoch")
        if (event.sessionEpoch == current.sessionEpoch && event.sequence <= current.lastSequence) {
            return StudyFlowLiveSessionApplyResult.Rejected("stale_sequence")
        }
        if (current.state == StudyFlowLiveSessionState.COMPLETED) {
            return StudyFlowLiveSessionApplyResult.Rejected("already_completed")
        }

        val participants = mergeParticipant(event)
        val nextState = when (event.kind) {
            StudyFlowLiveSessionEventKind.PAUSED -> StudyFlowLiveSessionState.PAUSED
            StudyFlowLiveSessionEventKind.RESUMED,
            StudyFlowLiveSessionEventKind.STARTED,
            StudyFlowLiveSessionEventKind.JOINED,
            StudyFlowLiveSessionEventKind.HEARTBEAT,
            StudyFlowLiveSessionEventKind.CURSOR,
            StudyFlowLiveSessionEventKind.ANSWER_RECORDED,
            -> StudyFlowLiveSessionState.RUNNING
            StudyFlowLiveSessionEventKind.COMPLETED -> StudyFlowLiveSessionState.COMPLETED
        }
        val completionOperation = if (event.kind == StudyFlowLiveSessionEventKind.COMPLETED) {
            event.completionOperationId
        } else {
            current.completionOperationId
        }
        val completedBy = if (event.kind == StudyFlowLiveSessionEventKind.COMPLETED) {
            event.sourceDeviceId
        } else {
            current.completedByDeviceId
        }
        current = current.copy(
            state = nextState,
            cursor = event.cursor,
            participants = participants,
            completedByDeviceId = completedBy,
            completionOperationId = completionOperation,
            sessionEpoch = event.sessionEpoch,
            lastSequence = event.sequence,
            updatedAtEpochMillis = event.occurredAtEpochMillis,
        )
        appliedEvents += event.eventId
        StudyFlowLiveSessionApplyResult.Applied(current)
    }

    private fun mergeParticipant(event: StudyFlowLiveSessionEvent): List<StudyFlowLiveSessionParticipant> {
        val incoming = event.participant ?: return current.participants
        return (current.participants.filter { it.device.deviceId != incoming.device.deviceId } + incoming)
            .sortedBy { it.device.deviceId.value }
    }
}

class StudyFlowLiveSessionContinuityController(
    initialSnapshot: StudyFlowLiveSessionSnapshot,
    private val clock: StudyFlowPeerClock,
) {
    private val reducer = StudyFlowLiveSessionReducer(initialSnapshot)

    fun snapshot(): StudyFlowLiveSessionSnapshot = reducer.snapshot()

    fun heartbeat(
        eventId: StudyFlowMessageId,
        sourceDeviceId: StudyFlowDeviceId,
        cursor: StudyFlowLiveSessionCursor = snapshot().cursor,
    ): StudyFlowLiveSessionApplyResult = emit(eventId, sourceDeviceId, StudyFlowLiveSessionEventKind.HEARTBEAT, cursor)

    fun updateCursor(
        eventId: StudyFlowMessageId,
        sourceDeviceId: StudyFlowDeviceId,
        cursor: StudyFlowLiveSessionCursor,
    ): StudyFlowLiveSessionApplyResult = emit(eventId, sourceDeviceId, StudyFlowLiveSessionEventKind.CURSOR, cursor)

    fun pause(eventId: StudyFlowMessageId, sourceDeviceId: StudyFlowDeviceId): StudyFlowLiveSessionApplyResult =
        emit(eventId, sourceDeviceId, StudyFlowLiveSessionEventKind.PAUSED, snapshot().cursor)

    fun resume(eventId: StudyFlowMessageId, sourceDeviceId: StudyFlowDeviceId): StudyFlowLiveSessionApplyResult =
        emit(eventId, sourceDeviceId, StudyFlowLiveSessionEventKind.RESUMED, snapshot().cursor)

    fun complete(
        eventId: StudyFlowMessageId,
        sourceDeviceId: StudyFlowDeviceId,
        completionOperationId: StudyFlowOperationId,
    ): StudyFlowLiveSessionApplyResult = emit(
        eventId = eventId,
        sourceDeviceId = sourceDeviceId,
        kind = StudyFlowLiveSessionEventKind.COMPLETED,
        cursor = snapshot().cursor,
        completionOperationId = completionOperationId,
    )

    private fun emit(
        eventId: StudyFlowMessageId,
        sourceDeviceId: StudyFlowDeviceId,
        kind: StudyFlowLiveSessionEventKind,
        cursor: StudyFlowLiveSessionCursor,
        completionOperationId: StudyFlowOperationId? = null,
    ): StudyFlowLiveSessionApplyResult {
        val before = snapshot()
        return reducer.apply(
            StudyFlowLiveSessionEvent(
                eventId = eventId,
                sessionId = before.sessionId,
                workspaceId = before.workspaceId,
                sourceDeviceId = sourceDeviceId,
                sessionEpoch = before.sessionEpoch,
                sequence = before.lastSequence + 1,
                kind = kind,
                cursor = cursor,
                completionOperationId = completionOperationId,
                occurredAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }

    fun isStale(nowEpochMillis: Long = clock.nowEpochMillis()): Boolean =
        nowEpochMillis - snapshot().updatedAtEpochMillis > DefaultLiveSessionTtlMillis
}

@Serializable
enum class StudyFlowPresentationContentKind {
    @SerialName("study_session")
    STUDY_SESSION,

    @SerialName("question_set")
    QUESTION_SET,

    @SerialName("review")
    REVIEW,

    @SerialName("library_material")
    LIBRARY_MATERIAL,

    @SerialName("document")
    DOCUMENT,

    @SerialName("analytics")
    ANALYTICS,

    @SerialName("generic")
    GENERIC,
}

@Serializable
enum class StudyFlowPresentationControlKind {
    @SerialName("next")
    NEXT,

    @SerialName("previous")
    PREVIOUS,

    @SerialName("seek")
    SEEK,

    @SerialName("toggle_notes")
    TOGGLE_NOTES,

    @SerialName("zoom_in")
    ZOOM_IN,

    @SerialName("zoom_out")
    ZOOM_OUT,

    @SerialName("end")
    END,
}

@Serializable
data class StudyFlowPresentationProjectionOffer(
    val projectionId: StudyFlowProjectionId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val contentKind: StudyFlowPresentationContentKind,
    val entityId: StudyFlowEntityId? = null,
    val title: String,
    val contentHash: String,
    val requiredCapabilities: List<StudyFlowLinkCapability>,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultProjectionOfferTtlMillis,
) {
    init {
        require(title.isNotBlank()) { "Projection title must not be blank" }
        require(contentHash.matches(sha256HexPattern)) { "Projection content hash must be lowercase SHA-256 hex" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Projection offer must expire after creation" }
        require(StudyFlowLinkCapabilities.DevicePresentationSource in sourceDevice.capabilities) {
            "Projection source must advertise device.presentation_source"
        }
        require(requiredCapabilities.isNotEmpty()) { "Projection offer must declare required capabilities" }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Projection required capabilities must be unique"
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis
}

@Serializable
data class StudyFlowPresentationFrame(
    val projectionId: StudyFlowProjectionId,
    val frameId: StudyFlowMessageId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val contentRevision: StudyFlowEntityRevision,
    val contentHash: String,
    val cursor: StudyFlowActivityCursor,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Projection frame sequence must be at least 1" }
        require(contentHash.matches(sha256HexPattern)) { "Projection frame hash must be lowercase SHA-256 hex" }
    }
}

@Serializable
data class StudyFlowPresentationProjectionAccepted(
    val projectionId: StudyFlowProjectionId,
    val acceptedBy: StudyFlowLinkPeerDescriptor,
    val acceptedAtEpochMillis: Long,
) {
    init {
        require(acceptedAtEpochMillis >= 0L) { "Projection acceptance time must not be negative" }
    }
}

@Serializable
data class StudyFlowPresentationFrameAck(
    val projectionId: StudyFlowProjectionId,
    val sinkDeviceId: StudyFlowDeviceId,
    val acknowledgedFrameId: StudyFlowMessageId,
    val acknowledgedSequence: Long,
    val contentHash: String,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Acknowledged projection sequence must be at least 1" }
        require(contentHash.matches(sha256HexPattern)) { "Projection ack hash must be lowercase SHA-256 hex" }
        require(acknowledgedAtEpochMillis >= 0L) { "Projection ack time must not be negative" }
    }
}

@Serializable
data class StudyFlowPresentationControlCommand(
    val commandId: StudyFlowMessageId,
    val projectionId: StudyFlowProjectionId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val kind: StudyFlowPresentationControlKind,
    val issuedAtEpochMillis: Long,
    val seekPositionToken: String? = null,
) {
    init {
        require(sourceDeviceId != targetDeviceId) { "Projection control must target another device" }
        require(issuedAtEpochMillis >= 0L) { "Projection control time must not be negative" }
        if (kind == StudyFlowPresentationControlKind.SEEK) {
            require(!seekPositionToken.isNullOrBlank()) { "Seek commands require a position token" }
        }
        if (kind != StudyFlowPresentationControlKind.SEEK) {
            require(seekPositionToken == null) { "Only seek commands may carry a position token" }
        }
    }
}

sealed class StudyFlowPresentationApplyResult {
    data class Accepted(val activeFrame: StudyFlowPresentationFrame?) : StudyFlowPresentationApplyResult()
    data class Rejected(val reason: String) : StudyFlowPresentationApplyResult()
    object Duplicate : StudyFlowPresentationApplyResult()
}

class StudyFlowPresentationProjectionReceiver(
    private val sink: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedProjectionIds = linkedSetOf<StudyFlowProjectionId>()
    private val consumedFrames = linkedSetOf<StudyFlowMessageId>()
    private val latestFrames = linkedMapOf<StudyFlowProjectionId, StudyFlowPresentationFrame>()

    fun accept(offer: StudyFlowPresentationProjectionOffer): StudyFlowPresentationApplyResult = synchronized(this) {
        if (offer.isExpired(clock.nowEpochMillis())) return StudyFlowPresentationApplyResult.Rejected("expired")
        if (offer.targetDeviceId != null && offer.targetDeviceId != sink.deviceId) {
            return StudyFlowPresentationApplyResult.Rejected("wrong_target")
        }
        if (offer.sourceDevice.deviceId == sink.deviceId) {
            return StudyFlowPresentationApplyResult.Rejected("self_projection")
        }
        if (offer.requiredCapabilities.any { it in StudyFlowPresentationProjectionPolicy.writeCapabilities }) {
            return StudyFlowPresentationApplyResult.Rejected("projection_write_capability")
        }
        val sinkCapabilities = sink.capabilities.toSet()
        val missing = offer.requiredCapabilities.filter { it !in sinkCapabilities }
        if (missing.isNotEmpty()) return StudyFlowPresentationApplyResult.Rejected("missing_capability")
        acceptedProjectionIds += offer.projectionId
        StudyFlowPresentationApplyResult.Accepted(activeFrame = null)
    }

    fun applyFrame(frame: StudyFlowPresentationFrame): StudyFlowPresentationApplyResult = synchronized(this) {
        if (frame.frameId in consumedFrames) return StudyFlowPresentationApplyResult.Duplicate
        if (frame.projectionId !in acceptedProjectionIds) {
            return StudyFlowPresentationApplyResult.Rejected("projection_not_accepted")
        }
        val latest = latestFrames[frame.projectionId]
        if (latest != null && frame.sequence <= latest.sequence) {
            return StudyFlowPresentationApplyResult.Rejected("stale_frame")
        }
        consumedFrames += frame.frameId
        latestFrames[frame.projectionId] = frame
        StudyFlowPresentationApplyResult.Accepted(activeFrame = frame)
    }

    fun latestFrame(projectionId: StudyFlowProjectionId): StudyFlowPresentationFrame? = synchronized(this) {
        latestFrames[projectionId]
    }

    fun acknowledge(projectionId: StudyFlowProjectionId): StudyFlowPresentationFrameAck? = synchronized(this) {
        val frame = latestFrames[projectionId] ?: return null
        StudyFlowPresentationFrameAck(
            projectionId = projectionId,
            sinkDeviceId = sink.deviceId,
            acknowledgedFrameId = frame.frameId,
            acknowledgedSequence = frame.sequence,
            contentHash = frame.contentHash,
            acknowledgedAtEpochMillis = clock.nowEpochMillis(),
        )
    }
}

object StudyFlowPresentationProjectionPolicy {
    val writeCapabilities: Set<StudyFlowLinkCapability> = setOf(
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

    fun defaultSinkCapabilities(contentKind: StudyFlowPresentationContentKind): List<StudyFlowLinkCapability> =
        when (contentKind) {
            StudyFlowPresentationContentKind.STUDY_SESSION,
            StudyFlowPresentationContentKind.QUESTION_SET,
            StudyFlowPresentationContentKind.REVIEW,
            -> listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.WorkspaceSync,
                StudyFlowLinkCapabilities.SessionPresent,
                StudyFlowLinkCapabilities.DevicePresentationSink,
                StudyFlowLinkCapabilities.DeviceControl,
                StudyFlowLinkCapabilities.AudioOutput,
                StudyFlowLinkCapabilities.VideoOutput,
            )
            StudyFlowPresentationContentKind.LIBRARY_MATERIAL -> listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.LibraryPresent,
                StudyFlowLinkCapabilities.DevicePresentationSink,
                StudyFlowLinkCapabilities.VideoOutput,
            )
            StudyFlowPresentationContentKind.DOCUMENT -> listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.DocumentPresent,
                StudyFlowLinkCapabilities.DevicePresentationSink,
                StudyFlowLinkCapabilities.VideoOutput,
            )
            StudyFlowPresentationContentKind.ANALYTICS -> listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.AnalyticsRead,
                StudyFlowLinkCapabilities.DevicePresentationSink,
                StudyFlowLinkCapabilities.VideoOutput,
            )
            StudyFlowPresentationContentKind.GENERIC -> listOf(
                StudyFlowLinkCapabilities.WorkspaceRead,
                StudyFlowLinkCapabilities.DevicePresentationSink,
            )
        }.distinct()
}

object StudyFlowLiveSessionMessageNames {
    const val Start = "session.live_start"
    const val Join = "session.live_join"
    const val Heartbeat = "session.live_heartbeat"
    const val Cursor = "session.live_cursor"
    const val Pause = "session.live_pause"
    const val Resume = "session.live_resume"
    const val Complete = "session.live_complete"
}

object StudyFlowPresentationProjectionMessageNames {
    const val Offer = "presentation.projection_offer"
    const val Accept = "presentation.projection_accept"
    const val Frame = "presentation.projection_frame"
    const val Ack = "presentation.projection_ack"
    const val Control = "presentation.projection_control"
    const val End = "presentation.projection_end"
}

object StudyFlowLiveSessionWire {
    fun event(
        messageId: StudyFlowMessageId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        event: StudyFlowLiveSessionEvent,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = event.workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = event.nameForWire(),
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowLiveSessionEvent.serializer(), event),
    )

    private fun StudyFlowLiveSessionEvent.nameForWire(): String = when (kind) {
        StudyFlowLiveSessionEventKind.STARTED -> StudyFlowLiveSessionMessageNames.Start
        StudyFlowLiveSessionEventKind.JOINED -> StudyFlowLiveSessionMessageNames.Join
        StudyFlowLiveSessionEventKind.HEARTBEAT -> StudyFlowLiveSessionMessageNames.Heartbeat
        StudyFlowLiveSessionEventKind.CURSOR,
        StudyFlowLiveSessionEventKind.ANSWER_RECORDED,
        -> StudyFlowLiveSessionMessageNames.Cursor
        StudyFlowLiveSessionEventKind.PAUSED -> StudyFlowLiveSessionMessageNames.Pause
        StudyFlowLiveSessionEventKind.RESUMED -> StudyFlowLiveSessionMessageNames.Resume
        StudyFlowLiveSessionEventKind.COMPLETED -> StudyFlowLiveSessionMessageNames.Complete
    }
}

object StudyFlowPresentationProjectionWire {
    fun offer(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        offer: StudyFlowPresentationProjectionOffer,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.REQUEST,
        name = StudyFlowPresentationProjectionMessageNames.Offer,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresentationProjectionOffer.serializer(), offer),
    )

    fun accepted(
        messageId: StudyFlowMessageId,
        requestMessageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        accepted: StudyFlowPresentationProjectionAccepted,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.RESPONSE,
        name = StudyFlowPresentationProjectionMessageNames.Accept,
        sentAtEpochMillis = sentAtEpochMillis,
        correlationId = requestMessageId,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresentationProjectionAccepted.serializer(), accepted),
    )

    fun frame(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        frame: StudyFlowPresentationFrame,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = StudyFlowPresentationProjectionMessageNames.Frame,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresentationFrame.serializer(), frame),
    )

    fun control(
        messageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        command: StudyFlowPresentationControlCommand,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.EVENT,
        name = StudyFlowPresentationProjectionMessageNames.Control,
        sentAtEpochMillis = sentAtEpochMillis,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresentationControlCommand.serializer(), command),
    )

    fun ack(
        messageId: StudyFlowMessageId,
        requestMessageId: StudyFlowMessageId,
        workspaceId: StudyFlowWorkspaceId,
        senderDeviceId: StudyFlowDeviceId,
        sentAtEpochMillis: Long,
        ack: StudyFlowPresentationFrameAck,
    ): StudyFlowLinkEnvelope = StudyFlowLinkEnvelope(
        messageId = messageId,
        workspaceId = workspaceId,
        senderDeviceId = senderDeviceId,
        messageType = StudyFlowLinkMessageType.RESPONSE,
        name = StudyFlowPresentationProjectionMessageNames.Ack,
        sentAtEpochMillis = sentAtEpochMillis,
        correlationId = requestMessageId,
        payload = StudyFlowLinkCodec.encodePayload(StudyFlowPresentationFrameAck.serializer(), ack),
    )
}
