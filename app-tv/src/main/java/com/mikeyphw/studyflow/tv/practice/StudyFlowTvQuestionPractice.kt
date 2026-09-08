package com.mikeyphw.studyflow.tv.practice

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvCastState
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole

/**
 * Structured Android TV question-practice surface introduced in TVOV09.
 *
 * The TV practice model is self-contained, remote-first, and phone-controllable. It gives the TV
 * APK a real answer-selection and explanation flow without depending on Room, repositories,
 * phone feature modules, Compose, AndroidX TV UI, Cast SDK, or MediaRouter.
 */
enum class StudyFlowTvPracticeQuestionId {
    QUESTION_ONE,
    QUESTION_TWO,
    QUESTION_THREE,
}

enum class StudyFlowTvPracticeChoiceId(val optionId: String) {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
}

enum class StudyFlowTvPracticeAction {
    SELECT_A,
    SELECT_B,
    SELECT_C,
    SELECT_D,
    PREVIOUS_QUESTION,
    NEXT_QUESTION,
    SHOW_EXPLANATION,
    FINISH_SET,
}

data class StudyFlowTvPracticeChoice(
    val id: StudyFlowTvPracticeChoiceId,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "TV practice choice label must not be blank." }
    }
}

data class StudyFlowTvPracticeQuestion(
    val id: StudyFlowTvPracticeQuestionId,
    val prompt: String,
    val choices: List<StudyFlowTvPracticeChoice>,
    val correctChoiceId: StudyFlowTvPracticeChoiceId,
    val explanation: String,
    val row: Int,
    val column: Int,
) {
    init {
        require(prompt.isNotBlank()) { "TV practice prompt must not be blank." }
        require(choices.size == StudyFlowTvQuestionPractice.choiceCount) {
            "TV practice question must expose exactly ${StudyFlowTvQuestionPractice.choiceCount} choices."
        }
        require(choices.map { it.id }.toSet().size == choices.size) { "TV practice choices must be unique." }
        require(choices.any { it.id == correctChoiceId }) { "TV practice correct choice must exist." }
        require(explanation.isNotBlank()) { "TV practice explanation must not be blank." }
        require(row >= 0) { "TV practice question row must be non-negative." }
        require(column >= 0) { "TV practice question column must be non-negative." }
    }
}

data class StudyFlowTvQuestionPracticeState(
    val questionSetId: String,
    val title: String,
    val subject: String,
    val questions: List<StudyFlowTvPracticeQuestion>,
    val activeQuestionIndex: Int = 0,
    val selectedChoices: Map<StudyFlowTvPracticeQuestionId, StudyFlowTvPracticeChoiceId> = emptyMap(),
    val explanationVisible: Boolean = false,
    val completed: Boolean = false,
) {
    init {
        require(questionSetId.isNotBlank()) { "TV practice question set id must not be blank." }
        require(title.isNotBlank()) { "TV practice title must not be blank." }
        require(subject.isNotBlank()) { "TV practice subject must not be blank." }
        require(questions.isNotEmpty()) { "TV practice must have questions." }
        require(activeQuestionIndex in questions.indices) { "TV practice active question index is out of bounds." }
    }

    val activeQuestion: StudyFlowTvPracticeQuestion
        get() = questions[activeQuestionIndex]

    val answeredCount: Int
        get() = questions.count { it.id in selectedChoices }

    val progressPercent: Int
        get() = ((answeredCount * 100) / questions.size).coerceIn(0, 100)

    val selectedChoiceId: StudyFlowTvPracticeChoiceId?
        get() = selectedChoices[activeQuestion.id]

    val correctCount: Int
        get() = questions.count { question -> selectedChoices[question.id] == question.correctChoiceId }
}

object StudyFlowTvQuestionPractice {
    const val overlay = "TVOV09"
    const val ownsQuestionPracticeSurface = true
    const val remoteCanSelectAnswersAndRevealExplanation = true
    const val pairedPhoneCanSelectAnswersAndNavigateQuestions = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val questionCount = 3
    const val choiceCount = 4
    const val actionCount = 8
    const val choiceRow = 0
    const val actionRow = 1
    const val contentColumnStart = 1

