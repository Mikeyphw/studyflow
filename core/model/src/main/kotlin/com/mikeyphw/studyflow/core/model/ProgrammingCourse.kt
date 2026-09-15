package com.mikeyphw.studyflow.core.model

enum class ProgrammingLanguage(
    val displayName: String,
    val fileExtensions: Set<String>,
) {
    KOTLIN("Kotlin", setOf("kt", "kts")),
    JAVA("Java", setOf("java")),
    PYTHON("Python", setOf("py")),
    JAVASCRIPT("JavaScript", setOf("js", "mjs", "cjs")),
    TYPESCRIPT("TypeScript", setOf("ts", "tsx")),
    SHELL("Shell", setOf("sh", "bash", "zsh")),
    SQL("SQL", setOf("sql")),
    HTML("HTML", setOf("html", "htm")),
    CSS("CSS", setOf("css")),
    JSON("JSON", setOf("json")),
    YAML("YAML", setOf("yml", "yaml")),
    MARKDOWN("Markdown", setOf("md", "markdown")),
    C("C", setOf("c", "h")),
    CPP("C++", setOf("cc", "cpp", "cxx", "hpp")),
    GO("Go", setOf("go")),
    RUST("Rust", setOf("rs")),
    OTHER("Other", emptySet());

    companion object {
        fun fromNameOrExtension(value: String?): ProgrammingLanguage {
            val normalized = value.orEmpty().trim().lowercase().removePrefix(".")
            return entries.firstOrNull { language ->
                language.name.lowercase() == normalized ||
                    language.displayName.lowercase() == normalized ||
                    normalized in language.fileExtensions
            } ?: OTHER
        }
    }
}

enum class ProgrammingCourseLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    MIXED,
}

enum class CodingExerciseType {
    CONCEPT_CHECK,
    SYNTAX_RECALL,
    CODE_READING,
    DEBUGGING_DRILL,
    IMPLEMENTATION_TASK,
    TESTING_TASK,
    MINI_PROJECT_STEP,
}

enum class CodingExerciseStatus {
    NOT_STARTED,
    IN_PROGRESS,
    RAN_SUCCESSFULLY,
    FAILED_TESTS,
    NEEDS_REVIEW,
    COMPLETED,
}

enum class CodingExerciseFileRole {
    STARTER,
    TEST,
    SUPPORT,
    SOLUTION_NOTES,
    EXPECTED_OUTPUT,
}

enum class MiniProjectStatus {
    NOT_STARTED,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED,
    ARCHIVED,
}

data class ProgrammingCourseProfile(
    val id: ProgrammingCourseProfileId,
    val studyPlanId: StudyPlanId,
    val primaryLanguage: ProgrammingLanguage,
    val level: ProgrammingCourseLevel,
    val repositoryUri: String?,
    val workspaceRelativePath: String,
    val buildSystem: String?,
    val packageManager: String?,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(workspaceRelativePath.isNotBlank()) { "Programming workspace path cannot be blank." }
        require(!workspaceRelativePath.startsWith("/")) { "Programming workspace path must be relative." }
        require(".." !in workspaceRelativePath.split('/')) {
            "Programming workspace path cannot escape its course root."
        }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class CodeSnippet(
    val id: CodeSnippetId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val title: String,
    val language: ProgrammingLanguage,
    val code: String,
    val description: String,
    val sourceUri: String?,
    val tags: List<String>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank()) { "Code snippet title cannot be blank." }
        require(code.isNotBlank()) { "Code snippet cannot be blank." }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class CodingExerciseFile(
    val relativePath: String,
    val contents: String,
    val role: CodingExerciseFileRole,
) {
    init {
        require(relativePath.isNotBlank()) { "Exercise file path cannot be blank." }
        require(!relativePath.startsWith("/")) { "Exercise file path must be relative." }
        require(".." !in relativePath.split('/')) { "Exercise file path cannot escape its exercise root." }
    }
}

data class CodingExercise(
    val id: CodingExerciseId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val snippetId: CodeSnippetId?,
    val type: CodingExerciseType,
    val title: String,
    val prompt: String,
    val starterCode: String,
    val expectedOutput: String?,
    val hints: List<String>,
    val solutionNotes: String,
    val language: ProgrammingLanguage,
    val difficulty: Int,
    val status: CodingExerciseStatus,
    val runCommandTemplate: String?,
    val testCommandTemplate: String?,
    val files: List<CodingExerciseFile>,
    val tags: List<String>,
    val lastResultJson: String?,
    val retryAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank()) { "Coding exercise title cannot be blank." }
        require(prompt.isNotBlank()) { "Coding exercise prompt cannot be blank." }
        require(difficulty in 1..5) { "Coding exercise difficulty must be between 1 and 5." }
        require(retryAtEpochMillis == null || retryAtEpochMillis >= 0) { "Retry timestamp cannot be negative." }
        require(!runCommandTemplate.isNullOrBlank()) { "Run command template is mandatory for coding exercises." }
        require(!testCommandTemplate.isNullOrBlank()) { "Test command template is mandatory for coding exercises." }
        require(isAllowedTermuxCommandTemplate(runCommandTemplate)) { "Run command template is not Termux-allowed." }
        require(isAllowedTermuxCommandTemplate(testCommandTemplate)) { "Test command template is not Termux-allowed." }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class MiniProjectCheckpoint(
    val title: String,
    val description: String,
    val completed: Boolean,
) {
    init {
        require(title.isNotBlank()) { "Mini project checkpoint title cannot be blank." }
    }
}

data class MiniProject(
    val id: MiniProjectId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val title: String,
    val description: String,
    val status: MiniProjectStatus,
    val language: ProgrammingLanguage,
    val repositoryUri: String?,
    val workspaceRelativePath: String,
    val checkpoints: List<MiniProjectCheckpoint>,
    val deliverables: List<String>,
    val runCommandTemplate: String?,
    val testCommandTemplate: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank()) { "Mini project title cannot be blank." }
        require(workspaceRelativePath.isNotBlank()) { "Mini project workspace path cannot be blank." }
        require(!workspaceRelativePath.startsWith("/")) { "Mini project workspace path must be relative." }
        require(".." !in workspaceRelativePath.split('/')) { "Mini project workspace cannot escape its course root." }
        require(!runCommandTemplate.isNullOrBlank()) { "Run command template is mandatory for mini projects." }
        require(!testCommandTemplate.isNullOrBlank()) { "Test command template is mandatory for mini projects." }
        require(isAllowedTermuxCommandTemplate(runCommandTemplate)) { "Run command template is not Termux-allowed." }
        require(isAllowedTermuxCommandTemplate(testCommandTemplate)) { "Test command template is not Termux-allowed." }
        require(createdAtEpochMillis >= 0) { "Created timestamp cannot be negative." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "Updated timestamp cannot precede created timestamp." }
    }
}

