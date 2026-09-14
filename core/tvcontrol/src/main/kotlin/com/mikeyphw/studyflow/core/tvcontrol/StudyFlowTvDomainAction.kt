package com.mikeyphw.studyflow.core.tvcontrol

import kotlinx.serialization.Serializable

/**
 * TVOV25 canonical mutation contract. The TV never owns StudyFlow business state; it requests a
 * mutation from the paired data-owning phone and waits for the next canonical presentation.
 */
object StudyFlowTvDomainActionProtocol {
    const val version = 1
    const val canonicalDataOwnerRequired = true
    const val tvRepositoryMutationForbidden = true
    const val optimisticScoringForbidden = true
    const val maxPendingActions = 32

    fun requiresPersistence(command: StudyFlowTvControlCommand): Boolean = when (command.surface) {
        StudyFlowTvSurface.STUDY_SESSION -> command.action in setOf(
            StudyFlowTvControlAction.START,
            StudyFlowTvControlAction.PAUSE,
            StudyFlowTvControlAction.RESUME,
            StudyFlowTvControlAction.COMPLETE,
        )
        StudyFlowTvSurface.QUESTION_SET -> command.action in setOf(
            StudyFlowTvControlAction.SELECT_ANSWER,
            StudyFlowTvControlAction.MARK_FOR_REVIEW,
            StudyFlowTvControlAction.COMPLETE,
        )
        StudyFlowTvSurface.MOCK_EXAM -> command.action in setOf(
            StudyFlowTvControlAction.START,
            StudyFlowTvControlAction.PAUSE,
            StudyFlowTvControlAction.RESUME,
            StudyFlowTvControlAction.SUBMIT,
        )
        StudyFlowTvSurface.REVIEW -> command.action in setOf(
            StudyFlowTvControlAction.COMPLETE,
            StudyFlowTvControlAction.MARK_FOR_REVIEW,
            StudyFlowTvControlAction.RECLASSIFY,
            StudyFlowTvControlAction.RESOLVE,
            StudyFlowTvControlAction.REOPEN,
        )
        StudyFlowTvSurface.PROGRESS -> command.action in setOf(
            StudyFlowTvControlAction.COMPLETE,
            StudyFlowTvControlAction.PAUSE,
            StudyFlowTvControlAction.RESUME,
        )
        else -> false
    }
}

@Serializable
enum class StudyFlowTvDomainActionKind {
    SESSION_START,
    SESSION_PAUSE,
    SESSION_RESUME,
    SESSION_COMPLETE,
    PRACTICE_ANSWER,
    PRACTICE_MARK_REVIEW,
    PRACTICE_FINISH,
    MOCK_START,
    MOCK_PAUSE,
    MOCK_RESUME,
    MOCK_FINISH,
    REVIEW_GRADE,
    REVIEW_ERROR_NOTE,
    ERROR_RECLASSIFY,
    ERROR_RESOLVE,
    ERROR_REOPEN,
    GOAL_COMPLETE,
    GOAL_PAUSE,
    GOAL_RESUME,
}

