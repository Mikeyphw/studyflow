package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.ReadinessConfidence
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.ReadinessSignal
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
import com.mikeyphw.studyflow.core.model.StudyTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageExamReadiness(
    private val analyticsRepository: StudyAnalyticsRepository,
    private val scheduleRepository: StudyScheduleRepository,
    private val practiceRepository: StudyPracticeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeReadiness(plan: StudyPlan): Flow<ExamReadinessScore> = combine(
        analyticsRepository.observeSnapshot(plan.id),
        scheduleRepository.observeSchedule(plan.id),
        practiceRepository.observeMockExams(plan.id),
    ) { analytics, schedule, mockExams ->
        ExamReadinessEngine.calculate(
            plan = plan,
            analytics = analytics,
            schedule = schedule,
            mockExams = mockExams,
            nowEpochMillis = clock(),
            zoneId = ZoneId.systemDefault(),
        )
    }
}

object ExamReadinessEngine {
    fun calculate(
        plan: StudyPlan,
        analytics: StudyAnalyticsSnapshot,
        schedule: StudySchedule,
        mockExams: List<MockExam>,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        target: StudyTarget? = null,
    ): ExamReadinessScore {
        val daysUntilExam = PublicExamDateResolver.daysUntilOfficial(plan, target, nowEpochMillis, zoneId)
        val coverage = averagePercent(plan.progressPercent, analytics.contentProgressPercent)
        val reviewEvidenceCount = reviewEvidenceCount(analytics)
        val reviewHealth = reviewHealth(analytics.dueReviewCount, analytics.overdueReviewCount)
            .takeIf { reviewEvidenceCount > 0 }
        val practiceHealth = practiceHealth(analytics.questionAccuracyPercent, analytics.answeredQuestions)
        val submittedMocks = submittedMockExams(mockExams)
        val mockHealth = mockExamHealth(submittedMocks)
        val consistency = analytics.consistencyScorePercent
            .coerceIn(0, 100)
            .takeIf { analytics.completedSessionsLastSevenDays > 0 || analytics.studiedMinutesLastSevenDays > 0 }
        val observedScheduleCount = observedScheduleEvidenceCount(schedule, nowEpochMillis)
        val scheduleHealth = analytics.scheduleAdherencePercent
            .coerceIn(0, 100)
            .takeIf { observedScheduleCount > 0 }
        val timeFeasibility = daysUntilExam?.let { days ->
            timeFeasibility(
                coveragePercent = coverage,
                weeklyGoalHours = plan.weeklyGoalHours,
                daysUntilExam = days,
                pendingMinutes = schedule.pendingActivities.sumOf { it.plannedDurationMinutes },
            )
        }
        val availableDimensions = listOfNotNull(
            coverage,
            reviewHealth,
            practiceHealth,
            mockHealth,
            consistency,
            scheduleHealth,
            timeFeasibility,
        ).size
        val evidenceSampleCount = (
            analytics.completedSessionsLastSevenDays.coerceAtLeast(0) +
                analytics.answeredQuestions.coerceAtLeast(0) +
                submittedMocks.size +
                reviewEvidenceCount +
                observedScheduleCount
            )
        val behavioralDimensions = listOfNotNull(
            reviewHealth,
            practiceHealth,
            mockHealth,
            consistency,
            scheduleHealth,
        ).size
        val hasMinimumEvidence = hasMinimumEvidence(
            answeredQuestions = analytics.answeredQuestions,
            completedSessions = analytics.completedSessionsLastSevenDays,
            submittedMocks = submittedMocks.size,
            behavioralDimensions = behavioralDimensions,
            evidenceSampleCount = evidenceSampleCount,
        )
        val score = if (hasMinimumEvidence) {
            weightedAveragePresent(
                coverage to 24,
                reviewHealth to 18,
                practiceHealth to 14,
                mockHealth to 14,
                consistency to 10,
                scheduleHealth to 10,
                timeFeasibility to 10,
            )
        } else {
            null
        }
        val band = score?.let(::bandFor)
        val confidence = score?.let {
            confidenceFor(
                availableDimensions = availableDimensions,
                answeredQuestions = analytics.answeredQuestions,
                completedSessions = analytics.completedSessionsLastSevenDays,
                submittedMocks = submittedMocks.size,
            )
        }
        return ExamReadinessScore(
            studyPlanId = plan.id,
            scorePercent = score,
            band = band,
            daysUntilExam = daysUntilExam,
            coveragePercent = coverage,
            reviewHealthPercent = reviewHealth,
            practiceHealthPercent = practiceHealth,
            mockExamHealthPercent = mockHealth,
            consistencyPercent = consistency,
            scheduleHealthPercent = scheduleHealth,
            timeFeasibilityPercent = timeFeasibility,
            signals = buildSignals(
                coverage = coverage,
                reviewHealth = reviewHealth,
                practiceHealth = practiceHealth,
                mockHealth = mockHealth,
                consistency = consistency,
                scheduleHealth = scheduleHealth,
                timeFeasibility = timeFeasibility,
                daysUntilExam = daysUntilExam,
                weakAreaCount = analytics.weakAreas.size,
            ),
            confidence = confidence,
            evidenceDimensionCount = availableDimensions,
            evidenceSampleCount = evidenceSampleCount,
        )
    }

