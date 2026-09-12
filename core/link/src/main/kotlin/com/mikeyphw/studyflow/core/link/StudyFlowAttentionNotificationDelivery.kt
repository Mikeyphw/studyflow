package com.mikeyphw.studyflow.core.link

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.serialization.json.JsonObject

/** Converts an ATT01/ATT07 occurrence into the authenticated SFLP18 notification envelope. */
object StudyFlowAttentionNotificationDeliveryFactory {
    private val lastSequence = AtomicLong(0L)

    private fun nextSequence(nowEpochMillis: Long): Long {
        while (true) {
            val previous = lastSequence.get()
            val next = max(max(1L, nowEpochMillis), previous + 1L)
            if (lastSequence.compareAndSet(previous, next)) return next
        }
    }

    fun create(
        occurrence: StudyFlowAttentionOccurrence,
        sourceDeviceId: StudyFlowDeviceId,
        targetDeviceId: StudyFlowDeviceId,
        title: String,
        body: String,
        nowEpochMillis: Long,
        deliveryRoute: StudyFlowAttentionDeliveryRoute? = null,
        payload: JsonObject = occurrence.payload,
    ): StudyFlowNotificationDelivery {
        require(sourceDeviceId != targetDeviceId) { "Remote attention delivery requires another device" }
        require(deliveryRoute == null || deliveryRoute.occurrenceId == occurrence.occurrenceId) {
            "Remote attention route must match the occurrence"
        }
        require(deliveryRoute == null || targetDeviceId in deliveryRoute.targetDeviceIds) {
            "Remote attention target must be selected by ATT07"
        }
        val stable = UUID.nameUUIDFromBytes(
            (
                "${occurrence.occurrenceId.value}|${sourceDeviceId.value}|${targetDeviceId.value}"
            ).toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expires = max(occurrence.expiresAtEpochMillis ?: (nowEpochMillis + 86_400_000L), nowEpochMillis + 1L)
        return StudyFlowNotificationDelivery(
            messageId = StudyFlowMessageId("att-$stable"),
            sourceDeviceId = sourceDeviceId,
            targetDeviceId = targetDeviceId,
            sequence = nextSequence(nowEpochMillis),
            notification = StudyFlowNotificationPayload(
                notificationId = StudyFlowNotificationId("att-$stable"),
                kind = occurrence.category.notificationKind(),
                title = title.ifBlank { "StudyFlow" },
                body = body.ifBlank { "StudyFlow needs your attention." },
                route = StudyFlowNotificationRoute(
                    workspaceId = occurrence.workspaceId,
                    targetDeviceId = targetDeviceId,
                ),
                scheduleActivityId = occurrence.scheduleEntityId?.let { StudyFlowActivityId(it.value) },
                dueAtEpochMillis = occurrence.scheduledAtEpochMillis,
                createdAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = expires,
                attentionOccurrence = occurrence.copy(
                    sourceDeviceId = sourceDeviceId,
                    targetDeviceIds = listOf(targetDeviceId),
                ),
                attentionRoute = deliveryRoute,
                payload = payload,
            ),
            sentAtEpochMillis = nowEpochMillis,
        )
    }

    private fun StudyFlowAttentionCategory.notificationKind(): StudyFlowNotificationKind = when (this) {
        StudyFlowAttentionCategory.DUE_REVIEW -> StudyFlowNotificationKind.REVIEW_DUE
        StudyFlowAttentionCategory.PLANNED_STUDY,
        StudyFlowAttentionCategory.MISSED_ACTIVITY,
        StudyFlowAttentionCategory.DAILY_SUMMARY,
        StudyFlowAttentionCategory.EXAM_COUNTDOWN,
        -> StudyFlowNotificationKind.STUDY_DUE
        StudyFlowAttentionCategory.SESSION_ENDING,
        StudyFlowAttentionCategory.SESSION_COMPLETED,
        -> StudyFlowNotificationKind.SESSION_RESUME
        StudyFlowAttentionCategory.SCHEDULE_CHANGE,
        StudyFlowAttentionCategory.PLAN_WARNING,
        StudyFlowAttentionCategory.SYNC_ATTENTION,
        -> StudyFlowNotificationKind.SYNC_ATTENTION
    }
}
