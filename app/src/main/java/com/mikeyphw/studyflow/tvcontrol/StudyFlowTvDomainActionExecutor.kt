package com.mikeyphw.studyflow.tvcontrol

import android.content.Context
import com.mikeyphw.studyflow.core.domain.ManageStudyPractice
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTestId
import java.util.UUID
import kotlinx.coroutines.flow.first
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult

/** TVOV25 phone/data-owner persistence contract. */
object StudyFlowTvDomainPersistenceContract {
    const val overlay = "TVOV25"
    const val phoneOwnsCanonicalMutations = true
    const val tvRepositoriesForbidden = true
    const val persistedManagersRequired = true
    const val duplicateActionReplayForbidden = true
    const val fakeMockScoringForbidden = true
    const val localOnlyReviewCompletionForbidden = true
    const val canonicalProjectionAfterMutationRequired = true
}

interface StudyFlowTvDomainActionJournal {
    fun read(actionId: String): StudyFlowTvDomainActionResult?
    fun write(result: StudyFlowTvDomainActionResult)
}

class SharedPreferencesStudyFlowTvDomainActionJournal(context: Context) : StudyFlowTvDomainActionJournal {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    override fun read(actionId: String): StudyFlowTvDomainActionResult? {
        val encoded = preferences.getString(key(actionId), null) ?: return null
        val fields = encoded.split('|', limit = 3)
        if (fields.size < 2) return null
        val ok = fields[0] == "1"
        val completedAt = fields[1].toLongOrNull() ?: return null
        val reason = fields.getOrNull(2)?.takeIf(String::isNotBlank)
        return runCatching {
            StudyFlowTvDomainActionResult(
                actionId = actionId,
                ok = ok,
                reason = if (ok) null else reason ?: "domain-action-failed",
                completedAtEpochMillis = completedAt,
            )
        }.getOrNull()
    }

    override fun write(result: StudyFlowTvDomainActionResult) {
        val value = listOf(
            if (result.ok) "1" else "0",
            result.completedAtEpochMillis.toString(),
            result.reason.orEmpty().replace('|', '-'),
        ).joinToString("|")
        preferences.edit().putString(key(result.actionId), value).apply()
    }

    private fun key(actionId: String): String = "action_" + actionId
        .filter { it.isLetterOrDigit() || it in "-_" }
        .take(160)

    private companion object {
        const val PREFERENCE_NAME = "studyflow_tv_domain_actions"
    }
}

/** Small seam that keeps the executor testable while production remains backed by real managers. */
fun interface StudyFlowTvCanonicalMutationGateway {
    suspend fun apply(action: StudyFlowTvDomainAction)
}

