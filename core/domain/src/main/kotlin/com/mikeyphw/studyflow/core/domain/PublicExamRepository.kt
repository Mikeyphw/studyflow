package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.StudyCycleItemId
import com.mikeyphw.studyflow.core.model.StudyCycleSnapshot
import com.mikeyphw.studyflow.core.model.StudyCycleSessionBinding
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.StudySessionId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class StudyCycleAction(
    val binding: StudyCycleSessionBinding,
    val subjectId: SubjectId,
    val topicId: TopicId?,
    val title: String,
    val targetDurationMinutes: Int,
    val activityType: com.mikeyphw.studyflow.core.model.StudyActivityType,
)

interface PublicExamRepository {
    fun observeExamNoticeRevisions(planId: StudyPlanId): Flow<List<ExamNoticeRevision>>

    suspend fun prepareExamNoticeRevision(
        planId: StudyPlanId,
        importPreview: ExamNoticeImportPreview,
        existing: StudySyllabus,
        publicationDateIso: String? = null,
    ): ExamNoticeRevisionPreview

    suspend fun applyExamNoticeRevision(preview: ExamNoticeRevisionPreview): ExamNoticeRevision

    fun observeStudyCycle(planId: StudyPlanId): Flow<StudyCycleSnapshot?>

    suspend fun replaceStudyCycleFromSyllabus(
        planId: StudyPlanId,
        syllabus: StudySyllabus,
        name: String,
        targetDurationMinutes: Int,
    ): StudyCycleSnapshot

    suspend fun prepareNextStudyCycleAction(planId: StudyPlanId): StudyCycleAction?

    suspend fun skipCurrentStudyCycleItem(planId: StudyPlanId): StudyCycleSnapshot?

    suspend fun moveStudyCycleItemToEnd(planId: StudyPlanId, itemId: StudyCycleItemId): StudyCycleSnapshot

    suspend fun setStudyCycleEnabled(planId: StudyPlanId, enabled: Boolean): StudyCycleSnapshot

    suspend fun setStudyCycleItemEnabled(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
        enabled: Boolean,
    ): StudyCycleSnapshot
}

class ManagePublicExam(
    private val repository: PublicExamRepository,
    private val sessionManager: ManageStudySession,
) {
    fun observeExamNoticeRevisions(planId: StudyPlanId): Flow<List<ExamNoticeRevision>> =
        repository.observeExamNoticeRevisions(planId)

    suspend fun prepareExamNoticeRevision(
        planId: StudyPlanId,
        importPreview: ExamNoticeImportPreview,
        existing: StudySyllabus,
        publicationDateIso: String? = null,
    ): ExamNoticeRevisionPreview = repository.prepareExamNoticeRevision(
        planId = planId,
        importPreview = importPreview,
        existing = existing,
        publicationDateIso = publicationDateIso,
    )

    suspend fun applyExamNoticeRevision(preview: ExamNoticeRevisionPreview): ExamNoticeRevision =
        repository.applyExamNoticeRevision(preview)

    fun observeStudyCycle(planId: StudyPlanId): Flow<StudyCycleSnapshot?> = repository.observeStudyCycle(planId)

    suspend fun nextStudyCycleAction(planId: StudyPlanId): StudyCycleAction? =
        repository.prepareNextStudyCycleAction(planId)

    suspend fun replaceStudyCycleFromSyllabus(
        planId: StudyPlanId,
        syllabus: StudySyllabus,
        name: String = "Public exam study cycle",
        targetDurationMinutes: Int = 45,
    ): StudyCycleSnapshot {
        require(name.isNotBlank()) { "Study cycle name cannot be blank." }
        require(targetDurationMinutes in 5..240) { "Study cycle duration must be between 5 and 240 minutes." }
        return repository.replaceStudyCycleFromSyllabus(planId, syllabus, name.trim(), targetDurationMinutes)
    }

    suspend fun startCurrentStudyCycleItem(planId: StudyPlanId): StudySession? {
        val action = repository.prepareNextStudyCycleAction(planId) ?: return null
        return sessionManager.startQuickSession(
            sessionId = StudySessionId(UUID.randomUUID().toString()),
            studyPlanId = planId,
            title = action.title,
            type = action.activityType,
            subjectId = action.subjectId,
            topicId = action.topicId,
            targetDurationMinutes = action.targetDurationMinutes,
            cycleBinding = action.binding,
        )
    }

    suspend fun skipCurrentStudyCycleItem(planId: StudyPlanId): StudyCycleSnapshot? =
        repository.skipCurrentStudyCycleItem(planId)

    suspend fun moveStudyCycleItemToEnd(planId: StudyPlanId, itemId: StudyCycleItemId): StudyCycleSnapshot =
        repository.moveStudyCycleItemToEnd(planId, itemId)

    suspend fun setStudyCycleEnabled(planId: StudyPlanId, enabled: Boolean): StudyCycleSnapshot =
        repository.setStudyCycleEnabled(planId, enabled)

    suspend fun setStudyCycleItemEnabled(
        planId: StudyPlanId,
        itemId: StudyCycleItemId,
        enabled: Boolean,
    ): StudyCycleSnapshot = repository.setStudyCycleItemEnabled(planId, itemId, enabled)
}
