package com.mikeyphw.studyflow.feature.analytics

import androidx.annotation.StringRes
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot

data class AnalyticsMetricRow(
    @StringRes val titleRes: Int,
    val value: String,
)

fun StudyAnalyticsSnapshot.metricRows(): List<AnalyticsMetricRow> = listOf(
    AnalyticsMetricRow(R.string.study_consistency, "$consistencyScorePercent%"),
    AnalyticsMetricRow(R.string.schedule_adherence, "$scheduleAdherencePercent%"),
    AnalyticsMetricRow(R.string.question_accuracy, "$questionAccuracyPercent%"),
    AnalyticsMetricRow(R.string.goal_completion, "$goalCompletionPercent%"),
    AnalyticsMetricRow(R.string.content_progress, "$contentProgressPercent%"),
    AnalyticsMetricRow(R.string.library_progress, "$libraryProgressPercent%"),
)