    fun bandFor(scorePercent: Int): ExamReadinessBand = when (scorePercent.coerceIn(0, 100)) {
        in 80..100 -> ExamReadinessBand.ON_TRACK
        in 65..79 -> ExamReadinessBand.WATCH
        in 45..64 -> ExamReadinessBand.AT_RISK
        else -> ExamReadinessBand.CRITICAL
    }

    internal fun daysUntil(
        examDateIso: String?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int? {
        val examDate = examDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val currentDate = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(currentDate, examDate).toInt().coerceAtLeast(0)
    }

    private fun buildSignals(
        coverage: Int?,
        reviewHealth: Int?,
        practiceHealth: Int?,
        mockHealth: Int?,
        consistency: Int?,
        scheduleHealth: Int?,
        timeFeasibility: Int?,
        daysUntilExam: Int?,
        weakAreaCount: Int,
    ): List<ReadinessSignal> = buildList {
        add(signal(ReadinessSignalKind.COVERAGE, "Coverage", coverage, "Syllabus coverage is the base layer."))
        add(signal(ReadinessSignalKind.RETENTION, "Reviews", reviewHealth, "Overdue reviews erode readiness first."))
        add(
            signal(
                ReadinessSignalKind.PRACTICE,
                "Practice",
                practiceHealth,
                "Question accuracy measures applied recall.",
            ),
        )
        add(
            signal(
                ReadinessSignalKind.MOCK_EXAM,
                "Mock exams",
                mockHealth,
                "Timed score evidence anchors the forecast.",
            ),
        )
        add(signal(ReadinessSignalKind.CONSISTENCY, "Consistency", consistency, "Recent study days protect momentum."))
        add(
            signal(
                ReadinessSignalKind.SCHEDULE,
                "Schedule",
                scheduleHealth,
                "Plan adherence shows whether the week is realistic.",
            ),
        )
        add(
            signal(
                ReadinessSignalKind.TIME_LEFT,
                "Time left",
                timeFeasibility,
                daysUntilExam?.let { "Exam is in $it days." } ?: "No confirmed exam date is set.",
            ),
        )
        if (weakAreaCount > 0) {
            add(
                signal(
                    ReadinessSignalKind.WEAK_AREAS,
                    "Weak areas",
                    (100 - weakAreaCount * 8).coerceIn(0, 100),
                    "$weakAreaCount weak area(s) need targeted remediation.",
                ),
            )
        }
    }

    private fun signal(kind: ReadinessSignalKind, label: String, score: Int?, message: String): ReadinessSignal =
        ReadinessSignal(
            kind = kind,
            label = label,
            scorePercent = score?.coerceIn(0, 100),
            message = message,
            severity = score?.let(::bandFor),
        )

    private fun averagePercent(first: Int, second: Int): Int =
        ((first.coerceIn(0, 100) + second.coerceIn(0, 100)) / 2)

    private fun reviewHealth(due: Int, overdue: Int): Int {
        val penalty = overdue.coerceAtLeast(0) * 9 + due.coerceAtLeast(0) * 3
        return (100 - penalty).coerceIn(0, 100)
    }

    private fun reviewEvidenceCount(analytics: StudyAnalyticsSnapshot): Int =
        analytics.dueReviewCount.coerceAtLeast(0) +
            analytics.overdueReviewCount.coerceAtLeast(0) +
            if (analytics.retentionEaseAverage > 0.0) 1 else 0

    private fun practiceHealth(accuracy: Int, answered: Int): Int? = when {
        answered <= 0 -> null
        answered < 20 -> averagePercent(accuracy, 55)
        else -> accuracy.coerceIn(0, 100)
    }

    private fun submittedMockExams(mockExams: List<MockExam>): List<MockExam> =
        mockExams.filter { it.submittedAtEpochMillis != null && it.scorePercent != null }

    private fun mockExamHealth(submitted: List<MockExam>): Int? {
        if (submitted.isEmpty()) return null
        val recent = submitted.sortedByDescending { it.submittedAtEpochMillis }.take(3)
        return recent.mapNotNull { it.scorePercent }.average().roundToInt().coerceIn(0, 100)
    }

    private fun observedScheduleEvidenceCount(schedule: StudySchedule, nowEpochMillis: Long): Int =
        schedule.plannedActivities.count { activity ->
            activity.status != PlannedActivityStatus.PLANNED ||
                activity.scheduledStartEpochMillis?.let { it <= nowEpochMillis } == true
        }

    private fun timeFeasibility(
        coveragePercent: Int,
        weeklyGoalHours: Int,
        daysUntilExam: Int,
        pendingMinutes: Int,
    ): Int {
        if (daysUntilExam == 0) return coveragePercent
        val availableMinutes = (weeklyGoalHours.coerceAtLeast(1) * 60.0) * (daysUntilExam / 7.0)
        if (pendingMinutes <= 0) {
            val horizonScore = when {
                daysUntilExam >= 30 -> 100
                daysUntilExam >= 14 -> 95
                daysUntilExam >= 7 -> 90
                else -> 85
            }
            return averagePercent(coveragePercent, horizonScore)
        }
        val ratio = availableMinutes / pendingMinutes.toDouble()
        return (ratio * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun hasMinimumEvidence(
        answeredQuestions: Int,
        completedSessions: Int,
        submittedMocks: Int,
        behavioralDimensions: Int,
        evidenceSampleCount: Int,
    ): Boolean = when {
        submittedMocks >= 1 -> true
        answeredQuestions >= 30 -> true
        completedSessions >= 3 -> true
        behavioralDimensions >= 2 && evidenceSampleCount >= 10 -> true
        else -> false
    }

    private fun confidenceFor(
        availableDimensions: Int,
        answeredQuestions: Int,
        completedSessions: Int,
        submittedMocks: Int,
    ): ReadinessConfidence = when {
        availableDimensions >= 5 && answeredQuestions >= 200 && completedSessions >= 5 && submittedMocks >= 2 ->
            ReadinessConfidence.HIGH
        availableDimensions >= 4 &&
            (answeredQuestions >= 50 || completedSessions >= 5 || submittedMocks >= 2) -> ReadinessConfidence.MEDIUM
        else -> ReadinessConfidence.LOW
    }

    private fun weightedAveragePresent(vararg pairs: Pair<Int?, Int>): Int? {
        val present = pairs.filter { (value, weight) -> value != null && weight > 0 }
        val totalWeight = present.sumOf { it.second }
        if (totalWeight == 0) return null
        return (present.sumOf { (value, weight) -> requireNotNull(value).coerceIn(0, 100) * weight } /
            totalWeight.toDouble())
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun floorDiv(value: Long, divisor: Long): Long {
        var result = value / divisor
        if ((value xor divisor) < 0 && result * divisor != value) result -= 1
        return result
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        var y = year
        val m = month
        y -= if (m <= 2) 1 else 0
        val era = floorDiv(y.toLong(), 400L)
        val yoe = (y.toLong() - era * 400L).toInt()
        val mp = m + if (m > 2) -3 else 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - DAYS_0000_TO_1970
    }

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val DAYS_0000_TO_1970 = 719468L
}
