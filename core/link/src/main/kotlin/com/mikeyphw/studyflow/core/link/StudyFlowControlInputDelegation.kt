package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DefaultControlLeaseTtlMillis = 60_000L
private const val DefaultInputDelegationTtlMillis = 120_000L
private val controlWireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

@Serializable
@JvmInline
value class StudyFlowControlSessionId(val value: String) {
    init { require(controlWireTokenPattern.matches(value)) { "Control session ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowControlLeaseToken(val value: String) {
    init { require(controlWireTokenPattern.matches(value)) { "Control lease token must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowInputDelegationId(val value: String) {
    init { require(controlWireTokenPattern.matches(value)) { "Input delegation ID must be a bounded wire token" } }
}

@Serializable
enum class StudyFlowControlCommandKind {
    @SerialName("navigate_home") NAVIGATE_HOME,
    @SerialName("navigate_back") NAVIGATE_BACK,
    @SerialName("open_activity") OPEN_ACTIVITY,
    @SerialName("start_session") START_SESSION,
    @SerialName("pause_session") PAUSE_SESSION,
    @SerialName("resume_session") RESUME_SESSION,
    @SerialName("complete_session") COMPLETE_SESSION,
    @SerialName("select") SELECT,
    @SerialName("focus_next") FOCUS_NEXT,
    @SerialName("focus_previous") FOCUS_PREVIOUS,
    @SerialName("set_volume") SET_VOLUME,
    @SerialName("start_projection") START_PROJECTION,
    @SerialName("stop_projection") STOP_PROJECTION,
}

@Serializable
enum class StudyFlowControlCommandStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
}

@Serializable
enum class StudyFlowInputEventKind {
    @SerialName("text_commit") TEXT_COMMIT,
    @SerialName("text_replace") TEXT_REPLACE,
    @SerialName("key_down") KEY_DOWN,
    @SerialName("key_up") KEY_UP,
    @SerialName("dpad") DPAD,
    @SerialName("select") SELECT,
    @SerialName("back") BACK,
    @SerialName("touch_tap") TOUCH_TAP,
    @SerialName("touch_scroll") TOUCH_SCROLL,
    @SerialName("voice_dictation") VOICE_DICTATION,
}

@Serializable
data class StudyFlowControlSurface(
    val device: StudyFlowLinkPeerDescriptor,
    val supportedCommands: List<StudyFlowControlCommandKind>,
    val inputCapabilities: List<StudyFlowLinkCapability>,
) {
    init {
        require(StudyFlowLinkCapabilities.DeviceControl in device.capabilities) {
            "Control surfaces must advertise device.control"
        }
        require(supportedCommands.isNotEmpty()) { "A control surface must expose at least one command" }
        require(supportedCommands.distinct().size == supportedCommands.size) {
            "Control commands must be unique"
        }
    }
}

@Serializable
data class StudyFlowControlLease(
    val sessionId: StudyFlowControlSessionId,
    val workspaceId: StudyFlowWorkspaceId,
    val controller: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId,
    val leaseToken: StudyFlowControlLeaseToken,
    val grantedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = grantedAtEpochMillis + DefaultControlLeaseTtlMillis,
    val allowedCommands: List<StudyFlowControlCommandKind>,
) {
    init {
        require(controller.deviceId != targetDeviceId) { "A device cannot lease control to itself" }
        require(expiresAtEpochMillis > grantedAtEpochMillis) { "Control lease must expire after grant" }
        require(allowedCommands.isNotEmpty()) { "Control lease must allow at least one command" }
        require(StudyFlowLinkCapabilities.DeviceControl in controller.capabilities) {
            "Controller must advertise device.control"
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis
}

@Serializable
data class StudyFlowControlCommand(
    val messageId: StudyFlowMessageId,
    val sessionId: StudyFlowControlSessionId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val leaseToken: StudyFlowControlLeaseToken,
    val sequence: Long,
    val kind: StudyFlowControlCommandKind,
    val activityId: StudyFlowActivityId? = null,
    val projectionId: StudyFlowProjectionId? = null,
    val occurredAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Control command sequence must be positive" }
        require(sourceDeviceId != targetDeviceId) { "Control command cannot target the source device" }
        require(occurredAtEpochMillis >= 0L) { "Control command time must not be negative" }
    }
}

@Serializable
data class StudyFlowControlCommandAck(
    val messageId: StudyFlowMessageId,
    val sessionId: StudyFlowControlSessionId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowControlCommandStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Control acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Control acknowledgement time must not be negative" }
        require(status != StudyFlowControlCommandStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected commands require a reason"
        }
    }
}

@Serializable
data class StudyFlowInputDelegationOffer(
    val delegationId: StudyFlowInputDelegationId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId,
    val acceptedInputCapabilities: List<StudyFlowLinkCapability>,
    val activityId: StudyFlowActivityId? = null,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultInputDelegationTtlMillis,
    val oneTimeNonceHex: String,
) {
    init {
        require(sourceDevice.deviceId != targetDeviceId) { "Input delegation cannot target the source device" }
        require(acceptedInputCapabilities.isNotEmpty()) { "Input delegation must declare accepted capabilities" }
        require(acceptedInputCapabilities.distinct().size == acceptedInputCapabilities.size) {
            "Input capabilities must be unique"
        }
        require(oneTimeNonceHex.matches(Regex("[a-f0-9]{32,128}"))) { "Input delegation nonce must be hex" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Input delegation must expire after creation" }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis
}

@Serializable
data class StudyFlowInputEvent(
    val messageId: StudyFlowMessageId,
    val delegationId: StudyFlowInputDelegationId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val kind: StudyFlowInputEventKind,
    val text: String? = null,
    val key: String? = null,
    val occurredAtEpochMillis: Long,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Input event sequence must be positive" }
        require(sourceDeviceId != targetDeviceId) { "Input event cannot target the source device" }
        require(occurredAtEpochMillis >= 0L) { "Input event time must not be negative" }
        if (kind == StudyFlowInputEventKind.TEXT_COMMIT || kind == StudyFlowInputEventKind.TEXT_REPLACE) {
            require(!text.isNullOrBlank()) { "Text input events require text" }
        }
    }
}

sealed class StudyFlowControlApplyResult {
    data class Accepted(val ack: StudyFlowControlCommandAck) : StudyFlowControlApplyResult()
    data class Rejected(val reason: String) : StudyFlowControlApplyResult()
    object Duplicate : StudyFlowControlApplyResult()
}

sealed class StudyFlowInputDelegationResult {
    data class Accepted(val offer: StudyFlowInputDelegationOffer) : StudyFlowInputDelegationResult()
    data class Rejected(val reason: String) : StudyFlowInputDelegationResult()
    object Duplicate : StudyFlowInputDelegationResult()
}

object StudyFlowControlMessageNames {
    const val LeaseGrant = "control.lease_grant"
    const val Command = "control.command"
    const val CommandAck = "control.command_ack"
    const val InputOffer = "input.delegation_offer"
    const val InputEvent = "input.event"
    const val InputAck = "input.event_ack"
}

object StudyFlowControlPolicy {
    fun grantLease(
        workspaceId: StudyFlowWorkspaceId,
        controller: StudyFlowLinkPeerDescriptor,
        target: StudyFlowLinkPeerDescriptor,
        sessionId: StudyFlowControlSessionId,
        leaseToken: StudyFlowControlLeaseToken,
        allowedCommands: List<StudyFlowControlCommandKind>,
        nowEpochMillis: Long,
    ): StudyFlowControlLease {
        require(StudyFlowLinkCapabilities.DeviceControl in controller.capabilities) {
            "Controller lacks device.control"
        }
        require(StudyFlowLinkCapabilities.SessionControl in target.capabilities ||
            StudyFlowLinkCapabilities.DevicePresentationSink in target.capabilities) {
            "Target lacks a controllable surface"
        }
        require(StudyFlowLinkCapabilities.WorkspaceWrite !in target.capabilities ||
            target.clientKind != StudyFlowLinkClientKind.ANDROID_TV &&
                target.clientKind != StudyFlowLinkClientKind.WEB_TV) {
            "TV-class sinks must not receive write authority through control"
        }
        return StudyFlowControlLease(
            sessionId = sessionId,
            workspaceId = workspaceId,
            controller = controller,
            targetDeviceId = target.deviceId,
            leaseToken = leaseToken,
            grantedAtEpochMillis = nowEpochMillis,
            allowedCommands = allowedCommands,
        )
    }

    fun inputOffer(
        delegationId: StudyFlowInputDelegationId,
        workspaceId: StudyFlowWorkspaceId,
        source: StudyFlowLinkPeerDescriptor,
        target: StudyFlowLinkPeerDescriptor,
        requestedInput: List<StudyFlowLinkCapability>,
        nonceHex: String,
        nowEpochMillis: Long,
        activityId: StudyFlowActivityId? = null,
    ): StudyFlowInputDelegationOffer {
        require(requestedInput.all { it in target.capabilities }) { "Target lacks requested input capability" }
        require(requestedInput.any {
            it == StudyFlowLinkCapabilities.KeyboardInput ||
                it == StudyFlowLinkCapabilities.TouchInput ||
                it == StudyFlowLinkCapabilities.DpadInput ||
                it == StudyFlowLinkCapabilities.TextInputProvide
        }) { "Input delegation requires an input capability" }
        return StudyFlowInputDelegationOffer(
            delegationId = delegationId,
            workspaceId = workspaceId,
            sourceDevice = source,
            targetDeviceId = target.deviceId,
            acceptedInputCapabilities = requestedInput.distinct(),
            activityId = activityId,
            createdAtEpochMillis = nowEpochMillis,
            oneTimeNonceHex = nonceHex,
        )
    }
}

class StudyFlowControlCommandReducer(
    private val targetDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedLeases = linkedMapOf<StudyFlowControlSessionId, StudyFlowControlLease>()
    private val appliedMessages = linkedSetOf<StudyFlowMessageId>()
    private val lastSequenceBySession = linkedMapOf<StudyFlowControlSessionId, Long>()

    fun rememberLease(lease: StudyFlowControlLease) {
        require(lease.targetDeviceId == targetDevice.deviceId) { "Lease targets a different device" }
        acceptedLeases[lease.sessionId] = lease
    }

    fun apply(command: StudyFlowControlCommand): StudyFlowControlApplyResult {
        if (command.messageId in appliedMessages) return StudyFlowControlApplyResult.Duplicate
        val lease = acceptedLeases[command.sessionId]
            ?: return StudyFlowControlApplyResult.Rejected("unknown_lease")
        if (lease.isExpired(clock.nowEpochMillis())) return StudyFlowControlApplyResult.Rejected("lease_expired")
        if (command.workspaceId != lease.workspaceId) return StudyFlowControlApplyResult.Rejected("workspace_mismatch")
        if (command.targetDeviceId != targetDevice.deviceId) return StudyFlowControlApplyResult.Rejected("wrong_target")
        if (command.sourceDeviceId != lease.controller.deviceId) {
            return StudyFlowControlApplyResult.Rejected("wrong_controller")
        }
        if (command.leaseToken != lease.leaseToken) return StudyFlowControlApplyResult.Rejected("bad_lease_token")
        if (command.kind !in lease.allowedCommands) return StudyFlowControlApplyResult.Rejected("command_not_allowed")
        val last = lastSequenceBySession[command.sessionId] ?: 0L
        if (command.sequence <= last) return StudyFlowControlApplyResult.Rejected("stale_sequence")
        appliedMessages += command.messageId
        lastSequenceBySession[command.sessionId] = command.sequence
        return StudyFlowControlApplyResult.Accepted(
            StudyFlowControlCommandAck(
                messageId = StudyFlowMessageId("ack-${command.messageId.value}"),
                sessionId = command.sessionId,
                targetDeviceId = targetDevice.deviceId,
                acknowledgedSequence = command.sequence,
                status = StudyFlowControlCommandStatus.ACCEPTED,
                acknowledgedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }
}

class StudyFlowInputDelegationReducer(
    private val targetDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedDelegations = linkedMapOf<StudyFlowInputDelegationId, StudyFlowInputDelegationOffer>()
    private val consumedOffers = linkedSetOf<StudyFlowInputDelegationId>()
    private val appliedMessages = linkedSetOf<StudyFlowMessageId>()
    private val lastSequenceByDelegation = linkedMapOf<StudyFlowInputDelegationId, Long>()

    fun accept(offer: StudyFlowInputDelegationOffer): StudyFlowInputDelegationResult {
        if (offer.delegationId in consumedOffers) return StudyFlowInputDelegationResult.Duplicate
        if (offer.isExpired(clock.nowEpochMillis())) return StudyFlowInputDelegationResult.Rejected("expired")
        if (offer.targetDeviceId != targetDevice.deviceId) {
            return StudyFlowInputDelegationResult.Rejected("wrong_target")
        }
        if (!targetDevice.capabilities.containsAll(offer.acceptedInputCapabilities)) {
            return StudyFlowInputDelegationResult.Rejected("missing_input_capability")
        }
        consumedOffers += offer.delegationId
        acceptedDelegations[offer.delegationId] = offer
        return StudyFlowInputDelegationResult.Accepted(offer)
    }

    fun apply(event: StudyFlowInputEvent): StudyFlowInputDelegationResult {
        if (event.messageId in appliedMessages) return StudyFlowInputDelegationResult.Duplicate
        val offer = acceptedDelegations[event.delegationId]
            ?: return StudyFlowInputDelegationResult.Rejected("unknown_delegation")
        if (offer.isExpired(clock.nowEpochMillis())) {
            return StudyFlowInputDelegationResult.Rejected("delegation_expired")
        }
        if (event.workspaceId != offer.workspaceId) return StudyFlowInputDelegationResult.Rejected("workspace_mismatch")
        if (event.sourceDeviceId != offer.sourceDevice.deviceId) {
            return StudyFlowInputDelegationResult.Rejected("wrong_source")
        }
        if (event.targetDeviceId != targetDevice.deviceId) {
            return StudyFlowInputDelegationResult.Rejected("wrong_target")
        }
        val last = lastSequenceByDelegation[event.delegationId] ?: 0L
        if (event.sequence <= last) return StudyFlowInputDelegationResult.Rejected("stale_sequence")
        appliedMessages += event.messageId
        lastSequenceByDelegation[event.delegationId] = event.sequence
        return StudyFlowInputDelegationResult.Accepted(offer)
    }

    fun toDomainOperation(
        event: StudyFlowInputEvent,
        entityRevision: StudyFlowEntityRevision,
        originSequence: Long,
        logicalTimestamp: Long,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("op-${event.messageId.value}"),
        workspaceId = event.workspaceId,
        originDeviceId = event.sourceDeviceId,
        originSequence = originSequence,
        entityId = StudyFlowEntityId(event.delegationId.value),
        entityRevision = entityRevision,
        logicalTimestamp = logicalTimestamp,
        operationType = "input.${event.kind.name.lowercase()}",
        occurredAtEpochMillis = event.occurredAtEpochMillis,
        payload = JsonObject(
            mapOf(
                "delegationId" to JsonPrimitive(event.delegationId.value),
                "targetDeviceId" to JsonPrimitive(event.targetDeviceId.value),
                "inputDelegation" to JsonPrimitive("sflp17"),
            ),
        ),
    )
}
