package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyProgressRepository
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomStudyProgressRepository(
    private val database: StudyFlowDatabase,
) : StudyProgressRepository {
    private val planningDao = database.studyPlanningDao()
    private val supportDao = database.studySupportDao()
    private val planDao = database.studyPlanDao()

    override fun observeGoals(planId: StudyPlanId): Flow<List<StudyGoal>> =
        planningDao.observeStudyGoals(planId.value).map { goals -> goals.map { it.asExternalModel() } }

    override fun observeNotes(planId: StudyPlanId): Flow<List<StudyNote>> =
        supportDao.observeNotes(planId.value).map { notes -> notes.map { it.asExternalModel() } }

    override suspend fun findGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal? =
        planningDao.findStudyGoal(planId.value, goalId.value)?.asExternalModel()

    override suspend fun findNote(planId: StudyPlanId, noteId: StudyNoteId): StudyNote? =
        supportDao.findNote(planId.value, noteId.value)?.asExternalModel()

    override suspend fun upsertGoal(goal: StudyGoal): StudyGoal = database.withTransaction {
        requirePlan(goal.studyPlanId)
        goal.subjectId?.let { subjectId -> requireSubject(goal.studyPlanId, subjectId.value) }
        goal.topicId?.let { topicId -> requireTopic(goal.studyPlanId, topicId.value) }
        planningDao.upsertStudyGoal(goal.asEntity())
        requireNotNull(planningDao.findStudyGoal(goal.studyPlanId.value, goal.id.value)).asExternalModel()
    }

    override suspend fun upsertNote(note: StudyNote): StudyNote = database.withTransaction {
        requirePlan(note.studyPlanId)
        note.subjectId?.let { subjectId -> requireSubject(note.studyPlanId, subjectId.value) }
        note.topicId?.let { topicId -> requireTopic(note.studyPlanId, topicId.value) }
        note.materialId?.let { materialId -> requireMaterial(note.studyPlanId, materialId.value) }
        supportDao.upsertNote(note.asEntity())
        requireNotNull(supportDao.findNote(note.studyPlanId.value, note.id.value)).asExternalModel()
    }

    override suspend fun deleteGoal(planId: StudyPlanId, goalId: StudyGoalId) {
        database.withTransaction {
            require(planningDao.deleteStudyGoal(planId.value, goalId.value) == 1) {
                "Goal does not belong to this Study Plan."
            }
        }
    }

    override suspend fun deleteNote(planId: StudyPlanId, noteId: StudyNoteId) {
        database.withTransaction {
            require(supportDao.deleteNote(planId.value, noteId.value) == 1) {
                "Note does not belong to this Study Plan."
            }
        }
    }

    private suspend fun requirePlan(planId: StudyPlanId) {
        requireNotNull(planDao.findById(planId.value)) { "Study Plan does not exist." }
    }

    private suspend fun requireSubject(planId: StudyPlanId, subjectId: String) {
        requireNotNull(database.studyStructureDao().findSubject(planId.value, subjectId)) {
            "The selected subject does not belong to this Study Plan."
        }
    }

    private suspend fun requireTopic(planId: StudyPlanId, topicId: String) {
        requireNotNull(database.studyStructureDao().findTopic(planId.value, topicId)) {
            "The selected topic does not belong to this Study Plan."
        }
    }

    private suspend fun requireMaterial(planId: StudyPlanId, materialId: String) {
        requireNotNull(database.studyLibraryDao().findMaterial(planId.value, materialId)) {
            "The selected material does not belong to this Study Plan."
        }
    }
}
