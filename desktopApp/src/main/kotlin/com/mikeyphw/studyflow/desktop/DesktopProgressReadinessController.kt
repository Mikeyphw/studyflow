package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.AdaptiveReschedulePlanner
import com.mikeyphw.studyflow.core.domain.ExamReadinessEngine
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReview
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewBuilder
import com.mikeyphw.studyflow.core.domain.StudyCoachWeeklyReviewInput
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupEngine
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupInput
import com.mikeyphw.studyflow.core.domain.StudyContentCleanupReport
import com.mikeyphw.studyflow.core.domain.StudyEvidenceEvent
import com.mikeyphw.studyflow.core.domain.StudyEvidenceTimelineBuilder
import com.mikeyphw.studyflow.core.domain.StudyEvidenceTimelineInput
import com.mikeyphw.studyflow.core.domain.StudyExamReportBuilder
import com.mikeyphw.studyflow.core.domain.StudyExamReportInput
import com.mikeyphw.studyflow.core.domain.StudyExamReportSnapshot
import com.mikeyphw.studyflow.core.domain.StudyExamRunbook
import com.mikeyphw.studyflow.core.domain.StudyExamRunbookPlanner
import com.mikeyphw.studyflow.core.domain.StudySprintPlanner
import com.mikeyphw.studyflow.core.domain.StudySprintPreview
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyGoal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * DOV19 is a derived view over persisted Desktop repositories. It intentionally does not persist
 * readiness/report/sprint projections as a second source of truth: reopening the app recomputes them
 * from the DOV08-DOV18 durable workspace.
 */
data class DesktopProgressReadinessState(
    val plan: StudyPlan?,
    val readiness: ExamReadinessScore?,
    val weeklyReview: StudyCoachWeeklyReview?,
    val cleanupReport: StudyContentCleanupReport?,
    val examReport: StudyExamReportSnapshot?,
    val runbook: StudyExamRunbook?,
    val sprintPreview: StudySprintPreview?,
    val goals: List<StudyGoal>,
    val evidence: List<StudyEvidenceEvent>,
) {
    val available: Boolean get() = plan != null
}

