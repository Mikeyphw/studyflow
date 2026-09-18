package com.mikeyphw.studyflow.core.model

private const val DefaultTermuxWorkspaceRoot = "~/studyflow/courses"

private val AllowedTermuxExecutables = setOf(
    "sh",
    "bash",
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
    "bash",
    "sqlite3",
)

private val InlineInterpreterExecutables = setOf("sh",
    "bash",
    "python", "python3", "node", "bash")
private val InlineInterpreterFlags = setOf("-c", "--command", "-e", "--eval", "--execute")

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
        require(id == sanitizeTermuxWorkspaceSlug(id)) { "Termux request id must be a stable safe slug." }
        require(createdAtEpochMillis >= 0) { "Termux request timestamp cannot be negative." }
        if (action == TermuxBridgeAction.RUN_COMMAND) {
            require(commandTemplate != null) { "RUN_COMMAND requires a command template." }
        }
    }

    fun withState(nextState: TermuxExecutionState): TermuxCommandRequest = copy(state = nextState)
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
        require(requestId == sanitizeTermuxWorkspaceSlug(requestId)) { "Termux result request id must be a stable safe slug." }
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
    if (command.isEmpty()) return false
    val parts = runCatching { tokenizeTermuxCommandTemplate(command) }.getOrElse { return false }
    val invocation = runCatching {
        TermuxCommandInvocation(executable = parts.first(), arguments = parts.drop(1))
    }.getOrElse { return false }
    return invocation.isAllowedStudyFlowInvocation()
}

fun safeTermuxCommandInvocation(commandLine: String): TermuxCommandInvocation? = runCatching {
    splitTermuxCommandTemplate(commandLine)
}.getOrNull()

fun isAllowedInlineInterpreterInvocation(
    executable: String,
    arguments: List<String>,
): Boolean {
    if (executable !in InlineInterpreterExecutables) return false
    val invocation = runCatching {
        TermuxCommandInvocation(executable = executable, arguments = arguments)
    }.getOrElse { return false }
    return invocation.isAllowedStudyFlowInvocation()
}

data class TermuxCommandInvocation(
    val executable: String,
    val arguments: List<String>,
) {
    init {
        require(executable.isNotBlank()) { "Termux executable cannot be blank." }
        require(executable in AllowedTermuxExecutables) { "Termux executable is not allowed." }
        require(arguments.none { it.isBlank() }) { "Termux arguments cannot be blank." }
        require(arguments.none { argument -> argument.any { it.code == 0 } }) {
            "Termux arguments cannot contain NUL bytes."
        }
        require(executable !in InlineInterpreterExecutables || arguments.none { it in InlineInterpreterFlags }) {
            "Inline interpreter code is not allowed in StudyFlow Termux templates."
        }
    }
}

fun splitTermuxCommandTemplate(commandLine: String): TermuxCommandInvocation {
    val parts = tokenizeTermuxCommandTemplate(commandLine)
    val invocation = TermuxCommandInvocation(
        executable = parts.first(),
        arguments = parts.drop(1),
    )
    require(invocation.isAllowedStudyFlowInvocation()) {
        "Termux command template is outside the allowed StudyFlow execution surface."
    }
    return invocation
}

private fun TermuxCommandInvocation.isAllowedStudyFlowInvocation(): Boolean {
    if (executable !in AllowedTermuxExecutables) return false
    if (arguments.any { !isSafeTermuxArgument(it) }) return false
    return when (executable) {
        "sh",
    "bash",
    "python", "python3" -> allowsPythonFileOrPytest(arguments)
        "pytest" -> arguments.all(::isSafeRelativeTermuxPathOrFlag)
        "node" -> arguments.size == 1 && arguments.single().hasOneOfSuffixes(".js", ".mjs", ".cjs")
        "npm" -> arguments == listOf("test") || arguments == listOf("run", "test")
        "npx" -> arguments.size == 2 && arguments.first() == "ts-node" && arguments.last().hasOneOfSuffixes(".ts", ".tsx")
        "gradle", "./gradlew" -> arguments.isNotEmpty() && arguments.all {
            it == "test" || it.startsWith(":") && it.endsWith("test", ignoreCase = true)
        }
        "kotlinc" -> arguments.isNotEmpty() && arguments.all { it.hasOneOfSuffixes(".kt", ".kts") }
        "javac" -> arguments.isNotEmpty() && arguments.all { it.hasOneOfSuffixes(".java") }
        "java" -> arguments.size == 1 && arguments.single().matches(Regex("[A-Za-z_][A-Za-z0-9_.]*"))
        "gcc", "clang" -> arguments.isNotEmpty() && arguments.all {
            it.hasOneOfSuffixes(".c", ".cc", ".cpp", ".cxx")
        }
        "go" -> arguments == listOf("test")
        "cargo" -> arguments == listOf("test")
        "bash" -> arguments.size == 1 && arguments.single().hasOneOfSuffixes(".sh")
        "sqlite3" -> arguments.size == 1 && arguments.single().hasOneOfSuffixes(".db", ".sqlite", ".sqlite3")
        else -> false
    }
}

