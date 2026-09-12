package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class StudyFlowNotificationMediaSyncTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")

    @Test
    fun notificationDeliveryIsTargetedMonotonicAndDuplicateSafe() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val reducer = StudyFlowNotificationScheduleReducer(desktop, StudyFlowPeerClock { 2_000L })
        val payload = StudyFlowNotificationPayload(
            notificationId = StudyFlowNotificationId("notification-review-1"),
            kind = StudyFlowNotificationKind.REVIEW_DUE,
            title = "Review due",
            body = "Portuguese vocabulary review is ready.",
            route = StudyFlowNotificationRoute(workspace, targetDeviceId = desktop.deviceId),
            scheduleActivityId = StudyFlowActivityId("activity-review-1"),
            createdAtEpochMillis = 1_000L,
        )
        val delivery = StudyFlowNotificationDelivery(
            messageId = StudyFlowMessageId("notification-message-1"),
            sourceDeviceId = phone.deviceId,
            targetDeviceId = desktop.deviceId,
            sequence = 1,
            notification = payload,
            sentAtEpochMillis = 1_001L,
        )
        assertEquals(StudyFlowNotificationDeliveryStatus.ACCEPTED, reducer.apply(delivery).status)
        assertEquals(
            StudyFlowNotificationDeliveryStatus.DUPLICATE,
            reducer.apply(delivery.copy(messageId = StudyFlowMessageId("notification-message-2"))).status,
        )
    }

    @Test
    fun scheduleReminderChangeBecomesCanonicalDomainOperation() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("planned:schedule-item-1:5000"),
            workspaceId = workspace,
            scheduleEntityId = StudyFlowEntityId("schedule-item-1"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            priority = StudyFlowAttentionPriority.HIGH,
            scheduledAtEpochMillis = 5_000L,
            originalScheduledAtEpochMillis = 4_500L,
            expiresAtEpochMillis = 10_000L,
            timezoneId = "America/Sao_Paulo",
            quietHours = StudyFlowAttentionQuietHours(22 * 60, 7 * 60),
            deliveryPolicy = StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY,
            actions = listOf(StudyFlowAttentionAction(StudyFlowAttentionActionKind.START_NOW)),
            sourceDeviceId = phone.deviceId,
            payload = JsonObject(mapOf("planId" to JsonPrimitive("plan-main"))),
        )
        val change = StudyFlowScheduleReminderChange(
            changeId = StudyFlowScheduleSyncId("schedule-change-1"),
            workspaceId = workspace,
            sourceDeviceId = phone.deviceId,
            sequence = 7,
            scheduleEntityId = StudyFlowEntityId("schedule-item-1"),
            changeKind = StudyFlowScheduleSyncChangeKind.REMINDER_UPSERTED,
            reminderAtEpochMillis = 5_000L,
            quietHoursStartMinute = 22 * 60,
            quietHoursEndMinute = 7 * 60,
            occurredAtEpochMillis = 4_000L,
            attentionOccurrence = occurrence,
            payload = JsonObject(mapOf("legacyKey" to JsonPrimitive("legacy-value"))),
        )
        val op = change.toDomainOperation(StudyFlowEntityRevision(2), logicalTimestamp = 9)
        assertEquals(StudyFlowDomainOperationTypes.ScheduleReminderUpserted, op.operationType)
        assertEquals(7, op.originSequence)
        assertEquals("sflp18", op.payload["notificationScheduleSync"]?.toString()?.trim('"'))
        assertEquals("v1", op.payload["attentionSchema"]?.toString()?.trim('"'))
        assertEquals(change, StudyFlowScheduleReminderChange.fromDomainOperation(op))
        assertEquals(occurrence, change.canonicalAttentionOccurrence())
    }

    @Test
    fun materialStreamingOfferIsReadOnlyAndChunkAcknowledged() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val reducer = StudyFlowMaterialStreamReducer(tv, StudyFlowPeerClock { 2_000L })
        val offer = StudyFlowMaterialStreamOffer(
            streamId = StudyFlowMaterialStreamId("stream-material-1"),
            workspaceId = workspace,
            materialId = StudyFlowMaterialId("material-1"),
            sourceDevice = desktop,
            targetDeviceId = tv.deviceId,
            materialKind = StudyFlowMaterialKind.PDF,
            title = "Biology notes",
            mimeType = "application/pdf",
            totalBytes = 6L,
            chunkSize = 3,
            contentSha256 = "0".repeat(64),
            requiredCapabilities = listOf(
                StudyFlowLinkCapabilities.LibraryRead,
                StudyFlowLinkCapabilities.DocumentPresent,
            ),
            createdAtEpochMillis = 1_000L,
        )
        assertTrue(reducer.accept(offer))
        val chunk = StudyFlowMaterialChunk(
            messageId = StudyFlowMessageId("chunk-message-1"),
            streamId = offer.streamId,
            sourceDeviceId = desktop.deviceId,
            targetDeviceId = tv.deviceId,
            chunkIndex = 0,
            byteOffset = 0L,
            bytesBase64 = "QUJD",
            chunkSha256 = "1".repeat(64),
            sentAtEpochMillis = 1_100L,
        )
        assertEquals(StudyFlowMaterialChunkStatus.ACCEPTED, reducer.apply(chunk).status)
        assertEquals(
            StudyFlowMaterialChunkStatus.DUPLICATE,
            reducer.apply(chunk.copy(messageId = StudyFlowMessageId("chunk-message-2"))).status,
        )
    }

    @Test
    fun tvRejectsMaterialOffersThatRequireWrites() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.WEB_TV, StudyFlowClientProfiles.WebTv)
        val offer = StudyFlowMaterialStreamOffer(
            streamId = StudyFlowMaterialStreamId("stream-material-2"),
            workspaceId = workspace,
            materialId = StudyFlowMaterialId("material-2"),
            sourceDevice = desktop,
            targetDeviceId = tv.deviceId,
            materialKind = StudyFlowMaterialKind.HTML,
            title = "Unsafe import",
            mimeType = "text/html",
            totalBytes = 12L,
            chunkSize = 6,
            contentSha256 = "2".repeat(64),
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.LibraryImport),
            createdAtEpochMillis = 1_000L,
        )
        assertFalse(offer.accepts(tv, nowEpochMillis = 2_000L))
    }

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = capabilities,
    )
}
