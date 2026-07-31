package com.mikeyphw.studyflow.feature.subjects

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SyllabusFilter
import com.mikeyphw.studyflow.core.model.SyllabusSort
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.TopicNode

internal data class VisibleSubject(
    val subject: Subject,
    val topicTree: List<TopicNode>,
    val visibleTopicCount: Int,
)

internal fun StudySyllabus.visibleSubjects(
    query: String,
    filter: SyllabusFilter,
    sort: SyllabusSort,
): List<VisibleSubject> {
    val normalizedQuery = query.trim().lowercase()
    val matchingTopicIds = topics.filter { topic ->
        normalizedQuery.isBlank() || topic.name.lowercase().contains(normalizedQuery) ||
            topic.description.lowercase().contains(normalizedQuery)
    }.mapTo(mutableSetOf(), Topic::id)
    val ancestorIds = buildSet {
        matchingTopicIds.forEach { id ->
            var parent = topics.firstOrNull { it.id == id }?.parentTopicId
            while (parent != null && add(parent)) {
                parent = topics.firstOrNull { it.id == parent }?.parentTopicId
            }
        }
    }
    val allowedTopicIds = matchingTopicIds + ancestorIds

    val candidates = subjects.filter { subject ->
        val subjectMatches = normalizedQuery.isBlank() ||
            subject.name.lowercase().contains(normalizedQuery) ||
            subject.description.lowercase().contains(normalizedQuery)
        val hasMatchingTopic = topics.any { it.subjectId == subject.id && it.id in allowedTopicIds }
        (subjectMatches || hasMatchingTopic) && subject.matches(filter)
    }
    val sorted = when (sort) {
        SyllabusSort.POSITION -> candidates.sortedWith(
            compareBy<Subject> { it.position }.thenBy { it.name.lowercase() },
        )
        SyllabusSort.PRIORITY -> candidates.sortedWith(
            compareByDescending<Subject> { it.priority }.thenBy { it.name.lowercase() },
        )
        SyllabusSort.NAME -> candidates.sortedBy { it.name.lowercase() }
        SyllabusSort.PROGRESS -> candidates.sortedWith(
            compareBy<Subject> { it.progressPercent }.thenBy { it.name.lowercase() },
        )
    }
    return sorted.map { subject ->
        val subjectTopics = topics.filter { topic ->
            topic.subjectId == subject.id && topic.matches(filter) &&
                (normalizedQuery.isBlank() || topic.id in allowedTopicIds)
        }
        VisibleSubject(
            subject = subject,
            topicTree = com.mikeyphw.studyflow.core.model.buildTopicTree(subjectTopics),
            visibleTopicCount = subjectTopics.size,
        )
    }
}

private fun Subject.matches(filter: SyllabusFilter): Boolean = when (filter) {
    SyllabusFilter.ALL -> true
    SyllabusFilter.ACTIVE -> !archived
    SyllabusFilter.NEEDS_ATTENTION -> !archived && progressPercent < 70
    SyllabusFilter.MASTERED -> !archived && progressPercent >= 100
    SyllabusFilter.ARCHIVED -> archived
}

private fun Topic.matches(filter: SyllabusFilter): Boolean = when (filter) {
    SyllabusFilter.ALL -> true
    SyllabusFilter.ACTIVE -> !archived
    SyllabusFilter.NEEDS_ATTENTION -> !archived && masteryPercent < 70
    SyllabusFilter.MASTERED -> !archived && status == ContentProgressStatus.MASTERED
    SyllabusFilter.ARCHIVED -> archived
}

internal fun siblingIds(
    topics: List<Topic>,
    topic: Topic,
): List<TopicId> = topics
    .filter { it.subjectId == topic.subjectId && it.parentTopicId == topic.parentTopicId }
    .sortedWith(compareBy<Topic> { it.position }.thenBy { it.name.lowercase() })
    .map(Topic::id)

internal fun <T> moveItem(values: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in values.indices || toIndex !in values.indices || fromIndex == toIndex) return values
    return values.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}
