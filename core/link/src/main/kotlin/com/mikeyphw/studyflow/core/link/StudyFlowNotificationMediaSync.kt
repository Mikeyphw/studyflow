package com.mikeyphw.studyflow.core.link

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DefaultNotificationTtlMillis = 86_400_000L
private const val DefaultMaterialOfferTtlMillis = 300_000L
private val notificationMediaWireTokenPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
private val notificationMediaSha256HexPattern = Regex("[a-f0-9]{64}")

@Serializable
@JvmInline
value class StudyFlowNotificationId(val value: String) {
    init {
        require(notificationMediaWireTokenPattern.matches(value)) {
            "Notification ID must be a bounded wire token"
        }
    }
}

@Serializable
@JvmInline
value class StudyFlowScheduleSyncId(val value: String) {
    init {
        require(notificationMediaWireTokenPattern.matches(value)) {
            "Schedule sync ID must be a bounded wire token"
        }
    }
}

@Serializable
@JvmInline
value class StudyFlowMaterialId(val value: String) {
    init { require(notificationMediaWireTokenPattern.matches(value)) { "Material ID must be a bounded wire token" } }
}

@Serializable
@JvmInline
value class StudyFlowMaterialStreamId(val value: String) {
    init {
        require(notificationMediaWireTokenPattern.matches(value)) {
            "Material stream ID must be a bounded wire token"
        }
    }
}

@Serializable
enum class StudyFlowNotificationKind {
    @SerialName("study_due") STUDY_DUE,
    @SerialName("review_due") REVIEW_DUE,
    @SerialName("session_resume") SESSION_RESUME,
    @SerialName("sync_attention") SYNC_ATTENTION,
    @SerialName("handoff_ready") HANDOFF_READY,
    @SerialName("material_ready") MATERIAL_READY,
}

@Serializable
enum class StudyFlowNotificationDeliveryStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("expired") EXPIRED,
}

@Serializable
enum class StudyFlowScheduleSyncChangeKind {
    @SerialName("reminder_upserted") REMINDER_UPSERTED,
    @SerialName("reminder_cancelled") REMINDER_CANCELLED,
    @SerialName("activity_moved") ACTIVITY_MOVED,
    @SerialName("quiet_hours_changed") QUIET_HOURS_CHANGED,
    @SerialName("notification_policy_changed") NOTIFICATION_POLICY_CHANGED,
}

@Serializable
enum class StudyFlowMaterialKind {
    @SerialName("pdf") PDF,
    @SerialName("image") IMAGE,
    @SerialName("audio") AUDIO,
    @SerialName("video") VIDEO,
    @SerialName("html") HTML,
    @SerialName("markdown") MARKDOWN,
    @SerialName("opaque_blob") OPAQUE_BLOB,
}

@Serializable
enum class StudyFlowMaterialChunkStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("rejected") REJECTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("complete") COMPLETE,
}

@Serializable
data class StudyFlowNotificationRoute(
    val workspaceId: StudyFlowWorkspaceId,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val requiredCapabilities: List<StudyFlowLinkCapability> = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
    val allowTvSurfaces: Boolean = true,
) {
    init {
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Notification required capabilities must be unique"
        }
        require(requiredCapabilities.isNotEmpty()) { "Notification route must require at least one capability" }
    }

    fun accepts(target: StudyFlowLinkPeerDescriptor): Boolean {
        if (targetDeviceId != null && target.deviceId != targetDeviceId) return false
        if (
            !allowTvSurfaces &&
            target.clientKind in listOf(StudyFlowLinkClientKind.ANDROID_TV, StudyFlowLinkClientKind.WEB_TV)
        ) {
            return false
        }
        return requiredCapabilities.all { it in target.capabilities }
    }
}

