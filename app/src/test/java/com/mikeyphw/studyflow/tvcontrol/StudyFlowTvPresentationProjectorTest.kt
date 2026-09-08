package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyMaterialType
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvPresentationProjectorTest {
    private val planId = StudyPlanId("plan-real")
    private val subjectId = SubjectId("subject-real")

    @Test
    fun projectsCanonicalDomainIdsAndContentIntoTypedSnapshot() {
        val snapshot = StudyFlowTvPresentationProjector.project(input(plan = plan()))

        assertEquals("plan-real", snapshot.studyPlanId)
        assertEquals("My real plan", snapshot.planTitle)
        assertEquals("activity-real", snapshot.today?.nextActivity?.activityId)
        assertEquals("question-real", snapshot.questionSet?.questions?.single()?.questionId)
        assertEquals("activity-real", snapshot.schedule?.items?.single()?.activityId)
        assertEquals("material-real", snapshot.library?.references?.single()?.referenceId)
        assertEquals(50, snapshot.progress?.weeklyProgressPercent)
        val flattened = snapshot.toString()
        assertFalse(flattened.contains("TRT Técnico Judiciário"))
        assertFalse(flattened.contains("Constitution article 5"))
    }

    @Test
    fun missingPlanProducesWaitingSnapshotWithoutStudyFixtures() {
        val snapshot = StudyFlowTvPresentationProjector.project(input(plan = null))

        assertEquals("plan-real", snapshot.studyPlanId)
        assertFalse(snapshot.hasRenderableStudyData)
        assertNull(snapshot.today)
        assertNull(snapshot.questionSet)
        assertNull(snapshot.schedule)
        assertNull(snapshot.library)
    }

    private fun input(plan: StudyPlan?) = StudyFlowTvPresentationProjectionInput(
        snapshotId = "snapshot-real",
        generatedAtEpochMillis = 10_000L,
        requestedPlanId = planId.value,
        plan = plan,
        syllabus = StudySyllabus(
            studyPlanId = planId,
            subjects = listOf(
                Subject(
                    id = subjectId,
                    studyPlanId = planId,
                    name = "Algorithms",
                    description = "",
                    colorArgb = null,
                    priority = 3,
                    examWeight = 1.0,
                    estimatedMinutes = 600,
                    progressPercent = 40,
                    accuracyPercent = 75,
                    status = ContentProgressStatus.STUDYING,
                    position = 0,
                    archived = false,
                ),
            ),
            topics = emptyList(),
        ),
        schedule = StudySchedule(
            studyPlanId = planId,
            strategy = null,
            scheduleRules = emptyList(),
            plannedActivities = listOf(
                PlannedActivity(
                    id = PlannedActivityId("activity-real"),
                    studyPlanId = planId,
                    subjectId = subjectId,
                    topicId = null,
                    materialId = null,
                    videoResourceId = null,
                    type = StudyActivityType.QUESTIONS,
                    title = "Graph practice",
                    scheduledStartEpochMillis = 12_000L,
                    plannedDurationMinutes = 30,
                    status = PlannedActivityStatus.PLANNED,
                    locked = false,
                    completedAtEpochMillis = null,
                ),
            ),
        ),
        sessions = emptyList(),
        questions = listOf(
            Question(
                id = QuestionId("question-real"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = QuestionType.MULTIPLE_CHOICE,
                statement = "What is BFS?",
                options = listOf("Breadth-first search", "Binary file sort"),
                correctAnswer = "Breadth-first search",
                explanation = "It visits graph layers in order.",
                examBoard = null,
                source = null,
                year = null,
                difficulty = 2,
                favorite = false,
                disputed = false,
                tags = emptyList(),
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        ),
        tests = emptyList(),
        mockExams = emptyList(),
        reviewItems = emptyList(),
        flashcards = emptyList(),
        errorEntries = emptyList(),
        analytics = StudyAnalyticsSnapshot(
            studyPlanId = planId,
            studiedMinutesLastSevenDays = 300,
            completedSessionsLastSevenDays = 4,
            scheduleAdherencePercent = 80,
            questionAccuracyPercent = 75,
            answeredQuestions = 40,
            dueReviewCount = 3,
            overdueReviewCount = 1,
            retentionEaseAverage = 2.4,
            goalCompletionPercent = 50,
            contentProgressPercent = 40,
            libraryProgressPercent = 25,
            consistencyScorePercent = 70,
            weakAreas = emptyList(),
            insights = emptyList(),
        ),
        materials = listOf(
            StudyMaterial(
                id = StudyMaterialId("material-real"),
                studyPlanId = planId,
                subjectId = subjectId,
                topicId = null,
                type = StudyMaterialType.NOTE,
                title = "Graph notes",
                description = "BFS and DFS summary",
                sourceUri = null,
                author = "Me",
                totalUnits = null,
                completedUnits = 0,
                primary = true,
                favorite = true,
                archived = false,
                tags = listOf("graphs"),
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            ),
        ),
    )

    private fun plan() = StudyPlan(
        id = planId,
        name = "My real plan",
        description = "",
        type = StudyType.FREE_STUDY,
        status = StudyPlanStatus.ACTIVE,
        examDateIso = null,
        weeklyGoalHours = 10,
        progressPercent = 40,
        studiedMinutesThisWeek = 300,
        questionsAnswered = 40,
        averageAccuracyPercent = 75,
    )
}
