package com.mikeyphw.studyflow.tv.mockexam

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole

/**
 * Structured Android TV Mock Exam Studio introduced in TVOV10.
 *
 * The studio gives TV users a timed, section-based mock exam flow with remote and paired-phone
 * controls. It deliberately stays model- and view-driven inside the TV APK; repository sync,
 * scoring persistence, and phone-only editing remain outside this module.
 */
enum class StudyFlowTvMockExamStatus {
    READY,
    RUNNING,
    PAUSED,
    SUBMITTED,
    RESULTS,
}

enum class StudyFlowTvMockExamSectionId {
    GENERAL_KNOWLEDGE,
    PORTUGUESE,
    ADMIN_LAW,
    SPECIFIC_KNOWLEDGE,
    SECTION_FIVE,
    SECTION_SIX,
    SECTION_SEVEN,
    SECTION_EIGHT,
    SECTION_NINE,
    SECTION_TEN,
    SECTION_ELEVEN,
    SECTION_TWELVE,
}

enum class StudyFlowTvMockExamAction {
    START,
    PAUSE,
    RESUME,
    NEXT_SECTION,
    MARK_FOR_REVIEW,
    SUBMIT,
    REVIEW_RESULTS,
    RETURN_HOME,
}

data class StudyFlowTvMockExamSection(
    val id: StudyFlowTvMockExamSectionId,
    val sourceId: String = id.name.lowercase(),
    val title: String,
    val questionCount: Int,
    val minutes: Int,
    val row: Int,
    val column: Int,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
) {
    init {
        require(sourceId.isNotBlank()) { "TV mock exam source id must not be blank." }
        require(title.isNotBlank()) { "TV mock exam section title must not be blank." }
        require(questionCount > 0) { "TV mock exam section must contain questions." }
        require(minutes > 0) { "TV mock exam section minutes must be positive." }
        require(row >= 0) { "TV mock exam section row must be non-negative." }
        require(column >= 0) { "TV mock exam section column must be non-negative." }
        require(answeredCount in 0..questionCount) { "TV mock exam section answered count is outside question count." }
        require(correctCount in 0..answeredCount) { "TV mock exam section correct count is outside answered count." }
    }
}

data class StudyFlowTvMockExamState(
    val examId: String,
    val title: String,
    val sections: List<StudyFlowTvMockExamSection>,
    val status: StudyFlowTvMockExamStatus = StudyFlowTvMockExamStatus.READY,
    val activeSectionIndex: Int = 0,
    val elapsedMinutes: Int = 0,
    val flaggedSectionIds: Set<StudyFlowTvMockExamSectionId> = emptySet(),
    val correctAnswerCount: Int = 0,
    val answeredQuestionCount: Int = sections.sumOf { it.answeredCount },
    val canPersistActions: Boolean = true,
    val projectedAtEpochMillis: Long = 0L,
) {
    init {
        require(examId.isNotBlank()) { "TV mock exam id must not be blank." }
        require(title.isNotBlank()) { "TV mock exam title must not be blank." }
        require(sections.isNotEmpty()) { "TV mock exam must have sections." }
        require(activeSectionIndex in sections.indices) { "TV mock exam active section index is out of bounds." }
        require(elapsedMinutes >= 0) { "TV mock exam elapsed minutes must be non-negative." }
        require(correctAnswerCount >= 0) { "TV mock exam correct answer count must be non-negative." }
        require(answeredQuestionCount in 0..totalQuestions) { "TV mock exam answered count is outside question count." }
        require(correctAnswerCount <= answeredQuestionCount.coerceAtLeast(correctAnswerCount)) {
            "TV mock exam correct answer count must not exceed answered count when an answered count is available."
        }
        require(projectedAtEpochMillis >= 0L) { "TV mock exam projection time must not be negative." }
    }

    val activeSection: StudyFlowTvMockExamSection
        get() = sections[activeSectionIndex]

    val totalQuestions: Int
        get() = sections.sumOf { it.questionCount }

    val totalMinutes: Int
        get() = sections.sumOf { it.minutes }

    val progressPercent: Int
        get() = when (status) {
            StudyFlowTvMockExamStatus.READY -> 0
            StudyFlowTvMockExamStatus.SUBMITTED,
            StudyFlowTvMockExamStatus.RESULTS,
            -> 100
            StudyFlowTvMockExamStatus.RUNNING,
            StudyFlowTvMockExamStatus.PAUSED,
            -> ((elapsedMinutes * 100) / totalMinutes).coerceIn(0, 99)
        }
}

