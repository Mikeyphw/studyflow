package com.mikeyphw.studyflow.desktop

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
import com.mikeyphw.studyflow.core.model.ProgrammingCourseLevel
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfileId
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.StudyContentMode
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.UsageExample
import com.mikeyphw.studyflow.core.model.buildLanguageReviewPrompt
import com.mikeyphw.studyflow.core.model.defaultLanguageReviewDirections
import com.mikeyphw.studyflow.core.model.evaluateLanguageReviewAnswer
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
)

data class DesktopContentModeState(
    val mode: StudyContentMode,
    val programmingProfile: ProgrammingCourseProfile?,
    val codingExercises: List<CodingExercise>,
    val languageDeck: LanguageDeckProfile?,
    val languageExpressions: List<LanguageExpression>,
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
            codingExercises = planId?.let(repository::codingExercises).orEmpty(),
            languageDeck = planId?.let(repository::languageDeck),
            languageExpressions = planId?.let(repository::languageExpressions).orEmpty(),
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
        val exercise = CodingExercise(
            id = CodingExerciseId(idFactory()),
            studyPlanId = plan.id,
            subjectId = null,
            topicId = null,
            snippetId = null,
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
            runCommandTemplate = "python3 main.py",
            testCommandTemplate = "python3 main.py",
            files = emptyList(),
            tags = listOf("desktop", "sample"),
            lastResultJson = null,
            retryAtEpochMillis = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        repository.upsertProgramming(profile, exercise)
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
            result.timedOut || result.exitCode != 0 -> CodingExerciseStatus.FAILED_TESTS
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
        return DesktopProgrammingExecutionResult(
            supported = true,
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdout.take(65_536),
            stderr = stderr.take(65_536),
            commandLabel = command.joinToString(" "),
            timedOut = !finished,
        )
    }

    private fun commandFor(exercise: CodingExercise): List<String>? = when (exercise.language) {
        ProgrammingLanguage.PYTHON -> listOf("python3", "main.py")
        ProgrammingLanguage.JAVASCRIPT -> listOf("node", "index.js")
        ProgrammingLanguage.SHELL -> listOf("bash", "main.sh")
        ProgrammingLanguage.GO -> listOf("go", "run", "main.go")
        else -> null
    }

    private fun exerciseFileBundle(exercise: CodingExercise): List<Pair<String, String>> {
        if (exercise.files.isNotEmpty()) return exercise.files.map { it.relativePath to it.contents }
        val file = when (exercise.language) {
            ProgrammingLanguage.PYTHON -> "main.py"
            ProgrammingLanguage.JAVASCRIPT -> "index.js"
            ProgrammingLanguage.SHELL -> "main.sh"
            ProgrammingLanguage.GO -> "main.go"
            else -> "exercise.txt"
        }
        return listOf(file to exercise.starterCode)
    }

    private fun unsupported(reason: String) = DesktopProgrammingExecutionResult(
        supported = false,
        exitCode = null,
        stdout = "",
        stderr = reason,
        commandLabel = "unavailable",
        timedOut = false,
    )
}

private fun DesktopProgrammingExecutionResult.toCompactRecord(): String = listOf(
    "supported=$supported", "exitCode=${exitCode ?: "none"}", "timedOut=$timedOut",
    "stdout=${stdout.take(512)}", "stderr=${stderr.take(512)}",
).joinToString(";")
