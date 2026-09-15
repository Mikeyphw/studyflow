package com.mikeyphw.studyflow.core.database.mapper

import android.util.Base64
import com.mikeyphw.studyflow.core.database.entity.CodeSnippetEntity
import com.mikeyphw.studyflow.core.database.entity.CodingExerciseEntity
import com.mikeyphw.studyflow.core.database.entity.MiniProjectEntity
import com.mikeyphw.studyflow.core.database.entity.ProgrammingCourseProfileEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxCommandRequestEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxCommandResultEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxRuntimePreferenceEntity
import com.mikeyphw.studyflow.core.model.CodeSnippet
import com.mikeyphw.studyflow.core.model.CodeSnippetId
import com.mikeyphw.studyflow.core.model.CodingExercise
import com.mikeyphw.studyflow.core.model.CodingExerciseFile
import com.mikeyphw.studyflow.core.model.CodingExerciseFileRole
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
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TermuxBridgeAction
import com.mikeyphw.studyflow.core.model.TermuxCommandRequest
import com.mikeyphw.studyflow.core.model.TermuxCommandResult
import com.mikeyphw.studyflow.core.model.TermuxCommandTemplate
import com.mikeyphw.studyflow.core.model.TermuxExecutionState
import com.mikeyphw.studyflow.core.model.TermuxRuntimePreference
import com.mikeyphw.studyflow.core.model.TermuxWorkspace
import com.mikeyphw.studyflow.core.model.TopicId
import java.nio.charset.StandardCharsets

private const val EncodedProgrammingPrefix = "b64:"
private const val FieldSeparator = "|"

private fun String.encodeField(): String = EncodedProgrammingPrefix + Base64.encodeToString(
    toByteArray(StandardCharsets.UTF_8),
    Base64.NO_WRAP,
)

private fun String.decodeField(field: String): String {
    val encoded = removePrefix(EncodedProgrammingPrefix)
    return try {
        String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw StudyFlowDatabaseCorruptionException("Invalid encoded $field.", error)
    }
}

private fun List<CodingExerciseFile>.encodeExerciseFilesStable(): String = joinToString("\n") { file ->
    listOf(file.role.name, file.relativePath.encodeField(), file.contents.encodeField()).joinToString(FieldSeparator)
}

private fun String.decodeExerciseFilesStable(field: String): List<CodingExerciseFile> {
    if (isEmpty()) return emptyList()
    return lineSequence().mapIndexed { index, raw ->
        val parts = raw.split(FieldSeparator)
        if (parts.size != 3) {
            throw StudyFlowDatabaseCorruptionException("Invalid coding exercise file row $index in $field.")
        }
        CodingExerciseFile(
            role = parts[0].decodeEnum("$field[$index].role"),
            relativePath = parts[1].decodeField("$field[$index].relativePath"),
            contents = parts[2].decodeField("$field[$index].contents"),
        )
    }.toList()
}

private fun List<MiniProjectCheckpoint>.encodeCheckpointsStable(): String = joinToString("\n") { checkpoint ->
    listOf(checkpoint.title.encodeField(), checkpoint.description.encodeField(), checkpoint.completed.toString())
        .joinToString(FieldSeparator)
}

private fun String.decodeCheckpointsStable(field: String): List<MiniProjectCheckpoint> {
    if (isEmpty()) return emptyList()
    return lineSequence().mapIndexed { index, raw ->
        val parts = raw.split(FieldSeparator)
        if (parts.size != 3) {
            throw StudyFlowDatabaseCorruptionException("Invalid mini project checkpoint row $index in $field.")
        }
        MiniProjectCheckpoint(
            title = parts[0].decodeField("$field[$index].title"),
            description = parts[1].decodeField("$field[$index].description"),
            completed = parts[2].toBooleanStrictOrNull()
                ?: throw StudyFlowDatabaseCorruptionException("Invalid checkpoint boolean in $field[$index].completed."),
        )
    }.toList()
}

