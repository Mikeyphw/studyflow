package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.TestMode
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private const val QuestionIdsPrefix = "questionIds="
private const val AnswerPrefix = "answer="
private const val QuestionSnapshotPrefix = "questionSnapshotV1="
private const val FieldSeparator = "|"
private const val Base64UrlAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

class PracticeExecutionEngine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun stateForTest(test: StudyTest, questions: List<Question>): PracticeExecutionState =
        PracticeAttemptCodec.decode(
            attemptId = test.id.value,
            studyPlanId = test.studyPlanId,
            title = test.title,
            mode = test.mode,
            questionCount = test.questionCount,
            configuration = test.configurationJson,
            questions = questions,
            startedAtEpochMillis = test.startedAtEpochMillis,
            submittedAtEpochMillis = test.submittedAtEpochMillis,
            timeLimitSeconds = test.timeLimitSeconds,
            negativeMarking = 0.0,
            minimumScorePercent = null,
        )

    fun stateForMockExam(mockExam: MockExam, questions: List<Question>): PracticeExecutionState =
        PracticeAttemptCodec.decode(
            attemptId = mockExam.id.value,
            studyPlanId = mockExam.studyPlanId,
            title = mockExam.title,
            mode = mockExam.mode,
            questionCount = mockExam.questionCount,
            configuration = mockExam.configurationJson,
            questions = questions,
            startedAtEpochMillis = mockExam.startedAtEpochMillis,
            submittedAtEpochMillis = mockExam.submittedAtEpochMillis,
            timeLimitSeconds = mockExam.timeLimitSeconds,
            negativeMarking = mockExam.negativeMarking,
            minimumScorePercent = mockExam.minimumScorePercent,
        )

    fun answer(
        state: PracticeExecutionState,
        question: Question,
        chosenAnswer: String,
        elapsedSeconds: Long,
        errorCategory: ErrorCategory? = null,
    ): PracticeExecutionState {
        require(!state.submitted) { "Submitted attempts cannot be changed." }
        require(state.startedAtEpochMillis != null) { "Attempt must be explicitly started before answering." }
        require(!state.deadlinePassed(clock())) { "Attempt deadline has elapsed; submit the attempt." }
        val frozenQuestion = requireNotNull(state.questions.firstOrNull { it.id == question.id }) {
            "Question does not belong to this attempt."
        }
        require(elapsedSeconds >= 0L) { "Answer elapsed time cannot be negative." }
        val normalizedAnswer = chosenAnswer.trim()
        require(normalizedAnswer.isNotBlank()) { "Answer cannot be blank." }
        val record = PracticeAnswerRecord(
            questionId = frozenQuestion.id,
            chosenAnswer = normalizedAnswer,
            correctAnswer = frozenQuestion.correctAnswer,
            correct = normalizedAnswer.equals(frozenQuestion.correctAnswer.trim(), ignoreCase = true),
            elapsedSeconds = elapsedSeconds,
            answeredAtEpochMillis = clock(),
            errorCategory = if (normalizedAnswer.equals(frozenQuestion.correctAnswer.trim(), ignoreCase = true)) {
                null
            } else {
                errorCategory ?: ErrorCategory.OTHER
            },
        )
        val answers = state.answers.filterNot { it.questionId == question.id } + record
        return state.copy(answers = answers.sortedBy { state.questionIds.indexOf(it.questionId) })
    }

    fun finish(state: PracticeExecutionState): PracticeExecutionSummary {
        require(state.startedAtEpochMillis != null) { "Attempt must be explicitly started before submission." }
        require(!state.submitted) { "Attempt is already terminal." }
        val answered = state.answers.size
        val correct = state.answers.count(PracticeAnswerRecord::correct)
        val incorrect = answered - correct
        val rawPoints = correct.toDouble() - incorrect.toDouble() * state.negativeMarking
        val score = if (state.questionIds.isEmpty()) 0 else {
            ((rawPoints.coerceAtLeast(0.0) / state.questionIds.size.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        }
        return PracticeExecutionSummary(
            answeredCount = answered,
            correctCount = correct,
            totalCount = state.questionIds.size,
            elapsedSeconds = state.answers.sumOf(PracticeAnswerRecord::elapsedSeconds),
            scorePercent = score,
            passed = state.minimumScorePercent?.let { score >= it },
            finishedAtEpochMillis = clock(),
        )
    }
}

data class PracticeExecutionState(
    val attemptId: String,
    val title: String,
    val mode: TestMode,
    val questionIds: List<QuestionId>,
    val questions: List<Question>,
    val answers: List<PracticeAnswerRecord>,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val timeLimitSeconds: Long?,
    val negativeMarking: Double,
    val minimumScorePercent: Int?,
    val metadataLines: List<String> = emptyList(),
) {
    val submitted: Boolean get() = submittedAtEpochMillis != null
    val answeredCount: Int get() = answers.size
    val correctCount: Int get() = answers.count(PracticeAnswerRecord::correct)
    val deadlineEpochMillis: Long? get() = if (startedAtEpochMillis != null && timeLimitSeconds != null) {
        startedAtEpochMillis + timeLimitSeconds * 1_000L
    } else null
    val currentQuestion: Question? get() = questions.firstOrNull { question ->
        answers.none { answer -> answer.questionId == question.id }
    } ?: questions.firstOrNull()

    fun deadlinePassed(nowEpochMillis: Long): Boolean = deadlineEpochMillis?.let { nowEpochMillis >= it } == true

    /** EXAM/PRACTICE hide explanations until terminal submission; IMMEDIATE_FEEDBACK reveals after answering. */
    fun explanationVisible(questionId: QuestionId): Boolean = when (mode) {
        TestMode.IMMEDIATE_FEEDBACK -> submitted || answers.any { it.questionId == questionId }
        TestMode.PRACTICE, TestMode.EXAM -> submitted
    }
}

data class PracticeAnswerRecord(
    val questionId: QuestionId,
    val chosenAnswer: String,
    val correctAnswer: String,
    val correct: Boolean,
    val elapsedSeconds: Long,
    val answeredAtEpochMillis: Long,
    val errorCategory: ErrorCategory? = null,
)

data class PracticeExecutionSummary(
    val answeredCount: Int,
    val correctCount: Int,
    val totalCount: Int,
    val elapsedSeconds: Long,
    val scorePercent: Int,
    val passed: Boolean?,
    val finishedAtEpochMillis: Long,
)

object PracticeAttemptCodec {
    fun decode(
        attemptId: String,
        studyPlanId: StudyPlanId,
        title: String,
        mode: TestMode = TestMode.PRACTICE,
        questionCount: Int,
        configuration: String,
        questions: List<Question>,
        startedAtEpochMillis: Long?,
        submittedAtEpochMillis: Long?,
        timeLimitSeconds: Long?,
        negativeMarking: Double = 0.0,
        minimumScorePercent: Int? = null,
    ): PracticeExecutionState {
        val lines = configuration.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val ids = requireQuestionIds(lines, questionCount)
        val frozen = lines.filter { it.startsWith(QuestionSnapshotPrefix) }
            .map(::decodeQuestionSnapshot)
        val resolvedQuestions = if (frozen.isNotEmpty()) {
            require(frozen.map(Question::id).distinct().size == frozen.size) {
                "Practice attempt contains duplicate frozen question identities."
            }
            val byId = frozen.associateBy(Question::id)
            require(byId.keys == ids.toSet()) {
                "Practice attempt frozen questions do not match the recorded question identity."
            }
            ids.map { id -> byId.getValue(id).copy(studyPlanId = studyPlanId) }
        } else {
            require(startedAtEpochMillis == null) {
                "Started practice attempts must contain immutable question snapshots."
            }
            val knownQuestions = questions.associateBy(Question::id)
            require(ids.all(knownQuestions::containsKey)) {
                "Practice attempt references missing questions; refusing mutable legacy fallback."
            }
            ids.map(knownQuestions::getValue)
        }
        val answerRecords = lines
            .filter { it.startsWith(AnswerPrefix) }
            .mapNotNull { decodeAnswerLine(it.removePrefix(AnswerPrefix)) }
            .filter { ids.contains(it.questionId) }
            .distinctBy(PracticeAnswerRecord::questionId)
        val metadata = lines.filterNot {
            it.startsWith(QuestionIdsPrefix) || it.startsWith(QuestionSnapshotPrefix) || it.startsWith(AnswerPrefix)
        }
        return PracticeExecutionState(
            attemptId = attemptId,
            title = title,
            mode = mode,
            questionIds = ids,
            questions = resolvedQuestions,
            answers = answerRecords,
            startedAtEpochMillis = startedAtEpochMillis,
            submittedAtEpochMillis = submittedAtEpochMillis,
            timeLimitSeconds = timeLimitSeconds,
            negativeMarking = negativeMarking,
            minimumScorePercent = minimumScorePercent,
            metadataLines = metadata,
        )
    }

    fun configurationForQuestions(
        questions: List<Question>,
        metadataLines: List<String> = emptyList(),
    ): String {
        require(questions.isNotEmpty()) { "Practice attempt needs at least one question." }
        require(questions.map(Question::id).distinct().size == questions.size) {
            "Practice attempt contains duplicate question identities."
        }
        val owner = questions.first().studyPlanId
        require(questions.all { it.studyPlanId == owner }) { "Practice attempt questions must belong to one Study Plan." }
        return encodeConfiguration(questions, metadataLines, emptyList())
    }

    fun freezeConfiguration(configuration: String, questions: List<Question>): String {
        val lines = configuration.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val ids = requireQuestionIds(lines, questions.size)
        val byId = questions.associateBy(Question::id)
        require(byId.size == questions.size && ids.all(byId::containsKey)) {
            "Cannot freeze a practice attempt whose question identities do not resolve exactly."
        }
        val ordered = ids.map(byId::getValue)
        val metadata = lines.filterNot {
            it.startsWith(QuestionIdsPrefix) || it.startsWith(QuestionSnapshotPrefix) || it.startsWith(AnswerPrefix)
        }
        val answers = lines.filter { it.startsWith(AnswerPrefix) }
            .mapNotNull { decodeAnswerLine(it.removePrefix(AnswerPrefix)) }
        return encodeConfiguration(ordered, metadata, answers)
    }

    fun hasFrozenQuestions(configuration: String): Boolean = configuration.lineSequence()
        .map(String::trim)
        .any { it.startsWith(QuestionSnapshotPrefix) }

    fun encode(state: PracticeExecutionState): String = encodeConfiguration(
        questions = state.questions,
        metadataLines = state.metadataLines,
        answers = state.answers,
    )

    fun referencedQuestionIds(configuration: String): Set<QuestionId> {
        val lines = configuration.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val idLine = lines.firstOrNull { it.startsWith(QuestionIdsPrefix) } ?: return emptySet()
        return idLine.removePrefix(QuestionIdsPrefix)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::QuestionId)
            .toSet()
    }

    private fun requireQuestionIds(lines: List<String>, questionCount: Int): List<QuestionId> {
        val records = lines.filter { it.startsWith(QuestionIdsPrefix) }
        require(records.size == 1) { "Practice attempt must contain exactly one question identity record." }
        val ids = records.single().removePrefix(QuestionIdsPrefix)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::QuestionId)
        require(ids.isNotEmpty()) { "Practice attempt question identity cannot be empty." }
        require(ids.distinct().size == ids.size) { "Practice attempt question identity contains duplicates." }
        require(ids.size == questionCount) {
            "Practice attempt question identity does not match its recorded question count."
        }
        return ids
    }

    private fun encodeConfiguration(
        questions: List<Question>,
        metadataLines: List<String>,
        answers: List<PracticeAnswerRecord>,
    ): String = buildString {
        append(QuestionIdsPrefix)
        appendLine(questions.joinToString(",") { it.id.value })
        questions.forEach { question -> appendLine(encodeQuestionSnapshot(question)) }
        metadataLines.map(String::trim).filter(String::isNotBlank).forEach { line ->
            require(!line.startsWith(QuestionIdsPrefix) && !line.startsWith(QuestionSnapshotPrefix) &&
                !line.startsWith(AnswerPrefix)) { "Practice metadata collides with a reserved attempt record." }
            appendLine(line)
        }
        answers.forEach { answer ->
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
            append(answer.answeredAtEpochMillis)
            append(FieldSeparator)
            appendLine(answer.errorCategory?.name.orEmpty())
        }
    }.trimEnd()

    private fun encodeQuestionSnapshot(question: Question): String = buildString {
        append(QuestionSnapshotPrefix)
        append(question.id.value.b64())
        append(FieldSeparator).append(question.type.name)
        append(FieldSeparator).append(question.statement.b64())
        append(FieldSeparator).append(question.correctAnswer.b64())
        append(FieldSeparator).append(question.explanation.b64())
        append(FieldSeparator).append(question.difficulty)
        append(FieldSeparator).append(question.favorite)
        append(FieldSeparator).append(question.disputed)
        append(FieldSeparator).append(question.options.size)
        question.options.forEach { option -> append(FieldSeparator).append(option.b64()) }
    }

    private fun decodeQuestionSnapshot(line: String): Question {
        val parts = line.removePrefix(QuestionSnapshotPrefix).split(FieldSeparator)
        require(parts.size >= QuestionSnapshotFixedFields) { "Practice attempt contains an invalid question snapshot." }
        return runCatching {
            val optionCount = parts[8].toInt()
            require(optionCount >= 0 && parts.size == QuestionSnapshotFixedFields + optionCount)
            Question(
                id = QuestionId(parts[0].fromB64()),
                studyPlanId = StudyPlanId(FrozenOwnerPlaceholder),
                subjectId = null,
                topicId = null,
                type = QuestionType.valueOf(parts[1]),
                statement = parts[2].fromB64(),
                options = parts.drop(QuestionSnapshotFixedFields).map { it.fromB64() },
                correctAnswer = parts[3].fromB64(),
                explanation = parts[4].fromB64(),
                examBoard = null,
                source = null,
                year = null,
                difficulty = parts[5].toInt(),
                favorite = parts[6].toBooleanStrict(),
                disputed = parts[7].toBooleanStrict(),
                tags = emptyList(),
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            )
        }.getOrElse { throw IllegalArgumentException("Practice attempt contains an invalid question snapshot.", it) }
    }

    private fun decodeAnswerLine(value: String): PracticeAnswerRecord? {
        val parts = value.split(FieldSeparator)
        if (parts.size !in 6..7) return null
        return runCatching {
            PracticeAnswerRecord(
                questionId = QuestionId(parts[0].fromB64()),
                chosenAnswer = parts[1].fromB64(),
                correctAnswer = parts[2].fromB64(),
                correct = parts[3].toBooleanStrict(),
                elapsedSeconds = parts[4].toLong().coerceAtLeast(0L),
                answeredAtEpochMillis = parts[5].toLong(),
                errorCategory = parts.getOrNull(6)?.takeIf(String::isNotBlank)?.let(ErrorCategory::valueOf),
            )
        }.getOrNull()
    }

    private fun String.b64(): String = toByteArray(StandardCharsets.UTF_8).toBase64UrlNoPadding()
    private fun String.fromB64(): String = String(fromBase64UrlNoPadding(), StandardCharsets.UTF_8)

    private fun ByteArray.toBase64UrlNoPadding(): String = buildString((size * 4 + 2) / 3) {
        var index = 0
        while (index + 2 < size) {
            val chunk = ((this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 16) or
                ((this@toBase64UrlNoPadding[index + 1].toInt() and 0xFF) shl 8) or
                (this@toBase64UrlNoPadding[index + 2].toInt() and 0xFF)
            append(Base64UrlAlphabet[(chunk ushr 18) and 0x3F])
            append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
            append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
            append(Base64UrlAlphabet[chunk and 0x3F])
            index += 3
        }
        when (size - index) {
            1 -> {
                val chunk = (this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 4
                append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                append(Base64UrlAlphabet[chunk and 0x3F])
            }
            2 -> {
                val chunk = ((this@toBase64UrlNoPadding[index].toInt() and 0xFF) shl 10) or
                    ((this@toBase64UrlNoPadding[index + 1].toInt() and 0xFF) shl 2)
                append(Base64UrlAlphabet[(chunk ushr 12) and 0x3F])
                append(Base64UrlAlphabet[(chunk ushr 6) and 0x3F])
                append(Base64UrlAlphabet[chunk and 0x3F])
            }
        }
    }

    private fun String.fromBase64UrlNoPadding(): ByteArray {
        val output = ByteArrayOutputStream(length * 3 / 4)
        var buffer = 0
        var bits = 0
        forEach { character ->
            val value = Base64UrlAlphabet.indexOf(character)
            require(value >= 0) { "Invalid Base64Url character." }
            buffer = (buffer shl 6) or value
            bits += 6
            while (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
            }
        }
        require(bits == 0 || (buffer and ((1 shl bits) - 1)) == 0) { "Invalid Base64Url tail bits." }
        return output.toByteArray()
    }

    private const val QuestionSnapshotFixedFields = 9
    private const val FrozenOwnerPlaceholder = "__frozen_attempt_owner__"
}

class ManagePracticeExecution(
    private val repository: StudyPracticeRepository,
    private val engine: PracticeExecutionEngine = PracticeExecutionEngine(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun startTest(planId: StudyPlanId, testId: StudyTestId): StudyTest {
        val current = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        val prepared = ensureFrozen(current)
        return repository.startTestIfNotStarted(planId, prepared.id, clock())
    }

    suspend fun answerTestQuestion(
        planId: StudyPlanId,
        testId: StudyTestId,
        questionId: QuestionId,
        chosenAnswer: String,
        elapsedSeconds: Long,
        errorCategory: ErrorCategory? = null,
    ): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        val current = engine.stateForTest(test, emptyList())
        val frozenQuestion = requireNotNull(current.questions.firstOrNull { it.id == questionId }) {
            "Question does not belong to this attempt."
        }
        val next = engine.answer(current, frozenQuestion, chosenAnswer, elapsedSeconds, errorCategory)
        return repository.updateTestAttemptIfOpen(
            expectedConfiguration = test.configurationJson,
            next = test.copy(
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                configurationJson = PracticeAttemptCodec.encode(next),
            ),
        )
    }

    suspend fun finishTest(planId: StudyPlanId, testId: StudyTestId): StudyTest {
        val test = requireNotNull(repository.findTest(planId, testId)) { "Practice Test does not exist." }
        val state = engine.stateForTest(test, emptyList())
        val summary = engine.finish(state)
        return repository.submitTestIfOpen(
            expectedConfiguration = test.configurationJson,
            next = test.copy(
                answeredCount = summary.answeredCount,
                correctCount = summary.correctCount,
                submittedAtEpochMillis = summary.finishedAtEpochMillis,
                configurationJson = PracticeAttemptCodec.encode(state),
            ),
            wrongAnswers = wrongAnswers(state),
        )
    }

    suspend fun startMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam {
        val current = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        val prepared = ensureFrozen(current)
        return repository.startMockExamIfNotStarted(planId, prepared.id, clock())
    }

    suspend fun answerMockQuestion(
        planId: StudyPlanId,
        mockExamId: MockExamId,
        questionId: QuestionId,
        chosenAnswer: String,
        elapsedSeconds: Long,
        errorCategory: ErrorCategory? = null,
    ): MockExam {
        val mock = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        val current = engine.stateForMockExam(mock, emptyList())
        val frozenQuestion = requireNotNull(current.questions.firstOrNull { it.id == questionId }) {
            "Question does not belong to this attempt."
        }
        val next = engine.answer(current, frozenQuestion, chosenAnswer, elapsedSeconds, errorCategory)
        return repository.updateMockExamAttemptIfOpen(
            expectedConfiguration = mock.configurationJson,
            next = mock.copy(
                answeredCount = next.answeredCount,
                correctCount = next.correctCount,
                configurationJson = PracticeAttemptCodec.encode(next),
            ),
        )
    }

    suspend fun finishMockExam(planId: StudyPlanId, mockExamId: MockExamId): MockExam {
        val mock = requireNotNull(repository.findMockExam(planId, mockExamId)) { "Mock Exam does not exist." }
        val state = engine.stateForMockExam(mock, emptyList())
        val summary = engine.finish(state)
        val blueprintScore = ExamBlueprintCodec.decode(mock.configurationJson)?.let { frozen ->
            ExamBlueprintScorer.score(state, frozen)
        }
        return repository.submitMockExamIfOpen(
            expectedConfiguration = mock.configurationJson,
            next = mock.copy(
                answeredCount = summary.answeredCount,
                correctCount = summary.correctCount,
                scorePercent = (blueprintScore?.scorePercent ?: summary.scorePercent).toDouble(),
                submittedAtEpochMillis = summary.finishedAtEpochMillis,
                configurationJson = PracticeAttemptCodec.encode(state),
            ),
            wrongAnswers = wrongAnswers(state),
        )
    }

    fun stateForTest(test: StudyTest, questions: List<Question>): PracticeExecutionState = engine
        .stateForTest(test, questions)
    fun stateForMockExam(mockExam: MockExam, questions: List<Question>): PracticeExecutionState = engine
        .stateForMockExam(mockExam, questions)

    private suspend fun ensureFrozen(test: StudyTest): StudyTest {
        if (PracticeAttemptCodec.hasFrozenQuestions(test.configurationJson)) return test
        require(test.startedAtEpochMillis == null) {
            "Started Practice Test is missing immutable question snapshots and cannot be repaired safely."
        }
        val questions = questionsForAttempt(test.studyPlanId, test.configurationJson, test.questionCount)
        val frozen = PracticeAttemptCodec.freezeConfiguration(test.configurationJson, questions)
        return repository.updateTestAttemptIfOpen(
            expectedConfiguration = test.configurationJson,
            next = test.copy(configurationJson = frozen),
        )
    }

    private suspend fun ensureFrozen(mock: MockExam): MockExam {
        if (PracticeAttemptCodec.hasFrozenQuestions(mock.configurationJson)) return mock
        require(mock.startedAtEpochMillis == null) {
            "Started Mock Exam is missing immutable question snapshots and cannot be repaired safely."
        }
        val questions = questionsForAttempt(mock.studyPlanId, mock.configurationJson, mock.questionCount)
        val frozen = PracticeAttemptCodec.freezeConfiguration(mock.configurationJson, questions)
        return repository.updateMockExamAttemptIfOpen(
            expectedConfiguration = mock.configurationJson,
            next = mock.copy(configurationJson = frozen),
        )
    }

    private fun wrongAnswers(state: PracticeExecutionState): List<PracticeWrongAnswerCapture> = state.answers
        .filterNot(PracticeAnswerRecord::correct)
        .mapNotNull { answer ->
            state.questions.firstOrNull { it.id == answer.questionId }?.let { question ->
                PracticeWrongAnswerCapture(
                    question = question,
                    chosenAnswer = answer.chosenAnswer,
                    category = answer.errorCategory ?: ErrorCategory.OTHER,
                )
            }
        }

    private suspend fun questionsForAttempt(
        planId: StudyPlanId,
        configuration: String,
        expectedCount: Int,
    ): List<Question> {
        val ids = PracticeAttemptCodec.referencedQuestionIds(configuration).toList()
        require(ids.size == expectedCount && ids.isNotEmpty()) {
            "Practice attempt has malformed or missing question identity."
        }
        val questions = ids.map { id ->
            requireNotNull(repository.findQuestion(planId, id)) {
                "Practice attempt references a missing question and cannot be frozen safely."
            }
        }
        require(questions.all { it.studyPlanId == planId }) {
            "Practice attempt question ownership does not match the Study Plan."
        }
        return questions
    }
}
