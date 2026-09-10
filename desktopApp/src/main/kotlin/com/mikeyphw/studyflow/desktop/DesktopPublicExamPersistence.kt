package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ExamNoticeDiffKind
import com.mikeyphw.studyflow.core.domain.ExamNoticeImportPreview
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPlanner
import com.mikeyphw.studyflow.core.domain.ExamNoticeRevisionPreview
import com.mikeyphw.studyflow.core.domain.LegislationImportPreview
import com.mikeyphw.studyflow.core.domain.PublicExamRepository
import com.mikeyphw.studyflow.core.domain.StudyCycleAction
import com.mikeyphw.studyflow.core.domain.StudyCyclePlanner
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.LegislationDocument
import com.mikeyphw.studyflow.core.model.LegislationDocumentId
import com.mikeyphw.studyflow.core.model.LegislationEvidenceLink
import com.mikeyphw.studyflow.core.model.LegislationEvidenceLinkId
import com.mikeyphw.studyflow.core.model.LegislationProvision
import com.mikeyphw.studyflow.core.model.LegislationProvisionId
import com.mikeyphw.studyflow.core.model.LegislationStudySnapshot
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyCycle
import com.mikeyphw.studyflow.core.model.StudyCycleId
import com.mikeyphw.studyflow.core.model.StudyCycleItem
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private const val PublicExamSchemaVersion = 1
private const val PublicExamFileName = "public-exam-v1.properties"
private const val PublicExamNull = "~"
private const val PublicExamRecordSeparator = '\u001e'
private const val PublicExamFieldSeparator = '\u001f'

data class DesktopPublicExamSnapshot(
    val revisions: List<ExamNoticeRevision> = emptyList(),
    val legislationDocuments: List<LegislationDocument> = emptyList(),
    val legislationProvisions: List<LegislationProvision> = emptyList(),
    val legislationEvidenceLinks: List<LegislationEvidenceLink> = emptyList(),
    val studyCycles: List<StudyCycle> = emptyList(),
    val studyCycleItems: List<StudyCycleItem> = emptyList(),
    val sessionBindings: Map<StudySessionId, StudyCycleSessionBinding> = emptyMap(),
)

class DesktopPublicExamPersistence private constructor(
    val root: Path,
    private val file: Path,
    private var snapshot: DesktopPublicExamSnapshot,
) {
    fun current(): DesktopPublicExamSnapshot = snapshot

    @Synchronized
    fun update(next: DesktopPublicExamSnapshot) {
        writeAtomically(next)
        snapshot = next
    }

    private fun writeAtomically(value: DesktopPublicExamSnapshot) {
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".studyflow-public-exam-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { DesktopPublicExamPropertiesCodec.write(it, value) }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun open(root: Path): DesktopPublicExamPersistence {
            val normalized = root.toAbsolutePath().normalize()
            Files.createDirectories(normalized)
            val file = normalized.resolve(PublicExamFileName)
            val initial = if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(DesktopPublicExamPropertiesCodec::read)
            } else {
                DesktopPublicExamSnapshot()
            }
            return DesktopPublicExamPersistence(normalized, file, initial).also { persistence ->
                if (!Files.isRegularFile(file)) persistence.writeAtomically(initial)
            }
        }
    }
}

