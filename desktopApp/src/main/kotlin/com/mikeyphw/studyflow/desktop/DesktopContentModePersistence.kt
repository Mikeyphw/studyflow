package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.model.CodingExercise
import com.mikeyphw.studyflow.core.model.CodingExerciseFile
import com.mikeyphw.studyflow.core.model.CodingExerciseFileRole
import com.mikeyphw.studyflow.core.model.CodingExerciseId
import com.mikeyphw.studyflow.core.model.CodingExerciseStatus
import com.mikeyphw.studyflow.core.model.CodingExerciseType
import com.mikeyphw.studyflow.core.model.CodeSnippetId
import com.mikeyphw.studyflow.core.model.IdiomCard
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
import com.mikeyphw.studyflow.core.model.LanguageReviewOutcome
import com.mikeyphw.studyflow.core.model.ProgrammingCourseLevel
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfile
import com.mikeyphw.studyflow.core.model.ProgrammingCourseProfileId
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage
import com.mikeyphw.studyflow.core.model.ReviewItemId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import com.mikeyphw.studyflow.core.model.UsageExample
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

private const val ContentModeSchemaVersion = 1
private const val ContentModeFileName = "content-modes-v1.properties"
private const val ContentModeNull = "~"
private const val ListSeparator = '\u001f'
private const val RecordSeparator = '\u001e'

data class DesktopContentModeSnapshot(
    val programmingProfiles: List<ProgrammingCourseProfile> = emptyList(),
    val codingExercises: List<CodingExercise> = emptyList(),
    val languageDecks: List<LanguageDeckProfile> = emptyList(),
    val languageExpressions: List<LanguageExpression> = emptyList(),
    val idiomCards: List<IdiomCard> = emptyList(),
    val languageAttempts: List<LanguageReviewAttempt> = emptyList(),
)

class DesktopContentModePersistence private constructor(
    val root: Path,
    private val file: Path,
    private var snapshot: DesktopContentModeSnapshot,
) {
    fun current(): DesktopContentModeSnapshot = snapshot

    fun replaceFromPersistence(next: DesktopContentModeSnapshot) {
        snapshot = next
    }

    @Synchronized
    fun update(next: DesktopContentModeSnapshot) {
        writeAtomically(next)
        snapshot = next
    }

    private fun writeAtomically(value: DesktopContentModeSnapshot) {
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, ".studyflow-content-modes-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { DesktopContentModePropertiesCodec.write(it, value) }
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
        fun open(root: Path): DesktopContentModePersistence {
            val normalized = root.toAbsolutePath().normalize()
            Files.createDirectories(normalized)
            val file = normalized.resolve(ContentModeFileName)
            val initial = if (Files.isRegularFile(file)) {
                Files.newInputStream(file).use(DesktopContentModePropertiesCodec::read)
            } else {
                DesktopContentModeSnapshot()
            }
            return DesktopContentModePersistence(normalized, file, initial).also { persistence ->
                if (!Files.isRegularFile(file)) persistence.writeAtomically(initial)
            }
        }
    }
}

