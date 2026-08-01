package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
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

internal class RoomStudyDiscoveryRepository(
    database: StudyFlowDatabase,
) : StudyDiscoveryRepository {
    private val dao = database.studyDiscoveryDao()

    override suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType>,
        includeArchived: Boolean,
    ): List<GlobalSearchResult> = buildList {
        if (GlobalSearchContentType.PLAN in contentTypes) {
            addAll(dao.searchPlans(query, includeArchived).toResults(query, GlobalSearchContentType.PLAN))
        }
        if (GlobalSearchContentType.SUBJECT in contentTypes) {
            addAll(dao.searchSubjects(query, includeArchived).toResults(query, GlobalSearchContentType.SUBJECT))
        }
        if (GlobalSearchContentType.TOPIC in contentTypes) {
            addAll(dao.searchTopics(query, includeArchived).toResults(query, GlobalSearchContentType.TOPIC))
        }
        if (GlobalSearchContentType.QUESTION in contentTypes) {
            addAll(dao.searchQuestions(query, includeArchived).toResults(query, GlobalSearchContentType.QUESTION))
        }
        if (GlobalSearchContentType.NOTE in contentTypes) {
            addAll(dao.searchNotes(query, includeArchived).toResults(query, GlobalSearchContentType.NOTE))
        }
        if (GlobalSearchContentType.MATERIAL in contentTypes) {
            addAll(dao.searchMaterials(query, includeArchived).toResults(query, GlobalSearchContentType.MATERIAL))
        }
        if (GlobalSearchContentType.FLASHCARD in contentTypes) {
            addAll(dao.searchFlashcards(query, includeArchived).toResults(query, GlobalSearchContentType.FLASHCARD))
        }
        if (GlobalSearchContentType.ERROR in contentTypes) {
            addAll(dao.searchErrors(query, includeArchived).toResults(query, GlobalSearchContentType.ERROR))
        }
    }.sortedWith(
        compareByDescending<GlobalSearchResult> { it.score }
            .thenByDescending { it.updatedAtEpochMillis }
            .thenBy { it.type.ordinal }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id },
    ).take(MaximumSearchResults)

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
        val input = StudyRecommendationInput(
            studyPlanId = studyPlanId,
            questionAccuracyPercent = plan.averageAccuracyPercent.coerceIn(0, 100),
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
