package com.mikeyphw.studyflow.reminders

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContent
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.StudyPlanId

private const val ReminderOccurrencePreferences = "studyflow_reminder_occurrences_v2"
private const val FieldSeparator = '\u001f'
private const val MaxDeliveredOccurrenceIds = 256
private const val NewEncodingFieldCount = 11

data class ScheduledReminderOccurrence(
    val type: ReminderNotificationType,
    val occurrenceId: String,
    val scheduledAtEpochMillis: Long,
    val fireAtEpochMillis: Long,
    val contentKind: ReminderNotificationContentKind = type.defaultContentKind(),
    val contentCount: Int? = null,
    val userLabel: String? = null,
    val targetId: String? = null,
    val snoozedFromOccurrenceId: String? = null,
    val delayedByQuietHours: Boolean = false,
    val notificationId: Int = stableNotificationId(occurrenceId),
) {
    fun toCandidate(): ReminderNotificationCandidate = ReminderNotificationCandidate(
        type = type,
        occurrenceId = occurrenceId,
        content = ReminderNotificationContent(
            kind = contentKind,
            count = contentCount,
            userLabel = userLabel,
            targetId = targetId,
        ),
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        fireAtEpochMillis = fireAtEpochMillis,
        delayedByQuietHours = delayedByQuietHours,
    )
}

class StudyReminderOccurrenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ReminderOccurrencePreferences,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun scheduled(planId: StudyPlanId): List<ScheduledReminderOccurrence> =
        preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty().mapNotNull(::decodeScheduled)

    @Synchronized
    fun replaceScheduled(planId: StudyPlanId, candidates: Collection<ReminderNotificationCandidate>) {
        val existingSnoozes = scheduled(planId).filter { it.snoozedFromOccurrenceId != null }
        val encoded = candidates.mapTo(linkedSetOf()) { encodeScheduled(it.toOccurrence()) }
        existingSnoozes.forEach { encoded += encodeScheduled(it) }
        preferences.edit(commit = true) { putStringSet(scheduledKey(planId), encoded) }
    }

    @Synchronized
    fun addScheduled(
        planId: StudyPlanId,
        candidate: ReminderNotificationCandidate,
        snoozedFromOccurrenceId: String? = null,
    ) {
        val encoded = LinkedHashSet(preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty())
        encoded.removeAll { value -> decodeScheduled(value)?.occurrenceId == candidate.occurrenceId }
        encoded += encodeScheduled(candidate.toOccurrence(snoozedFromOccurrenceId))
        preferences.edit(commit = true) { putStringSet(scheduledKey(planId), encoded) }
    }

    @Synchronized
    fun removeScheduled(planId: StudyPlanId, occurrenceId: String) {
        val encoded = preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty()
            .filterTo(linkedSetOf()) { value -> decodeScheduled(value)?.occurrenceId != occurrenceId }
        preferences.edit(commit = true) {
            if (encoded.isEmpty()) remove(scheduledKey(planId)) else putStringSet(scheduledKey(planId), encoded)
        }
    }

    @Synchronized
    fun clearScheduled(planId: StudyPlanId) {
        preferences.edit(commit = true) { remove(scheduledKey(planId)) }
    }

    @Synchronized
    fun deliveredOccurrenceIds(planId: StudyPlanId): Set<String> = LinkedHashSet(loadDeliveredHistory(planId))

    @Synchronized
    fun markDelivered(planId: StudyPlanId, occurrenceId: String) {
        val delivered = DeliveredReminderHistory.append(
            orderedOccurrenceIds = loadDeliveredHistory(planId),
            occurrenceId = occurrenceId,
            maxSize = MaxDeliveredOccurrenceIds,
        )
        persistDeliveredHistory(planId, delivered)
    }

    @Synchronized
    fun removeDelivered(planId: StudyPlanId, occurrenceId: String) {
        val current = loadDeliveredHistory(planId)
        if (occurrenceId !in current) return
        persistDeliveredHistory(planId, DeliveredReminderHistory.remove(current, occurrenceId))
    }

    @Synchronized
    fun markProductionPipelineCanary(planId: StudyPlanId, verifiedAtEpochMillis: Long) {
        preferences.edit(commit = true) { putLong(canaryKey(planId), verifiedAtEpochMillis) }
    }

    @Synchronized
    fun productionPipelineCanaryAt(planId: StudyPlanId): Long? =
        preferences.getLong(canaryKey(planId), -1L).takeIf { it >= 0L }

    @Synchronized
    fun clearPlan(planId: StudyPlanId) {
        preferences.edit(commit = true) {
            remove(scheduledKey(planId))
            remove(deliveredKey(planId))
            remove(deliveredOrderKey(planId))
            remove(canaryKey(planId))
        }
    }

    private fun loadDeliveredHistory(planId: StudyPlanId): List<String> {
        val orderedKey = deliveredOrderKey(planId)
        if (preferences.contains(orderedKey)) {
            val decoded = preferences.getString(orderedKey, null)?.let(DeliveredReminderHistory::decode)
            if (decoded != null) return decoded.takeLast(MaxDeliveredOccurrenceIds)
        }
        val legacy = preferences.getStringSet(deliveredKey(planId), emptySet()).orEmpty().toSet()
        if (legacy.isEmpty()) return emptyList()
        val migrated = DeliveredReminderHistory.migrateLegacy(legacy, MaxDeliveredOccurrenceIds)
        persistDeliveredHistory(planId, migrated)
        return migrated
    }

    private fun persistDeliveredHistory(planId: StudyPlanId, delivered: List<String>) {
        preferences.edit(commit = true) {
            if (delivered.isEmpty()) {
                remove(deliveredOrderKey(planId))
                remove(deliveredKey(planId))
            } else {
                putString(deliveredOrderKey(planId), DeliveredReminderHistory.encode(delivered))
                putStringSet(deliveredKey(planId), LinkedHashSet(delivered))
            }
        }
    }

    private fun scheduledKey(planId: StudyPlanId): String = "scheduled:${planId.value}"
    private fun deliveredKey(planId: StudyPlanId): String = "delivered:${planId.value}"
    private fun deliveredOrderKey(planId: StudyPlanId): String = "delivered-order:${planId.value}"
    private fun canaryKey(planId: StudyPlanId): String = "pipeline-canary:${planId.value}"

    private fun encodeScheduled(value: ScheduledReminderOccurrence): String = listOf(
        value.type.name,
        value.occurrenceId,
        value.scheduledAtEpochMillis.toString(),
        value.fireAtEpochMillis.toString(),
        value.contentKind.name,
        value.contentCount?.toString().orEmpty(),
        encodeNullable(value.userLabel),
        encodeNullable(value.targetId),
        encodeNullable(value.snoozedFromOccurrenceId),
        value.delayedByQuietHours.toString(),
        value.notificationId.toString(),
    ).joinToString(FieldSeparator.toString())

    private fun decodeScheduled(value: String): ScheduledReminderOccurrence? {
        val parts = value.split(FieldSeparator)
        if (parts.size == 4) {
            val type = ReminderNotificationType.entries.firstOrNull { it.name == parts[0] } ?: return null
            return ScheduledReminderOccurrence(
                type = type,
                occurrenceId = parts[1],
                scheduledAtEpochMillis = parts[2].toLongOrNull() ?: return null,
                fireAtEpochMillis = parts[3].toLongOrNull() ?: return null,
            )
        }
        if (parts.size != NewEncodingFieldCount) return null
        val type = ReminderNotificationType.entries.firstOrNull { it.name == parts[0] } ?: return null
        val kind = ReminderNotificationContentKind.entries.firstOrNull { it.name == parts[4] }
            ?: type.defaultContentKind()
        return ScheduledReminderOccurrence(
            type = type,
            occurrenceId = parts[1],
            scheduledAtEpochMillis = parts[2].toLongOrNull() ?: return null,
            fireAtEpochMillis = parts[3].toLongOrNull() ?: return null,
            contentKind = kind,
            contentCount = parts[5].toIntOrNull(),
            userLabel = decodeNullable(parts[6]),
            targetId = decodeNullable(parts[7]),
            snoozedFromOccurrenceId = decodeNullable(parts[8]),
            delayedByQuietHours = parts[9].toBooleanStrictOrNull() ?: false,
            notificationId = parts[10].toIntOrNull() ?: stableNotificationId(parts[1]),
        )
    }
}

