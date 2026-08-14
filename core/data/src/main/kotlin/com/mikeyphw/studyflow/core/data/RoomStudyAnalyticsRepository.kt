package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.GoalEvidence
import com.mikeyphw.studyflow.core.domain.GoalProgressCalculator
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsCalculator
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsRepository
import com.mikeyphw.studyflow.core.domain.StudyAnalyticsSnapshot
import com.mikeyphw.studyflow.core.domain.StudyGoalProgressEngine
import com.mikeyphw.studyflow.core.domain.WeakStudyArea
import com.mikeyphw.studyflow.core.domain.tickingClockFlow
import com.mikeyphw.studyflow.core.model.StudyPlanId
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn

internal class RoomStudyAnalyticsRepository(
    database: StudyFlowDatabase,
    private val sharingScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StudyAnalyticsRepository {
    private val analyticsDao = database.studyAnalyticsDao()
    private val metadataDao = database.databaseMetadataDao()
    private val planningDao = database.studyPlanningDao()
    private val practiceDao = database.studyPracticeDao()
    private val structureDao = database.studyStructureDao()
    private val sharedSnapshots = ConcurrentHashMap<String, Flow<StudyAnalyticsSnapshot>>()

    override fun observeSnapshot(planId: StudyPlanId): Flow<StudyAnalyticsSnapshot> = sharedSnapshots.computeIfAbsent(
        planId.value,
    ) {
        val graphRevision = metadataDao.observeValue(StudyFlowIntegrity.GraphRevisionMetadataKey)
            .map { value -> value?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L }
            .distinctUntilChanged()
        combine(graphRevision, tickingClockFlow(clock)) { _, now -> now }
            .mapLatest { now -> buildSnapshot(planId, loadInputs(planId, now), now) }
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = SharedFlowStopTimeoutMillis),
                replay = 1,
            )
    }

    private suspend fun loadInputs(planId: StudyPlanId, now: Long): AnalyticsInputs {
        val sevenDayWindowStart = localSevenDayWindowStart(now)
        val plan = planId.value
        return AnalyticsInputs(
            aggregate = analyticsDao.snapshot(
                planId = plan,
                sevenDayWindowStart = sevenDayWindowStart,
                nowEpochMillis = now,
                overdueCutoffEpochMillis = now - DayMillis,
            ),
            sessions = planningDao.findStudySessions(plan),
            tests = practiceDao.findAllTests(plan),
            mockExams = practiceDao.findAllMockExams(plan),
            topics = structureDao.findTopics(plan),
            goals = planningDao.findStudyGoals(plan),
            sevenDayWindowStart = sevenDayWindowStart,
        )
    }

    private fun buildSnapshot(
        planId: StudyPlanId,
        inputs: AnalyticsInputs,
        now: Long,
    ): StudyAnalyticsSnapshot {
        val aggregate = inputs.aggregate
        val activeDays = inputs.sessions.asSequence()
            .filter { session -> session.status == "COMPLETED" && session.startedAtEpochMillis >= inputs
                .sevenDayWindowStart }
            .map { session -> Instant.ofEpochMilli(session.startedAtEpochMillis).atZone(zoneId).toLocalDate() }
            .distinct()
            .count()
        val goalEvidence = GoalEvidence(
            sessions = inputs.sessions.map(StudySessionEntity::asExternalModel),
            tests = inputs.tests.map(StudyTestEntity::asExternalModel),
            mockExams = inputs.mockExams.map(MockExamEntity::asExternalModel),
            topics = inputs.topics.map(TopicEntity::asExternalModel),
        )
        val evaluatedGoals = inputs.goals.map { goal ->
            StudyGoalProgressEngine.evaluate(goal.asExternalModel(), goalEvidence, now, zoneId)
        }
        val goalPercent = GoalProgressCalculator.overallGoalPercent(evaluatedGoals)
        val activeTopics = inputs.topics.filterNot(TopicEntity::archived)
        val weakAreas = activeTopics
            .filter { topic -> topic.masteryPercent < WeakMasteryThreshold || topic
                .progressPercent < WeakProgressThreshold }
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
        val libraryItemCount = aggregate.materialCount + aggregate.videoCount
        val libraryProgress = if (libraryItemCount == 0) 0 else {
            (aggregate.materialProgressScoreSum + aggregate.completedVideoCount * 100) / libraryItemCount
        }
        val snapshot = StudyAnalyticsSnapshot(
            studyPlanId = planId,
            studiedMinutesLastSevenDays = aggregate.studiedMinutesLastSevenDays,
            completedSessionsLastSevenDays = aggregate.completedSessionsLastSevenDays,
            scheduleAdherencePercent = StudyAnalyticsCalculator.percent(
                aggregate.completedPlannedCount,
                aggregate.elapsedPlannedCount,
            ),
            questionAccuracyPercent = StudyAnalyticsCalculator.percent(
                aggregate.correctAnswers,
                aggregate.answeredQuestions,
            ),
            answeredQuestions = aggregate.answeredQuestions,
            dueReviewCount = aggregate.dueReviewCount,
            overdueReviewCount = aggregate.overdueReviewCount,
            retentionEaseAverage = aggregate.retentionEaseAverage,
            goalCompletionPercent = goalPercent,
            contentProgressPercent = aggregate.contentProgressAverage.toInt(),
            libraryProgressPercent = libraryProgress,
            consistencyScorePercent = StudyAnalyticsCalculator.consistencyScore(activeDays),
            weakAreas = weakAreas,
            insights = emptyList(),
        )
        return snapshot.copy(insights = StudyAnalyticsCalculator.buildInsights(snapshot))
    }

    private fun localSevenDayWindowStart(now: Long): Long = Instant.ofEpochMilli(now)
        .atZone(zoneId)
        .toLocalDate()
        .minusDays(6)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private data class AnalyticsInputs(
        val aggregate: com.mikeyphw.studyflow.core.database.dao.StudyAnalyticsAggregateRow,
        val sessions: List<StudySessionEntity>,
        val tests: List<StudyTestEntity>,
        val mockExams: List<MockExamEntity>,
        val topics: List<TopicEntity>,
        val goals: List<StudyGoalEntity>,
        val sevenDayWindowStart: Long,
    )

    private companion object {
        const val DayMillis = 86_400_000L
        const val WeakMasteryThreshold = 65
        const val WeakProgressThreshold = 50
        const val SharedFlowStopTimeoutMillis = 5_000L
    }
}