class DesktopProgressReadinessController(
    private val services: DesktopStudyFlowServices,
    private val workspaceRoot: Path?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun state(): DesktopProgressReadinessState {
        val plan = services.currentDefaultPlanForDesktop()
            ?: return DesktopProgressReadinessState(null, null, null, null, null, null, null, emptyList(), emptyList())
        val now = clock()
        val planId = plan.id
        val analytics = services.analyticsRepository.currentSnapshot(planId)
        val schedule = services.scheduleRepository.currentSchedule(planId)
        val questions = services.practiceRepository.currentQuestions(planId)
        val tests = services.practiceRepository.currentTests(planId)
        val mockExams = services.practiceRepository.currentMockExams(planId)
        val reviewItems = services.reviewRepository.currentReviews(planId)
        val flashcards = services.reviewRepository.currentFlashcards(planId)
        val errors = services.reviewRepository.currentErrors(planId)
        val sessions = services.sessionRepository.currentSessions(planId)
        val syllabus = services.contentRepository.currentSyllabus(planId)
        val target = services.contentRepository.currentState().targets.firstOrNull { it.studyPlanId == planId }
        val library = services.libraryRepository.currentSnapshot(planId)
        val goals = services.progressRepository.currentGoals(planId)
        val progressNotes = services.progressRepository.currentNotes(planId)
        val readiness = ExamReadinessEngine.calculate(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            mockExams = mockExams,
            nowEpochMillis = now,
            target = target,
        )
        val settings = services.planRepository.currentSettings()[planId]
        val reschedule = if (settings?.adaptivePlanningEnabled == false) {
            AdaptiveReschedulePlanner.disabledPreview(plan, analytics, schedule, readiness.scorePercent, now)
        } else {
            AdaptiveReschedulePlanner.preview(plan, analytics, schedule, readiness.scorePercent, now)
        }
        val evidence = StudyEvidenceTimelineBuilder.buildAll(
            StudyEvidenceTimelineInput(
                studyPlanId = planId,
                plannedActivities = schedule.plannedActivities,
                sessions = sessions,
                reviewItems = reviewItems,
                questions = questions,
                tests = tests,
                mockExams = mockExams,
                goals = goals,
                notes = progressNotes,
                nowEpochMillis = now,
            ),
        )
        val weeklyReview = StudyCoachWeeklyReviewBuilder.build(
            StudyCoachWeeklyReviewInput(
                plan = plan,
                analytics = analytics,
                readiness = readiness,
                reschedulePreview = reschedule,
                evidence = evidence,
                generatedAtEpochMillis = now,
            ),
        )
        val cleanup = StudyContentCleanupEngine.inspect(
            StudyContentCleanupInput(
                plan = plan,
                syllabus = syllabus,
                questions = questions,
                materials = library.materials,
                videos = library.videos,
                annexes = library.annexes,
                reviewItems = reviewItems,
                flashcards = flashcards,
                errorEntries = errors,
                plannedActivities = schedule.plannedActivities,
                studySessions = sessions,
                notes = progressNotes,
                nowEpochMillis = now,
            ),
        )
        val report = StudyExamReportBuilder.build(
            StudyExamReportInput(
                plan = plan,
                analytics = analytics,
                readiness = readiness,
                weeklyReview = weeklyReview,
                evidence = evidence.take(12),
                totalEvidenceCount = evidence.size,
                schedule = schedule,
                reviewItems = reviewItems,
                questions = questions,
                tests = tests,
                mockExams = mockExams,
                reschedulePreview = reschedule,
                cleanupReport = cleanup,
                generatedAtEpochMillis = now,
            ),
        )
        return DesktopProgressReadinessState(
            plan = plan,
            readiness = readiness,
            weeklyReview = weeklyReview,
            cleanupReport = cleanup,
            examReport = report,
            runbook = StudyExamRunbookPlanner.build(plan, analytics, readiness, schedule, mockExams, target),
            sprintPreview = StudySprintPlanner.preview(plan, analytics, readiness, schedule, now),
            goals = goals,
            evidence = evidence,
        )
    }

    suspend fun applyCurrentSprint(): Int {
        val current = state()
        val plan = requireNotNull(current.plan) { "Select a Study Plan first." }
        require(plan.status == StudyPlanStatus.ACTIVE) { "Only an active Study Plan can apply a study sprint." }
        val preview = requireNotNull(current.sprintPreview) { "No study sprint preview is available." }
        require(preview.executable) { "The current study sprint has no safe executable changes." }
        return services.manageStudySchedule.applyVersionedPreview(plan.id, preview)
    }

    fun exportCurrentExamReport(): Path {
        val root = requireNotNull(workspaceRoot) { "Persistent Desktop workspace is required for report export." }
            .toAbsolutePath()
            .normalize()
        val report = requireNotNull(state().examReport) { "Select a Study Plan before exporting a report." }
        val directory = root.resolve("exam-reports").normalize()
        require(directory.startsWith(root)) { "Invalid report destination." }
        Files.createDirectories(directory)
        val target = directory.resolve("desktop-exam-report-${report.generatedAtEpochMillis}.html")
        val temporary = directory.resolve(".${target.fileName}.tmp")
        Files.writeString(temporary, renderReport(report), StandardCharsets.UTF_8)
        runCatching {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    private fun renderReport(report: StudyExamReportSnapshot): String = buildString {
        appendLine(
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>StudyFlow exam report</title></head><body>",
        )
        appendLine("<h1>${html(report.studyPlanName)}</h1>")
        appendLine("<p>Generated: ${report.generatedAtEpochMillis}</p>")
        appendLine("<p>Readiness: ${formatPercent(report.readinessScorePercent, "insufficient evidence")}</p>")
        appendLine("<p>Readiness band: ${report.readinessBand?.name ?: "unscored"}</p>")
        appendLine("<p>Study time (7 days): ${report.studiedMinutesLastSevenDays} minutes</p>")
        appendLine("<p>Evidence events: ${report.evidenceCount}</p>")
        appendLine("<p>Practice accuracy: ${formatPercent(report.practiceTestAccuracyPercent, "n/a")}</p>")
        appendLine("<p>Content health: ${report.cleanupHealthScorePercent}% (${report.cleanupIssueCount} issues)</p>")
        appendLine("<h2>Next safe actions</h2><ul>")
        report.nextActions.forEach { appendLine("<li>${html(it.name.replace('_', ' '))}</li>") }
        appendLine("</ul><h2>Metric provenance</h2><ul>")
        report.metricProvenance.forEach { appendLine("<li>${html(it.metric)}: ${html(it.source.name)}</li>") }
        appendLine("</ul></body></html>")
    }

    private fun formatPercent(value: Int?, unavailable: String): String = value?.let { "$it%" } ?: unavailable

    private fun html(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