    fun defaultState(): StudyFlowTvQuestionPracticeState = StudyFlowTvQuestionPracticeState(
        questionSetId = "tv-practice-core-1",
        title = "Question practice",
        subject = "Concurso basics",
        questions = listOf(
            StudyFlowTvPracticeQuestion(
                id = StudyFlowTvPracticeQuestionId.QUESTION_ONE,
                prompt = "Which action keeps TV practice under user control?",
                choices = defaultChoices(
                    a = "Auto-advance without review",
                    b = "Select answer and reveal explanation",
                    c = "Skip all feedback",
                    d = "Open phone-only editor",
                ),
                correctChoiceId = StudyFlowTvPracticeChoiceId.B,
                explanation = "TV practice must support answer selection, explanation reveal, and explicit navigation.",
                row = 0,
                column = contentColumnStart,
            ),
            StudyFlowTvPracticeQuestion(
                id = StudyFlowTvPracticeQuestionId.QUESTION_TWO,
                prompt = "What must paired-phone control send to the TV?",
                choices = defaultChoices(
                    a = "Raw database rows",
                    b = "Arbitrary UI trees",
                    c = "Small StudyFlow control commands",
                    d = "Unscoped file paths",
                ),
                correctChoiceId = StudyFlowTvPracticeChoiceId.C,
                explanation = "Phone control sends small commands through the shared TV protocol, not private storage data.",
                row = 0,
                column = contentColumnStart,
            ),
            StudyFlowTvPracticeQuestion(
                id = StudyFlowTvPracticeQuestionId.QUESTION_THREE,
                prompt = "Which TV affordance is required for couch practice?",
                choices = defaultChoices(
                    a = "Visible focused choices",
                    b = "Hidden touch-only actions",
                    c = "Mouse-only navigation",
                    d = "Desktop shortcut keys",
                ),
                correctChoiceId = StudyFlowTvPracticeChoiceId.A,
                explanation = "Question choices must be focusable, visible, and operable from a D-pad remote.",
                row = 0,
                column = contentColumnStart,
            ),
        ),
    )

    fun availableActions(state: StudyFlowTvQuestionPracticeState): List<StudyFlowTvPracticeAction> {
        val selectionActions = listOf(
            StudyFlowTvPracticeAction.SELECT_A,
            StudyFlowTvPracticeAction.SELECT_B,
            StudyFlowTvPracticeAction.SELECT_C,
            StudyFlowTvPracticeAction.SELECT_D,
        )
        val navigationActions = buildList {
            add(StudyFlowTvPracticeAction.PREVIOUS_QUESTION)
            add(StudyFlowTvPracticeAction.NEXT_QUESTION)
            if (state.selectedChoiceId != null) add(StudyFlowTvPracticeAction.SHOW_EXPLANATION)
            add(StudyFlowTvPracticeAction.FINISH_SET)
        }
        return selectionActions + navigationActions
    }

    fun applyAction(
        state: StudyFlowTvQuestionPracticeState,
        action: StudyFlowTvPracticeAction,
    ): StudyFlowTvQuestionPracticeState = when (action) {
        StudyFlowTvPracticeAction.SELECT_A -> state.select(StudyFlowTvPracticeChoiceId.A)
        StudyFlowTvPracticeAction.SELECT_B -> state.select(StudyFlowTvPracticeChoiceId.B)
        StudyFlowTvPracticeAction.SELECT_C -> state.select(StudyFlowTvPracticeChoiceId.C)
        StudyFlowTvPracticeAction.SELECT_D -> state.select(StudyFlowTvPracticeChoiceId.D)
        StudyFlowTvPracticeAction.PREVIOUS_QUESTION -> state.copy(
            activeQuestionIndex = (state.activeQuestionIndex - 1).coerceAtLeast(0),
            explanationVisible = false,
        )
        StudyFlowTvPracticeAction.NEXT_QUESTION -> state.copy(
            activeQuestionIndex = (state.activeQuestionIndex + 1).coerceAtMost(state.questions.lastIndex),
            explanationVisible = false,
        )
        StudyFlowTvPracticeAction.SHOW_EXPLANATION -> state.copy(explanationVisible = state.selectedChoiceId != null)
        StudyFlowTvPracticeAction.FINISH_SET -> state.copy(completed = true, explanationVisible = true)
    }

