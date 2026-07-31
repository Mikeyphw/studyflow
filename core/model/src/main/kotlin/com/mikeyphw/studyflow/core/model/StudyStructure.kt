package com.mikeyphw.studyflow.core.model

enum class ExamStageType {
    OBJECTIVE,
    WRITTEN,
    ESSAY,
    PRACTICAL,
    PHYSICAL,
    ORAL,
    TITLES,
    OTHER,
}

enum class ContentProgressStatus {
    NOT_STARTED,
    STUDYING,
    REVIEWING,
    MASTERED,
    ARCHIVED,
}

data class StudyTarget(
    val id: StudyTargetId,
    val studyPlanId: StudyPlanId,
    val organization: String,
    val role: String,
    val examBoard: String,
    val noticeUrl: String?,
    val confirmedExamDateIso: String?,
    val expectedExamDateIso: String?,
    val openings: Int?,
    val estimatedCutoffPercent: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ExamStage(
    val id: ExamStageId,
    val studyPlanId: StudyPlanId,
    val studyTargetId: StudyTargetId,
    val name: String,
    val type: ExamStageType,
    val scheduledDateIso: String?,
    val weight: Double,
    val minimumScorePercent: Int?,
    val position: Int,
)

data class Subject(
    val id: SubjectId,
    val studyPlanId: StudyPlanId,
    val name: String,
    val description: String,
    val colorArgb: Long?,
    val priority: Int,
    val examWeight: Double,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val accuracyPercent: Int,
    val status: ContentProgressStatus,
    val position: Int,
    val archived: Boolean,
    val examStageId: ExamStageId? = null,
)

data class Topic(
    val id: TopicId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId,
    val parentTopicId: TopicId?,
    val name: String,
    val description: String,
    val priority: Int,
    val difficulty: Int,
    val examIncidencePercent: Int?,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val masteryPercent: Int,
    val status: ContentProgressStatus,
    val position: Int,
    val archived: Boolean,
    val examStageId: ExamStageId? = null,
)

enum class SyllabusFilter {
    ALL,
    ACTIVE,
    NEEDS_ATTENTION,
    MASTERED,
    ARCHIVED,
}

enum class SyllabusSort {
    POSITION,
    PRIORITY,
    NAME,
    PROGRESS,
}

data class StudySyllabus(
    val studyPlanId: StudyPlanId,
    val subjects: List<Subject>,
    val topics: List<Topic>,
    val examStages: List<ExamStage> = emptyList(),
) {
    fun topicsFor(subjectId: SubjectId): List<Topic> = topics.filter { it.subjectId == subjectId }

    fun topicTree(subjectId: SubjectId): List<TopicNode> = buildTopicTree(topicsFor(subjectId))
}

data class TopicNode(
    val topic: Topic,
    val children: List<TopicNode>,
    val depth: Int,
)

fun buildTopicTree(topics: List<Topic>): List<TopicNode> {
    val byParent = topics.groupBy { it.parentTopicId }
    fun build(parentId: TopicId?, depth: Int, ancestry: Set<TopicId>): List<TopicNode> =
        byParent[parentId].orEmpty()
            .sortedWith(compareBy<Topic> { it.position }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .mapNotNull { topic ->
                if (topic.id in ancestry) return@mapNotNull null
                TopicNode(
                    topic = topic,
                    children = build(topic.id, depth + 1, ancestry + topic.id),
                    depth = depth,
                )
            }
    return build(parentId = null, depth = 0, ancestry = emptySet())
}
