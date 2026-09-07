package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.roundToInt

enum class ExamScoringMode {
    NO_PENALTY,
    FIXED_NEGATIVE,
    RIGHT_MINUS_WRONG,
    WEIGHTED_CUSTOM,
}

data class ExamScoringPolicy(
    val mode: ExamScoringMode = ExamScoringMode.NO_PENALTY,
    val wrongAnswerPenalty: Double = 0.0,
    val unansweredPenalty: Double = 0.0,
) {
    init {
        require(wrongAnswerPenalty.isFinite() && wrongAnswerPenalty >= 0.0) {
            "Wrong-answer penalty must be finite and non-negative."
        }
        require(unansweredPenalty.isFinite() && unansweredPenalty >= 0.0) {
            "Unanswered penalty must be finite and non-negative."
        }
    }

    val legacyNegativeMarking: Double
        get() = when (mode) {
            ExamScoringMode.NO_PENALTY,
            ExamScoringMode.WEIGHTED_CUSTOM,
            -> 0.0
            ExamScoringMode.FIXED_NEGATIVE -> wrongAnswerPenalty
            ExamScoringMode.RIGHT_MINUS_WRONG -> 1.0
        }
}

data class ExamBlueprintSection(
    val key: String,
    val questionCount: Int,
    val weight: Double = 1.0,
    val minimumScorePercent: Int? = null,
    val subjectId: SubjectId? = null,
    val topicId: TopicId? = null,
    val allowedQuestionTypes: Set<QuestionType> = setOf(
        QuestionType.MULTIPLE_CHOICE,
        QuestionType.TRUE_FALSE,
        QuestionType.SHORT_ANSWER,
    ),
) {
    init {
        require(key.isNotBlank()) { "Blueprint section key cannot be blank." }
        require(questionCount > 0) { "Blueprint section must request at least one question." }
        require(weight.isFinite() && weight > 0.0) { "Blueprint section weight must be positive." }
        require(minimumScorePercent == null || minimumScorePercent in 0..100) {
            "Section minimum score must be between 0 and 100."
        }
        require(allowedQuestionTypes.isNotEmpty()) { "Blueprint section needs at least one allowed question type." }
    }
}

data class ExamBlueprint(
    val title: String,
    val sections: List<ExamBlueprintSection>,
    val timeLimitMinutes: Int,
    val minimumOverallScorePercent: Int? = null,
    val examBoard: String? = null,
    val year: Int? = null,
    val strictExamMode: Boolean = true,
    val scoringPolicy: ExamScoringPolicy = ExamScoringPolicy(),
) {
    init {
        require(title.isNotBlank()) { "Exam blueprint title cannot be blank." }
        require(sections.isNotEmpty()) { "Exam blueprint needs at least one section." }
        require(sections.map(ExamBlueprintSection::key).distinct().size == sections.size) {
            "Exam blueprint section keys must be unique."
        }
        require(timeLimitMinutes > 0) { "Exam blueprint time limit must be positive." }
        require(minimumOverallScorePercent == null || minimumOverallScorePercent in 0..100) {
            "Overall minimum score must be between 0 and 100."
        }
        require(year == null || year in 1900..2200) { "Exam blueprint year is outside the supported range." }
    }

    val requestedQuestionCount: Int get() = sections.sumOf(ExamBlueprintSection::questionCount)
}

data class ExamBlueprintSectionPreview(
    val section: ExamBlueprintSection,
    val selectedQuestions: List<Question>,
    val missingQuestionCount: Int,
) {
    val complete: Boolean get() = missingQuestionCount == 0
}

data class ExamBlueprintPreview(
    val blueprint: ExamBlueprint,
    val sections: List<ExamBlueprintSectionPreview>,
) {
    val selectedQuestions: List<Question> = sections.flatMap(ExamBlueprintSectionPreview::selectedQuestions)
    val missingQuestionCount: Int = sections.sumOf(ExamBlueprintSectionPreview::missingQuestionCount)
    val executable: Boolean = selectedQuestions.isNotEmpty() && missingQuestionCount == 0
}

object ExamBlueprintPlanner {
    fun preview(questions: List<Question>, blueprint: ExamBlueprint): ExamBlueprintPreview {
        val claimed = mutableSetOf<QuestionId>()
        val previews = blueprint.sections.map { section ->
            val candidates = questions.asSequence()
                .filter { question -> question.id !in claimed }
                .filter { question -> question.isEligibleFor(blueprint, section) }
                .sortedWith(
                    compareByDescending<Question> { it.points ?: 0.0 }
                        .thenByDescending(Question::difficulty)
                        .thenByDescending { it.year ?: 0 }
                        .thenBy { it.id.value },
                )
                .take(section.questionCount)
                .toList()
            claimed += candidates.map(Question::id)
            ExamBlueprintSectionPreview(
                section = section,
                selectedQuestions = candidates,
                missingQuestionCount = (section.questionCount - candidates.size).coerceAtLeast(0),
            )
        }
        return ExamBlueprintPreview(blueprint = blueprint, sections = previews)
    }

