package com.mikeyphw.studyflow.tv.today

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV Today workspace introduced in TVOV07.
 *
 * The TV Today surface is a remote-first plan cockpit. It keeps the next study block, review
 * queue, practice queue, weekly plan summary, and paired-phone control action model together
 * without depending on Room, repositories, phone feature modules, Compose, or Cast transport.
 */
enum class StudyFlowTvTodayBlockId {
    NEXT_STUDY_BLOCK,
    DUE_REVIEWS,
    QUESTION_PRACTICE,
    MOCK_EXAM_PREP,
}

enum class StudyFlowTvTodayAction {
    START_NEXT_BLOCK,
    START_REVIEWS,
    START_PRACTICE,
    OPEN_SCHEDULE,
}

data class StudyFlowTvTodaySnapshot(
    val examName: String,
    val dateLabel: String,
    val nextBlockTitle: String,
    val nextBlockMinutes: Int,
    val dueReviewCount: Int,
    val practiceQuestionCount: Int,
    val weeklyProgressPercent: Int,
    val mockExamScheduled: Boolean,
) {
    init {
        require(examName.isNotBlank()) { "TV Today exam name must not be blank." }
        require(dateLabel.isNotBlank()) { "TV Today date label must not be blank." }
        require(nextBlockTitle.isNotBlank()) { "TV Today next block title must not be blank." }
        require(nextBlockMinutes > 0) { "TV Today next block must have a positive duration." }
        require(dueReviewCount >= 0) { "TV Today due review count must be non-negative." }
        require(practiceQuestionCount >= 0) { "TV Today practice question count must be non-negative." }
        require(weeklyProgressPercent in 0..100) { "TV Today weekly progress must be 0..100." }
    }
}

data class StudyFlowTvTodayBlock(
    val id: StudyFlowTvTodayBlockId,
    val action: StudyFlowTvTodayAction,
    val surface: StudyFlowTvSurface,
    val row: Int,
    val column: Int,
) {
    init {
        require(row >= 0) { "TV Today block row must be non-negative." }
        require(column >= 0) { "TV Today block column must be non-negative." }
    }
}

data class StudyFlowTvTodayWorkspaceState(
    val snapshot: StudyFlowTvTodaySnapshot,
    val blocks: List<StudyFlowTvTodayBlock>,
)

object StudyFlowTvTodayWorkspace {
    const val overlay = "TVOV07"
    const val ownsTodayDestination = true
    const val nextStudyBlockVisible = true
    const val reviewQueueVisible = true
    const val practiceQueueVisible = true
    const val weeklyPlanSummaryVisible = true
    const val pairedPhoneCanOpenTodayActions = true
    const val usesSharedNavigationDestinations = true
    const val usesSharedControlProtocol = true
    const val blockCount = 4
    const val actionCount = 4
    const val blockColumnStart = 1

    fun defaultSnapshot(): StudyFlowTvTodaySnapshot = StudyFlowTvTodaySnapshot(
        examName = "StudyFlow plan",
        dateLabel = "Today",
        nextBlockTitle = "Focused study block",
        nextBlockMinutes = 45,
        dueReviewCount = 12,
        practiceQuestionCount = 20,
        weeklyProgressPercent = 38,
        mockExamScheduled = true,
    )

    fun workspaceForSnapshot(snapshot: StudyFlowTvTodaySnapshot): StudyFlowTvTodayWorkspaceState =
        StudyFlowTvTodayWorkspaceState(snapshot = snapshot, blocks = blocks())

    fun blocks(): List<StudyFlowTvTodayBlock> = listOf(
        StudyFlowTvTodayBlock(
            id = StudyFlowTvTodayBlockId.NEXT_STUDY_BLOCK,
            action = StudyFlowTvTodayAction.START_NEXT_BLOCK,
            surface = StudyFlowTvSurface.STUDY_SESSION,
            row = 0,
            column = blockColumnStart,
        ),
        StudyFlowTvTodayBlock(
            id = StudyFlowTvTodayBlockId.DUE_REVIEWS,
            action = StudyFlowTvTodayAction.START_REVIEWS,
            surface = StudyFlowTvSurface.REVIEW,
            row = 0,
            column = blockColumnStart + 1,
        ),
        StudyFlowTvTodayBlock(
            id = StudyFlowTvTodayBlockId.QUESTION_PRACTICE,
            action = StudyFlowTvTodayAction.START_PRACTICE,
            surface = StudyFlowTvSurface.QUESTION_SET,
            row = 1,
            column = blockColumnStart,
        ),
        StudyFlowTvTodayBlock(
            id = StudyFlowTvTodayBlockId.MOCK_EXAM_PREP,
            action = StudyFlowTvTodayAction.OPEN_SCHEDULE,
            surface = StudyFlowTvSurface.SCHEDULE,
            row = 1,
            column = blockColumnStart + 1,
        ),
    )

    fun destinationForAction(action: StudyFlowTvTodayAction): StudyFlowTvDestination = when (action) {
        StudyFlowTvTodayAction.START_NEXT_BLOCK -> StudyFlowTvDestination.STUDY_SESSION
        StudyFlowTvTodayAction.START_REVIEWS -> StudyFlowTvDestination.REVIEWS
        StudyFlowTvTodayAction.START_PRACTICE -> StudyFlowTvDestination.PRACTICE
        StudyFlowTvTodayAction.OPEN_SCHEDULE -> StudyFlowTvDestination.SCHEDULE
    }

    fun focusItems(): List<StudyFlowTvFocusItem> = blocks().map { block ->
        StudyFlowTvFocusItem(
            id = "today-block-${block.id.name.lowercase()}",
            role = StudyFlowTvFocusRole.CONTENT_CARD,
            row = block.row,
            column = block.column,
        )
    }

    fun commandForAction(
        action: StudyFlowTvTodayAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvNavigationShell.openCommandFor(
        destination = destinationForAction(action),
        commandId = commandId,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )
}
