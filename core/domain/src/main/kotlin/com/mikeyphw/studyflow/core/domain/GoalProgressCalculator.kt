package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.Topic
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object GoalProgressCalculator {
    fun goalPercent(goal: StudyGoal): Int {
        if (goal.targetValue <= 0.0) return 0
        return ((goal.currentValue.coerceAtLeast(0.0) / goal.targetValue) * 100.0).toInt().coerceIn(0, 100)
    }

    fun overallGoalPercent(goals: List<StudyGoal>): Int = if (goals.isEmpty()) 0 else {
        goals.filter { !it.archived && it.completedAtEpochMillis == null }
            .map(::goalPercent)
            .ifEmpty { listOf(100) }
            .average()
            .toInt()
    }
}

data class GoalEvidence(
    val sessions: List<StudySession>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
    val topics: List<Topic>,
)

object StudyGoalProgressEngine {
    fun evaluate(
        goal: StudyGoal,
        evidence: GoalEvidence,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): StudyGoal {
        if (goal.archived) return goal
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val (start, endExclusive) = effectivePeriod(goal, today)
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val sessions = evidence.sessions.filter { session ->
            session.status == StudySessionStatus.COMPLETED &&
                session.startedAtEpochMillis in startMillis until endMillis &&
                (goal.subjectId == null || session.subjectId == goal.subjectId) &&
                (goal.topicId == null || session.topicId == goal.topicId)
        }
        val submittedTests = evidence.tests.filter { test ->
            val submittedAt = test.submittedAtEpochMillis
            submittedAt != null && submittedAt in startMillis until endMillis
        }
        val submittedMocks = evidence.mockExams.filter { mock ->
            val submittedAt = mock.submittedAtEpochMillis
            submittedAt != null && submittedAt in startMillis until endMillis
        }
        val current = when (goal.type) {
            GoalType.STUDY_TIME -> sessions.sumOf { session ->
                (session.grossDurationSeconds - session.pauseDurationSeconds).coerceAtLeast(0L)
            } / 60.0
            GoalType.SESSION_COUNT -> sessions.size.toDouble()
            GoalType.QUESTION_COUNT -> (
                sessions.sumOf(StudySession::questionsAnswered) + submittedTests.sumOf(StudyTest::answeredCount)
            ).toDouble()
            GoalType.ACCURACY -> {
                val answered = sessions.sumOf(StudySession::questionsAnswered) + submittedTests.sumOf(StudyTest::answeredCount)
                val correct = sessions.sumOf(StudySession::correctAnswers) + submittedTests.sumOf(StudyTest::correctCount)
                if (answered == 0) 0.0 else correct * 100.0 / answered
            }
            GoalType.TOPIC_COMPLETION -> evidence.topics.count { topic ->
                !topic.archived && topic.progressPercent >= 100 &&
                    (goal.subjectId == null || topic.subjectId == goal.subjectId) &&
                    (goal.topicId == null || topic.id == goal.topicId)
            }.toDouble()
            GoalType.MOCK_EXAM_COUNT -> submittedMocks.size.toDouble()
            GoalType.CUSTOM -> goal.currentValue
        }.coerceAtLeast(0.0)
        return goal.copy(
            currentValue = current,
            startsOnIso = start.toString(),
            endsOnIso = when (goal.period) {
                GoalPeriod.CUSTOM -> goal.endsOnIso
                else -> endExclusive.minusDays(1).toString()
            },
            completedAtEpochMillis = if (current >= goal.targetValue) goal.completedAtEpochMillis ?: nowEpochMillis else null,
        )
    }

    private fun effectivePeriod(goal: StudyGoal, today: LocalDate): Pair<LocalDate, LocalDate> {
        val configuredStart = runCatching { LocalDate.parse(goal.startsOnIso) }.getOrElse { today }
        return when (goal.period) {
            GoalPeriod.DAILY -> today to today.plusDays(1)
            GoalPeriod.WEEKLY -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            }
            GoalPeriod.MONTHLY -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            GoalPeriod.CUSTOM -> {
                val end = goal.endsOnIso?.let { runCatching { LocalDate.parse(it).plusDays(1) }.getOrNull() }
                    ?: configuredStart.plusYears(100)
                configuredStart to end
            }
        }
    }
}

data class StudyProgressSnapshot(
    val goals: List<StudyGoal>,
    val notes: List<StudyNote>,
) {
    val activeGoals: List<StudyGoal> = goals.filter { !it.archived && it.completedAtEpochMillis == null }
    val completedGoals: List<StudyGoal> = goals.filter { !it.archived && it.completedAtEpochMillis != null }
    val archivedGoals: List<StudyGoal> = goals.filter(StudyGoal::archived)
    val pinnedNotes: List<StudyNote> = notes.filter(StudyNote::pinned)
    val goalProgressPercent: Int = GoalProgressCalculator.overallGoalPercent(goals)
}
