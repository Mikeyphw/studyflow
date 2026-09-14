package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.GoalEvidence
import com.mikeyphw.studyflow.core.domain.StudyGoalProgressEngine
import com.mikeyphw.studyflow.core.domain.StudyProgressRepository
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class DesktopStudyProgressRepository(
    private val root: Path? = null,
    private val evidenceProvider: (StudyPlanId) -> GoalEvidence = { emptyGoalEvidence() },
    private val clock: () -> Long = System::currentTimeMillis,
) : StudyProgressRepository {
    private val goals = MutableStateFlow<List<StudyGoal>>(emptyList())
    private val notes = MutableStateFlow<List<StudyNote>>(emptyList())

    init {
        root?.let(::load)
    }

    override fun observeGoals(planId: StudyPlanId): Flow<List<StudyGoal>> = goals.map { rows ->
        rows.filter { it.studyPlanId == planId }.map { goal ->
            StudyGoalProgressEngine.evaluate(goal, evidenceProvider(planId), clock())
        }
    }

    override fun observeNotes(planId: StudyPlanId): Flow<List<StudyNote>> = notes.map { rows ->
        rows.filter { it.studyPlanId == planId }
    }

    override fun observeGoalEvidence(planId: StudyPlanId): Flow<GoalEvidence> = goals.map { evidenceProvider(planId) }

    override suspend fun findGoal(planId: StudyPlanId, goalId: StudyGoalId): StudyGoal? =
        currentGoals(planId).firstOrNull { it.id == goalId }

    override suspend fun findNote(planId: StudyPlanId, noteId: StudyNoteId): StudyNote? =
        notes.value.firstOrNull { it.studyPlanId == planId && it.id == noteId }

    override suspend fun upsertGoal(goal: StudyGoal): StudyGoal {
        goals.value = goals.value.filterNot { it.id == goal.id } + goal
        persist()
        return goal
    }

    override suspend fun upsertNote(note: StudyNote): StudyNote {
        notes.value = notes.value.filterNot { it.id == note.id } + note
        persist()
        return note
    }

    override suspend fun deleteGoal(planId: StudyPlanId, goalId: StudyGoalId) {
        goals.value = goals.value.filterNot { it.studyPlanId == planId && it.id == goalId }
        persist()
    }

    override suspend fun deleteNote(planId: StudyPlanId, noteId: StudyNoteId) {
        notes.value = notes.value.filterNot { it.studyPlanId == planId && it.id == noteId }
        persist()
    }

    fun currentGoals(planId: StudyPlanId): List<StudyGoal> = goals.value
        .filter { it.studyPlanId == planId }
        .map { goal -> StudyGoalProgressEngine.evaluate(goal, evidenceProvider(planId), clock()) }

    fun currentNotes(planId: StudyPlanId): List<StudyNote> = notes.value.filter { it.studyPlanId == planId }

    fun reloadFromDisk() {
        goals.value = emptyList()
        notes.value = emptyList()
        root?.let(::load)
    }

    fun purgePlan(planId: StudyPlanId) {
        goals.value = goals.value.filterNot { it.studyPlanId == planId }
        notes.value = notes.value.filterNot { it.studyPlanId == planId }
        persist()
    }

    private fun load(workspaceRoot: Path) {
        val file = workspaceRoot.resolve(FILE_NAME)
        if (!Files.isRegularFile(file)) return
        val properties = Properties().apply { Files.newInputStream(file).use { input -> load(input) } }
        require(properties.getProperty("schema.version") == SCHEMA_VERSION.toString()) {
            "Unsupported Desktop progress schema."
        }
        goals.value = (0 until properties.getProperty("goal.count", "0").toInt()).map { index ->
            readGoal(properties, "goal.$index")
        }
        notes.value = (0 until properties.getProperty("note.count", "0").toInt()).map { index ->
            readNote(properties, "note.$index")
        }
    }

    private fun persist() {
        val workspaceRoot = root ?: return
        Files.createDirectories(workspaceRoot)
        val file = workspaceRoot.resolve(FILE_NAME)
        val temp = workspaceRoot.resolve("$FILE_NAME.tmp")
        val properties = Properties().apply {
            setProperty("schema.version", SCHEMA_VERSION.toString())
            setProperty("goal.count", goals.value.size.toString())
            goals.value.forEachIndexed { index, goal -> writeGoal(this, "goal.$index", goal) }
            setProperty("note.count", notes.value.size.toString())
            notes.value.forEachIndexed { index, note -> writeNote(this, "note.$index", note) }
        }
        Files.newOutputStream(temp).use { output -> properties.store(output, "StudyFlow Desktop progress") }
        runCatching {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val FILE_NAME = "progress-v1.properties"
        private const val SCHEMA_VERSION = 1

        private fun emptyGoalEvidence() = GoalEvidence(emptyList(), emptyList(), emptyList(), emptyList())

        private fun writeGoal(properties: Properties, prefix: String, goal: StudyGoal) = with(properties) {
            setProperty("$prefix.id", goal.id.value)
            setProperty("$prefix.plan", goal.studyPlanId.value)
            setProperty("$prefix.subject", goal.subjectId?.value.orEmpty())
            setProperty("$prefix.topic", goal.topicId?.value.orEmpty())
            setProperty("$prefix.type", goal.type.name)
            setProperty("$prefix.period", goal.period.name)
            setProperty("$prefix.title", goal.title)
            setProperty("$prefix.target", goal.targetValue.toString())
            setProperty("$prefix.current", goal.currentValue.toString())
            setProperty("$prefix.starts", goal.startsOnIso)
            setProperty("$prefix.ends", goal.endsOnIso.orEmpty())
            setProperty("$prefix.completed", goal.completedAtEpochMillis?.toString().orEmpty())
            setProperty("$prefix.archived", goal.archived.toString())
        }

        private fun readGoal(properties: Properties, prefix: String) = StudyGoal(
            id = StudyGoalId(properties.getProperty("$prefix.id")),
            studyPlanId = StudyPlanId(properties.getProperty("$prefix.plan")),
            subjectId = properties.getProperty("$prefix.subject").takeIf(String::isNotBlank)?.let(::SubjectId),
            topicId = properties.getProperty("$prefix.topic").takeIf(String::isNotBlank)?.let(::TopicId),
            type = GoalType.valueOf(properties.getProperty("$prefix.type")),
            period = GoalPeriod.valueOf(properties.getProperty("$prefix.period")),
            title = properties.getProperty("$prefix.title"),
            targetValue = properties.getProperty("$prefix.target").toDouble(),
            currentValue = properties.getProperty("$prefix.current").toDouble(),
            startsOnIso = properties.getProperty("$prefix.starts"),
            endsOnIso = properties.getProperty("$prefix.ends").takeIf(String::isNotBlank),
            completedAtEpochMillis = properties.getProperty("$prefix.completed").takeIf(String::isNotBlank)?.toLong(),
            archived = properties.getProperty("$prefix.archived").toBoolean(),
        )

        private fun writeNote(properties: Properties, prefix: String, note: StudyNote) = with(properties) {
            setProperty("$prefix.id", note.id.value)
            setProperty("$prefix.plan", note.studyPlanId.value)
            setProperty("$prefix.subject", note.subjectId?.value.orEmpty())
            setProperty("$prefix.topic", note.topicId?.value.orEmpty())
            setProperty("$prefix.material", note.materialId?.value.orEmpty())
            setProperty("$prefix.type", note.type.name)
            setProperty("$prefix.title", note.title)
            setProperty("$prefix.content", note.content)
            setProperty("$prefix.pinned", note.pinned.toString())
            setProperty("$prefix.tags", note.tags.joinToString("\u001f"))
            setProperty("$prefix.created", note.createdAtEpochMillis.toString())
            setProperty("$prefix.updated", note.updatedAtEpochMillis.toString())
        }

        private fun readNote(properties: Properties, prefix: String) = StudyNote(
            id = StudyNoteId(properties.getProperty("$prefix.id")),
            studyPlanId = StudyPlanId(properties.getProperty("$prefix.plan")),
            subjectId = properties.getProperty("$prefix.subject").takeIf(String::isNotBlank)?.let(::SubjectId),
            topicId = properties.getProperty("$prefix.topic").takeIf(String::isNotBlank)?.let(::TopicId),
            materialId = properties.getProperty("$prefix.material").takeIf(String::isNotBlank)?.let(::StudyMaterialId),
            type = StudyNoteType.valueOf(properties.getProperty("$prefix.type")),
            title = properties.getProperty("$prefix.title"),
            content = properties.getProperty("$prefix.content"),
            pinned = properties.getProperty("$prefix.pinned").toBoolean(),
            tags = properties.getProperty("$prefix.tags").split("\u001f").filter(String::isNotBlank),
            createdAtEpochMillis = properties.getProperty("$prefix.created").toLong(),
            updatedAtEpochMillis = properties.getProperty("$prefix.updated").toLong(),
        )
    }
}
