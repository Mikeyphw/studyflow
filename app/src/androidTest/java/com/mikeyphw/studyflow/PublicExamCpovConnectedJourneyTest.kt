package com.mikeyphw.studyflow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.core.domain.ExamBlueprint
import com.mikeyphw.studyflow.core.domain.ExamBlueprintSection
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportParser
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportSource
import com.mikeyphw.studyflow.core.domain.ExamReadinessEngine
import com.mikeyphw.studyflow.core.domain.ExamScoringMode
import com.mikeyphw.studyflow.core.domain.ExamScoringPolicy
import com.mikeyphw.studyflow.core.domain.LegislationImportFormat
import com.mikeyphw.studyflow.core.domain.PublicExamDashboardBuilder
import com.mikeyphw.studyflow.core.domain.StudySprintPlanner
import com.mikeyphw.studyflow.core.domain.SyllabusImportFormat
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicExamCpovConnectedJourneyTest {
    @Test
    fun publicExamJourneyCrossesCpovFeaturesAndPortableRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as StudyFlowApplication
        val container = application.container
        awaitApplicationReady(container)
        val suffix = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val planId = StudyPlanId("cpov-plan-$suffix")
        val subjectId = SubjectId("cpov-subject-$suffix")
        val topicId = TopicId("cpov-topic-$suffix")
        val backup = File.createTempFile("studyflow-cpov13-", ".studyplan", context.cacheDir)
        var restoredPlanId: StudyPlanId? = null
        try {
            val examDate = LocalDate.now().plusDays(7).toString()
            container.createStudyPlan(
                StudyPlan(
                    id = planId,
                    name = "Public exam journey $suffix",
                    description = "CPOV connected acceptance",
                    type = StudyType.PUBLIC_EXAMINATION,
                    status = StudyPlanStatus.ACTIVE,
                    examDateIso = examDate,
                    weeklyGoalHours = 18,
                    progressPercent = 0,
                    studiedMinutesThisWeek = 0,
                    questionsAnswered = 0,
                    averageAccuracyPercent = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
                StudyPlanSettings.defaults(planId),
            )
            val target = container.manageStudyContent.saveStudyTarget(
                StudyTarget(
                    id = StudyTargetId("cpov-target-$suffix"),
                    studyPlanId = planId,
                    organization = "Acceptance Agency",
                    role = "Analyst",
                    examBoard = "CPOV Board",
                    noticeUrl = "local://edital/$suffix",
                    confirmedExamDateIso = examDate,
                    expectedExamDateIso = null,
                    openings = 10,
                    estimatedCutoffPercent = 70,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            val subject = container.manageStudyContent.saveSubject(
                Subject(
                    id = subjectId,
                    studyPlanId = planId,
                    name = "Constitutional law $suffix",
                    description = "",
                    colorArgb = null,
                    priority = 5,
                    examWeight = 3.0,
                    estimatedMinutes = 600,
                    progressPercent = 10,
                    accuracyPercent = 0,
                    status = ContentProgressStatus.STUDYING,
                    position = 0,
                    archived = false,
                ),
            )
            val topic = container.manageStudyContent.saveTopic(
                Topic(
                    id = topicId,
                    studyPlanId = planId,
                    subjectId = subject.id,
                    parentTopicId = null,
                    name = "Fundamental rights $suffix",
                    description = "",
                    priority = 5,
                    difficulty = 4,
                    examIncidencePercent = 80,
                    estimatedMinutes = 240,
                    progressPercent = 10,
                    masteryPercent = 25,
                    status = ContentProgressStatus.STUDYING,
                    position = 0,
                    archived = false,
                ),
            )

            val syllabusBefore = container.manageStudyContent.observeSyllabus(planId).first()
            val importPreview = ExamNoticeImportParser.preview(
                planId = planId,
                source = ExamNoticeImportSource(
                    displayName = "acceptance-edital.txt",
                    format = SyllabusImportFormat.STRUCTURED_TEXT,
                    text = "Disciplina: ${subject.name}\n1 ${topic.name}",
                ),
                existing = syllabusBefore,
            )
            val revisionPreview = container.managePublicExam.prepareExamNoticeRevision(
                planId = planId,
                importPreview = importPreview,
                existing = syllabusBefore,
                publicationDateIso = LocalDate.now().toString(),
            )
            val revision = container.managePublicExam.applyExamNoticeRevision(revisionPreview)
            assertEquals(planId, revision.studyPlanId)
            assertTrue(container.managePublicExam.observeExamNoticeRevisions(planId).first().isNotEmpty())
            val legislationPreview = container.managePublicExam.previewLegislationImport(
                title = "Acceptance Constitution",
                sourceReference = "local://law/$suffix",
                rawText = "Art. 1 Fundamental rights\nI - Equality",
                format = LegislationImportFormat.STRUCTURED_TEXT,
            )
            val legislation = container.managePublicExam.applyLegislationImport(planId, legislationPreview)
            val provision = legislation.provisions.first()

            val syllabus = container.manageStudyContent.observeSyllabus(planId).first()
            val cycle = container.managePublicExam.replaceStudyCycleFromSyllabus(
                planId = planId,
                syllabus = syllabus,
                name = "Acceptance cycle",
                targetDurationMinutes = 25,
            )
            assertTrue(cycle.items.isNotEmpty())
            val cycleSession = requireNotNull(container.managePublicExam.startCurrentStudyCycleItem(planId))
            container.manageStudySession.complete(planId, cycleSession.id, 0, 5, 4, 4, 3, "cycle acceptance")
            assertNotNull(container.managePublicExam.observeStudyCycle(planId).first())

            val q1 = container.manageStudyPractice.createQuestion(
                id = QuestionId("cpov-q1-$suffix"),
                studyPlanId = planId,
                subjectId = subject.id,
                topicId = topic.id,
                type = QuestionType.MULTIPLE_CHOICE,
                statement = "Question one $suffix",
                options = listOf("A", "B"),
                correctAnswer = "A",
                explanation = "A",
                examBoard = target.examBoard,
                source = "official",
                year = LocalDate.now().year,
                difficulty = 3,
                tags = listOf("cpov13"),
                organization = target.organization,
                role = target.role,
                authorship = QuestionAuthorship.OFFICIAL,
                examNoticeRevisionId = revision.id,
                externalReference = "Q1-$suffix",
                points = 1.0,
            )
            val q2 = container.manageStudyPractice.createQuestion(
                id = QuestionId("cpov-q2-$suffix"),
                studyPlanId = planId,
                subjectId = subject.id,
                topicId = topic.id,
                type = QuestionType.MULTIPLE_CHOICE,
                statement = "Question two $suffix",
                options = listOf("C", "D"),
                correctAnswer = "C",
                explanation = "C",
                examBoard = target.examBoard,
                source = "official",
                year = LocalDate.now().year,
                difficulty = 4,
                tags = listOf("cpov13"),
                organization = target.organization,
                role = target.role,
                authorship = QuestionAuthorship.OFFICIAL,
                examNoticeRevisionId = revision.id,
                externalReference = "Q2-$suffix",
                points = 1.0,
            )
            container.managePublicExam.linkLegislationEvidence(
                planId = planId,
                provisionId = provision.id,
                questionId = q1.id,
            )
            val error = container.manageStudyReview.recordWrongAnswer(
                question = q2,
                category = ErrorCategory.CONCEPT_CONFUSION,
                chosenAnswer = "D",
            )
            assertEquals(planId, error.studyPlanId)
            val reviewItems = container.manageStudyReview.observeReviewItems(planId).first()
            assertTrue(reviewItems.any { it.errorEntryId == error.id })

            val blueprint = ExamBlueprint(
                title = "Acceptance banca mock",
                sections = listOf(
                    ExamBlueprintSection(
                        key = "constitutional",
                        questionCount = 2,
                        subjectId = subject.id,
                        weight = 1.0,
                    ),
                ),
                timeLimitMinutes = 20,
                minimumOverallScorePercent = 50,
                examBoard = target.examBoard,
                year = LocalDate.now().year,
                scoringPolicy = ExamScoringPolicy(ExamScoringMode.RIGHT_MINUS_WRONG),
            )
            val mockId = MockExamId("cpov-mock-$suffix")
            container.manageStudyPractice.createMockExamFromBlueprint(mockId, planId, listOf(q1, q2), blueprint)
            container.manageStudyPractice.startMockExamAttempt(planId, mockId)
            container.manageStudyPractice.answerMockQuestion(planId, mockId, q1.id, "A", 5L)
            container.manageStudyPractice.answerMockQuestion(planId, mockId, q2.id, "D", 5L)
            val mock = container.manageStudyPractice.finishMockExamAttempt(planId, mockId)
            assertNotNull(mock.scorePercent)

            val sprintCandidate = container.manageStudySchedule.saveActivity(
                PlannedActivity(
                    id = PlannedActivityId("cpov-sprint-$suffix"),
                    studyPlanId = planId,
                    subjectId = subject.id,
                    topicId = topic.id,
                    materialId = null,
                    videoResourceId = null,
                    type = StudyActivityType.THEORY,
                    title = "Final stretch candidate",
                    scheduledStartEpochMillis = now + 2L * 86_400_000L,
                    plannedDurationMinutes = 90,
                    status = PlannedActivityStatus.PLANNED,
                    locked = false,
                    completedAtEpochMillis = null,
                ),
            )
            val analytics = withTimeout(10_000L) {
                container.manageStudyAnalytics.observeSnapshot(planId).first {
                    it.completedSessionsLastSevenDays >= 1
                }
            }
            val schedule = container.manageStudySchedule.observeSchedule(planId).first()
            val plan = requireNotNull(container.manageStudyPlan.observePlan(planId).first())
            val readiness = ExamReadinessEngine.calculate(
                plan = plan,
                analytics = analytics,
                schedule = schedule,
                mockExams = container.manageStudyPractice.observeMockExams(planId).first(),
                nowEpochMillis = now,
                zoneId = ZoneId.systemDefault(),
            )
            val sprintPreview = StudySprintPlanner.preview(plan, analytics, readiness, schedule, now)
            assertTrue(sprintPreview.executable)
            assertTrue(container.manageStudySchedule.applyVersionedPreview(planId, sprintPreview) > 0)
            val sprintAfter = container.manageStudySchedule.observeSchedule(planId).first().plannedActivities
                .single { it.id == sprintCandidate.id }
            assertTrue(
                sprintAfter.plannedDurationMinutes < sprintCandidate.plannedDurationMinutes ||
                    sprintAfter.type != sprintCandidate.type,
            )

            val questions = container.manageStudyPractice.observeQuestions(planId).first()
            val dashboard = PublicExamDashboardBuilder.build(
                plan = plan,
                target = target,
                syllabus = container.manageStudyContent.observeSyllabus(planId).first(),
                questions = questions,
                reviews = container.manageStudyReview.observeReviewItems(planId).first(),
                errors = container.manageStudyReview.observeErrorEntries(planId).first(),
                sessions = container.manageStudySession.observeSessions(planId).first(),
                mockExams = container.manageStudyPractice.observeMockExams(planId).first(),
                nowEpochMillis = System.currentTimeMillis(),
            )
            assertEquals(2, dashboard.metrics.totalQuestions)
            assertTrue(dashboard.matrix.isNotEmpty())

            backup.outputStream().use { output ->
                container.manageStudyPlanPortability.exportPlan(
                    planId,
                    StudyPlanExportOptions(StudyPlanExportMode.FULL, true, false),
                    output,
                )
            }
            val integrity = backup.inputStream().use { input ->
                container.manageStudyPlanPortability.inspectBackup(input)
            }
            assertTrue(integrity.checksumVerified)
            val imported = backup.inputStream().use { input ->
                container.manageStudyPlanPortability.importPlan(
                    input,
                    StudyPlanImportStrategy.CREATE_AS_NEW,
                    null,
                )
            }
            restoredPlanId = StudyPlanId(imported.studyPlanId)
            assertNotEquals(planId, restoredPlanId)
            assertNotNull(container.manageStudyPlan.observePlan(requireNotNull(restoredPlanId)).first())
            val restoredId = requireNotNull(restoredPlanId)
            assertTrue(container.managePublicExam.observeExamNoticeRevisions(restoredId).first().isNotEmpty())
            assertTrue(container.managePublicExam.observeLegislation(restoredId).first().documents.isNotEmpty())
        } finally {
            backup.delete()
            restoredPlanId?.let { runCatching { container.manageStudyPlan.deletePermanently(it) } }
            runCatching { container.manageStudyPlan.deletePermanently(planId) }
        }
    }

    private suspend fun awaitApplicationReady(container: AppContainer) {
        withTimeout(30_000L) {
            val state = container.initializationState.filter { it !is AppInitializationState.Initializing }.first()
            assertTrue("application initialization failed: $state", state is AppInitializationState.Ready)
        }
    }
}
