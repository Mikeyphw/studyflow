package com.mikeyphw.studyflow

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.studyflow.core.domain.ExamReadinessEngine
import com.mikeyphw.studyflow.core.domain.StudyExamRunbookPlanner
import com.mikeyphw.studyflow.core.domain.StudySprintPlanner
import com.mikeyphw.studyflow.core.model.*
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.reminders.StudyFlowWidgetUpdateCoordinator
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowFullJourneyAcceptanceTest {
    @Test
    fun planThroughRealRestorePersistsAcrossProductionRepositories() = runBlocking {
        assumeTrue(journeyMode() != MODE_POST_RESTART)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as StudyFlowApplication
        val container = application.container
        awaitApplicationReady(container)
        val retainForRestart = journeyMode() == MODE_RESTART_INITIAL
        val suffix = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val planId = StudyPlanId("acceptance-plan-$suffix")
        val subjectId = SubjectId("acceptance-subject-$suffix")
        val topicId = TopicId("acceptance-topic-$suffix")
        val oldDefault = container.appPreferencesRepository.preferences.first().defaultStudyPlanId
        val packageFile = File.createTempFile("studyflow-final-journey-", ".studyplan", context.cacheDir)
        var importedPlanId: StudyPlanId? = null
        var retainedSuccessfully = false
        try {
            val canonicalName = "Release journey $suffix"
            container.createStudyPlan(
                StudyPlan(
                    planId,
                    canonicalName,
                    "Final release acceptance journey",
                    StudyType.FREE_STUDY,
                    StudyPlanStatus.ACTIVE,
                    LocalDate.now().plusDays(5).toString(),
                    5,
                    0,
                    0,
                    0,
                    0,
                    now,
                    now,
                    null,
                ),
                StudyPlanSettings.defaults(planId),
            )
            container.appPreferencesRepository.setDefaultStudyPlan(planId)
            val subject = container.manageStudyContent.saveSubject(
                Subject(
                    subjectId,
                    planId,
                    "Journey subject $suffix",
                    "Acceptance subject",
                    null,
                    3,
                    1.0,
                    120,
                    0,
                    0,
                    ContentProgressStatus.STUDYING,
                    0,
                    false,
                ),
            )
            val topic = container.manageStudyContent.saveTopic(
                Topic(
                    topicId,
                    planId,
                    subject.id,
                    null,
                    "Journey topic $suffix",
                    "Acceptance topic",
                    3,
                    3,
                    50,
                    60,
                    0,
                    0,
                    ContentProgressStatus.STUDYING,
                    0,
                    false,
                ),
            )
            val rule = container.manageStudySchedule.saveRule(
                ScheduleRule(
                    ScheduleRuleId("acceptance-rule-$suffix"),
                    planId,
                    subject.id,
                    ScheduleRuleType.FIXED_BLOCK,
                    LocalDate.now().dayOfWeek.value,
                    12 * 60,
                    30,
                    "ONCE",
                    false,
                    true,
                    0,
                ),
            )
            val generated = container.manageStudySchedule.generateActivities(
                ScheduleGenerationRequest(planId, now, 1, true),
                listOf(rule),
            )
            assertEquals(1, generated.generatedActivities)
            val activity = container.manageStudySchedule.observeSchedule(planId).first().plannedActivities.single()
            val sessionId = StudySessionId("acceptance-session-$suffix")
            container.manageStudySession.startFromActivity(sessionId, activity)
            container.manageStudySession.pause(planId, sessionId)
            container.manageStudySession.resume(planId, sessionId)
            assertEquals(
                StudySessionStatus.COMPLETED,
                container.manageStudySession.complete(planId, sessionId, 2, 2, 1, 4, 3, "acceptance").status,
            )
            val q1 = container.manageStudyPractice.createQuestion(
                QuestionId("acceptance-q1-$suffix"),
                planId,
                subject.id,
                topic.id,
                QuestionType.MULTIPLE_CHOICE,
                "Acceptance question one $suffix",
                listOf("A", "B"),
                "A",
                "A is correct",
                null,
                "release-journey",
                null,
                2,
                listOf("acceptance"),
            )
            val q2 = container.manageStudyPractice.createQuestion(
                QuestionId("acceptance-q2-$suffix"),
                planId,
                subject.id,
                topic.id,
                QuestionType.MULTIPLE_CHOICE,
                "Acceptance question two $suffix",
                listOf("C", "D"),
                "C",
                "C is correct",
                null,
                "release-journey",
                null,
                3,
                listOf("acceptance"),
            )
            val testId = StudyTestId("acceptance-test-$suffix")
            container.manageStudyPractice.createPracticeTest(testId, planId, "Acceptance practice", listOf(q1, q2))
            container.manageStudyPractice.startPracticeTest(planId, testId)
            container.manageStudyPractice.answerPracticeQuestion(planId, testId, q1.id, "A", 5L)
            container.manageStudyPractice.answerPracticeQuestion(planId, testId, q2.id, "D", 5L)
            val practice = container.manageStudyPractice.finishPracticeAttempt(planId, testId)
            assertNotNull(practice.submittedAtEpochMillis)
            assertEquals(1, practice.correctCount)
            val mockId = MockExamId("acceptance-mock-$suffix")
            container.manageStudyPractice.createMockExam(
                mockId,
                planId,
                "Acceptance mock",
                listOf(q1, q2),
                600L,
                0.25,
                40,
            )
            container.manageStudyPractice.startMockExamAttempt(planId, mockId)
            container.manageStudyPractice.answerMockQuestion(planId, mockId, q1.id, "A", 5L)
            container.manageStudyPractice.answerMockQuestion(planId, mockId, q2.id, "D", 5L)
            val mock = container.manageStudyPractice.finishMockExamAttempt(planId, mockId)
            assertNotNull(mock.submittedAtEpochMillis)
            assertNotNull(mock.scorePercent)
            val error = container.manageStudyReview.recordWrongAnswer(
                q2,
                ErrorCategory.CONCEPT_CONFUSION,
                "D",
                ErrorEntryId("acceptance-error-$suffix"),
            )
            assertEquals(error.id, container.manageStudyReview.remediationFor(planId, error.id).error.id)
            val dueReview = container.manageStudyReview.observeQueue(planId).first { it.due.isNotEmpty() }.due.first()
            assertEquals(
                ReviewStatus.SCHEDULED,
                container.manageStudyReview.gradeReview(planId, dueReview.id, 4).status,
            )
            val goal = container.manageStudyProgress.createGoal(
                StudyGoalId("acceptance-goal-$suffix"),
                planId,
                subject.id,
                topic.id,
                GoalType.CUSTOM,
                GoalPeriod.CUSTOM,
                "Acceptance goal",
                1.0,
                LocalDate.now().toString(),
                null,
            )
            assertNotNull(container.manageStudyProgress.updateGoalProgress(planId, goal.id, 1.0).completedAtEpochMillis)
            container.manageStudyProgress.createNote(
                StudyNoteId("acceptance-note-$suffix"),
                planId,
                subject.id,
                topic.id,
                null,
                StudyNoteType.SUMMARY,
                "Acceptance note $suffix",
                "Acceptance content",
                listOf("release"),
            )
            val sprintActivity = container.manageStudySchedule.saveActivity(
                PlannedActivity(
                    id = PlannedActivityId("acceptance-sprint-$suffix"),
                    studyPlanId = planId,
                    subjectId = subject.id,
                    topicId = topic.id,
                    materialId = null,
                    videoResourceId = null,
                    type = StudyActivityType.QUESTIONS,
                    title = "Acceptance sprint candidate $suffix",
                    scheduledStartEpochMillis = now + 2L * 86_400_000L,
                    plannedDurationMinutes = 90,
                    status = PlannedActivityStatus.PLANNED,
                    locked = false,
                    completedAtEpochMillis = null,
                ),
            )
            val analytics = withTimeout(10_000L) {
                container.manageStudyAnalytics.observeSnapshot(planId).first { it.completedSessionsLastSevenDays >= 1 }
            }
            assertTrue(analytics.answeredQuestions >= 2)
            val scheduleBeforeSprint = container.manageStudySchedule.observeSchedule(planId).first()
            val sprintNow = System.currentTimeMillis()
            val readiness = ExamReadinessEngine.calculate(
                plan = requireNotNull(container.manageStudyPlan.observePlan(planId).first()),
                analytics = analytics,
                schedule = scheduleBeforeSprint,
                mockExams = container.manageStudyPractice.observeMockExams(planId).first(),
                nowEpochMillis = sprintNow,
                zoneId = java.time.ZoneId.systemDefault(),
            )
            val runbook = StudyExamRunbookPlanner.build(
                plan = requireNotNull(container.manageStudyPlan.observePlan(planId).first()),
                analytics = analytics,
                readiness = readiness,
                schedule = scheduleBeforeSprint,
                mockExams = container.manageStudyPractice.observeMockExams(planId).first(),
            )
            assertTrue(runbook.applicable)
            assertTrue(runbook.items.isNotEmpty())
            val sprintPreview = StudySprintPlanner.preview(
                plan = requireNotNull(container.manageStudyPlan.observePlan(planId).first()),
                analytics = analytics,
                readiness = readiness,
                schedule = scheduleBeforeSprint,
                nowEpochMillis = sprintNow,
            )
            assertTrue(sprintPreview.executable)
            assertTrue(sprintPreview.schedulePreview.changes.any { change ->
                change.plannedActivityId == sprintActivity.id && change.executable
            })
            assertTrue(container.manageStudySchedule.applyVersionedPreview(planId, sprintPreview) > 0)
            val sprintAfterApply = container.manageStudySchedule.observeSchedule(planId).first()
                .plannedActivities.single { candidate -> candidate.id == sprintActivity.id }
            assertEquals(sprintActivity.scheduledStartEpochMillis, sprintAfterApply.scheduledStartEpochMillis)
            assertEquals(PlannedActivityStatus.PLANNED, sprintAfterApply.status)
            assertTrue(!sprintAfterApply.locked)
            assertTrue(
                sprintAfterApply.plannedDurationMinutes < sprintActivity.plannedDurationMinutes ||
                    sprintAfterApply.type != sprintActivity.type,
            )
            assertTrue(container.manageStudyDiscovery.search("Journey topic $suffix").any { it.studyPlanId == planId })
            container.reminderDeliveryCoordinator.reconcilePlan(planId)
            assertEquals("ready", StudyFlowWidgetUpdateCoordinator.refreshAllAndAwait(context).state)
            packageFile.outputStream().use { output ->
                container.manageStudyPlanPortability.exportPlan(
                    planId,
                    StudyPlanExportOptions(StudyPlanExportMode.FULL, true, false),
                    output,
                )
            }
            val integrity = packageFile.inputStream().use { container.manageStudyPlanPortability.inspectBackup(it) }
            assertTrue(integrity.checksumVerified)
            assertTrue(integrity.canRestore)
            val dryRun = packageFile.inputStream().use {
                container.manageStudyPlanPortability.dryRunImport(it, StudyPlanImportStrategy.CREATE_AS_NEW)
            }
            assertEquals(0, dryRun.databaseRowsChanged)
            val original = requireNotNull(container.manageStudyPlan.observePlan(planId).first())
            container.manageStudyPlan.update(original.copy(name = "Mutated $suffix", updatedAtEpochMillis = now + 1))
            val replaced = packageFile.inputStream().use {
                container.manageStudyPlanPortability.importPlan(
                    it,
                    StudyPlanImportStrategy.REPLACE_EXISTING,
                    planId,
                )
            }
            assertEquals(planId.value, replaced.studyPlanId)
            assertEquals(canonicalName, requireNotNull(container.manageStudyPlan.observePlan(planId).first()).name)
            val created = packageFile.inputStream().use {
                container.manageStudyPlanPortability.importPlan(it, StudyPlanImportStrategy.CREATE_AS_NEW)
            }
            importedPlanId = StudyPlanId(created.studyPlanId)
            assertTrue(importedPlanId != planId)
            assertNotNull(container.manageStudyPlan.observePlan(requireNotNull(importedPlanId)).first())
            assertEquals(
                canonicalName,
                requireNotNull(application.container.manageStudyPlan.observePlan(planId).first()).name,
            )
            reportPhase63_64Status(KEY_PHASE63_64_INITIAL_STATUS)
            if (retainForRestart) {
                acceptanceMarker(context).edit()
                    .clear()
                    .putString(KEY_PLAN_ID, planId.value)
                    .putString(KEY_IMPORTED_PLAN_ID, requireNotNull(importedPlanId).value)
                    .putString(KEY_CANONICAL_NAME, canonicalName)
                    .putString(KEY_SPRINT_ACTIVITY_ID, sprintActivity.id.value)
                    .putString(KEY_PROCESS_TOKEN, processInstanceToken)
                    .putBoolean(KEY_OLD_DEFAULT_PRESENT, oldDefault != null)
                    .putString(KEY_OLD_DEFAULT_ID, oldDefault?.value.orEmpty())
                    .commit()
                retainedSuccessfully = true
            }
        } finally {
            packageFile.delete()
            if (!retainForRestart || !retainedSuccessfully) {
                container.appPreferencesRepository.setDefaultStudyPlan(oldDefault)
                importedPlanId?.let { runCatching { container.manageStudyPlan.deletePermanently(it) } }
                runCatching { container.manageStudyPlan.deletePermanently(planId) }
                acceptanceMarker(context).edit().clear().commit()
            }
        }
    }

    @Test
    fun restoredJourneySurvivesProcessRestartAndCleansUp() = runBlocking {
        assumeTrue(journeyMode() == MODE_POST_RESTART)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as StudyFlowApplication
        val container = application.container
        awaitApplicationReady(container)
        val marker = acceptanceMarker(context)
        val planId = StudyPlanId(requireNotNull(marker.getString(KEY_PLAN_ID, null)))
        val importedPlanId = StudyPlanId(requireNotNull(marker.getString(KEY_IMPORTED_PLAN_ID, null)))
        val canonicalName = requireNotNull(marker.getString(KEY_CANONICAL_NAME, null))
        val sprintActivityId = PlannedActivityId(requireNotNull(marker.getString(KEY_SPRINT_ACTIVITY_ID, null)))
        val previousProcessToken = requireNotNull(marker.getString(KEY_PROCESS_TOKEN, null))
        val oldDefault = marker.getString(KEY_OLD_DEFAULT_ID, "")
            ?.takeIf { marker.getBoolean(KEY_OLD_DEFAULT_PRESENT, false) && it.isNotBlank() }
            ?.let(::StudyPlanId)
        assertNotEquals(
            "post-restore acceptance must run in a new process",
            previousProcessToken,
            processInstanceToken,
        )
        try {
            assertEquals(canonicalName, requireNotNull(container.manageStudyPlan.observePlan(planId).first()).name)
            assertNotNull(container.manageStudyPlan.observePlan(importedPlanId).first())
            assertEquals(planId, container.appPreferencesRepository.preferences.first().defaultStudyPlanId)
            val analytics = withTimeout(10_000L) {
                container.manageStudyAnalytics.observeSnapshot(planId).first { it.completedSessionsLastSevenDays >= 1 }
            }
            assertTrue(analytics.answeredQuestions >= 2)
            val restoredSchedule = container.manageStudySchedule.observeSchedule(planId).first()
            assertTrue(restoredSchedule.plannedActivities.any { activity ->
                activity.status == PlannedActivityStatus.COMPLETED
            })
            val restoredSprintActivity = restoredSchedule.plannedActivities.single { activity ->
                activity.id == sprintActivityId
            }
            assertEquals(PlannedActivityStatus.PLANNED, restoredSprintActivity.status)
            assertTrue(!restoredSprintActivity.locked)
            assertTrue(
                restoredSprintActivity.plannedDurationMinutes < 90 ||
                    restoredSprintActivity.type != StudyActivityType.QUESTIONS,
            )
            assertTrue(
                container.manageStudyDiscovery.search(canonicalName).any { result -> result.studyPlanId == planId },
            )
            reportPhase63_64Status(KEY_PHASE63_64_POST_RESTART_STATUS)
        } finally {
            container.appPreferencesRepository.setDefaultStudyPlan(oldDefault)
            runCatching { container.manageStudyPlan.deletePermanently(importedPlanId) }
            runCatching { container.manageStudyPlan.deletePermanently(planId) }
            marker.edit().clear().commit()
        }
    }

    private suspend fun awaitApplicationReady(container: AppContainer) {
        withTimeout(30_000L) {
            val state = container.initializationState.filter { it !is AppInitializationState.Initializing }.first()
            assertTrue("application initialization failed: $state", state is AppInitializationState.Ready)
        }
    }

    private fun journeyMode(): String? = InstrumentationRegistry.getArguments().getString(ARG_JOURNEY_MODE)

    private fun reportPhase63_64Status(key: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(key, "true") },
        )
    }

    private fun acceptanceMarker(context: Context) =
        context.getSharedPreferences(MARKER_PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val ARG_JOURNEY_MODE = "studyflowJourneyMode"
        const val MODE_RESTART_INITIAL = "restart_initial"
        const val MODE_POST_RESTART = "post_restart"
        const val MARKER_PREFERENCES = "studyflow_release_acceptance_marker"
        const val KEY_PLAN_ID = "plan_id"
        const val KEY_IMPORTED_PLAN_ID = "imported_plan_id"
        const val KEY_CANONICAL_NAME = "canonical_name"
        const val KEY_SPRINT_ACTIVITY_ID = "sprint_activity_id"
        const val KEY_PROCESS_TOKEN = "process_token"
        const val KEY_PHASE63_64_INITIAL_STATUS = "studyflowPhase63_64InitialVerified"
        const val KEY_PHASE63_64_POST_RESTART_STATUS = "studyflowPhase63_64PostRestartVerified"
        const val KEY_OLD_DEFAULT_PRESENT = "old_default_present"
        const val KEY_OLD_DEFAULT_ID = "old_default_id"
        val processInstanceToken: String = UUID.randomUUID().toString()
    }
}