    fun balancedFromSelection(
        title: String,
        questions: List<Question>,
        questionLimit: Int = 50,
        timeLimitMinutes: Int = 120,
        scoringPolicy: ExamScoringPolicy = ExamScoringPolicy(),
        minimumOverallScorePercent: Int? = 60,
        examBoard: String? = null,
        year: Int? = null,
    ): ExamBlueprint {
        require(questions.isNotEmpty()) { "A banca blueprint needs at least one candidate question." }
        val boundedCount = questionLimit.coerceIn(1, 120).coerceAtMost(questions.size)
        val grouped = questions.groupBy { question -> question.subjectId?.value ?: GeneralSectionKey }
        val ordered = grouped.entries.sortedBy { it.key }
        val counts = ordered.associate { it.key to 0 }.toMutableMap()
        var remaining = boundedCount
        while (remaining > 0) {
            var allocated = false
            ordered.forEach { entry ->
                if (remaining == 0) return@forEach
                val current = counts.getValue(entry.key)
                if (current < entry.value.size) {
                    counts[entry.key] = current + 1
                    remaining -= 1
                    allocated = true
                }
            }
            if (!allocated) break
        }
        val sections = ordered.mapNotNull { entry ->
            val count = counts.getValue(entry.key)
            if (count == 0) return@mapNotNull null
            ExamBlueprintSection(
                key = entry.key,
                subjectId = entry.value.firstOrNull()?.subjectId,
                questionCount = count,
                weight = 1.0,
            )
        }
        return ExamBlueprint(
            title = title.trim().ifBlank { "Banca mock exam" },
            sections = sections,
            timeLimitMinutes = timeLimitMinutes,
            minimumOverallScorePercent = minimumOverallScorePercent,
            examBoard = examBoard?.trim()?.ifBlank { null },
            year = year,
            scoringPolicy = scoringPolicy,
        )
    }

    private fun Question.isEligibleFor(blueprint: ExamBlueprint, section: ExamBlueprintSection): Boolean {
        if (type == QuestionType.ESSAY || correctAnswer.isBlank()) return false
        if (type !in section.allowedQuestionTypes) return false
        if (section.subjectId != null && subjectId != section.subjectId) return false
        if (section.topicId != null && topicId != section.topicId) return false
        if (blueprint.examBoard != null && !examBoardMatches(blueprint.examBoard, examBoard)) return false
        if (blueprint.year != null && year != blueprint.year) return false
        return true
    }

    private fun examBoardMatches(expected: String, actual: String?): Boolean {
        val candidate = actual?.trim()?.takeIf(String::isNotBlank) ?: return false
        val requested = expected.trim()
        if (candidate.equals(requested, ignoreCase = true)) return true
        val shorter = if (candidate.length <= requested.length) candidate else requested
        val longer = if (candidate.length > requested.length) candidate else requested
        return shorter.length >= 5 && longer.contains(shorter, ignoreCase = true)
    }

    private const val GeneralSectionKey = "general"
}

data class ExamBlueprintSectionScore(
    val key: String,
    val earnedPoints: Double,
    val maximumPoints: Double,
    val scorePercent: Int,
    val passed: Boolean?,
)

data class ExamBlueprintScore(
    val scorePercent: Int,
    val passed: Boolean?,
    val sectionScores: List<ExamBlueprintSectionScore>,
)

object ExamBlueprintScorer {
    fun score(state: PracticeExecutionState, frozen: FrozenExamBlueprint): ExamBlueprintScore {
        val answerByQuestion = state.answers.associateBy(PracticeAnswerRecord::questionId)
        var offset = 0
        val sectionScores = frozen.sections.map { section ->
            val ids = state.questionIds.drop(offset).take(section.questionCount)
            offset += section.questionCount
            val maxPoints = section.weight.coerceAtLeast(0.000001)
            val perQuestion = maxPoints / section.questionCount.toDouble()
            var earned = 0.0
            ids.forEach { id ->
                val answer = answerByQuestion[id]
                earned += when {
                    answer == null -> -frozen.scoringPolicy.unansweredPenalty * perQuestion
                    answer.correct -> perQuestion
                    frozen.scoringPolicy.mode == ExamScoringMode.NO_PENALTY -> 0.0
                    frozen.scoringPolicy.mode == ExamScoringMode.RIGHT_MINUS_WRONG -> -perQuestion
                    else -> -frozen.scoringPolicy.wrongAnswerPenalty * perQuestion
                }
            }
            val percent = ((earned.coerceAtLeast(0.0) / maxPoints) * 100.0).roundToInt().coerceIn(0, 100)
            ExamBlueprintSectionScore(
                key = section.key,
                earnedPoints = earned,
                maximumPoints = maxPoints,
                scorePercent = percent,
                passed = section.minimumScorePercent?.let { percent >= it },
            )
        }
        val earned = sectionScores.sumOf(ExamBlueprintSectionScore::earnedPoints).coerceAtLeast(0.0)
        val maximum = sectionScores.sumOf(ExamBlueprintSectionScore::maximumPoints).coerceAtLeast(0.000001)
        val overall = ((earned / maximum) * 100.0).roundToInt().coerceIn(0, 100)
        val sectionsPass = sectionScores.all { it.passed != false }
        val overallPass = frozen.minimumOverallScorePercent?.let { overall >= it } ?: true
        return ExamBlueprintScore(
            scorePercent = overall,
            passed = if (frozen.minimumOverallScorePercent == null && sectionScores.all { it.passed == null }) {
                null
            } else {
                overallPass && sectionsPass
            },
            sectionScores = sectionScores,
        )
    }
}

