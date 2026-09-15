package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.CodeSnippet
import com.mikeyphw.studyflow.core.model.CodeSnippetId
import com.mikeyphw.studyflow.core.model.CodingExercise
import com.mikeyphw.studyflow.core.model.CodingExerciseId
import com.mikeyphw.studyflow.core.model.CodingExerciseStatus
import com.mikeyphw.studyflow.core.model.CodingExerciseType
import com.mikeyphw.studyflow.core.model.IdiomCardId
import com.mikeyphw.studyflow.core.model.LanguageDeckId
import com.mikeyphw.studyflow.core.model.LanguageDeckProfile
import com.mikeyphw.studyflow.core.model.LanguageExpression
import com.mikeyphw.studyflow.core.model.LanguageExpressionId
import com.mikeyphw.studyflow.core.model.LanguageExpressionType
import com.mikeyphw.studyflow.core.model.LanguageRegister
import com.mikeyphw.studyflow.core.model.LanguageReviewAttempt
import com.mikeyphw.studyflow.core.model.LanguageReviewAttemptId
import com.mikeyphw.studyflow.core.model.LanguageReviewDirection
import com.mikeyphw.studyflow.core.model.MiniProject
import com.mikeyphw.studyflow.core.model.MiniProjectCheckpoint
import com.mikeyphw.studyflow.core.model.MiniProjectId
import com.mikeyphw.studyflow.core.model.MiniProjectStatus
import com.mikeyphw.studyflow.core.model.ProgrammingCourseLevel
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfileId
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.TermuxRuntimePreference
import com.mikeyphw.studyflow.core.model.UsageExample
import com.mikeyphw.studyflow.core.model.VocabularyCard
import com.mikeyphw.studyflow.core.model.VocabularyCardId
import com.mikeyphw.studyflow.core.model.buildLanguageReviewPrompt
import com.mikeyphw.studyflow.core.model.defaultLanguageReviewDirections
import com.mikeyphw.studyflow.core.model.evaluateLanguageReviewAnswer
import com.mikeyphw.studyflow.core.model.tokenizeTermuxCommandTemplate
import com.mikeyphw.studyflow.core.model.toIdiomCard
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class DesktopProgrammingExecutionResult(
    val supported: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val commandLabel: String,
    val timedOut: Boolean,
    val expectedOutputMatched: Boolean = true,
)

data class DesktopContentModeState(
    val mode: StudyContentMode,
    val programmingProfile: ProgrammingCourseProfile?,
    val codeSnippets: List<CodeSnippet>,
    val codingExercises: List<CodingExercise>,
    val miniProjects: List<MiniProject>,
    val termuxRuntimePreference: TermuxRuntimePreference?,
    val languageDeck: LanguageDeckProfile?,
    val languageExpressions: List<LanguageExpression>,
    val idiomCards: List<com.mikeyphw.studyflow.core.model.IdiomCard>,
    val vocabularyCards: List<VocabularyCard>,
    val languageAttempts: List<LanguageReviewAttempt>,
    val lastProgrammingResult: DesktopProgrammingExecutionResult?,
)

