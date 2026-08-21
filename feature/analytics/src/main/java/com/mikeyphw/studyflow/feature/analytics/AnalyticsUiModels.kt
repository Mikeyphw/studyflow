package com.mikeyphw.studyflow.feature.analytics

import androidx.annotation.StringRes
import com.mikeyphw.studyflow.core.domain.ModeAwareAnalyticsCalculator
import com.mikeyphw.studyflow.core.domain.ModeAwareAnalyticsMetricKey
import com.mikeyphw.studyflow.core.domain.ModeAwareWeakAreaLabel
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.model.StudyContentMode

data class AnalyticsMetricRow(
    @StringRes val titleRes: Int,
    val value: String,
)

fun StudyAnalyticsSnapshot.metricRows(
    contentMode: StudyContentMode = StudyContentMode.EXAM_PLAN,
): List<AnalyticsMetricRow> = ModeAwareAnalyticsCalculator
    .buildProfile(this, contentMode)
    .metrics
    .map { metric -> AnalyticsMetricRow(metric.key.titleRes(), metric.value) }

fun StudyContentMode.weakAreasTitleRes(): Int = ModeAwareAnalyticsCalculator
    .buildProfile(emptyAnalyticsSnapshot(), this)
    .weakAreaLabel
    .titleRes()

private fun ModeAwareAnalyticsMetricKey.titleRes(): Int = when (this) {
    ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY -> R.string.study_consistency
    ModeAwareAnalyticsMetricKey.SCHEDULE_ADHERENCE -> R.string.schedule_adherence
    ModeAwareAnalyticsMetricKey.QUESTION_ACCURACY -> R.string.question_accuracy
    ModeAwareAnalyticsMetricKey.GOAL_COMPLETION -> R.string.goal_completion
    ModeAwareAnalyticsMetricKey.CONTENT_PROGRESS -> R.string.content_progress
    ModeAwareAnalyticsMetricKey.LIBRARY_PROGRESS -> R.string.library_progress
    ModeAwareAnalyticsMetricKey.MODULES_COMPLETED -> R.string.modules_completed
    ModeAwareAnalyticsMetricKey.EXERCISES_COMPLETED -> R.string.exercises_completed
    ModeAwareAnalyticsMetricKey.WEAK_CONCEPTS -> R.string.weak_concepts
    ModeAwareAnalyticsMetricKey.DEBUGGING_ACCURACY -> R.string.debugging_accuracy
    ModeAwareAnalyticsMetricKey.PROJECT_PROGRESS -> R.string.project_progress
    ModeAwareAnalyticsMetricKey.IDIOMS_LEARNED -> R.string.idioms_learned
    ModeAwareAnalyticsMetricKey.IDIOMS_DUE -> R.string.idioms_due
    ModeAwareAnalyticsMetricKey.USAGE_ACCURACY -> R.string.usage_accuracy
    ModeAwareAnalyticsMetricKey.MISTAKE_PATTERNS -> R.string.mistake_patterns
    ModeAwareAnalyticsMetricKey.REGISTER_COVERAGE -> R.string.register_coverage
    ModeAwareAnalyticsMetricKey.READING_PROGRESS -> R.string.reading_progress
    ModeAwareAnalyticsMetricKey.SKILL_PROGRESS -> R.string.skill_progress
}

private fun ModeAwareWeakAreaLabel.titleRes(): Int = when (this) {
    ModeAwareWeakAreaLabel.WEAK_AREAS -> R.string.weak_areas
    ModeAwareWeakAreaLabel.WEAK_CONCEPTS -> R.string.weak_concepts
    ModeAwareWeakAreaLabel.LANGUAGE_MISTAKE_PATTERNS -> R.string.mistake_patterns
    ModeAwareWeakAreaLabel.READING_GAPS -> R.string.reading_gaps
    ModeAwareWeakAreaLabel.SKILL_GAPS -> R.string.skill_gaps
}

private fun emptyAnalyticsSnapshot() = StudyAnalyticsSnapshot(
    studyPlanId = com.mikeyphw.studyflow.core.model.StudyPlanId("analytics-title-only"),
    studiedMinutesLastSevenDays = 0,
    completedSessionsLastSevenDays = 0,
    scheduleAdherencePercent = 0,
    questionAccuracyPercent = 0,
    answeredQuestions = 0,
    dueReviewCount = 0,
    overdueReviewCount = 0,
    retentionEaseAverage = 0.0,
    goalCompletionPercent = 0,
    contentProgressPercent = 0,
    libraryProgressPercent = 0,
    consistencyScorePercent = 0,
    weakAreas = emptyList(),
    insights = emptyList(),
)
