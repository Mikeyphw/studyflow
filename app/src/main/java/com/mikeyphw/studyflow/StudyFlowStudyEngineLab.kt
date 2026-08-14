package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticArea
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticOutcome
import com.mikeyphw.studyflow.core.diagnostics.DiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.NoOpDiagnosticRecorder
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnostic
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineDiagnosticKind
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabActionResult
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabMessage
import com.mikeyphw.studyflow.core.diagnostics.StudyEngineLabReport
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.PracticeAnalytics
import com.mikeyphw.studyflow.core.domain.PracticeExecutionEngine
import com.mikeyphw.studyflow.core.domain.PracticeDeckRequest
import com.mikeyphw.studyflow.core.domain.MockExamStudioPlanner
import com.mikeyphw.studyflow.core.domain.MockExamStudioRequest
import com.mikeyphw.studyflow.core.domain.SpacedRepetitionScheduler
import com.mikeyphw.studyflow.core.domain.StudyLocalCheckpointReadinessAuditor
import com.mikeyphw.studyflow.core.domain.StudySchedulePlanner
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

fun interface StudyEngineInspectionOperation {
    suspend fun inspect(planId: StudyPlanId): StudyEngineLabReport
}

class StudyFlowStudyEngineLab(
    private val operation: StudyEngineInspectionOperation,
    private val defaultPlanId: () -> StudyPlanId?,
    private val recorder: DiagnosticRecorder = NoOpDiagnosticRecorder,
    private val executionContext: CoroutineContext = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        container: AppContainer,
        defaultPlanId: () -> StudyPlanId?,
    ) : this(
        operation = StudyEngineInspectionOperation { planId ->
            inspectStudyEngine(container, planId, System.currentTimeMillis())
        },
        defaultPlanId = defaultPlanId,
        recorder = container.diagnosticRecorder,
    )

    suspend fun inspect(): Pair<StudyEngineLabReport, StudyEngineLabActionResult> = withContext(executionContext) {
        recordEvent(DiagnosticOutcome.STARTED)
        val planId = defaultPlanId()
        if (planId == null) {
            val report = StudyEngineLabReport(
                status = DebugLabStatus.BLOCKED,
                message = StudyEngineLabMessage.MISSING_DEFAULT_PLAN,
                inspectedAtEpochMillis = clock(),
            )
            recordEvent(DiagnosticOutcome.BLOCKED, mapOf("reason" to "missing-default-plan"))
            return@withContext report to StudyEngineLabActionResult.MISSING_DEFAULT_PLAN
        }
        try {
            val report = operation.inspect(planId)
            recordEvent(
                DiagnosticOutcome.SUCCEEDED,
                mapOf(
                    "diagnostic-count" to report.diagnostics.size.toString(),
                    "passed-count" to report.passedCount.toString(),
                    "warning-count" to report.warningCount.toString(),
                    "empty-count" to report.emptyCount.toString(),
                    "not-applicable-count" to report.notApplicableCount.toString(),
                ),
            )
            report to StudyEngineLabActionResult.INSPECTED
        } catch (cancelled: CancellationException) {
            recordEvent(DiagnosticOutcome.CANCELLED)
            throw cancelled
        } catch (fatal: Error) {
            throw fatal
        } catch (_: Exception) {
            val report = StudyEngineLabReport(
                status = DebugLabStatus.FAILED,
                message = StudyEngineLabMessage.INSPECTION_FAILED,
                inspectedAtEpochMillis = clock(),
            )
            recordEvent(DiagnosticOutcome.FAILED)
            report to StudyEngineLabActionResult.FAILED
        }
    }

    private fun recordEvent(
        outcome: DiagnosticOutcome,
        details: Map<String, String> = emptyMap(),
    ) {
        recorder.record(
            area = DiagnosticArea.VALIDATION,
            action = "study-engine-lab-inspect",
            outcome = outcome,
            safeDetails = details,
        )
    }
}

