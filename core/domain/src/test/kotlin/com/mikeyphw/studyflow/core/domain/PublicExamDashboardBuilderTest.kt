package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.*
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicExamDashboardBuilderTest {
    private val planId = StudyPlanId("p")
    private val subjectId = SubjectId("s")
    private val topicId = TopicId("t")
    private val now = 2_000_000_000_000L

    @Test
    fun `zero question plan preserves unknown rates and cutoff gap`() {
        val dashboard = PublicExamDashboardBuilder.build(
            plan = plan(), target = null, syllabus = syllabus(), questions = emptyList(), reviews = emptyList(),
            errors = emptyList(), sessions = emptyList(), mockExams = emptyList(), nowEpochMillis = now,
            zoneId = ZoneId.of("UTC"),
        )
        assertEquals(0, dashboard.metrics.totalQuestions)
        assertNull(dashboard.metrics.accuracy7d)
        assertNull(dashboard.metrics.estimatedCutoffGapPercent)
    }

    @Test
    fun `seven thirty ninety day accuracy windows use completed session evidence`() {
        val sessions = listOf(
            session(now - 2 * 86_400_000L, 10, 8),
            session(now - 15 * 86_400_000L, 10, 5),
            session(now - 60 * 86_400_000L, 10, 2),
        )
        val d = PublicExamDashboardBuilder.build(plan(), null, syllabus(), emptyList(), emptyList(), emptyList(),
            sessions, emptyList(), now, ZoneId.of("UTC"))
        assertEquals(80.0, d.metrics.accuracy7d)
        assertEquals(65.0, d.metrics.accuracy30d)
        assertEquals(50.0, d.metrics.accuracy90d)
    }

    @Test
    fun `weighted mastery and matrix preserve unknown incidence`() {
        val d = PublicExamDashboardBuilder.build(plan(), null, syllabus(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), now, ZoneId.of("UTC"))
        assertTrue((d.metrics.weightedMasteryPercent ?: 0.0) > 0.0)
        assertNull(d.matrix.single().incidencePercent)
    }

    private fun plan() = StudyPlan(
        id = planId, name = "Concurso", description = "", type = StudyType.PUBLIC_EXAMINATION,
        contentMode = StudyContentMode.EXAM_PLAN, status = StudyPlanStatus.ACTIVE, examDateIso = null,
        weeklyGoalHours = 10, progressPercent = 40, studiedMinutesThisWeek = 0, questionsAnswered = 0,
        averageAccuracyPercent = 0, createdAtEpochMillis = 1, updatedAtEpochMillis = 1, trashedAtEpochMillis = null,
    )

    private fun syllabus() = StudySyllabus(
        studyPlanId = planId,
        subjects = listOf(Subject(subjectId, planId, "Direito", "", null, 5, 2.0, 60, 50, 0,
            ContentProgressStatus.STUDYING, 0, false)),
        topics = listOf(Topic(topicId, planId, subjectId, null, "Constitucional", "", 5, 3, null, 30, 50, 60,
            ContentProgressStatus.STUDYING, 0, false)),
    )

    private fun session(start: Long, answered: Int, correct: Int) = StudySession(
        id = StudySessionId("session-$start"), studyPlanId = planId, plannedActivityId = null, subjectId = subjectId,
        topicId = topicId, materialId = null, videoResourceId = null, type = StudyActivityType.QUESTIONS,
        status = StudySessionStatus.COMPLETED, startedAtEpochMillis = start, endedAtEpochMillis = start + 3600000,
        grossDurationSeconds = 3600, pauseDurationSeconds = 0, pagesCompleted = 0, questionsAnswered = answered,
        correctAnswers = correct, concentrationRating = null, difficultyRating = null, notes = "",
    )
}
