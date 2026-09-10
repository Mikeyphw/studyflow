package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ManageStudyContent
import com.mikeyphw.studyflow.core.domain.ManageStudyPlan
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.util.UUID

data class DesktopPlanStructureState(
    val plans: List<StudyPlan>,
    val selectedPlan: StudyPlan?,
    val subjects: List<Subject>,
    val topics: List<Topic>,
)

class DesktopPlanContentController(
    private val planRepository: InMemoryStudyPlanRepository,
    private val contentRepository: DesktopStudyContentRepository,
    private val preferencesStore: DesktopPreferencesStore,
    private val managePlan: ManageStudyPlan,
    private val manageContent: ManageStudyContent,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun state(): DesktopPlanStructureState {
        val plans = planRepository.currentPlans().sortedByDescending(StudyPlan::updatedAtEpochMillis)
        val selectedId = preferencesStore.current().defaultStudyPlanId ?: plans.firstOrNull()?.id
        val selected = plans.firstOrNull { it.id == selectedId } ?: plans.firstOrNull()
        val syllabus = selected?.id?.let(contentRepository::currentSyllabus)
        return DesktopPlanStructureState(
            plans = plans,
            selectedPlan = selected,
            subjects = syllabus?.subjects.orEmpty(),
            topics = syllabus?.topics.orEmpty(),
        )
    }

    suspend fun createPlan(name: String): StudyPlan {
        val now = clock()
        val id = StudyPlanId(idFactory())
        val plan = StudyPlan(
            id = id,
            name = name.trim(),
            description = "",
            type = StudyType.FREE_STUDY,
            status = StudyPlanStatus.ACTIVE,
            examDateIso = null,
            weeklyGoalHours = 10,
            progressPercent = 0,
            studiedMinutesThisWeek = 0,
            questionsAnswered = 0,
            averageAccuracyPercent = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        planRepository.create(plan, StudyPlanSettings.defaults(id))
        preferencesStore.setDefaultPlan(id)
        return plan
    }

    fun selectPlan(planId: StudyPlanId) {
        require(planRepository.currentPlans().any { it.id == planId }) { "Study Plan does not exist." }
        preferencesStore.setDefaultPlan(planId)
    }

    suspend fun renameSelected(name: String): StudyPlan {
        val selected = requireNotNull(state().selectedPlan) { "No Study Plan is selected." }
        val updated = selected.copy(name = name.trim(), updatedAtEpochMillis = clock())
        managePlan.update(updated)
        return updated
    }

    suspend fun archiveSelected() {
        val selected = requireNotNull(state().selectedPlan) { "No Study Plan is selected." }
        managePlan.archive(selected.id)
    }

    suspend fun restoreSelected() {
        val selected = requireNotNull(state().selectedPlan) { "No Study Plan is selected." }
        when (selected.status) {
            StudyPlanStatus.ARCHIVED -> managePlan.restoreArchived(selected.id)
            StudyPlanStatus.TRASHED -> managePlan.restoreFromTrash(selected.id)
            else -> Unit
        }
    }

    suspend fun trashSelected() {
        val selected = requireNotNull(state().selectedPlan) { "No Study Plan is selected." }
        managePlan.moveToTrash(selected.id)
    }

    suspend fun deleteSelectedPermanently() {
        val selected = requireNotNull(state().selectedPlan) { "No Study Plan is selected." }
        contentRepository.purgePlan(selected.id)
        managePlan.deletePermanently(selected.id)
        preferencesStore.setDefaultPlan(planRepository.currentPlans().firstOrNull()?.id)
    }

    suspend fun createSubject(name: String): Subject {
        val selected = requireNotNull(state().selectedPlan) { "Create or select a Study Plan first." }
        val position = contentRepository.currentSyllabus(selected.id).subjects.size
        return manageContent.saveSubject(
            Subject(
                id = SubjectId(idFactory()),
                studyPlanId = selected.id,
                name = name.trim(),
                description = "",
                colorArgb = null,
                priority = 3,
                examWeight = 1.0,
                estimatedMinutes = 0,
                progressPercent = 0,
                accuracyPercent = 0,
                status = ContentProgressStatus.NOT_STARTED,
                position = position,
                archived = false,
            ),
        )
    }

    suspend fun archiveSubject(subjectId: SubjectId) {
        val selected = requireNotNull(state().selectedPlan)
        val subject = contentRepository.currentSyllabus(selected.id).subjects.first { it.id == subjectId }
        manageContent.archiveSubject(subject)
    }

    suspend fun restoreSubject(subjectId: SubjectId) {
        val selected = requireNotNull(state().selectedPlan)
        val subject = contentRepository.currentSyllabus(selected.id).subjects.first { it.id == subjectId }
        manageContent.restoreSubject(subject)
    }

    suspend fun deleteSubject(subjectId: SubjectId) {
        val selected = requireNotNull(state().selectedPlan)
        manageContent.deleteSubject(selected.id, subjectId)
    }

    suspend fun createTopic(subjectId: SubjectId, name: String): Topic {
        val selected = requireNotNull(state().selectedPlan) { "Create or select a Study Plan first." }
        val syllabus = contentRepository.currentSyllabus(selected.id)
        require(syllabus.subjects.any { it.id == subjectId && !it.archived }) { "Select an active Subject first." }
        val position = syllabus.topics.count { it.subjectId == subjectId && it.parentTopicId == null }
        return manageContent.saveTopic(
            Topic(
                id = TopicId(idFactory()),
                studyPlanId = selected.id,
                subjectId = subjectId,
                parentTopicId = null,
                name = name.trim(),
                description = "",
                priority = 3,
                difficulty = 3,
                examIncidencePercent = null,
                estimatedMinutes = 0,
                progressPercent = 0,
                masteryPercent = 0,
                status = ContentProgressStatus.NOT_STARTED,
                position = position,
                archived = false,
            ),
        )
    }

    suspend fun archiveTopic(topicId: TopicId) {
        val selected = requireNotNull(state().selectedPlan)
        val topic = contentRepository.currentSyllabus(selected.id).topics.first { it.id == topicId }
        manageContent.archiveTopic(topic)
    }

    suspend fun restoreTopic(topicId: TopicId) {
        val selected = requireNotNull(state().selectedPlan)
        val topic = contentRepository.currentSyllabus(selected.id).topics.first { it.id == topicId }
        manageContent.restoreTopic(topic)
    }

    suspend fun deleteTopic(topicId: TopicId) {
        val selected = requireNotNull(state().selectedPlan)
        manageContent.deleteTopic(selected.id, topicId)
    }
}
