package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudyMaterialEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.database.entity.VideoResourceEntity
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsCalculator
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.WeakStudyArea
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class RoomStudyAnalyticsRepository(
    database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : StudyAnalyticsRepository {
    private val planningDao = database.studyPlanningDao()
    private val practiceDao = database.studyPracticeDao()
    private val retentionDao = database.studyRetentionDao()
    private val structureDao = database.studyStructureDao()
    private val libraryDao = database.studyLibraryDao()

    override fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot> {
        val plan = planId.value
        val execution = combine(
            planningDao.observeStudySessions(plan),
            planningDao.observePlannedActivities(plan),
            practiceDao.observeTests(plan),
            practiceDao.observeQuestions(plan),
            retentionDao.observeReviewItems(plan),
        ) { sessions, activities, tests, questions, reviews ->
            ExecutionInputs(sessions, activities, tests, questions, reviews)
        }
        val content = combine(
            structureDao.observeTopics(plan),
            libraryDao.observeMaterials(plan),
            libraryDao.observeVideos(plan),
            planningDao.observeStudyGoals(plan),
        ) { topics, materials, videos, goals ->
            ContentInputs(topics, materials, videos, goals)
        }
        return combine(execution, content) { executionInputs, contentInputs ->
            buildSnapshot(planId, executionInputs, contentInputs)
        }
    }

    private fun buildSnapshot(
        planId: StudyPlanId,
        execution: ExecutionInputs,
        content: ContentInputs,
    ): StudyAnalyticsSnapshot {
        val now = clock()
        val sevenDaysAgo = now - SevenDaysMillis
        val recentCompletedSessions = execution.sessions.filter {
            it.status == "COMPLETED" && it.startedAtEpochMillis >= sevenDaysAgo
        }
        val studiedMinutes = recentCompletedSessions.sumOf { session ->
            ((session.grossDurationSeconds - session.pauseDurationSeconds).coerceAtLeast(0L) / 60L).toInt()
        }
        val activeDays = recentCompletedSessions.map { it.startedAtEpochMillis / DayMillis }.distinct().size
        val completedPlanned = execution.activities.count { it.status == "COMPLETED" }
        val elapsedPlanned = execution.activities.count { activity ->
            val start = activity.scheduledStartEpochMillis
            start != null && start <= now && activity.status != "CANCELLED"
        }
        val testsAnswered = execution.tests.sumOf(StudyTestEntity::answeredCount)
        val testsCorrect = execution.tests.sumOf(StudyTestEntity::correctCount)
        val fallbackAnswered = execution.sessions.sumOf(StudySessionEntity::questionsAnswered)
        val fallbackCorrect = execution.sessions.sumOf(StudySessionEntity::correctAnswers)
        val answered = if (testsAnswered > 0) testsAnswered else fallbackAnswered
        val correct = if (testsAnswered > 0) testsCorrect else fallbackCorrect
        val dueReviews = execution.reviews.count { review ->
            review.status in setOf("SCHEDULED", "DUE") && review.dueAtEpochMillis <= now
        }
        val overdueReviews = execution.reviews.count { review ->
            review.status in setOf("SCHEDULED", "DUE") && review.dueAtEpochMillis < now - DayMillis
        }
        val completedReviews = execution.reviews.filter { it.status == "COMPLETED" }
        val easeAverage = completedReviews.map(ReviewItemEntity::easeFactor).average().takeIf { !it.isNaN() } ?: 0.0
        val activeGoals = content.goals.filterNot(StudyGoalEntity::archived)
        val goalPercent = if (activeGoals.isEmpty()) {
            0
        } else {
            activeGoals
                .map { StudyAnalyticsCalculator.goalCompletion(it.currentValue, it.targetValue) }
                .average()
                .toInt()
        }
        val activeTopics = content.topics.filterNot(TopicEntity::archived)
        val contentProgress = activeTopics
            .map(TopicEntity::progressPercent)
            .average()
            .takeIf { !it.isNaN() }
            ?.toInt() ?: 0
        val weakAreas = activeTopics
            .sortedWith(compareBy<TopicEntity> { it.masteryPercent }.thenByDescending { it.priority })
            .take(5)
            .map { topic ->
                WeakStudyArea(
                    label = topic.name,
                    progressPercent = topic.progressPercent,
                    masteryPercent = topic.masteryPercent,
                    priority = topic.priority,
                )
            }
        val materialScores = content.materials.filterNot(StudyMaterialEntity::archived).map { material ->
            val total = material.totalUnits ?: 0
            if (total > 0) StudyAnalyticsCalculator.percent(material.completedUnits, total) else 0
        }
        val videoScores = content.videos.map { video -> if (video.completed) 100 else 0 }
        val libraryScores = materialScores + videoScores
        val libraryProgress = libraryScores.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        val snapshot = StudyAnalyticsSnapshot(
            studyPlanId = planId,
            studiedMinutesLastSevenDays = studiedMinutes,
            completedSessionsLastSevenDays = recentCompletedSessions.size,
            scheduleAdherencePercent = StudyAnalyticsCalculator.percent(completedPlanned, elapsedPlanned),
            questionAccuracyPercent = StudyAnalyticsCalculator.percent(correct, answered),
            answeredQuestions = answered,
            dueReviewCount = dueReviews,
            overdueReviewCount = overdueReviews,
            retentionEaseAverage = easeAverage,
            goalCompletionPercent = goalPercent,
            contentProgressPercent = contentProgress,
            libraryProgressPercent = libraryProgress,
            consistencyScorePercent = StudyAnalyticsCalculator.consistencyScore(activeDays),
            weakAreas = weakAreas,
            insights = emptyList(),
        )
        return snapshot.copy(insights = StudyAnalyticsCalculator.buildInsights(snapshot))
    }

    private data class ExecutionInputs(
        val sessions: List<StudySessionEntity>,
        val activities: List<PlannedActivityEntity>,
        val tests: List<StudyTestEntity>,
        val questions: List<QuestionEntity>,
        val reviews: List<ReviewItemEntity>,
    )

    private data class ContentInputs(
        val topics: List<TopicEntity>,
        val materials: List<StudyMaterialEntity>,
        val videos: List<VideoResourceEntity>,
        val goals: List<StudyGoalEntity>,
    )

    private companion object {
        const val DayMillis = 86_400_000L
        const val SevenDaysMillis = 7L * DayMillis
    }
}
