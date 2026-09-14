package com.mikeyphw.studyflow.reminders

import android.content.Context
import androidx.core.content.edit
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategoryEscalationPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionEscalationMode
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionQuietWindow
import com.mikeyphw.studyflow.core.link.defaultAttentionEscalationPolicies
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.TimeZone

private const val FocusPreferencesName = "studyflow_android_attention_focus_policy_v1"
private const val FocusSchemaVersion = 1

class AndroidStudyFlowAttentionFocusPolicyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FocusPreferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun load(
        planId: StudyPlanId,
        legacyQuietStartMinute: Int? = null,
        legacyQuietEndMinute: Int? = null,
    ): StudyFlowAttentionFocusPolicy {
        val prefix = "plan:${planId.value}"
        if (preferences.getInt("$prefix.schema", 0) < FocusSchemaVersion) {
            val migratedQuiet = if (
                legacyQuietStartMinute != null &&
                legacyQuietEndMinute != null &&
                legacyQuietStartMinute != legacyQuietEndMinute
            ) {
                StudyFlowAttentionQuietWindow(
                    enabled = true,
                    startMinute = legacyQuietStartMinute,
                    endMinute = legacyQuietEndMinute,
                    timezoneId = TimeZone.getDefault().id,
                )
            } else null
            val migrated = StudyFlowAttentionFocusPolicy(quietWindow = migratedQuiet)
            save(planId, migrated)
            return migrated
        }
        val quietEnabled = preferences.getBoolean("$prefix.quiet.enabled", false)
        val quiet = if (quietEnabled) {
            val currentZone = TimeZone.getDefault().id
            val storedZone = preferences.getString("$prefix.quiet.zone", currentZone)
            if (storedZone != currentZone) {
                // OV17: ATT10 quiet hours track the recipient phone timezone after travel/timezone changes.
                preferences.edit(commit = true) { putString("$prefix.quiet.zone", currentZone) }
            }
            StudyFlowAttentionQuietWindow(
                enabled = true,
                startMinute = preferences.getInt("$prefix.quiet.start", 22 * 60).coerceIn(0, 1439),
                endMinute = preferences.getInt("$prefix.quiet.end", 7 * 60).coerceIn(0, 1439).let { end ->
                    val start = preferences.getInt("$prefix.quiet.start", 22 * 60).coerceIn(0, 1439)
                    if (end == start) (end + 60) % 1440 else end
                },
                timezoneId = currentZone,
            )
        } else null
        val categories = defaultAttentionEscalationPolicies().map { default ->
            val rawMode = preferences.getString("$prefix.category.${default.category.name}.mode", default.mode.name)
            default.copy(
                mode = StudyFlowAttentionEscalationMode.entries.firstOrNull { it.name == rawMode } ?: default.mode,
                bypassQuietHours = preferences.getBoolean("$prefix.category.${default.category.name}.bypass", default.bypassQuietHours),
                mirrorAfterMinutes = preferences.getInt("$prefix.category.${default.category.name}.mirror", default.mirrorAfterMinutes).coerceIn(0, 720),
            )
        }
        return StudyFlowAttentionFocusPolicy(
            enabled = preferences.getBoolean("$prefix.enabled", true),
            profile = StudyFlowAttentionFocusProfile.entries.firstOrNull {
                it.name == preferences.getString("$prefix.profile", StudyFlowAttentionFocusProfile.BALANCED.name)
            } ?: StudyFlowAttentionFocusProfile.BALANCED,
            quietWindow = quiet,
            categoryPolicies = categories,
            allowHighPriorityQuietBypass = preferences.getBoolean("$prefix.highBypass", true),
            allowTimeSensitiveQuietBypass = preferences.getBoolean("$prefix.timeBypass", true),
            maxEscalationMirrors = preferences.getInt("$prefix.mirrors", 1).coerceIn(0, 3),
        )
    }

    @Synchronized
    fun save(planId: StudyPlanId, policy: StudyFlowAttentionFocusPolicy) {
        val prefix = "plan:${planId.value}"
        preferences.edit(commit = true) {
            putInt("$prefix.schema", FocusSchemaVersion)
            putBoolean("$prefix.enabled", policy.enabled)
            putString("$prefix.profile", policy.profile.name)
            val quietWindow = policy.quietWindow
            putBoolean("$prefix.quiet.enabled", quietWindow?.enabled == true)
            quietWindow?.let { quiet ->
                putInt("$prefix.quiet.start", quiet.startMinute)
                putInt("$prefix.quiet.end", quiet.endMinute)
                putString("$prefix.quiet.zone", quiet.timezoneId ?: TimeZone.getDefault().id)
            } ?: run {
                remove("$prefix.quiet.start")
                remove("$prefix.quiet.end")
                remove("$prefix.quiet.zone")
            }
            putBoolean("$prefix.highBypass", policy.allowHighPriorityQuietBypass)
            putBoolean("$prefix.timeBypass", policy.allowTimeSensitiveQuietBypass)
            putInt("$prefix.mirrors", policy.maxEscalationMirrors)
            policy.categoryPolicies.forEach { category ->
                putString("$prefix.category.${category.category.name}.mode", category.mode.name)
                putBoolean("$prefix.category.${category.category.name}.bypass", category.bypassQuietHours)
                putInt("$prefix.category.${category.category.name}.mirror", category.mirrorAfterMinutes)
            }
        }
    }

    fun updateCategory(
        planId: StudyPlanId,
        category: StudyFlowAttentionCategory,
        transform: (StudyFlowAttentionCategoryEscalationPolicy) -> StudyFlowAttentionCategoryEscalationPolicy,
    ): StudyFlowAttentionFocusPolicy {
        val current = load(planId)
        val updated = current.copy(categoryPolicies = current.categoryPolicies.map {
            if (it.category == category) transform(it) else it
        })
        save(planId, updated)
        return updated
    }
}
