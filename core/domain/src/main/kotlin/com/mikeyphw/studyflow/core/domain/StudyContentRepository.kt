package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import kotlinx.coroutines.flow.Flow

data class TopicBulkUpdate(
    val priority: Int? = null,
    val difficulty: Int? = null,
    val status: ContentProgressStatus? = null,
    val archived: Boolean? = null,
    val examStageId: ExamStageId? = null,
    val replaceExamStage: Boolean = false,
)

interface StudyContentRepository {
    fun observeSyllabus(planId: StudyPlanId): Flow<StudySyllabus>

    suspend fun upsertStudyTarget(target: StudyTarget): StudyTarget
    suspend fun upsertExamStage(stage: ExamStage): ExamStage
    suspend fun upsertSubject(subject: Subject): Subject
    suspend fun upsertTopic(topic: Topic): Topic
    suspend fun deleteSubject(planId: StudyPlanId, subjectId: SubjectId)
    suspend fun deleteTopic(planId: StudyPlanId, topicId: TopicId)
    suspend fun reorderSubjects(planId: StudyPlanId, orderedSubjectIds: List<SubjectId>)
    suspend fun reorderTopics(
        planId: StudyPlanId,
        subjectId: SubjectId,
        parentTopicId: TopicId?,
        orderedTopicIds: List<TopicId>,
    )
    suspend fun moveTopic(
        planId: StudyPlanId,
        topicId: TopicId,
        destinationSubjectId: SubjectId,
        destinationParentTopicId: TopicId?,
        destinationPosition: Int,
    )
    suspend fun bulkUpdateTopics(
        planId: StudyPlanId,
        topicIds: Set<TopicId>,
        update: TopicBulkUpdate,
    )
    suspend fun importSyllabus(planId: StudyPlanId, draft: SyllabusImportDraft): SyllabusImportResult
}
