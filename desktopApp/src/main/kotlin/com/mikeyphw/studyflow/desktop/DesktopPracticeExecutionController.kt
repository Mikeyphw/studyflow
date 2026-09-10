package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.PracticeExecutionState
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import java.util.UUID

data class DesktopPracticeExecutionState(
    val attempt: StudyTest?,
    val execution: PracticeExecutionState?,
    val currentQuestion: DesktopSafeQuestion?,
    val recentAttempts: List<StudyTest>,
) {
    val canStart: Boolean get() = attempt != null && attempt.startedAtEpochMillis == null
    val canAnswer: Boolean get() = execution?.let { !it.submitted && it.startedAtEpochMillis != null } == true
    val canFinish: Boolean get() = canAnswer
}

data class DesktopSafeQuestion(
    val id: QuestionId,
    val statement: String,
    val options: List<String>,
    val favorite: Boolean,
    val disputed: Boolean,
    val explanation: String?,
)

class DesktopPracticeExecutionController(
    private val services: DesktopStudyFlowServices,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun state(): DesktopPracticeExecutionState {
        val planId = services.defaultPlanId ?: return DesktopPracticeExecutionState(null, null, null, emptyList())
        val questions = services.practiceRepository.currentQuestions(planId)
        val attempts = services.practiceRepository.currentTests(planId)
        val current = attempts.firstOrNull { it.submittedAtEpochMillis == null }
        val execution = current?.let { services.manageStudyPractice.stateForTest(it, questions) }
        return DesktopPracticeExecutionState(
            attempt = current,
            execution = execution,
            currentQuestion = execution?.currentQuestion?.toSafe(execution),
            recentAttempts = attempts,
        )
    }

    suspend fun createAttempt(title: String, mode: TestMode = TestMode.PRACTICE): StudyTest {
        val planId = requireActivePlan()
        val questions = services.practiceRepository.currentQuestions(planId)
        require(questions.isNotEmpty()) { "No questions are available for this Study Plan." }
        return services.manageStudyPractice.createPracticeTest(
            id = StudyTestId(idFactory()),
            planId = planId,
            title = title.trim().ifBlank { "Desktop practice" },
            questions = questions,
            mode = mode,
        )
    }

    suspend fun start(testId: StudyTestId? = state().attempt?.id): StudyTest {
        val planId = requireActivePlan()
        return services.manageStudyPractice.startPracticeTest(
            planId,
            requireNotNull(testId) { "Create a Practice Test first." },
        )
    }

    suspend fun answerCurrent(chosenAnswer: String, elapsedSeconds: Long = 0L): StudyTest {
        val planId = requireActivePlan()
        val current = requireNotNull(state().execution) { "No active Practice Test." }
        val question = requireNotNull(current.currentQuestion) { "Practice Test has no questions." }
        return services.manageStudyPractice.answerPracticeQuestion(
            planId = planId,
            testId = StudyTestId(current.attemptId),
            questionId = question.id,
            chosenAnswer = chosenAnswer,
            elapsedSeconds = elapsedSeconds,
        )
    }

    suspend fun finish(): StudyTest {
        val planId = requireActivePlan()
        val current = requireNotNull(state().execution) { "No active Practice Test." }
        return services.manageStudyPractice.finishPracticeAttempt(planId, StudyTestId(current.attemptId))
    }

    suspend fun toggleFavorite(questionId: QuestionId) = services.manageStudyPractice.toggleFavorite(
        requireActivePlan(),
        questionId,
    )

    suspend fun toggleDisputed(questionId: QuestionId) = services.manageStudyPractice.toggleDisputed(
        requireActivePlan(),
        questionId,
    )

    private fun requireActivePlan() = requireNotNull(services.currentDefaultPlanForDesktop()) {
        "No Study Plan is selected."
    }.also { plan ->
        require(plan.status == StudyPlanStatus.ACTIVE) { "Only active Study Plans can run practice." }
    }.id

    private fun Question.toSafe(execution: PracticeExecutionState): DesktopSafeQuestion = DesktopSafeQuestion(
        id = id,
        statement = statement,
        options = options,
        favorite = favorite,
        disputed = disputed,
        explanation = explanation.takeIf { execution.explanationVisible(id) },
    )
}
