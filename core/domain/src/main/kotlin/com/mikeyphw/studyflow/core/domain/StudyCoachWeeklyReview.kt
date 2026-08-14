package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.AdaptiveReschedulePreview
import com.mikeyphw.studyflow.core.model.ExamReadinessBand
import com.mikeyphw.studyflow.core.model.ExamReadinessScore
import com.mikeyphw.studyflow.core.model.StudyPlan
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Phase 59 turns existing V2 evidence into one weekly coaching summary. */
enum class StudyCoachWeeklyReviewItemKind {
    READINESS_STABLE,
    READINESS_RISK,
    REVIEW_BACKLOG,
    PRACTICE_ACCURACY,
    SCHEDULE_DRIFT,
    MOCK_EXAM_SIGNAL,
    CONSISTENCY_WIN,
    EVIDENCE_EMPTY,
    RESCHEDULE_READY,
}

enum class StudyCoachWeeklyReviewTone {
    WIN,
    WATCH,
    ACTION,
}

data class StudyCoachWeeklyReviewItem(
    val kind: StudyCoachWeeklyReviewItemKind,
    val tone: StudyCoachWeeklyReviewTone,
    val scorePercent: Int?,
    val count: Int?,
) {
    init {
        require(scorePercent == null || scorePercent in 0..100) { "Coach score must be between 0 and 100." }
        require(count == null || count >= 0) { "Coach count cannot be negative." }
    }
}

data class StudyCoachWeeklyReview(
    val studyPlanName: String,
    val generatedAtEpochMillis: Long,
    val studiedMinutes: Int,
    val evidenceCount: Int,
    val readinessBand: ExamReadinessBand?,
    val items: List<StudyCoachWeeklyReviewItem>,
) {
    init {
        require(studyPlanName.isNotBlank()) { "Study Plan name cannot be blank." }
        require(generatedAtEpochMillis >= 0L) { "Weekly review time cannot be negative." }
        require(studiedMinutes >= 0) { "Studied minutes cannot be negative." }
        require(evidenceCount >= 0) { "Evidence count cannot be negative." }
    }

    val actionCount: Int = items.count { it.tone == StudyCoachWeeklyReviewTone.ACTION }
    val watchCount: Int = items.count { it.tone == StudyCoachWeeklyReviewTone.WATCH }
    val winCount: Int = items.count { it.tone == StudyCoachWeeklyReviewTone.WIN }
}

data class StudyCoachWeeklyReviewInput(
    val plan: StudyPlan,
    val analytics: StudyAnalyticsSnapshot?,
    val readiness: ExamReadinessScore?,
    val reschedulePreview: AdaptiveReschedulePreview?,
    val evidence: List<StudyEvidenceEvent>,
    val generatedAtEpochMillis: Long,
    val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    init {
        require(generatedAtEpochMillis >= 0L) { "Weekly review time cannot be negative." }
    }
}

object StudyCoachWeeklyReviewBuilder {
    fun build(input: StudyCoachWeeklyReviewInput): StudyCoachWeeklyReview {
        val generatedDate = Instant.ofEpochMilli(input.generatedAtEpochMillis).atZone(input.zoneId).toLocalDate()
        val weekStartDate = generatedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = weekStartDate.atStartOfDay(input.zoneId).toInstant().toEpochMilli()
        val weekEndExclusive = weekStartDate.plusDays(7).atStartOfDay(input.zoneId).toInstant().toEpochMilli()
        val weeklyEvidence = input.evidence.filter { event ->
            event.occurredAtEpochMillis in weekStart until weekEndExclusive
        }
        val weeklyInput = input.copy(evidence = weeklyEvidence)
        val studiedMinutes = weeklyEvidence.sumOf { event -> event.minutes ?: 0 }
        return StudyCoachWeeklyReview(
            studyPlanName = input.plan.name,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
            studiedMinutes = studiedMinutes,
            evidenceCount = weeklyEvidence.size,
            readinessBand = input.readiness?.band,
            items = itemsFor(weeklyInput),
        )
    }

