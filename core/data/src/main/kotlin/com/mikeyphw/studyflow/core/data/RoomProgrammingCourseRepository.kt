package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.ProgrammingCourseRepository
import com.mikeyphw.studyflow.core.domain.ProgrammingCourseSnapshot
import com.mikeyphw.studyflow.core.domain.StudyFlowProgrammingImportPreview
import com.mikeyphw.studyflow.core.model.CodeSnippet
import com.mikeyphw.studyflow.core.model.CodeSnippetId
import com.mikeyphw.studyflow.core.model.CodingExercise
import com.mikeyphw.studyflow.core.model.CodingExerciseId
import com.mikeyphw.studyflow.core.model.CodingExerciseStatus
import com.mikeyphw.studyflow.core.model.CodingExerciseType
import com.mikeyphw.studyflow.core.model.MiniProject
import com.mikeyphw.studyflow.core.model.MiniProjectCheckpoint
import com.mikeyphw.studyflow.core.model.MiniProjectId
import com.mikeyphw.studyflow.core.model.MiniProjectStatus
import com.mikeyphw.studyflow.core.model.ProgrammingCourseLevel
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfileId
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.StudyFlowImportRowKind
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.TermuxRuntimePreference
import com.mikeyphw.studyflow.core.model.toTermuxExecutionPlan
import com.mikeyphw.studyflow.core.model.TermuxWorkspace
import com.mikeyphw.studyflow.core.model.TermuxExecutionState
import com.mikeyphw.studyflow.core.model.TermuxExerciseRunMode
import com.mikeyphw.studyflow.core.model.TermuxCommandResult
import com.mikeyphw.studyflow.core.model.TermuxCommandRequest
import com.mikeyphw.studyflow.core.model.TermuxBridgeAction
import com.mikeyphw.studyflow.core.model.applyTermuxResult
import com.mikeyphw.studyflow.core.model.defaultTermuxTemplates
import com.mikeyphw.studyflow.core.model.sanitizeTermuxWorkspaceSlug
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomProgrammingCourseRepository(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProgrammingCourseRepository {
    private val dao = database.programmingCourseDao()
    private val planDao = database.studyPlanDao()

    override fun observeSnapshot(planId: StudyPlanId): Flow<ProgrammingCourseSnapshot> = combine(
        dao.observeProfile(planId.value).map { entity -> entity?.asExternalModel() },
        dao.observeCodeSnippets(planId.value).map { entities -> entities.map { it.asExternalModel() } },
        dao.observeCodingExercises(planId.value).map { entities -> entities.map { it.asExternalModel() } },
        dao.observeMiniProjects(planId.value).map { entities -> entities.map { it.asExternalModel() } },
        dao.observeTermuxRuntimePreference(planId.value).map { entity -> entity?.asExternalModel() },
    ) { profile, snippets, exercises, projects, preference ->
        ProgrammingCourseSnapshot(
            studyPlanId = planId,
            profile = profile,
            codeSnippets = snippets,
            codingExercises = exercises,
            miniProjects = projects,
            termuxRuntimePreference = preference ?: TermuxRuntimePreference.disabled(planId),
        )
    }

    override suspend fun saveProfile(profile: ProgrammingCourseProfile): ProgrammingCourseProfile =
        database.withTransaction {
            requirePlan(profile.studyPlanId)
            dao.upsertProfile(profile.asEntity())
            profile
        }

    override suspend fun saveCodeSnippet(snippet: CodeSnippet): CodeSnippet = database.withTransaction {
        requirePlan(snippet.studyPlanId)
        dao.upsertCodeSnippet(snippet.asEntity())
        snippet
    }

    override suspend fun saveCodingExercise(exercise: CodingExercise): CodingExercise = database.withTransaction {
        requirePlan(exercise.studyPlanId)
        val normalized = exercise.withExplicitCommandTemplates()
        dao.upsertCodingExercise(normalized.asEntity())
        normalized
    }

    override suspend fun saveMiniProject(project: MiniProject): MiniProject = database.withTransaction {
        requirePlan(project.studyPlanId)
        val normalized = project.withExplicitCommandTemplates()
        dao.upsertMiniProject(normalized.asEntity())
        normalized
    }

    override suspend fun saveTermuxRuntimePreference(
        preference: TermuxRuntimePreference,
    ): TermuxRuntimePreference = database.withTransaction {
        requirePlan(preference.studyPlanId)
        dao.upsertTermuxRuntimePreference(preference.asEntity())
        preference
    }

    override suspend fun enqueueTermuxExerciseRun(
        planId: StudyPlanId,
        exerciseId: CodingExerciseId,
        runMode: TermuxExerciseRunMode,
        requestIdPrefix: String,
    ): TermuxCommandRequest = database.withTransaction {
        requirePlan(planId)
        require(requestIdPrefix == sanitizeTermuxWorkspaceSlug(requestIdPrefix)) {
            "Termux request id prefix must be a collision-resistant safe slug."
        }
        val exercise = requireNotNull(
            dao.findCodingExercise(planId.value, exerciseId.value)?.asExternalModel(),
        ) { "Coding exercise ${exerciseId.value} does not belong to plan ${planId.value}." }
        val preference = requireNotNull(dao.findTermuxRuntimePreference(planId.value)?.asExternalModel()) {
            "Termux runtime must be enabled and configured before running coding exercises."
        }
        require(preference.enabled) { "Termux runtime is disabled for this programming course." }
        val profile = dao.findProfile(planId.value)?.asExternalModel()
        val workspace = TermuxWorkspace(
            root = preference.workspaceRoot,
            courseSlug = sanitizeTermuxWorkspaceSlug(planId.value),
            relativePath = profile?.workspaceRelativePath ?: "programming/${sanitizeTermuxWorkspaceSlug(planId.value)}",
        )
        val plan = exercise.toTermuxExecutionPlan(
            workspace = workspace,
            runMode = runMode,
            requestIdPrefix = requestIdPrefix,
            createdAtEpochMillis = clock().coerceAtLeast(0L),
        )
        plan.requests.forEach { request -> dao.upsertTermuxCommandRequest(request.asEntity()) }
        plan.requests.single { request -> request.action == TermuxBridgeAction.RUN_COMMAND }
    }

    override suspend fun markTermuxRequestWaitingForTermux(requestId: String): TermuxCommandRequest =
        database.withTransaction {
            val request = requireNotNull(dao.findTermuxCommandRequest(requestId)?.asExternalModel()) {
                "Termux request $requestId was not durably queued."
            }
            require(request.action == TermuxBridgeAction.RUN_COMMAND) {
                "Only RUN_COMMAND requests can be sent to Termux."
            }
            require(request.state == TermuxExecutionState.QUEUED || request.state == TermuxExecutionState.RUNNING) {
                "Termux request $requestId is already terminal or in-flight."
            }
            dao.updateTermuxCommandRequestState(request.id, TermuxExecutionState.WAITING_FOR_TERMUX.name)
            request.withState(TermuxExecutionState.WAITING_FOR_TERMUX)
        }

    override suspend fun recordTermuxCommandResult(result: TermuxCommandResult): CodingExercise = database.withTransaction {
        val request = requireNotNull(dao.findTermuxCommandRequest(result.requestId)?.asExternalModel()) {
            "Uncorrelated Termux result ${result.requestId} cannot mutate a coding exercise."
        }
        require(request.action == TermuxBridgeAction.RUN_COMMAND) {
            "Only RUN_COMMAND results can update coding exercises."
        }
        val exerciseId = requireNotNull(request.exerciseId) {
            "Termux RUN_COMMAND result ${request.id} has no exercise correlation."
        }
        val exercise = requireNotNull(
            dao.findCodingExercise(request.studyPlanId.value, exerciseId.value)?.asExternalModel(),
        ) { "Coding exercise ${exerciseId.value} was removed before Termux returned." }
        require(result.requestId == request.id) {
            "Termux result request id must match the durable request row exactly."
        }
        dao.upsertTermuxCommandResult(result.asEntity())
        dao.updateTermuxCommandRequestState(request.id, result.state.name)
        val updated = exercise.applyTermuxResult(result, clock().coerceAtLeast(result.completedAtEpochMillis))
        dao.upsertCodingExercise(updated.asEntity())
        updated
    }

    override suspend fun applyImportPreview(
        planId: StudyPlanId,
        preview: StudyFlowProgrammingImportPreview,
    ): ProgrammingCourseSnapshot = database.withTransaction {
        requirePlan(planId)
        val now = clock().coerceAtLeast(0L)
        val profile = dao.findProfile(planId.value)?.asExternalModel()
            ?: defaultProgrammingProfile(planId, preview, now).also { dao.upsertProfile(it.asEntity()) }
        val defaultLanguage = profile.primaryLanguage
        val existingPreference = dao.findTermuxRuntimePreference(planId.value)?.asExternalModel()
        if (existingPreference == null) {
            val defaultTemplates = defaultTermuxTemplates(defaultLanguage).map { it.commandLine }
            dao.upsertTermuxRuntimePreference(
                TermuxRuntimePreference(
                    studyPlanId = planId,
                    enabled = false,
                    workspaceRoot = "~/studyflow/courses",
                    preferredLanguage = defaultLanguage,
                    defaultRunCommand = defaultTemplates.firstOrNull(),
                    defaultTestCommand = defaultTemplates.drop(1).firstOrNull() ?: defaultTemplates.firstOrNull(),
                    allowedCommandTemplates = defaultTemplates,
                    captureStdout = true,
                    captureStderr = true,
                    updatedAtEpochMillis = now,
                ).asEntity(),
            )
        }
        preview.sourcePreview.rows.forEach { row ->
            val language = ProgrammingLanguage.fromNameOrExtension(row.raw["language"] ?: row.raw["Language"])
                .takeUnless { it == ProgrammingLanguage.OTHER } ?: defaultLanguage
            when (row.kind) {
                StudyFlowImportRowKind.PROGRAMMING_EXERCISE -> dao.upsertCodingExercise(
                    importedExercise(planId, row.title, row.summary, row.raw, row.tags, language, now).asEntity(),
                )
                StudyFlowImportRowKind.PROGRAMMING_PROJECT -> dao.upsertMiniProject(
                    importedProject(planId, row.title, row.summary, row.raw, row.tags, language, now).asEntity(),
                )
                else -> error("Programming import preview contained ${row.kind}.")
            }
        }
        snapshot(planId)
    }

    override suspend fun deleteCodeSnippet(planId: StudyPlanId, snippetId: CodeSnippetId) {
        database.withTransaction { dao.deleteCodeSnippet(planId.value, snippetId.value) }
    }

    override suspend fun deleteCodingExercise(planId: StudyPlanId, exerciseId: CodingExerciseId) {
        database.withTransaction { dao.deleteCodingExercise(planId.value, exerciseId.value) }
    }

    override suspend fun deleteMiniProject(planId: StudyPlanId, projectId: MiniProjectId) {
        database.withTransaction { dao.deleteMiniProject(planId.value, projectId.value) }
    }

    private suspend fun snapshot(planId: StudyPlanId): ProgrammingCourseSnapshot = ProgrammingCourseSnapshot(
        studyPlanId = planId,
        profile = dao.findProfile(planId.value)?.asExternalModel(),
        codeSnippets = dao.findCodeSnippets(planId.value).map { it.asExternalModel() },
        codingExercises = dao.findCodingExercises(planId.value).map { it.asExternalModel() },
        miniProjects = dao.findMiniProjects(planId.value).map { it.asExternalModel() },
        termuxRuntimePreference = dao.findTermuxRuntimePreference(planId.value)?.asExternalModel()
            ?: TermuxRuntimePreference.disabled(planId),
    )

    private suspend fun requirePlan(planId: StudyPlanId) {
        require(planDao.countById(planId.value) == 1) { "Study plan ${planId.value} does not exist." }
    }

    private fun defaultProgrammingProfile(
        planId: StudyPlanId,
        preview: StudyFlowProgrammingImportPreview,
        now: Long,
    ): ProgrammingCourseProfile {
        val language = preview.sourcePreview.rows.firstNotNullOfOrNull { row ->
            ProgrammingLanguage.fromNameOrExtension(row.raw["language"] ?: row.raw["Language"]).takeUnless {
                it == ProgrammingLanguage.OTHER
            }
        } ?: ProgrammingLanguage.PYTHON
        return ProgrammingCourseProfile(
            id = ProgrammingCourseProfileId(UUID.randomUUID().toString()),
            studyPlanId = planId,
            primaryLanguage = language,
            level = ProgrammingCourseLevel.MIXED,
            repositoryUri = null,
            workspaceRelativePath = "programming/${sanitizeTermuxWorkspaceSlug(planId.value)}",
            buildSystem = null,
            packageManager = null,
            tags = listOf("imported"),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

    private fun importedExercise(
        planId: StudyPlanId,
        title: String,
        summary: String,
        raw: Map<String, String>,
        tags: List<String>,
        language: ProgrammingLanguage,
        now: Long,
    ): CodingExercise = CodingExercise(
        id = CodingExerciseId(UUID.randomUUID().toString()),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        snippetId = null,
        type = CodingExerciseType.IMPLEMENTATION_TASK,
        title = title,
        prompt = raw["prompt"].orEmpty().ifBlank { summary.ifBlank { title } },
        starterCode = raw["starter_code"].orEmpty().ifBlank { raw["code"].orEmpty().ifBlank { defaultStarterCode(language) } },
        expectedOutput = raw["expected_output"],
        hints = raw["hints"].orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() },
        solutionNotes = raw["solution"].orEmpty(),
        language = language,
        difficulty = raw["difficulty"].orEmpty().toIntOrNull()?.coerceIn(1, 5) ?: 2,
        status = CodingExerciseStatus.NOT_STARTED,
        runCommandTemplate = firstTemplate(language),
        testCommandTemplate = secondTemplate(language),
        files = emptyList(),
        tags = tags,
        lastResultJson = null,
        retryAtEpochMillis = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun importedProject(
        planId: StudyPlanId,
        title: String,
        summary: String,
        raw: Map<String, String>,
        tags: List<String>,
        language: ProgrammingLanguage,
        now: Long,
    ): MiniProject = MiniProject(
        id = MiniProjectId(UUID.randomUUID().toString()),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        title = title,
        description = raw["prompt"].orEmpty().ifBlank { summary.ifBlank { title } },
        status = MiniProjectStatus.NOT_STARTED,
        language = language,
        repositoryUri = raw["repository"],
        workspaceRelativePath = "projects/${sanitizeTermuxWorkspaceSlug(title)}",
        checkpoints = listOf(MiniProjectCheckpoint("Implement", "Create the requested project behavior.", completed = false)),
        deliverables = tags.ifEmpty { listOf("working project") },
        runCommandTemplate = firstTemplate(language),
        testCommandTemplate = secondTemplate(language),
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun CodingExercise.withExplicitCommandTemplates(): CodingExercise {
        val run = runCommandTemplate?.takeIf { it.isNotBlank() } ?: firstTemplate(language)
        val test = testCommandTemplate?.takeIf { it.isNotBlank() } ?: secondTemplate(language)
        return copy(runCommandTemplate = run, testCommandTemplate = test)
    }

    private fun MiniProject.withExplicitCommandTemplates(): MiniProject {
        val run = runCommandTemplate?.takeIf { it.isNotBlank() } ?: firstTemplate(language)
        val test = testCommandTemplate?.takeIf { it.isNotBlank() } ?: secondTemplate(language)
        return copy(runCommandTemplate = run, testCommandTemplate = test)
    }

    private fun firstTemplate(language: ProgrammingLanguage): String = defaultTermuxTemplates(language)
        .firstOrNull()?.commandLine ?: "python --version"

    private fun secondTemplate(language: ProgrammingLanguage): String = defaultTermuxTemplates(language)
        .drop(1).firstOrNull()?.commandLine ?: firstTemplate(language)

    private fun defaultStarterCode(language: ProgrammingLanguage): String = when (language) {
        ProgrammingLanguage.KOTLIN -> "fun main() = println(\"StudyFlow\")"
        ProgrammingLanguage.JAVA -> "class Main { public static void main(String[] args) { System.out.println(\"StudyFlow\"); } }"
        ProgrammingLanguage.JAVASCRIPT,
        ProgrammingLanguage.TYPESCRIPT,
        -> "console.log('StudyFlow')"
        ProgrammingLanguage.SHELL -> "echo StudyFlow"
        else -> "print('StudyFlow')"
    }
}