class DesktopContentModeController(
    private val services: DesktopStudyFlowServices,
    private val repository: DesktopContentModeRepository,
    workspaceRoot: Path?,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val programmingBackend = DesktopProgrammingExecutionBackend(workspaceRoot)
    private var lastProgrammingResult: DesktopProgrammingExecutionResult? = null

    fun state(): DesktopContentModeState {
        val plan = services.planContentController.state().selectedPlan
        val planId = plan?.id
        return DesktopContentModeState(
            mode = plan?.contentMode ?: StudyContentMode.EXAM_PLAN,
            programmingProfile = planId?.let(repository::programmingProfile),
            codeSnippets = planId?.let(repository::codeSnippets).orEmpty(),
            codingExercises = planId?.let(repository::codingExercises).orEmpty(),
            miniProjects = planId?.let(repository::miniProjects).orEmpty(),
            termuxRuntimePreference = planId?.let(repository::termuxRuntimePreference),
            languageDeck = planId?.let(repository::languageDeck),
            languageExpressions = planId?.let(repository::languageExpressions).orEmpty(),
            idiomCards = planId?.let(repository::idiomCards).orEmpty(),
            vocabularyCards = planId?.let(repository::vocabularyCards).orEmpty(),
            languageAttempts = planId?.let(repository::languageAttempts).orEmpty(),
            lastProgrammingResult = lastProgrammingResult,
        )
    }

    suspend fun setMode(mode: StudyContentMode) {
        val plan = requireMutablePlan()
        services.planRepository.replace(plan.copy(contentMode = mode, updatedAtEpochMillis = clock()))
    }

    suspend fun ensureProgrammingSample(): CodingExercise {
        val plan = requireMutablePlan()
        if (plan.contentMode != StudyContentMode.PROGRAMMING_COURSE) setMode(StudyContentMode.PROGRAMMING_COURSE)
        repository.codingExercises(plan.id).firstOrNull()?.let { return it }
        val now = clock()
        val profile = ProgrammingCourseProfile(
            id = ProgrammingCourseProfileId(idFactory()),
            studyPlanId = plan.id,
            primaryLanguage = ProgrammingLanguage.PYTHON,
            level = ProgrammingCourseLevel.BEGINNER,
            repositoryUri = null,
            workspaceRelativePath = "courses/${plan.id.value}",
            buildSystem = null,
            packageManager = "pip",
            tags = listOf("desktop"),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val snippet = CodeSnippet(
            id = CodeSnippetId(idFactory()),
            studyPlanId = plan.id,
            subjectId = null,
            topicId = null,
            title = "Hello snippet",
            language = ProgrammingLanguage.PYTHON,
            code = "print(\"StudyFlow Desktop\")\n",
            description = "Desktop specialized parity sample snippet.",
            sourceUri = null,
            tags = listOf("desktop", "sample"),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val exercise = CodingExercise(
            id = CodingExerciseId(idFactory()),
            studyPlanId = plan.id,
            subjectId = null,
            topicId = null,
            snippetId = snippet.id,
            type = CodingExerciseType.IMPLEMENTATION_TASK,
            title = "Hello StudyFlow",
            prompt = "Print the text StudyFlow Desktop.",
            starterCode = "print(\"StudyFlow Desktop\")\n",
            expectedOutput = "StudyFlow Desktop",
            hints = listOf("Use Python's print function."),
            solutionNotes = "A one-line print call is enough.",
            language = ProgrammingLanguage.PYTHON,
            difficulty = 1,
            status = CodingExerciseStatus.NOT_STARTED,
            runCommandTemplate = "python3 {mainFile}",
            testCommandTemplate = "python3 {mainFile}",
            files = emptyList(),
            tags = listOf("desktop", "sample"),
            lastResultJson = null,
            retryAtEpochMillis = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val project = MiniProject(
            id = MiniProjectId(idFactory()),
            studyPlanId = plan.id,
            subjectId = null,
            topicId = null,
            title = "Desktop parity mini project",
            description = "Exercise expected-output and command-template parity on Desktop.",
            status = MiniProjectStatus.NOT_STARTED,
            language = ProgrammingLanguage.PYTHON,
            repositoryUri = null,
            workspaceRelativePath = "projects/${plan.id.value}",
            checkpoints = listOf(MiniProjectCheckpoint("Run command", "Execute the configured command template.", false)),
            deliverables = listOf("main.py"),
            runCommandTemplate = "python3 {mainFile}",
            testCommandTemplate = "python3 {mainFile}",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val runtimePreference = TermuxRuntimePreference(
            studyPlanId = plan.id,
            enabled = false,
            workspaceRoot = "~/studyflow/courses",
            preferredLanguage = ProgrammingLanguage.PYTHON,
            defaultRunCommand = "python3 {mainFile}",
            defaultTestCommand = "python3 {mainFile}",
            allowedCommandTemplates = listOf("python3 {mainFile}"),
            captureStdout = true,
            captureStderr = true,
            updatedAtEpochMillis = now,
        )
        repository.upsertProgramming(profile, exercise, snippet, project, runtimePreference)
        return exercise
    }

    fun runFirstProgrammingExercise(): DesktopProgrammingExecutionResult {
        val plan = requireNotNull(services.planContentController.state().selectedPlan) { "Select a Study Plan first." }
        require(plan.contentMode == StudyContentMode.PROGRAMMING_COURSE) { "Switch to Programming Course first." }
        val exercise = repository.codingExercises(plan.id).firstOrNull()
            ?: error("Create a programming exercise first.")
        val result = programmingBackend.run(exercise)
        lastProgrammingResult = result
        val nextStatus = when {
            !result.supported -> CodingExerciseStatus.NEEDS_REVIEW
            result.timedOut || result.exitCode != 0 || !result.expectedOutputMatched -> CodingExerciseStatus.FAILED_TESTS
            else -> CodingExerciseStatus.RAN_SUCCESSFULLY
        }
        repository.updateExercise(
            exercise.copy(
                status = nextStatus,
                lastResultJson = result.toCompactRecord(),
                retryAtEpochMillis = if (nextStatus == CodingExerciseStatus.FAILED_TESTS) clock() else null,
                updatedAtEpochMillis = clock(),
            ),
        )
        return result
    }

    suspend fun ensureLanguageSample(): LanguageExpression {
        val plan = requireMutablePlan()
        if (plan.contentMode != StudyContentMode.LANGUAGE_DECK) setMode(StudyContentMode.LANGUAGE_DECK)
        repository.languageExpressions(plan.id).firstOrNull()?.let { return it }
        val now = clock()
        val deck = LanguageDeckProfile(
            id = LanguageDeckId(idFactory()), studyPlanId = plan.id, targetLanguage = "English",
            nativeLanguage = "Portuguese", regionFocus = null, defaultRegister = LanguageRegister.NEUTRAL,
            reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
            tags = listOf("desktop"), createdAtEpochMillis = now, updatedAtEpochMillis = now,
        )
        val expression = LanguageExpression(
            id = LanguageExpressionId(idFactory()), studyPlanId = plan.id, deckId = deck.id,
            expression = "break the ice", meaning = "start a friendly conversation", literalTranslation = null,
            translationEquivalent = "quebrar o gelo", type = LanguageExpressionType.IDIOM,
            register = LanguageRegister.NEUTRAL, region = null, pronunciationNote = null,
            commonMistake = "Using it for literally breaking frozen water.",
            examples = listOf(UsageExample("A joke helped break the ice.", null, null)),
            tags = listOf("conversation"), createdAtEpochMillis = now, updatedAtEpochMillis = now,
        )
        repository.upsertLanguage(deck, expression, expression.toIdiomCard(IdiomCardId(idFactory())))
        val vocabularyExpression = LanguageExpression(
            id = LanguageExpressionId(idFactory()), studyPlanId = plan.id, deckId = deck.id,
            expression = "workspace", meaning = "a place where work happens", literalTranslation = null,
            translationEquivalent = "espaço de trabalho", type = LanguageExpressionType.VOCABULARY,
            register = LanguageRegister.NEUTRAL, region = null, pronunciationNote = null,
            commonMistake = null,
            examples = listOf(UsageExample("The desktop workspace saves your progress.", null, null)),
            tags = listOf("desktop"), createdAtEpochMillis = now, updatedAtEpochMillis = now,
        )
        repository.upsertLanguage(
            deck = deck,
            expression = vocabularyExpression,
            vocabularyCard = VocabularyCard(
                id = VocabularyCardId(idFactory()),
                expressionId = vocabularyExpression.id,
                partOfSpeech = "noun",
                synonyms = listOf("work area"),
                antonyms = emptyList(),
                reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.VOCABULARY),
                difficulty = 1,
            ),
        )
        return expression
    }

    fun reviewFirstLanguageExpression(answer: String): LanguageReviewAttempt {
        val plan = requireNotNull(services.planContentController.state().selectedPlan) { "Select a Study Plan first." }
        require(plan.contentMode == StudyContentMode.LANGUAGE_DECK) { "Switch to Language Deck first." }
        val expression = repository.languageExpressions(plan.id).firstOrNull() ?: error("Create a language card first.")
        val idiom = repository.idiomCards(plan.id).firstOrNull { it.expressionId == expression.id }
            ?: error("Language card is missing its idiom review model.")
        val prompt = buildLanguageReviewPrompt(expression, idiom, LanguageReviewDirection.EXPRESSION_TO_MEANING)
        val attempt = evaluateLanguageReviewAnswer(
            prompt = prompt,
            attemptId = LanguageReviewAttemptId(idFactory()),
            answerText = answer,
            createdAtEpochMillis = clock(),
        )
        repository.addLanguageAttempt(attempt)
        return attempt
    }

    private fun requireMutablePlan() = requireNotNull(services.planContentController.state().selectedPlan) {
        "Select a Study Plan first."
    }.also { plan ->
        require(plan.status !in setOf(StudyPlanStatus.ARCHIVED, StudyPlanStatus.TRASHED, StudyPlanStatus.COMPLETED)) {
            "Restore or reactivate the Study Plan before changing its content mode."
        }
    }
}

class DesktopProgrammingExecutionBackend(private val workspaceRoot: Path?) {
    fun run(exercise: CodingExercise): DesktopProgrammingExecutionResult {
        val root = workspaceRoot ?: return unsupported("Persistent Desktop workspace is required.")
        val command = commandFor(exercise)
            ?: return unsupported("${exercise.language.displayName} runner is unavailable.")
        val directory = root.resolve("content-mode-files")
            .resolve(exercise.studyPlanId.value).resolve(exercise.id.value).toAbsolutePath().normalize()
        val allowedRoot = root.resolve("content-mode-files").toAbsolutePath().normalize()
        require(directory.startsWith(allowedRoot)) { "Programming workspace escaped the Desktop data root." }
        Files.createDirectories(directory)
        exerciseFileBundle(exercise).forEach { (relative, contents) ->
            val target = directory.resolve(relative).normalize()
            require(target.startsWith(directory)) { "Programming exercise file escaped its workspace." }
            Files.createDirectories(target.parent)
            Files.writeString(target, contents, StandardCharsets.UTF_8)
        }
        val process = runCatching { ProcessBuilder(command).directory(directory.toFile()).start() }
            .getOrElse { return unsupported("Runner failed to start: ${it.message.orEmpty()}") }
        var stdout = ""
        var stderr = ""
        val outThread = thread(isDaemon = true) { stdout = process.inputStream.bufferedReader().use { it.readText() } }
        val errThread = thread(isDaemon = true) { stderr = process.errorStream.bufferedReader().use { it.readText() } }
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(1_000)
        errThread.join(1_000)
        val exitCode = if (finished) process.exitValue() else null
        val outputMatched = expectedOutputMatches(exercise, stdout, finished, exitCode)
        return DesktopProgrammingExecutionResult(
            supported = true,
            exitCode = exitCode,
            stdout = stdout.take(65_536),
            stderr = stderr.take(65_536),
            commandLabel = command.joinToString(" "),
            timedOut = !finished,
            expectedOutputMatched = outputMatched,
        )
    }

    private fun commandFor(exercise: CodingExercise): List<String>? {
        val template = exercise.testCommandTemplate?.takeIf(String::isNotBlank)
            ?: exercise.runCommandTemplate?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            tokenizeTermuxCommandTemplate(template).map { token -> resolveTemplateToken(token, exercise) }
        }.getOrNull()
    }

    private fun resolveTemplateToken(token: String, exercise: CodingExercise): String = token
        .replace("{mainFile}", defaultMainFile(exercise))
        .replace("{exerciseFile}", defaultMainFile(exercise))
        .replace("{expectedOutput}", exercise.expectedOutput.orEmpty())

    private fun expectedOutputMatches(
        exercise: CodingExercise,
        stdout: String,
        finished: Boolean,
        exitCode: Int?,
    ): Boolean {
        val expected = exercise.expectedOutput ?: return true
        return finished && exitCode == 0 && normalizeProgramOutput(stdout) == normalizeProgramOutput(expected)
    }

    private fun normalizeProgramOutput(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

    private fun exerciseFileBundle(exercise: CodingExercise): List<Pair<String, String>> {
        if (exercise.files.isNotEmpty()) return exercise.files.map { it.relativePath to it.contents }
        return listOf(defaultMainFile(exercise) to exercise.starterCode)
    }

    private fun defaultMainFile(exercise: CodingExercise): String = when (exercise.language) {
        ProgrammingLanguage.PYTHON -> "main.py"
        ProgrammingLanguage.JAVASCRIPT -> "index.js"
        ProgrammingLanguage.TYPESCRIPT -> "index.ts"
        ProgrammingLanguage.SHELL -> "main.sh"
        ProgrammingLanguage.GO -> "main.go"
        ProgrammingLanguage.KOTLIN -> "Main.kt"
        ProgrammingLanguage.JAVA -> "Main.java"
        ProgrammingLanguage.RUST -> "main.rs"
        ProgrammingLanguage.C -> "main.c"
        ProgrammingLanguage.CPP -> "main.cpp"
        else -> "exercise.txt"
    }

    private fun unsupported(reason: String) = DesktopProgrammingExecutionResult(
        supported = false,
        exitCode = null,
        stdout = "",
        stderr = reason,
        commandLabel = "unavailable",
        timedOut = false,
        expectedOutputMatched = false,
    )
}

private fun DesktopProgrammingExecutionResult.toCompactRecord(): String = listOf(
    "supported=$supported", "exitCode=${exitCode ?: "none"}", "timedOut=$timedOut",
    "expectedOutputMatched=$expectedOutputMatched",
    "stdout=${stdout.take(512)}", "stderr=${stderr.take(512)}",
).joinToString(";")
