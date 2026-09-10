package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.ExamStageId
import com.mikeyphw.studyflow.core.model.MockExam
import com.mikeyphw.studyflow.core.model.MockExamId
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionAuthorship
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyTest
import com.mikeyphw.studyflow.core.model.StudyTestId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TestMode
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

private const val PracticeSchemaVersion = 1
private const val PracticeFileName = "practice-v1.properties"
private const val NullToken = "~"

data class DesktopPracticeSnapshot(
    val questions: List<Question>,
    val tests: List<StudyTest>,
    val mockExams: List<MockExam>,
)

class DesktopPracticePersistence private constructor(
    val root: Path,
    private val file: Path,
    initial: DesktopPracticeSnapshot,
) {
    private var snapshot: DesktopPracticeSnapshot = initial

    fun current(): DesktopPracticeSnapshot = snapshot

    @Synchronized
    fun update(next: DesktopPracticeSnapshot) {
        writeAtomically(next)
        snapshot = next
    }

    private fun writeAtomically(value: DesktopPracticeSnapshot) {
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".studyflow-practice-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output -> PracticePropertiesCodec.write(output, value) }
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun open(root: Path, seed: DesktopPracticeSnapshot): DesktopPracticePersistence {
            val normalizedRoot = root.toAbsolutePath().normalize()
            Files.createDirectories(normalizedRoot)
            val file = normalizedRoot.resolve(PracticeFileName)
            val loaded = if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(PracticePropertiesCodec::read)
            } else {
                seed
            }
            return DesktopPracticePersistence(normalizedRoot, file, loaded).also { persistence ->
                if (!Files.isRegularFile(file)) persistence.writeAtomically(loaded)
            }
        }
    }
}

private object PracticePropertiesCodec {
    fun write(output: OutputStream, snapshot: DesktopPracticeSnapshot) {
        val properties = Properties()
        properties["schema.version"] = PracticeSchemaVersion.toString()
        properties["question.count"] = snapshot.questions.size.toString()
        snapshot.questions.forEachIndexed { index, value -> properties["question.$index"] = encodeQuestion(value) }
        properties["test.count"] = snapshot.tests.size.toString()
        snapshot.tests.forEachIndexed { index, value -> properties["test.$index"] = encodeTest(value) }
        properties["mock.count"] = snapshot.mockExams.size.toString()
        snapshot.mockExams.forEachIndexed { index, value -> properties["mock.$index"] = encodeMock(value) }
        properties.store(output, "StudyFlow Desktop practice schema v$PracticeSchemaVersion")
    }

    fun read(input: InputStream): DesktopPracticeSnapshot {
        val properties = Properties().apply { load(input) }
        val version = properties.required("schema.version").toInt()
        require(version == PracticeSchemaVersion) { "Unsupported Desktop practice schema: $version" }
        return DesktopPracticeSnapshot(
            questions = List(properties.required("question.count").toInt()) { index ->
                decodeQuestion(properties.required("question.$index"))
            },
            tests = List(properties.required("test.count").toInt()) { index ->
                decodeTest(properties.required("test.$index"))
            },
            mockExams = List(properties.required("mock.count").toInt()) { index ->
                decodeMock(properties.required("mock.$index"))
            },
        )
    }

    private fun encodeQuestion(value: Question): String = joinFields(
        value.id.value,
        value.studyPlanId.value,
        value.subjectId?.value,
        value.topicId?.value,
        value.type.name,
        value.statement,
        value.options.joinToString("\u001f"),
        value.correctAnswer,
        value.explanation,
        value.examBoard,
        value.source,
        value.year?.toString(),
        value.difficulty.toString(),
        value.favorite.toString(),
        value.disputed.toString(),
        value.tags.joinToString("\u001f"),
        value.createdAtEpochMillis.toString(),
        value.updatedAtEpochMillis.toString(),
        value.organization,
        value.role,
        value.examStageId?.value,
        value.authorship.name,
        value.examNoticeRevisionId?.value,
        value.externalReference,
        value.points?.toString(),
    )

