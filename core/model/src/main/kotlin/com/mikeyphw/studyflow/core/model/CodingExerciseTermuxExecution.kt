package com.mikeyphw.studyflow.core.model

enum class TermuxExerciseRunMode {
    RUN,
    TEST,
}

data class TermuxExerciseExecutionPlan(
    val exerciseId: CodingExerciseId,
    val workspace: TermuxWorkspace,
    val files: List<CodingExerciseFile>,
    val commandTemplate: TermuxCommandTemplate,
    val requests: List<TermuxCommandRequest>,
) {
    init {
        require(files.isNotEmpty()) { "A Termux exercise run must write at least one file." }
        require(requests.map { it.action } == expectedTermuxExerciseActions) {
            "Coding exercise execution must create workspace, write files, run command, then read result."
        }
        require(requests.all { it.workspace == workspace }) { "All Termux requests must target the same workspace." }
        require(requests[2].commandTemplate == commandTemplate) { "RUN_COMMAND must use the selected template." }
    }

    val requestEnvelope: TermuxBridgeEnvelope = TermuxBridgeEnvelope(request = requests[2])
}

data class CodingExerciseTermuxSummary(
    val exerciseId: CodingExerciseId,
    val title: String,
    val language: ProgrammingLanguage,
    val status: CodingExerciseStatus,
    val lastExitCode: Int?,
    val readyToRun: Boolean,
    val nextActionLabel: String,
) {
    init {
        require(title.isNotBlank()) { "Coding exercise summary title cannot be blank." }
    }
}

private val expectedTermuxExerciseActions = listOf(
    TermuxBridgeAction.CREATE_WORKSPACE,
    TermuxBridgeAction.WRITE_EXERCISE_FILES,
    TermuxBridgeAction.RUN_COMMAND,
    TermuxBridgeAction.READ_RESULT,
)

fun CodingExercise.toTermuxExecutionPlan(
    workspace: TermuxWorkspace,
    runMode: TermuxExerciseRunMode,
    requestIdPrefix: String,
    createdAtEpochMillis: Long,
): TermuxExerciseExecutionPlan {
    require(requestIdPrefix == sanitizeTermuxWorkspaceSlug(requestIdPrefix)) {
        "Termux request id prefix is not safe."
    }
    require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
    require(workspace.courseSlug == sanitizeTermuxWorkspaceSlug(workspace.courseSlug)) {
        "Termux course workspace is not safe."
    }

    val command = requireNotNull(safeCommandTemplateFor(runMode)) {
        "No safe Termux command is available for ${language.displayName}."
    }
    val template = TermuxCommandTemplate(
        id = "${requestIdPrefix}-${runMode.name.lowercase()}",
        label = if (runMode == TermuxExerciseRunMode.TEST) "Run exercise tests" else "Run exercise",
        language = language,
        commandLine = command,
        workingDirectory = exerciseWorkingDirectory,
    )
    val bundle = exerciseFileBundle()

    fun request(suffix: String, action: TermuxBridgeAction, commandTemplate: TermuxCommandTemplate? = null) =
        TermuxCommandRequest(
            id = "$requestIdPrefix-$suffix",
            studyPlanId = studyPlanId,
            exerciseId = id,
            workspace = workspace,
            action = action,
            commandTemplate = commandTemplate,
            createdAtEpochMillis = createdAtEpochMillis,
        )

    return TermuxExerciseExecutionPlan(
        exerciseId = id,
        workspace = workspace,
        files = bundle,
        commandTemplate = template,
        requests = listOf(
            request("workspace", TermuxBridgeAction.CREATE_WORKSPACE),
            request("files", TermuxBridgeAction.WRITE_EXERCISE_FILES),
            request("run", TermuxBridgeAction.RUN_COMMAND, template),
            request("result", TermuxBridgeAction.READ_RESULT),
        ),
    )
}

fun CodingExercise.exerciseFileBundle(): List<CodingExerciseFile> {
    val explicitFiles = files
    if (explicitFiles.isNotEmpty()) return explicitFiles

    val starterPath = when (language) {
        ProgrammingLanguage.KOTLIN -> "Main.kt"
        ProgrammingLanguage.JAVA -> "Main.java"
        ProgrammingLanguage.PYTHON -> "main.py"
        ProgrammingLanguage.JAVASCRIPT -> "index.js"
        ProgrammingLanguage.TYPESCRIPT -> "index.ts"
        ProgrammingLanguage.SHELL -> "main.sh"
        ProgrammingLanguage.SQL -> "query.sql"
        ProgrammingLanguage.HTML -> "index.html"
        ProgrammingLanguage.CSS -> "style.css"
        ProgrammingLanguage.JSON -> "data.json"
        ProgrammingLanguage.YAML -> "data.yaml"
        ProgrammingLanguage.MARKDOWN -> "exercise.md"
        ProgrammingLanguage.C -> "main.c"
        ProgrammingLanguage.CPP -> "main.cpp"
        ProgrammingLanguage.GO -> "main.go"
        ProgrammingLanguage.RUST -> "src/main.rs"
        ProgrammingLanguage.OTHER -> "exercise.txt"
    }

    val starter = CodingExerciseFile(
        relativePath = starterPath,
        contents = starterCode.ifBlank { prompt },
        role = CodingExerciseFileRole.STARTER,
    )
    val expected = expectedOutput?.takeIf { it.isNotBlank() }?.let { output ->
        CodingExerciseFile(
            relativePath = "expected_output.txt",
            contents = output,
            role = CodingExerciseFileRole.EXPECTED_OUTPUT,
        )
    }
    val notes = solutionNotes.takeIf { it.isNotBlank() }?.let { notesText ->
        CodingExerciseFile(
            relativePath = "solution_notes.md",
            contents = notesText,
            role = CodingExerciseFileRole.SOLUTION_NOTES,
        )
    }
    return listOfNotNull(starter, expected, notes)
}

