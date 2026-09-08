package com.mikeyphw.studyflow.tv.navigation

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole

/**
 * Structured Android TV navigation shell contract introduced in TVOV05.
 *
 * This model is intentionally data-first and dependency-budgeted. The TV app, D-pad focus
 * layer, and paired-phone controller share this destination set instead of drifting into
 * separate route names or Boolean route flags. TVOV07/TVOV08 add non-rail destinations
 * that are opened from Today or paired-phone commands while the primary rail remains stable.
 */
enum class StudyFlowTvDestination(
    val route: String,
    val surface: StudyFlowTvSurface,
) {
    HOME("home", StudyFlowTvSurface.HOME),
    TODAY("today", StudyFlowTvSurface.TODAY),
    STUDY_SESSION("session", StudyFlowTvSurface.STUDY_SESSION),
    PRACTICE("practice", StudyFlowTvSurface.QUESTION_SET),
    REVIEWS("reviews", StudyFlowTvSurface.REVIEW),
    MOCK_EXAM("mock-exam", StudyFlowTvSurface.MOCK_EXAM),
    PROGRESS("progress", StudyFlowTvSurface.PROGRESS),
    SCHEDULE("schedule", StudyFlowTvSurface.SCHEDULE),
    LIBRARY("library", StudyFlowTvSurface.LIBRARY),
    SETTINGS("settings", StudyFlowTvSurface.SETTINGS),
}

data class StudyFlowTvNavigationItem(
    val destination: StudyFlowTvDestination,
    val row: Int,
    val enabled: Boolean = true,
) {
    init {
        require(row >= 0) { "TV navigation row must be non-negative." }
        require(destination.route.isNotBlank()) { "TV navigation route must not be blank." }
    }
}

object StudyFlowTvNavigationShell {
    const val overlay = "TVOV05"
    const val shellOwnsTopLevelNavigation = true
    const val remoteAndPhoneControlShareDestinations = true
    const val routePayloadsUseSmallEnums = true
    const val destinationCount = 9
    const val secondaryDestinationCount = 1
    const val allDestinationCount = 10
    const val navigationRailColumn = 0

    val primaryDestinations: List<StudyFlowTvDestination> = listOf(
        StudyFlowTvDestination.HOME,
        StudyFlowTvDestination.TODAY,
        StudyFlowTvDestination.PRACTICE,
        StudyFlowTvDestination.REVIEWS,
        StudyFlowTvDestination.MOCK_EXAM,
        StudyFlowTvDestination.PROGRESS,
        StudyFlowTvDestination.SCHEDULE,
        StudyFlowTvDestination.LIBRARY,
        StudyFlowTvDestination.SETTINGS,
    )

    val secondaryDestinations: List<StudyFlowTvDestination> = listOf(
        StudyFlowTvDestination.STUDY_SESSION,
    )

    val allDestinations: List<StudyFlowTvDestination> = primaryDestinations + secondaryDestinations

    fun defaultDestination(): StudyFlowTvDestination = StudyFlowTvDestination.HOME

    fun destinationForRoute(route: String?): StudyFlowTvDestination =
        allDestinations.firstOrNull { it.route == route } ?: defaultDestination()

    fun destinationForSurface(surface: StudyFlowTvSurface): StudyFlowTvDestination? =
        allDestinations.firstOrNull { it.surface == surface }

    fun navigationItems(): List<StudyFlowTvNavigationItem> =
        primaryDestinations.mapIndexed { index, destination ->
            StudyFlowTvNavigationItem(destination = destination, row = index)
        }

    fun focusItems(): List<StudyFlowTvFocusItem> = navigationItems().map { item ->
        StudyFlowTvFocusItem(
            id = item.destination.route,
            role = StudyFlowTvFocusRole.NAVIGATION_ITEM,
            row = item.row,
            column = navigationRailColumn,
            enabled = item.enabled,
        )
    }

    fun openCommandFor(
        destination: StudyFlowTvDestination,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = destination.surface,
        action = StudyFlowTvControlAction.OPEN,
        targetId = destination.route,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )
}