    private fun decodeQuestion(encoded: String): Question {
        val f = splitFields(encoded, 25)
        return Question(
            id = QuestionId(f[0]!!),
            studyPlanId = StudyPlanId(f[1]!!),
            subjectId = f[2]?.let(::SubjectId),
            topicId = f[3]?.let(::TopicId),
            type = QuestionType.valueOf(f[4]!!),
            statement = f[5]!!,
            options = splitList(f[6]),
            correctAnswer = f[7]!!,
            explanation = f[8]!!,
            examBoard = f[9],
            source = f[10],
            year = f[11]?.toInt(),
            difficulty = f[12]!!.toInt(),
            favorite = f[13]!!.toBooleanStrict(),
            disputed = f[14]!!.toBooleanStrict(),
            tags = splitList(f[15]),
            createdAtEpochMillis = f[16]!!.toLong(),
            updatedAtEpochMillis = f[17]!!.toLong(),
            organization = f[18],
            role = f[19],
            examStageId = f[20]?.let(::ExamStageId),
            authorship = QuestionAuthorship.valueOf(f[21]!!),
            examNoticeRevisionId = f[22]?.let(::ExamNoticeRevisionId),
            externalReference = f[23],
            points = f[24]?.toDouble(),
        )
    }

    private fun encodeTest(value: StudyTest): String = joinFields(
        value.id.value,
        value.studyPlanId.value,
        value.title,
        value.mode.name,
        value.timeLimitSeconds?.toString(),
        value.questionCount.toString(),
        value.answeredCount.toString(),
        value.correctCount.toString(),
        value.startedAtEpochMillis?.toString(),
        value.submittedAtEpochMillis?.toString(),
        value.configurationJson,
    )

    private fun decodeTest(encoded: String): StudyTest {
        val f = splitFields(encoded, 11)
        return StudyTest(
            id = StudyTestId(f[0]!!),
            studyPlanId = StudyPlanId(f[1]!!),
            title = f[2]!!,
            mode = TestMode.valueOf(f[3]!!),
            timeLimitSeconds = f[4]?.toLong(),
            questionCount = f[5]!!.toInt(),
            answeredCount = f[6]!!.toInt(),
            correctCount = f[7]!!.toInt(),
            startedAtEpochMillis = f[8]?.toLong(),
            submittedAtEpochMillis = f[9]?.toLong(),
            configurationJson = f[10]!!,
        )
    }

    private fun encodeMock(value: MockExam): String = joinFields(
        value.id.value,
        value.studyPlanId.value,
        value.title,
        value.mode.name,
        value.timeLimitSeconds.toString(),
        value.questionCount.toString(),
        value.answeredCount.toString(),
        value.correctCount.toString(),
        value.scorePercent?.toString(),
        value.negativeMarking.toString(),
        value.minimumScorePercent?.toString(),
        value.startedAtEpochMillis?.toString(),
        value.submittedAtEpochMillis?.toString(),
        value.configurationJson,
    )

    private fun decodeMock(encoded: String): MockExam {
        val f = splitFields(encoded, 14)
        return MockExam(
            id = MockExamId(f[0]!!),
            studyPlanId = StudyPlanId(f[1]!!),
            title = f[2]!!,
            mode = TestMode.valueOf(f[3]!!),
            timeLimitSeconds = f[4]!!.toLong(),
            questionCount = f[5]!!.toInt(),
            answeredCount = f[6]!!.toInt(),
            correctCount = f[7]!!.toInt(),
            scorePercent = f[8]?.toDouble(),
            negativeMarking = f[9]!!.toDouble(),
            minimumScorePercent = f[10]?.toInt(),
            startedAtEpochMillis = f[11]?.toLong(),
            submittedAtEpochMillis = f[12]?.toLong(),
            configurationJson = f[13]!!,
        )
    }

    private fun joinFields(vararg fields: String?): String = fields.joinToString(".") { field ->
        field?.let(::encode) ?: NullToken
    }

    private fun splitFields(encoded: String, count: Int): List<String?> {
        val pieces = encoded.split('.')
        require(pieces.size == count) { "Corrupt Desktop practice record." }
        return pieces.map { value -> if (value == NullToken) null else decode(value) }
    }

    private fun splitList(value: String?): List<String> = value.orEmpty().split('\u001f').filter(String::isNotEmpty)

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) {
        "Desktop practice persistence is missing $key"
    }
}