class DesktopContentModeRepository(
    initial: DesktopContentModeSnapshot = DesktopContentModeSnapshot(),
    private val onChanged: (DesktopContentModeSnapshot) -> Unit = {},
) {
    private var snapshot = initial

    fun current(): DesktopContentModeSnapshot = snapshot

    fun replaceFromPersistence(next: DesktopContentModeSnapshot) {
        snapshot = next
    }
    fun programmingProfile(planId: StudyPlanId) = snapshot.programmingProfiles.firstOrNull { it.studyPlanId == planId }
    fun codingExercises(planId: StudyPlanId) = snapshot.codingExercises.filter { it.studyPlanId == planId }
    fun languageDeck(planId: StudyPlanId) = snapshot.languageDecks.firstOrNull { it.studyPlanId == planId }
    fun languageExpressions(planId: StudyPlanId) = snapshot.languageExpressions.filter { it.studyPlanId == planId }
    fun idiomCards(planId: StudyPlanId): List<IdiomCard> {
        val ids = languageExpressions(planId).map { it.id }.toSet()
        return snapshot.idiomCards.filter { it.expressionId in ids }
    }
    fun languageAttempts(planId: StudyPlanId): List<LanguageReviewAttempt> {
        val ids = languageExpressions(planId).map { it.id }.toSet()
        return snapshot.languageAttempts.filter { it.expressionId in ids }
    }

    fun upsertProgramming(profile: ProgrammingCourseProfile, exercise: CodingExercise? = null) {
        snapshot = snapshot.copy(
            programmingProfiles = snapshot.programmingProfiles.upsert(profile) { it.id == profile.id },
            codingExercises = exercise?.let { value ->
                snapshot.codingExercises.upsert(value) { it.id == value.id }
            } ?: snapshot.codingExercises,
        )
        persist()
    }

    fun updateExercise(exercise: CodingExercise) {
        require(snapshot.codingExercises.any { it.id == exercise.id }) { "Coding exercise does not exist." }
        snapshot = snapshot.copy(codingExercises = snapshot.codingExercises.upsert(exercise) { it.id == exercise.id })
        persist()
    }

    fun upsertLanguage(
        deck: LanguageDeckProfile,
        expression: LanguageExpression? = null,
        idiomCard: IdiomCard? = null,
    ) {
        snapshot = snapshot.copy(
            languageDecks = snapshot.languageDecks.upsert(deck) { it.id == deck.id },
            languageExpressions = expression?.let { value ->
                snapshot.languageExpressions.upsert(value) { it.id == value.id }
            } ?: snapshot.languageExpressions,
            idiomCards = idiomCard?.let { value ->
                snapshot.idiomCards.upsert(value) { it.id == value.id }
            } ?: snapshot.idiomCards,
        )
        persist()
    }

    fun addLanguageAttempt(attempt: LanguageReviewAttempt) {
        require(snapshot.languageExpressions.any { it.id == attempt.expressionId }) {
            "Language review expression does not exist."
        }
        snapshot = snapshot.copy(languageAttempts = snapshot.languageAttempts + attempt)
        persist()
    }

    fun purgePlan(planId: StudyPlanId) {
        val expressionIds = languageExpressions(planId).map { it.id }.toSet()
        snapshot = snapshot.copy(
            programmingProfiles = snapshot.programmingProfiles.filterNot { it.studyPlanId == planId },
            codingExercises = snapshot.codingExercises.filterNot { it.studyPlanId == planId },
            languageDecks = snapshot.languageDecks.filterNot { it.studyPlanId == planId },
            languageExpressions = snapshot.languageExpressions.filterNot { it.studyPlanId == planId },
            idiomCards = snapshot.idiomCards.filterNot { it.expressionId in expressionIds },
            languageAttempts = snapshot.languageAttempts.filterNot { it.expressionId in expressionIds },
        )
        persist()
    }

    private fun persist() = onChanged(snapshot)
    private fun <T> List<T>.upsert(value: T, matches: (T) -> Boolean): List<T> =
        if (any(matches)) map { if (matches(it)) value else it } else this + value
}

private object DesktopContentModePropertiesCodec {
    fun write(output: OutputStream, snapshot: DesktopContentModeSnapshot) {
        val props = Properties()
        props["schema.version"] = ContentModeSchemaVersion.toString()
        writeList(props, "programming", snapshot.programmingProfiles, ::encodeProgrammingProfile)
        writeList(props, "exercise", snapshot.codingExercises, ::encodeExercise)
        writeList(props, "deck", snapshot.languageDecks, ::encodeDeck)
        writeList(props, "expression", snapshot.languageExpressions, ::encodeExpression)
        writeList(props, "idiom", snapshot.idiomCards, ::encodeIdiom)
        writeList(props, "attempt", snapshot.languageAttempts, ::encodeAttempt)
        props.store(output, "StudyFlow Desktop content modes schema v$ContentModeSchemaVersion")
    }

    fun read(input: InputStream): DesktopContentModeSnapshot {
        val props = Properties().apply { load(input) }
        require(props.required("schema.version").toInt() == ContentModeSchemaVersion) {
            "Unsupported Desktop content-mode schema."
        }
        return DesktopContentModeSnapshot(
            programmingProfiles = readList(props, "programming", ::decodeProgrammingProfile),
            codingExercises = readList(props, "exercise", ::decodeExercise),
            languageDecks = readList(props, "deck", ::decodeDeck),
            languageExpressions = readList(props, "expression", ::decodeExpression),
            idiomCards = readList(props, "idiom", ::decodeIdiom),
            languageAttempts = readList(props, "attempt", ::decodeAttempt),
        )
    }

