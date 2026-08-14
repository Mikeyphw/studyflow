package com.mikeyphw.studyflow.reminders

import android.content.Context
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.StudyPlanId

private const val ReminderOccurrencePreferences = "studyflow_reminder_occurrences_v2"
private const val FieldSeparator = '\u001f'
private const val MaxDeliveredOccurrenceIds = 256

data class ScheduledReminderOccurrence(
    val type: ReminderNotificationType,
    val occurrenceId: String,
    val scheduledAtEpochMillis: Long,
    val fireAtEpochMillis: Long,
)

class StudyReminderOccurrenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ReminderOccurrencePreferences,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun scheduled(planId: StudyPlanId): List<ScheduledReminderOccurrence> =
        preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty()
            .mapNotNull(::decodeScheduled)

    @Synchronized
    fun replaceScheduled(planId: StudyPlanId, candidates: Collection<ReminderNotificationCandidate>) {
        val encoded = candidates.mapTo(linkedSetOf(), ::encodeScheduled)
        preferences.edit().putStringSet(scheduledKey(planId), encoded).commit()
    }


    @Synchronized
    fun addScheduled(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        val encoded = LinkedHashSet(preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty())
        encoded.removeAll { value -> decodeScheduled(value)?.occurrenceId == candidate.occurrenceId }
        encoded += encodeScheduled(candidate)
        preferences.edit().putStringSet(scheduledKey(planId), encoded).commit()
    }

    @Synchronized
    fun removeScheduled(planId: StudyPlanId, occurrenceId: String) {
        val encoded = preferences.getStringSet(scheduledKey(planId), emptySet()).orEmpty()
            .filterTo(linkedSetOf()) { value -> decodeScheduled(value)?.occurrenceId != occurrenceId }
        val editor = preferences.edit()
        if (encoded.isEmpty()) editor.remove(scheduledKey(planId)) else editor
            .putStringSet(scheduledKey(planId), encoded)
        editor.commit()
    }

    @Synchronized
    fun clearScheduled(planId: StudyPlanId) {
        preferences.edit().remove(scheduledKey(planId)).commit()
    }

    @Synchronized
    fun deliveredOccurrenceIds(planId: StudyPlanId): Set<String> =
        preferences.getStringSet(deliveredKey(planId), emptySet()).orEmpty().toSet()

    @Synchronized
    fun markDelivered(planId: StudyPlanId, occurrenceId: String) {
        val delivered = LinkedHashSet(preferences.getStringSet(deliveredKey(planId), emptySet()).orEmpty())
        delivered += occurrenceId
        while (delivered.size > MaxDeliveredOccurrenceIds) {
            delivered.remove(delivered.first())
        }
        preferences.edit().putStringSet(deliveredKey(planId), delivered).commit()
    }


    @Synchronized
    fun removeDelivered(planId: StudyPlanId, occurrenceId: String) {
        val delivered = LinkedHashSet(preferences.getStringSet(deliveredKey(planId), emptySet()).orEmpty())
        if (!delivered.remove(occurrenceId)) return
        val editor = preferences.edit()
        if (delivered.isEmpty()) editor.remove(deliveredKey(planId)) else editor
            .putStringSet(deliveredKey(planId), delivered)
        editor.commit()
    }

    @Synchronized
    fun markProductionPipelineCanary(planId: StudyPlanId, verifiedAtEpochMillis: Long) {
        preferences.edit().putLong(canaryKey(planId), verifiedAtEpochMillis).commit()
    }

    @Synchronized
    fun productionPipelineCanaryAt(planId: StudyPlanId): Long? =
        preferences.getLong(canaryKey(planId), -1L).takeIf { it >= 0L }

    @Synchronized
    fun clearPlan(planId: StudyPlanId) {
        preferences.edit()
            .remove(scheduledKey(planId))
            .remove(deliveredKey(planId))
            .remove(canaryKey(planId))
            .commit()
    }

    private fun scheduledKey(planId: StudyPlanId): String = "scheduled:${planId.value}"
    private fun deliveredKey(planId: StudyPlanId): String = "delivered:${planId.value}"
    private fun canaryKey(planId: StudyPlanId): String = "pipeline-canary:${planId.value}"

    private fun encodeScheduled(candidate: ReminderNotificationCandidate): String = listOf(
        candidate.type.name,
        candidate.occurrenceId,
        candidate.scheduledAtEpochMillis.toString(),
        candidate.fireAtEpochMillis.toString(),
    ).joinToString(FieldSeparator.toString())

    private fun decodeScheduled(value: String): ScheduledReminderOccurrence? {
        val parts = value.split(FieldSeparator)
        if (parts.size != 4) return null
        val type = ReminderNotificationType.entries.firstOrNull { it.name == parts[0] } ?: return null
        val scheduledAt = parts[2].toLongOrNull() ?: return null
        val fireAt = parts[3].toLongOrNull() ?: return null
        return ScheduledReminderOccurrence(
            type = type,
            occurrenceId = parts[1],
            scheduledAtEpochMillis = scheduledAt,
            fireAtEpochMillis = fireAt,
        )
    }
}
