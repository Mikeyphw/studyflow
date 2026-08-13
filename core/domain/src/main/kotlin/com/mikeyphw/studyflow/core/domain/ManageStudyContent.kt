package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId

class ManageStudyContent(
    private val repository: StudyContentRepository,
) {
    fun observeSyllabus(planId: StudyPlanId) = repository.observeSyllabus(planId)

    suspend fun saveSubject(subject: Subject): Subject {
        require(subject.name.isNotBlank()) { "Subject name cannot be blank." }
        require(subject.priority in 1..5) { "Subject priority must be between 1 and 5." }
        require(subject.examWeight.isFinite() && subject.examWeight >= 0.0) {
            "Subject exam weight must be finite and non-negative."
        }
        require(subject.estimatedMinutes >= 0) { "Estimated minutes cannot be negative." }
        return repository.upsertSubject(
            subject.copy(progressPercent = subject.progressPercent.coerceIn(0, 100)),
        )
    }

    suspend fun saveTopic(topic: Topic): Topic {
        require(topic.name.isNotBlank()) { "Topic name cannot be blank." }
        require(topic.priority in 1..5) { "Topic priority must be between 1 and 5." }
        require(topic.difficulty in 1..5) { "Topic difficulty must be between 1 and 5." }
        require(topic.estimatedMinutes >= 0) { "Estimated minutes cannot be negative." }
        require(topic.examIncidencePercent == null || topic.examIncidencePercent in 0..100) {
            "Exam incidence must be between 0 and 100."
        }
        return repository.upsertTopic(
            topic.copy(
                progressPercent = topic.progressPercent.coerceIn(0, 100),
                masteryPercent = topic.masteryPercent.coerceIn(0, 100),
            ),
        )
    }


    suspend fun archiveSubject(subject: Subject): Subject = saveSubject(
        subject.copy(status = com.mikeyphw.studyflow.core.model.ContentProgressStatus.ARCHIVED, archived = true),
    )

    suspend fun restoreSubject(subject: Subject): Subject = saveSubject(
        subject.copy(
            status = restoredStatus(subject.progressPercent),
            archived = false,
        ),
    )

    suspend fun archiveTopic(topic: Topic): Topic = saveTopic(
        topic.copy(status = com.mikeyphw.studyflow.core.model.ContentProgressStatus.ARCHIVED, archived = true),
    )

    suspend fun restoreTopic(topic: Topic): Topic = saveTopic(
        topic.copy(
            status = restoredStatus(topic.progressPercent),
            archived = false,
        ),
    )

    suspend fun deleteSubject(planId: StudyPlanId, subjectId: SubjectId) =
        repository.deleteSubject(planId, subjectId)

    suspend fun deleteTopic(planId: StudyPlanId, topicId: TopicId) =
        repository.deleteTopic(planId, topicId)

    suspend fun reorderSubjects(planId: StudyPlanId, orderedIds: List<SubjectId>) =
        repository.reorderSubjects(planId, orderedIds.distinct())

    suspend fun reorderTopics(
        planId: StudyPlanId,
        subjectId: SubjectId,
        parentTopicId: TopicId?,
        orderedIds: List<TopicId>,
    ) = repository.reorderTopics(planId, subjectId, parentTopicId, orderedIds.distinct())

    suspend fun moveTopic(
        planId: StudyPlanId,
        topicId: TopicId,
        subjectId: SubjectId,
        parentTopicId: TopicId?,
        position: Int,
    ) = repository.moveTopic(planId, topicId, subjectId, parentTopicId, position.coerceAtLeast(0))

    suspend fun bulkUpdateTopics(planId: StudyPlanId, topicIds: Set<TopicId>, update: TopicBulkUpdate) {
        if (topicIds.isEmpty()) return
        update.priority?.let { require(it in 1..5) }
        update.difficulty?.let { require(it in 1..5) }
        repository.bulkUpdateTopics(planId, topicIds, update)
    }

    suspend fun importSyllabus(planId: StudyPlanId, draft: SyllabusImportDraft): SyllabusImportResult {
        require(draft.subjects.isNotEmpty()) { "The syllabus does not contain any subjects." }
        require(draft.subjects.map { it.key }.distinct().size == draft.subjects.size) {
            "Syllabus subject keys must be unique."
        }
        draft.subjects.forEach { subject ->
            require(subject.name.isNotBlank()) { "Imported subject names cannot be blank." }
            require(subject.examWeight.isFinite() && subject.examWeight >= 0.0)
            val topicKeys = subject.topics.map { it.key }
            require(topicKeys.distinct().size == topicKeys.size) {
                "Syllabus topic keys must be unique within a subject."
            }
            require(subject.topics.all { it.parentKey == null || it.parentKey in topicKeys }) {
                "Imported topics must reference a parent from the same subject."
            }
        }
        return repository.importSyllabus(planId, draft)
    }
    private fun restoredStatus(progressPercent: Int): com.mikeyphw.studyflow.core.model.ContentProgressStatus = when {
        progressPercent >= 100 -> com.mikeyphw.studyflow.core.model.ContentProgressStatus.MASTERED
        progressPercent > 0 -> com.mikeyphw.studyflow.core.model.ContentProgressStatus.STUDYING
        else -> com.mikeyphw.studyflow.core.model.ContentProgressStatus.NOT_STARTED
    }

}