    fun focusItems(state: StudyFlowTvQuestionPracticeState): List<StudyFlowTvFocusItem> {
        val choices = state.activeQuestion.choices.mapIndexed { index, choice ->
            StudyFlowTvFocusItem(
                id = "practice-choice-${choice.id.optionId.lowercase()}",
                role = StudyFlowTvFocusRole.QUESTION_CHOICE,
                row = choiceRow,
                column = contentColumnStart + index,
            )
        }
        val actions = availableActions(state).mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "practice-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = actionRow,
                column = contentColumnStart + index,
            )
        }
        return choices + actions
    }

    fun commandForAction(
        action: StudyFlowTvPracticeAction,
        state: StudyFlowTvQuestionPracticeState,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = StudyFlowTvSurface.QUESTION_SET,
        action = controlActionFor(action),
        targetId = state.activeQuestion.id.name.lowercase(),
        optionId = choiceIdFor(action)?.optionId,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    fun castStateFor(
        state: StudyFlowTvQuestionPracticeState,
        castId: String,
        updatedAtEpochMillis: Long,
    ): StudyFlowTvCastState = StudyFlowTvCastState(
        castId = castId,
        surface = StudyFlowTvSurface.QUESTION_SET,
        title = state.title,
        subtitle = "${state.subject} · question ${state.activeQuestionIndex + 1}/${state.questions.size}",
        progressPercent = state.progressPercent,
        activeItemId = state.activeQuestion.id.name.lowercase(),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun isCurrentAnswerCorrect(state: StudyFlowTvQuestionPracticeState): Boolean? =
        state.selectedChoiceId?.let { it == state.activeQuestion.correctChoiceId }

    private fun StudyFlowTvQuestionPracticeState.select(choiceId: StudyFlowTvPracticeChoiceId): StudyFlowTvQuestionPracticeState =
        copy(
            selectedChoices = selectedChoices + (activeQuestion.id to choiceId),
            explanationVisible = false,
        )

    private fun defaultChoices(
        a: String,
        b: String,
        c: String,
        d: String,
    ): List<StudyFlowTvPracticeChoice> = listOf(
        StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.A, a),
        StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.B, b),
        StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.C, c),
        StudyFlowTvPracticeChoice(StudyFlowTvPracticeChoiceId.D, d),
    )

    private fun choiceIdFor(action: StudyFlowTvPracticeAction): StudyFlowTvPracticeChoiceId? = when (action) {
        StudyFlowTvPracticeAction.SELECT_A -> StudyFlowTvPracticeChoiceId.A
        StudyFlowTvPracticeAction.SELECT_B -> StudyFlowTvPracticeChoiceId.B
        StudyFlowTvPracticeAction.SELECT_C -> StudyFlowTvPracticeChoiceId.C
        StudyFlowTvPracticeAction.SELECT_D -> StudyFlowTvPracticeChoiceId.D
        StudyFlowTvPracticeAction.PREVIOUS_QUESTION,
        StudyFlowTvPracticeAction.NEXT_QUESTION,
        StudyFlowTvPracticeAction.SHOW_EXPLANATION,
        StudyFlowTvPracticeAction.FINISH_SET,
        -> null
    }

    private fun controlActionFor(action: StudyFlowTvPracticeAction): StudyFlowTvControlAction = when (action) {
        StudyFlowTvPracticeAction.SELECT_A,
        StudyFlowTvPracticeAction.SELECT_B,
        StudyFlowTvPracticeAction.SELECT_C,
        StudyFlowTvPracticeAction.SELECT_D,
        -> StudyFlowTvControlAction.SELECT_ANSWER
        StudyFlowTvPracticeAction.PREVIOUS_QUESTION -> StudyFlowTvControlAction.PREVIOUS
        StudyFlowTvPracticeAction.NEXT_QUESTION -> StudyFlowTvControlAction.NEXT
        StudyFlowTvPracticeAction.SHOW_EXPLANATION -> StudyFlowTvControlAction.SHOW_EXPLANATION
        StudyFlowTvPracticeAction.FINISH_SET -> StudyFlowTvControlAction.COMPLETE
    }
}
