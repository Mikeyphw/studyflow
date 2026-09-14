package com.mikeyphw.studyflow.tv

import android.content.Context
import android.content.SharedPreferences
import com.mikeyphw.studyflow.tv.library.StudyFlowTvMaterialViewerState
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell
import com.mikeyphw.studyflow.tv.search.StudyFlowTvSearch
import com.mikeyphw.studyflow.tv.search.StudyFlowTvSearchState

/**
 * Small TV UI-state checkpoint store. It does not persist canonical StudyFlow data; it only keeps
 * presentation/viewer/navigation/search cursors so Activity recreation does not reset the TV shell
 * away from the user's active surface.
 */
class StudyFlowTvDurableUiStateStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun restoreDestination(): StudyFlowTvDestination = StudyFlowTvNavigationShell.destinationForRoute(
        preferences.getString(KEY_DESTINATION_ROUTE, null),
    )

    fun saveDestination(destination: StudyFlowTvDestination, nowEpochMillis: Long): Boolean = preferences.edit()
        .putString(KEY_DESTINATION_ROUTE, destination.route)
        .putLong(KEY_UPDATED_AT, nowEpochMillis)
        .commit()

    fun restoreSearchState(current: StudyFlowTvSearchState): StudyFlowTvSearchState {
        if (!preferences.getBoolean(KEY_SEARCH_VISIBLE, false)) return current.copy(visible = false)
        val query = preferences.getString(KEY_SEARCH_QUERY, "").orEmpty()
        val selectedIndex = preferences.getInt(KEY_SEARCH_SELECTED_INDEX, 0).coerceAtLeast(0)
        val refreshed = StudyFlowTvSearch.updateQuery(current, query)
        return refreshed.copy(
            visible = true,
            selectedIndex = selectedIndex.coerceAtMost(refreshed.results.lastIndex.coerceAtLeast(0)),
        )
    }

    fun saveSearchState(searchState: StudyFlowTvSearchState, nowEpochMillis: Long): Boolean = preferences.edit()
        .putBoolean(KEY_SEARCH_VISIBLE, searchState.visible)
        .putString(KEY_SEARCH_QUERY, searchState.query)
        .putInt(KEY_SEARCH_SELECTED_INDEX, searchState.selectedIndex)
        .putLong(KEY_UPDATED_AT, nowEpochMillis)
        .commit()


    fun restoreMaterialViewerState(candidate: StudyFlowTvMaterialViewerState): StudyFlowTvMaterialViewerState {
        val storedTitle = preferences.getString(KEY_VIEWER_TITLE, null) ?: return candidate
        if (storedTitle != candidate.title) return candidate
        val page = preferences.getInt(KEY_VIEWER_PAGE, candidate.pageIndex)
            .coerceIn(0, candidate.pageCount - 1)
        val zoom = preferences.getInt(KEY_VIEWER_ZOOM, candidate.zoomPercent)
            .coerceIn(50, 300)
        return candidate.copy(
            pageIndex = page,
            zoomPercent = zoom,
            playing = preferences.getBoolean(KEY_VIEWER_PLAYING, candidate.playing),
            mediaPositionMillis = preferences.getLong(KEY_VIEWER_POSITION, candidate.mediaPositionMillis).coerceAtLeast(0L),
        )
    }

    fun saveMaterialViewerState(state: StudyFlowTvMaterialViewerState?, nowEpochMillis: Long): Boolean {
        val edit = preferences.edit().putLong(KEY_UPDATED_AT, nowEpochMillis)
        if (state == null) {
            edit.remove(KEY_VIEWER_TITLE)
                .remove(KEY_VIEWER_PAGE)
                .remove(KEY_VIEWER_ZOOM)
                .remove(KEY_VIEWER_PLAYING)
                .remove(KEY_VIEWER_POSITION)
        } else {
            edit.putString(KEY_VIEWER_TITLE, state.title)
                .putInt(KEY_VIEWER_PAGE, state.pageIndex)
                .putInt(KEY_VIEWER_ZOOM, state.zoomPercent)
                .putBoolean(KEY_VIEWER_PLAYING, state.playing)
                .putLong(KEY_VIEWER_POSITION, state.mediaPositionMillis)
        }
        return edit.commit()
    }

    companion object {
        const val overlay = "OV18"
        const val presentationUiStateIsDurable = true
        const val destinationSearchAndViewerCursorsPersist = true
        private const val PREFERENCES = "studyflow-tv-ui-state-v1"
        private const val KEY_DESTINATION_ROUTE = "destination-route"
        private const val KEY_SEARCH_VISIBLE = "search-visible"
        private const val KEY_SEARCH_QUERY = "search-query"
        private const val KEY_SEARCH_SELECTED_INDEX = "search-selected-index"
        private const val KEY_VIEWER_TITLE = "viewer-title"
        private const val KEY_VIEWER_PAGE = "viewer-page"
        private const val KEY_VIEWER_ZOOM = "viewer-zoom"
        private const val KEY_VIEWER_PLAYING = "viewer-playing"
        private const val KEY_VIEWER_POSITION = "viewer-position"
        private const val KEY_UPDATED_AT = "updated-at"
    }
}