@Serializable
data class StudyFlowTvDomainAction(
    val actionId: String,
    val protocolVersion: Int = StudyFlowTvDomainActionProtocol.version,
    val studyPlanId: String,
    val kind: StudyFlowTvDomainActionKind,
    val contextId: String? = null,
    val targetId: String? = null,
    val optionId: String? = null,
    val answerText: String? = null,
    val reviewGrade: Int? = null,
    val errorCategory: String? = null,
    val requestedAtEpochMillis: Long,
    val origin: StudyFlowTvControlOrigin,
) {
    init {
        require(actionId.isNotBlank()) { "TV domain action id must not be blank." }
        require(protocolVersion == StudyFlowTvDomainActionProtocol.version) { "Unsupported TV domain action version." }
        require(studyPlanId.isNotBlank()) { "TV domain action must identify a Study Plan." }
        require(requestedAtEpochMillis >= 0L) { "TV domain action time cannot be negative." }
        if (kind == StudyFlowTvDomainActionKind.PRACTICE_ANSWER) {
            require(!contextId.isNullOrBlank()) { "Practice answer requires an attempt id." }
            require(!targetId.isNullOrBlank()) { "Practice answer requires a question id." }
            require(!answerText.isNullOrBlank()) { "Practice answer requires canonical answer text." }
        }
        if (kind == StudyFlowTvDomainActionKind.REVIEW_GRADE) {
            require(!targetId.isNullOrBlank()) { "Review grade requires a review item id." }
            require(reviewGrade in 0..5) { "Review grade must be between 0 and 5." }
        }
        if (kind in setOf(
                StudyFlowTvDomainActionKind.ERROR_RECLASSIFY,
                StudyFlowTvDomainActionKind.ERROR_RESOLVE,
                StudyFlowTvDomainActionKind.ERROR_REOPEN,
            )
        ) {
            require(!targetId.isNullOrBlank()) { "Error Notebook mutation requires an error entry id." }
            require(!contextId.isNullOrBlank()) { "Error Notebook mutation requires a source question id." }
        }
        if (kind in setOf(StudyFlowTvDomainActionKind.ERROR_RECLASSIFY, StudyFlowTvDomainActionKind.ERROR_REOPEN)) {
            require(!errorCategory.isNullOrBlank()) { "Error Notebook category is required." }
        }
    }
}

@Serializable
data class StudyFlowTvDomainActionResult(
    val actionId: String,
    val ok: Boolean,
    val reason: String? = null,
    val completedAtEpochMillis: Long,
) {
    init {
        require(actionId.isNotBlank()) { "TV domain action result id must not be blank." }
        require(completedAtEpochMillis >= 0L) { "TV domain action result time cannot be negative." }
        if (!ok) require(!reason.isNullOrBlank()) { "Failed TV domain action must include a reason." }
    }
}

