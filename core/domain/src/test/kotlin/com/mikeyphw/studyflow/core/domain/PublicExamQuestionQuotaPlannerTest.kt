package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicExamQuestionQuotaPlannerTest {
    @Test
    fun `remaining phase target becomes exact daily and seven day quotas`() {
        val now = LocalDate.parse("2026-10-17").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val plan = PublicExamQuestionQuotaPlanner.plan(
            phase = phase(goal = 500),
            subjects = listOf(subject("a", "A", 50.0), subject("b", "B", 50.0)),
            sessions = listOf(session("s", "a", 200, 150, "2026-10-10")),
            reviews = emptyList(), errors = emptyList(), tests = emptyList(), mockExams = emptyList(),
            nowEpochMillis = now, zoneId = ZoneOffset.UTC,
        )
        requireNotNull(plan)
        assertEquals(300, plan.remainingQuestions)
        assertEquals(15, plan.remainingDays)
        assertEquals(20, plan.dailyQuestions)
        assertEquals(140, plan.nextSevenDaysQuestions)
        assertEquals(140, plan.subjects.sumOf { it.weeklyQuestions })
    }

    @Test
    fun `weak recurring overdue subject receives more quota than equally weighted healthy subject`() {
        val now = LocalDate.parse("2026-10-17").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val plan = PublicExamQuestionQuotaPlanner.plan(
            phase = phase(goal = 500),
            subjects = listOf(subject("weak", "Weak", 50.0), subject("strong", "Strong", 50.0)),
            sessions = listOf(
                session("w", "weak", 20, 8, "2026-10-10"),
                session("s", "strong", 100, 90, "2026-10-10"),
            ),
            reviews = listOf(review("weak", now - 1)),
            errors = listOf(error("weak", 4)),
            tests = emptyList(), mockExams = emptyList(), nowEpochMillis = now, zoneId = ZoneOffset.UTC,
        )
        requireNotNull(plan)
        val weak = plan.subjects.first { it.subjectId == SubjectId("weak") }
        val strong = plan.subjects.first { it.subjectId == SubjectId("strong") }
        assertTrue(weak.weeklyQuestions > strong.weeklyQuestions)
        assertEquals(40, weak.recentAccuracyPercent)
        assertEquals(1, weak.recurringErrors)
        assertEquals(1, weak.overdueReviews)
    }

    @Test
    fun `planner stays unavailable without an active phase question target`() {
        val phase = phase(goal = null)
        assertEquals(null, PublicExamQuestionQuotaPlanner.plan(
            phase, listOf(subject("a", "A", 100.0)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            LocalDate.parse("2026-10-17").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), ZoneOffset.UTC,
        ))
    }

    private fun phase(goal: Int?) = StudyPhaseDefinition(
        StudyPhaseKind.EXPANSION, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
        45, 40, 15, questionGoal = goal, mockExamGoal = 5, allocationIsStudyFlowSuggested = true,
    )

    private fun subject(id: String, name: String, weight: Double) = Subject(
        SubjectId(id), PLAN, name, "", null, 3, weight, 0, 0, 0,
        ContentProgressStatus.STUDYING, 0, false, null,
    )

    private fun session(id: String, subjectId: String, answered: Int, correct: Int, date: String) = StudySession(
        StudySessionId(id), PLAN, null, SubjectId(subjectId), null, null, null,
        StudyActivityType.QUESTIONS, StudySessionStatus.COMPLETED,
        LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        LocalDate.parse(date).atStartOfDay().plusMinutes(30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        1800, 0, 0, answered, correct, null, null, "",
    )

    private fun review(subjectId: String, due: Long) = ReviewItem(
        ReviewItemId("review-$subjectId"), PLAN, SubjectId(subjectId), null, null, null, null,
        ReviewMethod.QUESTION_BASED, ReviewStatus.DUE, due, 7, 1, 2.5, null,
    )

    private fun error(subjectId: String, recurrence: Int) = ErrorEntry(
        ErrorEntryId("error-$subjectId"), PLAN, null, SubjectId(subjectId), null,
        ErrorCategory.CONTENT_GAP, null, null, "", recurrence, 1L, 1L,
    )

    private companion object { val PLAN = StudyPlanId("plan") }
}
