package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.PracticeAnalytics
import com.mikeyphw.studyflow.core.domain.PracticeDeckRequest
import com.mikeyphw.studyflow.core.domain.PracticeSummary
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.StudyInsight
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.WeakStudyArea
import com.mikeyphw.studyflow.core.model.AppLanguage
import com.mikeyphw.studyflow.core.model.AppPreferences
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.ThemeMode

private const val MinuteMillis: Long = 60_000L

data class DesktopPlannerScreenState(
    val status: DesktopScreenStatus,
    val planName: String,
    val plannedActivities: List<DesktopPlannedActivityRow>,
    val activeSession: DesktopSessionRow?,
    val recentSessions: List<DesktopSessionRow>,
    val nextActivityTitle: String,
    val completedCount: Int,
    val missedCount: Int,
    val totalPlannedMinutes: Int,
)

data class DesktopPlannedActivityRow(
    val id: PlannedActivityId,
    val title: String,
    val typeLabel: String,
    val timeLabel: String,
    val durationLabel: String,
    val statusLabel: String,
    val locked: Boolean,
) {
    companion object {
        fun fromActivity(activity: PlannedActivity): DesktopPlannedActivityRow = DesktopPlannedActivityRow(
            id = activity.id,
            title = activity.title,
            typeLabel = activity.type.label,
            timeLabel = activity.scheduledStartEpochMillis?.let(::formatRelativeStudyTime) ?: "Backlog",
            durationLabel = "${activity.plannedDurationMinutes} min",
            statusLabel = activity.status.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            locked = activity.locked,
        )
    }
}

data class DesktopSessionRow(
    val title: String,
    val statusLabel: String,
    val durationLabel: String,
    val scoreLabel: String,
) {
    companion object {
        fun fromSession(session: StudySession): DesktopSessionRow = DesktopSessionRow(
            title = session.notes.ifBlank { session.type.label },
            statusLabel = session.status.name.lowercase().replaceFirstChar(Char::uppercase),
            durationLabel = "${(session.grossDurationSeconds / 60L).coerceAtLeast(0L)} min",
            scoreLabel = if (session.questionsAnswered > 0) {
                "${PracticeAnalytics.accuracyPercent(session.correctAnswers, session.questionsAnswered)}% accuracy"
            } else {
                "No questions logged"
            },
        )
    }
}

data class DesktopPracticeScreenState(
    val status: DesktopScreenStatus,
    val summary: DesktopPracticeSummary,
    val deck: List<DesktopQuestionRow>,
    val tests: List<DesktopPracticeAttemptRow>,
    val currentQuestion: DesktopQuestionRow?,
)

data class DesktopPracticeSummary(
    val questionCount: Int,
    val favoriteCount: Int,
    val disputedCount: Int,
    val testCount: Int,
    val mockExamCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracyPercent: Int,
) {
    companion object {
        fun fromSummary(summary: PracticeSummary): DesktopPracticeSummary = DesktopPracticeSummary(
            questionCount = summary.questionCount,
            favoriteCount = summary.favoriteCount,
            disputedCount = summary.disputedCount,
            testCount = summary.testCount,
            mockExamCount = summary.mockExamCount,
            answeredCount = summary.answeredCount,
            correctCount = summary.correctCount,
            accuracyPercent = summary.accuracyPercent,
        )
    }
}

data class DesktopQuestionRow(
    val id: QuestionId,
    val statement: String,
    val answer: String,
    val explanation: String,
    val difficultyLabel: String,
    val tags: List<String>,
    val favorite: Boolean,
    val disputed: Boolean,
) {
    companion object {
        fun fromQuestion(question: Question): DesktopQuestionRow = DesktopQuestionRow(
            id = question.id,
            statement = question.statement,
            answer = question.correctAnswer,
            explanation = question.explanation,
            difficultyLabel = "Difficulty ${question.difficulty}/5",
            tags = question.tags,
            favorite = question.favorite,
            disputed = question.disputed,
        )
    }
}

data class DesktopPracticeAttemptRow(
    val title: String,
    val modeLabel: String,
    val progressLabel: String,
    val scoreLabel: String,
) {
    companion object {
        fun fromTest(test: StudyTest): DesktopPracticeAttemptRow = DesktopPracticeAttemptRow(
            title = test.title,
            modeLabel = test.mode.name.lowercase().replaceFirstChar(Char::uppercase),
            progressLabel = "${test.answeredCount}/${test.questionCount} answered",
            scoreLabel = if (test.answeredCount > 0) {
                "${PracticeAnalytics.accuracyPercent(test.correctCount, test.answeredCount)}% correct"
            } else {
                "Not started"
            },
        )
    }
}

