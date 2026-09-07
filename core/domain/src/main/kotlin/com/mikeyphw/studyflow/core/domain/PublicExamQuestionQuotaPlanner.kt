package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Evidence-backed quota for the active OV14 phase. No question count is invented when no phase goal exists. */
data class PublicExamQuestionQuotaSubject(
    val subjectId: SubjectId,
    val subjectName: String,
    val weeklyQuestions: Int,
    val phaseQuestionsAnswered: Int,
    val recentAccuracyPercent: Int?,
    val recurringErrors: Int,
    val overdueReviews: Int,
    val priorityShare: Double,
)

data class PublicExamQuestionQuotaPlan(
    val phase: StudyPhaseDefinition,
    val phaseTargetQuestions: Int,
    val phaseQuestionsAnswered: Int,
    val remainingQuestions: Int,
    val remainingDays: Int,
    val dailyQuestions: Int,
    val nextSevenDaysQuestions: Int,
    val subjects: List<PublicExamQuestionQuotaSubject>,
) {
    init {
        require(remainingQuestions >= 0)
        require(remainingDays >= 1)
        require(dailyQuestions >= 0)
        require(nextSevenDaysQuestions >= 0)
        require(subjects.sumOf(PublicExamQuestionQuotaSubject::weeklyQuestions) == nextSevenDaysQuestions) {
            "Subject quotas must sum to the next-seven-days quota."
        }
    }
}

