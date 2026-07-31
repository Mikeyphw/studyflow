package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyProgressCalculator
import com.mikeyphw.studyflow.core.domain.SyllabusImportDraft
import com.mikeyphw.studyflow.core.domain.SyllabusImportResult
import com.mikeyphw.studyflow.core.domain.TopicBulkUpdate
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class RoomStudyContentRepository(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : StudyContentRepository {
    private val structureDao = database.studyStructureDao()
    private val planDao = database.studyPlanDao()

    override fun observeSyllabus(planId: StudyPlanId): Flow<StudySyllabus> = combine(
        structureDao.observeSubjects(planId.value),
        structureDao.observeTopics(planId.value),
        structureDao.observeExamStages(planId.value),
    ) { subjects, topics, stages ->
        StudySyllabus(
            studyPlanId = planId,
            subjects = subjects.map { it.asExternalModel() },
            topics = topics.map { it.asExternalModel() },
            examStages = stages.map { it.asExternalModel() },
        )
    }

    override suspend fun upsertSubject(subject: Subject): Subject = database.withTransaction {
        requirePlan(subject.studyPlanId)
        validateExamStage(subject.studyPlanId, subject.examStageId?.value)
        val normalized = subject.copy(
            progressPercent = subject.progressPercent.coerceIn(0, 100),
            accuracyPercent = subject.accuracyPercent.coerceIn(0, 100),
        )
        structureDao.upsertSubject(normalized.asEntity())
        recalculateProgress(subject.studyPlanId)
        requireNotNull(structureDao.findSubject(subject.studyPlanId.value, subject.id.value)).asExternalModel()
    }

    override suspend fun upsertTopic(topic: Topic): Topic = database.withTransaction {
        requirePlan(topic.studyPlanId)
        val subject = requireNotNull(structureDao.findSubject(topic.studyPlanId.value, topic.subjectId.value)) {
            "The selected subject does not belong to this Study Plan."
        }
        validateExamStage(topic.studyPlanId, topic.examStageId?.value)
        validateParent(topic, structureDao.findTopics(topic.studyPlanId.value).map { it.asExternalModel() })
        val normalized = normalizeTopic(topic.copy(subjectId = SubjectId(subject.id)))
        structureDao.upsertTopic(normalized.asEntity())
        recalculateProgress(topic.studyPlanId)
        requireNotNull(structureDao.findTopic(topic.studyPlanId.value, topic.id.value)).asExternalModel()
    }

    override suspend fun deleteSubject(planId: StudyPlanId, subjectId: SubjectId) {
        database.withTransaction {
            require(structureDao.deleteSubject(planId.value, subjectId.value) == 1) {
                "Subject does not belong to this Study Plan."
            }
            recalculateProgress(planId)
        }
    }

    override suspend fun deleteTopic(planId: StudyPlanId, topicId: TopicId) {
        database.withTransaction {
            require(structureDao.deleteTopic(planId.value, topicId.value) == 1) {
                "Topic does not belong to this Study Plan."
            }
            recalculateProgress(planId)
        }
    }

    override suspend fun reorderSubjects(planId: StudyPlanId, orderedSubjectIds: List<SubjectId>) {
        database.withTransaction {
            val existing = structureDao.findSubjects(planId.value).map { it.asExternalModel() }
            val ordered = reorder(existing, orderedSubjectIds, Subject::id)
            structureDao.upsertSubjects(ordered.mapIndexed { index, subject ->
                subject.copy(position = index).asEntity()
            })
        }
    }

    override suspend fun reorderTopics(
        planId: StudyPlanId,
        subjectId: SubjectId,
        parentTopicId: TopicId?,
        orderedTopicIds: List<TopicId>,
    ) {
        database.withTransaction {
            requireNotNull(structureDao.findSubject(planId.value, subjectId.value))
            val all = structureDao.findTopics(planId.value).map { it.asExternalModel() }
            val siblings = all.filter { it.subjectId == subjectId && it.parentTopicId == parentTopicId }
            val ordered = reorder(siblings, orderedTopicIds, Topic::id)
            structureDao.upsertTopics(ordered.mapIndexed { index, topic ->
                topic.copy(position = index).asEntity()
            })
        }
    }

    override suspend fun moveTopic(
        planId: StudyPlanId,
        topicId: TopicId,
        destinationSubjectId: SubjectId,
        destinationParentTopicId: TopicId?,
        destinationPosition: Int,
    ) {
        database.withTransaction {
            val all = structureDao.findTopics(planId.value).map { it.asExternalModel() }
            val topic = requireNotNull(all.firstOrNull { it.id == topicId }) {
                "Topic does not belong to this Study Plan."
            }
            requireNotNull(structureDao.findSubject(planId.value, destinationSubjectId.value)) {
                "Destination subject does not belong to this Study Plan."
            }
            val candidate = topic.copy(
                subjectId = destinationSubjectId,
                parentTopicId = destinationParentTopicId,
                position = destinationPosition,
            )
            validateParent(candidate, all)

            val descendantIds = descendantsOf(topicId, all)
            val oldSiblings = all
                .filter {
                    it.id != topicId &&
                        it.subjectId == topic.subjectId &&
                        it.parentTopicId == topic.parentTopicId
                }
                .mapIndexed { index, sibling -> sibling.copy(position = index) }
            val destinationSiblings = all
                .filter {
                    it.id != topicId &&
                        it.subjectId == destinationSubjectId &&
                        it.parentTopicId == destinationParentTopicId
                }
                .toMutableList()
            destinationSiblings.add(destinationPosition.coerceIn(0, destinationSiblings.size), candidate)
            val movedRootAndSiblings = destinationSiblings.mapIndexed { index, sibling ->
                sibling.copy(position = index)
            }
            val movedDescendants = all
                .filter { it.id in descendantIds }
                .map { descendant -> descendant.copy(subjectId = destinationSubjectId) }

            structureDao.upsertTopics(
                (movedRootAndSiblings + oldSiblings + movedDescendants)
                    .distinctBy(Topic::id)
                    .map(Topic::asEntity),
            )
            recalculateProgress(planId)
        }
    }

    override suspend fun bulkUpdateTopics(
        planId: StudyPlanId,
        topicIds: Set<TopicId>,
        update: TopicBulkUpdate,
    ) {
        database.withTransaction {
            validateExamStage(planId, update.examStageId?.value)
            val existing = structureDao.findTopics(planId.value).map { it.asExternalModel() }
            val selected = existing.filter { it.id in topicIds }
            require(selected.size == topicIds.size) { "One or more topics do not belong to this Study Plan." }
            structureDao.upsertTopics(selected.map { topic ->
                normalizeTopic(
                    topic.copy(
                        priority = update.priority ?: topic.priority,
                        difficulty = update.difficulty ?: topic.difficulty,
                        status = update.status ?: topic.status,
                        archived = update.archived ?: topic.archived,
                        examStageId = if (update.replaceExamStage) update.examStageId else topic.examStageId,
                    ),
                ).asEntity()
            })
            recalculateProgress(planId)
        }
    }

    override suspend fun importSyllabus(planId: StudyPlanId, draft: SyllabusImportDraft): SyllabusImportResult =
        database.withTransaction {
            requirePlan(planId)
            val existingSubjects = structureDao.findSubjects(planId.value)
            var subjectPosition = existingSubjects.size
            var importedTopics = 0
            draft.subjects.forEach { subjectDraft ->
                val subjectId = SubjectId(UUID.randomUUID().toString())
                structureDao.upsertSubject(
                    Subject(
                        id = subjectId,
                        studyPlanId = planId,
                        name = subjectDraft.name.trim(),
                        description = "",
                        colorArgb = null,
                        priority = subjectDraft.priority.coerceIn(1, 5),
                        examWeight = subjectDraft.examWeight.coerceAtLeast(0.0),
                        estimatedMinutes = subjectDraft.estimatedMinutes.coerceAtLeast(0),
                        progressPercent = 0,
                        accuracyPercent = 0,
                        status = ContentProgressStatus.NOT_STARTED,
                        position = subjectPosition++,
                        archived = false,
                    ).asEntity(),
                )
                val topicIds = mutableMapOf<String, TopicId>()
                subjectDraft.topics.forEachIndexed { index, topicDraft ->
                    val topicId = TopicId(UUID.randomUUID().toString())
                    topicIds[topicDraft.key] = topicId
                    structureDao.upsertTopic(
                        normalizeTopic(
                            Topic(
                                id = topicId,
                                studyPlanId = planId,
                                subjectId = subjectId,
                                parentTopicId = topicDraft.parentKey?.let(topicIds::get),
                                name = topicDraft.name.trim(),
                                description = "",
                                priority = topicDraft.priority.coerceIn(1, 5),
                                difficulty = topicDraft.difficulty.coerceIn(1, 5),
                                examIncidencePercent = topicDraft.examIncidencePercent?.coerceIn(0, 100),
                                estimatedMinutes = topicDraft.estimatedMinutes.coerceAtLeast(0),
                                progressPercent = topicDraft.progressPercent.coerceIn(0, 100),
                                masteryPercent = topicDraft.masteryPercent.coerceIn(0, 100),
                                status = topicDraft.status,
                                position = index,
                                archived = false,
                            ),
                        ).asEntity(),
                    )
                    importedTopics++
                }
            }
            recalculateProgress(planId)
            SyllabusImportResult(draft.subjects.size, importedTopics)
        }

    private suspend fun recalculateProgress(planId: StudyPlanId) {
        val subjects = structureDao.findSubjects(planId.value).map { it.asExternalModel() }
        val topics = structureDao.findTopics(planId.value).map { it.asExternalModel() }
        val updated = subjects.map { subject ->
            val progress = StudyProgressCalculator.subjectProgress(subject, topics)
            subject.copy(
                progressPercent = progress,
                status = when {
                    subject.archived -> ContentProgressStatus.ARCHIVED
                    progress >= 100 -> ContentProgressStatus.MASTERED
                    progress > 0 -> ContentProgressStatus.STUDYING
                    else -> ContentProgressStatus.NOT_STARTED
                },
            )
        }
        if (updated.isNotEmpty()) structureDao.upsertSubjects(updated.map(Subject::asEntity))
        planDao.updateProgress(planId.value, StudyProgressCalculator.planProgress(updated), clock())
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        require(planDao.countById(planId.value) == 1) { "Study Plan ${planId.value} does not exist." }
    }

    private suspend fun validateExamStage(planId: StudyPlanId, stageId: String?) {
        if (stageId != null) {
            require(structureDao.countExamStage(planId.value, stageId) == 1) {
                "Exam stage does not belong to this Study Plan."
            }
        }
    }

    private fun validateParent(topic: Topic, allTopics: List<Topic>) {
        val parentId = topic.parentTopicId ?: return
        require(parentId != topic.id) { "A topic cannot be its own parent." }
        val parent = requireNotNull(allTopics.firstOrNull { it.id == parentId }) {
            "Parent topic does not belong to this Study Plan."
        }
        require(parent.subjectId == topic.subjectId) { "Parent topic must belong to the same subject." }
        val parentChain = generateSequence(parent) { candidate ->
            candidate.parentTopicId?.let { id -> allTopics.firstOrNull { it.id == id } }
        }.map { it.id }.toSet()
        require(topic.id !in parentChain) { "Moving this topic would create a cycle." }
    }

    private fun descendantsOf(topicId: TopicId, allTopics: List<Topic>): Set<TopicId> {
        val childrenByParent = allTopics.groupBy(Topic::parentTopicId)
        val result = mutableSetOf<TopicId>()
        fun visit(parentId: TopicId) {
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (result.add(child.id)) visit(child.id)
            }
        }
        visit(topicId)
        return result
    }

    private fun normalizeTopic(topic: Topic): Topic = when (topic.status) {
        ContentProgressStatus.MASTERED -> topic.copy(progressPercent = 100, masteryPercent = 100, archived = false)
        ContentProgressStatus.NOT_STARTED -> topic.copy(progressPercent = 0, masteryPercent = 0, archived = false)
        ContentProgressStatus.ARCHIVED -> topic.copy(archived = true)
        else -> topic.copy(
            progressPercent = topic.progressPercent.coerceIn(0, 100),
            masteryPercent = topic.masteryPercent.coerceIn(0, 100),
        )
    }

    private fun <T, ID> reorder(existing: List<T>, orderedIds: List<ID>, id: (T) -> ID): List<T> {
        val byId = existing.associateBy(id)
        require(orderedIds.all(byId::containsKey)) { "Ordering contains an item outside the selected collection." }
        return orderedIds.distinct().mapNotNull(byId::get) + existing.filter { id(it) !in orderedIds }
    }
}
