package com.mikeyphw.studyflow.tv.reviews

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvNavigationShell

/**
 * Structured Android TV Review and error-notebook surface introduced in TVOV11.
 *
 * Reviews on TV are not a placeholder destination. The surface owns a small state machine,
 * D-pad focus targets, paired-phone command mapping, and cast-state output while staying clear of
 * Room, repositories, phone feature modules, Compose Runtime, AndroidX TV UI, MediaRouter, and
 * Cast SDK dependencies.
 */
enum class StudyFlowTvReviewId {
    CONSTITUTION_FLASHCARDS,
    ADMINISTRATIVE_LAW_ERRORS,
    PORTUGUESE_GRAMMAR,
    INFORMATICS_SHORT_NOTES,
}

enum class StudyFlowTvReviewStatus {
    READY,
    REVIEWING,
    EXPLANATION,
    ERROR_NOTEBOOK,
    COMPLETED,
}

enum class StudyFlowTvReviewAction {
    START_REVIEW,
    NEXT_ITEM,
    MARK_KNOWN,
    ADD_ERROR_NOTE,
    SHOW_EXPLANATION,
    COMPLETE_REVIEW,
    RETURN_TODAY,
}

data class StudyFlowTvReviewItem(
    val id: StudyFlowTvReviewId,
    val title: String,
    val subject: String,
    val reason: String,
    val confidencePercent: Int,
    val dueNow: Boolean,
) {
    init {
        require(title.isNotBlank()) { "TV review title must not be blank." }
        require(subject.isNotBlank()) { "TV review subject must not be blank." }
        require(reason.isNotBlank()) { "TV review reason must not be blank." }
        require(confidencePercent in 0..100) { "TV review confidence must be between 0 and 100." }
    }
}

data class StudyFlowTvReviewState(
    val status: StudyFlowTvReviewStatus,
    val activeReviewIndex: Int,
    val items: List<StudyFlowTvReviewItem>,
    val knownReviewIds: Set<StudyFlowTvReviewId> = emptySet(),
    val errorNoteIds: Set<StudyFlowTvReviewId> = emptySet(),
    val explanationVisible: Boolean = false,
) {
    init {
        require(items.isNotEmpty()) { "TV review surface requires at least one review item." }
        require(activeReviewIndex in items.indices) { "TV review active index must point at a review item." }
    }

    val activeReview: StudyFlowTvReviewItem = items[activeReviewIndex]
    val reviewedCount: Int = knownReviewIds.size + errorNoteIds.size
    val progressPercent: Int = ((reviewedCount.coerceAtMost(items.size) * 100) / items.size).coerceIn(0, 100)
    val dueCount: Int = items.count { it.dueNow }
}

object StudyFlowTvReviewNotebook {
    const val overlay = "TVOV11"
    const val ownsReviewAndErrorNotebookSurface = true
    const val spacedReviewQueueVisible = true
    const val errorNotebookCanBeOpenedFromReview = true
    const val remoteCanReviewMarkKnownExplainAndComplete = true
    const val pairedPhoneCanControlReviewNotebook = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val reviewItemCount = 4
    const val reviewActionCount = 7
    const val reviewItemRow = 0
    const val actionRow = 2

