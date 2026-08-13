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
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.StudyTarget
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

    override suspend fun upsertStudyTarget(target: StudyTarget): StudyTarget = database.withTransaction {
        requirePlan(target.studyPlanId)
        structureDao.upsertTarget(target.asEntity())
        requireNotNull(structureDao.findTarget(target.studyPlanId.value, target.id.value)).asExternalModel()
    }

    override suspend fun upsertExamStage(stage: ExamStage): ExamStage = database.withTransaction {
        requirePlan(stage.studyPlanId)
        requireNotNull(structureDao.findTarget(stage.studyPlanId.value, stage.studyTargetId.value)) {
            "Study target does not belong to this Study Plan."
        }
        structureDao.upsertExamStage(stage.asEntity())
        requireNotNull(structureDao.findExamStage(stage.studyPlanId.value, stage.id.value)).asExternalModel()
    }

    override suspend fun upsertSubject(subject: Subject): Subject = database.withTransaction {
        requirePlan(subject.studyPlanId)
        validateExamStage(subject.studyPlanId, subject.examStageId?.value)
        val normalized = normalizeSubject(subject)
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

            val subtreeIds = descendantsOf(topicId, all) + topicId
            require(destinationParentTopicId == null || destinationParentTopicId !in subtreeIds) {
                "A topic cannot be moved beneath its own descendant."
            }
            val subtree = all.filter { it.id in subtreeIds }
            val originals = subtree.associateBy(Topic::id)

            // The v4 integrity guard correctly forbids moving a parent away from children that still
            // belong to the old subject. Detach the complete subtree first, move it as one logical
            // unit, repair redundant subject ownership in linked rows, then restore hierarchy.
            val detached = subtree.map { it.copy(parentTopicId = null) }
            structureDao.upsertTopics(detached.map(Topic::asEntity))
            val movedDetached = detached.map { it.copy(subjectId = destinationSubjectId) }
            structureDao.upsertTopics(movedDetached.map(Topic::asEntity))
            subtreeIds.forEach { movedTopicId ->
                updateLinkedTopicSubject(planId, movedTopicId, destinationSubjectId)
            }
            val restored = movedDetached.map { moved ->
                val original = originals.getValue(moved.id)
                moved.copy(
                    parentTopicId = if (moved.id == topicId) destinationParentTopicId else original.parentTopicId,
                    position = if (moved.id == topicId) destinationPosition else original.position,
                )
            }
            structureDao.upsertTopics(restored.map(Topic::asEntity))

            val oldSiblings = all
                .filter {
                    it.id !in subtreeIds &&
                        it.subjectId == topic.subjectId &&
                        it.parentTopicId == topic.parentTopicId
                }
                .mapIndexed { index, sibling -> sibling.copy(position = index) }
            val movedRoot = restored.first { it.id == topicId }
            val destinationSiblings = all
                .filter {
                    it.id !in subtreeIds &&
                        it.subjectId == destinationSubjectId &&
                        it.parentTopicId == destinationParentTopicId
                }
                .toMutableList()
            destinationSiblings.add(destinationPosition.coerceIn(0, destinationSiblings.size), movedRoot)
            val reorderedDestination = destinationSiblings.mapIndexed { index, sibling -> sibling.copy(position = index) }
            structureDao.upsertTopics((oldSiblings + reorderedDestination).map(Topic::asEntity))
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
                    normalizeSubject(
                        Subject(
                            id = subjectId,
                            studyPlanId = planId,
                            name = subjectDraft.name.trim(),
                            description = "",
                            colorArgb = null,
                            priority = subjectDraft.priority,
                            examWeight = subjectDraft.examWeight,
                            estimatedMinutes = subjectDraft.estimatedMinutes,
                            progressPercent = 0,
                            accuracyPercent = 0,
                            status = ContentProgressStatus.NOT_STARTED,
                            position = subjectPosition++,
                            archived = false,
                        ),
                    ).asEntity(),
                )
                val topicIds = subjectDraft.topics.associate { draftTopic ->
                    draftTopic.key to TopicId(UUID.randomUUID().toString())
                }
                require(topicIds.size == subjectDraft.topics.size) { "Imported topic keys must be unique within a subject." }
                subjectDraft.topics.forEach { topicDraft ->
                    require(topicDraft.parentKey == null || topicDraft.parentKey in topicIds) {
                        "Imported topic '${topicDraft.name}' references an unknown parent key."
                    }
                }
                val pending = subjectDraft.topics.toMutableList()
                val inserted = mutableSetOf<String>()
                while (pending.isNotEmpty()) {
                    val ready = pending.filter { topicDraft ->
                        topicDraft.parentKey == null || topicDraft.parentKey in inserted
                    }
                    require(ready.isNotEmpty()) { "Imported topic hierarchy contains a cycle." }
                    ready.forEach { topicDraft ->
                        val topicId = topicIds.getValue(topicDraft.key)
                        structureDao.upsertTopic(
                            normalizeTopic(
                                Topic(
                                    id = topicId,
                                    studyPlanId = planId,
                                    subjectId = subjectId,
                                    parentTopicId = topicDraft.parentKey?.let(topicIds::getValue),
                                    name = topicDraft.name.trim(),
                                    description = "",
                                    priority = topicDraft.priority,
                                    difficulty = topicDraft.difficulty,
                                    examIncidencePercent = topicDraft.examIncidencePercent,
                                    estimatedMinutes = topicDraft.estimatedMinutes,
                                    progressPercent = topicDraft.progressPercent,
                                    masteryPercent = topicDraft.masteryPercent,
                                    status = topicDraft.status,
                                    position = subjectDraft.topics.indexOf(topicDraft),
                                    archived = false,
                                ),
                            ).asEntity(),
                        )
                        inserted += topicDraft.key
                        importedTopics++
                    }
                    pending.removeAll(ready.toSet())
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

    private fun normalizeSubject(subject: Subject): Subject {
        if (subject.archived) {
            return subject.copy(
                status = ContentProgressStatus.ARCHIVED,
                progressPercent = subject.progressPercent.coerceIn(0, 100),
                accuracyPercent = subject.accuracyPercent.coerceIn(0, 100),
            )
        }
        val restoredStatus = if (subject.status == ContentProgressStatus.ARCHIVED) {
            restoredStatus(subject.progressPercent)
        } else {
            subject.status
        }
        return when (restoredStatus) {
            ContentProgressStatus.MASTERED -> subject.copy(
                status = restoredStatus,
                progressPercent = 100,
                archived = false,
            )
            ContentProgressStatus.NOT_STARTED -> subject.copy(
                status = restoredStatus,
                progressPercent = 0,
                archived = false,
            )
            ContentProgressStatus.ARCHIVED -> error("Unreachable restored Subject status.")
            else -> subject.copy(
                status = restoredStatus,
                progressPercent = subject.progressPercent.coerceIn(0, 100),
                accuracyPercent = subject.accuracyPercent.coerceIn(0, 100),
                archived = false,
            )
        }
    }

    private fun updateLinkedTopicSubject(
        planId: StudyPlanId,
        topicId: TopicId,
        destinationSubjectId: SubjectId,
    ) {
        val databaseHandle = database.openHelper.writableDatabase
        LINKED_TOPIC_TABLES.forEach { table ->
            databaseHandle.execSQL(
                "UPDATE `$table` SET subjectId = ? WHERE studyPlanId = ? AND topicId = ?",
                arrayOf(destinationSubjectId.value, planId.value, topicId.value),
            )
        }
    }

    private fun normalizeTopic(topic: Topic): Topic {
        if (topic.archived) {
            return topic.copy(
                status = ContentProgressStatus.ARCHIVED,
                progressPercent = topic.progressPercent.coerceIn(0, 100),
                masteryPercent = topic.masteryPercent.coerceIn(0, 100),
            )
        }
        val restoredStatus = if (topic.status == ContentProgressStatus.ARCHIVED) {
            restoredStatus(topic.progressPercent)
        } else {
            topic.status
        }
        return when (restoredStatus) {
            ContentProgressStatus.MASTERED -> topic.copy(
                status = restoredStatus,
                progressPercent = 100,
                masteryPercent = 100,
                archived = false,
            )
            ContentProgressStatus.NOT_STARTED -> topic.copy(
                status = restoredStatus,
                progressPercent = 0,
                masteryPercent = 0,
                archived = false,
            )
            ContentProgressStatus.ARCHIVED -> error("Unreachable restored Topic status.")
            else -> topic.copy(
                status = restoredStatus,
                progressPercent = topic.progressPercent.coerceIn(0, 100),
                masteryPercent = topic.masteryPercent.coerceIn(0, 100),
                archived = false,
            )
        }
    }

    private fun restoredStatus(progressPercent: Int): ContentProgressStatus = when {
        progressPercent >= 100 -> ContentProgressStatus.MASTERED
        progressPercent > 0 -> ContentProgressStatus.STUDYING
        else -> ContentProgressStatus.NOT_STARTED
    }

    private companion object {
        val LINKED_TOPIC_TABLES = listOf(
            "study_materials",
            "video_resources",
            "planned_activities",
            "study_sessions",
            "study_goals",
            "annexes",
            "questions",
            "flashcards",
            "error_entries",
            "review_items",
            "study_notes",
        )
    }

    private fun <T, ID> reorder(existing: List<T>, orderedIds: List<ID>, id: (T) -> ID): List<T> {
        val byId = existing.associateBy(id)
        require(orderedIds.all(byId::containsKey)) { "Ordering contains an item outside the selected collection." }
        return orderedIds.distinct().mapNotNull(byId::get) + existing.filter { id(it) !in orderedIds }
    }
}
