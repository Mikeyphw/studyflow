package com.mikeyphw.studyflow.core.model

private const val DefaultTermuxWorkspaceRoot = "~/studyflow/courses"

private val AllowedTermuxExecutables = setOf(
    "python",
    "python3",
    "node",
    "npm",
    "npx",
    "gradle",
    "./gradlew",
    "kotlinc",
    "javac",
    "java",
    "gcc",
    "clang",
    "go",
    "cargo",
    "pytest",
)

enum class TermuxBridgeAction {
    OPEN_TERMUX,
    CREATE_WORKSPACE,
    WRITE_EXERCISE_FILES,
    RUN_COMMAND,
    READ_RESULT,
}

enum class TermuxExecutionState {
    QUEUED,
    WAITING_FOR_TERMUX,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class TermuxWorkspace(
    val root: String = DefaultTermuxWorkspaceRoot,
    val courseSlug: String,
    val relativePath: String = "",
) {
    init {
        require(isSafeTermuxPath(root)) { "Termux workspace root must stay inside a safe course area." }
        require(courseSlug == sanitizeTermuxWorkspaceSlug(courseSlug)) { "Termux course slug is not safe." }
        require(isSafeRelativeTermuxPath(relativePath)) { "Termux relative path cannot escape the workspace." }
    }

    val courseRelativePath: String = if (relativePath.isBlank()) {
        courseSlug
    } else {
        "$courseSlug/$relativePath"
    }
}

data class TermuxCommandTemplate(
    val id: String,
    val label: String,
    val language: ProgrammingLanguage?,
    val commandLine: String,
    val workingDirectory: String = ".",
) {
    init {
        require(id.isNotBlank()) { "Termux command template id cannot be blank." }
        require(label.isNotBlank()) { "Termux command template label cannot be blank." }
        require(isAllowedTermuxCommandTemplate(commandLine)) { "Termux command template is not allowed." }
        require(isSafeRelativeTermuxPath(workingDirectory)) { "Termux working directory is not safe." }
    }
}

data class TermuxCommandRequest(
    val id: String,
    val studyPlanId: StudyPlanId,
    val exerciseId: CodingExerciseId?,
    val workspace: TermuxWorkspace,
    val action: TermuxBridgeAction,
    val commandTemplate: TermuxCommandTemplate?,
    val state: TermuxExecutionState = TermuxExecutionState.QUEUED,
    val createdAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "Termux request id cannot be blank." }
        require(createdAtEpochMillis >= 0) { "Termux request timestamp cannot be negative." }
        if (action == TermuxBridgeAction.RUN_COMMAND) {
            require(commandTemplate != null) { "RUN_COMMAND requires a command template." }
        }
    }
}

data class TermuxCommandResult(
    val requestId: String,
    val state: TermuxExecutionState,
    val exitCode: Int?,
    val stdoutText: String,
    val stderrText: String,
    val resultJson: String?,
    val durationMillis: Long?,
    val completedAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "Termux result request id cannot be blank." }
        require(state in terminalTermuxStates) { "Termux result must use a terminal state." }
        require(durationMillis == null || durationMillis >= 0) { "Termux duration cannot be negative." }
        require(completedAtEpochMillis >= 0) { "Termux completion timestamp cannot be negative." }
    }
}

data class TermuxBridgeEnvelope(
    val schemaVersion: Int = 1,
    val request: TermuxCommandRequest,
) {
    init {
        require(schemaVersion == 1) { "Unsupported Termux bridge schema." }
    }
}