fun CodingExercise.commandTemplateFor(runMode: TermuxExerciseRunMode): String =
    safeCommandTemplateFor(runMode).orEmpty()

fun CodingExercise.safeCommandTemplateFor(runMode: TermuxExerciseRunMode): String? {
    val command = when (runMode) {
        TermuxExerciseRunMode.TEST -> testCommandTemplate ?: runCommandTemplate ?: defaultRunCommandFor(language)
        TermuxExerciseRunMode.RUN -> runCommandTemplate ?: testCommandTemplate ?: defaultRunCommandFor(language)
    }.trim()
    return command.takeIf(::isAllowedTermuxCommandTemplate)
}

fun CodingExercise.applyTermuxResult(result: TermuxCommandResult, updatedAtEpochMillis: Long): CodingExercise {
    require(result.requestId.isNotBlank()) { "Termux result must have a request id." }
    require(result.requestId.endsWith("-run")) { "Only the correlated RUN_COMMAND request can update an exercise." }
    require(result.requestId.contains(sanitizeTermuxWorkspaceSlug(id.value))) {
        "Termux result request id does not match this exercise."
    }
    require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    val nextStatus = when {
        result.state == TermuxExecutionState.CANCELLED -> CodingExerciseStatus.NEEDS_REVIEW
        result.exitCode == 0 && result.state == TermuxExecutionState.SUCCEEDED -> CodingExerciseStatus.RAN_SUCCESSFULLY
        else -> CodingExerciseStatus.FAILED_TESTS
    }
    return copy(
        status = nextStatus,
        lastResultJson = result.toExerciseResultJson(),
        retryAtEpochMillis = if (nextStatus == CodingExerciseStatus.FAILED_TESTS) {
            updatedAtEpochMillis
        } else {
            retryAtEpochMillis
        },
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun CodingExercise.toTermuxSummary(result: TermuxCommandResult? = null): CodingExerciseTermuxSummary {
    val lastExitCode = result?.exitCode
    val ready = status != CodingExerciseStatus.COMPLETED &&
        safeCommandTemplateFor(TermuxExerciseRunMode.TEST) != null
    val label = when (status) {
        CodingExerciseStatus.NOT_STARTED -> "Start in Termux"
        CodingExerciseStatus.IN_PROGRESS -> "Run in Termux"
        CodingExerciseStatus.RAN_SUCCESSFULLY -> "Review result"
        CodingExerciseStatus.FAILED_TESTS -> "Retry in Termux"
        CodingExerciseStatus.NEEDS_REVIEW -> "Review manually"
        CodingExerciseStatus.COMPLETED -> "Completed"
    }
    return CodingExerciseTermuxSummary(
        exerciseId = id,
        title = title,
        language = language,
        status = status,
        lastExitCode = lastExitCode,
        readyToRun = ready,
        nextActionLabel = label,
    )
}

private val CodingExercise.exerciseWorkingDirectory: String
    get() = "exercises/${sanitizeTermuxWorkspaceSlug(id.value)}"

private fun defaultRunCommandFor(language: ProgrammingLanguage): String = when (language) {
    ProgrammingLanguage.KOTLIN -> "kotlinc Main.kt"
    ProgrammingLanguage.JAVA -> "javac Main.java"
    ProgrammingLanguage.PYTHON -> "python main.py"
    ProgrammingLanguage.JAVASCRIPT -> "node index.js"
    ProgrammingLanguage.TYPESCRIPT -> "npx ts-node index.ts"
    ProgrammingLanguage.C -> "clang main.c"
    ProgrammingLanguage.CPP -> "clang main.cpp"
    ProgrammingLanguage.GO -> "go test"
    ProgrammingLanguage.RUST -> "cargo test"
    ProgrammingLanguage.SHELL,
    ProgrammingLanguage.SQL,
    ProgrammingLanguage.HTML,
    ProgrammingLanguage.CSS,
    ProgrammingLanguage.JSON,
    ProgrammingLanguage.YAML,
    ProgrammingLanguage.MARKDOWN,
    ProgrammingLanguage.OTHER,
    -> ""
}

private fun TermuxCommandResult.toExerciseResultJson(): String = buildString {
    append('{')
    append("\"requestId\":\"").append(requestId.escapeJson()).append("\",")
    append("\"state\":\"").append(state.name).append("\",")
    append("\"exitCode\":").append(exitCode ?: "null").append(',')
    append("\"stdout\":\"").append(stdoutText.escapeJson()).append("\",")
    append("\"stderr\":\"").append(stderrText.escapeJson()).append("\",")
    append("\"durationMillis\":").append(durationMillis ?: "null").append(',')
    append("\"completedAtEpochMillis\":").append(completedAtEpochMillis)
    append('}')
}

private fun String.escapeJson(): String = buildString {
    this@escapeJson.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
