package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.MockExamStudioBlueprint
import com.mikeyphw.studyflow.core.domain.MockExamStudioRequest
import com.mikeyphw.studyflow.core.domain.PracticeExecutionState
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.util.UUID

data class DesktopMockExamStudioState(
    val preview: MockExamStudioBlueprint,
    val activeExam: MockExam?,
    val execution: PracticeExecutionState?,
    val currentQuestion: DesktopMockSafeQuestion?,
    val recentExams: List<MockExam>,
) {
    val canCreate: Boolean get() = preview.executable && activeExam == null
    val canStart: Boolean get() = activeExam != null && activeExam.startedAtEpochMillis == null
    val canAnswer: Boolean get() = execution?.let { !it.submitted && it.startedAtEpochMillis != null } == true
    val canFinish: Boolean get() = canAnswer
}

data class DesktopMockSafeQuestion(
    val id: QuestionId,
    val statement: String,
    val options: List<String>,
    val explanation: String?,
)

class DesktopMockExamStudioController(
    private val services: DesktopStudyFlowServices,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun state(request: MockExamStudioRequest = defaultRequest()): DesktopMockExamStudioState {
        val planId = services.defaultPlanId
        val questions = planId?.let(services.practiceRepository::currentQuestions).orEmpty()
        val preview = services.manageStudyPractice.previewMockExamStudio(questions, request)
        val exams = planId?.let(services.practiceRepository::currentMockExams).orEmpty()
        val active = exams.firstOrNull { it.submittedAtEpochMillis == null }
        val execution = active?.let { services.manageStudyPractice.stateForMockExam(it, questions) }
        return DesktopMockExamStudioState(
            preview = preview,
            activeExam = active,
            execution = execution,
            currentQuestion = execution?.currentQuestion?.toSafe(execution),
            recentExams = exams,
        )
    }

    suspend fun create(request: MockExamStudioRequest = defaultRequest()): MockExam {
        val planId = requireActivePlan()
        val questions = services.practiceRepository.currentQuestions(planId)
        val effective = request.copy(
            questionCount = request.questionCount.coerceAtMost(questions.size.coerceAtLeast(1)),
        )
        return services.manageStudyPractice.createMockExamFromStudio(
            id = MockExamId(idFactory()),
            planId = planId,
            questions = questions,
            request = effective,
        )
    }

    suspend fun start(mockExamId: MockExamId? = state().activeExam?.id): MockExam {
        val planId = requireActivePlan()
        return services.manageStudyPractice.startMockExamAttempt(
            planId,
            requireNotNull(mockExamId) { "Create a Mock Exam first." },
        )
    }

    suspend fun answerCurrent(chosenAnswer: String, elapsedSeconds: Long = 0L): MockExam {
        val planId = requireActivePlan()
        val current = requireNotNull(state().execution) { "No active Mock Exam." }
        val question = requireNotNull(current.currentQuestion) { "Mock Exam has no questions." }
        return services.manageStudyPractice.answerMockQuestion(
            planId = planId,
            mockExamId = MockExamId(current.attemptId),
            questionId = question.id,
            chosenAnswer = chosenAnswer,
            elapsedSeconds = elapsedSeconds,
        )
    }

    suspend fun finish(): MockExam {
        val planId = requireActivePlan()
        val current = requireNotNull(state().execution) { "No active Mock Exam." }
        return services.manageStudyPractice.finishMockExamAttempt(planId, MockExamId(current.attemptId))
    }

    private fun defaultRequest(): MockExamStudioRequest {
        val count = services.defaultPlanId?.let(services.practiceRepository::currentQuestions)?.size.orZero()
        return MockExamStudioRequest(
            title = "Desktop Mock Exam",
            questionCount = count.coerceIn(1, 50),
            timeLimitMinutes = 120,
            minimumScorePercent = 60,
        )
    }

    private fun requireActivePlan() = requireNotNull(services.currentDefaultPlanForDesktop()) {
        "No Study Plan is selected."
    }.also { plan ->
        require(plan.status == StudyPlanStatus.ACTIVE) { "Only active Study Plans can run Mock Exams." }
    }.id

    private fun Question.toSafe(execution: PracticeExecutionState): DesktopMockSafeQuestion = DesktopMockSafeQuestion(
        id = id,
        statement = statement,
        options = options,
        explanation = explanation.takeIf { execution.explanationVisible(id) },
    )

    private fun Int?.orZero(): Int = this ?: 0
}
