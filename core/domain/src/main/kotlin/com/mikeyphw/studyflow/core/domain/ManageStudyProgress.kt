package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.GoalPeriod
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyGoal
import com.mikeyphw.studyflow.core.model.StudyGoalId
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyNoteId
import com.mikeyphw.studyflow.core.model.StudyNoteType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageStudyProgress(
    private val repository: StudyProgressRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeGoals(planId: StudyPlanId): Flow<List<StudyGoal>> = repository.observeGoals(planId)
    fun observeNotes(planId: StudyPlanId): Flow<List<StudyNote>> = repository.observeNotes(planId)

    fun observeSnapshot(planId: StudyPlanId): Flow<StudyProgressSnapshot> = combine(
        repository.observeGoals(planId),
        repository.observeNotes(planId),
    ) { goals, notes -> StudyProgressSnapshot(goals, notes) }

    suspend fun createGoal(
        id: StudyGoalId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        type: GoalType,
        period: GoalPeriod,
        title: String,
        targetValue: Double,
        startsOnIso: String,
        endsOnIso: String?,
    ): StudyGoal {
        require(title.isNotBlank()) { "Goal title cannot be blank." }
        require(targetValue > 0.0) { "Goal target must be positive." }
        require(startsOnIso.isNotBlank()) { "Goal start date cannot be blank." }
        return repository.upsertGoal(
            StudyGoal(
                id = id,
                studyPlanId = studyPlanId,
                subjectId = subjectId,
                topicId = topicId,
                type = type,
                period = period,
                title = title.trim(),
                targetValue = targetValue,
                currentValue = 0.0,
                startsOnIso = startsOnIso,
                endsOnIso = endsOnIso?.trim()?.ifBlank { null },
                completedAtEpochMillis = null,
                archived = false,
            ),
        )
    }

    suspend fun updateGoalProgress(planId: StudyPlanId, goalId: StudyGoalId, currentValue: Double): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        require(currentValue >= 0.0) { "Goal progress cannot be negative." }
        val completedAt = if (currentValue >= goal.targetValue) clock() else null
        return repository.upsertGoal(goal.copy(currentValue = currentValue, completedAtEpochMillis = completedAt))
    }

    suspend fun archiveGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        return repository.upsertGoal(goal.copy(archived = true))
    }

    suspend fun createNote(
        id: StudyNoteId,
        studyPlanId: StudyPlanId,
        subjectId: SubjectId?,
        topicId: TopicId?,
        materialId: StudyMaterialId?,
        type: StudyNoteType,
        title: String,
        content: String,
        tags: List<String>,
    ): StudyNote {
        require(title.isNotBlank()) { "Note title cannot be blank." }
        require(content.isNotBlank()) { "Note content cannot be blank." }
        val now = clock()
        return repository.upsertNote(
            StudyNote(
                id = id,
                studyPlanId = studyPlanId,
                subjectId = subjectId,
                topicId = topicId,
                materialId = materialId,
                type = type,
                title = title.trim(),
                content = content.trim(),
                pinned = false,
                tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun togglePinnedNote(planId: StudyPlanId, noteId: StudyNoteId): StudyNote {
        val note = requireNotNull(repository.findNote(planId, noteId)) { "Note does not exist." }
        return repository.upsertNote(note.copy(pinned = !note.pinned, updatedAtEpochMillis = clock()))
    }

    suspend fun deleteGoal(planId: StudyPlanId, goalId: StudyGoalId) = repository.deleteGoal(planId, goalId)
    suspend fun deleteNote(planId: StudyPlanId, noteId: StudyNoteId) = repository.deleteNote(planId, noteId)
}