@Serializable
data class StudyFlowNotificationPayload(
    val notificationId: StudyFlowNotificationId,
    val kind: StudyFlowNotificationKind,
    val title: String,
    val body: String,
    val route: StudyFlowNotificationRoute,
    val scheduleActivityId: StudyFlowActivityId? = null,
    val dueAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultNotificationTtlMillis,
    val attentionOccurrence: StudyFlowAttentionOccurrence? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(title.isNotBlank()) { "Notification title must not be blank" }
        require(body.isNotBlank()) { "Notification body must not be blank" }
        require(createdAtEpochMillis >= 0L) { "Notification creation time must not be negative" }
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L) { "Notification due time must not be negative" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Notification expiry must be after creation" }
        require(attentionOccurrence == null || attentionOccurrence.workspaceId == route.workspaceId) {
            "Notification attention occurrence must use the routed workspace"
        }
        require(
            attentionOccurrence?.scheduleEntityId == null ||
                scheduleActivityId == null ||
                attentionOccurrence.scheduleEntityId.value == scheduleActivityId.value,
        ) {
            "Notification attention occurrence must match the scheduled activity"
        }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis

    fun canonicalAttentionOccurrence(sourceDeviceId: StudyFlowDeviceId): StudyFlowAttentionOccurrence =
        attentionOccurrence ?: StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId.deterministic(
                "notification",
                notificationId.value,
            ),
            workspaceId = route.workspaceId,
            scheduleEntityId = scheduleActivityId?.let { StudyFlowEntityId(it.value) },
            category = when (kind) {
                StudyFlowNotificationKind.STUDY_DUE -> StudyFlowAttentionCategory.PLANNED_STUDY
                StudyFlowNotificationKind.REVIEW_DUE -> StudyFlowAttentionCategory.DUE_REVIEW
                StudyFlowNotificationKind.SESSION_RESUME -> StudyFlowAttentionCategory.SESSION_ENDING
                StudyFlowNotificationKind.SYNC_ATTENTION -> StudyFlowAttentionCategory.SYNC_ATTENTION
                StudyFlowNotificationKind.HANDOFF_READY -> StudyFlowAttentionCategory.SYNC_ATTENTION
                StudyFlowNotificationKind.MATERIAL_READY -> StudyFlowAttentionCategory.SYNC_ATTENTION
            },
            scheduledAtEpochMillis = dueAtEpochMillis ?: createdAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            sourceDeviceId = sourceDeviceId,
            targetDeviceIds = listOfNotNull(route.targetDeviceId),
            payload = payload,
        )
}

@Serializable
data class StudyFlowNotificationDelivery(
    val messageId: StudyFlowMessageId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val notification: StudyFlowNotificationPayload,
    val sentAtEpochMillis: Long,
) {
    init {
        require(sequence >= 1L) { "Notification delivery sequence must be positive" }
        require(sourceDeviceId != targetDeviceId) { "Notification delivery cannot target the source device" }
        require(sentAtEpochMillis >= 0L) { "Notification delivery time must not be negative" }
    }
}

@Serializable
data class StudyFlowNotificationDeliveryAck(
    val messageId: StudyFlowMessageId,
    val notificationId: StudyFlowNotificationId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedSequence: Long,
    val status: StudyFlowNotificationDeliveryStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedSequence >= 1L) { "Notification acknowledgement sequence must be positive" }
        require(acknowledgedAtEpochMillis >= 0L) { "Notification acknowledgement time must not be negative" }
        require(status != StudyFlowNotificationDeliveryStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected notification deliveries require a reason"
        }
    }
}