object StudyFlowTvMockExamStudio {
    const val overlay = "TVOV10"
    const val ov19TruthOverlay = "OV19"
    const val ownsMockExamSurface = true
    const val timedSectionsVisible = true
    const val remoteCanStartPauseSubmitAndReview = true
    const val pairedPhoneCanControlMockExam = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val sectionCount = 4
    const val actionCount = 8
    const val sectionColumnStart = 1
    const val actionRow = 2

    fun defaultState(): StudyFlowTvMockExamState = StudyFlowTvMockExamState(
        examId = "tv-mock-exam-1",
        title = "Mock Exam Studio",
        sections = listOf(
            StudyFlowTvMockExamSection(
                id = StudyFlowTvMockExamSectionId.GENERAL_KNOWLEDGE,
                title = "General knowledge",
                questionCount = 10,
                minutes = 20,
                row = 0,
                column = sectionColumnStart,
            ),
            StudyFlowTvMockExamSection(
                id = StudyFlowTvMockExamSectionId.PORTUGUESE,
                title = "Portuguese",
                questionCount = 12,
                minutes = 25,
                row = 0,
                column = sectionColumnStart + 1,
            ),
            StudyFlowTvMockExamSection(
                id = StudyFlowTvMockExamSectionId.ADMIN_LAW,
                title = "Administrative law",
                questionCount = 12,
                minutes = 25,
                row = 1,
                column = sectionColumnStart,
            ),
            StudyFlowTvMockExamSection(
                id = StudyFlowTvMockExamSectionId.SPECIFIC_KNOWLEDGE,
                title = "Specific knowledge",
                questionCount = 16,
                minutes = 30,
                row = 1,
                column = sectionColumnStart + 1,
            ),
        ),
    )

    fun availableActions(state: StudyFlowTvMockExamState): List<StudyFlowTvMockExamAction> {
        if (!state.canPersistActions) {
            return listOf(StudyFlowTvMockExamAction.RETURN_HOME)
        }
        return when (state.status) {
        StudyFlowTvMockExamStatus.READY -> listOf(
            StudyFlowTvMockExamAction.START,
            StudyFlowTvMockExamAction.RETURN_HOME,
        )
        StudyFlowTvMockExamStatus.RUNNING -> listOf(
            StudyFlowTvMockExamAction.PAUSE,
            StudyFlowTvMockExamAction.NEXT_SECTION,
            StudyFlowTvMockExamAction.MARK_FOR_REVIEW,
            StudyFlowTvMockExamAction.SUBMIT,
            StudyFlowTvMockExamAction.RETURN_HOME,
        )
        StudyFlowTvMockExamStatus.PAUSED -> listOf(
            StudyFlowTvMockExamAction.RESUME,
            StudyFlowTvMockExamAction.SUBMIT,
            StudyFlowTvMockExamAction.RETURN_HOME,
        )
        StudyFlowTvMockExamStatus.SUBMITTED -> listOf(
            StudyFlowTvMockExamAction.REVIEW_RESULTS,
            StudyFlowTvMockExamAction.RETURN_HOME,
        )
        StudyFlowTvMockExamStatus.RESULTS -> listOf(
            StudyFlowTvMockExamAction.RETURN_HOME,
        )
        }
    }

    fun applyAction(
        state: StudyFlowTvMockExamState,
        action: StudyFlowTvMockExamAction,
    ): StudyFlowTvMockExamState = when (action) {
        // TVOV25: start/submission are canonical mutations; pause/mark are not faked locally.
        StudyFlowTvMockExamAction.START,
        StudyFlowTvMockExamAction.PAUSE,
        StudyFlowTvMockExamAction.RESUME,
        StudyFlowTvMockExamAction.MARK_FOR_REVIEW,
        StudyFlowTvMockExamAction.SUBMIT,
        -> state
        StudyFlowTvMockExamAction.NEXT_SECTION -> advanceSection(state)
        StudyFlowTvMockExamAction.REVIEW_RESULTS -> state.copy(status = StudyFlowTvMockExamStatus.RESULTS)
        StudyFlowTvMockExamAction.RETURN_HOME -> state
    }

