package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private const val MaxWireTokenLength = 128
private const val MaxDisplayNameLength = 80
private val wireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
private val capabilityPattern = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
private val messageNamePattern = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")

@Serializable
data class StudyFlowLinkProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<StudyFlowLinkProtocolVersion> {
    init {
        require(major >= 1) { "Protocol major must be at least 1" }
        require(minor >= 0) { "Protocol minor must not be negative" }
    }

    override fun compareTo(other: StudyFlowLinkProtocolVersion): Int =
        compareValuesBy(this, other, StudyFlowLinkProtocolVersion::major, StudyFlowLinkProtocolVersion::minor)
}

@Serializable
data class StudyFlowLinkProtocolSupport(
    val major: Int,
    val minMinor: Int,
    val maxMinor: Int,
) {
    init {
        require(major >= 1) { "Protocol major must be at least 1" }
        require(minMinor >= 0) { "Minimum minor must not be negative" }
        require(maxMinor >= minMinor) { "Maximum minor must be greater than or equal to minimum minor" }
    }

    fun contains(version: StudyFlowLinkProtocolVersion): Boolean =
        version.major == major && version.minor in minMinor..maxMinor
}

object StudyFlowLinkProtocol {
    val Current = StudyFlowLinkProtocolVersion(major = 1, minor = 0)
    val CurrentSupport = StudyFlowLinkProtocolSupport(major = 1, minMinor = 0, maxMinor = 0)

    fun negotiate(
        local: StudyFlowLinkProtocolSupport,
        remote: StudyFlowLinkProtocolSupport,
    ): StudyFlowLinkProtocolVersion? {
        if (local.major != remote.major) return null
        val lowestSharedMinor = maxOf(local.minMinor, remote.minMinor)
        val highestSharedMinor = minOf(local.maxMinor, remote.maxMinor)
        if (lowestSharedMinor > highestSharedMinor) return null
        return StudyFlowLinkProtocolVersion(local.major, highestSharedMinor)
    }
}

@Serializable
@JvmInline
value class StudyFlowWorkspaceId(val value: String) {
    init {
        requireWireToken("workspaceId", value)
    }
}

@Serializable
@JvmInline
value class StudyFlowDeviceId(val value: String) {
    init {
        requireWireToken("deviceId", value)
    }
}

@Serializable
@JvmInline
value class StudyFlowEntityId(val value: String) {
    init {
        requireWireToken("entityId", value)
    }
}

@Serializable
@JvmInline
value class StudyFlowOperationId(val value: String) {
    init {
        requireWireToken("operationId", value)
    }
}

@Serializable
@JvmInline
value class StudyFlowMessageId(val value: String) {
    init {
        requireWireToken("messageId", value)
    }
}

@Serializable
@JvmInline
value class StudyFlowEntityRevision(val value: Long) {
    init {
        require(value >= 0L) { "Entity revision must not be negative" }
    }
}

@Serializable
@JvmInline
value class StudyFlowLinkCapability(val value: String) {
    init {
        require(value.length <= MaxWireTokenLength) { "Capability is too long" }
        require(capabilityPattern.matches(value)) { "Capability must use dotted lower-case tokens" }
    }
}

object StudyFlowLinkCapabilities {
    val WorkspaceRead = capability("workspace.read")
    val WorkspaceWrite = capability("workspace.write")
    val WorkspaceSync = capability("workspace.sync")
    val PlanRead = capability("plan.read")
    val PlanWrite = capability("plan.write")
    val ScheduleRead = capability("schedule.read")
    val ScheduleWrite = capability("schedule.write")
    val SessionExecute = capability("session.execute")
    val SessionControl = capability("session.control")
    val SessionPresent = capability("session.present")
    val QuestionAnswer = capability("question.answer")
    val QuestionAuthor = capability("question.author")
    val ReviewExecute = capability("review.execute")
    val ReviewEdit = capability("review.edit")
    val GoalRead = capability("goal.read")
    val GoalComplete = capability("goal.complete")
    val GoalEdit = capability("goal.edit")
    val LibraryRead = capability("library.read")
    val LibraryImport = capability("library.import")
    val LibraryPresent = capability("library.present")
    val DocumentRead = capability("document.read")
    val DocumentPresent = capability("document.present")
    val WritingRead = capability("writing.read")
    val WritingEdit = capability("writing.edit")
    val AnalyticsRead = capability("analytics.read")
    val BackupExport = capability("backup.export")
    val BackupImport = capability("backup.import")
    val DeviceControl = capability("device.control")
    val DevicePresentationSink = capability("device.presentation_sink")
    val DevicePresentationSource = capability("device.presentation_source")
    val TextInputProvide = capability("text_input.provide")
    val AudioOutput = capability("audio.output")
    val VideoOutput = capability("video.output")
    val TouchInput = capability("touch.input")
    val KeyboardInput = capability("keyboard.input")
    val DpadInput = capability("dpad.input")