fun ProgrammingCourseProfileEntity.asExternalModel(): ProgrammingCourseProfile = decodeModel("ProgrammingCourseProfileEntity") {
    ProgrammingCourseProfile(
        id = ProgrammingCourseProfileId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        primaryLanguage = primaryLanguage.decodeEnum("ProgrammingCourseProfileEntity.primaryLanguage"),
        level = level.decodeEnum("ProgrammingCourseProfileEntity.level"),
        repositoryUri = repositoryUri,
        workspaceRelativePath = workspaceRelativePath,
        buildSystem = buildSystem,
        packageManager = packageManager,
        tags = tagsJson.decodeStringListStable("ProgrammingCourseProfileEntity.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun ProgrammingCourseProfile.asEntity(): ProgrammingCourseProfileEntity = ProgrammingCourseProfileEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    primaryLanguage = primaryLanguage.name,
    level = level.name,
    repositoryUri = repositoryUri,
    workspaceRelativePath = workspaceRelativePath,
    buildSystem = buildSystem,
    packageManager = packageManager,
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun CodeSnippetEntity.asExternalModel(): CodeSnippet = decodeModel("CodeSnippetEntity") {
    CodeSnippet(
        id = CodeSnippetId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        title = title,
        language = language.decodeEnum("CodeSnippetEntity.language"),
        code = code,
        description = description,
        sourceUri = sourceUri,
        tags = tagsJson.decodeStringListStable("CodeSnippetEntity.tagsJson"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun CodeSnippet.asEntity(): CodeSnippetEntity = CodeSnippetEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    title = title,
    language = language.name,
    code = code,
    description = description,
    sourceUri = sourceUri,
    tagsJson = tags.encodeStringListStable(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun CodingExerciseEntity.asExternalModel(): CodingExercise = decodeModel("CodingExerciseEntity") {
    CodingExercise(
        id = CodingExerciseId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        snippetId = snippetId?.let(::CodeSnippetId),
        type = type.decodeEnum<CodingExerciseType>("CodingExerciseEntity.type"),
        title = title,
        prompt = prompt,
        starterCode = starterCode,
        expectedOutput = expectedOutput,
        hints = hintsJson.decodeStringListStable("CodingExerciseEntity.hintsJson"),
        solutionNotes = solutionNotes,
        language = language.decodeEnum<ProgrammingLanguage>("CodingExerciseEntity.language"),
        difficulty = difficulty,
        status = status.decodeEnum<CodingExerciseStatus>("CodingExerciseEntity.status"),
        runCommandTemplate = runCommandTemplate,
        testCommandTemplate = testCommandTemplate,
        files = filesJson.decodeExerciseFilesStable("CodingExerciseEntity.filesJson"),
        tags = tagsJson.decodeStringListStable("CodingExerciseEntity.tagsJson"),
        lastResultJson = lastResultJson,
        retryAtEpochMillis = retryAtEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun CodingExercise.asEntity(): CodingExerciseEntity = CodingExerciseEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    snippetId = snippetId?.value,
    type = type.name,
    title = title,
    prompt = prompt,
    starterCode = starterCode,
    expectedOutput = expectedOutput,
    hintsJson = hints.encodeStringListStable(),
    solutionNotes = solutionNotes,
    language = language.name,
    difficulty = difficulty,
    status = status.name,
    runCommandTemplate = requireNotNull(runCommandTemplate) { "Coding exercises persist explicit run templates." },
    testCommandTemplate = requireNotNull(testCommandTemplate) { "Coding exercises persist explicit test templates." },
    filesJson = files.encodeExerciseFilesStable(),
    tagsJson = tags.encodeStringListStable(),
    lastResultJson = lastResultJson,
    retryAtEpochMillis = retryAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun MiniProjectEntity.asExternalModel(): MiniProject = decodeModel("MiniProjectEntity") {
    MiniProject(
        id = MiniProjectId(id),
        studyPlanId = StudyPlanId(studyPlanId),
        subjectId = subjectId?.let(::SubjectId),
        topicId = topicId?.let(::TopicId),
        title = title,
        description = description,
        status = status.decodeEnum("MiniProjectEntity.status"),
        language = language.decodeEnum("MiniProjectEntity.language"),
        repositoryUri = repositoryUri,
        workspaceRelativePath = workspaceRelativePath,
        checkpoints = checkpointsJson.decodeCheckpointsStable("MiniProjectEntity.checkpointsJson"),
        deliverables = deliverablesJson.decodeStringListStable("MiniProjectEntity.deliverablesJson"),
        runCommandTemplate = runCommandTemplate,
        testCommandTemplate = testCommandTemplate,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun MiniProject.asEntity(): MiniProjectEntity = MiniProjectEntity(
    id = id.value,
    studyPlanId = studyPlanId.value,
    subjectId = subjectId?.value,
    topicId = topicId?.value,
    title = title,
    description = description,
    status = status.name,
    language = language.name,
    repositoryUri = repositoryUri,
    workspaceRelativePath = workspaceRelativePath,
    checkpointsJson = checkpoints.encodeCheckpointsStable(),
    deliverablesJson = deliverables.encodeStringListStable(),
    runCommandTemplate = requireNotNull(runCommandTemplate) { "Mini projects persist explicit run templates." },
    testCommandTemplate = requireNotNull(testCommandTemplate) { "Mini projects persist explicit test templates." },
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun TermuxRuntimePreferenceEntity.asExternalModel(): TermuxRuntimePreference = decodeModel("TermuxRuntimePreferenceEntity") {
    TermuxRuntimePreference(
        studyPlanId = StudyPlanId(studyPlanId),
        enabled = enabled,
        workspaceRoot = workspaceRoot,
        preferredLanguage = preferredLanguage?.decodeEnum<ProgrammingLanguage>(
            "TermuxRuntimePreferenceEntity.preferredLanguage",
        ),
        defaultRunCommand = defaultRunCommand,
        defaultTestCommand = defaultTestCommand,
        allowedCommandTemplates = allowedCommandTemplatesJson.decodeStringListStable(
            "TermuxRuntimePreferenceEntity.allowedCommandTemplatesJson",
        ),
        captureStdout = captureStdout,
        captureStderr = captureStderr,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun TermuxRuntimePreference.asEntity(): TermuxRuntimePreferenceEntity = TermuxRuntimePreferenceEntity(
    studyPlanId = studyPlanId.value,
    enabled = enabled,
    workspaceRoot = workspaceRoot,
    preferredLanguage = preferredLanguage?.name,
    defaultRunCommand = defaultRunCommand,
    defaultTestCommand = defaultTestCommand,
    allowedCommandTemplatesJson = allowedCommandTemplates.encodeStringListStable(),
    captureStdout = captureStdout,
    captureStderr = captureStderr,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun TermuxCommandRequestEntity.asExternalModel(): TermuxCommandRequest = decodeModel("TermuxCommandRequestEntity") {
    TermuxCommandRequest(
        id = id,
        studyPlanId = StudyPlanId(studyPlanId),
        exerciseId = exerciseId?.let(::CodingExerciseId),
        workspace = TermuxWorkspace(root = workspaceRoot, courseSlug = courseSlug, relativePath = workspaceRelativePath),
        action = action.decodeEnum("TermuxCommandRequestEntity.action"),
        commandTemplate = commandLine?.let {
            TermuxCommandTemplate(
                id = requireNotNull(commandTemplateId),
                label = requireNotNull(commandTemplateLabel),
                language = commandTemplateLanguage?.decodeEnum<ProgrammingLanguage>(
                    "TermuxCommandRequestEntity.commandTemplateLanguage",
                ),
                commandLine = it,
                workingDirectory = workingDirectory ?: ".",
            )
        },
        state = state.decodeEnum("TermuxCommandRequestEntity.state"),
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun TermuxCommandRequest.asEntity(): TermuxCommandRequestEntity = TermuxCommandRequestEntity(
    id = id,
    studyPlanId = studyPlanId.value,
    exerciseId = exerciseId?.value,
    workspaceRoot = workspace.root,
    courseSlug = workspace.courseSlug,
    workspaceRelativePath = workspace.relativePath,
    action = action.name,
    commandTemplateId = commandTemplate?.id,
    commandTemplateLabel = commandTemplate?.label,
    commandTemplateLanguage = commandTemplate?.language?.name,
    commandLine = commandTemplate?.commandLine,
    workingDirectory = commandTemplate?.workingDirectory,
    state = state.name,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun TermuxCommandResultEntity.asExternalModel(): TermuxCommandResult = decodeModel("TermuxCommandResultEntity") {
    TermuxCommandResult(
        requestId = requestId,
        state = state.decodeEnum<TermuxExecutionState>("TermuxCommandResultEntity.state"),
        exitCode = exitCode,
        stdoutText = stdoutText,
        stderrText = stderrText,
        resultJson = resultJson,
        durationMillis = durationMillis,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}

fun TermuxCommandResult.asEntity(id: String = requestId): TermuxCommandResultEntity = TermuxCommandResultEntity(
    id = id,
    requestId = requestId,
    state = state.name,
    exitCode = exitCode,
    stdoutText = stdoutText,
    stderrText = stderrText,
    resultJson = resultJson,
    durationMillis = durationMillis,
    completedAtEpochMillis = completedAtEpochMillis,
)
