package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.text.Normalizer

enum class QuestionImportFormat {
    AUTO,
    CSV,
    TSV,
    MARKDOWN,
}

enum class QuestionImportIssueSeverity {
    WARNING,
    ERROR,
}

data class QuestionImportIssue(
    val rowNumber: Int,
    val severity: QuestionImportIssueSeverity,
    val message: String,
)

data class QuestionImportDraft(
    val rowNumber: Int,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val type: QuestionType,
    val statement: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val examBoard: String?,
    val source: String?,
    val year: Int?,
    val difficulty: Int,
    val tags: List<String>,
    val duplicateOfExisting: Boolean,
    val duplicateWithinImport: Boolean,
    val valid: Boolean,
) {
    val importable: Boolean = valid && !duplicateOfExisting && !duplicateWithinImport
}

data class QuestionImportPreview(
    val format: QuestionImportFormat,
    val drafts: List<QuestionImportDraft>,
    val issues: List<QuestionImportIssue>,
) {
    val acceptedCount: Int = drafts.count(QuestionImportDraft::importable)
    val duplicateCount: Int = drafts.count { it.duplicateOfExisting || it.duplicateWithinImport }
    val rejectedCount: Int = drafts.count { !it.importable }
    val hasErrors: Boolean = issues.any { it.severity == QuestionImportIssueSeverity.ERROR }
}

data class QuestionImportResult(
    val preview: QuestionImportPreview,
    val imported: List<Question>,
) {
    val importedCount: Int = imported.size
    val skippedCount: Int = preview.drafts.size - imported.size
}

data class QuestionImportOwnership(
    val subjectIds: Set<SubjectId>,
    val topicSubjects: Map<TopicId, SubjectId>,
) {
    companion object {
        fun from(syllabus: StudySyllabus): QuestionImportOwnership = QuestionImportOwnership(
            subjectIds = syllabus.subjects.mapTo(linkedSetOf()) { it.id },
            topicSubjects = syllabus.topics.associate { it.id to it.subjectId },
        )
    }
}

object QuestionImportPipeline {
    fun preview(
        planId: StudyPlanId,
        rawText: String,
        existingQuestions: List<Question>,
        requestedFormat: QuestionImportFormat = QuestionImportFormat.AUTO,
        defaultSubjectId: SubjectId? = null,
        defaultTopicId: TopicId? = null,
        ownership: QuestionImportOwnership? = null,
    ): QuestionImportPreview {
        val format = resolveFormat(rawText, requestedFormat)
        val parsed = when (format) {
            QuestionImportFormat.CSV -> parseSeparated(rawText, ',', planId, defaultSubjectId, defaultTopicId)
            QuestionImportFormat.TSV -> parseSeparated(rawText, '\t', planId, defaultSubjectId, defaultTopicId)
            QuestionImportFormat.MARKDOWN -> parseMarkdown(rawText, planId, defaultSubjectId, defaultTopicId)
            QuestionImportFormat.AUTO -> emptyList()
        }
        val ownershipChecked = parsed.map { row ->
            val ownershipIssues = ownershipIssues(row.draft, ownership)
            row.copy(
                draft = row.draft.copy(
                    valid = row.draft.valid && ownershipIssues.none { it
                        .severity == QuestionImportIssueSeverity.ERROR },
                ),
                issues = row.issues + ownershipIssues,
            )
        }
        val existingFingerprints = existingQuestions.map(::fingerprint).toSet()
        val seen = mutableSetOf<String>()
        val drafts = ownershipChecked.map { row ->
            val draft = row.draft
            val print = fingerprint(draft)
            val existing = print in existingFingerprints
            val repeated = !seen.add(print)
            draft.copy(duplicateOfExisting = existing, duplicateWithinImport = repeated)
        }
        val duplicateIssues = drafts.flatMap { draft ->
            buildList {
                if (draft.duplicateOfExisting) {
                    add(
                        QuestionImportIssue(
                            rowNumber = draft.rowNumber,
                            severity = QuestionImportIssueSeverity.WARNING,
                            message = "Question already exists and will be skipped.",
                        ),
                    )
                }
                if (draft.duplicateWithinImport) {
                    add(
                        QuestionImportIssue(
                            rowNumber = draft.rowNumber,
                            severity = QuestionImportIssueSeverity.WARNING,
                            message = "Repeated import question will be skipped.",
                        ),
                    )
                }
            }
        }
        return QuestionImportPreview(
            format = format,
            drafts = drafts,
            issues = ownershipChecked.flatMap { it.issues } + duplicateIssues,
        )
    }

    fun fingerprint(question: Question): String = semanticFingerprint(
        type = question.type,
        statement = question.statement,
        options = question.options,
        correctAnswer = question.correctAnswer,
        explanation = question.explanation,
        examBoard = question.examBoard,
        source = question.source,
        year = question.year,
    )

