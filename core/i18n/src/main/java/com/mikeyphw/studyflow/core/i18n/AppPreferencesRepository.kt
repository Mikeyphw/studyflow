package com.mikeyphw.studyflow.core.i18n

import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setDefaultStudyPlan(planId: StudyPlanId?)

    /** Clears the default only if it still points at [planId]. */
    suspend fun clearDefaultStudyPlanIf(planId: StudyPlanId): Boolean

    /** Sets an initial default without overwriting a choice made concurrently elsewhere. */
    suspend fun setDefaultStudyPlanIfAbsent(planId: StudyPlanId): Boolean

    suspend fun recordRecentSearch(query: String)

    suspend fun clearRecentSearches()
}