private fun ReminderNotificationCandidate.toOccurrence(
    snoozedFromOccurrenceId: String? = null,
): ScheduledReminderOccurrence = ScheduledReminderOccurrence(
    type = type,
    occurrenceId = occurrenceId,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    fireAtEpochMillis = fireAtEpochMillis,
    contentKind = content.kind,
    contentCount = content.count,
    userLabel = content.userLabel,
    targetId = content.targetId,
    snoozedFromOccurrenceId = snoozedFromOccurrenceId,
    delayedByQuietHours = delayedByQuietHours,
)

internal fun ReminderNotificationType.defaultContentKind(): ReminderNotificationContentKind = when (this) {
    ReminderNotificationType.DUE_REVIEWS -> ReminderNotificationContentKind.DUE_REVIEWS
    ReminderNotificationType.PLANNED_ACTIVITY -> ReminderNotificationContentKind.PLANNED_ACTIVITY
    ReminderNotificationType.MISSED_ACTIVITY -> ReminderNotificationContentKind.MISSED_ACTIVITY
    ReminderNotificationType.DAILY_SUMMARY -> ReminderNotificationContentKind.DAILY_SUMMARY
    ReminderNotificationType.SESSION_ENDING -> ReminderNotificationContentKind.SESSION_ENDING
    ReminderNotificationType.SESSION_COMPLETED -> ReminderNotificationContentKind.SESSION_COMPLETED
    ReminderNotificationType.EXAM_COUNTDOWN -> ReminderNotificationContentKind.EXAM_COUNTDOWN
}

internal fun stableNotificationId(occurrenceId: String): Int = occurrenceId.hashCode() and 0x7fffffff

private fun encodeNullable(value: String?): String = value?.let {
    Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
}.orEmpty()

private fun decodeNullable(value: String): String? = value.takeIf(String::isNotEmpty)?.let {
    runCatching {
        String(Base64.decode(it, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
    }.getOrNull()
}