private fun allowsPythonFileOrPytest(arguments: List<String>): Boolean = when {
    arguments == listOf("--version") || arguments == listOf("-V") -> true
    arguments.size == 1 && (arguments.single().hasOneOfSuffixes(".py") || arguments.single() == "{mainFile}" || arguments.single() == "{exerciseFile}") -> true
    arguments.size >= 2 && arguments[0] == "-m" && arguments[1] == "pytest" -> {
        arguments.drop(2).all(::isSafeRelativeTermuxPathOrFlag)
    }
    else -> false
}

private fun isSafeTermuxArgument(argument: String): Boolean {
    if (argument == "{mainFile}" || argument == "{exerciseFile}" || argument == "{expectedOutput}") return true
    if (argument.isBlank()) return false
    if ('\u0000' in argument || '\\' in argument) return false
    if (argument.startsWith("/")) return false
    if (argument.startsWith("~")) return false
    if (".." in argument.split('/')) return false
    return true
}

private fun isSafeRelativeTermuxPathOrFlag(argument: String): Boolean =
    isSafeTermuxArgument(argument) && !argument.startsWith("--root") && !argument.startsWith("--basetemp")

private fun String.hasOneOfSuffixes(vararg suffixes: String): Boolean =
    suffixes.any { suffix -> endsWith(suffix, ignoreCase = true) }

fun termuxWorkspaceWorkdir(workspace: TermuxWorkspace, workingDirectory: String): String {
    require(isSafeRelativeTermuxPath(workingDirectory)) { "Termux working directory cannot escape workspace." }
    val root = workspace.root.trim().removeSuffix("/")
    val scoped = workspace.courseRelativePath.trim('/').takeIf { it.isNotBlank() }
    val work = workingDirectory.trim().trim('/').removePrefix("./").takeIf { it.isNotBlank() && it != "." }
    return listOfNotNull(root, scoped, work).joinToString("/")
}

fun termuxExecutablePath(executable: String): String {
    require(executable in AllowedTermuxExecutables) { "Termux executable is not allowed." }
    return when {
        executable.startsWith("./") -> executable
        else -> "$" + "PREFIX/bin/$executable"
    }
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
    ProgrammingLanguage.SHELL -> listOf(
        TermuxCommandTemplate("shell-run", "Run shell script", language, "bash main.sh"),
    )
    ProgrammingLanguage.SQL -> listOf(
        TermuxCommandTemplate("sql-open", "Open SQLite shell", language, "sqlite3 studyflow.db"),
    )
    ProgrammingLanguage.HTML,
    ProgrammingLanguage.CSS,
    ProgrammingLanguage.JSON,
    ProgrammingLanguage.YAML,
    ProgrammingLanguage.MARKDOWN,
    ProgrammingLanguage.OTHER,
    -> listOf(
        TermuxCommandTemplate(
            id = "${language.name.lowercase()}-workspace-check",
            label = "Check workspace runtime",
            language = language,
            commandLine = "python --version",
        ),
    )
}

private fun isSafeRelativeTermuxPath(path: String): Boolean {
    val normalized = path.trim()
    if (normalized.isEmpty() || normalized == ".") return true
    if (normalized.startsWith("/") || normalized.startsWith("~")) return false
    return ".." !in normalized.split('/') && '\\' !in normalized
}