private suspend fun inspectStudyEngine(
    container: AppContainer,
    planId: StudyPlanId,
    nowEpochMillis: Long,
): StudyEngineLabReport {
    val plan = requireNotNull(container.manageStudyPlan.observePlan(planId).first())
    val schedule = container.manageStudySchedule.observeSchedule(planId).first()
    var generatedIndex = 0
    val generatedActivities = StudySchedulePlanner(
        idFactory = { "debug-generated-${generatedIndex++}" },
    ).generateActivities(
        request = ScheduleGenerationRequest(
            studyPlanId = planId,
            startEpochMillis = nowEpochMillis,
            days = 7,
            replaceUnlocked = false,
        ),
        rules = schedule.scheduleRules,
    )
    val reviewQueue = container.manageStudyReview.observeQueue(planId).first()
    val questions = container.manageStudyPractice.observeQuestions(planId).first()
    val tests = container.manageStudyPractice.observeTests(planId).first()
    val mockExams = container.manageStudyPractice.observeMockExams(planId).first()
    val practiceSummary = PracticeAnalytics.summarize(questions, tests, mockExams)
    val deckRequest = PracticeDeckRequest(limit = 10)
    val practiceDeck = container.manageStudyPractice.buildDeck(questions, deckRequest)
    val recommendations = container.manageStudyDiscovery.recommendations(planId, nowEpochMillis)
    val searchQuery = plan.name.trim()
    val searchResults = if (searchQuery.length >= 2) container.manageStudyDiscovery.search(searchQuery) else emptyList()
    val progressSnapshot = container.manageStudyProgress.observeSnapshot(planId).first()
    val analyticsSnapshot = container.manageStudyAnalytics.observeSnapshot(planId).first()
    val syncState = container.manageStudyCloudSync.syncState.first()
    val syncSummary = container.manageStudyCloudSync.inspectLocalChanges()

    val scheduleExpansionValid = generatedActivities.map { it.id }.distinct().size == generatedActivities.size &&
        generatedActivities.all { activity ->
            activity.studyPlanId == planId &&
                activity.plannedDurationMinutes > 0 &&
                activity.scheduledStartEpochMillis != null &&
                activity.scheduledStartEpochMillis >= nowEpochMillis
        }
    val schedulePersistenceValid = schedule.plannedActivities.map { it.id }.distinct().size == schedule
        .plannedActivities.size &&
        schedule.plannedActivities.all { activity ->
            activity.studyPlanId == planId && activity.plannedDurationMinutes > 0
        }
    val queueItems = reviewQueue.due + reviewQueue.upcoming + reviewQueue.completedToday
    val reviewQueueValid = queueItems.map { it.id }.distinct().size == queueItems.size &&
        queueItems.all { it.studyPlanId == planId } &&
        reviewQueue.due.all { it.dueAtEpochMillis <= nowEpochMillis } &&
        reviewQueue.upcoming.all { it.dueAtEpochMillis > nowEpochMillis }
    val practiceDeckValid = practiceDeck.size <= deckRequest.limit &&
        practiceDeck.map { it.id }.distinct().size == practiceDeck.size &&
        practiceDeck.all { deckQuestion -> questions.any { it.id == deckQuestion.id } } &&
        practiceDeck == PracticeAnalytics.buildDeck(questions, deckRequest)
    val mockExamValid = mockExams.all { mock ->
        mock.studyPlanId == planId &&
            mock.answeredCount in 0..mock.questionCount &&
            mock.correctCount in 0..mock.answeredCount &&
            (mock.scorePercent == null || mock.scorePercent in 0.0..100.0) &&
            (mock.submittedAtEpochMillis == null || mock.scorePercent != null)
    }
    val recommendationsValid = recommendations.map { it.id }.distinct().size == recommendations.size &&
        recommendations.all { recommendation ->
            recommendation.studyPlanId == planId && recommendation.reasons.isNotEmpty() && recommendation.score >= 0
        }
    val searchValid = searchResults.any { result ->
        result.studyPlanId == planId && result.type == GlobalSearchContentType.PLAN && result.id == planId.value
    }
    val progressHasEvidence = progressSnapshot.goals.isNotEmpty() || progressSnapshot.notes.isNotEmpty()
    val progressValid = progressSnapshot.goalProgressPercent in 0..100 &&
        progressSnapshot.goals.all { goal -> goal.studyPlanId == planId && goal.targetValue > 0.0 && goal
            .currentValue >= 0.0 } &&
        progressSnapshot.notes.all { note -> note.studyPlanId == planId }
    val analyticsHasEvidence = analyticsSnapshot.studiedMinutesLastSevenDays > 0 ||
        analyticsSnapshot.completedSessionsLastSevenDays > 0 ||
        analyticsSnapshot.answeredQuestions > 0 ||
        analyticsSnapshot.dueReviewCount > 0 ||
        analyticsSnapshot.overdueReviewCount > 0 ||
        analyticsSnapshot.weakAreas.isNotEmpty()
    val analyticsValid = analyticsSnapshot.studyPlanId == planId &&
        analyticsSnapshot.studiedMinutesLastSevenDays >= 0 &&
        analyticsSnapshot.completedSessionsLastSevenDays >= 0 &&
        analyticsSnapshot.answeredQuestions >= 0 &&
        analyticsSnapshot.dueReviewCount >= 0 &&
        analyticsSnapshot.overdueReviewCount in 0..analyticsSnapshot.dueReviewCount &&
        analyticsSnapshot.retentionEaseAverage >= 0.0 &&
        listOf(
            analyticsSnapshot.scheduleAdherencePercent,
            analyticsSnapshot.questionAccuracyPercent,
            analyticsSnapshot.goalCompletionPercent,
            analyticsSnapshot.contentProgressPercent,
            analyticsSnapshot.libraryProgressPercent,
            analyticsSnapshot.consistencyScorePercent,
        ).all { it in 0..100 }
    val canonicalCheckpointReady = StudyLocalCheckpointReadinessAuditor.localCheckpointReady(syncState, syncSummary)
    val syncValid = !syncState.checkpointReady || canonicalCheckpointReady

    val syntheticReview = ReviewItem(
        id = ReviewItemId("debug-srs-canary"),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        questionId = null,
        flashcardId = null,
        errorEntryId = null,
        method = ReviewMethod.SPACED_REPETITION,
        status = ReviewStatus.DUE,
        dueAtEpochMillis = nowEpochMillis,
        intervalDays = 0,
        repetitionCount = 0,
        easeFactor = 2.5,
        completedAtEpochMillis = null,
    )
    val scheduledCanaryA = SpacedRepetitionScheduler.schedule(syntheticReview, grade = 5,
        reviewedAtEpochMillis = nowEpochMillis)
    val scheduledCanaryB = SpacedRepetitionScheduler.schedule(syntheticReview, grade = 5,
        reviewedAtEpochMillis = nowEpochMillis)
    val srsCanaryValid = scheduledCanaryA == scheduledCanaryB &&
        scheduledCanaryA.status == ReviewStatus.SCHEDULED &&
        scheduledCanaryA.repetitionCount == 1 &&
        scheduledCanaryA.intervalDays == 1 &&
        scheduledCanaryA.dueAtEpochMillis > nowEpochMillis
    val syntheticQuestions = listOf(
        diagnosticQuestion(planId, "debug-practice-1", "A", nowEpochMillis),
        diagnosticQuestion(planId, "debug-practice-2", "B", nowEpochMillis),
    )
    val questionIdsConfig = "questionIds=${syntheticQuestions.joinToString(",") { it.id.value }}"
    val executionEngine = PracticeExecutionEngine(clock = { nowEpochMillis + 1_000L })
    val immediateTest = StudyTest(
        id = StudyTestId("debug-immediate"),
        studyPlanId = planId,
        title = "diagnostic-immediate",
        mode = TestMode.IMMEDIATE_FEEDBACK,
        timeLimitSeconds = 60L,
        questionCount = syntheticQuestions.size,
        answeredCount = 0,
        correctCount = 0,
        startedAtEpochMillis = nowEpochMillis,
        submittedAtEpochMillis = null,
        configurationJson = questionIdsConfig,
    )
    val practiceTest = immediateTest.copy(id = StudyTestId("debug-practice"), mode = TestMode.PRACTICE)
    val immediateAnswered = executionEngine.answer(
        executionEngine.stateForTest(immediateTest, syntheticQuestions),
        syntheticQuestions.first(),
        syntheticQuestions.first().correctAnswer,
        elapsedSeconds = 3L,
    )
    val practiceAnswered = executionEngine.answer(
        executionEngine.stateForTest(practiceTest, syntheticQuestions),
        syntheticQuestions.first(),
        syntheticQuestions.first().correctAnswer,
        elapsedSeconds = 3L,
    )
    val deadlineState = executionEngine.stateForTest(practiceTest, syntheticQuestions)
    val timedOutAnswerRejected = try {
        PracticeExecutionEngine(clock = { nowEpochMillis + 60_000L }).answer(
            deadlineState,
            syntheticQuestions.first(),
            syntheticQuestions.first().correctAnswer,
            elapsedSeconds = 60L,
        )
        false
    } catch (_: IllegalArgumentException) {
        true
    }
    val practiceCanaryValid = PracticeAnalytics.accuracyPercent(3, 4) == 75 &&
        PracticeAnalytics.buildDeck(questions, deckRequest) == PracticeAnalytics.buildDeck(questions, deckRequest) &&
        immediateAnswered.explanationVisible(syntheticQuestions.first().id) &&
        !practiceAnswered.explanationVisible(syntheticQuestions.first().id) &&
        deadlineState.deadlinePassed(nowEpochMillis + 60_000L) &&
        timedOutAnswerRejected

    val mockExam = MockExam(
        id = MockExamId("debug-mock-execution"),
        studyPlanId = planId,
        title = "diagnostic-mock",
        mode = TestMode.EXAM,
        timeLimitSeconds = 120L,
        questionCount = syntheticQuestions.size,
        answeredCount = 0,
        correctCount = 0,
        scorePercent = null,
        negativeMarking = 0.5,
        minimumScorePercent = 30,
        startedAtEpochMillis = nowEpochMillis,
        submittedAtEpochMillis = null,
        configurationJson = questionIdsConfig,
    )
    val mockState = executionEngine.stateForMockExam(mockExam, syntheticQuestions)
    val mockWithCorrect = executionEngine.answer(
        mockState,
        syntheticQuestions[0],
        syntheticQuestions[0].correctAnswer,
        elapsedSeconds = 5L,
    )
    val mockWithWrong = executionEngine.answer(
        mockWithCorrect,
        syntheticQuestions[1],
        "A",
        elapsedSeconds = 7L,
    )
    val mockSummary = executionEngine.finish(mockWithWrong)
    val passingMockState = executionEngine.answer(
        executionEngine.answer(
            mockState,
            syntheticQuestions[0],
            syntheticQuestions[0].correctAnswer,
            elapsedSeconds = 4L,
        ),
        syntheticQuestions[1],
        syntheticQuestions[1].correctAnswer,
        elapsedSeconds = 4L,
    )
    val passingMockSummary = executionEngine.finish(passingMockState)
    val terminalMockState = executionEngine.stateForMockExam(
        mockExam.copy(submittedAtEpochMillis = nowEpochMillis + 20_000L, scorePercent = mockSummary
            .scorePercent.toDouble()),
        syntheticQuestions,
    )
    val terminalMutationRejected = try {
        executionEngine.answer(
            terminalMockState,
            syntheticQuestions.first(),
            syntheticQuestions.first().correctAnswer,
            elapsedSeconds = 1L,
        )
        false
    } catch (_: IllegalArgumentException) {
        true
    }
    val terminalFinishRejected = try {
        executionEngine.finish(terminalMockState)
        false
    } catch (_: IllegalArgumentException) {
        true
    }
    val mockRequest = MockExamStudioRequest(questionCount = 1)
    val mockBlueprintA = MockExamStudioPlanner.preview(questions, mockRequest)
    val mockBlueprintB = MockExamStudioPlanner.preview(questions, mockRequest)
    val mockPlannerCanaryValid = mockBlueprintA == mockBlueprintB &&
        mockBlueprintA.questionCount <= mockRequest.questionCount &&
        mockBlueprintA.selectedQuestions.map { it.id }.distinct().size == mockBlueprintA.selectedQuestions.size &&
        mockSummary.answeredCount == 2 &&
        mockSummary.correctCount == 1 &&
        mockSummary.scorePercent == 25 &&
        mockSummary.passed == false &&
        mockSummary.elapsedSeconds == 12L &&
        passingMockSummary.scorePercent == 100 &&
        passingMockSummary.passed == true &&
        terminalMockState.submitted &&
        terminalMockState.explanationVisible(syntheticQuestions.first().id) &&
        terminalMutationRejected && terminalFinishRejected

    val diagnostics = listOf(
        diagnostic(
            StudyEngineDiagnosticKind.SCHEDULE_EXPANSION,
            evidenceCount = schedule.scheduleRules.size,
            valid = scheduleExpansionValid && (schedule.scheduleRules.isEmpty() || generatedActivities.isNotEmpty()),
            primary = schedule.scheduleRules.size,
            secondary = generatedActivities.size,
        ),
        diagnostic(
            StudyEngineDiagnosticKind.SCHEDULE_PERSISTENCE,
            evidenceCount = schedule.plannedActivities.size,
            valid = schedulePersistenceValid,
            primary = schedule.plannedActivities.size,
            secondary = schedule.pendingActivities.size,
        ),
        diagnostic(
            StudyEngineDiagnosticKind.REVIEW_QUEUE,
            evidenceCount = queueItems.size,
            valid = reviewQueueValid,
            primary = reviewQueue.dueCount,
            secondary = reviewQueue.upcomingCount,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SRS_SCHEDULER_CANARY,
            status = if (srsCanaryValid) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
            primaryCount = scheduledCanaryA.repetitionCount,
            secondaryCount = scheduledCanaryA.intervalDays,
        ),
        diagnostic(
            StudyEngineDiagnosticKind.PRACTICE_DECK,
            evidenceCount = questions.size,
            valid = practiceDeckValid,
            primary = practiceDeck.size,
            secondary = questions.size,
            percent = practiceSummary.accuracyPercent,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.PRACTICE_CANARY,
            status = if (practiceCanaryValid) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
            percent = PracticeAnalytics.accuracyPercent(3, 4),
        ),
        diagnostic(
            StudyEngineDiagnosticKind.MOCK_EXAM,
            evidenceCount = mockExams.size,
            valid = mockExamValid,
            primary = mockExams.size,
            secondary = mockExams.count { it.submittedAtEpochMillis != null },
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.MOCK_PLANNER_CANARY,
            status = if (mockPlannerCanaryValid) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
            primaryCount = mockBlueprintA.questionCount,
            secondaryCount = mockBlueprintA.warnings.size,
        ),
        diagnostic(
            StudyEngineDiagnosticKind.RECOMMENDATIONS,
            evidenceCount = recommendations.size,
            valid = recommendationsValid,
            primary = recommendations.size,
            score = recommendations.firstOrNull()?.score,
        ),
        if (searchQuery.length < 2) {
            StudyEngineDiagnostic(StudyEngineDiagnosticKind.SEARCH_QUERY, DebugLabStatus.NOT_APPLICABLE)
        } else {
            StudyEngineDiagnostic(
                kind = StudyEngineDiagnosticKind.SEARCH_QUERY,
                status = if (searchValid) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
                primaryCount = searchResults.size,
                secondaryCount = searchResults.map { it.type }.distinct().size,
            )
        },
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.PROGRESS_SNAPSHOT,
            status = when {
                !progressHasEvidence -> DebugLabStatus.EMPTY
                progressValid -> DebugLabStatus.PASSED
                else -> DebugLabStatus.FAILED
            },
            primaryCount = progressSnapshot.goals.size,
            secondaryCount = progressSnapshot.notes.size,
            percent = progressSnapshot.goalProgressPercent,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.ANALYTICS_SNAPSHOT,
            status = when {
                !analyticsHasEvidence -> DebugLabStatus.EMPTY
                analyticsValid -> DebugLabStatus.PASSED
                else -> DebugLabStatus.FAILED
            },
            primaryCount = analyticsSnapshot.insights.size,
            secondaryCount = analyticsSnapshot.weakAreas.size,
            percent = analyticsSnapshot.consistencyScorePercent,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SYNC_FOUNDATION,
            status = when {
                !syncSummary.hasLocalData -> DebugLabStatus.EMPTY
                syncValid -> DebugLabStatus.PASSED
                else -> DebugLabStatus.FAILED
            },
            primaryCount = syncSummary.planCount,
            secondaryCount = syncSummary.activePlanCount,
        ),
    )
    val status = when {
        diagnostics.any { it.status == DebugLabStatus.FAILED } -> DebugLabStatus.FAILED
        diagnostics.any { it.status == DebugLabStatus.BLOCKED } -> DebugLabStatus.BLOCKED
        diagnostics.any { it.status == DebugLabStatus.WARNING } -> DebugLabStatus.WARNING
        diagnostics.all { it.status == DebugLabStatus.EMPTY || it.status == DebugLabStatus
            .NOT_APPLICABLE } -> DebugLabStatus.EMPTY
        else -> DebugLabStatus.PASSED
    }
    return StudyEngineLabReport(
        status = status,
        inspectedAtEpochMillis = nowEpochMillis,
        diagnostics = diagnostics,
    )
}

