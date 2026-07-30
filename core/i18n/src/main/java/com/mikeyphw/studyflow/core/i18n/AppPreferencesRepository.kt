package com.mikeyphw.studyflow.core.i18n

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setDynamicColorEnabled(enabled: Boolean)
}