    private fun itemsFor(input: StudyCoachWeeklyReviewInput): List<StudyCoachWeeklyReviewItem> = buildList {
        val readiness = input.readiness
        val analytics = input.analytics
        when (readiness?.band) {
            ExamReadinessBand.ON_TRACK -> add(
                item(
                    StudyCoachWeeklyReviewItemKind.READINESS_STABLE,
                    StudyCoachWeeklyReviewTone.WIN,
                    readiness.scorePercent,
                ),
            )
            ExamReadinessBand.WATCH,
            ExamReadinessBand.AT_RISK,
            ExamReadinessBand.CRITICAL -> add(
                item(
                    StudyCoachWeeklyReviewItemKind.READINESS_RISK,
                    StudyCoachWeeklyReviewTone.ACTION,
                    readiness.scorePercent,
                ),
            )
            null -> Unit
        }
        val backlog = analytics?.overdueReviewCount ?: 0
        if (backlog > 0) {
            add(item(StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG, StudyCoachWeeklyReviewTone.ACTION, count = backlog))
        }
        val accuracy = analytics?.questionAccuracyPercent
        if (accuracy != null && accuracy in 1..59) {
            add(item(StudyCoachWeeklyReviewItemKind.PRACTICE_ACCURACY, StudyCoachWeeklyReviewTone.ACTION, accuracy))
        }
        val adherence = analytics?.scheduleAdherencePercent
        if (adherence != null && adherence < 70) {
            add(item(StudyCoachWeeklyReviewItemKind.SCHEDULE_DRIFT, StudyCoachWeeklyReviewTone.WATCH, adherence))
        }
        if (readiness != null && readiness.mockExamHealthPercent < 60) {
            add(
                item(
                    StudyCoachWeeklyReviewItemKind.MOCK_EXAM_SIGNAL,
                    StudyCoachWeeklyReviewTone.WATCH,
                    readiness.mockExamHealthPercent,
                ),
            )
        }
        if ((analytics?.consistencyScorePercent ?: 0) >= 80) {
            add(
                item(
                    StudyCoachWeeklyReviewItemKind.CONSISTENCY_WIN,
                    StudyCoachWeeklyReviewTone.WIN,
                    analytics?.consistencyScorePercent,
                ),
            )
        }
        val executableChanges = input.reschedulePreview?.executableChangeCount ?: 0
        if (executableChanges > 0) {
            add(
                item(
                    StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY,
                    StudyCoachWeeklyReviewTone.ACTION,
                    count = executableChanges,
                ),
            )
        }
        if (input.evidence.isEmpty()) {
            add(item(StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY, StudyCoachWeeklyReviewTone.WATCH))
        }
        if (isEmpty()) {
            val score = listOfNotNull(
                analytics?.scheduleAdherencePercent,
                analytics?.goalCompletionPercent,
                readiness?.scorePercent,
            ).averageOrNull()?.roundToInt()
            add(item(StudyCoachWeeklyReviewItemKind.READINESS_STABLE, StudyCoachWeeklyReviewTone.WIN, score))
        }
    }.distinctBy(StudyCoachWeeklyReviewItem::kind)
        .sortedBy { candidate -> candidate.kind.coachPriority }
        .take(5)

    private val StudyCoachWeeklyReviewItemKind.coachPriority: Int
        get() = when (this) {
            StudyCoachWeeklyReviewItemKind.READINESS_RISK -> 0
            StudyCoachWeeklyReviewItemKind.READINESS_STABLE -> 0
            StudyCoachWeeklyReviewItemKind.RESCHEDULE_READY -> 1
            StudyCoachWeeklyReviewItemKind.REVIEW_BACKLOG -> 2
            StudyCoachWeeklyReviewItemKind.PRACTICE_ACCURACY -> 3
            StudyCoachWeeklyReviewItemKind.SCHEDULE_DRIFT -> 4
            StudyCoachWeeklyReviewItemKind.MOCK_EXAM_SIGNAL -> 5
            StudyCoachWeeklyReviewItemKind.CONSISTENCY_WIN -> 6
            StudyCoachWeeklyReviewItemKind.EVIDENCE_EMPTY -> 7
        }

    private fun item(
        kind: StudyCoachWeeklyReviewItemKind,
        tone: StudyCoachWeeklyReviewTone,
        scorePercent: Int? = null,
        count: Int? = null,
    ): StudyCoachWeeklyReviewItem = StudyCoachWeeklyReviewItem(
        kind = kind,
        tone = tone,
        scorePercent = scorePercent?.coerceIn(0, 100),
        count = count,
    )

    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
