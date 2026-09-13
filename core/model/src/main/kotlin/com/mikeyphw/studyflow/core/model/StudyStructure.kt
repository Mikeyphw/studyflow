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
) {
    init {
        require(organization.isNotBlank()) { "Study target organization cannot be blank." }
        require(role.isNotBlank()) { "Study target role cannot be blank." }
        require(examBoard.isNotBlank()) { "Exam board cannot be blank." }
        require(openings == null || openings >= 0) { "Openings cannot be negative." }
        require(estimatedCutoffPercent == null || estimatedCutoffPercent in 0..100) {
            "Estimated cutoff must be 0 to 100."
        }
        confirmedExamDateIso?.let { date -> requireValidIsoDate(date, "Confirmed exam date") }
        expectedExamDateIso?.let { date -> requireValidIsoDate(date, "Expected exam date") }
    }
}

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
) {
    init {
        require(name.isNotBlank()) { "Exam stage name cannot be blank." }
        require(weight >= 0.0) { "Exam stage weight cannot be negative." }
        require(minimumScorePercent == null || minimumScorePercent in 0..100) { "Minimum score must be 0 to 100." }
        require(position >= 0) { "Exam stage position cannot be negative." }
        scheduledDateIso?.let { date -> requireValidIsoDate(date, "Exam stage date") }
    }
}

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
) {
    init {
        require(name.isNotBlank()) { "Subject name cannot be blank." }
        require(priority in 1..5) { "Subject priority must be 1 to 5." }
        require(examWeight >= 0.0) { "Subject exam weight cannot be negative." }
        require(estimatedMinutes >= 0) { "Subject estimate cannot be negative." }
        require(progressPercent in 0..100) { "Subject progress must be 0 to 100." }
        require(accuracyPercent in 0..100) { "Subject accuracy must be 0 to 100." }
        require(position >= 0) { "Subject position cannot be negative." }
    }
}

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
) {
    init {
        require(name.isNotBlank()) { "Topic name cannot be blank." }
        require(priority in 1..5) { "Topic priority must be 1 to 5." }
        require(difficulty in 1..5) { "Topic difficulty must be 1 to 5." }
        require(examIncidencePercent == null || examIncidencePercent in 0..100) { "Exam incidence must be 0 to 100." }
        require(estimatedMinutes >= 0) { "Topic estimate cannot be negative." }
        require(progressPercent in 0..100) { "Topic progress must be 0 to 100." }
        require(masteryPercent in 0..100) { "Topic mastery must be 0 to 100." }
        require(position >= 0) { "Topic position cannot be negative." }
    }
}

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
    if (topics.isEmpty()) return emptyList()
    val byId = topics.associateBy(Topic::id)
    require(byId.size == topics.size) { "Topic tree contains duplicate topic identities." }
    topics.forEach { topic ->
        val parentId = topic.parentTopicId ?: return@forEach
        require(parentId != topic.id) { "Topic ${topic.id.value} cannot be its own parent." }
        val parent = requireNotNull(byId[parentId]) {
            "Topic ${topic.id.value} references missing parent ${parentId.value}."
        }
        require(parent.studyPlanId == topic.studyPlanId && parent.subjectId == topic.subjectId) {
            "Topic ${topic.id.value} parent belongs to another Study Plan or subject."
        }
    }

    val visitState = mutableMapOf<TopicId, Int>()
    fun detectCycle(topicId: TopicId) {
        when (visitState[topicId]) {
            1 -> error("Topic tree contains a parent cycle involving ${topicId.value}.")
            2 -> return
        }
        visitState[topicId] = 1
        byId.getValue(topicId).parentTopicId?.let(::detectCycle)
        visitState[topicId] = 2
    }
    topics.forEach { detectCycle(it.id) }

    val byParent = topics.groupBy(Topic::parentTopicId)
    fun build(parentId: TopicId?, depth: Int): List<TopicNode> = byParent[parentId].orEmpty()
        .sortedWith(compareBy<Topic> { it.position }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .map { topic ->
            TopicNode(
                topic = topic,
                children = build(topic.id, depth + 1),
                depth = depth,
            )
        }

    val result = build(parentId = null, depth = 0)
    fun count(nodes: List<TopicNode>): Int = nodes.sumOf { 1 + count(it.children) }
    require(count(result) == topics.size) { "Topic tree projection lost one or more topics." }
    return result
}
