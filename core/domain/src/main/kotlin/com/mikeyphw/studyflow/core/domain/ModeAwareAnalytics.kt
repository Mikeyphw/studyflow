package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyContentMode

enum class ModeAwareAnalyticsMetricKey {
    STUDY_CONSISTENCY,
    SCHEDULE_ADHERENCE,
    QUESTION_ACCURACY,
    GOAL_COMPLETION,
    CONTENT_PROGRESS,
    LIBRARY_PROGRESS,
    MODULES_COMPLETED,
    EXERCISES_COMPLETED,
    WEAK_CONCEPTS,
    DEBUGGING_ACCURACY,
    PROJECT_PROGRESS,
    IDIOMS_LEARNED,
    IDIOMS_DUE,
    USAGE_ACCURACY,
    MISTAKE_PATTERNS,
    REGISTER_COVERAGE,
    READING_PROGRESS,
    SKILL_PROGRESS,
}

enum class ModeAwareWeakAreaLabel {
    WEAK_AREAS,
    WEAK_CONCEPTS,
    LANGUAGE_MISTAKE_PATTERNS,
    READING_GAPS,
    SKILL_GAPS,
}

data class ModeAwareAnalyticsMetric(
    val key: ModeAwareAnalyticsMetricKey,
    val value: String,
    val priority: Int,
) {
    init {
        require(value.isNotBlank()) { "Metric value cannot be blank." }
        require(priority >= 0) { "Metric priority cannot be negative." }
    }
}

data class ModeAwareAnalyticsProfile(
    val contentMode: StudyContentMode,
    val weakAreaLabel: ModeAwareWeakAreaLabel,
    val metrics: List<ModeAwareAnalyticsMetric>,
) {
    init {
        require(metrics.isNotEmpty()) { "Mode-aware analytics must expose at least one metric." }
    }
}

object ModeAwareAnalyticsCalculator {
    fun buildProfile(
        snapshot: StudyAnalyticsSnapshot,
        contentMode: StudyContentMode,
    ): ModeAwareAnalyticsProfile = when (contentMode) {
        StudyContentMode.PROGRAMMING_COURSE -> programmingProfile(snapshot)
        StudyContentMode.LANGUAGE_DECK -> languageProfile(snapshot)
        StudyContentMode.READING_STUDY -> readingProfile(snapshot)
        StudyContentMode.GENERAL_SKILL -> generalSkillProfile(snapshot)
        StudyContentMode.EXAM_PLAN -> examProfile(snapshot)
    }

    private fun examProfile(snapshot: StudyAnalyticsSnapshot): ModeAwareAnalyticsProfile = ModeAwareAnalyticsProfile(
        contentMode = StudyContentMode.EXAM_PLAN,
        weakAreaLabel = ModeAwareWeakAreaLabel.WEAK_AREAS,
        metrics = listOf(
            metric(ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY, percent(snapshot.consistencyScorePercent), 0),
            metric(ModeAwareAnalyticsMetricKey.SCHEDULE_ADHERENCE, percent(snapshot.scheduleAdherencePercent), 1),
            metric(ModeAwareAnalyticsMetricKey.QUESTION_ACCURACY, percent(snapshot.questionAccuracyPercent), 2),
            metric(ModeAwareAnalyticsMetricKey.GOAL_COMPLETION, percent(snapshot.goalCompletionPercent), 3),
            metric(ModeAwareAnalyticsMetricKey.CONTENT_PROGRESS, percent(snapshot.contentProgressPercent), 4),
            metric(ModeAwareAnalyticsMetricKey.LIBRARY_PROGRESS, percent(snapshot.libraryProgressPercent), 5),
        ),
    )

