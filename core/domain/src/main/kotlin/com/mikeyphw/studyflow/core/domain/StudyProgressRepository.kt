package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow

interface StudyProgressRepository {
    fun observeGoals(planId: StudyPlanId): Flow<List<StudyGoal>>
    fun observeNotes(planId: StudyPlanId): Flow<List<StudyNote>>
    suspend fun findGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal?
    suspend fun findNote(planId: StudyPlanId, noteId: StudyNoteId): StudyNote?
    suspend fun upsertGoal(goal: StudyGoal): StudyGoal
    suspend fun upsertNote(note: StudyNote): StudyNote
    suspend fun deleteGoal(planId: StudyPlanId, goalId: StudyGoalId)
    suspend fun deleteNote(planId: StudyPlanId, noteId: StudyNoteId)
}
