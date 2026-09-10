package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.StudyContentRepository
import com.mikeyphw.studyflow.core.domain.StudyProgressCalculator
import com.mikeyphw.studyflow.core.domain.SyllabusImportDraft
import com.mikeyphw.studyflow.core.domain.SyllabusImportResult
import com.mikeyphw.studyflow.core.domain.TopicBulkUpdate
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamStage
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

data class DesktopStudyContentState(
    val targets: List<StudyTarget> = emptyList(),
    val examStages: List<ExamStage> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val topics: List<Topic> = emptyList(),
)

class DesktopStudyContentRepository(
    initialState: DesktopStudyContentState,
    private val plans: InMemoryStudyPlanRepository,
    private val onChanged: (DesktopStudyContentState) -> Unit = {},
) : StudyContentRepository {
    private val state = MutableStateFlow(initialState)

    override fun observeStudyTarget(planId: StudyPlanId): Flow<StudyTarget?> = state.map { current ->
        current.targets.firstOrNull { it.studyPlanId == planId }
    }

    override fun observeSyllabus(planId: StudyPlanId): Flow<StudySyllabus> = state.map { current ->
        StudySyllabus(
            studyPlanId = planId,
            subjects = current.subjects.filter { it.studyPlanId == planId }.sortedBy(Subject::position),
            topics = current.topics.filter { it.studyPlanId == planId },
            examStages = current.examStages.filter { it.studyPlanId == planId }.sortedBy(ExamStage::position),
        )
    }

    override suspend fun upsertStudyTarget(target: StudyTarget): StudyTarget {
        requireMutablePlan(target.studyPlanId)
        val existing = state.value.targets.firstOrNull { it.id == target.id }
        require(existing == null || existing.studyPlanId == target.studyPlanId) {
            "Study target id already belongs to another Study Plan."
        }
        mutate { current -> current.copy(targets = current.targets.upsertBy(target, StudyTarget::id)) }
        return target
    }

    override suspend fun upsertExamStage(stage: ExamStage): ExamStage {
        requireMutablePlan(stage.studyPlanId)
        require(state.value.targets.any { it.id == stage.studyTargetId && it.studyPlanId == stage.studyPlanId }) {
            "Study target does not belong to this Study Plan."
        }
        mutate { current -> current.copy(examStages = current.examStages.upsertBy(stage, ExamStage::id)) }
        return stage
    }

    override suspend fun upsertSubject(subject: Subject): Subject {
        requireMutablePlan(subject.studyPlanId)
        validateStage(subject.studyPlanId, subject.examStageId?.value)
        val normalized = normalizeSubject(subject)
        mutate { current -> current.copy(subjects = current.subjects.upsertBy(normalized, Subject::id)) }
        recalculateProgress(subject.studyPlanId)
        return currentSubject(subject.studyPlanId, subject.id)
    }

    override suspend fun upsertTopic(topic: Topic): Topic {
        requireMutablePlan(topic.studyPlanId)
        require(state.value.subjects.any { it.id == topic.subjectId && it.studyPlanId == topic.studyPlanId }) {
            "The selected subject does not belong to this Study Plan."
        }
        validateStage(topic.studyPlanId, topic.examStageId?.value)
        validateParent(topic, state.value.topics.filter { it.studyPlanId == topic.studyPlanId })
        val normalized = normalizeTopic(topic)
        mutate { current -> current.copy(topics = current.topics.upsertBy(normalized, Topic::id)) }
        recalculateProgress(topic.studyPlanId)
        return currentTopic(topic.studyPlanId, topic.id)
    }

    override suspend fun deleteSubject(planId: StudyPlanId, subjectId: SubjectId) {
        requireMutablePlan(planId)
        require(state.value.subjects.any { it.studyPlanId == planId && it.id == subjectId }) {
            "Subject does not belong to this Study Plan."
        }
        mutate { current ->
            current.copy(
                subjects = current.subjects.filterNot { it.studyPlanId == planId && it.id == subjectId },
                topics = current.topics.filterNot { it.studyPlanId == planId && it.subjectId == subjectId },
            )
        }
        recalculateProgress(planId)
    }

    override suspend fun deleteTopic(planId: StudyPlanId, topicId: TopicId) {
        requireMutablePlan(planId)
        val all = state.value.topics.filter { it.studyPlanId == planId }
        require(all.any { it.id == topicId }) { "Topic does not belong to this Study Plan." }
        val removed = descendantsOf(topicId, all) + topicId
        mutate { current ->
            current.copy(topics = current.topics.filterNot { it.studyPlanId == planId && it.id in removed })
        }
        recalculateProgress(planId)
    }

    override suspend fun reorderSubjects(planId: StudyPlanId, orderedSubjectIds: List<SubjectId>) {
        requireMutablePlan(planId)
        val subjects = state.value.subjects.filter { it.studyPlanId == planId }
        val ordered = reorder(subjects, orderedSubjectIds, Subject::id)
        val replacements = ordered.mapIndexed { index, subject -> subject.copy(position = index) }
        mutate { current ->
            current.copy(
                subjects = current.subjects.replacePlanRows(planId, replacements) { it.studyPlanId },
            )
        }
    }

    override suspend fun reorderTopics(
        planId: StudyPlanId,
        subjectId: SubjectId,
        parentTopicId: TopicId?,
        orderedTopicIds: List<TopicId>,
    ) {
        requireMutablePlan(planId)
        require(state.value.subjects.any { it.studyPlanId == planId && it.id == subjectId }) {
            "Subject does not belong to this Study Plan."
        }
        val all = state.value.topics.filter { it.studyPlanId == planId }
        val siblings = all.filter { it.subjectId == subjectId && it.parentTopicId == parentTopicId }
        val ordered = reorder(siblings, orderedTopicIds, Topic::id)
        val byId = ordered.mapIndexed { index, topic -> topic.id to topic.copy(position = index) }.toMap()
        mutate { current -> current.copy(topics = current.topics.map { byId[it.id] ?: it }) }
    }

    override suspend fun moveTopic(
        planId: StudyPlanId,
        topicId: TopicId,
        destinationSubjectId: SubjectId,
        destinationParentTopicId: TopicId?,
        destinationPosition: Int,
    ) {
        requireMutablePlan(planId)
        val all = state.value.topics.filter { it.studyPlanId == planId }
        val topic = requireNotNull(all.firstOrNull { it.id == topicId }) { "Topic does not belong to this Study Plan." }
        require(state.value.subjects.any { it.studyPlanId == planId && it.id == destinationSubjectId }) {
            "Destination subject does not belong to this Study Plan."
        }
        val subtree = descendantsOf(topicId, all) + topicId
        require(destinationParentTopicId == null || destinationParentTopicId !in subtree) {
            "A topic cannot be moved beneath its own descendant."
        }
        val candidate = topic.copy(
            subjectId = destinationSubjectId,
            parentTopicId = destinationParentTopicId,
            position = destinationPosition.coerceAtLeast(0),
        )
        validateParent(candidate, all.filterNot { it.id in subtree })
        mutate { current ->
            current.copy(topics = current.topics.map { row ->
                when {
                    row.id == topicId -> candidate
                    row.id in subtree -> row.copy(subjectId = destinationSubjectId)
                    else -> row
                }
            })
        }
        normalizeSiblingPositions(planId)
        recalculateProgress(planId)
    }

    override suspend fun bulkUpdateTopics(planId: StudyPlanId, topicIds: Set<TopicId>, update: TopicBulkUpdate) {
        requireMutablePlan(planId)
        validateStage(planId, update.examStageId?.value)
        val selected = state.value.topics.filter { it.studyPlanId == planId && it.id in topicIds }
        require(selected.size == topicIds.size) { "One or more topics do not belong to this Study Plan." }
        val replacements = selected.associate { topic ->
            topic.id to normalizeTopic(
                topic.copy(
                    priority = update.priority ?: topic.priority,
                    difficulty = update.difficulty ?: topic.difficulty,
                    status = update.status ?: topic.status,
                    archived = update.archived ?: topic.archived,
                    examStageId = if (update.replaceExamStage) update.examStageId else topic.examStageId,
                ),
            )
        }
        mutate { current -> current.copy(topics = current.topics.map { replacements[it.id] ?: it }) }
        recalculateProgress(planId)
    }

    override suspend fun importSyllabus(planId: StudyPlanId, draft: SyllabusImportDraft): SyllabusImportResult {
        requireMutablePlan(planId)
        var subjectPosition = state.value.subjects.count { it.studyPlanId == planId }
        var importedTopics = 0
        draft.subjects.forEach { subjectDraft ->
            val subjectId = SubjectId(UUID.randomUUID().toString())
            upsertSubject(
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
            )
            val ids = subjectDraft.topics.associate { it.key to TopicId(UUID.randomUUID().toString()) }
            require(ids.size == subjectDraft.topics.size) { "Imported topic keys must be unique within a subject." }
            val pending = subjectDraft.topics.toMutableList()
            val inserted = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val ready = pending.filter { it.parentKey == null || it.parentKey in inserted }
                require(ready.isNotEmpty()) { "Imported topic hierarchy contains a cycle." }
                ready.forEach { topicDraft ->
                    upsertTopic(
                        Topic(
                            id = ids.getValue(topicDraft.key),
                            studyPlanId = planId,
                            subjectId = subjectId,
                            parentTopicId = topicDraft.parentKey?.let(ids::getValue),
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
                    )
                    inserted += topicDraft.key
                    importedTopics++
                }
                pending.removeAll(ready.toSet())
            }
        }
        return SyllabusImportResult(draft.subjects.size, importedTopics)
    }

    fun purgePlan(planId: StudyPlanId) {
        mutate { current ->
            current.copy(
                targets = current.targets.filterNot { it.studyPlanId == planId },
                examStages = current.examStages.filterNot { it.studyPlanId == planId },
                subjects = current.subjects.filterNot { it.studyPlanId == planId },
                topics = current.topics.filterNot { it.studyPlanId == planId },
            )
        }
    }

    fun currentState(): DesktopStudyContentState = state.value

    fun currentSyllabus(planId: StudyPlanId): StudySyllabus = StudySyllabus(
        studyPlanId = planId,
        subjects = state.value.subjects.filter { it.studyPlanId == planId }.sortedBy(Subject::position),
        topics = state.value.topics.filter { it.studyPlanId == planId },
        examStages = state.value.examStages.filter { it.studyPlanId == planId }.sortedBy(ExamStage::position),
    )

    private suspend fun recalculateProgress(planId: StudyPlanId) {
        val current = state.value
        val topics = current.topics.filter { it.studyPlanId == planId }
        val subjects = current.subjects.filter { it.studyPlanId == planId }
        val updated = subjects.map { subject ->
            val progress = StudyProgressCalculator.subjectProgress(subject, topics)
            normalizeSubject(subject.copy(progressPercent = progress))
        }
        mutate { snapshot ->
            snapshot.copy(subjects = snapshot.subjects.replacePlanRows(planId, updated) { it.studyPlanId })
        }
        val plan = plans.currentPlans().firstOrNull { it.id == planId } ?: return
        plans.replace(plan.copy(progressPercent = StudyProgressCalculator.planProgress(updated)))
    }

    private fun normalizeSiblingPositions(planId: StudyPlanId) {
        val all = state.value.topics.filter { it.studyPlanId == planId }
        val normalized = all.groupBy { it.subjectId to it.parentTopicId }.values.flatMap { siblings ->
            siblings.sortedBy(Topic::position).mapIndexed { index, topic -> topic.copy(position = index) }
        }
        mutate { current ->
            current.copy(topics = current.topics.replacePlanRows(planId, normalized) { it.studyPlanId })
        }
    }

    private fun requireMutablePlan(planId: StudyPlanId) {
        val plan = requireNotNull(plans.currentPlans().firstOrNull { it.id == planId }) {
            "Study Plan ${planId.value} does not exist."
        }
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED)) {
            "Archived or trashed Study Plans must be restored before editing content."
        }
    }

    private fun validateStage(planId: StudyPlanId, stageId: String?) {
        if (stageId != null) {
            require(state.value.examStages.any { it.studyPlanId == planId && it.id.value == stageId }) {
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
        require(parent.studyPlanId == topic.studyPlanId && parent.subjectId == topic.subjectId) {
            "Parent topic must belong to the same Study Plan and subject."
        }
        val byId = allTopics.associateBy(Topic::id)
        var cursor: Topic? = parent
        while (cursor != null) {
            require(cursor.id != topic.id) { "Moving this topic would create a cycle." }
            cursor = cursor.parentTopicId?.let(byId::get)
        }
    }

    private fun descendantsOf(topicId: TopicId, all: List<Topic>): Set<TopicId> {
        val byParent = all.groupBy(Topic::parentTopicId)
        val result = mutableSetOf<TopicId>()
        fun visit(parent: TopicId) {
            byParent[parent].orEmpty().forEach { child -> if (result.add(child.id)) visit(child.id) }
        }
        visit(topicId)
        return result
    }

    private fun normalizeSubject(subject: Subject): Subject = if (subject.archived) {
        subject.copy(status = ContentProgressStatus.ARCHIVED)
    } else {
        subject.copy(
            archived = false,
            status = when {
                subject.status == ContentProgressStatus.ARCHIVED && subject.progressPercent >= 100 ->
                    ContentProgressStatus.MASTERED
                subject.status == ContentProgressStatus.ARCHIVED && subject.progressPercent > 0 ->
                    ContentProgressStatus.STUDYING
                subject.status == ContentProgressStatus.ARCHIVED -> ContentProgressStatus.NOT_STARTED
                subject.progressPercent >= 100 -> ContentProgressStatus.MASTERED
                else -> subject.status
            },
        )
    }

    private fun normalizeTopic(topic: Topic): Topic = if (topic.archived) {
        topic.copy(status = ContentProgressStatus.ARCHIVED)
    } else {
        topic.copy(
            archived = false,
            status = when {
                topic.status == ContentProgressStatus.ARCHIVED && topic.progressPercent >= 100 ->
                    ContentProgressStatus.MASTERED
                topic.status == ContentProgressStatus.ARCHIVED && topic.progressPercent > 0 ->
                    ContentProgressStatus.STUDYING
                topic.status == ContentProgressStatus.ARCHIVED -> ContentProgressStatus.NOT_STARTED
                topic.progressPercent >= 100 -> ContentProgressStatus.MASTERED
                else -> topic.status
            },
        )
    }

    private fun currentSubject(planId: StudyPlanId, subjectId: SubjectId): Subject = requireNotNull(
        state.value.subjects.firstOrNull { it.studyPlanId == planId && it.id == subjectId },
    )

    private fun currentTopic(planId: StudyPlanId, topicId: TopicId): Topic = requireNotNull(
        state.value.topics.firstOrNull { it.studyPlanId == planId && it.id == topicId },
    )

    private fun mutate(transform: (DesktopStudyContentState) -> DesktopStudyContentState) {
        state.value = transform(state.value)
        onChanged(state.value)
    }

    private fun <T, K> List<T>.upsertBy(value: T, key: (T) -> K): List<T> = filterNot { key(it) == key(value) } + value

    private fun <T, K> reorder(rows: List<T>, orderedIds: List<K>, id: (T) -> K): List<T> {
        require(orderedIds.size == rows.size && orderedIds.toSet() == rows.map(id).toSet()) {
            "Reorder must contain every sibling exactly once."
        }
        val byId = rows.associateBy(id)
        return orderedIds.map(byId::getValue)
    }

    private fun <T> List<T>.replacePlanRows(
        planId: StudyPlanId,
        replacements: List<T>,
        owner: (T) -> StudyPlanId,
    ): List<T> = filterNot { owner(it) == planId } + replacements
}
