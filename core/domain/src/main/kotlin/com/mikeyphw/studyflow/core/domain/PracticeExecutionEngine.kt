package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val QuestionIdsPrefix = "questionIds="
private const val AnswerPrefix = "answer="
private const val FieldSeparator = "|"

class PracticeExecutionEngine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun stateForTest(test: StudyTest, questions: List<Question>): PracticeExecutionState =
        PracticeAttemptCodec.decode(
            attemptId = test.id.value,
            title = test.title,
            questionCount = test.questionCount,
            configuration = test.configurationJson,
            questions = questions,
            startedAtEpochMillis = test.startedAtEpochMillis,
            submittedAtEpochMillis = test.submittedAtEpochMillis,
            timeLimitSeconds = test.timeLimitSeconds,
        )

    fun stateForMockExam(mockExam: MockExam, questions: List<Question>): PracticeExecutionState =
        PracticeAttemptCodec.decode(
            attemptId = mockExam.id.value,
            title = mockExam.title,
            questionCount = mockExam.questionCount,
            configuration = mockExam.configurationJson,
            questions = questions,
            startedAtEpochMillis = mockExam.startedAtEpochMillis,
            submittedAtEpochMillis = mockExam.submittedAtEpochMillis,
            timeLimitSeconds = mockExam.timeLimitSeconds,
        )

    fun answer(
        state: PracticeExecutionState,
        question: Question,
        chosenAnswer: String,
        elapsedSeconds: Long,
    ): PracticeExecutionState {
        require(!state.submitted) { "Submitted attempts cannot be changed." }
        require(state.questionIds.contains(question.id)) { "Question does not belong to this attempt." }
        val normalizedAnswer = chosenAnswer.trim()
        val record = PracticeAnswerRecord(
            questionId = question.id,
            chosenAnswer = normalizedAnswer,
            correctAnswer = question.correctAnswer,
            correct = normalizedAnswer.equals(question.correctAnswer.trim(), ignoreCase = true),
            elapsedSeconds = elapsedSeconds.coerceAtLeast(0),
            answeredAtEpochMillis = clock(),
        )
        val answers = state.answers.filterNot { it.questionId == question.id } + record
        return state.copy(answers = answers.sortedBy { state.questionIds.indexOf(it.questionId) })
    }

    fun finish(state: PracticeExecutionState): PracticeExecutionSummary = PracticeExecutionSummary(
        answeredCount = state.answers.size,
        correctCount = state.answers.count { it.correct },
        totalCount = state.questionIds.size,
        elapsedSeconds = state.answers.sumOf { it.elapsedSeconds },
        scorePercent = if (state.answers.isEmpty()) {
            0
        } else {
            state.answers.count { it.correct } * 100 / state.answers.size
        },
        finishedAtEpochMillis = clock(),
    )
}

data class PracticeExecutionState(
    val attemptId: String,
    val title: String,
    val questionIds: List<QuestionId>,
    val questions: List<Question>,
    val answers: List<PracticeAnswerRecord>,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val timeLimitSeconds: Long?,
) {
    val submitted: Boolean get() = submittedAtEpochMillis != null
    val answeredCount: Int get() = answers.size
    val correctCount: Int get() = answers.count { it.correct }
    val currentQuestion: Question? get() = questions.firstOrNull { question ->
        answers.none { answer -> answer.questionId == question.id }
    } ?: questions.firstOrNull()
}

data class PracticeAnswerRecord(
    val questionId: QuestionId,
    val chosenAnswer: String,
    val correctAnswer: String,
    val correct: Boolean,
    val elapsedSeconds: Long,
    val answeredAtEpochMillis: Long,
)

data class PracticeExecutionSummary(
    val answeredCount: Int,
    val correctCount: Int,
    val totalCount: Int,
    val elapsedSeconds: Long,
    val scorePercent: Int,
    val finishedAtEpochMillis: Long,
)

object PracticeAttemptCodec {
    fun decode(
        attemptId: String,
        title: String,
        questionCount: Int,
        configuration: String,
        questions: List<Question>,
        startedAtEpochMillis: Long?,
        submittedAtEpochMillis: Long?,
        timeLimitSeconds: Long?,
    ): PracticeExecutionState {
        val lines = configuration.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val ids = lines.firstOrNull { it.startsWith(QuestionIdsPrefix) }
            ?.removePrefix(QuestionIdsPrefix)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.map(::QuestionId)
            .orEmpty()
            .ifEmpty { questions.take(questionCount).map { it.id } }
        val knownQuestions = questions.associateBy { it.id }
        val answerRecords = lines
            .filter { it.startsWith(AnswerPrefix) }
            .mapNotNull { decodeAnswerLine(it.removePrefix(AnswerPrefix)) }
            .filter { ids.contains(it.questionId) }
        return PracticeExecutionState(
            attemptId = attemptId,
            title = title,
            questionIds = ids,
            questions = ids.mapNotNull { knownQuestions[it] },
            answers = answerRecords,
            startedAtEpochMillis = startedAtEpochMillis,
            submittedAtEpochMillis = submittedAtEpochMillis,
            timeLimitSeconds = timeLimitSeconds,
        )
    }

