package com.mikeyphw.studyflow.tv.schedule

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV read-only schedule surface introduced in TVOV13.
 *
 * The TV schedule is intentionally presentation-state only. It shows today, this week, review
 * windows, mock-exam entries, and rest buffers while preventing editing, importing, deleting,
 * rescheduling, or direct repository access from the TV APK. Schedule changes remain owned by the
 * phone/tablet app and shared domain/data layers; TV can navigate, open the active StudyFlow flow,
 * and emit cast state.
 */
enum class StudyFlowTvScheduleItemId {
    MORNING_REVIEW,
    FOCUS_BLOCK,
    PRACTICE_SET,
    MOCK_EXAM_REHEARSAL,
    REST_BUFFER,
}

enum class StudyFlowTvScheduleItemKind {
    STUDY_BLOCK,
    REVIEW_WINDOW,
    PRACTICE_SET,
    MOCK_EXAM,
    BUFFER,
}

enum class StudyFlowTvScheduleAction {
    OPEN_ACTIVE_ITEM,
    PREVIOUS_ITEM,
    NEXT_ITEM,
    OPEN_TODAY,
    OPEN_MOCK_EXAM,
}

data class StudyFlowTvScheduleItem(
    val id: StudyFlowTvScheduleItemId,
    val title: String,
    val timeLabel: String,
    val kind: StudyFlowTvScheduleItemKind,
    val destination: StudyFlowTvDestination,
    val row: Int,
    val column: Int,
) {
    init {
        require(title.isNotBlank()) { "TV schedule item title must not be blank." }
        require(timeLabel.isNotBlank()) { "TV schedule item time label must not be blank." }
        require(row >= 0) { "TV schedule item row must be non-negative." }
        require(column >= 0) { "TV schedule item column must be non-negative." }
        require(destination != StudyFlowTvDestination.SETTINGS) { "TV schedule must not route to settings as study work." }
    }
}

data class StudyFlowTvScheduleSnapshot(
    val scheduleId: String,
    val title: String,
    val weekLabel: String,
    val items: List<StudyFlowTvScheduleItem>,
    val activeItemIndex: Int = 0,
) {
    init {
        require(scheduleId.isNotBlank()) { "TV schedule id must not be blank." }
        require(title.isNotBlank()) { "TV schedule title must not be blank." }
        require(weekLabel.isNotBlank()) { "TV schedule week label must not be blank." }
        require(items.isNotEmpty()) { "TV schedule must expose at least one item." }
        require(activeItemIndex in items.indices) { "TV schedule active index is out of bounds." }
        require(items.map { it.id }.toSet().size == items.size) { "TV schedule items must be unique." }
    }

    val activeItem: StudyFlowTvScheduleItem
        get() = items[activeItemIndex]

    val mockExamCount: Int
        get() = items.count { it.kind == StudyFlowTvScheduleItemKind.MOCK_EXAM }

    val reviewWindowCount: Int
        get() = items.count { it.kind == StudyFlowTvScheduleItemKind.REVIEW_WINDOW }
}

object StudyFlowTvScheduleSurface {
    const val overlay = "TVOV13"
    const val ownsReadOnlyScheduleSurface = true
    const val todayAndWeekScheduleVisible = true
    const val reviewWindowsAndMockExamsVisible = true
    const val scheduleEditingDisabledOnTv = true
    const val pairedPhoneCanNavigateScheduleButNotMutate = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val scheduleItemCount = 5
    const val actionCount = 5
    const val itemColumnStart = 1
    const val actionRow = 3