@Serializable
data class StudyFlowScheduleReminderChange(
    val changeId: StudyFlowScheduleSyncId,
    val workspaceId: StudyFlowWorkspaceId,
    val sourceDeviceId: StudyFlowDeviceId,
    val sequence: Long,
    val scheduleEntityId: StudyFlowEntityId,
    val changeKind: StudyFlowScheduleSyncChangeKind,
    val reminderAtEpochMillis: Long? = null,
    val quietHoursStartMinute: Int? = null,
    val quietHoursEndMinute: Int? = null,
    val occurredAtEpochMillis: Long,
    val attentionOccurrence: StudyFlowAttentionOccurrence? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(sequence >= 1L) { "Schedule sync sequence must be positive" }
        require(reminderAtEpochMillis == null || reminderAtEpochMillis >= 0L) { "Reminder time must not be negative" }
        require(quietHoursStartMinute == null || quietHoursStartMinute in 0..1439) {
            "Quiet-hour start minute out of range"
        }
        require(quietHoursEndMinute == null || quietHoursEndMinute in 0..1439) {
            "Quiet-hour end minute out of range"
        }
        require(occurredAtEpochMillis >= 0L) { "Schedule sync time must not be negative" }
        require(attentionOccurrence == null || attentionOccurrence.workspaceId == workspaceId) {
            "Schedule attention occurrence must use the changed workspace"
        }
        require(
            attentionOccurrence?.scheduleEntityId == null ||
                attentionOccurrence.scheduleEntityId == scheduleEntityId,
        ) {
            "Schedule attention occurrence must match the changed entity"
        }
    }

    fun canonicalAttentionOccurrence(): StudyFlowAttentionOccurrence? {
        attentionOccurrence?.let { return it }
        val reminderAt = reminderAtEpochMillis ?: return null
        val quietHours = if (quietHoursStartMinute != null && quietHoursEndMinute != null) {
            StudyFlowAttentionQuietHours(quietHoursStartMinute, quietHoursEndMinute)
        } else {
            null
        }
        return StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId.deterministic(
                "schedule",
                scheduleEntityId.value,
                reminderAt.toString(),
            ),
            workspaceId = workspaceId,
            scheduleEntityId = scheduleEntityId,
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = reminderAt,
            quietHours = quietHours,
            sourceDeviceId = sourceDeviceId,
            payload = payload,
        )
    }

    fun toDomainOperation(
        entityRevision: StudyFlowEntityRevision,
        logicalTimestamp: Long,
    ): StudyFlowDomainOperation = StudyFlowDomainOperation(
        operationId = StudyFlowOperationId("op-${changeId.value}"),
        workspaceId = workspaceId,
        originDeviceId = sourceDeviceId,
        originSequence = sequence,
        entityId = scheduleEntityId,
        entityRevision = entityRevision,
        logicalTimestamp = logicalTimestamp,
        operationType = when (changeKind) {
            StudyFlowScheduleSyncChangeKind.REMINDER_UPSERTED ->
                StudyFlowDomainOperationTypes.ScheduleReminderUpserted
            StudyFlowScheduleSyncChangeKind.REMINDER_CANCELLED ->
                StudyFlowDomainOperationTypes.ScheduleReminderCancelled
            StudyFlowScheduleSyncChangeKind.ACTIVITY_MOVED -> StudyFlowDomainOperationTypes.ScheduleActivityMoved
            StudyFlowScheduleSyncChangeKind.QUIET_HOURS_CHANGED ->
                StudyFlowDomainOperationTypes.NotificationQuietHoursChanged
            StudyFlowScheduleSyncChangeKind.NOTIFICATION_POLICY_CHANGED ->
                StudyFlowDomainOperationTypes.NotificationPolicyChanged
        },
        occurredAtEpochMillis = occurredAtEpochMillis,
        payload = JsonObject(
            mapOf(
                "scheduleSyncId" to JsonPrimitive(changeId.value),
                "changeKind" to JsonPrimitive(changeKind.name.lowercase()),
                "notificationScheduleSync" to JsonPrimitive("sflp18"),
                "attentionSchema" to JsonPrimitive("v1"),
                "attentionScheduleChange" to StudyFlowAttentionWireJson.encodeToJsonElement(
                    StudyFlowScheduleReminderChange.serializer(),
                    this,
                ),
            ),
        ),
    )

    companion object {
        fun fromDomainOperation(operation: StudyFlowDomainOperation): StudyFlowScheduleReminderChange? {
            val encoded = operation.payload["attentionScheduleChange"] ?: return null
            return runCatching {
                StudyFlowAttentionWireJson.decodeFromJsonElement(StudyFlowScheduleReminderChange.serializer(), encoded)
            }.getOrNull()
        }
    }
}