    fun encode(state: PracticeExecutionState): String = buildString {
        append(QuestionIdsPrefix)
        appendLine(state.questionIds.joinToString(",") { it.value })
        state.answers.forEach { answer ->
            append(AnswerPrefix)
            append(answer.questionId.value.b64())
            append(FieldSeparator)
            append(answer.chosenAnswer.b64())
            append(FieldSeparator)
            append(answer.correctAnswer.b64())
            append(FieldSeparator)
            append(answer.correct)
            append(FieldSeparator)
            append(answer.elapsedSeconds)
            append(FieldSeparator)
            appendLine(answer.answeredAtEpochMillis)
        }
    }.trimEnd()

    private fun decodeAnswerLine(value: String): PracticeAnswerRecord? {
        val parts = value.split(FieldSeparator)
        if (parts.size != 6) return null
        return runCatching {
            PracticeAnswerRecord(
                questionId = QuestionId(parts[0].fromB64()),
                chosenAnswer = parts[1].fromB64(),
                correctAnswer = parts[2].fromB64(),
                correct = parts[3].toBooleanStrict(),
                elapsedSeconds = parts[4].toLong(),
                answeredAtEpochMillis = parts[5].toLong(),
            )
        }.getOrNull()
    }

    private fun String.b64(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(toByteArray(StandardCharsets.UTF_8))

    private fun String.fromB64(): String = String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}

class ManagePracticeExecution(
    private val repository: StudyPracticeRepository,
    private val engine: PracticeExecutionEngine = PracticeExecutionEngine(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun startTest(planId: StudyPlanId, testId: StudyTestId): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        return repository.upsertTest(test.copy(startedAtEpochMillis = test.startedAtEpochMillis ?: clock()))
    }

    suspend fun answerTestQuestion(
        planId: StudyPlanId,
        testId: StudyTestId,
        questionId: QuestionId,
        chosenAnswer: String,
        elapsedSeconds: Long = 0,
    ): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        val question = requireNotNull(repository.findQuestion(planId, questionId)) { "Question does not exist." }
        val questions = questionsForAttempt(planId, test.configurationJson)
        val current = engine.stateForTest(test, questions)
        val next = engine.answer(current, question, chosenAnswer, elapsedSeconds)
        return repository.upsertTest(
            test.copy(
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                startedAtEpochMillis = test.startedAtEpochMillis ?: clock(),
                configurationJson = PracticeAttemptCodec.encode(next),
            ),
        )
    }

    suspend fun finishTest(planId: StudyPlanId, testId: StudyTestId): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        val questions = questionsForAttempt(planId, test.configurationJson)
        val state = engine.stateForTest(test, questions)
        val summary = engine.finish(state)
        return repository.upsertTest(
            test.copy(
                answeredCount = summary.answeredCount,
                correctCount = summary.correctCount,
                submittedAtEpochMillis = summary.finishedAtEpochMillis,
                configurationJson = PracticeAttemptCodec.encode(state),
            ),
        )
    }

    suspend fun startMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam {
        val mockExam = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        return repository.upsertMockExam(mockExam.copy(startedAtEpochMillis = mockExam.startedAtEpochMillis ?: clock()))
    }

    suspend fun answerMockQuestion(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        questionId: QuestionId,
        chosenAnswer: String,
        elapsedSeconds: Long = 0,
    ): MockExam {
        val mockExam = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        val question = requireNotNull(repository.findQuestion(planId, questionId)) { "Question does not exist." }
        val questions = questionsForAttempt(planId, mockExam.configurationJson)
        val current = engine.stateForMockExam(mockExam, questions)
        val next = engine.answer(current, question, chosenAnswer, elapsedSeconds)
        return repository.upsertMockExam(
            mockExam.copy(
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                startedAtEpochMillis = mockExam.startedAtEpochMillis ?: clock(),
                configurationJson = PracticeAttemptCodec.encode(next),
            ),
        )
    }

    suspend fun finishMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam {
        val mockExam = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        val questions = questionsForAttempt(planId, mockExam.configurationJson)
        val state = engine.stateForMockExam(mockExam, questions)
        val summary = engine.finish(state)
        return repository.upsertMockExam(
            mockExam.copy(
                answeredCount = summary.answeredCount,
                correctCount = summary.correctCount,
                scorePercent = summary.scorePercent.toDouble(),
                submittedAtEpochMillis = summary.finishedAtEpochMillis,
                configurationJson = PracticeAttemptCodec.encode(state),
            ),
        )
    }

    fun stateForTest(test: StudyTest, questions: List<Question>): PracticeExecutionState =
        engine.stateForTest(test, questions)

    fun stateForMockExam(mockExam: MockExam, questions: List<Question>): PracticeExecutionState =
        engine.stateForMockExam(mockExam, questions)

    private suspend fun questionsForAttempt(planId: StudyPlanId, configuration: String): List<Question> {
        val ids = configuration.lineSequence()
            .firstOrNull { it.startsWith(QuestionIdsPrefix) }
            ?.removePrefix(QuestionIdsPrefix)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.map(::QuestionId)
            .orEmpty()
        return ids.mapNotNull { questionId -> repository.findQuestion(planId, questionId) }
    }
}