    fun defaultSnapshot(): StudyFlowTvScheduleSnapshot = StudyFlowTvScheduleSnapshot(
        scheduleId = "tv-schedule-week-12",
        title = "Read-only schedule",
        weekLabel = "Week 12 · Sep 7-13",
        items = listOf(
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.MORNING_REVIEW,
                title = "Morning review window",
                timeLabel = "08:00 · due flashcards",
                kind = StudyFlowTvScheduleItemKind.REVIEW_WINDOW,
                destination = StudyFlowTvDestination.REVIEWS,
                row = 0,
                column = itemColumnStart,
            ),
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.FOCUS_BLOCK,
                title = "Administrative Law focus",
                timeLabel = "10:00 · 45 min",
                kind = StudyFlowTvScheduleItemKind.STUDY_BLOCK,
                destination = StudyFlowTvDestination.STUDY_SESSION,
                row = 0,
                column = itemColumnStart + 1,
            ),
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.PRACTICE_SET,
                title = "Question practice set",
                timeLabel = "14:00 · 30 questions",
                kind = StudyFlowTvScheduleItemKind.PRACTICE_SET,
                destination = StudyFlowTvDestination.PRACTICE,
                row = 1,
                column = itemColumnStart,
            ),
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.MOCK_EXAM_REHEARSAL,
                title = "Mock exam rehearsal",
                timeLabel = "Saturday · timed block",
                kind = StudyFlowTvScheduleItemKind.MOCK_EXAM,
                destination = StudyFlowTvDestination.MOCK_EXAM,
                row = 1,
                column = itemColumnStart + 1,
            ),
            StudyFlowTvScheduleItem(
                id = StudyFlowTvScheduleItemId.REST_BUFFER,
                title = "Recovery buffer",
                timeLabel = "Evening · no editing on TV",
                kind = StudyFlowTvScheduleItemKind.BUFFER,
                destination = StudyFlowTvDestination.TODAY,
                row = 2,
                column = itemColumnStart,
            ),
        ),
    )

    val actions: List<StudyFlowTvScheduleAction> = listOf(
        StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM,
        StudyFlowTvScheduleAction.PREVIOUS_ITEM,
        StudyFlowTvScheduleAction.NEXT_ITEM,
        StudyFlowTvScheduleAction.OPEN_TODAY,
        StudyFlowTvScheduleAction.OPEN_MOCK_EXAM,
    )

    fun applyAction(
        snapshot: StudyFlowTvScheduleSnapshot,
        action: StudyFlowTvScheduleAction,
    ): StudyFlowTvScheduleSnapshot = when (action) {
        StudyFlowTvScheduleAction.PREVIOUS_ITEM -> snapshot.copy(
            activeItemIndex = (snapshot.activeItemIndex - 1).coerceAtLeast(0),
        )
        StudyFlowTvScheduleAction.NEXT_ITEM -> snapshot.copy(
            activeItemIndex = (snapshot.activeItemIndex + 1).coerceAtMost(snapshot.items.lastIndex),
        )
        StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM,
        StudyFlowTvScheduleAction.OPEN_TODAY,
        StudyFlowTvScheduleAction.OPEN_MOCK_EXAM,
        -> snapshot
    }

    fun destinationForAction(
        action: StudyFlowTvScheduleAction,
        snapshot: StudyFlowTvScheduleSnapshot,
    ): StudyFlowTvDestination = when (action) {
        StudyFlowTvScheduleAction.OPEN_ACTIVE_ITEM -> snapshot.activeItem.destination
        StudyFlowTvScheduleAction.PREVIOUS_ITEM,
        StudyFlowTvScheduleAction.NEXT_ITEM,
        -> StudyFlowTvDestination.SCHEDULE
        StudyFlowTvScheduleAction.OPEN_TODAY -> StudyFlowTvDestination.TODAY
        StudyFlowTvScheduleAction.OPEN_MOCK_EXAM -> StudyFlowTvDestination.MOCK_EXAM
    }

    fun focusItems(snapshot: StudyFlowTvScheduleSnapshot): List<StudyFlowTvFocusItem> {
        val items = snapshot.items.map { item ->
            StudyFlowTvFocusItem(
                id = "schedule-item-${item.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = item.row,
                column = item.column,
            )
        }
        val actionItems = actions.mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "schedule-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = itemColumnStart + index,
            )
        }
        return items + actionItems
    }

    fun commandForAction(
        action: StudyFlowTvScheduleAction,
        snapshot: StudyFlowTvScheduleSnapshot,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = when (action) {
        StudyFlowTvScheduleAction.PREVIOUS_ITEM -> scheduleCommand(action, snapshot, commandId, issuedAtEpochMillis, StudyFlowTvControlAction.PREVIOUS)
        StudyFlowTvScheduleAction.NEXT_ITEM -> scheduleCommand(action, snapshot, commandId, issuedAtEpochMillis, StudyFlowTvControlAction.NEXT)
        else -> StudyFlowTvNavigationShell.openCommandFor(
            destination = destinationForAction(action, snapshot),
            commandId = commandId,
            issuedAtEpochMillis = issuedAtEpochMillis,
        )
    }

    fun castStateFor(
        snapshot: StudyFlowTvScheduleSnapshot,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.SCHEDULE,
        title = snapshot.title,
        subtitle = "${snapshot.weekLabel} · ${snapshot.activeItem.title}",
        progressPercent = ((snapshot.activeItemIndex + 1) * 100 / snapshot.items.size).coerceIn(0, 100),
        activeItemId = snapshot.activeItem.id.name.lowercase(),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun scheduleCommand(
        action: StudyFlowTvScheduleAction,
        snapshot: StudyFlowTvScheduleSnapshot,
        commandId: String,
        issuedAtEpochMillis: Long,
        controlAction: StudyFlowTvControlAction,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.SCHEDULE,
        action = controlAction,
        targetId = snapshot.activeItem.id.name.lowercase(),
        optionId = action.name.lowercase(),
        issuedAtEpochMillis = issuedAtEpochMillis,
    )
}