    private fun programmingProfile(
        snapshot: StudyAnalyticsSnapshot,
    ): ModeAwareAnalyticsProfile = ModeAwareAnalyticsProfile(
        contentMode = StudyContentMode.PROGRAMMING_COURSE,
        weakAreaLabel = ModeAwareWeakAreaLabel.WEAK_CONCEPTS,
        metrics = listOf(
            metric(ModeAwareAnalyticsMetricKey.MODULES_COMPLETED, percent(snapshot.contentProgressPercent), 0),
            metric(ModeAwareAnalyticsMetricKey.EXERCISES_COMPLETED, count(snapshot.answeredQuestions), 1),
            metric(ModeAwareAnalyticsMetricKey.DEBUGGING_ACCURACY, percent(snapshot.questionAccuracyPercent), 2),
            metric(ModeAwareAnalyticsMetricKey.PROJECT_PROGRESS, percent(snapshot.goalCompletionPercent), 3),
            metric(ModeAwareAnalyticsMetricKey.WEAK_CONCEPTS, count(snapshot.weakAreas.size), 4),
            metric(ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY, percent(snapshot.consistencyScorePercent), 5),
        ),
    )

    private fun languageProfile(
        snapshot: StudyAnalyticsSnapshot,
    ): ModeAwareAnalyticsProfile = ModeAwareAnalyticsProfile(
        contentMode = StudyContentMode.LANGUAGE_DECK,
        weakAreaLabel = ModeAwareWeakAreaLabel.LANGUAGE_MISTAKE_PATTERNS,
        metrics = listOf(
            metric(ModeAwareAnalyticsMetricKey.IDIOMS_LEARNED, percent(snapshot.contentProgressPercent), 0),
            metric(ModeAwareAnalyticsMetricKey.IDIOMS_DUE, count(snapshot.dueReviewCount), 1),
            metric(ModeAwareAnalyticsMetricKey.USAGE_ACCURACY, percent(snapshot.questionAccuracyPercent), 2),
            metric(ModeAwareAnalyticsMetricKey.MISTAKE_PATTERNS, count(snapshot.weakAreas.size), 3),
            metric(ModeAwareAnalyticsMetricKey.REGISTER_COVERAGE, percent(snapshot.libraryProgressPercent), 4),
            metric(ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY, percent(snapshot.consistencyScorePercent), 5),
        ),
    )

    private fun readingProfile(snapshot: StudyAnalyticsSnapshot): ModeAwareAnalyticsProfile = ModeAwareAnalyticsProfile(
        contentMode = StudyContentMode.READING_STUDY,
        weakAreaLabel = ModeAwareWeakAreaLabel.READING_GAPS,
        metrics = listOf(
            metric(ModeAwareAnalyticsMetricKey.READING_PROGRESS, percent(snapshot.contentProgressPercent), 0),
            metric(ModeAwareAnalyticsMetricKey.LIBRARY_PROGRESS, percent(snapshot.libraryProgressPercent), 1),
            metric(ModeAwareAnalyticsMetricKey.GOAL_COMPLETION, percent(snapshot.goalCompletionPercent), 2),
            metric(ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY, percent(snapshot.consistencyScorePercent), 3),
        ),
    )

    private fun generalSkillProfile(
        snapshot: StudyAnalyticsSnapshot,
    ): ModeAwareAnalyticsProfile = ModeAwareAnalyticsProfile(
        contentMode = StudyContentMode.GENERAL_SKILL,
        weakAreaLabel = ModeAwareWeakAreaLabel.SKILL_GAPS,
        metrics = listOf(
            metric(ModeAwareAnalyticsMetricKey.SKILL_PROGRESS, percent(snapshot.contentProgressPercent), 0),
            metric(ModeAwareAnalyticsMetricKey.GOAL_COMPLETION, percent(snapshot.goalCompletionPercent), 1),
            metric(ModeAwareAnalyticsMetricKey.STUDY_CONSISTENCY, percent(snapshot.consistencyScorePercent), 2),
            metric(ModeAwareAnalyticsMetricKey.LIBRARY_PROGRESS, percent(snapshot.libraryProgressPercent), 3),
        ),
    )

    private fun metric(
        key: ModeAwareAnalyticsMetricKey,
        value: String,
        priority: Int,
    ): ModeAwareAnalyticsMetric = ModeAwareAnalyticsMetric(
        key = key,
        value = value,
        priority = priority,
    )

    private fun percent(value: Int): String = "${value.coerceIn(0, 100)}%"

    private fun count(value: Int): String = value.coerceAtLeast(0).toString()
}