data class TermuxRuntimePreference(
    val studyPlanId: StudyPlanId,
    val enabled: Boolean,
    val workspaceRoot: String,
    val preferredLanguage: ProgrammingLanguage?,
    val defaultRunCommand: String?,
    val defaultTestCommand: String?,
    val allowedCommandTemplates: List<String>,
    val captureStdout: Boolean,
    val captureStderr: Boolean,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(workspaceRoot.isNotBlank()) { "Termux workspace root cannot be blank." }
        require(isSafeTermuxPath(workspaceRoot)) { "Termux workspace root must stay inside an app-owned workspace." }
        require(isSafeTermuxCommandTemplate(defaultRunCommand)) { "Default run command is not Termux-safe." }
        require(isSafeTermuxCommandTemplate(defaultTestCommand)) { "Default test command is not Termux-safe." }
        require(allowedCommandTemplates.all(::isAllowedTermuxCommandTemplate)) {
            "Allowed command templates must be Termux-allowed."
        }
        require(updatedAtEpochMillis >= 0) { "Updated timestamp cannot be negative." }
    }

    companion object {
        fun disabled(studyPlanId: StudyPlanId): TermuxRuntimePreference = TermuxRuntimePreference(
            studyPlanId = studyPlanId,
            enabled = false,
            workspaceRoot = "~/studyflow/courses",
            preferredLanguage = null,
            defaultRunCommand = null,
            defaultTestCommand = null,
            allowedCommandTemplates = emptyList(),
            captureStdout = true,
            captureStderr = true,
            updatedAtEpochMillis = 0L,
        )
    }
}

private val DangerousShellFragments = listOf("&&", "||", ";", "`", "$(", ">", "<", "|", "\n", "\r")

/**
 * Parse the small Termux command-template language used by StudyFlow.
 *
 * OV27 intentionally does not pass templates to a shell. Quoted arguments are supported so
 * filenames/prompts survive correctly, but shell metacharacters, unterminated quotes and blank
 * tokens fail before an Android PendingIntent can be sent to Termux.
 */
fun tokenizeTermuxCommandTemplate(commandLine: String): List<String> {
    val command = commandLine.trim()
    require(command.isNotBlank()) { "Termux command template cannot be blank." }
    require(DangerousShellFragments.none { fragment -> fragment in command }) {
        "Termux command template contains a shell operator."
    }

    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false

    fun flush() {
        if (current.isNotEmpty()) {
            tokens += current.toString()
            current.clear()
        }
    }

    command.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            quote != null -> {
                if (char == quote) quote = null else current.append(char)
            }
            char == '\'' || char == '"' -> quote = char
            char.isWhitespace() -> flush()
            else -> current.append(char)
        }
    }
    require(!escaped) { "Termux command template ends with a dangling escape." }
    require(quote == null) { "Termux command template has an unterminated quote." }
    flush()
    require(tokens.isNotEmpty()) { "Termux command template cannot be blank." }
    return tokens
}

fun isSafeTermuxCommandTemplate(template: String?): Boolean {
    val command = template?.trim().orEmpty()
    if (command.isEmpty()) return true
    return runCatching { tokenizeTermuxCommandTemplate(command) }.isSuccess
}

// Termux execution is intentionally confined to Termux HOME-relative StudyFlow workspaces.
// Absolute private Android paths such as /data/data/... are rejected because Android cannot
// safely promise that Termux will execute inside the configured StudyFlow root.
@Suppress("SdCardPath")
fun isSafeTermuxPath(path: String): Boolean {
    val normalized = path.trim().removeSuffix("/")
    if (normalized.isEmpty()) return false
    if (normalized.startsWith("/")) return false
    if (normalized.startsWith("/sdcard") || normalized.startsWith("/storage")) return false
    if ('\u0000' in normalized || '\\' in normalized) return false
    if (".." in normalized.split('/')) return false
    return normalized == "~" || normalized.startsWith("~/") || normalized == "$" + "HOME" ||
        normalized.startsWith("$" + "HOME/")
}
