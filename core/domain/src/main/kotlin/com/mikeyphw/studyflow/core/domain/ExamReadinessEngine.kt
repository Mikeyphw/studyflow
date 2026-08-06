package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.ReadinessSignal
import com.mikeyphw.studyflow.core.model.ReadinessSignalKind
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudySchedule
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
    ): ExamReadinessScore {
        val daysUntilExam = daysUntil(plan.examDateIso, nowEpochMillis)
        val coverage = averagePercent(plan.progressPercent, analytics.contentProgressPercent)
        val reviewHealth = reviewHealth(analytics.dueReviewCount, analytics.overdueReviewCount)
        val practiceHealth = practiceHealth(analytics.questionAccuracyPercent, analytics.answeredQuestions)
        val mockHealth = mockExamHealth(mockExams)
        val consistency = analytics.consistencyScorePercent.coerceIn(0, 100)
        val scheduleHealth = analytics.scheduleAdherencePercent.coerceIn(0, 100)
        val timeFeasibility = timeFeasibility(
            coveragePercent = coverage,
            weeklyGoalHours = plan.weeklyGoalHours,
            daysUntilExam = daysUntilExam,
            pendingMinutes = schedule.pendingActivities.sumOf { it.plannedDurationMinutes },
        )
        val score = weightedAverage(
            coverage to 24,
            reviewHealth to 18,
            practiceHealth to 14,
            mockHealth to 14,
            consistency to 10,
            scheduleHealth to 10,
            timeFeasibility to 10,
        )
        val band = bandFor(score)
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
        )
    }

    fun bandFor(scorePercent: Int): ExamReadinessBand = when (scorePercent.coerceIn(0, 100)) {
        in 80..100 -> ExamReadinessBand.ON_TRACK
        in 65..79 -> ExamReadinessBand.WATCH
        in 45..64 -> ExamReadinessBand.AT_RISK
        else -> ExamReadinessBand.CRITICAL
    }

    internal fun daysUntil(examDateIso: String?, nowEpochMillis: Long): Int? {
        val parts = examDateIso?.split('-') ?: return null
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val currentDays = floorDiv(nowEpochMillis, MILLIS_PER_DAY)
        val examDays = daysFromCivil(year, month, day)
        return (examDays - currentDays).toInt().coerceAtLeast(0)
    }

    private fun buildSignals(
        coverage: Int,
        reviewHealth: Int,
        practiceHealth: Int,
        mockHealth: Int,
        consistency: Int,
        scheduleHealth: Int,
        timeFeasibility: Int,
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

    private fun signal(kind: ReadinessSignalKind, label: String, score: Int, message: String): ReadinessSignal =
        ReadinessSignal(kind, label, score.coerceIn(0, 100), message, bandFor(score))

    private fun averagePercent(first: Int, second: Int): Int = ((first.coerceIn(0, 100) + second.coerceIn(0, 100)) / 2)

    private fun reviewHealth(due: Int, overdue: Int): Int {
        val penalty = overdue.coerceAtLeast(0) * 9 + due.coerceAtLeast(0) * 3
        return (100 - penalty).coerceIn(0, 100)
    }

    private fun practiceHealth(accuracy: Int, answered: Int): Int = when {
        answered <= 0 -> 45
        answered < 20 -> averagePercent(accuracy, 55)
        else -> accuracy.coerceIn(0, 100)
    }

    private fun mockExamHealth(mockExams: List<MockExam>): Int {
        val submitted = mockExams.filter { it.submittedAtEpochMillis != null && it.scorePercent != null }
        if (submitted.isEmpty()) return 50
        val recent = submitted.sortedByDescending { it.submittedAtEpochMillis }.take(3)
        return recent.mapNotNull { it.scorePercent }.average().roundToInt().coerceIn(0, 100)
    }

    private fun timeFeasibility(
        coveragePercent: Int,
        weeklyGoalHours: Int,
        daysUntilExam: Int?,
        pendingMinutes: Int,
    ): Int {
        if (daysUntilExam == null) return 70
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

    private fun weightedAverage(vararg pairs: Pair<Int, Int>): Int {
        val totalWeight = pairs.sumOf { it.second.coerceAtLeast(0) }
        if (totalWeight == 0) return 0
        return (pairs.sumOf { (value, weight) -> value.coerceIn(0, 100) * weight } / totalWeight.toDouble())
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
