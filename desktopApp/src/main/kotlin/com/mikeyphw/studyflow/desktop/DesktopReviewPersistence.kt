package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.ErrorCategory
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.ErrorEntryId
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.FlashcardId
import com.mikeyphw.studyflow.core.model.FlashcardType
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.ReviewMethod
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

private const val ReviewSchemaVersion = 1
private const val ReviewFileName = "reviews-v1.properties"
private const val ReviewNull = "~"

data class DesktopReviewSnapshot(
    val reviews: List<ReviewItem>,
    val flashcards: List<Flashcard>,
    val errors: List<ErrorEntry>,
)

class DesktopReviewPersistence private constructor(
    val root: Path,
    private val file: Path,
    initial: DesktopReviewSnapshot,
) {
    private var snapshot = initial
    fun current(): DesktopReviewSnapshot = snapshot

    @Synchronized
    fun update(next: DesktopReviewSnapshot) {
        writeAtomically(next)
        snapshot = next
    }

    private fun writeAtomically(value: DesktopReviewSnapshot) {
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".studyflow-reviews-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { ReviewPropertiesCodec.write(it, value) }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun open(root: Path): DesktopReviewPersistence {
            val normalized = root.toAbsolutePath().normalize()
            Files.createDirectories(normalized)
            val file = normalized.resolve(ReviewFileName)
            val initial = if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(ReviewPropertiesCodec::read)
            } else {
                DesktopReviewSnapshot(emptyList(), emptyList(), emptyList())
            }
            return DesktopReviewPersistence(normalized, file, initial).also {
                if (!Files.isRegularFile(file)) it.writeAtomically(initial)
            }
        }
    }
}

private object ReviewPropertiesCodec {
    fun write(output: OutputStream, snapshot: DesktopReviewSnapshot) {
        val properties = Properties()
        properties["schema.version"] = ReviewSchemaVersion.toString()
        properties["review.count"] = snapshot.reviews.size.toString()
        snapshot.reviews.forEachIndexed { index, item -> properties["review.$index"] = encodeReview(item) }
        properties["flashcard.count"] = snapshot.flashcards.size.toString()
        snapshot.flashcards.forEachIndexed { index, card -> properties["flashcard.$index"] = encodeFlashcard(card) }
        properties["error.count"] = snapshot.errors.size.toString()
        snapshot.errors.forEachIndexed { index, error -> properties["error.$index"] = encodeError(error) }
        properties.store(output, "StudyFlow Desktop review schema v$ReviewSchemaVersion")
    }

    fun read(input: InputStream): DesktopReviewSnapshot {
        val properties = Properties().apply { load(input) }
        require(properties.required("schema.version").toInt() == ReviewSchemaVersion) {
            "Unsupported Desktop review schema."
        }
        return DesktopReviewSnapshot(
            reviews = List(properties.required("review.count").toInt()) {
                decodeReview(properties.required("review.$it"))
            },
            flashcards = List(properties.required("flashcard.count").toInt()) {
                decodeFlashcard(properties.required("flashcard.$it"))
            },
            errors = List(properties.required("error.count").toInt()) { decodeError(properties.required("error.$it")) },
        )
    }

    private fun encodeReview(v: ReviewItem) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.questionId?.value,
        v.flashcardId?.value, v.errorEntryId?.value, v.method.name, v.status.name, v.dueAtEpochMillis.toString(),
        v.intervalDays.toString(), v.repetitionCount.toString(), v.easeFactor.toString(),
        v.completedAtEpochMillis?.toString(),
    )

    private fun decodeReview(value: String): ReviewItem {
        val f = split(value, 14)
        return ReviewItem(
            id = ReviewItemId(f[0]!!),
            studyPlanId = StudyPlanId(f[1]!!),
            subjectId = f[2]?.let(::SubjectId),
            topicId = f[3]?.let(::TopicId),
            questionId = f[4]?.let(::QuestionId),
            flashcardId = f[5]?.let(::FlashcardId),
            errorEntryId = f[6]?.let(::ErrorEntryId),
            method = ReviewMethod.valueOf(f[7]!!),
            status = ReviewStatus.valueOf(f[8]!!),
            dueAtEpochMillis = f[9]!!.toLong(),
            intervalDays = f[10]!!.toInt(),
            repetitionCount = f[11]!!.toInt(),
            easeFactor = f[12]!!.toDouble(),
            completedAtEpochMillis = f[13]?.toLong(),
        )
    }

    private fun encodeFlashcard(v: Flashcard) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.type.name, v.front, v.back,
        v.clozeText, v.tags.joinToString("\u001f"), v.suspended.toString(), v.createdAtEpochMillis.toString(),
        v.updatedAtEpochMillis.toString(),
    )

    private fun decodeFlashcard(value: String): Flashcard {
        val f = split(value, 12)
        return Flashcard(
            id = FlashcardId(f[0]!!), studyPlanId = StudyPlanId(f[1]!!), subjectId = f[2]?.let(::SubjectId),
            topicId = f[3]?.let(::TopicId), type = FlashcardType.valueOf(f[4]!!), front = f[5]!!, back = f[6]!!,
            clozeText = f[7], tags = splitList(f[8]), suspended = f[9]!!.toBooleanStrict(),
            createdAtEpochMillis = f[10]!!.toLong(), updatedAtEpochMillis = f[11]!!.toLong(),
        )
    }

    private fun encodeError(v: ErrorEntry) = fields(
        v.id.value, v.studyPlanId.value, v.questionId?.value, v.subjectId?.value, v.topicId?.value, v.category.name,
        v.chosenAnswer, v.correctAnswer, v.explanation, v.recurrenceCount.toString(),
        v.createdAtEpochMillis.toString(), v.updatedAtEpochMillis.toString(),
    )

    private fun decodeError(value: String): ErrorEntry {
        val f = split(value, 12)
        return ErrorEntry(
            id = ErrorEntryId(f[0]!!), studyPlanId = StudyPlanId(f[1]!!), questionId = f[2]?.let(::QuestionId),
            subjectId = f[3]?.let(::SubjectId), topicId = f[4]?.let(::TopicId),
            category = ErrorCategory.valueOf(f[5]!!), chosenAnswer = f[6], correctAnswer = f[7], explanation = f[8]!!,
            recurrenceCount = f[9]!!.toInt(), createdAtEpochMillis = f[10]!!.toLong(),
            updatedAtEpochMillis = f[11]!!.toLong(),
        )
    }

    private fun fields(vararg values: String?): String = values.joinToString(".") { it?.let(::encode) ?: ReviewNull }
    private fun split(value: String, expected: Int): List<String?> = value.split('.').also {
        require(it.size == expected) { "Corrupt Desktop review record." }
    }.map { if (it == ReviewNull) null else decode(it) }
    private fun splitList(value: String?): List<String> = value.orEmpty().split('\u001f').filter(String::isNotEmpty)
    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "Missing $key" }
}