data class DesktopAnalyticsScreenState(
    val status: DesktopScreenStatus,
    val studiedHoursSevenDays: Double,
    val completedSessions: Int,
    val scheduleAdherencePercent: Int,
    val questionAccuracyPercent: Int,
    val answeredQuestions: Int,
    val libraryProgressPercent: Int,
    val consistencyScorePercent: Int,
    val dueReviewCount: Int,
    val overdueReviewCount: Int,
    val weakAreas: List<WeakStudyArea>,
    val insights: List<StudyInsight>,
) {
    companion object {
        fun fromSnapshot(snapshot: StudyAnalyticsSnapshot): DesktopAnalyticsScreenState = DesktopAnalyticsScreenState(
            status = DesktopScreenStatus.CONTENT,
            studiedHoursSevenDays = (snapshot.studiedMinutesLastSevenDays.toDouble() / 60.0),
            completedSessions = snapshot.completedSessionsLastSevenDays,
            scheduleAdherencePercent = snapshot.scheduleAdherencePercent,
            questionAccuracyPercent = snapshot.questionAccuracyPercent,
            answeredQuestions = snapshot.answeredQuestions,
            libraryProgressPercent = snapshot.libraryProgressPercent,
            consistencyScorePercent = snapshot.consistencyScorePercent,
            dueReviewCount = snapshot.dueReviewCount,
            overdueReviewCount = snapshot.overdueReviewCount,
            weakAreas = snapshot.weakAreas,
            insights = snapshot.insights,
        )
    }
}

data class DesktopSearchScreenState(
    val status: DesktopScreenStatus,
    val query: String,
    val results: List<DesktopSearchResultRow>,
    val recommendations: List<DesktopRecommendationRow>,
    val recentSearches: List<String>,
)

data class DesktopSearchResultRow(
    val title: String,
    val subtitle: String,
    val typeLabel: String,
    val score: Int,
) {
    companion object {
        fun fromResult(result: GlobalSearchResult): DesktopSearchResultRow = DesktopSearchResultRow(
            title = result.title,
            subtitle = "${result.planName} • ${result.subtitle}",
            typeLabel = result.type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
            score = result.score,
        )
    }
}

data class DesktopRecommendationRow(
    val title: String,
    val destinationLabel: String,
    val reasonLabel: String,
    val score: Int,
) {
    companion object {
        fun fromRecommendation(
            recommendation: StudyRecommendation,
        ): DesktopRecommendationRow = DesktopRecommendationRow(
            title = recommendation.targetLabel.ifBlank { recommendation.type.name.replace('_', ' ') },
            destinationLabel = recommendation.destination.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase),
            reasonLabel = recommendation.reasons.firstOrNull()?.type?.name
                ?.lowercase()
                ?.replace('_', ' ')
                ?.replaceFirstChar(Char::uppercase)
                ?: recommendation.presentation.name.lowercase().replace('_', ' '),
            score = recommendation.score,
        )
    }
}

data class DesktopSettingsScreenState(
    val status: DesktopScreenStatus,
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val dynamicColorEnabled: Boolean,
    val defaultPlanLabel: String,
    val recentSearches: List<String>,
    val boundaryModules: List<String>,
    val deferredDesktopIntegrations: List<String>,
) {
    companion object {
        fun fromPreferences(
            preferences: AppPreferences,
            defaultPlanLabel: String,
        ): DesktopSettingsScreenState = DesktopSettingsScreenState(
            status = DesktopScreenStatus.CONTENT,
            themeMode = preferences.themeMode,
            language = preferences.language,
            dynamicColorEnabled = preferences.dynamicColorEnabled,
            defaultPlanLabel = defaultPlanLabel,
            recentSearches = preferences.recentSearches,
            boundaryModules = StudyFlowDesktopBoundary.allowedSharedModules,
            deferredDesktopIntegrations = listOf(
                "Native file pickers",
                "Persistent preferences store",
                "Desktop notifications",
                "Tray/menu shortcuts",
            ),
        )
    }
}

val StudyActivityType.label: String
    get() = name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)

fun buildDesktopPracticeDeck(questions: List<Question>): List<Question> = PracticeAnalytics.buildDeck(
    questions = questions,
    request = PracticeDeckRequest(disputedOnly = false, minimumDifficulty = 2, limit = 8),
)

private fun formatRelativeStudyTime(epochMillis: Long): String {
    val minutes = epochMillis / MinuteMillis
    val hour = ((minutes / 60L) % 24L).toString().padStart(2, '0')
    val minute = (minutes % 60L).toString().padStart(2, '0')
    return "${hour}:${minute}"
}