data class FrozenExamBlueprintSection(
    val key: String,
    val questionCount: Int,
    val weight: Double,
    val minimumScorePercent: Int?,
)

data class FrozenExamBlueprint(
    val version: Int,
    val title: String,
    val timeLimitMinutes: Int,
    val minimumOverallScorePercent: Int?,
    val strictExamMode: Boolean,
    val scoringPolicy: ExamScoringPolicy,
    val sections: List<FrozenExamBlueprintSection>,
) {
    val questionCount: Int get() = sections.sumOf(FrozenExamBlueprintSection::questionCount)
}

object ExamBlueprintCodec {
    private const val MetadataPrefix = "examBlueprintV1="

    fun encodeConfiguration(preview: ExamBlueprintPreview): String {
        require(preview.executable) { "Cannot freeze an incomplete exam blueprint." }
        val questionLine = "questionIds=" + preview.selectedQuestions.joinToString(",") { it.id.value }
        return questionLine + "\n" + encodeMetadata(preview.blueprint)
    }

    fun decode(configuration: String): FrozenExamBlueprint? = configuration.lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith(MetadataPrefix) }
        ?.removePrefix(MetadataPrefix)
        ?.let(::decodePayload)

    private fun encodeMetadata(blueprint: ExamBlueprint): String {
        val payload = buildString {
            appendLine("version=1")
            appendLine("title=${blueprint.title.encode()}")
            appendLine("time=${blueprint.timeLimitMinutes}")
            appendLine("minimum=${blueprint.minimumOverallScorePercent ?: -1}")
            appendLine("strict=${blueprint.strictExamMode}")
            appendLine("mode=${blueprint.scoringPolicy.mode.name}")
            appendLine("wrong=${blueprint.scoringPolicy.wrongAnswerPenalty}")
            appendLine("blank=${blueprint.scoringPolicy.unansweredPenalty}")
            blueprint.sections.forEach { section ->
                append("section=")
                append(section.key.encode())
                append('|').append(section.questionCount)
                append('|').append(section.weight)
                append('|').append(section.minimumScorePercent ?: -1)
                appendLine()
            }
        }.trimEnd()
        return MetadataPrefix + payload.encode()
    }

    private fun decodePayload(encoded: String): FrozenExamBlueprint? = runCatching {
        val values = mutableMapOf<String, String>()
        val sections = mutableListOf<FrozenExamBlueprintSection>()
        encoded.decode().lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
            if (line.startsWith("section=")) {
                val parts = line.removePrefix("section=").split('|')
                require(parts.size == 4)
                sections += FrozenExamBlueprintSection(
                    key = parts[0].decode(),
                    questionCount = parts[1].toInt(),
                    weight = parts[2].toDouble(),
                    minimumScorePercent = parts[3].toInt().takeIf { it >= 0 },
                )
            } else {
                val split = line.indexOf('=')
                require(split > 0)
                values[line.substring(0, split)] = line.substring(split + 1)
            }
        }
        FrozenExamBlueprint(
            version = values.getValue("version").toInt(),
            title = values.getValue("title").decode(),
            timeLimitMinutes = values.getValue("time").toInt(),
            minimumOverallScorePercent = values.getValue("minimum").toInt().takeIf { it >= 0 },
            strictExamMode = values.getValue("strict").toBooleanStrict(),
            scoringPolicy = ExamScoringPolicy(
                mode = ExamScoringMode.valueOf(values.getValue("mode")),
                wrongAnswerPenalty = values.getValue("wrong").toDouble(),
                unansweredPenalty = values.getValue("blank").toDouble(),
            ),
            sections = sections,
        ).also { frozen ->
            require(frozen.version == 1)
            require(frozen.sections.isNotEmpty())
        }
    }.getOrNull()

    private fun String.encode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(toByteArray(StandardCharsets.UTF_8))

    private fun String.decode(): String = String(
        Base64.getUrlDecoder().decode(this),
        StandardCharsets.UTF_8,
    )
}
