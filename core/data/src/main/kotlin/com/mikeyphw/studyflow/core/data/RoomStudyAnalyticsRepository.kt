package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudyMaterialEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.database.entity.VideoResourceEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.GoalEvidence
import com.mikeyphw.studyflow.core.domain.GoalProgressCalculator
import com.mikeyphw.studyflow.core.domain.StudyGoalProgressEngine
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsCalculator
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.WeakStudyArea
import com.mikeyphw.studyflow.core.domain.tickingClockFlow
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.combine

internal class RoomStudyAnalyticsRepository(
    database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StudyAnalyticsRepository {
    private val planningDao = database.studyPlanningDao()
    private val practiceDao = database.studyPracticeDao()
    private val retentionDao = database.studyRetentionDao()
    private val structureDao = database.studyStructureDao()
    private val libraryDao = database.studyLibraryDao()

    override fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot> {
        val plan = planId.value
        val attempts = combine(
            practiceDao.observeTests(plan),
            practiceDao.observeMockExams(plan),
        ) { tests, mockExams -> tests to mockExams }
        val execution = combine(
            planningDao.observeStudySessions(plan),
            planningDao.observePlannedActivities(plan),
            attempts,
            retentionDao.observeReviewItems(plan),
        ) { sessions, activities, practiceAttempts, reviews ->
            ExecutionInputs(
                sessions = sessions,
                activities = activities,
                tests = practiceAttempts.first,
                mockExams = practiceAttempts.second,
                reviews = reviews,
            )
        }
        val content = combine(
            structureDao.observeTopics(plan),
            libraryDao.observeMaterials(plan),
            libraryDao.observeVideos(plan),
            planningDao.observeStudyGoals(plan),
        ) { topics, materials, videos, goals ->
            ContentInputs(topics, materials, videos, goals)
        }
        val inputs = combine(execution, content) { executionInputs, contentInputs -> executionInputs to contentInputs }
        return combine(inputs, tickingClockFlow(clock)) { (executionInputs, contentInputs), now ->
            buildSnapshot(planId, executionInputs, contentInputs, now)
        }
    }

    private fun buildSnapshot(
        planId: StudyPlanId,
        execution: ExecutionInputs,
        content: ContentInputs,
        now: Long,
    ): StudyAnalyticsSnapshot {
        val sevenDayLocalWindowStart = Instant.ofEpochMilli(now)
            .atZone(zoneId)
            .toLocalDate()
            .minusDays(6)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val recentCompletedSessions = execution.sessions.filter {
            it.status == "COMPLETED" && it.startedAtEpochMillis >= sevenDayLocalWindowStart
        }
        val studiedMinutes = recentCompletedSessions.sumOf { session ->
            ((session.grossDurationSeconds - session.pauseDurationSeconds).coerceAtLeast(0L) / 60L).toInt()
        }
        val activeDays = recentCompletedSessions.map { session ->
            Instant.ofEpochMilli(session.startedAtEpochMillis).atZone(zoneId).toLocalDate()
        }.distinct().size
        val completedPlanned = execution.activities.count { it.status == "COMPLETED" }
        val elapsedPlanned = execution.activities.count { activity ->
            val start = activity.scheduledStartEpochMillis
            start != null && start <= now && activity.status != "CANCELLED"
        }
        val submittedTests = execution.tests.filter { test -> test.submittedAtEpochMillis != null }
        val completedEvidenceSessions = execution.sessions.filter { session -> session.status == "COMPLETED" }
        val testsAnswered = submittedTests.sumOf(StudyTestEntity::answeredCount)
        val testsCorrect = submittedTests.sumOf(StudyTestEntity::correctCount)
        val sessionAnswered = completedEvidenceSessions.sumOf(StudySessionEntity::questionsAnswered)
        val sessionCorrect = completedEvidenceSessions.sumOf(StudySessionEntity::correctAnswers)
        val answered = testsAnswered + sessionAnswered
        val correct = testsCorrect + sessionCorrect
        val dueReviews = execution.reviews.count { review ->
            review.status in setOf("SCHEDULED", "DUE") && review.dueAtEpochMillis <= now
        }
        val overdueReviews = execution.reviews.count { review ->
            review.status in setOf("SCHEDULED", "DUE") && review.dueAtEpochMillis < now - DayMillis
        }
        val completedReviews = execution.reviews.filter { it.status == "COMPLETED" }
        val easeAverage = completedReviews.map(ReviewItemEntity::easeFactor).average().takeIf { !it.isNaN() } ?: 0.0
        val goalEvidence = GoalEvidence(
            sessions = execution.sessions.map { session -> session.asExternalModel() },
            tests = execution.tests.map { test -> test.asExternalModel() },
            mockExams = execution.mockExams.map { mock -> mock.asExternalModel() },
            topics = content.topics.map { topic -> topic.asExternalModel() },
        )
        val evaluatedGoals = content.goals.map { goal ->
            StudyGoalProgressEngine.evaluate(goal.asExternalModel(), goalEvidence, now, zoneId)
        }
        val goalPercent = GoalProgressCalculator.overallGoalPercent(evaluatedGoals)
        val activeTopics = content.topics.filterNot(TopicEntity::archived)
        val contentProgress = activeTopics
            .map(TopicEntity::progressPercent)
            .average()
            .takeIf { !it.isNaN() }
            ?.toInt() ?: 0
        val weakAreas = activeTopics
            .filter { topic -> topic.masteryPercent < WeakMasteryThreshold || topic.progressPercent < WeakProgressThreshold }
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
        val mockExams: List<MockExamEntity>,
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
        const val WeakMasteryThreshold = 65
        const val WeakProgressThreshold = 50
    }
}
