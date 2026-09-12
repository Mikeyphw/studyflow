package com.mikeyphw.studyflow.reminders

import android.content.Context
import androidx.core.content.edit
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings

private const val PreferencesName = "studyflow_android_attention_policy_v1"
private const val SchemaVersion = 1

class AndroidStudyReminderPolicyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun load(planId: StudyPlanId, legacySettings: StudyPlanSettings): StudyReminderPolicy {
        val prefix = prefix(planId)
        if (preferences.getInt("$prefix.schema", 0) < SchemaVersion) {
            save(planId, StudyReminderPolicy.fromLegacy(legacySettings))
        }
        return StudyReminderPolicy(
            plannedStudyEnabled = preferences.getBoolean("$prefix.planned", true),
            dueReviewEnabled = preferences.getBoolean("$prefix.reviews", legacySettings.reviewNotificationsEnabled),
            missedActivityEnabled = preferences.getBoolean("$prefix.missed", true),
            dailySummaryEnabled = preferences.getBoolean("$prefix.summary", true),
            sessionTimerEnabled = preferences.getBoolean("$prefix.sessions", true),
            examCountdownEnabled = preferences.getBoolean("$prefix.exam", false),
            plannedStudyLeadMinutes = preferences.getInt("$prefix.lead", 10).coerceIn(0, 120),
            dailySummaryMinuteOfDay = preferences.getInt("$prefix.summaryMinute", 8 * 60).coerceIn(0, 1439),
            missedActivityDelayMinutes = preferences.getInt("$prefix.missedDelay", 30).coerceIn(5, 720),
            defaultSnoozeMinutes = preferences.getInt("$prefix.snooze", 10).coerceIn(5, 240),
            sessionEndingLeadMinutes = preferences.getInt("$prefix.sessionLead", 5).coerceIn(1, 30),
            examCountdownDays = preferences.getInt("$prefix.examDays", 7).coerceIn(1, 60),
        )
    }

    @Synchronized
    fun save(planId: StudyPlanId, policy: StudyReminderPolicy) {
        val prefix = prefix(planId)
        preferences.edit(commit = true) {
            putInt("$prefix.schema", SchemaVersion)
            putBoolean("$prefix.planned", policy.plannedStudyEnabled)
            putBoolean("$prefix.reviews", policy.dueReviewEnabled)
            putBoolean("$prefix.missed", policy.missedActivityEnabled)
            putBoolean("$prefix.summary", policy.dailySummaryEnabled)
            putBoolean("$prefix.sessions", policy.sessionTimerEnabled)
            putBoolean("$prefix.exam", policy.examCountdownEnabled)
            putInt("$prefix.lead", policy.plannedStudyLeadMinutes)
            putInt("$prefix.summaryMinute", policy.dailySummaryMinuteOfDay)
            putInt("$prefix.missedDelay", policy.missedActivityDelayMinutes)
            putInt("$prefix.snooze", policy.defaultSnoozeMinutes)
            putInt("$prefix.sessionLead", policy.sessionEndingLeadMinutes)
            putInt("$prefix.examDays", policy.examCountdownDays)
        }
    }

    private fun prefix(planId: StudyPlanId): String = "plan:${planId.value}"
}

data class StudyReminderRuntimeStatus(
    val lastReconciledAtEpochMillis: Long? = null,
    val lastDeliveredAtEpochMillis: Long? = null,
    val lastDeliveredOccurrenceId: String? = null,
    val lastRecoveryReason: String? = null,
)

class StudyReminderRuntimeStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "studyflow_reminder_runtime_status_v1",
        Context.MODE_PRIVATE,
    )

    fun snapshot(): StudyReminderRuntimeStatus = StudyReminderRuntimeStatus(
        lastReconciledAtEpochMillis = preferences.getLong("lastReconciledAt", -1L).takeIf { it >= 0 },
        lastDeliveredAtEpochMillis = preferences.getLong("lastDeliveredAt", -1L).takeIf { it >= 0 },
        lastDeliveredOccurrenceId = preferences.getString("lastDeliveredOccurrenceId", null),
        lastRecoveryReason = preferences.getString("lastRecoveryReason", null),
    )

    fun markReconciled(atEpochMillis: Long, reason: String? = null) {
        preferences.edit(commit = true) {
            putLong("lastReconciledAt", atEpochMillis)
            if (reason == null) remove("lastRecoveryReason") else putString("lastRecoveryReason", reason)
        }
    }

    fun markDelivered(occurrenceId: String, atEpochMillis: Long) {
        preferences.edit(commit = true) {
            putLong("lastDeliveredAt", atEpochMillis)
            putString("lastDeliveredOccurrenceId", occurrenceId)
        }
    }
}