    private fun ownershipIssues(
        draft: QuestionImportDraft,
        ownership: QuestionImportOwnership?,
    ): List<QuestionImportIssue> {
        if (ownership == null) return emptyList()
        return buildList {
            val subjectId = draft.subjectId
            if (subjectId != null && subjectId !in ownership.subjectIds) {
                add(error(draft.rowNumber, "Subject does not belong to this Study Plan."))
            }
            val topicId = draft.topicId
            val topicSubject = topicId?.let(ownership.topicSubjects::get)
            if (topicId != null && topicSubject == null) {
                add(error(draft.rowNumber, "Topic does not belong to this Study Plan."))
            } else if (subjectId != null && topicSubject != null && topicSubject != subjectId) {
                add(error(draft.rowNumber, "Topic does not belong to the selected subject."))
            }
        }
    }

    private fun fingerprint(draft: QuestionImportDraft): String = semanticFingerprint(
        type = draft.type,
        statement = draft.statement,
        options = draft.options,
        correctAnswer = draft.correctAnswer,
        explanation = draft.explanation,
        examBoard = draft.examBoard,
        source = draft.source,
        year = draft.year,
    )

    private fun semanticFingerprint(
        type: QuestionType,
        statement: String,
        options: List<String>,
        correctAnswer: String,
        explanation: String,
        examBoard: String?,
        source: String?,
        year: Int?,
    ): String = listOf(
        type.name,
        normalizeFingerprintText(statement),
        options.map(::normalizeFingerprintText).sorted().joinToString("~"),
        normalizeFingerprintText(correctAnswer),
        normalizeFingerprintText(explanation),
        normalizeFingerprintText(examBoard.orEmpty()),
        normalizeFingerprintText(source.orEmpty()),
        year?.toString().orEmpty(),
    ).joinToString("|")

    private fun normalizeFingerprintText(value: String): String = Normalizer
        .normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase()
        .replace(Regex("\\s+"), " ")

    private fun resolveFormat(rawText: String, requestedFormat: QuestionImportFormat): QuestionImportFormat {
        if (requestedFormat != QuestionImportFormat.AUTO) return requestedFormat
        val trimmed = rawText.trimStart()
        return when {
            trimmed.startsWith("#") || "Question:" in trimmed || "Answer:" in trimmed ->
                QuestionImportFormat.MARKDOWN
            rawText.lineSequence().firstOrNull { it.isNotBlank() }?.contains('\t') == true ->
                QuestionImportFormat.TSV
            else -> QuestionImportFormat.CSV
        }
    }

    private data class ParsedDraft(
        val draft: QuestionImportDraft,
        val issues: List<QuestionImportIssue>,
    )

    private fun parseSeparated(
        rawText: String,
        separator: Char,
        planId: StudyPlanId,
        defaultSubjectId: SubjectId?,
        defaultTopicId: TopicId?,
    ): List<ParsedDraft> {
        val rows = rawText.lineSequence()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { (_, line) -> line.isNotBlank() }
            .toList()
        if (rows.isEmpty()) return emptyList()
        val header = splitRow(rows.first().second, separator).map { it.lowercase() }
        val hasHeader = header.any { it in setOf("statement", "question", "answer", "correctanswer") }
        val body = if (hasHeader) rows.drop(1) else rows
        return body.mapNotNull { (rowNumber, line) ->
            val cells = splitRow(line, separator)
            val map = if (hasHeader) header.zip(cells).toMap() else emptyMap()
            val statement = map.firstValue("statement", "question") ?: cells.getOrNull(0)
            val answer = map.firstValue("correctanswer", "answer", "correct") ?: cells.getOrNull(1)
            val explanation = map.firstValue("explanation") ?: cells.getOrNull(2).orEmpty()
            val options = map.firstValue("options") ?: cells.getOrNull(3).orEmpty()
            buildDraft(
                rowNumber = rowNumber,
                planId = planId,
                defaultSubjectId = defaultSubjectId,
                defaultTopicId = defaultTopicId,
                statement = statement.orEmpty(),
                correctAnswer = answer.orEmpty(),
                explanation = explanation,
                options = options,
                type = map.firstValue("type") ?: cells.getOrNull(4),
                difficulty = map.firstValue("difficulty") ?: cells.getOrNull(5),
                tags = map.firstValue("tags") ?: cells.getOrNull(6),
                examBoard = map.firstValue("examboard", "board"),
                source = map.firstValue("source"),
                year = map.firstValue("year"),
                subjectId = map.firstValue("subjectid", "subject"),
                topicId = map.firstValue("topicid", "topic"),
            )
        }
    }

