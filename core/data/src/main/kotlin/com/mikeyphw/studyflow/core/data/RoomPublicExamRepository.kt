package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.ExamNoticeRevisionEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationProvisionEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationEvidenceLinkEntity
import com.mikeyphw.studyflow.core.database.entity.LegislationDocumentEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleItemEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.ExamNoticeDiffKind
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportPreview
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPlanner
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPreview
import com.mikeyphw.studyflow.core.domain.PublicExamRepository
import com.mikeyphw.studyflow.core.domain.StudyCycleAction
import com.mikeyphw.studyflow.core.domain.StudyCyclePlanner
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.LegislationDocument
import com.mikeyphw.studyflow.core.model.LegislationDocumentId
import com.mikeyphw.studyflow.core.model.LegislationEvidenceLink
import com.mikeyphw.studyflow.core.model.LegislationEvidenceLinkId
import com.mikeyphw.studyflow.core.model.LegislationProvision
import com.mikeyphw.studyflow.core.model.LegislationProvisionId
import com.mikeyphw.studyflow.core.model.LegislationProvisionKind
import com.mikeyphw.studyflow.core.model.LegislationStudySnapshot
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyCycle
import com.mikeyphw.studyflow.core.model.StudyCycleId
import com.mikeyphw.studyflow.core.model.StudyCycleItem
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomPublicExamRepository(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : PublicExamRepository {
    private val publicExamDao = database.publicExamDao()
    private val structureDao = database.studyStructureDao()
    private val planDao = database.studyPlanDao()
    private val practiceDao = database.studyPracticeDao()
    private val retentionDao = database.studyRetentionDao()

    override fun observeExamNoticeRevisions(planId: StudyPlanId): Flow<List<ExamNoticeRevision>> =
        publicExamDao.observeExamNoticeRevisions(planId.value).map { rows ->
            rows.map(ExamNoticeRevisionEntity::toModel)
        }

    override suspend fun prepareExamNoticeRevision(
        planId: StudyPlanId,
        importPreview: ExamNoticeImportPreview,
        existing: StudySyllabus,
        publicationDateIso: String?,
    ): ExamNoticeRevisionPreview = database.withTransaction {
        require(existing.studyPlanId == planId) { "Existing syllabus belongs to another Study Plan." }
        val target = requireNotNull(structureDao.findTargetForPlan(planId.value)) {
            "A public examination Study Target must be saved before importing an edital revision."
        }
        val latest = publicExamDao.findLatestExamNoticeRevision(planId.value)
        ExamNoticeRevisionPlanner.preview(
            planId = planId,
            targetId = StudyTargetId(target.id),
            baseRevisionId = latest?.id?.let(::ExamNoticeRevisionId),
            importPreview = importPreview,
            existing = existing,
            publicationDateIso = publicationDateIso,
        )
    }

    override suspend fun applyExamNoticeRevision(preview: ExamNoticeRevisionPreview): ExamNoticeRevision =
        database.withTransaction {
            val target = requireNotNull(
                structureDao.findTarget(preview.studyPlanId.value, preview.studyTargetId.value),
            ) {
                "Exam notice revision target does not belong to this Study Plan."
            }
            require(target.id == preview.studyTargetId.value) { "Exam notice revision target changed." }
            val latest = publicExamDao.findLatestExamNoticeRevision(preview.studyPlanId.value)
            require(latest?.id == preview.baseRevisionId?.value) {
                "A newer exam notice revision was accepted after this preview. Preview again before applying."
            }
            val current = loadSyllabus(preview.studyPlanId)
            require(ExamNoticeRevisionPlanner.canonicalSyllabusSha256(current) == preview.baseSyllabusSha256) {
                "The syllabus changed after this exam notice preview. Preview again before applying."
            }

            val subjectIdsByIncomingKey = linkedMapOf<String, String>()
            preview.changes.filter { change -> change.existingTopicId == null }.forEach { change ->
                val incomingKey = change.incomingSubjectKey
                when (change.kind) {
                    ExamNoticeDiffKind.UNCHANGED -> if (incomingKey != null && change.existingSubjectId != null) {
                        subjectIdsByIncomingKey[incomingKey] = change.existingSubjectId.value
                    }
                    ExamNoticeDiffKind.CHANGED -> {
                        val subjectId = requireNotNull(change.existingSubjectId)
                        val newName = requireNotNull(change.afterName).trim()
                        require(publicExamDao.renameSubject(preview.studyPlanId.value, subjectId.value, newName) == 1) {
                            "Changed edital subject no longer belongs to this Study Plan."
                        }
                        if (incomingKey != null) subjectIdsByIncomingKey[incomingKey] = subjectId.value
                    }
                    ExamNoticeDiffKind.ADDED -> {
                        val subjectDraft = preview.draft.subjects.first { subject -> subject.key == incomingKey }
                        val subjectId = UUID.randomUUID().toString()
                        structureDao.upsertSubject(
                            SubjectEntity(
                                id = subjectId,
                                studyPlanId = preview.studyPlanId.value,
                                name = subjectDraft.name.trim(),
                                description = "",
                                colorArgb = null,
                                priority = subjectDraft.priority,
                                examWeight = subjectDraft.examWeight,
                                estimatedMinutes = subjectDraft.estimatedMinutes,
                                progressPercent = 0,
                                accuracyPercent = 0,
                                status = ContentProgressStatus.NOT_STARTED.name,
                                position = preview.draft.subjects.indexOf(subjectDraft),
                                archived = false,
                                examStageId = null,
                            ),
                        )
                        subjectIdsByIncomingKey[subjectDraft.key] = subjectId
                    }
                    ExamNoticeDiffKind.REMOVED -> {
                        val subjectId = requireNotNull(change.existingSubjectId)
                        require(publicExamDao.archiveSubject(preview.studyPlanId.value, subjectId.value) == 1) {
                            "Removed edital subject no longer belongs to this Study Plan."
                        }
                        publicExamDao.archiveTopicsForSubject(preview.studyPlanId.value, subjectId.value)
                    }
                }
            }

            val topicIdsByIncomingKey = linkedMapOf<String, String>()
            preview.changes.filter { change -> change.incomingTopicKey != null && change.existingTopicId != null }
                .forEach { change ->
                    topicIdsByIncomingKey[requireNotNull(change.incomingTopicKey)] =
                        requireNotNull(change.existingTopicId).value
                    if (change.kind == ExamNoticeDiffKind.CHANGED) {
                        require(
                            publicExamDao.renameTopic(
                                preview.studyPlanId.value,
                                requireNotNull(change.existingTopicId).value,
                                requireNotNull(change.afterName).trim(),
                            ) == 1,
                        ) { "Changed edital topic no longer belongs to this Study Plan." }
                    }
                }
            preview.changes.filter { change ->
                change.kind == ExamNoticeDiffKind.REMOVED && change.existingTopicId != null
            }
                .forEach { change ->
                    require(
                        publicExamDao.archiveTopic(
                            preview.studyPlanId.value,
                            requireNotNull(change.existingTopicId).value,
                        ) == 1,
                    ) {
                        "Removed edital topic no longer belongs to this Study Plan."
                    }
                }

            preview.draft.subjects.forEach { subjectDraft ->
                val subjectId = requireNotNull(subjectIdsByIncomingKey[subjectDraft.key]) {
                    "Accepted edital subject could not be resolved."
                }
                subjectDraft.topics.forEachIndexed { topicIndex, topicDraft ->
                    if (topicDraft.key in topicIdsByIncomingKey) return@forEachIndexed
                    val topicId = UUID.randomUUID().toString()
                    val parentTopicId = topicDraft.parentKey?.let { parentKey ->
                        requireNotNull(topicIdsByIncomingKey[parentKey]) {
                            "Accepted edital topic parent could not be resolved."
                        }
                    }
                    structureDao.upsertTopic(
                        TopicEntity(
                            id = topicId,
                            studyPlanId = preview.studyPlanId.value,
                            subjectId = subjectId,
                            parentTopicId = parentTopicId,
                            name = topicDraft.name.trim(),
                            description = "",
                            priority = topicDraft.priority,
                            difficulty = topicDraft.difficulty,
                            examIncidencePercent = topicDraft.examIncidencePercent,
                            estimatedMinutes = topicDraft.estimatedMinutes,
                            progressPercent = topicDraft.progressPercent,
                            masteryPercent = topicDraft.masteryPercent,
                            status = topicDraft.status.name,
                            position = topicIndex,
                            archived = false,
                            examStageId = null,
                        ),
                    )
                    topicIdsByIncomingKey[topicDraft.key] = topicId
                }
            }

            val revision = ExamNoticeRevisionEntity(
                id = UUID.randomUUID().toString(),
                studyPlanId = preview.studyPlanId.value,
                studyTargetId = preview.studyTargetId.value,
                label = preview.label,
                sourceReference = preview.sourceReference,
                importedAtEpochMillis = clock(),
                normalizedContentSha256 = preview.normalizedContentSha256,
                parserVersion = preview.parserVersion,
                publicationDateIso = preview.publicationDateIso,
                addedCount = preview.addedCount,
                removedCount = preview.removedCount,
                changedCount = preview.changedCount,
                unchangedCount = preview.unchangedCount,
            )
            publicExamDao.insertExamNoticeRevision(revision)
            revision.toModel()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

    override fun observeLegislation(planId: StudyPlanId): Flow<LegislationStudySnapshot> = combine(
        publicExamDao.observeLegislationDocuments(planId.value),
        publicExamDao.observeLegislationProvisions(planId.value),
        publicExamDao.observeLegislationEvidenceLinks(planId.value),
    ) { documents, provisions, links ->
        LegislationStudySnapshot(
            studyPlanId = planId,
            documents = documents.map(LegislationDocumentEntity::toModel),
            provisions = provisions.map(LegislationProvisionEntity::toModel),
            evidenceLinks = links.map(LegislationEvidenceLinkEntity::toModel),
        )
    }

    override suspend fun applyLegislationImport(
        planId: StudyPlanId,
        preview: com.mikeyphw.studyflow.core.domain.LegislationImportPreview,
        existingDocumentId: LegislationDocumentId?,
    ): LegislationStudySnapshot = database.withTransaction {
        require(planDao.findById(planId.value) != null) { "Study Plan does not exist." }
        val now = clock()
        val existing = existingDocumentId?.let { documentId ->
            requireNotNull(publicExamDao.findLegislationDocument(planId.value, documentId.value)) {
                "Legislation document does not belong to this Study Plan."
            }
        }
        val documentId = existing?.id ?: UUID.randomUUID().toString()
        publicExamDao.upsertLegislationDocument(
            LegislationDocumentEntity(
                id = documentId,
                studyPlanId = planId.value,
                title = preview.title,
                sourceReference = preview.sourceReference,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
        val old = publicExamDao.findLegislationProvisions(planId.value, documentId)
        val oldById = old.associateBy { it.id }
        val rows = preview.provisions.mapIndexed { position, draft ->
            val stableId = UUID.nameUUIDFromBytes("$documentId:${draft.key}".toByteArray()).toString()
            val previous = oldById[stableId]
            LegislationProvisionEntity(
                id = stableId,
                legislationDocumentId = documentId,
                studyPlanId = planId.value,
                reference = draft.reference,
                parentReference = draft.parentReference,
                kind = draft.kind.name,
                text = draft.text,
                position = position,
                read = previous?.read ?: false,
                important = previous?.important ?: false,
                createdAtEpochMillis = previous?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
        }
        // Move existing positions out of the unique-index range before upserting reordered rows.
        publicExamDao.shiftLegislationProvisionPositions(planId.value, documentId, 1_000_000)
        publicExamDao.upsertLegislationProvisions(rows)
        val retainedIds = rows.mapTo(hashSetOf()) { it.id }
        old.filterNot { it.id in retainedIds }.forEach { removed ->
            publicExamDao.deleteLegislationProvision(planId.value, documentId, removed.id)
        }
        loadLegislationSnapshot(planId)
    }

    override suspend fun setLegislationProvisionRead(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        read: Boolean,
    ): LegislationStudySnapshot = database.withTransaction {
        require(publicExamDao.setLegislationProvisionRead(planId.value, provisionId.value, read, clock()) == 1) {
            "Legislation provision does not belong to this Study Plan."
        }
        loadLegislationSnapshot(planId)
    }

    override suspend fun setLegislationProvisionImportant(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        important: Boolean,
    ): LegislationStudySnapshot = database.withTransaction {
        require(
            publicExamDao.setLegislationProvisionImportant(
                planId.value,
                provisionId.value,
                important,
                clock(),
            ) == 1,
        ) {
            "Legislation provision does not belong to this Study Plan."
        }
        loadLegislationSnapshot(planId)
    }

    override suspend fun linkLegislationEvidence(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        questionId: QuestionId?,
        errorEntryId: ErrorEntryId?,
        reviewItemId: ReviewItemId?,
    ): LegislationEvidenceLink = database.withTransaction {
        require(listOf(questionId, errorEntryId, reviewItemId).count { it != null } == 1) {
            "Legislation evidence link requires exactly one target."
        }
        requireNotNull(publicExamDao.findLegislationProvision(planId.value, provisionId.value)) {
            "Legislation provision does not belong to this Study Plan."
        }
        questionId?.let { id ->
            requireNotNull(practiceDao.findQuestion(planId.value, id.value)) {
                "Question evidence does not belong to this Study Plan."
            }
        }
        errorEntryId?.let { id ->
            requireNotNull(retentionDao.findErrorEntry(planId.value, id.value)) {
                "Error evidence does not belong to this Study Plan."
            }
        }
        reviewItemId?.let { id ->
            requireNotNull(retentionDao.findReviewItem(planId.value, id.value)) {
                "Review evidence does not belong to this Study Plan."
            }
        }
        val row = LegislationEvidenceLinkEntity(
            id = UUID.randomUUID().toString(),
            studyPlanId = planId.value,
            provisionId = provisionId.value,
            questionId = questionId?.value,
            errorEntryId = errorEntryId?.value,
            reviewItemId = reviewItemId?.value,
        )
        publicExamDao.upsertLegislationEvidenceLink(row)
        row.toModel()
    }

    override fun observeStudyCycle(planId: StudyPlanId): Flow<StudyCycleSnapshot?> =
        publicExamDao.observeStudyCycle(planId.value).flatMapLatest { cycle ->
            if (cycle == null) {
                flowOf(null)
            } else {
                publicExamDao.observeStudyCycleItems(planId.value, cycle.id).map { items ->
                    StudyCycleSnapshot(cycle.toModel(), items.map(StudyCycleItemEntity::toModel))
                }
            }
        }

    override suspend fun replaceStudyCycleFromSyllabus(
        planId: StudyPlanId,
        syllabus: StudySyllabus,
        name: String,
        targetDurationMinutes: Int,
    ): StudyCycleSnapshot = database.withTransaction {
        require(syllabus.studyPlanId == planId) { "Study cycle syllabus belongs to another Study Plan." }
        val activeSubjects = syllabus.subjects
            .filterNot { subject -> subject.archived }
            .sortedBy { subject -> subject.position }
        require(activeSubjects.isNotEmpty()) { "At least one active subject is required to build a study cycle." }
        val existing = publicExamDao.findStudyCycle(planId.value)
        val now = clock()
        val cycleId = existing?.id ?: UUID.randomUUID().toString()
        val items = activeSubjects.mapIndexed { index, subject ->
            StudyCycleItemEntity(
                id = UUID.randomUUID().toString(),
                studyCycleId = cycleId,
                studyPlanId = planId.value,
                subjectId = subject.id.value,
                topicId = null,
                position = index,
                targetDurationMinutes = targetDurationMinutes,
                enabled = true,
                activityType = StudyActivityType.THEORY.name,
                completionRule = "SESSION_COMPLETED",
            )
        }
        publicExamDao.upsertStudyCycle(
            StudyCycleEntity(
                id = cycleId,
                studyPlanId = planId.value,
                name = name,
                enabled = existing?.enabled ?: true,
                currentItemId = items.first().id,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
        publicExamDao.deleteStudyCycleItems(planId.value, cycleId)
        publicExamDao.upsertStudyCycleItems(items)
        requireNotNull(loadCycleSnapshot(planId))
    }

    override suspend fun prepareNextStudyCycleAction(
        planId: StudyPlanId,
    ): StudyCycleAction? = database.withTransaction {
        val cycle = publicExamDao.findStudyCycle(planId.value) ?: return@withTransaction null
        if (!cycle.enabled) return@withTransaction null
        val eligible = publicExamDao.findEligibleStudyCycleItems(planId.value, cycle.id)
        if (eligible.isEmpty()) {
            publicExamDao.setCurrentStudyCycleItem(planId.value, cycle.id, null, clock())
            return@withTransaction null
        }
        val snapshot = StudyCycleSnapshot(
            cycle = cycle.toModel(),
            items = publicExamDao.findStudyCycleItems(planId.value, cycle.id).map(StudyCycleItemEntity::toModel),
        )
        val currentModel = requireNotNull(
            StudyCyclePlanner.currentEligibleItem(
                snapshot = snapshot,
                eligibleItemIds = eligible.map { StudyCycleItemId(it.id) }.toSet(),
            ),
        )
        val current = requireNotNull(eligible.firstOrNull { item -> item.id == currentModel.id.value })
        if (current.id != cycle.currentItemId) {
            require(publicExamDao.setCurrentStudyCycleItem(planId.value, cycle.id, current.id, clock()) == 1) {
                "Study cycle changed while resolving its current item."
            }
        }
        val subject = requireNotNull(
            current.subjectId?.let { subjectId -> structureDao.findSubject(planId.value, subjectId) },
        ) {
            "Study cycle subject is no longer eligible."
        }
        val topic = current.topicId?.let { topicId ->
            requireNotNull(structureDao.findTopic(planId.value, topicId)) { "Study cycle topic is no longer eligible." }
        }
        StudyCycleAction(
            binding = StudyCycleSessionBinding(StudyCycleId(cycle.id), StudyCycleItemId(current.id)),
            subjectId = SubjectId(subject.id),
            topicId = topic?.id?.let(::TopicId),
            title = topic?.name ?: subject.name,
            targetDurationMinutes = current.targetDurationMinutes,
            activityType = StudyActivityType.valueOf(current.activityType),
        )
    }

    override suspend fun skipCurrentStudyCycleItem(
        planId: StudyPlanId,
    ): StudyCycleSnapshot? = database.withTransaction {
        val cycle = publicExamDao.findStudyCycle(planId.value) ?: return@withTransaction null
        val eligible = publicExamDao.findEligibleStudyCycleItems(planId.value, cycle.id)
        if (eligible.isEmpty()) {
            publicExamDao.setCurrentStudyCycleItem(planId.value, cycle.id, null, clock())
            return@withTransaction loadCycleSnapshot(planId)
        }
        val snapshot = StudyCycleSnapshot(
            cycle = cycle.toModel(),
            items = publicExamDao.findStudyCycleItems(planId.value, cycle.id).map(StudyCycleItemEntity::toModel),
        )
        val next = requireNotNull(
            StudyCyclePlanner.nextAfterSkip(
                snapshot = snapshot,
                eligibleItemIds = eligible.map { StudyCycleItemId(it.id) }.toSet(),
            ),
        )
        require(publicExamDao.setCurrentStudyCycleItem(planId.value, cycle.id, next.id.value, clock()) == 1) {
            "Study cycle changed while skipping its current item."
        }
        loadCycleSnapshot(planId)
    }

    override suspend fun moveStudyCycleItemToEnd(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
    ): StudyCycleSnapshot = database.withTransaction {
        val cycle = requireNotNull(publicExamDao.findStudyCycle(planId.value)) { "Study cycle does not exist." }
        val items = publicExamDao.findStudyCycleItems(planId.value, cycle.id)
        val item = requireNotNull(items.firstOrNull { candidate -> candidate.id == itemId.value }) {
            "Study cycle item does not belong to this Study Plan."
        }
        val newPosition = (items.maxOfOrNull(StudyCycleItemEntity::position) ?: -1) + 1
        require(publicExamDao.updateStudyCycleItemPosition(planId.value, cycle.id, item.id, newPosition) == 1) {
            "Study cycle item changed before it could be moved."
        }
        loadCycleSnapshot(planId) ?: error("Study cycle disappeared after move-to-end.")
    }

    override suspend fun setStudyCycleEnabled(planId: StudyPlanId, enabled: Boolean): StudyCycleSnapshot =
        database.withTransaction {
            val cycle = requireNotNull(publicExamDao.findStudyCycle(planId.value)) { "Study cycle does not exist." }
            require(publicExamDao.setStudyCycleEnabled(planId.value, cycle.id, enabled, clock()) == 1) {
                "Study cycle changed before it could be updated."
            }
            loadCycleSnapshot(planId) ?: error("Study cycle disappeared after update.")
        }

    override suspend fun setStudyCycleItemEnabled(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
        enabled: Boolean,
    ): StudyCycleSnapshot = database.withTransaction {
        val cycle = requireNotNull(publicExamDao.findStudyCycle(planId.value)) { "Study cycle does not exist." }
        require(publicExamDao.setStudyCycleItemEnabled(planId.value, cycle.id, itemId.value, enabled) == 1) {
            "Study cycle item does not belong to this Study Plan."
        }
        val eligible = publicExamDao.findEligibleStudyCycleItems(planId.value, cycle.id)
        val normalizedCurrent =
            eligible.firstOrNull { item -> item.id == cycle.currentItemId } ?: eligible.firstOrNull()
        publicExamDao.setCurrentStudyCycleItem(planId.value, cycle.id, normalizedCurrent?.id, clock())
        loadCycleSnapshot(planId) ?: error("Study cycle disappeared after item update.")
    }

    private suspend fun loadCycleSnapshot(planId: StudyPlanId): StudyCycleSnapshot? {
        val cycle = publicExamDao.findStudyCycle(planId.value) ?: return null
        val items = publicExamDao.findStudyCycleItems(planId.value, cycle.id)
        return StudyCycleSnapshot(cycle.toModel(), items.map(StudyCycleItemEntity::toModel))
    }

    private suspend fun loadSyllabus(planId: StudyPlanId): StudySyllabus = StudySyllabus(
        studyPlanId = planId,
        subjects = structureDao.findSubjects(planId.value).map { entity -> entity.asExternalModel() },
        topics = structureDao.findTopics(planId.value).map { entity -> entity.asExternalModel() },
        examStages = structureDao.findExamStages(planId.value).map { entity -> entity.asExternalModel() },
    )
}

private fun ExamNoticeRevisionEntity.toModel(): ExamNoticeRevision = ExamNoticeRevision(
    id = ExamNoticeRevisionId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    studyTargetId = StudyTargetId(studyTargetId),
    label = label,
    sourceReference = sourceReference,
    importedAtEpochMillis = importedAtEpochMillis,
    normalizedContentSha256 = normalizedContentSha256,
    parserVersion = parserVersion,
    publicationDateIso = publicationDateIso,
    addedCount = addedCount,
    removedCount = removedCount,
    changedCount = changedCount,
    unchangedCount = unchangedCount,
)

private fun StudyCycleEntity.toModel(): StudyCycle = StudyCycle(
    id = StudyCycleId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    name = name,
    enabled = enabled,
    currentItemId = currentItemId?.let(::StudyCycleItemId),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun StudyCycleItemEntity.toModel(): StudyCycleItem = StudyCycleItem(
    id = StudyCycleItemId(id),
    studyCycleId = StudyCycleId(studyCycleId),
    studyPlanId = StudyPlanId(studyPlanId),
    subjectId = subjectId?.let(::SubjectId),
    topicId = topicId?.let(::TopicId),
    position = position,
    targetDurationMinutes = targetDurationMinutes,
    enabled = enabled,
    activityType = StudyActivityType.valueOf(activityType),
    completionRule = completionRule,
)


private fun LegislationDocumentEntity.toModel(): LegislationDocument = LegislationDocument(
    id = LegislationDocumentId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    title = title,
    sourceReference = sourceReference,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun LegislationProvisionEntity.toModel(): LegislationProvision = LegislationProvision(
    id = LegislationProvisionId(id),
    legislationDocumentId = LegislationDocumentId(legislationDocumentId),
    studyPlanId = StudyPlanId(studyPlanId),
    reference = reference,
    parentReference = parentReference,
    kind = LegislationProvisionKind.valueOf(kind),
    text = text,
    position = position,
    read = read,
    important = important,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun LegislationEvidenceLinkEntity.toModel(): LegislationEvidenceLink = LegislationEvidenceLink(
    id = LegislationEvidenceLinkId(id),
    studyPlanId = StudyPlanId(studyPlanId),
    provisionId = LegislationProvisionId(provisionId),
    questionId = questionId?.let(::QuestionId),
    errorEntryId = errorEntryId?.let(::ErrorEntryId),
    reviewItemId = reviewItemId?.let(::ReviewItemId),
)