    fun focusItems(state: StudyFlowTvMockExamState): List<StudyFlowTvFocusItem> {
        val sections = state.sections.map { section ->
            StudyFlowTvFocusItem(
                id = "mock-section-${section.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = section.row,
                column = section.column,
            )
        }
        val actions = availableActions(state).mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "mock-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = sectionColumnStart + index,
            )
        }
        return sections + actions
    }

    fun commandForAction(
        action: StudyFlowTvMockExamAction,
        state: StudyFlowTvMockExamState,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.MOCK_EXAM,
        action = controlActionFor(action),
        targetId = targetIdForAction(action, state),
        optionId = action.name.lowercase(),
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    fun castStateFor(
        state: StudyFlowTvMockExamState,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.MOCK_EXAM,
        title = state.title,
        subtitle = "${state.status.name.lowercase()} · ${state.activeSection.title}",
        progressPercent = progressPercentAt(state, updatedAtEpochMillis),
        activeItemId = state.activeSection.sourceId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun resultSummary(state: StudyFlowTvMockExamState): String =
        "${state.correctAnswerCount}/${state.totalQuestions} correct · ${state.answeredQuestionCount} answered · ${state.flaggedSectionIds.size} sections marked"

    fun liveElapsedMinutes(
        state: StudyFlowTvMockExamState,
        nowEpochMillis: Long,
    ): Int {
        val base = state.elapsedMinutes.coerceIn(0, state.totalMinutes)
        if (state.status != StudyFlowTvMockExamStatus.RUNNING || state.projectedAtEpochMillis <= 0L) return base
        val deltaMinutes = ((nowEpochMillis - state.projectedAtEpochMillis).coerceAtLeast(0L) / 60_000L).toInt()
        return (base + deltaMinutes).coerceIn(0, state.totalMinutes)
    }

    fun progressPercentAt(state: StudyFlowTvMockExamState, nowEpochMillis: Long): Int = when (state.status) {
        StudyFlowTvMockExamStatus.READY -> 0
        StudyFlowTvMockExamStatus.SUBMITTED,
        StudyFlowTvMockExamStatus.RESULTS,
        -> 100
        StudyFlowTvMockExamStatus.RUNNING,
        StudyFlowTvMockExamStatus.PAUSED,
        -> ((liveElapsedMinutes(state, nowEpochMillis) * 100) / state.totalMinutes).coerceIn(0, 99)
    }

    private fun targetIdForAction(
        action: StudyFlowTvMockExamAction,
        state: StudyFlowTvMockExamState,
    ): String = when (action) {
        StudyFlowTvMockExamAction.START,
        StudyFlowTvMockExamAction.PAUSE,
        StudyFlowTvMockExamAction.RESUME,
        StudyFlowTvMockExamAction.SUBMIT,
        StudyFlowTvMockExamAction.REVIEW_RESULTS,
        -> state.examId
        StudyFlowTvMockExamAction.NEXT_SECTION,
        StudyFlowTvMockExamAction.MARK_FOR_REVIEW,
        -> state.activeSection.sourceId
        StudyFlowTvMockExamAction.RETURN_HOME -> "home"
    }

    private fun advanceSection(state: StudyFlowTvMockExamState): StudyFlowTvMockExamState {
        val nextIndex = (state.activeSectionIndex + 1).coerceAtMost(state.sections.lastIndex)
        return state.copy(activeSectionIndex = nextIndex)
    }

    private fun controlActionFor(action: StudyFlowTvMockExamAction): StudyFlowTvControlAction = when (action) {
        StudyFlowTvMockExamAction.START -> StudyFlowTvControlAction.START
        StudyFlowTvMockExamAction.PAUSE -> StudyFlowTvControlAction.PAUSE
        StudyFlowTvMockExamAction.RESUME -> StudyFlowTvControlAction.RESUME
        StudyFlowTvMockExamAction.NEXT_SECTION -> StudyFlowTvControlAction.NEXT
        StudyFlowTvMockExamAction.MARK_FOR_REVIEW -> StudyFlowTvControlAction.MARK_FOR_REVIEW
        StudyFlowTvMockExamAction.SUBMIT -> StudyFlowTvControlAction.SUBMIT
        StudyFlowTvMockExamAction.REVIEW_RESULTS -> StudyFlowTvControlAction.REVIEW_RESULTS
        StudyFlowTvMockExamAction.RETURN_HOME -> StudyFlowTvControlAction.BACK
    }
}
