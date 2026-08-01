package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId

enum class GlobalSearchContentType {
    PLAN,
    SUBJECT,
    TOPIC,
    QUESTION,
    NOTE,
    MATERIAL,
    FLASHCARD,
    ERROR,
}

enum class GlobalSearchDestination {
    DASHBOARD,
    SUBJECTS,
    PRACTICE,
    PROGRESS,
    LIBRARY,
    REVIEWS,
}

fun GlobalSearchContentType.destination(): GlobalSearchDestination = when (this) {
    GlobalSearchContentType.PLAN -> GlobalSearchDestination.DASHBOARD
    GlobalSearchContentType.SUBJECT,
    GlobalSearchContentType.TOPIC,
    -> GlobalSearchDestination.SUBJECTS
    GlobalSearchContentType.QUESTION -> GlobalSearchDestination.PRACTICE
    GlobalSearchContentType.NOTE -> GlobalSearchDestination.PROGRESS
    GlobalSearchContentType.MATERIAL -> GlobalSearchDestination.LIBRARY
    GlobalSearchContentType.FLASHCARD,
    GlobalSearchContentType.ERROR,
    -> GlobalSearchDestination.REVIEWS
}

data class GlobalSearchResult(
    val id: String,
    val studyPlanId: StudyPlanId,
    val planName: String,
    val type: GlobalSearchContentType,
    val title: String,
    val subtitle: String,
    val score: Int,
    val updatedAtEpochMillis: Long,
)

enum class StudyRecommendationType {
    NEXT_STUDY_ITEM,
    WEAK_TOPIC_REVIEW,
    PRACTICE_DECK,
    OVERDUE_RESOURCE,
}

enum class StudyRecommendationDestination {
    REVIEWS,
    SCHEDULE,
    SUBJECTS,
    PRACTICE,
    LIBRARY,
}

enum class StudyRecommendationPresentation {
    DUE_REVIEWS,
    PLANNED_ACTIVITY,
    WEAK_TOPIC,
    PRACTICE_TOPIC,
    OVERDUE_RESOURCE,
}

enum class StudyRecommendationReasonType {
    DUE_REVIEW_COUNT,
    SCHEDULE_ADHERENCE,
    GOAL_DEADLINE,
    MISSED_ACTIVITY,
    NEXT_ACTIVITY,
    MASTERY,
    RECURRING_ERRORS,
    QUESTION_ACCURACY,
    AVAILABLE_QUESTIONS,
    OVERDUE_STUDY_BLOCK,
    RESOURCE_PROGRESS,
    RESOURCE_UNFINISHED,
}

data class StudyRecommendationReason(
    val type: StudyRecommendationReasonType,
    val count: Int? = null,
    val percent: Int? = null,
    val title: String? = null,
    val dateIso: String? = null,
    val completedUnits: Int? = null,
    val totalUnits: Int? = null,
)

data class StudyRecommendation(
    val id: String,
    val studyPlanId: StudyPlanId,
    val type: StudyRecommendationType,
    val presentation: StudyRecommendationPresentation,
    val targetLabel: String,
    val reasons: List<StudyRecommendationReason>,
    val destination: StudyRecommendationDestination,
    val targetId: String?,
    val score: Int,
)

data class TopicRecommendationInput(
    val id: String,
    val name: String,
    val masteryPercent: Int,
    val priority: Int,
    val questionCount: Int,
    val errorFrequency: Int,
)

data class ActivityRecommendationInput(
    val id: String,
    val title: String,
    val scheduledStartEpochMillis: Long?,
    val missed: Boolean,
)

data class ResourceRecommendationInput(
    val id: String,
    val title: String,
    val completedUnits: Int,
    val totalUnits: Int?,
    val scheduledStartEpochMillis: Long,
)

data class GoalDeadlineInput(
    val title: String,
    val endsOnIso: String,
)

data class StudyRecommendationInput(
    val studyPlanId: StudyPlanId,
    val questionAccuracyPercent: Int,
    val dueReviewCount: Int,
    val scheduleAdherencePercent: Int,
    val topics: List<TopicRecommendationInput>,
    val nextActivity: ActivityRecommendationInput?,
    val overdueResource: ResourceRecommendationInput?,
    val nearestGoal: GoalDeadlineInput?,
)

