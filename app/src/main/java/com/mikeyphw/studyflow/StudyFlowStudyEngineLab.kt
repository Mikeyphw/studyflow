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
import com.mikeyphw.studyflow.core.domain.PracticeAnalytics
import com.mikeyphw.studyflow.core.domain.PracticeDeckRequest
import com.mikeyphw.studyflow.core.domain.StudySchedulePlanner
import com.mikeyphw.studyflow.core.model.ScheduleGenerationRequest
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
    val schedule = container.manageStudySchedule.observeSchedule(planId).first()
    val generatedActivities = StudySchedulePlanner(
        idFactory = { "debug-generated-${nowEpochMillis}" },
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
    val questions = container.studyPracticeRepository.observeQuestions(planId).first()
    val tests = container.studyPracticeRepository.observeTests(planId).first()
    val mockExams = container.studyPracticeRepository.observeMockExams(planId).first()
    val practiceSummary = PracticeAnalytics.summarize(questions, tests, mockExams)
    val practiceDeck = container.manageStudyPractice.buildDeck(
        questions = questions,
        request = PracticeDeckRequest(limit = 10),
    )
    val recommendations = container.manageStudyDiscovery.recommendations(planId, nowEpochMillis)
    val searchResults = container.manageStudyDiscovery.search("study")
    val progressSnapshot = container.manageStudyProgress.observeSnapshot(planId).first()
    val analyticsSnapshot = container.manageStudyAnalytics.observeSnapshot(planId).first()
    val syncState = container.manageStudyCloudSync.syncState.first()
    val syncSummary = container.manageStudyCloudSync.inspectLocalChanges()

    val diagnostics = listOf(
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SCHEDULE_EXPANSION,
            status = statusFor(schedule.scheduleRules.size + generatedActivities.size),
            primaryCount = schedule.scheduleRules.size,
            secondaryCount = generatedActivities.size,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.REVIEW_QUEUE,
            status = statusFor(reviewQueue.dueCount + reviewQueue.upcomingCount + reviewQueue.flashcardCount),
            primaryCount = reviewQueue.dueCount,
            secondaryCount = reviewQueue.upcomingCount,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.PRACTICE_DECK,
            status = statusFor(questions.size),
            primaryCount = practiceDeck.size,
            secondaryCount = questions.size,
            percent = practiceSummary.accuracyPercent,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.MOCK_EXAM,
            status = statusFor(mockExams.size),
            primaryCount = mockExams.size,
            secondaryCount = mockExams.count { it.submittedAtEpochMillis != null },
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.RECOMMENDATIONS,
            status = statusFor(recommendations.size),
            primaryCount = recommendations.size,
            score = recommendations.firstOrNull()?.score,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SEARCH_QUERY,
            status = statusFor(searchResults.size),
            primaryCount = searchResults.size,
            secondaryCount = searchResults.map { it.type }.distinct().size,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.PROGRESS_SNAPSHOT,
            status = statusFor(progressSnapshot.goals.size + progressSnapshot.notes.size),
            primaryCount = progressSnapshot.goals.size,
            secondaryCount = progressSnapshot.notes.size,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.ANALYTICS_SNAPSHOT,
            status = DebugLabStatus.PASSED,
            primaryCount = analyticsSnapshot.insights.size,
            secondaryCount = analyticsSnapshot.weakAreas.size,
            percent = analyticsSnapshot.consistencyScorePercent,
        ),
        StudyEngineDiagnostic(
            kind = StudyEngineDiagnosticKind.SYNC_FOUNDATION,
            status = if (syncState.checkpointReady || syncSummary.hasLocalData) {
                DebugLabStatus.PASSED
            } else {
                DebugLabStatus.WARNING
            },
            primaryCount = syncSummary.planCount,
            secondaryCount = syncSummary.activePlanCount,
        ),
    )
    val status = when {
        diagnostics.any { it.status == DebugLabStatus.FAILED } -> DebugLabStatus.FAILED
        diagnostics.any { it.status == DebugLabStatus.WARNING } -> DebugLabStatus.WARNING
        else -> DebugLabStatus.PASSED
    }
    return StudyEngineLabReport(
        status = status,
        inspectedAtEpochMillis = nowEpochMillis,
        diagnostics = diagnostics,
    )
}

private fun statusFor(count: Int): DebugLabStatus = if (count > 0) DebugLabStatus.PASSED else DebugLabStatus.WARNING