object PublicExamQuestionQuotaPlanner {
    fun plan(
        phase: StudyPhaseDefinition,
        subjects: List<Subject>,
        sessions: List<StudySession>,
        reviews: List<ReviewItem>,
        errors: List<ErrorEntry>,
        tests: List<StudyTest>,
        mockExams: List<MockExam>,
        nowEpochMillis: Long,
        zoneId: ZoneId,
    ): PublicExamQuestionQuotaPlan? {
        val target = phase.questionGoal ?: return null
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        if (today.isBefore(phase.startsOn) || today.isAfter(phase.endsOn)) return null
        val activeSubjects = subjects.filterNot(Subject::archived)
        if (activeSubjects.isEmpty()) return null

        fun inPhase(epochMillis: Long): Boolean {
            val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
            return !date.isBefore(phase.startsOn) && !date.isAfter(phase.endsOn)
        }

        val completedSessions = sessions.filter { session ->
            session.status == StudySessionStatus.COMPLETED && inPhase(session.startedAtEpochMillis)
        }
        val sessionAnswered = completedSessions.sumOf(StudySession::questionsAnswered)
        val testAnswered = tests.filter { it.submittedAtEpochMillis?.let(::inPhase) == true }.sumOf(StudyTest::answeredCount)
        val mockAnswered = mockExams.filter { it.submittedAtEpochMillis?.let(::inPhase) == true }.sumOf(MockExam::answeredCount)
        val answered = sessionAnswered + testAnswered + mockAnswered
        val remaining = (target - answered).coerceAtLeast(0)
        val remainingDays = (ChronoUnit.DAYS.between(today, phase.endsOn) + 1).toInt().coerceAtLeast(1)
        val daily = if (remaining == 0) 0 else ceil(remaining.toDouble() / remainingDays).toInt()
        val horizonDays = minOf(7, remainingDays)
        val weekly = if (remaining == 0) 0 else minOf(
            remaining,
            ceil(remaining.toDouble() * horizonDays / remainingDays).toInt(),
        )

        val perSubjectAnswered = activeSubjects.associate { subject ->
            subject.id to completedSessions.filter { it.subjectId == subject.id }.sumOf(StudySession::questionsAnswered)
        }
        val perSubjectAccuracy = activeSubjects.associate { subject ->
            val scoped = completedSessions.filter { it.subjectId == subject.id }
            val attempts = scoped.sumOf(StudySession::questionsAnswered)
            val correct = scoped.sumOf(StudySession::correctAnswers)
            subject.id to if (attempts > 0) ((correct * 100.0) / attempts).toInt().coerceIn(0, 100) else null
        }
        val recurring = activeSubjects.associate { subject ->
            subject.id to errors.count { it.subjectId == subject.id && it.recurrenceCount > 1 }
        }
        val overdue = activeSubjects.associate { subject ->
            subject.id to reviews.count {
                it.subjectId == subject.id &&
                    it.dueAtEpochMillis <= nowEpochMillis &&
                    (it.status == ReviewStatus.DUE || it.status == ReviewStatus.SCHEDULED)
            }
        }

        val weightValues = activeSubjects.map { max(0.0, it.examWeight) }
        val weightShares = normalized(weightValues, equalFallback = true)
        val subjectAnsweredTotal = perSubjectAnswered.values.sum().toDouble()
        val answeredShares = activeSubjects.map { subject ->
            if (subjectAnsweredTotal > 0.0) perSubjectAnswered.getValue(subject.id) / subjectAnsweredTotal else 0.0
        }
        val underPracticeShares = normalized(
            weightShares.indices.map { index -> max(0.0, weightShares[index] - answeredShares[index]) },
            equalFallback = false,
        )
        val accuracyShares = normalized(activeSubjects.map { subject ->
            perSubjectAccuracy.getValue(subject.id)?.let { (100 - it).coerceAtLeast(0).toDouble() } ?: 0.0
        }, equalFallback = false)
        val errorShares = normalized(activeSubjects.map { recurring.getValue(it.id).toDouble() }, equalFallback = false)
        val reviewShares = normalized(activeSubjects.map { overdue.getValue(it.id).toDouble() }, equalFallback = false)

        val raw = activeSubjects.indices.map { index ->
            0.35 * weightShares[index] +
                0.20 * underPracticeShares[index] +
                0.20 * accuracyShares[index] +
                0.15 * errorShares[index] +
                0.10 * reviewShares[index]
        }
        val priorityShares = normalized(raw, equalFallback = true)
        val allocations = allocateExactly(weekly, priorityShares)
        val rows = activeSubjects.mapIndexed { index, subject ->
            PublicExamQuestionQuotaSubject(
                subjectId = subject.id,
                subjectName = subject.name,
                weeklyQuestions = allocations[index],
                phaseQuestionsAnswered = perSubjectAnswered.getValue(subject.id),
                recentAccuracyPercent = perSubjectAccuracy.getValue(subject.id),
                recurringErrors = recurring.getValue(subject.id),
                overdueReviews = overdue.getValue(subject.id),
                priorityShare = priorityShares[index],
            )
        }.sortedWith(compareByDescending<PublicExamQuestionQuotaSubject> { it.weeklyQuestions }.thenBy { it.subjectName })

        return PublicExamQuestionQuotaPlan(
            phase = phase,
            phaseTargetQuestions = target,
            phaseQuestionsAnswered = answered,
            remainingQuestions = remaining,
            remainingDays = remainingDays,
            dailyQuestions = daily,
            nextSevenDaysQuestions = weekly,
            subjects = rows,
        )
    }

    private fun normalized(values: List<Double>, equalFallback: Boolean): List<Double> {
        if (values.isEmpty()) return emptyList()
        val positive = values.map { max(0.0, it) }
        val total = positive.sum()
        if (total > 0.0) return positive.map { it / total }
        return if (equalFallback) List(values.size) { 1.0 / values.size } else List(values.size) { 0.0 }
    }

    private fun allocateExactly(total: Int, shares: List<Double>): List<Int> {
        if (shares.isEmpty()) return emptyList()
        if (total <= 0) return List(shares.size) { 0 }
        val exact = shares.map { it * total }
        val base = exact.map { floor(it).toInt() }.toMutableList()
        var remaining = total - base.sum()
        exact.indices
            .sortedWith(compareByDescending<Int> { exact[it] - base[it] }.thenBy { it })
            .forEach { index ->
                if (remaining > 0) {
                    base[index] += 1
                    remaining -= 1
                }
            }
        return base
    }
}