class DefaultStudyFlowTvCanonicalMutationGateway(
    private val sessionManager: ManageStudySession,
    private val practiceManager: ManageStudyPractice,
    private val reviewManager: ManageStudyReview,
    private val sessionRepository: StudySessionRepository,
    private val practiceRepository: StudyPracticeRepository,
    private val scheduleRepository: StudyScheduleRepository,
) : StudyFlowTvCanonicalMutationGateway {
    override suspend fun apply(action: StudyFlowTvDomainAction) {
        val planId = StudyPlanId(action.studyPlanId)
        when (action.kind) {
            StudyFlowTvDomainActionKind.SESSION_START -> {
                val activityId = PlannedActivityId(requireTarget(action))
                val activity = scheduleRepository.observeSchedule(planId).first().plannedActivities
                    .firstOrNull { it.id == activityId }
                    ?: error("Planned Study Activity does not exist.")
                sessionManager.startFromActivity(
                    sessionId = StudySessionId(UUID.randomUUID().toString()),
                    activity = activity,
                )
            }

            StudyFlowTvDomainActionKind.SESSION_PAUSE ->
                sessionManager.pause(planId, StudySessionId(requireTarget(action)))

            StudyFlowTvDomainActionKind.SESSION_RESUME ->
                sessionManager.resume(planId, StudySessionId(requireTarget(action)))

            StudyFlowTvDomainActionKind.SESSION_COMPLETE -> {
                val sessionId = StudySessionId(requireTarget(action))
                val current = requireNotNull(sessionRepository.findSession(planId, sessionId)) {
                    "Study Session does not exist."
                }
                require(current.status == StudySessionStatus.RUNNING || current.status == StudySessionStatus.PAUSED) {
                    "Only an active Study Session can be completed."
                }
                sessionManager.complete(
                    planId = planId,
                    sessionId = sessionId,
                    pagesCompleted = current.pagesCompleted,
                    questionsAnswered = current.questionsAnswered,
                    correctAnswers = current.correctAnswers,
                    concentrationRating = current.concentrationRating,
                    difficultyRating = current.difficultyRating,
                    notes = current.notes,
                )
            }

            StudyFlowTvDomainActionKind.PRACTICE_ANSWER -> {
                val testId = StudyTestId(requireContext(action))
                val questionId = QuestionId(requireTarget(action))
                val test = requireNotNull(practiceRepository.findTest(planId, testId)) {
                    "Practice Test does not exist."
                }
                if (test.startedAtEpochMillis == null) practiceManager.startPracticeTest(planId, testId)
                practiceManager.answerPracticeQuestion(
                    planId = planId,
                    testId = testId,
                    questionId = questionId,
                    chosenAnswer = requireNotNull(action.answerText) { "Practice answer is missing." },
                    elapsedSeconds = 0L,
                )
            }

            StudyFlowTvDomainActionKind.PRACTICE_MARK_REVIEW ->
                reviewManager.scheduleQuestionReview(
                    planId = planId,
                    questionId = QuestionId(requireTarget(action)),
                    grade = action.reviewGrade ?: 3,
                )

            StudyFlowTvDomainActionKind.PRACTICE_FINISH -> {
                val testId = StudyTestId(requireContext(action))
                val test = requireNotNull(practiceRepository.findTest(planId, testId)) {
                    "Practice Test does not exist."
                }
                require(test.startedAtEpochMillis != null) { "Practice Test has not started." }
                practiceManager.finishPracticeAttempt(planId, testId)
            }

            StudyFlowTvDomainActionKind.MOCK_START ->
                practiceManager.startMockExamAttempt(planId, MockExamId(requireContext(action)))

            StudyFlowTvDomainActionKind.MOCK_FINISH -> {
                val mockId = MockExamId(requireContext(action))
                val mock = requireNotNull(practiceRepository.findMockExam(planId, mockId)) {
                    "Mock Exam does not exist."
                }
                require(mock.startedAtEpochMillis != null) { "Mock Exam has not started." }
                practiceManager.finishMockExamAttempt(planId, mockId)
            }

            StudyFlowTvDomainActionKind.REVIEW_GRADE ->
                reviewManager.gradeReview(
                    planId = planId,
                    reviewItemId = ReviewItemId(requireTarget(action)),
                    grade = requireNotNull(action.reviewGrade) { "Review grade is missing." },
                )

            StudyFlowTvDomainActionKind.REVIEW_ERROR_NOTE -> {
                val questionId = QuestionId(requireContext(action))
                val question = requireNotNull(practiceRepository.findQuestion(planId, questionId)) {
                    "Review source question does not exist."
                }
                reviewManager.createErrorFromQuestion(
                    id = ErrorEntryId(UUID.randomUUID().toString()),
                    question = question,
                    category = ErrorCategory.OTHER,
                    chosenAnswer = null,
                )
            }
        }
    }

    private fun requireContext(action: StudyFlowTvDomainAction): String =
        requireNotNull(action.contextId?.takeIf(String::isNotBlank)) { "Domain action context is required." }

    private fun requireTarget(action: StudyFlowTvDomainAction): String =
        requireNotNull(action.targetId?.takeIf(String::isNotBlank)) { "Domain action target is required." }
}

class StudyFlowTvDomainActionExecutor(
    private val gateway: StudyFlowTvCanonicalMutationGateway,
    private val journal: StudyFlowTvDomainActionJournal,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(action: StudyFlowTvDomainAction): StudyFlowTvDomainActionResult {
        journal.read(action.actionId)?.let { return it }
        val result = try {
            gateway.apply(action)
            StudyFlowTvDomainActionResult(
                actionId = action.actionId,
                ok = true,
                completedAtEpochMillis = clock(),
            )
        } catch (error: Exception) {
            StudyFlowTvDomainActionResult(
                actionId = action.actionId,
                ok = false,
                reason = failureReason(action.kind, error),
                completedAtEpochMillis = clock(),
            )
        }
        journal.write(result)
        return result
    }

    private fun failureReason(kind: StudyFlowTvDomainActionKind, error: Exception): String {
        val type = error::class.simpleName?.lowercase() ?: "exception"
        return "domain-${kind.name.lowercase().replace('_', '-')}-$type"
    }
}