@Serializable
data class StudyFlowMaterialStreamOffer(
    val streamId: StudyFlowMaterialStreamId,
    val workspaceId: StudyFlowWorkspaceId,
    val materialId: StudyFlowMaterialId,
    val sourceDevice: StudyFlowLinkPeerDescriptor,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val materialKind: StudyFlowMaterialKind,
    val title: String,
    val mimeType: String,
    val totalBytes: Long,
    val chunkSize: Int,
    val contentSha256: String,
    val requiredCapabilities: List<StudyFlowLinkCapability> = listOf(StudyFlowLinkCapabilities.LibraryRead),
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long = createdAtEpochMillis + DefaultMaterialOfferTtlMillis,
) {
    init {
        require(title.isNotBlank()) { "Material stream title must not be blank" }
        require(mimeType.contains('/')) { "Material stream MIME type must be explicit" }
        require(totalBytes >= 0L) { "Material stream total bytes must not be negative" }
        require(chunkSize in 1..1_048_576) { "Material stream chunk size must be bounded" }
        require(notificationMediaSha256HexPattern.matches(contentSha256)) {
            "Material stream content hash must be lowercase SHA-256 hex"
        }
        require(requiredCapabilities.distinct().size == requiredCapabilities.size) {
            "Material stream capabilities must be unique"
        }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Material stream offer must expire after creation" }
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis > expiresAtEpochMillis

    fun accepts(target: StudyFlowLinkPeerDescriptor, nowEpochMillis: Long): Boolean {
        if (isExpired(nowEpochMillis)) return false
        if (targetDeviceId != null && target.deviceId != targetDeviceId) return false
        if (requiredCapabilities.any { it.value.endsWith(".write") || it == StudyFlowLinkCapabilities.LibraryImport }) {
            return false
        }
        return requiredCapabilities.all { it in target.capabilities }
    }
}

@Serializable
data class StudyFlowMaterialChunk(
    val messageId: StudyFlowMessageId,
    val streamId: StudyFlowMaterialStreamId,
    val sourceDeviceId: StudyFlowDeviceId,
    val targetDeviceId: StudyFlowDeviceId,
    val chunkIndex: Int,
    val byteOffset: Long,
    val bytesBase64: String,
    val chunkSha256: String,
    val finalChunk: Boolean = false,
    val sentAtEpochMillis: Long,
) {
    init {
        require(chunkIndex >= 0) { "Material chunk index must not be negative" }
        require(byteOffset >= 0L) { "Material chunk byte offset must not be negative" }
        require(bytesBase64.isNotBlank()) { "Material chunk payload must not be blank" }
        require(notificationMediaSha256HexPattern.matches(chunkSha256)) {
            "Material chunk hash must be lowercase SHA-256 hex"
        }
        require(sourceDeviceId != targetDeviceId) { "Material chunk cannot target the source device" }
        require(sentAtEpochMillis >= 0L) { "Material chunk sent time must not be negative" }
    }
}

@Serializable
data class StudyFlowMaterialChunkAck(
    val messageId: StudyFlowMessageId,
    val streamId: StudyFlowMaterialStreamId,
    val targetDeviceId: StudyFlowDeviceId,
    val acknowledgedChunkIndex: Int,
    val acknowledgedByteOffset: Long,
    val status: StudyFlowMaterialChunkStatus,
    val reason: String? = null,
    val acknowledgedAtEpochMillis: Long,
) {
    init {
        require(acknowledgedChunkIndex >= 0) { "Material chunk ack index must not be negative" }
        require(acknowledgedByteOffset >= 0L) { "Material chunk ack offset must not be negative" }
        require(acknowledgedAtEpochMillis >= 0L) { "Material chunk ack time must not be negative" }
        require(status != StudyFlowMaterialChunkStatus.REJECTED || !reason.isNullOrBlank()) {
            "Rejected material chunks require a reason"
        }
    }
}

object StudyFlowNotificationMediaMessageNames {
    const val NotificationDeliver = "notification.deliver"
    const val NotificationAck = "notification.delivery_ack"
    const val ScheduleChange = "schedule.notification_change"
    const val MaterialOffer = "material.stream_offer"
    const val MaterialChunk = "material.chunk"
    const val MaterialChunkAck = "material.chunk_ack"
}

class StudyFlowNotificationScheduleReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
    private val attentionLedger: StudyFlowAttentionLedger = StudyFlowAttentionLedger(),
) {
    fun apply(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck = synchronized(this) {
        if (delivery.targetDeviceId != localDevice.deviceId) {
            return ack(delivery, StudyFlowNotificationDeliveryStatus.REJECTED, "wrong_target")
        }
        if (!delivery.notification.route.accepts(localDevice)) {
            return ack(delivery, StudyFlowNotificationDeliveryStatus.REJECTED, "missing_capability")
        }
        val now = clock.nowEpochMillis()
        if (delivery.notification.isExpired(now)) {
            return ack(delivery, StudyFlowNotificationDeliveryStatus.EXPIRED, "expired")
        }
        val occurrence = delivery.notification.canonicalAttentionOccurrence(delivery.sourceDeviceId)
        return when (
            attentionLedger.admitInboundDelivery(
                occurrence = occurrence,
                sourceDeviceId = delivery.sourceDeviceId,
                targetDeviceId = delivery.targetDeviceId,
                sequence = delivery.sequence,
                deliveredAtEpochMillis = now,
            )
        ) {
            StudyFlowAttentionLedgerAdmission.ACCEPTED ->
                ack(delivery, StudyFlowNotificationDeliveryStatus.ACCEPTED, null)
            StudyFlowAttentionLedgerAdmission.DUPLICATE ->
                ack(delivery, StudyFlowNotificationDeliveryStatus.DUPLICATE, null)
            StudyFlowAttentionLedgerAdmission.STALE_SEQUENCE ->
                ack(delivery, StudyFlowNotificationDeliveryStatus.REJECTED, "stale_sequence")
        }
    }

    fun attentionDiagnostics(): StudyFlowAttentionDiagnosticsSnapshot = attentionLedger.diagnostics()

    private fun ack(
        delivery: StudyFlowNotificationDelivery,
        status: StudyFlowNotificationDeliveryStatus,
        reason: String?,
    ) = StudyFlowNotificationDeliveryAck(
        messageId = StudyFlowMessageId("ack-${delivery.messageId.value}"),
        notificationId = delivery.notification.notificationId,
        targetDeviceId = localDevice.deviceId,
        acknowledgedSequence = delivery.sequence,
        status = status,
        reason = reason,
        acknowledgedAtEpochMillis = clock.nowEpochMillis(),
    )
}

class StudyFlowMaterialStreamReducer(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val acceptedOffers = linkedMapOf<StudyFlowMaterialStreamId, StudyFlowMaterialStreamOffer>()
    private val receivedChunks = linkedMapOf<StudyFlowMaterialStreamId, LinkedHashSet<Int>>()

    fun accept(offer: StudyFlowMaterialStreamOffer): Boolean = synchronized(this) {
        if (!offer.accepts(localDevice, clock.nowEpochMillis())) return false
        acceptedOffers[offer.streamId] = offer
        receivedChunks.getOrPut(offer.streamId) { linkedSetOf() }
        true
    }

    fun apply(chunk: StudyFlowMaterialChunk): StudyFlowMaterialChunkAck = synchronized(this) {
        val offer = acceptedOffers[chunk.streamId]
            ?: return ack(chunk, StudyFlowMaterialChunkStatus.REJECTED, "unknown_stream")
        if (chunk.targetDeviceId != localDevice.deviceId) {
            return ack(chunk, StudyFlowMaterialChunkStatus.REJECTED, "wrong_target")
        }
        if (chunk.sourceDeviceId != offer.sourceDevice.deviceId) {
            return ack(chunk, StudyFlowMaterialChunkStatus.REJECTED, "wrong_source")
        }
        if (chunk.byteOffset != chunk.chunkIndex.toLong() * offer.chunkSize.toLong()) {
            return ack(chunk, StudyFlowMaterialChunkStatus.REJECTED, "unexpected_offset")
        }
        val chunks = receivedChunks.getOrPut(chunk.streamId) { linkedSetOf() }
        if (chunk.chunkIndex in chunks) {
            return ack(chunk, StudyFlowMaterialChunkStatus.DUPLICATE, null)
        }
        chunks += chunk.chunkIndex
        val status = if (chunk.finalChunk) {
            StudyFlowMaterialChunkStatus.COMPLETE
        } else {
            StudyFlowMaterialChunkStatus.ACCEPTED
        }
        return ack(chunk, status, null)
    }

    private fun ack(
        chunk: StudyFlowMaterialChunk,
        status: StudyFlowMaterialChunkStatus,
        reason: String?,
    ) = StudyFlowMaterialChunkAck(
        messageId = StudyFlowMessageId("ack-${chunk.messageId.value}"),
        streamId = chunk.streamId,
        targetDeviceId = localDevice.deviceId,
        acknowledgedChunkIndex = chunk.chunkIndex,
        acknowledgedByteOffset = chunk.byteOffset,
        status = status,
        reason = reason,
        acknowledgedAtEpochMillis = clock.nowEpochMillis(),
    )

    companion object {
        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
