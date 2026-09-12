package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

data class DesktopAttentionGlobalPreferences(
    val remindersEnabled: Boolean = true,
    val nativeNotificationsEnabled: Boolean = true,
    val trayDashboardEnabled: Boolean = true,
    val paused: Boolean = false,
)

class DesktopAttentionPreferences private constructor(
    private val file: Path,
    private var properties: Properties,
) {
    @Synchronized
    fun global(): DesktopAttentionGlobalPreferences = DesktopAttentionGlobalPreferences(
        remindersEnabled = properties.getProperty("global.remindersEnabled")?.toBooleanStrictOrNull() ?: true,
        nativeNotificationsEnabled = properties.getProperty("global.nativeNotificationsEnabled")?.toBooleanStrictOrNull() ?: true,
        trayDashboardEnabled = properties.getProperty("global.trayDashboardEnabled")?.toBooleanStrictOrNull() ?: true,
        paused = properties.getProperty("global.paused")?.toBooleanStrictOrNull() ?: false,
    )

    @Synchronized
    fun updateGlobal(transform: (DesktopAttentionGlobalPreferences) -> DesktopAttentionGlobalPreferences) {
        val value = transform(global())
        properties["global.remindersEnabled"] = value.remindersEnabled.toString()
        properties["global.nativeNotificationsEnabled"] = value.nativeNotificationsEnabled.toString()
        properties["global.trayDashboardEnabled"] = value.trayDashboardEnabled.toString()
        properties["global.paused"] = value.paused.toString()
        persistLocked()
    }

    @Synchronized
    fun policy(planId: StudyPlanId, legacy: StudyPlanSettings): StudyReminderPolicy {
        val prefix = "plan.${planId.value}."
        if (properties.getProperty(prefix + "schema") == null) {
            savePolicy(planId, StudyReminderPolicy.fromLegacy(legacy))
        }
        return StudyReminderPolicy(
            plannedStudyEnabled = bool(prefix + "planned", true),
            dueReviewEnabled = bool(prefix + "reviews", legacy.reviewNotificationsEnabled),
            missedActivityEnabled = bool(prefix + "missed", true),
            dailySummaryEnabled = bool(prefix + "summary", true),
            sessionTimerEnabled = bool(prefix + "sessions", true),
            examCountdownEnabled = bool(prefix + "exam", false),
            plannedStudyLeadMinutes = int(prefix + "lead", 10).coerceIn(0, 120),
            dailySummaryMinuteOfDay = int(prefix + "summaryMinute", 8 * 60).coerceIn(0, 1439),
            missedActivityDelayMinutes = int(prefix + "missedDelay", 30).coerceIn(5, 720),
            defaultSnoozeMinutes = int(prefix + "snooze", 10).coerceIn(5, 240),
            sessionEndingLeadMinutes = int(prefix + "sessionLead", 5).coerceIn(1, 30),
            examCountdownDays = int(prefix + "examDays", 7).coerceIn(1, 60),
        )
    }

    @Synchronized
    fun savePolicy(planId: StudyPlanId, policy: StudyReminderPolicy) {
        val prefix = "plan.${planId.value}."
        properties[prefix + "schema"] = "1"
        properties[prefix + "planned"] = policy.plannedStudyEnabled.toString()
        properties[prefix + "reviews"] = policy.dueReviewEnabled.toString()
        properties[prefix + "missed"] = policy.missedActivityEnabled.toString()
        properties[prefix + "summary"] = policy.dailySummaryEnabled.toString()
        properties[prefix + "sessions"] = policy.sessionTimerEnabled.toString()
        properties[prefix + "exam"] = policy.examCountdownEnabled.toString()
        properties[prefix + "lead"] = policy.plannedStudyLeadMinutes.toString()
        properties[prefix + "summaryMinute"] = policy.dailySummaryMinuteOfDay.toString()
        properties[prefix + "missedDelay"] = policy.missedActivityDelayMinutes.toString()
        properties[prefix + "snooze"] = policy.defaultSnoozeMinutes.toString()
        properties[prefix + "sessionLead"] = policy.sessionEndingLeadMinutes.toString()
        properties[prefix + "examDays"] = policy.examCountdownDays.toString()
        persistLocked()
    }

    private fun bool(key: String, fallback: Boolean) = properties.getProperty(key)?.toBooleanStrictOrNull() ?: fallback
    private fun int(key: String, fallback: Int) = properties.getProperty(key)?.toIntOrNull() ?: fallback

    @Synchronized
    private fun persistLocked() {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "attention-preferences-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output -> properties.store(output, "StudyFlow Desktop attention preferences") }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun open(root: Path = defaultDesktopWorkspaceRoot()): DesktopAttentionPreferences {
            Files.createDirectories(root)
            val file = root.resolve("attention-preferences-v1.properties")
            val properties = Properties()
            if (Files.isRegularFile(file)) Files.newInputStream(file).use { input -> properties.load(input) }
            return DesktopAttentionPreferences(file, properties).also { prefs ->
                if (!Files.exists(file)) prefs.persistLocked()
            }
        }
    }
}
