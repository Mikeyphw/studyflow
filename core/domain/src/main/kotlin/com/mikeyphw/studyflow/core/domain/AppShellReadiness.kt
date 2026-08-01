package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.ThemeMode

data class AppShellReadinessInput(
    val language: AppLanguage,
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val hasDefaultPlan: Boolean,
)

data class AppShellReadinessReport(
    val languageConfigured: Boolean,
    val appearanceConfigured: Boolean,
    val backupEntryAvailable: Boolean,
    val accessibilityBaselineReady: Boolean,
) {
    val readyCount: Int = listOf(
        languageConfigured,
        appearanceConfigured,
        backupEntryAvailable,
        accessibilityBaselineReady,
    ).count { it }

    val totalCount: Int = 4
    val readyPercent: Int = (readyCount * 100) / totalCount
}

object AppShellReadinessAuditor {
    fun evaluate(input: AppShellReadinessInput): AppShellReadinessReport = AppShellReadinessReport(
        languageConfigured = input.language != AppLanguage.SYSTEM_DEFAULT,
        appearanceConfigured = input.themeMode != ThemeMode.SYSTEM_DEFAULT || input.dynamicColorEnabled,
        backupEntryAvailable = input.hasDefaultPlan,
        accessibilityBaselineReady = true,
    )
}
