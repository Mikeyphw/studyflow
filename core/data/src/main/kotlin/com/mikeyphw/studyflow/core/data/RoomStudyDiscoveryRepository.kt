package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.database.dao.DiscoverySearchRow
import com.mikeyphw.studyflow.core.domain.ActivityRecommendationInput
import com.mikeyphw.studyflow.core.domain.GlobalSearchContentType
import com.mikeyphw.studyflow.core.domain.GlobalSearchRanker
import com.mikeyphw.studyflow.core.domain.GlobalSearchResult
import com.mikeyphw.studyflow.core.domain.GoalDeadlineInput
import com.mikeyphw.studyflow.core.domain.ResourceRecommendationInput
import com.mikeyphw.studyflow.core.domain.StudyDiscoveryRepository
import com.mikeyphw.studyflow.core.domain.StudyRecommendation
import com.mikeyphw.studyflow.core.domain.StudyRecommendationEngine
import com.mikeyphw.studyflow.core.domain.StudyRecommendationInput
import com.mikeyphw.studyflow.core.domain.TopicRecommendationInput
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyDiscoveryRepository(
    database: StudyFlowDatabase,
) : StudyDiscoveryRepository {
    private val dao = database.studyDiscoveryDao()
    private val metadataDao = database.databaseMetadataDao()

    override fun observeRevision(): Flow<Long> =
        metadataDao.observeValue(StudyFlowIntegrity.GraphRevisionMetadataKey)
            .map { value -> value?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L }

    override suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType>,
        includeArchived: Boolean,
    ): List<GlobalSearchResult> = contentTypes
        .flatMap { type -> candidateRows(type, includeArchived).toResults(query, type) }
        .filter { result -> result.score > 0 }
        .sortedWith(
            compareByDescending<GlobalSearchResult> { it.score }
                .thenByDescending { it.updatedAtEpochMillis }
                .thenBy { it.type.ordinal }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id },
        )
        .take(MaximumSearchResults)

    override suspend fun resolve(
        result: GlobalSearchResult,
        includeArchived: Boolean,
    ): GlobalSearchResult? = candidateRows(result.type, includeArchived)
        .firstOrNull { row -> row.id == result.id && row.planId == result.studyPlanId.value }
        ?.let { row ->
            GlobalSearchResult(
                id = row.id,
                studyPlanId = StudyPlanId(row.planId),
                planName = row.planName,
                type = result.type,
                title = row.title,
                subtitle = row.subtitle,
                score = result.score,
                updatedAtEpochMillis = row.updatedAtEpochMillis,
            )
        }

    private suspend fun candidateRows(
        type: GlobalSearchContentType,
        includeArchived: Boolean,
    ): List<DiscoverySearchRow> = when (type) {
        GlobalSearchContentType.PLAN -> dao.searchPlans("", includeArchived)
        GlobalSearchContentType.SUBJECT -> dao.searchSubjects("", includeArchived)
        GlobalSearchContentType.TOPIC -> dao.searchTopics("", includeArchived)
        GlobalSearchContentType.QUESTION -> dao.searchQuestions("", includeArchived)
        GlobalSearchContentType.NOTE -> dao.searchNotes("", includeArchived)
        GlobalSearchContentType.MATERIAL -> dao.searchMaterials("", includeArchived)
        GlobalSearchContentType.FLASHCARD -> dao.searchFlashcards("", includeArchived)
        GlobalSearchContentType.ERROR -> dao.searchErrors("", includeArchived)
    }

    override suspend fun recommendations(
        studyPlanId: StudyPlanId,
        nowEpochMillis: Long,
    ): List<StudyRecommendation> {
        val plan = dao.findPlan(studyPlanId.value) ?: return emptyList()
        if (plan.status in setOf("ARCHIVED", "TRASHED", "COMPLETED")) return emptyList()
        val adherence = dao.findScheduleAdherence(studyPlanId.value, nowEpochMillis)
        val adherencePercent = if (adherence.elapsedCount == 0) {
            100
        } else {
            (adherence.completedCount * 100 / adherence.elapsedCount).coerceIn(0, 100)
        }
        val practiceAccuracy = dao.findPracticeAccuracy(studyPlanId.value)
        val liveAccuracyPercent = if (practiceAccuracy.answeredCount == 0) {
            100
        } else {
            (practiceAccuracy.correctCount * 100 / practiceAccuracy.answeredCount).coerceIn(0, 100)
        }
        val input = StudyRecommendationInput(
            studyPlanId = studyPlanId,
            questionAccuracyPercent = liveAccuracyPercent,
            dueReviewCount = dao.countDueReviews(studyPlanId.value, nowEpochMillis),
            scheduleAdherencePercent = adherencePercent,
            topics = dao.findTopicRecommendations(studyPlanId.value).map { row ->
                TopicRecommendationInput(
                    id = row.topicId,
                    name = row.topicName,
                    masteryPercent = row.masteryPercent,
                    priority = row.priority,
                    questionCount = row.questionCount,
                    errorFrequency = row.errorFrequency,
                )
            },
            nextActivity = dao.findNextActivity(studyPlanId.value)?.let { row ->
                ActivityRecommendationInput(
                    id = row.id,
                    title = row.title,
                    scheduledStartEpochMillis = row.scheduledStartEpochMillis,
                    missed = row.status == "MISSED" ||
                        (row.scheduledStartEpochMillis?.let { it < nowEpochMillis } == true),
                )
            },
            overdueResource = dao.findOverdueResource(studyPlanId.value, nowEpochMillis)?.let { row ->
                ResourceRecommendationInput(
                    id = row.id,
                    title = row.title,
                    completedUnits = row.completedUnits,
                    totalUnits = row.totalUnits,
                    scheduledStartEpochMillis = row.scheduledStartEpochMillis,
                )
            },
            nearestGoal = dao.findNearestGoal(studyPlanId.value)?.let { row ->
                GoalDeadlineInput(row.title, row.endsOnIso)
            },
        )
        return StudyRecommendationEngine.recommend(input)
    }

    private fun List<DiscoverySearchRow>.toResults(
        query: String,
        type: GlobalSearchContentType,
    ): List<GlobalSearchResult> = map { row ->
        GlobalSearchResult(
            id = row.id,
            studyPlanId = StudyPlanId(row.planId),
            planName = row.planName,
            type = type,
            title = row.title,
            subtitle = row.subtitle,
            score = GlobalSearchRanker.rank(query, row.title, row.subtitle),
            updatedAtEpochMillis = row.updatedAtEpochMillis,
        )
    }

    private companion object {
        const val MaximumSearchResults = 80
    }
}