private fun diagnosticQuestion(
    planId: StudyPlanId,
    id: String,
    correctAnswer: String,
    nowEpochMillis: Long,
): Question = Question(
    id = QuestionId(id),
    studyPlanId = planId,
    subjectId = null,
    topicId = null,
    type = QuestionType.MULTIPLE_CHOICE,
    statement = "Diagnostic question $id",
    options = listOf("A", "B"),
    correctAnswer = correctAnswer,
    explanation = "Diagnostic explanation",
    examBoard = null,
    source = null,
    year = null,
    difficulty = 1,
    favorite = false,
    disputed = false,
    tags = emptyList(),
    createdAtEpochMillis = nowEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
)

private fun diagnostic(
    kind: StudyEngineDiagnosticKind,
    evidenceCount: Int,
    valid: Boolean,
    primary: Int = 0,
    secondary: Int = 0,
    percent: Int? = null,
    score: Int? = null,
): StudyEngineDiagnostic = StudyEngineDiagnostic(
    kind = kind,
    status = when {
        evidenceCount == 0 -> DebugLabStatus.EMPTY
        valid -> DebugLabStatus.PASSED
        else -> DebugLabStatus.FAILED
    },
    primaryCount = primary,
    secondaryCount = secondary,
    percent = percent,
    score = score,
)
