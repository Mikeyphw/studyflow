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
import java.time.ZoneId
import kotlinx.coroutines.flow.combine

class ManageStudyProgress(
    private val repository: StudyProgressRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun observeGoals(planId: StudyPlanId): Flow<List<StudyGoal>> = repository.observeGoals(planId)
    fun observeNotes(planId: StudyPlanId): Flow<List<StudyNote>> = repository.observeNotes(planId)

    fun observeSnapshot(planId: StudyPlanId): Flow<StudyProgressSnapshot> = combine(
        combine(repository.observeGoals(planId), repository.observeNotes(planId)) { goals, notes -> goals to notes },
        repository.observeGoalEvidence(planId),
        tickingClockFlow(clock),
    ) { (goals, notes), evidence, now ->
        val evaluated = goals.map { goal -> StudyGoalProgressEngine.evaluate(goal, evidence, now, zoneId) }
        StudyProgressSnapshot(evaluated, notes)
    }

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
        val normalizedStart = startsOnIso.trim()
        val normalizedEnd = endsOnIso?.trim()?.ifBlank { null }
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
                startsOnIso = normalizedStart,
                endsOnIso = normalizedEnd,
                completedAtEpochMillis = null,
                archived = false,
            ),
        )
    }

    suspend fun updateGoalProgress(planId: StudyPlanId, goalId: StudyGoalId, currentValue: Double): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        require(goal.type == GoalType.CUSTOM) {
            "Evidence-backed goals cannot be manually overwritten; only CUSTOM goals accept manual progress."
        }
        require(currentValue >= 0.0) { "Goal progress cannot be negative." }
        val completedAt = if (currentValue >= goal.targetValue) clock() else null
        return repository.upsertGoal(goal.copy(currentValue = currentValue, completedAtEpochMillis = completedAt))
    }

    suspend fun completeGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        require(goal.type == GoalType.CUSTOM) {
            "Evidence-backed goals complete from evidence; only CUSTOM goals can be completed manually."
        }
        return repository.upsertGoal(
            goal.copy(
                currentValue = goal.targetValue,
                completedAtEpochMillis = goal.completedAtEpochMillis ?: clock(),
                archived = false,
            ),
        )
    }

    suspend fun archiveGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        return repository.upsertGoal(goal.copy(archived = true))
    }

    suspend fun restoreGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal {
        val goal = requireNotNull(repository.findGoal(planId, goalId)) { "Goal does not exist." }
        return repository.upsertGoal(goal.copy(archived = false))
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