/** Maps a presentation-safe command into the canonical mutation that the phone must execute. */
object StudyFlowTvDomainActionMapper {
    fun fromCommand(
        command: StudyFlowTvControlCommand,
        snapshot: StudyFlowTvPresentationSnapshot?,
        origin: StudyFlowTvControlOrigin,
    ): StudyFlowTvDomainAction? {
        if (!StudyFlowTvDomainActionProtocol.requiresPersistence(command)) return null
        val current = snapshot?.takeIf { it.availability == StudyFlowTvPresentationAvailability.READY } ?: return null
        val planId = current.studyPlanId
        val kind = when (command.surface) {
            StudyFlowTvSurface.STUDY_SESSION -> when (command.action) {
                StudyFlowTvControlAction.START -> StudyFlowTvDomainActionKind.SESSION_START
                StudyFlowTvControlAction.PAUSE -> StudyFlowTvDomainActionKind.SESSION_PAUSE
                StudyFlowTvControlAction.RESUME -> StudyFlowTvDomainActionKind.SESSION_RESUME
                StudyFlowTvControlAction.COMPLETE -> StudyFlowTvDomainActionKind.SESSION_COMPLETE
                else -> return null
            }
            StudyFlowTvSurface.QUESTION_SET -> when (command.action) {
                StudyFlowTvControlAction.SELECT_ANSWER -> StudyFlowTvDomainActionKind.PRACTICE_ANSWER
                StudyFlowTvControlAction.MARK_FOR_REVIEW -> StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW
                StudyFlowTvControlAction.COMPLETE -> StudyFlowTvDomainActionKind.PRACTICE_FINISH
                else -> return null
            }
            StudyFlowTvSurface.MOCK_EXAM -> when (command.action) {
                StudyFlowTvControlAction.START -> StudyFlowTvDomainActionKind.MOCK_START
                StudyFlowTvControlAction.PAUSE -> StudyFlowTvDomainActionKind.MOCK_PAUSE
                StudyFlowTvControlAction.RESUME -> StudyFlowTvDomainActionKind.MOCK_RESUME
                StudyFlowTvControlAction.SUBMIT -> StudyFlowTvDomainActionKind.MOCK_FINISH
                else -> return null
            }
            StudyFlowTvSurface.REVIEW -> when (command.action) {
                StudyFlowTvControlAction.COMPLETE -> StudyFlowTvDomainActionKind.REVIEW_GRADE
                StudyFlowTvControlAction.MARK_FOR_REVIEW -> StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE
                StudyFlowTvControlAction.RECLASSIFY -> StudyFlowTvDomainActionKind.ERROR_RECLASSIFY
                StudyFlowTvControlAction.RESOLVE -> StudyFlowTvDomainActionKind.ERROR_RESOLVE
                StudyFlowTvControlAction.REOPEN -> StudyFlowTvDomainActionKind.ERROR_REOPEN
                else -> return null
            }
            StudyFlowTvSurface.PROGRESS -> when (command.action) {
                StudyFlowTvControlAction.COMPLETE -> StudyFlowTvDomainActionKind.GOAL_COMPLETE
                StudyFlowTvControlAction.PAUSE -> StudyFlowTvDomainActionKind.GOAL_PAUSE
                StudyFlowTvControlAction.RESUME -> StudyFlowTvDomainActionKind.GOAL_RESUME
                else -> return null
            }
            else -> return null
        }

        val questionSet = current.questionSet
        val question = if (kind in setOf(
                StudyFlowTvDomainActionKind.PRACTICE_ANSWER,
                StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW,
            )
        ) {
            val requested = command.targetId ?: questionSet?.currentQuestionId
            questionSet?.questions?.firstOrNull { it.questionId == requested }
        } else null
        val choice = question?.let { resolveChoice(it, command.optionId) }
        val review = current.review
        val reviewItem = review?.items?.firstOrNull { item ->
            item.reviewItemId == (command.targetId ?: review.items.firstOrNull()?.reviewItemId) ||
                item.errorEntryId == command.targetId
        }

        return StudyFlowTvDomainAction(
            actionId = command.commandId,
            studyPlanId = planId,
            kind = kind,
            contextId = when (kind) {
                StudyFlowTvDomainActionKind.PRACTICE_ANSWER,
                StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW,
                StudyFlowTvDomainActionKind.PRACTICE_FINISH,
                -> questionSet?.questionSetId
                StudyFlowTvDomainActionKind.MOCK_START,
                StudyFlowTvDomainActionKind.MOCK_PAUSE,
                StudyFlowTvDomainActionKind.MOCK_RESUME,
                StudyFlowTvDomainActionKind.MOCK_FINISH,
                -> current.mockExam?.mockExamId
                StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE -> reviewItem?.questionId
                StudyFlowTvDomainActionKind.ERROR_RECLASSIFY,
                StudyFlowTvDomainActionKind.ERROR_RESOLVE,
                StudyFlowTvDomainActionKind.ERROR_REOPEN,
                -> reviewItem?.questionId
                else -> null
            },
            targetId = when (kind) {
                StudyFlowTvDomainActionKind.SESSION_START -> current.today?.nextActivity?.activityId
                StudyFlowTvDomainActionKind.SESSION_PAUSE,
                StudyFlowTvDomainActionKind.SESSION_RESUME,
                StudyFlowTvDomainActionKind.SESSION_COMPLETE,
                -> current.studySession?.sessionId
                StudyFlowTvDomainActionKind.PRACTICE_ANSWER,
                StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW,
                -> command.targetId ?: question?.questionId ?: questionSet?.currentQuestionId
                StudyFlowTvDomainActionKind.MOCK_START,
                StudyFlowTvDomainActionKind.MOCK_PAUSE,
                StudyFlowTvDomainActionKind.MOCK_RESUME,
                StudyFlowTvDomainActionKind.MOCK_FINISH,
                -> current.mockExam?.mockExamId
                StudyFlowTvDomainActionKind.REVIEW_GRADE,
                StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE,
                -> command.targetId ?: reviewItem?.reviewItemId
                StudyFlowTvDomainActionKind.ERROR_RECLASSIFY,
                StudyFlowTvDomainActionKind.ERROR_RESOLVE,
                StudyFlowTvDomainActionKind.ERROR_REOPEN,
                -> command.targetId ?: reviewItem?.errorEntryId
                StudyFlowTvDomainActionKind.GOAL_COMPLETE,
                StudyFlowTvDomainActionKind.GOAL_PAUSE,
                StudyFlowTvDomainActionKind.GOAL_RESUME,
                -> command.targetId
                else -> command.targetId
            },
            optionId = choice?.optionId ?: command.optionId,
            answerText = choice?.label,
            reviewGrade = when (kind) {
                StudyFlowTvDomainActionKind.REVIEW_GRADE -> reviewGradeFor(command.optionId)
                StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW -> 3
                else -> null
            },
            errorCategory = when (kind) {
                StudyFlowTvDomainActionKind.ERROR_RECLASSIFY,
                StudyFlowTvDomainActionKind.ERROR_REOPEN,
                -> command.optionId ?: reviewItem?.errorCategory
                else -> null
            },
            requestedAtEpochMillis = command.issuedAtEpochMillis,
            origin = origin,
        ).takeIf { action ->
            when (action.kind) {
                StudyFlowTvDomainActionKind.PRACTICE_ANSWER ->
                    !action.contextId.isNullOrBlank() &&
                        !action.targetId.isNullOrBlank() &&
                        !action.answerText.isNullOrBlank()
                StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW ->
                    !action.targetId.isNullOrBlank()
                StudyFlowTvDomainActionKind.PRACTICE_FINISH,
                -> !action.contextId.isNullOrBlank()
                StudyFlowTvDomainActionKind.MOCK_START,
                StudyFlowTvDomainActionKind.MOCK_PAUSE,
                StudyFlowTvDomainActionKind.MOCK_RESUME,
                StudyFlowTvDomainActionKind.MOCK_FINISH,
                -> !action.contextId.isNullOrBlank() && !action.targetId.isNullOrBlank()
                StudyFlowTvDomainActionKind.SESSION_START,
                StudyFlowTvDomainActionKind.SESSION_PAUSE,
                StudyFlowTvDomainActionKind.SESSION_RESUME,
                StudyFlowTvDomainActionKind.SESSION_COMPLETE,
                StudyFlowTvDomainActionKind.REVIEW_GRADE,
                StudyFlowTvDomainActionKind.GOAL_COMPLETE,
                StudyFlowTvDomainActionKind.GOAL_PAUSE,
                StudyFlowTvDomainActionKind.GOAL_RESUME,
                -> !action.targetId.isNullOrBlank()
                StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE,
                StudyFlowTvDomainActionKind.ERROR_RESOLVE,
                -> !action.targetId.isNullOrBlank() && !action.contextId.isNullOrBlank()
                StudyFlowTvDomainActionKind.ERROR_RECLASSIFY,
                StudyFlowTvDomainActionKind.ERROR_REOPEN,
                -> !action.targetId.isNullOrBlank() &&
                    !action.contextId.isNullOrBlank() &&
                    !action.errorCategory.isNullOrBlank()
            }
        }
    }

    private fun reviewGradeFor(optionId: String?): Int = when (optionId?.trim()?.lowercase()) {
        "known", "mark_known", "mark-known" -> 5
        "complete", "complete_review", "complete-review" -> 4
        "again", "forgot" -> 1
        else -> 4
    }

    private fun resolveChoice(
        question: StudyFlowTvQuestionPresentation,
        optionId: String?,
    ): StudyFlowTvQuestionChoicePresentation? {
        val raw = optionId?.trim()?.takeIf(String::isNotBlank) ?: return null
        question.choices.firstOrNull { it.optionId.equals(raw, ignoreCase = true) }?.let { return it }
        val index = when {
            raw.toIntOrNull() != null -> raw.toInt()
            raw.length == 1 && raw[0].uppercaseChar() in 'A'..'D' -> raw[0].uppercaseChar() - 'A'
            else -> -1
        }
        return question.choices.getOrNull(index)
    }
}
