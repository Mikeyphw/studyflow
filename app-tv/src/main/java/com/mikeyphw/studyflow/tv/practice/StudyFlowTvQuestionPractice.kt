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
    QUESTION_FOUR,
    QUESTION_FIVE,
    QUESTION_SIX,
    QUESTION_SEVEN,
    QUESTION_EIGHT,
    QUESTION_NINE,
    QUESTION_TEN,
    QUESTION_ELEVEN,
    QUESTION_TWELVE,
    QUESTION_THIRTEEN,
    QUESTION_FOURTEEN,
    QUESTION_FIFTEEN,
    QUESTION_SIXTEEN,
    QUESTION_SEVENTEEN,
    QUESTION_EIGHTEEN,
    QUESTION_NINETEEN,
    QUESTION_TWENTY,
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
    val sourceId: String = id.name.lowercase(),
    val prompt: String,
    val choices: List<StudyFlowTvPracticeChoice>,
    val correctChoiceId: StudyFlowTvPracticeChoiceId,
    val explanation: String,
    val row: Int,
    val column: Int,
) {
    init {
        require(sourceId.isNotBlank()) { "TV practice source id must not be blank." }
        require(prompt.isNotBlank()) { "TV practice prompt must not be blank." }
        require(choices.size in 2..StudyFlowTvQuestionPractice.choiceCount) {
            "TV practice question must expose between 2 and ${StudyFlowTvQuestionPractice.choiceCount} choices."
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
    val canPersistActions: Boolean = true,
    val availabilityReason: String? = null,
) {
    init {
        require(questionSetId.isNotBlank()) { "TV practice question set id must not be blank." }
        require(title.isNotBlank()) { "TV practice title must not be blank." }
        require(subject.isNotBlank()) { "TV practice subject must not be blank." }
        require(questions.isNotEmpty()) { "TV practice must have questions." }
        require(questions.size <= StudyFlowTvQuestionPractice.questionCount) {
            "TV practice must not exceed the remote-reachable projection size."
        }
        require(activeQuestionIndex in questions.indices) { "TV practice active question index is out of bounds." }
        require(availabilityReason == null || availabilityReason.isNotBlank()) {
            "TV practice availability reason must not be blank when present."
        }
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
    const val ov19TruthOverlay = "OV19"
    const val ownsQuestionPracticeSurface = true
    const val remoteCanSelectAnswersAndRevealExplanation = true
    const val pairedPhoneCanSelectAnswersAndNavigateQuestions = true
    const val emitsCastStateWithoutMirroring = true
    const val usesSharedControlProtocol = true
    const val questionCount = 20
    const val choiceCount = 4
    const val actionCount = 8
    const val choiceRow = 0
    const val actionRow = 1
    const val contentColumnStart = 1

    fun defaultState(): StudyFlowTvQuestionPracticeState = StudyFlowTvQuestionPracticeState(
        questionSetId = "tv-practice-core-1",
        title = "Question practice",
        subject = "Concurso basics",
        questions = StudyFlowTvPracticeQuestionId.entries.take(questionCount).mapIndexed { index, id ->
            val correct = StudyFlowTvPracticeChoiceId.entries[index % StudyFlowTvPracticeChoiceId.entries.size]
            StudyFlowTvPracticeQuestion(
                id = id,
                sourceId = "tv-practice-question-${index + 1}",
                prompt = "Practice question ${index + 1}: choose the StudyFlow-safe answer.",
                choices = defaultChoices(
                    a = "Persist through the phone domain owner",
                    b = "Mutate only local TV memory",
                    c = "Skip canonical evidence",
                    d = "Use a private fixture row",
                ),
                correctChoiceId = correct,
                explanation = "Every TV answer is projected from, and persisted by, canonical StudyFlow domain state.",
                row = index / 4,
                column = contentColumnStart + (index % 4),
            )
        },
    )

    fun availableActions(state: StudyFlowTvQuestionPracticeState): List<StudyFlowTvPracticeAction> {
        if (!state.canPersistActions || state.completed) {
            return listOfNotNull(
                StudyFlowTvPracticeAction.PREVIOUS_QUESTION.takeIf { state.activeQuestionIndex > 0 },
                StudyFlowTvPracticeAction.NEXT_QUESTION.takeIf { state.activeQuestionIndex < state.questions.lastIndex },
                StudyFlowTvPracticeAction.SHOW_EXPLANATION.takeIf { state.selectedChoiceId != null },
            )
        }
        val selectionActions = state.activeQuestion.choices.map { choice ->
            when (choice.id) {
                StudyFlowTvPracticeChoiceId.A -> StudyFlowTvPracticeAction.SELECT_A
                StudyFlowTvPracticeChoiceId.B -> StudyFlowTvPracticeAction.SELECT_B
                StudyFlowTvPracticeChoiceId.C -> StudyFlowTvPracticeAction.SELECT_C
                StudyFlowTvPracticeChoiceId.D -> StudyFlowTvPracticeAction.SELECT_D
            }
        }
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
        // TVOV25: answer selection is canonical only after the phone persists and re-projects it.
        StudyFlowTvPracticeAction.SELECT_A,
        StudyFlowTvPracticeAction.SELECT_B,
        StudyFlowTvPracticeAction.SELECT_C,
        StudyFlowTvPracticeAction.SELECT_D,
        -> state
        StudyFlowTvPracticeAction.PREVIOUS_QUESTION -> state.copy(
            activeQuestionIndex = (state.activeQuestionIndex - 1).coerceAtLeast(0),
            explanationVisible = false,
        )
        StudyFlowTvPracticeAction.NEXT_QUESTION -> state.copy(
            activeQuestionIndex = (state.activeQuestionIndex + 1).coerceAtMost(state.questions.lastIndex),
            explanationVisible = false,
        )
        StudyFlowTvPracticeAction.SHOW_EXPLANATION -> state.copy(explanationVisible = state.selectedChoiceId != null)
        StudyFlowTvPracticeAction.FINISH_SET -> state
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
        targetId = state.activeQuestion.sourceId,
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
        activeItemId = state.activeQuestion.sourceId,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    fun isCurrentAnswerCorrect(state: StudyFlowTvQuestionPracticeState): Boolean? =
        state.selectedChoiceId?.let { it == state.activeQuestion.correctChoiceId }

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
