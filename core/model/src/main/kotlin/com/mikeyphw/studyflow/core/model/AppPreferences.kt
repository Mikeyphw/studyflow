package com.mikeyphw.studyflow.core.model

enum class AppLanguage(val languageTag: String?) {
    SYSTEM_DEFAULT(null),
    ENGLISH("en"),
    PORTUGUESE_BRAZIL("pt-BR"),
}

enum class ThemeMode {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK,
}

data class AppPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM_DEFAULT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT,
    val dynamicColorEnabled: Boolean = true,
    val defaultStudyPlanId: StudyPlanId? = null,
    val recentSearches: List<String> = emptyList(),
)