interface StudyDiscoveryRepository {
    suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType> = GlobalSearchContentType.entries.toSet(),
        includeArchived: Boolean = false,
    ): List<GlobalSearchResult>

    suspend fun recommendations(
        studyPlanId: StudyPlanId,
        nowEpochMillis: Long,
    ): List<StudyRecommendation>
}

class ManageStudyDiscovery(
    private val repository: StudyDiscoveryRepository,
) {
    suspend fun search(
        query: String,
        contentTypes: Set<GlobalSearchContentType> = GlobalSearchContentType.entries.toSet(),
        includeArchived: Boolean = false,
    ): List<GlobalSearchResult> {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length < MinimumSearchLength || contentTypes.isEmpty()) return emptyList()
        return repository.search(normalized, contentTypes, includeArchived)
    }

    suspend fun recommendations(
        studyPlanId: StudyPlanId,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<StudyRecommendation> = repository.recommendations(studyPlanId, nowEpochMillis)

    private companion object {
        const val MinimumSearchLength = 2
    }
}

object GlobalSearchRanker {
    fun rank(query: String, title: String, subtitle: String): Int {
        val needle = query.trim().lowercase()
        val normalizedTitle = title.trim().lowercase()
        val normalizedSubtitle = subtitle.trim().lowercase()
        return when {
            normalizedTitle == needle -> 1_000
            normalizedTitle.startsWith(needle) -> 800
            normalizedTitle.contains(needle) -> 600
            normalizedSubtitle.contains(needle) -> 300
            else -> 0
        }
    }
}

object StudyRecommendationEngine {
    fun recommend(input: StudyRecommendationInput): List<StudyRecommendation> {
        val rankedTopics = input.topics.sortedWith(
            compareByDescending<TopicRecommendationInput>(::topicRiskScore)
                .thenBy { it.name.lowercase() }
                .thenBy { it.id },
        )
        val weakTopic = rankedTopics.firstOrNull(::isWeakTopic)
        val practiceTopic = rankedTopics.firstOrNull { topic ->
            topic.questionCount > 0 && (
                topic.masteryPercent < PracticeMasteryThreshold ||
                    topic.errorFrequency > 0 ||
                    input.questionAccuracyPercent < PracticeAccuracyThreshold
                )
        }
        return buildList {
            nextStudyRecommendation(input)?.let(::add)
            weakTopic?.let { add(weakTopicRecommendation(input, it)) }
            practiceTopic?.let { add(practiceRecommendation(input, it)) }
            input.overdueResource?.let { add(resourceRecommendation(input, it)) }
        }.sortedWith(compareByDescending<StudyRecommendation> { it.score }.thenBy { it.id })
    }

    private fun nextStudyRecommendation(input: StudyRecommendationInput): StudyRecommendation? {
        if (input.dueReviewCount > 0) {
            return StudyRecommendation(
                id = "next-review:${input.studyPlanId.value}",
                studyPlanId = input.studyPlanId,
                type = StudyRecommendationType.NEXT_STUDY_ITEM,
                presentation = StudyRecommendationPresentation.DUE_REVIEWS,
                targetLabel = "",
                reasons = listOfNotNull(
                    StudyRecommendationReason(
                        type = StudyRecommendationReasonType.DUE_REVIEW_COUNT,
                        count = input.dueReviewCount,
                    ),
                    StudyRecommendationReason(
                        type = StudyRecommendationReasonType.SCHEDULE_ADHERENCE,
                        percent = input.scheduleAdherencePercent.coerceIn(0, 100),
                    ),
                    input.nearestGoal?.asReason(),
                ),
                destination = StudyRecommendationDestination.REVIEWS,
                targetId = null,
                score = 950 + input.dueReviewCount.coerceAtMost(50),
            )
        }
        val activity = input.nextActivity ?: return null
        return StudyRecommendation(
            id = "next-activity:${activity.id}",
            studyPlanId = input.studyPlanId,
            type = StudyRecommendationType.NEXT_STUDY_ITEM,
            presentation = StudyRecommendationPresentation.PLANNED_ACTIVITY,
            targetLabel = activity.title,
            reasons = listOfNotNull(
                StudyRecommendationReason(
                    type = if (activity.missed) {
                        StudyRecommendationReasonType.MISSED_ACTIVITY
                    } else {
                        StudyRecommendationReasonType.NEXT_ACTIVITY
                    },
                ),
                StudyRecommendationReason(
                    type = StudyRecommendationReasonType.SCHEDULE_ADHERENCE,
                    percent = input.scheduleAdherencePercent.coerceIn(0, 100),
                ),
                input.nearestGoal?.asReason(),
            ),
            destination = StudyRecommendationDestination.SCHEDULE,
            targetId = activity.id,
            score = if (activity.missed) 925 else 850,
        )
    }