    fun defaultState(): StudyFlowTvReviewState = StudyFlowTvReviewState(
        status = StudyFlowTvReviewStatus.READY,
        activeReviewIndex = 0,
        items = listOf(
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.CONSTITUTION_FLASHCARDS,
                title = "Constitution flashcards",
                subject = "Constitutional Law",
                reason = "Due spaced review from the weekly plan",
                confidencePercent = 64,
                dueNow = true,
            ),
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.ADMINISTRATIVE_LAW_ERRORS,
                title = "Administrative law errors",
                subject = "Administrative Law",
                reason = "Repeated errors from practice sessions",
                confidencePercent = 48,
                dueNow = true,
            ),
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.PORTUGUESE_GRAMMAR,
                title = "Portuguese grammar traps",
                subject = "Portuguese",
                reason = "Error-notebook follow-up",
                confidencePercent = 58,
                dueNow = false,
            ),
            StudyFlowTvReviewItem(
                id = StudyFlowTvReviewId.INFORMATICS_SHORT_NOTES,
                title = "Informatics short notes",
                subject = "Informatics",
                reason = "Low-confidence checkpoint",
                confidencePercent = 52,
                dueNow = false,
            ),
        ),
    )

    fun availableActions(state: StudyFlowTvReviewState): List<StudyFlowTvReviewAction> = when (state.status) {
        StudyFlowTvReviewStatus.READY -> listOf(
            StudyFlowTvReviewAction.START_REVIEW,
            StudyFlowTvReviewAction.SHOW_EXPLANATION,
            StudyFlowTvReviewAction.ADD_ERROR_NOTE,
            StudyFlowTvReviewAction.RETURN_TODAY,
        )
        StudyFlowTvReviewStatus.REVIEWING -> listOf(
            StudyFlowTvReviewAction.MARK_KNOWN,
            StudyFlowTvReviewAction.ADD_ERROR_NOTE,
            StudyFlowTvReviewAction.SHOW_EXPLANATION,
            StudyFlowTvReviewAction.NEXT_ITEM,
            StudyFlowTvReviewAction.COMPLETE_REVIEW,
        )
        StudyFlowTvReviewStatus.EXPLANATION -> listOf(
            StudyFlowTvReviewAction.MARK_KNOWN,
            StudyFlowTvReviewAction.ADD_ERROR_NOTE,
            StudyFlowTvReviewAction.NEXT_ITEM,
            StudyFlowTvReviewAction.COMPLETE_REVIEW,
        )
        StudyFlowTvReviewStatus.ERROR_NOTEBOOK -> listOf(
            StudyFlowTvReviewAction.MARK_KNOWN,
            StudyFlowTvReviewAction.SHOW_EXPLANATION,
            StudyFlowTvReviewAction.NEXT_ITEM,
            StudyFlowTvReviewAction.COMPLETE_REVIEW,
        )
        StudyFlowTvReviewStatus.COMPLETED -> listOf(StudyFlowTvReviewAction.RETURN_TODAY)
    }

    fun applyAction(
        state: StudyFlowTvReviewState,
        action: StudyFlowTvReviewAction,
    ): StudyFlowTvReviewState = when (action) {
        StudyFlowTvReviewAction.START_REVIEW -> state.copy(
            status = StudyFlowTvReviewStatus.REVIEWING,
            explanationVisible = false,
        )
        StudyFlowTvReviewAction.NEXT_ITEM -> state.copy(
            status = StudyFlowTvReviewStatus.REVIEWING,
            activeReviewIndex = (state.activeReviewIndex + 1).coerceAtMost(state.items.lastIndex),
            explanationVisible = false,
        )
        StudyFlowTvReviewAction.MARK_KNOWN -> advanceAfterMark(
            state.copy(
                knownReviewIds = state.knownReviewIds + state.activeReview.id,
                explanationVisible = false,
            ),
        )
        StudyFlowTvReviewAction.ADD_ERROR_NOTE -> state.copy(
            status = StudyFlowTvReviewStatus.ERROR_NOTEBOOK,
            errorNoteIds = state.errorNoteIds + state.activeReview.id,
            explanationVisible = false,
        )
        StudyFlowTvReviewAction.SHOW_EXPLANATION -> state.copy(
            status = StudyFlowTvReviewStatus.EXPLANATION,
            explanationVisible = true,
        )
        StudyFlowTvReviewAction.COMPLETE_REVIEW -> state.copy(
            status = StudyFlowTvReviewStatus.COMPLETED,
            explanationVisible = false,
        )
        StudyFlowTvReviewAction.RETURN_TODAY -> state
    }

    fun focusItems(state: StudyFlowTvReviewState): List<StudyFlowTvFocusItem> =
        state.items.mapIndexed { index, item ->
            StudyFlowTvFocusItem(
                id = "review-item-${item.id.name.lowercase()}",
                role = StudyFlowTvFocusRole.CONTENT_CARD,
                row = reviewItemRow + (index / 2),
                column = 1 + (index % 2),
                enabled = state.status != StudyFlowTvReviewStatus.COMPLETED,
            )
        } + availableActions(state).mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "review-action-${action.name.lowercase()}",
                role = when (action) {
                    StudyFlowTvReviewAction.ADD_ERROR_NOTE -> StudyFlowTvFocusRole.DIALOG_ACTION
                    else -> StudyFlowTvFocusRole.PRIMARY_ACTION
                },
                row = actionRow,
                column = 1 + index,
            )
        }

    fun commandForAction(
        action: StudyFlowTvReviewAction,
        state: StudyFlowTvReviewState,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand {
        if (action == StudyFlowTvReviewAction.RETURN_TODAY) {
            return StudyFlowTvNavigationShell.openCommandFor(
                destination = StudyFlowTvDestination.TODAY,
                commandId = commandId,
                issuedAtEpochMillis = issuedAtEpochMillis,
            )
        }
        return StudyFlowTvControlCommand(
            commandId = commandId,
            surface = StudyFlowTvSurface.REVIEW,
            action = controlActionFor(action),
            targetId = state.activeReview.id.name.lowercase(),
            issuedAtEpochMillis = issuedAtEpochMillis,
        )
    }

    fun castStateFor(
        state: StudyFlowTvReviewState,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.REVIEW,
        title = state.activeReview.title,
        subtitle = "${state.status.name.lowercase()} · ${state.activeReview.subject}",
        progressPercent = state.progressPercent,
        activeItemId = state.activeReview.id.name.lowercase(),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun destinationForAction(action: StudyFlowTvReviewAction): StudyFlowTvDestination = when (action) {
        StudyFlowTvReviewAction.RETURN_TODAY -> StudyFlowTvDestination.TODAY
        else -> StudyFlowTvDestination.REVIEWS
    }

    private fun advanceAfterMark(state: StudyFlowTvReviewState): StudyFlowTvReviewState {
        val done = state.knownReviewIds.size + state.errorNoteIds.size >= state.items.size
        return if (done) {
            state.copy(status = StudyFlowTvReviewStatus.COMPLETED)
        } else {
            state.copy(
                status = StudyFlowTvReviewStatus.REVIEWING,
                activeReviewIndex = (state.activeReviewIndex + 1).coerceAtMost(state.items.lastIndex),
            )
        }
    }

    private fun controlActionFor(action: StudyFlowTvReviewAction): StudyFlowTvControlAction = when (action) {
        StudyFlowTvReviewAction.START_REVIEW -> StudyFlowTvControlAction.START
        StudyFlowTvReviewAction.NEXT_ITEM -> StudyFlowTvControlAction.NEXT
        StudyFlowTvReviewAction.MARK_KNOWN -> StudyFlowTvControlAction.COMPLETE
        StudyFlowTvReviewAction.ADD_ERROR_NOTE -> StudyFlowTvControlAction.MARK_FOR_REVIEW
        StudyFlowTvReviewAction.SHOW_EXPLANATION -> StudyFlowTvControlAction.SHOW_EXPLANATION
        StudyFlowTvReviewAction.COMPLETE_REVIEW -> StudyFlowTvControlAction.COMPLETE
        StudyFlowTvReviewAction.RETURN_TODAY -> StudyFlowTvControlAction.BACK
    }
}
