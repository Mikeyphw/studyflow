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
    RECLASSIFY_ERROR,
    RESOLVE_ERROR,
    REOPEN_ERROR,
    RETURN_TODAY,
}

data class StudyFlowTvReviewItem(
    val id: StudyFlowTvReviewId,
    val sourceId: String = id.name.lowercase(),
    val title: String,
    val subject: String,
    val reason: String,
    val confidencePercent: Int,
    val dueNow: Boolean,
    val errorEntryId: String? = null,
    val errorCategory: String? = null,
    val questionId: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "TV review source id must not be blank." }
        require(title.isNotBlank()) { "TV review title must not be blank." }
        require(subject.isNotBlank()) { "TV review subject must not be blank." }
        require(reason.isNotBlank()) { "TV review reason must not be blank." }
        require(confidencePercent in 0..100) { "TV review confidence must be between 0 and 100." }
        if (errorEntryId != null) require(!errorCategory.isNullOrBlank())
    }
}

data class StudyFlowTvReviewState(
    val status: StudyFlowTvReviewStatus,
    val activeReviewIndex: Int,
    val items: List<StudyFlowTvReviewItem>,
    val knownReviewIds: Set<StudyFlowTvReviewId> = emptySet(),
    val errorNoteIds: Set<StudyFlowTvReviewId> = emptySet(),
    val explanationVisible: Boolean = false,
    val locallyResolvedSourceIds: Set<String> = emptySet(),
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
    const val introducedOverlay = "TVOV11"
    const val overlay = "TVOV29"
    const val ownsReviewAndErrorNotebookSurface = true
    const val spacedReviewQueueVisible = true
    const val errorNotebookCanBeOpenedFromReview = true
    const val remoteCanReviewMarkKnownExplainAndComplete = true
    const val pairedPhoneCanControlReviewNotebook = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val reviewItemCount = 4
    const val reviewActionCount = 10
    const val errorNotebookMutationParity = true
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

    fun availableActions(state: StudyFlowTvReviewState): List<StudyFlowTvReviewAction> {
        val base = when (state.status) {
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
        val errorId = state.activeReview.errorEntryId ?: return base
        val mutation = if (errorId in state.locallyResolvedSourceIds) {
            listOf(StudyFlowTvReviewAction.REOPEN_ERROR)
        } else {
            listOf(StudyFlowTvReviewAction.RECLASSIFY_ERROR, StudyFlowTvReviewAction.RESOLVE_ERROR)
        }
        return (mutation + base).distinct()
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
        // TVOV25: review grading/error evidence is canonical only after persisted phone execution.
        StudyFlowTvReviewAction.MARK_KNOWN,
        StudyFlowTvReviewAction.ADD_ERROR_NOTE,
        -> state
        StudyFlowTvReviewAction.SHOW_EXPLANATION -> state.copy(
            status = StudyFlowTvReviewStatus.EXPLANATION,
            explanationVisible = true,
        )
        StudyFlowTvReviewAction.COMPLETE_REVIEW -> state
        StudyFlowTvReviewAction.RECLASSIFY_ERROR -> state
        StudyFlowTvReviewAction.RESOLVE_ERROR -> state.copy(
            locallyResolvedSourceIds = state.locallyResolvedSourceIds + listOfNotNull(state.activeReview.errorEntryId),
        )
        StudyFlowTvReviewAction.REOPEN_ERROR -> state.copy(
            locallyResolvedSourceIds =
                state.locallyResolvedSourceIds - listOfNotNull(state.activeReview.errorEntryId).toSet(),
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
                    StudyFlowTvReviewAction.ADD_ERROR_NOTE,
                    StudyFlowTvReviewAction.RECLASSIFY_ERROR,
                    StudyFlowTvReviewAction.RESOLVE_ERROR,
                    StudyFlowTvReviewAction.REOPEN_ERROR,
                    -> StudyFlowTvFocusRole.DIALOG_ACTION
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
        val active = state.activeReview
        val isErrorMutation = action in setOf(
            StudyFlowTvReviewAction.RECLASSIFY_ERROR,
            StudyFlowTvReviewAction.RESOLVE_ERROR,
            StudyFlowTvReviewAction.REOPEN_ERROR,
        )
        return StudyFlowTvControlCommand(
            commandId = commandId,
            surface = StudyFlowTvSurface.REVIEW,
            action = controlActionFor(action),
            targetId = if (isErrorMutation) active.errorEntryId else active.sourceId,
            optionId = if (action == StudyFlowTvReviewAction.RECLASSIFY_ERROR) nextErrorCategory(active.errorCategory)
                else if (action == StudyFlowTvReviewAction.REOPEN_ERROR) active.errorCategory
                else null,
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
        activeItemId = state.activeReview.sourceId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun destinationForAction(action: StudyFlowTvReviewAction): StudyFlowTvDestination = when (action) {
        StudyFlowTvReviewAction.RETURN_TODAY -> StudyFlowTvDestination.TODAY
        else -> StudyFlowTvDestination.REVIEWS
    }

    private val errorCategories = listOf(
        "CONTENT_GAP",
        "FORGOTTEN",
        "CONCEPT_CONFUSION",
        "INTERPRETATION",
        "DISTRACTION",
        "CALCULATION",
        "TIME_PRESSURE",
        "DISPUTED",
        "OTHER",
    )

    fun nextErrorCategory(current: String?): String {
        val index = errorCategories.indexOf(current?.uppercase())
        return errorCategories[(index + 1).mod(errorCategories.size)]
    }

    private fun controlActionFor(action: StudyFlowTvReviewAction): StudyFlowTvControlAction = when (action) {
        StudyFlowTvReviewAction.START_REVIEW -> StudyFlowTvControlAction.START
        StudyFlowTvReviewAction.NEXT_ITEM -> StudyFlowTvControlAction.NEXT
        StudyFlowTvReviewAction.MARK_KNOWN -> StudyFlowTvControlAction.COMPLETE
        StudyFlowTvReviewAction.ADD_ERROR_NOTE -> StudyFlowTvControlAction.MARK_FOR_REVIEW
        StudyFlowTvReviewAction.SHOW_EXPLANATION -> StudyFlowTvControlAction.SHOW_EXPLANATION
        StudyFlowTvReviewAction.COMPLETE_REVIEW -> StudyFlowTvControlAction.COMPLETE
        StudyFlowTvReviewAction.RECLASSIFY_ERROR -> StudyFlowTvControlAction.RECLASSIFY
        StudyFlowTvReviewAction.RESOLVE_ERROR -> StudyFlowTvControlAction.RESOLVE
        StudyFlowTvReviewAction.REOPEN_ERROR -> StudyFlowTvControlAction.REOPEN
        StudyFlowTvReviewAction.RETURN_TODAY -> StudyFlowTvControlAction.BACK
    }
}
