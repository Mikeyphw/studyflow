package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import kotlin.math.roundToInt

object StudyProgressCalculator {
    fun topicProgress(topics: List<Topic>): Map<TopicId, Int> {
        val byParent = topics.filterNot { it.archived }.groupBy { it.parentTopicId }
        val visiting = mutableSetOf<TopicId>()
        val memo = mutableMapOf<TopicId, Int>()

        fun calculate(topic: Topic): Int {
            memo[topic.id]?.let { return it }
            if (!visiting.add(topic.id)) return topic.progressPercent.coerceIn(0, 100)
            val children = byParent[topic.id].orEmpty()
            val value = if (children.isEmpty()) {
                topic.progressPercent.coerceIn(0, 100)
            } else {
                weightedAverage(children.map { child -> calculate(child) to child.estimatedMinutes })
            }
            visiting.remove(topic.id)
            memo[topic.id] = value
            return value
        }

        topics.filterNot { it.archived }.forEach(::calculate)
        return memo
    }

    fun subjectProgress(subject: Subject, topics: List<Topic>): Int {
        val active = topics.filter { it.subjectId == subject.id && !it.archived }
        if (active.isEmpty()) return 0
        val progress = topicProgress(active)
        val roots = active.filter { it.parentTopicId == null || parentMissing(it, active) }
        return weightedAverage(roots.map { topic -> progress.getValue(topic.id) to topic.estimatedMinutes })
    }

    fun planProgress(subjects: List<Subject>): Int {
        val active = subjects.filterNot { it.archived }
        if (active.isEmpty()) return 0
        val weighted = active.map { subject ->
            val weight = when {
                subject.examWeight > 0.0 -> (subject.examWeight * 1000).roundToInt()
                subject.estimatedMinutes > 0 -> subject.estimatedMinutes
                else -> 1
            }
            subject.progressPercent.coerceIn(0, 100) to weight
        }
        return weightedAverage(weighted)
    }

    private fun parentMissing(topic: Topic, topics: List<Topic>): Boolean =
        topic.parentTopicId != null && topics.none { it.id == topic.parentTopicId }

    private fun weightedAverage(values: List<Pair<Int, Int>>): Int {
        if (values.isEmpty()) return 0
        val normalized = values.map { (value, weight) -> value.coerceIn(0, 100) to weight.coerceAtLeast(0) }
        val totalWeight = normalized.sumOf { it.second }
        if (totalWeight == 0) return normalized.map { it.first }.average().roundToInt()
        return (normalized.sumOf { (value, weight) -> value.toLong() * weight } / totalWeight.toDouble())
            .roundToInt()
            .coerceIn(0, 100)
    }
}