    private fun encodeProgrammingProfile(v: ProgrammingCourseProfile) = fields(
        v.id.value, v.studyPlanId.value, v.primaryLanguage.name, v.level.name, v.repositoryUri,
        v.workspaceRelativePath, v.buildSystem, v.packageManager, v.tags.joinToString(ListSeparator.toString()),
        v.createdAtEpochMillis.toString(), v.updatedAtEpochMillis.toString(),
    )

    private fun decodeProgrammingProfile(value: String): ProgrammingCourseProfile {
        val f = split(value, 11)
        return ProgrammingCourseProfile(
            ProgrammingCourseProfileId(f[0]!!), StudyPlanId(f[1]!!), ProgrammingLanguage.valueOf(f[2]!!),
            ProgrammingCourseLevel.valueOf(f[3]!!), f[4], f[5]!!, f[6], f[7], splitList(f[8]),
            f[9]!!.toLong(), f[10]!!.toLong(),
        )
    }

    private fun encodeExercise(v: CodingExercise) = fields(
        v.id.value, v.studyPlanId.value, v.subjectId?.value, v.topicId?.value, v.snippetId?.value, v.type.name,
        v.title, v.prompt, v.starterCode, v.expectedOutput, v.hints.joinToString(ListSeparator.toString()),
        v.solutionNotes, v.language.name, v.difficulty.toString(), v.status.name, v.runCommandTemplate,
        v.testCommandTemplate, encodeExerciseFiles(v.files), v.tags.joinToString(ListSeparator.toString()),
        v.lastResultJson, v.retryAtEpochMillis?.toString(), v.createdAtEpochMillis.toString(),
        v.updatedAtEpochMillis.toString(),
    )

    private fun decodeExercise(value: String): CodingExercise {
        val f = split(value, 23)
        return CodingExercise(
            id = CodingExerciseId(f[0]!!), studyPlanId = StudyPlanId(f[1]!!), subjectId = f[2]?.let(::SubjectId),
            topicId = f[3]?.let(::TopicId), snippetId = f[4]?.let(::CodeSnippetId),
            type = CodingExerciseType.valueOf(f[5]!!), title = f[6]!!, prompt = f[7]!!, starterCode = f[8]!!,
            expectedOutput = f[9], hints = splitList(f[10]), solutionNotes = f[11]!!,
            language = ProgrammingLanguage.valueOf(f[12]!!), difficulty = f[13]!!.toInt(),
            status = CodingExerciseStatus.valueOf(f[14]!!), runCommandTemplate = f[15], testCommandTemplate = f[16],
            files = decodeExerciseFiles(f[17]), tags = splitList(f[18]), lastResultJson = f[19],
            retryAtEpochMillis = f[20]?.toLong(), createdAtEpochMillis = f[21]!!.toLong(),
            updatedAtEpochMillis = f[22]!!.toLong(),
        )
    }

    private fun encodeDeck(v: LanguageDeckProfile) = fields(
        v.id.value, v.studyPlanId.value, v.targetLanguage, v.nativeLanguage, v.regionFocus, v.defaultRegister.name,
        v.reviewDirections.joinToString(ListSeparator.toString()) { it.name },
        v.tags.joinToString(ListSeparator.toString()),
        v.createdAtEpochMillis.toString(),
        v.updatedAtEpochMillis.toString(),
    )

    private fun decodeDeck(value: String): LanguageDeckProfile {
        val f = split(value, 10)
        return LanguageDeckProfile(
            LanguageDeckId(f[0]!!), StudyPlanId(f[1]!!), f[2]!!, f[3], f[4], LanguageRegister.valueOf(f[5]!!),
            splitList(f[6]).map(LanguageReviewDirection::valueOf).toSet(), splitList(f[7]),
            f[8]!!.toLong(), f[9]!!.toLong(),
        )
    }

    private fun encodeExpression(v: LanguageExpression) = fields(
        v.id.value, v.studyPlanId.value, v.deckId.value, v.expression, v.meaning, v.literalTranslation,
        v.translationEquivalent, v.type.name, v.register.name, v.region, v.pronunciationNote, v.commonMistake,
        encodeExamples(v.examples), v.tags.joinToString(ListSeparator.toString()), v.createdAtEpochMillis.toString(),
        v.updatedAtEpochMillis.toString(),
    )

    private fun decodeExpression(value: String): LanguageExpression {
        val f = split(value, 16)
        return LanguageExpression(
            LanguageExpressionId(f[0]!!), StudyPlanId(f[1]!!), LanguageDeckId(f[2]!!), f[3]!!, f[4]!!,
            f[5], f[6], LanguageExpressionType.valueOf(f[7]!!), LanguageRegister.valueOf(f[8]!!), f[9], f[10], f[11],
            decodeExamples(f[12]), splitList(f[13]), f[14]!!.toLong(), f[15]!!.toLong(),
        )
    }