    private fun weakTopicRecommendation(
        input: StudyRecommendationInput,
        topic: TopicRecommendationInput,
    ): StudyRecommendation = StudyRecommendation(
        id = "weak-topic:${topic.id}",
        studyPlanId = input.studyPlanId,
        type = StudyRecommendationType.WEAK_TOPIC_REVIEW,
        presentation = StudyRecommendationPresentation.WEAK_TOPIC,
        targetLabel = topic.name,
        reasons = listOf(
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.MASTERY,
                percent = topic.masteryPercent.coerceIn(0, 100),
            ),
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.RECURRING_ERRORS,
                count = topic.errorFrequency.coerceAtLeast(0),
            ),
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.QUESTION_ACCURACY,
                percent = input.questionAccuracyPercent.coerceIn(0, 100),
            ),
        ),
        destination = StudyRecommendationDestination.SUBJECTS,
        targetId = topic.id,
        score = 700 + topicRiskScore(topic),
    )

    private fun practiceRecommendation(
        input: StudyRecommendationInput,
        topic: TopicRecommendationInput,
    ): StudyRecommendation = StudyRecommendation(
        id = "practice:${topic.id}",
        studyPlanId = input.studyPlanId,
        type = StudyRecommendationType.PRACTICE_DECK,
        presentation = StudyRecommendationPresentation.PRACTICE_TOPIC,
        targetLabel = topic.name,
        reasons = listOf(
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.AVAILABLE_QUESTIONS,
                count = topic.questionCount.coerceAtLeast(0),
            ),
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.RECURRING_ERRORS,
                count = topic.errorFrequency.coerceAtLeast(0),
            ),
            StudyRecommendationReason(
                type = StudyRecommendationReasonType.QUESTION_ACCURACY,
                percent = input.questionAccuracyPercent.coerceIn(0, 100),
            ),
        ),
        destination = StudyRecommendationDestination.PRACTICE,
        targetId = topic.id,
        score = 620 + topicRiskScore(topic) + topic.questionCount.coerceIn(0, 40),
    )

    private fun resourceRecommendation(
        input: StudyRecommendationInput,
        resource: ResourceRecommendationInput,
    ): StudyRecommendation = StudyRecommendation(
        id = "resource:${resource.id}",
        studyPlanId = input.studyPlanId,
        type = StudyRecommendationType.OVERDUE_RESOURCE,
        presentation = StudyRecommendationPresentation.OVERDUE_RESOURCE,
        targetLabel = resource.title,
        reasons = listOfNotNull(
            StudyRecommendationReason(StudyRecommendationReasonType.OVERDUE_STUDY_BLOCK),
            resource.totalUnits?.takeIf { it > 0 }?.let { total ->
                StudyRecommendationReason(
                    type = StudyRecommendationReasonType.RESOURCE_PROGRESS,
                    completedUnits = resource.completedUnits.coerceAtLeast(0),
                    totalUnits = total,
                )
            } ?: StudyRecommendationReason(StudyRecommendationReasonType.RESOURCE_UNFINISHED),
            input.nearestGoal?.asReason(),
        ),
        destination = StudyRecommendationDestination.LIBRARY,
        targetId = resource.id,
        score = 680,
    )

    private fun GoalDeadlineInput.asReason() = StudyRecommendationReason(
        type = StudyRecommendationReasonType.GOAL_DEADLINE,
        title = title,
        dateIso = endsOnIso,
    )

    private fun isWeakTopic(topic: TopicRecommendationInput): Boolean =
        topic.masteryPercent < WeakMasteryThreshold || topic.errorFrequency > 0

    private fun topicRiskScore(topic: TopicRecommendationInput): Int =
        (100 - topic.masteryPercent.coerceIn(0, 100)) * 4 +
            topic.priority.coerceIn(0, 5) * 20 +
            topic.errorFrequency.coerceAtLeast(0) * 30

    private const val WeakMasteryThreshold = 80
    private const val PracticeMasteryThreshold = 85
    private const val PracticeAccuracyThreshold = 75
}
