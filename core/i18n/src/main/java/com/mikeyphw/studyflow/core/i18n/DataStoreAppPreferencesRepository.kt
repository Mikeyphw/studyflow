package com.mikeyphw.studyflow.core.i18n

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.RecentSearchHistory
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyFlowPreferencesDataStore by preferencesDataStore(name = "studyflow_preferences")

class DataStoreAppPreferencesRepository(
    context: Context,
) : AppPreferencesRepository {
    private val dataStore = context.applicationContext.studyFlowPreferencesDataStore

    override val preferences: Flow<AppPreferences> = dataStore.data.map { values ->
        AppPreferences(
            language = values[LanguageKey]
                ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }
                ?: AppLanguage.SYSTEM_DEFAULT,
            themeMode = values[ThemeModeKey]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM_DEFAULT,
            dynamicColorEnabled = values[DynamicColorKey] ?: true,
            defaultStudyPlanId = values[DefaultStudyPlanKey]
                ?.takeIf(String::isNotBlank)
                ?.let(::StudyPlanId),
            recentSearches = RecentSearchHistory.decode(values[RecentSearchesKey]),
        )
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { values -> values[LanguageKey] = language.name }
        AppLocaleController.apply(language)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { values -> values[ThemeModeKey] = themeMode.name }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[DynamicColorKey] = enabled }
    }

    override suspend fun setDefaultStudyPlan(planId: StudyPlanId?) {
        dataStore.edit { values ->
            if (planId == null) {
                values.remove(DefaultStudyPlanKey)
            } else {
                values[DefaultStudyPlanKey] = planId.value
            }
        }
    }

    override suspend fun clearDefaultStudyPlanIf(planId: StudyPlanId): Boolean {
        var cleared = false
        dataStore.edit { values ->
            if (values[DefaultStudyPlanKey] == planId.value) {
                values.remove(DefaultStudyPlanKey)
                cleared = true
            }
        }
        return cleared
    }

    override suspend fun setDefaultStudyPlanIfAbsent(planId: StudyPlanId): Boolean {
        var stored = false
        dataStore.edit { values ->
            if (values[DefaultStudyPlanKey].isNullOrBlank()) {
                values[DefaultStudyPlanKey] = planId.value
                stored = true
            }
        }
        return stored
    }

    override suspend fun recordRecentSearch(query: String) {
        dataStore.edit { values ->
            val current = RecentSearchHistory.decode(values[RecentSearchesKey])
            values[RecentSearchesKey] = RecentSearchHistory.encode(
                RecentSearchHistory.record(current, query),
            )
        }
    }

    override suspend fun clearRecentSearches() {
        dataStore.edit { values -> values.remove(RecentSearchesKey) }
    }

    private companion object {
        val LanguageKey = stringPreferencesKey("app_language")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val DynamicColorKey = booleanPreferencesKey("dynamic_color_enabled")
        val DefaultStudyPlanKey = stringPreferencesKey("default_study_plan_id")
        val RecentSearchesKey = stringPreferencesKey("recent_searches")
    }
}