    private fun encodeIdiom(v: IdiomCard) = fields(
        v.id.value, v.expressionId.value, v.naturalExample, v.userSentence, v.clozePrompt,
        v.acceptedAnswers.joinToString(ListSeparator.toString()),
        v.reviewDirections.joinToString(ListSeparator.toString()) { it.name }, v.difficulty.toString(),
    )

    private fun decodeIdiom(value: String): IdiomCard {
        val f = split(value, 8)
        return IdiomCard(
            IdiomCardId(f[0]!!), LanguageExpressionId(f[1]!!), f[2]!!, f[3], f[4], splitList(f[5]),
            splitList(f[6]).map(LanguageReviewDirection::valueOf).toSet(), f[7]!!.toInt(),
        )
    }

    private fun encodeAttempt(v: LanguageReviewAttempt) = fields(
        v.id.value, v.expressionId.value, v.idiomCardId?.value, v.direction.name, v.promptText, v.answerText,
        v.outcome.name, v.scorePercent.toString(), v.reviewItemId?.value, v.createdAtEpochMillis.toString(),
    )

    private fun decodeAttempt(value: String): LanguageReviewAttempt {
        val f = split(value, 10)
        return LanguageReviewAttempt(
            LanguageReviewAttemptId(f[0]!!), LanguageExpressionId(f[1]!!), f[2]?.let(::IdiomCardId),
            LanguageReviewDirection.valueOf(f[3]!!), f[4]!!, f[5]!!, LanguageReviewOutcome.valueOf(f[6]!!),
            f[7]!!.toInt(), f[8]?.let(::ReviewItemId), f[9]!!.toLong(),
        )
    }

    private fun encodeExerciseFiles(files: List<CodingExerciseFile>): String =
        files.joinToString(RecordSeparator.toString()) {
            listOf(it.relativePath, it.role.name, it.contents)
                .joinToString(ListSeparator.toString()) { value -> encode(value) }
    }

    private fun decodeExerciseFiles(value: String?): List<CodingExerciseFile> = value.orEmpty()
        .split(RecordSeparator).filter(String::isNotEmpty).map { record ->
            val pieces = record.split(ListSeparator)
            require(pieces.size == 3) { "Corrupt Desktop coding exercise file bundle." }
            CodingExerciseFile(
                decode(pieces[0]),
                decode(pieces[2]),
                CodingExerciseFileRole.valueOf(decode(pieces[1])),
            )
        }

    private fun encodeExamples(examples: List<UsageExample>): String =
        examples.joinToString(RecordSeparator.toString()) {
        listOf(it.sentence, it.translation ?: ContentModeNull, it.note ?: ContentModeNull)
            .joinToString(ListSeparator.toString()) { value -> encode(value) }
    }

    private fun decodeExamples(value: String?): List<UsageExample> = value.orEmpty()
        .split(RecordSeparator).filter(String::isNotEmpty).map { record ->
            val pieces = record.split(ListSeparator).map(::decode)
            require(pieces.size == 3) { "Corrupt Desktop language example." }
            UsageExample(
                pieces[0],
                pieces[1].takeUnless { it == ContentModeNull },
                pieces[2].takeUnless { it == ContentModeNull },
            )
        }

    private fun <T> writeList(props: Properties, prefix: String, values: List<T>, encodeValue: (T) -> String) {
        props["$prefix.count"] = values.size.toString()
        values.forEachIndexed { index, value -> props["$prefix.$index"] = encodeValue(value) }
    }

    private fun <T> readList(props: Properties, prefix: String, decodeValue: (String) -> T): List<T> =
        List(props.required("$prefix.count").toInt()) { index -> decodeValue(props.required("$prefix.$index")) }

    private fun fields(vararg values: String?): String =
        values.joinToString(".") { it?.let(::encode) ?: ContentModeNull }
    private fun split(value: String, expected: Int): List<String?> = value.split('.').also {
        require(it.size == expected) { "Corrupt Desktop content-mode record." }
    }.map { if (it == ContentModeNull) null else decode(it) }
    private fun splitList(value: String?): List<String> =
        value.orEmpty().split(ListSeparator).filter(String::isNotEmpty)
    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "Missing $key" }
}