data class TermuxRuntimePolicy(
    val allowedActions: Set<TermuxBridgeAction>,
    val allowedTemplates: List<TermuxCommandTemplate>,
    val requiresExplicitUserAction: Boolean = true,
) {
    init {
        require(TermuxBridgeAction.RUN_COMMAND !in allowedActions || allowedTemplates.isNotEmpty()) {
            "Running Termux commands requires at least one allowed template."
        }
        require(requiresExplicitUserAction) { "Termux runtime must require explicit user action." }
    }

    companion object {
        fun defaultForProgrammingCourse(language: ProgrammingLanguage): TermuxRuntimePolicy {
            val templates = defaultTermuxTemplates(language)
            return TermuxRuntimePolicy(
                allowedActions = setOf(
                    TermuxBridgeAction.OPEN_TERMUX,
                    TermuxBridgeAction.CREATE_WORKSPACE,
                    TermuxBridgeAction.WRITE_EXERCISE_FILES,
                    TermuxBridgeAction.RUN_COMMAND,
                    TermuxBridgeAction.READ_RESULT,
                ),
                allowedTemplates = templates,
            )
        }
    }
}

val terminalTermuxStates: Set<TermuxExecutionState> = setOf(
    TermuxExecutionState.SUCCEEDED,
    TermuxExecutionState.FAILED,
    TermuxExecutionState.CANCELLED,
)

fun sanitizeTermuxWorkspaceSlug(value: String): String {
    val slug = value.trim().lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
    return slug.ifBlank { "course" }
}

fun termuxRequestFileName(requestId: String): String {
    require(requestId == sanitizeTermuxWorkspaceSlug(requestId)) { "Unsafe Termux request id." }
    return "$requestId.request.json"
}

fun termuxResultFileName(requestId: String): String {
    require(requestId == sanitizeTermuxWorkspaceSlug(requestId)) { "Unsafe Termux request id." }
    return "$requestId.result.json"
}

fun isAllowedTermuxCommandTemplate(template: String?): Boolean {
    val command = template?.trim().orEmpty()
    if (command.isEmpty()) return true
    if (!isSafeTermuxCommandTemplate(command)) return false
    val executable = command.split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
    return executable in AllowedTermuxExecutables
}

fun defaultTermuxTemplates(language: ProgrammingLanguage): List<TermuxCommandTemplate> = when (language) {
    ProgrammingLanguage.KOTLIN -> listOf(
        TermuxCommandTemplate("kotlin-gradle-test", "Run Gradle tests", language, "./gradlew test"),
        TermuxCommandTemplate("kotlin-compile", "Compile Kotlin file", language, "kotlinc Main.kt"),
    )
    ProgrammingLanguage.JAVA -> listOf(
        TermuxCommandTemplate("java-compile", "Compile Java file", language, "javac Main.java"),
        TermuxCommandTemplate("java-run", "Run Java class", language, "java Main"),
    )
    ProgrammingLanguage.PYTHON -> listOf(
        TermuxCommandTemplate("python-run", "Run Python file", language, "python main.py"),
        TermuxCommandTemplate("python-test", "Run pytest", language, "python -m pytest"),
    )
    ProgrammingLanguage.JAVASCRIPT,
    ProgrammingLanguage.TYPESCRIPT,
    -> listOf(
        TermuxCommandTemplate("node-run", "Run with Node", language, "node index.js"),
        TermuxCommandTemplate("npm-test", "Run npm test", language, "npm test"),
    )
    ProgrammingLanguage.C -> listOf(
        TermuxCommandTemplate("c-compile", "Compile C file", language, "clang main.c"),
    )
    ProgrammingLanguage.CPP -> listOf(
        TermuxCommandTemplate("cpp-compile", "Compile C++ file", language, "clang main.cpp"),
    )
    ProgrammingLanguage.GO -> listOf(
        TermuxCommandTemplate("go-test", "Run Go tests", language, "go test"),
    )
    ProgrammingLanguage.RUST -> listOf(
        TermuxCommandTemplate("cargo-test", "Run Cargo tests", language, "cargo test"),
    )
    else -> emptyList()
}

private fun isSafeRelativeTermuxPath(path: String): Boolean {
    val normalized = path.trim()
    if (normalized.isEmpty() || normalized == ".") return true
    if (normalized.startsWith("/") || normalized.startsWith("~")) return false
    return ".." !in normalized.split('/') && '\\' !in normalized
}