class DesktopPublicExamRepository(
    initial: DesktopPublicExamSnapshot = DesktopPublicExamSnapshot(),
    private val plans: InMemoryStudyPlanRepository,
    private val content: DesktopStudyContentRepository,
    private val practice: InMemoryStudyPracticeRepository,
    private val review: InMemoryStudyReviewRepository,
    private val onChanged: (DesktopPublicExamSnapshot) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : PublicExamRepository {
    private val state = MutableStateFlow(initial)

    fun current(): DesktopPublicExamSnapshot = state.value

    fun importPortableStructure(
        planId: StudyPlanId,
        documents: List<LegislationDocument>,
        provisions: List<LegislationProvision>,
        cycles: List<StudyCycle>,
        cycleItems: List<StudyCycleItem>,
    ) {
        require(documents.all { it.studyPlanId == planId }) { "Legislation documents belong to another Study Plan." }
        require(provisions.all { it.studyPlanId == planId }) { "Legislation provisions belong to another Study Plan." }
        require(cycles.all { it.studyPlanId == planId }) { "Study cycles belong to another Study Plan." }
        require(cycleItems.all { it.studyPlanId == planId }) { "Study cycle items belong to another Study Plan." }
        val documentIds = documents.mapTo(linkedSetOf(), LegislationDocument::id)
        require(provisions.all { it.legislationDocumentId in documentIds }) {
            "Portable legislation provision references a missing document."
        }
        val cycleIds = cycles.mapTo(linkedSetOf(), StudyCycle::id)
        require(cycleItems.all { it.studyCycleId in cycleIds }) {
            "Portable study-cycle item references a missing cycle."
        }
        mutate { snapshot ->
            snapshot.copy(
                legislationDocuments = snapshot.legislationDocuments.filterNot { it.studyPlanId == planId } + documents,
                legislationProvisions = snapshot.legislationProvisions.filterNot { it.studyPlanId == planId } + provisions,
                studyCycles = snapshot.studyCycles.filterNot { it.studyPlanId == planId } + cycles,
                studyCycleItems = snapshot.studyCycleItems.filterNot { it.studyPlanId == planId } + cycleItems,
            )
        }
    }

    override fun observeExamNoticeRevisions(planId: StudyPlanId): Flow<List<ExamNoticeRevision>> =
        state.map { snapshot ->
            snapshot.revisions
                .filter { it.studyPlanId == planId }
                .sortedByDescending { it.importedAtEpochMillis }
        }

    override suspend fun prepareExamNoticeRevision(
        planId: StudyPlanId,
        importPreview: ExamNoticeImportPreview,
        existing: StudySyllabus,
        publicationDateIso: String?,
    ): ExamNoticeRevisionPreview {
        require(existing.studyPlanId == planId) { "Existing syllabus belongs to another Study Plan." }
        val target = requireNotNull(content.currentState().targets.firstOrNull { it.studyPlanId == planId }) {
            "A public examination Study Target must be saved before importing an edital revision."
        }
        return ExamNoticeRevisionPlanner.preview(
            planId = planId,
            targetId = target.id,
            baseRevisionId = latestRevision(planId)?.id,
            importPreview = importPreview,
            existing = existing,
            publicationDateIso = publicationDateIso,
        )
    }

    override suspend fun applyExamNoticeRevision(preview: ExamNoticeRevisionPreview): ExamNoticeRevision {
        requireMutablePlan(preview.studyPlanId)
        val target = content.currentState().targets.firstOrNull {
            it.studyPlanId == preview.studyPlanId && it.id == preview.studyTargetId
        }
        requireNotNull(target) { "Exam notice revision target does not belong to this Study Plan." }
        require(latestRevision(preview.studyPlanId)?.id == preview.baseRevisionId) {
            "A newer exam notice revision was accepted after this preview. Preview again before applying."
        }
        val current = content.currentSyllabus(preview.studyPlanId)
        require(ExamNoticeRevisionPlanner.canonicalSyllabusSha256(current) == preview.baseSyllabusSha256) {
            "The syllabus changed after this exam notice preview. Preview again before applying."
        }
        applyRevisionChanges(preview)
        val revision = ExamNoticeRevision(
            id = ExamNoticeRevisionId(idFactory()),
            studyPlanId = preview.studyPlanId,
            studyTargetId = preview.studyTargetId,
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
        mutate { it.copy(revisions = it.revisions + revision) }
        return revision
    }

    override fun observeLegislation(planId: StudyPlanId): Flow<LegislationStudySnapshot> = state.map { snapshot ->
        snapshot.legislation(planId)
    }

    override suspend fun applyLegislationImport(
        planId: StudyPlanId,
        preview: LegislationImportPreview,
        existingDocumentId: LegislationDocumentId?,
    ): LegislationStudySnapshot {
        requireMutablePlan(planId)
        val now = clock()
        val previousDocument = existingDocumentId?.let { id ->
            requireNotNull(state.value.legislationDocuments.firstOrNull { it.studyPlanId == planId && it.id == id }) {
                "Legislation document does not belong to this Study Plan."
            }
        }
        val documentId = previousDocument?.id ?: LegislationDocumentId(idFactory())
        val document = LegislationDocument(
            id = documentId,
            studyPlanId = planId,
            title = preview.title,
            sourceReference = preview.sourceReference,
            createdAtEpochMillis = previousDocument?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        val previousProvisions = state.value.legislationProvisions.filter {
            it.studyPlanId == planId && it.legislationDocumentId == documentId
        }
        val previousById = previousProvisions.associateBy { it.id }
        val provisions = preview.provisions.mapIndexed { position, draft ->
            val stableId = LegislationProvisionId(
                UUID.nameUUIDFromBytes("${documentId.value}:${draft.key}".toByteArray()).toString(),
            )
            val previous = previousById[stableId]
            LegislationProvision(
                id = stableId,
                legislationDocumentId = documentId,
                studyPlanId = planId,
                reference = draft.reference,
                parentReference = draft.parentReference,
                kind = draft.kind,
                text = draft.text,
                position = position,
                read = previous?.read ?: false,
                important = previous?.important ?: false,
                createdAtEpochMillis = previous?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
        }
        val retainedIds = provisions.mapTo(mutableSetOf()) { it.id }
        mutate { snapshot ->
            snapshot.copy(
                legislationDocuments = snapshot.legislationDocuments.upsert(document) { it.id == documentId },
                legislationProvisions = snapshot.legislationProvisions
                    .filterNot { it.studyPlanId == planId && it.legislationDocumentId == documentId } + provisions,
                legislationEvidenceLinks = snapshot.legislationEvidenceLinks.filterNot { link ->
                    link.studyPlanId == planId && link.provisionId !in retainedIds &&
                        previousProvisions.any { it.id == link.provisionId }
                },
            )
        }
        return state.value.legislation(planId)
    }

    override suspend fun setLegislationProvisionRead(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        read: Boolean,
    ): LegislationStudySnapshot = updateProvision(planId, provisionId) { it.copy(read = read) }

    override suspend fun setLegislationProvisionImportant(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        important: Boolean,
    ): LegislationStudySnapshot = updateProvision(planId, provisionId) { it.copy(important = important) }

    override suspend fun linkLegislationEvidence(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        questionId: QuestionId?,
        errorEntryId: ErrorEntryId?,
        reviewItemId: ReviewItemId?,
    ): LegislationEvidenceLink {
        require(listOf(questionId, errorEntryId, reviewItemId).count { it != null } == 1) {
            "Legislation evidence link requires exactly one target."
        }
        require(state.value.legislationProvisions.any { it.studyPlanId == planId && it.id == provisionId }) {
            "Legislation provision does not belong to this Study Plan."
        }
        questionId?.let { id ->
            require(practice.currentQuestions(planId).any { it.id == id }) {
                "Question evidence does not belong to this Study Plan."
            }
        }
        errorEntryId?.let { id ->
            require(review.currentErrors(planId).any { it.id == id }) {
                "Error evidence does not belong to this Study Plan."
            }
        }
        reviewItemId?.let { id ->
            require(review.currentReviews(planId).any { it.id == id }) {
                "Review evidence does not belong to this Study Plan."
            }
        }
        val link = LegislationEvidenceLink(
            id = LegislationEvidenceLinkId(idFactory()),
            studyPlanId = planId,
            provisionId = provisionId,
            questionId = questionId,
            errorEntryId = errorEntryId,
            reviewItemId = reviewItemId,
        )
        mutate { it.copy(legislationEvidenceLinks = it.legislationEvidenceLinks + link) }
        return link
    }

    override fun observeStudyCycle(planId: StudyPlanId): Flow<StudyCycleSnapshot?> = state.map { it.cycle(planId) }

    override suspend fun replaceStudyCycleFromSyllabus(
        planId: StudyPlanId,
        syllabus: StudySyllabus,
        name: String,
        targetDurationMinutes: Int,
    ): StudyCycleSnapshot {
        requireMutablePlan(planId)
        require(syllabus.studyPlanId == planId) { "Study cycle syllabus belongs to another Study Plan." }
        val activeSubjects = syllabus.subjects.filterNot(Subject::archived).sortedBy(Subject::position)
        require(activeSubjects.isNotEmpty()) { "At least one active subject is required to build a study cycle." }
        val previous = state.value.studyCycles.firstOrNull { it.studyPlanId == planId }
        val now = clock()
        val cycleId = previous?.id ?: StudyCycleId(idFactory())
        val items = activeSubjects.mapIndexed { index, subject ->
            StudyCycleItem(
                id = StudyCycleItemId(idFactory()),
                studyCycleId = cycleId,
                studyPlanId = planId,
                subjectId = subject.id,
                topicId = null,
                position = index,
                targetDurationMinutes = targetDurationMinutes,
                enabled = true,
                activityType = StudyActivityType.THEORY,
                completionRule = "SESSION_COMPLETED",
            )
        }
        val cycle = StudyCycle(
            id = cycleId,
            studyPlanId = planId,
            name = name,
            enabled = previous?.enabled ?: true,
            currentItemId = items.first().id,
            createdAtEpochMillis = previous?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        mutate { snapshot ->
            snapshot.copy(
                studyCycles = snapshot.studyCycles.filterNot { it.studyPlanId == planId } + cycle,
                studyCycleItems = snapshot.studyCycleItems.filterNot { it.studyPlanId == planId } + items,
            )
        }
        return StudyCycleSnapshot(cycle, items)
    }

    override suspend fun prepareNextStudyCycleAction(planId: StudyPlanId): StudyCycleAction? {
        val snapshot = state.value.cycle(planId) ?: return null
        if (!snapshot.cycle.enabled) return null
        val eligible = eligibleItemIds(snapshot)
        val current = StudyCyclePlanner.currentEligibleItem(snapshot, eligible)
        if (current == null) {
            updateCurrentCycleItem(planId, null)
            return null
        }
        if (current.id != snapshot.cycle.currentItemId) updateCurrentCycleItem(planId, current.id)
        val syllabus = content.currentSyllabus(planId)
        val subject = requireNotNull(syllabus.subjects.firstOrNull { it.id == current.subjectId && !it.archived }) {
            "Study cycle subject is no longer eligible."
        }
        val topic = current.topicId?.let { topicId ->
            requireNotNull(syllabus.topics.firstOrNull { it.id == topicId && !it.archived }) {
                "Study cycle topic is no longer eligible."
            }
        }
        return StudyCycleAction(
            binding = StudyCycleSessionBinding(snapshot.cycle.id, current.id),
            subjectId = subject.id,
            topicId = topic?.id,
            title = topic?.name ?: subject.name,
            targetDurationMinutes = current.targetDurationMinutes,
            activityType = current.activityType,
        )
    }

    override suspend fun skipCurrentStudyCycleItem(planId: StudyPlanId): StudyCycleSnapshot? {
        val snapshot = state.value.cycle(planId) ?: return null
        val next = StudyCyclePlanner.nextAfterSkip(snapshot, eligibleItemIds(snapshot))
        return updateCurrentCycleItem(planId, next?.id)
    }

    override suspend fun moveStudyCycleItemToEnd(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
    ): StudyCycleSnapshot {
        val snapshot = requireNotNull(state.value.cycle(planId)) { "Study cycle does not exist." }
        require(snapshot.items.any { it.id == itemId }) { "Study cycle item does not belong to this Study Plan." }
        val ordered = snapshot.orderedItems.filterNot { it.id == itemId } + snapshot.items.first { it.id == itemId }
        val normalized = ordered.mapIndexed { index, item -> item.copy(position = index) }
        mutate { current ->
            current.copy(
                studyCycleItems = current.studyCycleItems.filterNot { it.studyPlanId == planId } + normalized,
            )
        }
        return requireNotNull(state.value.cycle(planId))
    }

    override suspend fun setStudyCycleEnabled(planId: StudyPlanId, enabled: Boolean): StudyCycleSnapshot {
        val snapshot = requireNotNull(state.value.cycle(planId)) { "Study cycle does not exist." }
        val cycle = snapshot.cycle.copy(enabled = enabled, updatedAtEpochMillis = clock())
        mutate { it.copy(studyCycles = it.studyCycles.upsert(cycle) { row -> row.id == cycle.id }) }
        return requireNotNull(state.value.cycle(planId))
    }

    override suspend fun setStudyCycleItemEnabled(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
        enabled: Boolean,
    ): StudyCycleSnapshot {
        val snapshot = requireNotNull(state.value.cycle(planId)) { "Study cycle does not exist." }
        val target = requireNotNull(snapshot.items.firstOrNull { it.id == itemId }) {
            "Study cycle item does not belong to this Study Plan."
        }
        mutate { current ->
            current.copy(
                studyCycleItems = current.studyCycleItems.upsert(target.copy(enabled = enabled)) { it.id == itemId },
            )
        }
        val refreshed = requireNotNull(state.value.cycle(planId))
        val eligible = eligibleItemIds(refreshed)
        val currentId = refreshed.cycle.currentItemId?.takeIf { it in eligible }
            ?: refreshed.orderedItems.firstOrNull { it.id in eligible }?.id
        return requireNotNull(updateCurrentCycleItem(planId, currentId))
    }

    fun recordSessionBinding(sessionId: StudySessionId, binding: StudyCycleSessionBinding?) {
        mutate { snapshot ->
            snapshot.copy(sessionBindings = if (binding == null) snapshot.sessionBindings - sessionId else {
                snapshot.sessionBindings + (sessionId to binding)
            })
        }
    }

    fun onSessionTerminal(planId: StudyPlanId, sessionId: StudySessionId, status: StudySessionStatus) {
        val binding = state.value.sessionBindings[sessionId] ?: return
        if (status == StudySessionStatus.COMPLETED) {
            val snapshot = state.value.cycle(planId)
            if (snapshot != null && snapshot.cycle.id == binding.cycleId) {
                val next = StudyCyclePlanner.nextAfterCompletion(snapshot, binding.itemId, eligibleItemIds(snapshot))
                updateCurrentCycleItemSync(planId, next?.id)
            }
        }
        recordSessionBinding(sessionId, null)
    }

    fun purgePlan(planId: StudyPlanId) {
        val cycleIds = state.value.studyCycles.filter { it.studyPlanId == planId }.mapTo(mutableSetOf()) { it.id }
        val sessionIds = state.value.sessionBindings.filterValues { it.cycleId in cycleIds }.keys
        mutate { snapshot ->
            snapshot.copy(
                revisions = snapshot.revisions.filterNot { it.studyPlanId == planId },
                legislationDocuments = snapshot.legislationDocuments.filterNot { it.studyPlanId == planId },
                legislationProvisions = snapshot.legislationProvisions.filterNot { it.studyPlanId == planId },
                legislationEvidenceLinks = snapshot.legislationEvidenceLinks.filterNot { it.studyPlanId == planId },
                studyCycles = snapshot.studyCycles.filterNot { it.studyPlanId == planId },
                studyCycleItems = snapshot.studyCycleItems.filterNot { it.studyPlanId == planId },
                sessionBindings = snapshot.sessionBindings - sessionIds,
            )
        }
    }

    private suspend fun applyRevisionChanges(preview: ExamNoticeRevisionPreview) {
        val planId = preview.studyPlanId
        val incomingSubjects = linkedMapOf<String, SubjectId>()
        preview.changes.filter { it.existingTopicId == null }.forEach { change ->
            when (change.kind) {
                ExamNoticeDiffKind.UNCHANGED -> {
                    val incomingSubjectKey = change.incomingSubjectKey
                    val existingSubjectId = change.existingSubjectId
                    if (incomingSubjectKey != null && existingSubjectId != null) {
                        incomingSubjects[incomingSubjectKey] = existingSubjectId
                    }
                }
                ExamNoticeDiffKind.CHANGED -> {
                    val existing = requireNotNull(
                        content.currentState().subjects.firstOrNull { it.id == change.existingSubjectId },
                    )
                    content.upsertSubject(existing.copy(name = requireNotNull(change.afterName)))
                    change.incomingSubjectKey?.let { incomingSubjects[it] = existing.id }
                }
                ExamNoticeDiffKind.ADDED -> {
                    val draft = preview.draft.subjects.first { it.key == change.incomingSubjectKey }
                    val subject = Subject(
                        id = SubjectId(idFactory()), studyPlanId = planId, name = draft.name, description = "",
                        colorArgb = null, priority = draft.priority, examWeight = draft.examWeight,
                        estimatedMinutes = draft.estimatedMinutes, progressPercent = 0, accuracyPercent = 0,
                        status = ContentProgressStatus.NOT_STARTED,
                        position = content.currentSyllabus(planId).subjects.size, archived = false,
                    )
                    content.upsertSubject(subject)
                    incomingSubjects[draft.key] = subject.id
                }
                ExamNoticeDiffKind.REMOVED -> {
                    val existing = requireNotNull(
                        content.currentState().subjects.firstOrNull { it.id == change.existingSubjectId },
                    )
                    content.upsertSubject(existing.copy(archived = true, status = ContentProgressStatus.ARCHIVED))
                    content.currentState().topics.filter { it.subjectId == existing.id }.forEach { topic ->
                        content.upsertTopic(topic.copy(archived = true, status = ContentProgressStatus.ARCHIVED))
                    }
                }
            }
        }
        val incomingTopics = linkedMapOf<String, TopicId>()
        preview.changes.filter { it.incomingTopicKey != null && it.existingTopicId != null }.forEach { change ->
            incomingTopics[requireNotNull(change.incomingTopicKey)] = requireNotNull(change.existingTopicId)
            if (change.kind == ExamNoticeDiffKind.CHANGED) {
                val existing = requireNotNull(
                    content.currentState().topics.firstOrNull { it.id == change.existingTopicId },
                )
                content.upsertTopic(existing.copy(name = requireNotNull(change.afterName)))
            }
        }
        preview.changes
            .filter { it.kind == ExamNoticeDiffKind.REMOVED && it.existingTopicId != null }
            .forEach { change ->
            val existing = requireNotNull(content.currentState().topics.firstOrNull { it.id == change.existingTopicId })
            content.upsertTopic(existing.copy(archived = true, status = ContentProgressStatus.ARCHIVED))
        }
        preview.draft.subjects.forEach { subjectDraft ->
            val subjectId = requireNotNull(incomingSubjects[subjectDraft.key]) {
                "Accepted edital subject could not be resolved."
            }
            subjectDraft.topics.forEachIndexed { position, draft ->
                if (draft.key in incomingTopics) return@forEachIndexed
                val topic = Topic(
                    id = TopicId(idFactory()), studyPlanId = planId, subjectId = subjectId,
                    parentTopicId = draft.parentKey?.let { requireNotNull(incomingTopics[it]) },
                    name = draft.name, description = "", priority = draft.priority, difficulty = draft.difficulty,
                    examIncidencePercent = draft.examIncidencePercent, estimatedMinutes = draft.estimatedMinutes,
                    progressPercent = draft.progressPercent, masteryPercent = draft.masteryPercent,
                    status = draft.status, position = position, archived = false,
                )
                content.upsertTopic(topic)
                incomingTopics[draft.key] = topic.id
            }
        }
    }

    private suspend fun updateProvision(
        planId: StudyPlanId,
        provisionId: LegislationProvisionId,
        change: (LegislationProvision) -> LegislationProvision,
    ): LegislationStudySnapshot {
        requireMutablePlan(planId)
        val previous = requireNotNull(state.value.legislationProvisions.firstOrNull {
            it.studyPlanId == planId && it.id == provisionId
        }) { "Legislation provision does not belong to this Study Plan." }
        val updated = change(previous).copy(updatedAtEpochMillis = clock())
        mutate { snapshot ->
            snapshot.copy(
                legislationProvisions = snapshot.legislationProvisions.upsert(updated) { it.id == provisionId },
            )
        }
        return state.value.legislation(planId)
    }

    private fun eligibleItemIds(snapshot: StudyCycleSnapshot): Set<StudyCycleItemId> {
        val syllabus = content.currentSyllabus(snapshot.cycle.studyPlanId)
        val activeSubjects = syllabus.subjects.filterNot { it.archived }.mapTo(mutableSetOf()) { it.id }
        val activeTopics = syllabus.topics.filterNot { it.archived }.mapTo(mutableSetOf()) { it.id }
        return snapshot.items.filter { item ->
            item.enabled && item.subjectId in activeSubjects && (item.topicId == null || item.topicId in activeTopics)
        }.mapTo(mutableSetOf()) { it.id }
    }

    private suspend fun updateCurrentCycleItem(planId: StudyPlanId, itemId: StudyCycleItemId?): StudyCycleSnapshot? {
        updateCurrentCycleItemSync(planId, itemId)
        return state.value.cycle(planId)
    }

    private fun updateCurrentCycleItemSync(planId: StudyPlanId, itemId: StudyCycleItemId?) {
        val previous = state.value.studyCycles.firstOrNull { it.studyPlanId == planId } ?: return
        val cycle = previous.copy(currentItemId = itemId, updatedAtEpochMillis = clock())
        mutate { it.copy(studyCycles = it.studyCycles.upsert(cycle) { row -> row.id == cycle.id }) }
    }

    private fun latestRevision(planId: StudyPlanId): ExamNoticeRevision? = state.value.revisions
        .filter { it.studyPlanId == planId }
        .maxByOrNull { it.importedAtEpochMillis }

    private fun requireMutablePlan(planId: StudyPlanId) {
        val plan = requireNotNull(plans.currentPlans().firstOrNull { it.id == planId }) { "Study Plan does not exist." }
        require(plan.status.name !in setOf("ARCHIVED", "TRASHED", "COMPLETED")) {
            "Restore or reactivate the Study Plan before changing public-exam data."
        }
    }

    private fun mutate(transform: (DesktopPublicExamSnapshot) -> DesktopPublicExamSnapshot) {
        state.value = transform(state.value)
        onChanged(state.value)
    }

    private fun <T> List<T>.upsert(value: T, matches: (T) -> Boolean): List<T> =
        if (any(matches)) map { if (matches(it)) value else it } else this + value
}

private fun DesktopPublicExamSnapshot.legislation(planId: StudyPlanId) = LegislationStudySnapshot(
    studyPlanId = planId,
    documents = legislationDocuments.filter { it.studyPlanId == planId },
    provisions = legislationProvisions.filter { it.studyPlanId == planId }.sortedBy { it.position },
    evidenceLinks = legislationEvidenceLinks.filter { it.studyPlanId == planId },
)

private fun DesktopPublicExamSnapshot.cycle(planId: StudyPlanId): StudyCycleSnapshot? {
    val cycle = studyCycles.firstOrNull { it.studyPlanId == planId } ?: return null
    return StudyCycleSnapshot(cycle, studyCycleItems.filter { it.studyPlanId == planId && it.studyCycleId == cycle.id })
}

private object DesktopPublicExamPropertiesCodec {
    fun write(output: OutputStream, snapshot: DesktopPublicExamSnapshot) {
        val props = Properties()
        props["schema.version"] = PublicExamSchemaVersion.toString()
        writeRows(props, "revision", snapshot.revisions, ::encodeRevision)
        writeRows(props, "document", snapshot.legislationDocuments, ::encodeDocument)
        writeRows(props, "provision", snapshot.legislationProvisions, ::encodeProvision)
        writeRows(props, "evidence", snapshot.legislationEvidenceLinks, ::encodeEvidence)
        writeRows(props, "cycle", snapshot.studyCycles, ::encodeCycle)
        writeRows(props, "cycleItem", snapshot.studyCycleItems, ::encodeCycleItem)
        writeRows(props, "binding", snapshot.sessionBindings.entries.toList(), ::encodeBinding)
        props.store(output, "StudyFlow Desktop public exam schema v$PublicExamSchemaVersion")
    }

    fun read(input: InputStream): DesktopPublicExamSnapshot {
        val props = Properties().apply { load(input) }
        require(props.getProperty("schema.version")?.toIntOrNull() == PublicExamSchemaVersion) {
            "Unsupported Desktop public-exam schema."
        }
        return DesktopPublicExamSnapshot(
            revisions = readRows(props, "revision", ::decodeRevision),
            legislationDocuments = readRows(props, "document", ::decodeDocument),
            legislationProvisions = readRows(props, "provision", ::decodeProvision),
            legislationEvidenceLinks = readRows(props, "evidence", ::decodeEvidence),
            studyCycles = readRows(props, "cycle", ::decodeCycle),
            studyCycleItems = readRows(props, "cycleItem", ::decodeCycleItem),
            sessionBindings = readRows(props, "binding", ::decodeBinding).toMap(),
        )
    }

    private fun encodeRevision(value: ExamNoticeRevision) = fields(
        value.id.value,
        value.studyPlanId.value,
        value.studyTargetId.value,
        value.label,
        nullable(value.sourceReference),
        value.importedAtEpochMillis,
        value.normalizedContentSha256,
        value.parserVersion,
        nullable(value.publicationDateIso),
        value.addedCount, value.removedCount, value.changedCount, value.unchangedCount,
    )

    private fun decodeRevision(row: String): ExamNoticeRevision {
        val f = split(row)
        return ExamNoticeRevision(
            ExamNoticeRevisionId(f[0]), StudyPlanId(f[1]), com.mikeyphw.studyflow.core.model.StudyTargetId(f[2]), f[3],
            unnull(f[4]), f[5].toLong(), f[6], f[7], unnull(f[8]), f[9].toInt(), f[10].toInt(), f[11].toInt(),
            f[12].toInt(),
        )
    }

    private fun encodeDocument(value: LegislationDocument) = fields(
        value.id.value, value.studyPlanId.value, value.title, nullable(value.sourceReference),
        value.createdAtEpochMillis, value.updatedAtEpochMillis,
    )

    private fun decodeDocument(row: String): LegislationDocument {
        val f = split(row)
        return LegislationDocument(
            LegislationDocumentId(f[0]),
            StudyPlanId(f[1]),
            f[2],
            unnull(f[3]),
            f[4].toLong(),
            f[5].toLong(),
        )
    }

    private fun encodeProvision(value: LegislationProvision) = fields(
        value.id.value, value.legislationDocumentId.value, value.studyPlanId.value, value.reference,
        nullable(value.parentReference), value.kind.name, value.text, value.position, value.read, value.important,
        value.createdAtEpochMillis, value.updatedAtEpochMillis,
    )

    private fun decodeProvision(row: String): LegislationProvision {
        val f = split(row)
        return LegislationProvision(
            LegislationProvisionId(f[0]), LegislationDocumentId(f[1]), StudyPlanId(f[2]), f[3], unnull(f[4]),
            com.mikeyphw.studyflow.core.model.LegislationProvisionKind.valueOf(f[5]), f[6], f[7].toInt(),
            f[8].toBoolean(), f[9].toBoolean(), f[10].toLong(), f[11].toLong(),
        )
    }

    private fun encodeEvidence(value: LegislationEvidenceLink) = fields(
        value.id.value, value.studyPlanId.value, value.provisionId.value, nullable(value.questionId?.value),
        nullable(value.errorEntryId?.value), nullable(value.reviewItemId?.value),
    )

    private fun decodeEvidence(row: String): LegislationEvidenceLink {
        val f = split(row)
        return LegislationEvidenceLink(
            LegislationEvidenceLinkId(f[0]), StudyPlanId(f[1]), LegislationProvisionId(f[2]),
            unnull(f[3])?.let(::QuestionId), unnull(f[4])?.let(::ErrorEntryId), unnull(f[5])?.let(::ReviewItemId),
        )
    }

    private fun encodeCycle(value: StudyCycle) = fields(
        value.id.value, value.studyPlanId.value, value.name, value.enabled, nullable(value.currentItemId?.value),
        value.createdAtEpochMillis, value.updatedAtEpochMillis,
    )

    private fun decodeCycle(row: String): StudyCycle {
        val f = split(row)
        return StudyCycle(
            StudyCycleId(f[0]), StudyPlanId(f[1]), f[2], f[3].toBoolean(), unnull(f[4])?.let(::StudyCycleItemId),
            f[5].toLong(), f[6].toLong(),
        )
    }

    private fun encodeCycleItem(value: StudyCycleItem) = fields(
        value.id.value, value.studyCycleId.value, value.studyPlanId.value, nullable(value.subjectId?.value),
        nullable(value.topicId?.value), value.position, value.targetDurationMinutes, value.enabled,
        value.activityType.name, nullable(value.completionRule),
    )

    private fun decodeCycleItem(row: String): StudyCycleItem {
        val f = split(row)
        return StudyCycleItem(
            StudyCycleItemId(f[0]), StudyCycleId(f[1]), StudyPlanId(f[2]), unnull(f[3])?.let(::SubjectId),
            unnull(f[4])?.let(::TopicId), f[5].toInt(), f[6].toInt(), f[7].toBoolean(),
            StudyActivityType.valueOf(f[8]), unnull(f[9]),
        )
    }

    private fun encodeBinding(value: Map.Entry<StudySessionId, StudyCycleSessionBinding>) = fields(
        value.key.value, value.value.cycleId.value, value.value.itemId.value,
    )

    private fun decodeBinding(row: String): Pair<StudySessionId, StudyCycleSessionBinding> {
        val f = split(row)
        return StudySessionId(f[0]) to StudyCycleSessionBinding(StudyCycleId(f[1]), StudyCycleItemId(f[2]))
    }

    private fun fields(vararg values: Any): String = values.joinToString(PublicExamFieldSeparator.toString()) {
        encode(it.toString())
    }

    private fun split(row: String): List<String> = row.split(PublicExamFieldSeparator).map(::decode)
    private fun nullable(value: String?): String = value ?: PublicExamNull
    private fun unnull(value: String): String? = value.takeUnless { it == PublicExamNull }
    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private fun <T> writeRows(props: Properties, key: String, rows: List<T>, encoder: (T) -> String) {
        props[key] = rows.joinToString(PublicExamRecordSeparator.toString(), transform = encoder)
    }

    private fun <T> readRows(props: Properties, key: String, decoder: (String) -> T): List<T> =
        props.getProperty(key).orEmpty().split(PublicExamRecordSeparator).filter(String::isNotEmpty).map(decoder)
}