    val V1: List<StudyFlowLinkCapability> = listOf(
        WorkspaceRead,
        WorkspaceWrite,
        WorkspaceSync,
        PlanRead,
        PlanWrite,
        ScheduleRead,
        ScheduleWrite,
        SessionExecute,
        SessionControl,
        SessionPresent,
        QuestionAnswer,
        QuestionAuthor,
        ReviewExecute,
        ReviewEdit,
        GoalRead,
        GoalComplete,
        GoalEdit,
        LibraryRead,
        LibraryImport,
        LibraryPresent,
        DocumentRead,
        DocumentPresent,
        WritingRead,
        WritingEdit,
        AnalyticsRead,
        BackupExport,
        BackupImport,
        DeviceControl,
        DevicePresentationSink,
        DevicePresentationSource,
        TextInputProvide,
        AudioOutput,
        VideoOutput,
        TouchInput,
        KeyboardInput,
        DpadInput,
    )

    private fun capability(value: String) = StudyFlowLinkCapability(value)
}

@Serializable
enum class StudyFlowLinkClientKind {
    @SerialName("android")
    ANDROID,

    @SerialName("desktop")
    DESKTOP,

    @SerialName("web")
    WEB,

    @SerialName("android_tv")
    ANDROID_TV,

    @SerialName("web_tv")
    WEB_TV,
}

@Serializable
data class StudyFlowLinkPeerDescriptor(
    val deviceId: StudyFlowDeviceId,
    val displayName: String,
    val clientKind: StudyFlowLinkClientKind,
    val protocolSupport: StudyFlowLinkProtocolSupport = StudyFlowLinkProtocol.CurrentSupport,
    val capabilities: List<StudyFlowLinkCapability>,
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(displayName.length <= MaxDisplayNameLength) { "Display name is too long" }
        require(capabilities.distinct().size == capabilities.size) { "Capabilities must not contain duplicates" }
    }
}

@Serializable
enum class StudyFlowLinkMessageType {
    @SerialName("request")
    REQUEST,

    @SerialName("response")
    RESPONSE,

    @SerialName("event")
    EVENT,

    @SerialName("error")
    ERROR,
}

@Serializable
data class StudyFlowLinkEnvelope(
    val protocol: StudyFlowLinkProtocolVersion = StudyFlowLinkProtocol.Current,
    val messageId: StudyFlowMessageId,
    val workspaceId: StudyFlowWorkspaceId,
    val senderDeviceId: StudyFlowDeviceId,
    @SerialName("type")
    val messageType: StudyFlowLinkMessageType,
    val name: String,
    val sentAtEpochMillis: Long,
    val correlationId: StudyFlowMessageId? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(name.length <= MaxWireTokenLength) { "Message name is too long" }
        require(messageNamePattern.matches(name)) { "Message name must use dotted lower-case tokens" }
        require(sentAtEpochMillis >= 0L) { "Message timestamp must not be negative" }
        when (messageType) {
            StudyFlowLinkMessageType.RESPONSE,
            StudyFlowLinkMessageType.ERROR,
            -> requireNotNull(correlationId) { "Response and error messages require correlationId" }

            StudyFlowLinkMessageType.REQUEST,
            StudyFlowLinkMessageType.EVENT,
            -> require(correlationId == null) { "Request and event messages must not set correlationId" }
        }
    }
}

@Serializable
data class StudyFlowLinkError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(code.length <= MaxWireTokenLength) { "Error code is too long" }
        require(messageNamePattern.matches(code)) { "Error code must use dotted lower-case tokens" }
        require(message.isNotBlank()) { "Error message must not be blank" }
    }
}

object StudyFlowLinkMessageNames {
    const val Hello = "link.hello"
    const val Ping = "link.ping"
    const val CapabilitiesChanged = "link.capabilities_changed"
}

object StudyFlowLinkErrorCodes {
    const val IncompatibleProtocol = "link.incompatible_protocol"
    const val UnsupportedCapability = "link.unsupported_capability"
    const val InvalidMessage = "link.invalid_message"
    const val Unauthorized = "link.unauthorized"
    const val Conflict = "link.conflict"
    const val Unavailable = "link.unavailable"
    const val InternalError = "link.internal_error"
}

private fun requireWireToken(label: String, value: String) {
    require(value.length <= MaxWireTokenLength) { "$label is too long" }
    require(wireTokenPattern.matches(value)) { "$label contains unsupported characters" }
}