    private fun parseMarkdown(
        rawText: String,
        planId: StudyPlanId,
        defaultSubjectId: SubjectId?,
        defaultTopicId: TopicId?,
    ): List<ParsedDraft> = rawText.split(Regex("\\n\\s*\\n"))
        .mapIndexedNotNull { index, block ->
            val values = block.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .mapNotNull { line ->
                    val delimiter = line.indexOf(':')
                    when {
                        line.startsWith("#") -> "question" to line.trimStart('#').trim()
                        delimiter > 0 -> line.take(delimiter).trim().lowercase() to line.drop(delimiter + 1).trim()
                        else -> null
                    }
                }.toMap()
            if (values.isEmpty()) return@mapIndexedNotNull null
            buildDraft(
                rowNumber = index + 1,
                planId = planId,
                defaultSubjectId = defaultSubjectId,
                defaultTopicId = defaultTopicId,
                statement = values["question"].orEmpty(),
                correctAnswer = values["answer"].orEmpty(),
                explanation = values["explanation"].orEmpty(),
                options = values["options"].orEmpty(),
                type = values["type"],
                difficulty = values["difficulty"],
                tags = values["tags"],
                examBoard = values["examboard"] ?: values["board"],
                source = values["source"],
                year = values["year"],
                subjectId = values["subjectid"] ?: values["subject"],
                topicId = values["topicid"] ?: values["topic"],
            )
        }

    private fun buildDraft(
        rowNumber: Int,
        planId: StudyPlanId,
        defaultSubjectId: SubjectId?,
        defaultTopicId: TopicId?,
        statement: String,
        correctAnswer: String,
        explanation: String,
        options: String,
        type: String?,
        difficulty: String?,
        tags: String?,
        examBoard: String?,
        source: String?,
        year: String?,
        subjectId: String?,
        topicId: String?,
    ): ParsedDraft {
        val issues = mutableListOf<QuestionImportIssue>()
        if (statement.isBlank()) {
            issues += error(rowNumber, "Question statement is required.")
        }
        if (correctAnswer.isBlank()) {
            issues += error(rowNumber, "Correct answer is required.")
        }
        val parsedOptions = options.split('|', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
        val normalizedType = type?.trim()?.takeIf(String::isNotBlank)
        val parsedType = normalizedType?.uppercase()?.let { raw ->
            QuestionType.entries.firstOrNull { it.name == raw.replace(' ', '_') }
        } ?: if (normalizedType == null) {
            if (parsedOptions.size >= 2) QuestionType.MULTIPLE_CHOICE else QuestionType.SHORT_ANSWER
        } else {
            issues += error(rowNumber, "Unknown question type: $normalizedType.")
            QuestionType.SHORT_ANSWER
        }
        if (parsedType == QuestionType.MULTIPLE_CHOICE) {
            if (parsedOptions.size < 2) {
                issues += error(rowNumber, "Multiple-choice questions need at least two options.")
            }
            if (correctAnswer.isNotBlank() && parsedOptions.none { it.equals(correctAnswer.trim(),
                ignoreCase = true) }) {
                issues += error(rowNumber, "Multiple-choice correct answer must be one of the options.")
            }
        }
        val normalizedDifficulty = difficulty?.trim()?.takeIf(String::isNotBlank)
        val parsedDifficulty = normalizedDifficulty?.toIntOrNull()?.takeIf { it in 1..5 } ?: 3
        if (normalizedDifficulty != null && normalizedDifficulty.toIntOrNull()?.let { it in 1..5 } != true) {
            issues += error(rowNumber, "Difficulty must be an integer between 1 and 5.")
        }
        val normalizedYear = year?.trim()?.takeIf(String::isNotBlank)
        val parsedYear = normalizedYear?.toIntOrNull()?.takeIf { it in 1900..2200 }
        if (normalizedYear != null && parsedYear == null) {
            issues += error(rowNumber, "Year must be an integer between 1900 and 2200.")
        }
        val accepted = issues.none { it.severity == QuestionImportIssueSeverity.ERROR }
        val draft = QuestionImportDraft(
            rowNumber = rowNumber,
            studyPlanId = planId,
            subjectId = subjectId?.trim()?.ifBlank { null }?.let(::SubjectId) ?: defaultSubjectId,
            topicId = topicId?.trim()?.ifBlank { null }?.let(::TopicId) ?: defaultTopicId,
            type = parsedType,
            statement = statement.trim(),
            options = parsedOptions,
            correctAnswer = correctAnswer.trim(),
            explanation = explanation.trim(),
            examBoard = examBoard?.trim()?.ifBlank { null },
            source = source?.trim()?.ifBlank { null },
            year = parsedYear,
            difficulty = parsedDifficulty,
            tags = tags.splitTags(),
            duplicateOfExisting = false,
            duplicateWithinImport = false,
            valid = accepted,
        )
        return ParsedDraft(draft = draft, issues = issues)
    }

    private fun splitRow(row: String, separator: Char): List<String> {
        if (separator == '\t') return row.split('\t').map(String::trim)
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < row.length) {
            val char = row[index]
            when {
                char == '"' && row.getOrNull(index + 1) == '"' && quoted -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == separator && !quoted -> {
                    cells += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        cells += current.toString().trim()
        return cells
    }

    private fun Map<String, String>.firstValue(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> this[name]?.trim()?.ifBlank { null } }

    private fun String?.splitTags(): List<String> = this.orEmpty()
        .split('|', ';', ',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    private fun error(rowNumber: Int, message: String) = QuestionImportIssue(
        rowNumber = rowNumber,
        severity = QuestionImportIssueSeverity.ERROR,
        message = message,
    )
}
