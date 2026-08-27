package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import java.text.Normalizer


enum class ExamNoticeImportIssueKind {
    DUPLICATE_SUBJECT,
    DUPLICATE_TOPIC,
    UNRECOGNIZED_HEADING,
    ORPHAN_TOPIC,
}


data class ExamNoticeImportIssue(
    val kind: ExamNoticeImportIssueKind,
    val value: String,
    val context: String? = null,
    val lineNumber: Int? = null,
)


data class ExamNoticeImportSource(
    val displayName: String,
    val format: SyllabusImportFormat,
    val text: String,
)


data class ExamNoticeImportPreview(
    val source: ExamNoticeImportSource,
    val draft: SyllabusImportDraft,
    val issues: List<ExamNoticeImportIssue>,
    val duplicateSubjects: Int,
    val duplicateTopics: Int,
    val destructiveChanges: Int = 0,
) {
    val canApply: Boolean
        get() = draft.subjects.isNotEmpty()
}


object ExamNoticeImportParser {
    private val explicitSubject = Regex(
        pattern = "^(?:disciplina|mat[eé]ria|subject)\\s*[:\\-]\\s*(.+)$",
        option = RegexOption.IGNORE_CASE,
    )
    private val numberedItem = Regex("^(\\d+(?:\\.\\d+)*)[.)]?\\s+(.+)$")
    private val sectionHeading = setOf(
        "ANEXO",
        "CONHECIMENTOS BASICOS",
        "CONHECIMENTOS ESPECIFICOS",
        "CONTEUDO PROGRAMATICO",
        "PROGRAMA",
    )

    fun preview(
        planId: StudyPlanId,
        source: ExamNoticeImportSource,
        existing: StudySyllabus,
    ): ExamNoticeImportPreview {
        require(existing.studyPlanId == planId) {
            "Existing syllabus belongs to another Study Plan."
        }
        require(source.text.isNotBlank()) { "The exam notice source is empty." }

        val parsed = when (source.format) {
            SyllabusImportFormat.STRUCTURED_TEXT -> parseStructuredNotice(source.text)
            SyllabusImportFormat.CSV,
            SyllabusImportFormat.TSV,
            -> ParsedDraft(SyllabusImportParser.parse(source.text, source.format), emptyList())
        }
        val duplicateIssues = detectDuplicates(parsed.draft, existing)
        val allIssues = parsed.issues + duplicateIssues
        return ExamNoticeImportPreview(
            source = source.copy(),
            draft = parsed.draft,
            issues = allIssues,
            duplicateSubjects = allIssues.count { it.kind == ExamNoticeImportIssueKind.DUPLICATE_SUBJECT },
            duplicateTopics = allIssues.count { it.kind == ExamNoticeImportIssueKind.DUPLICATE_TOPIC },
        )
    }

    private fun parseStructuredNotice(input: String): ParsedDraft {
        val lines = input.lineSequence().toList()
        if (lines.any(::usesIndentation)) {
            return ParsedDraft(SyllabusImportParser.parseStructuredText(input), emptyList())
        }

        val subjects = mutableListOf<MutableExamNoticeSubject>()
        val issues = mutableListOf<ExamNoticeImportIssue>()
        var currentSubject: MutableExamNoticeSubject? = null
        val numberedKeys = mutableMapOf<String, String>()

        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed

            explicitSubject.matchEntire(line)?.let { match ->
                currentSubject = MutableExamNoticeSubject(
                    key = "subject:${subjects.size}",
                    name = match.groupValues[1].trim(),
                ).also(subjects::add)
                numberedKeys.clear()
                return@forEachIndexed
            }

            if (isSectionHeading(line)) {
                issues += ExamNoticeImportIssue(
                    kind = ExamNoticeImportIssueKind.UNRECOGNIZED_HEADING,
                    value = line,
                    lineNumber = lineNumber,
                )
                return@forEachIndexed
            }

            numberedItem.matchEntire(line)?.let { match ->
                val subject = currentSubject ?: run {
                    issues += ExamNoticeImportIssue(
                        kind = ExamNoticeImportIssueKind.ORPHAN_TOPIC,
                        value = match.groupValues[2].trim(),
                        lineNumber = lineNumber,
                    )
                    return@forEachIndexed
                }
                val path = match.groupValues[1]
                val parentPath = path.substringBeforeLast('.', missingDelimiterValue = "")
                val parentKey = parentPath.takeIf(String::isNotEmpty)?.let(numberedKeys::get)
                if (parentPath.isNotEmpty() && parentKey == null) {
                    issues += ExamNoticeImportIssue(
                        kind = ExamNoticeImportIssueKind.ORPHAN_TOPIC,
                        value = match.groupValues[2].trim(),
                        context = parentPath,
                        lineNumber = lineNumber,
                    )
                }
                val key = "${subject.key}:topic:${subject.topics.size}"
                subject.topics += SyllabusTopicDraft(
                    key = key,
                    parentKey = parentKey,
                    name = match.groupValues[2].trim(),
                )
                numberedKeys[path] = key
                return@forEachIndexed
            }

            if (looksLikeSubject(line)) {
                currentSubject = MutableExamNoticeSubject(
                    key = "subject:${subjects.size}",
                    name = line.removeSuffix(":"),
                ).also(subjects::add)
                numberedKeys.clear()
                return@forEachIndexed
            }

            val subject = currentSubject
            if (subject == null) {
                issues += ExamNoticeImportIssue(
                    kind = ExamNoticeImportIssueKind.UNRECOGNIZED_HEADING,
                    value = line,
                    lineNumber = lineNumber,
                )
            } else {
                val key = "${subject.key}:topic:${subject.topics.size}"
                subject.topics += SyllabusTopicDraft(
                    key = key,
                    parentKey = null,
                    name = line.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim(),
                )
            }
        }

        require(subjects.isNotEmpty()) {
            "No discipline could be detected in the exam notice. Add 'Disciplina: Name' headings or indentation."
        }
        val draft = SyllabusImportDraft(subjects.map(MutableExamNoticeSubject::freeze))
        return ParsedDraft(draft, issues)
    }

    private fun detectDuplicates(
        draft: SyllabusImportDraft,
        existing: StudySyllabus,
    ): List<ExamNoticeImportIssue> {
        val issues = mutableListOf<ExamNoticeImportIssue>()
        val existingSubjects = existing.subjects.associateBy { canonical(it.name) }
        val importedSubjectNames = mutableSetOf<String>()

        draft.subjects.forEach { subject ->
            val normalizedSubject = canonical(subject.name)
            val existingSubject = existingSubjects[normalizedSubject]
            if (!importedSubjectNames.add(normalizedSubject) || existingSubject != null) {
                issues += ExamNoticeImportIssue(
                    kind = ExamNoticeImportIssueKind.DUPLICATE_SUBJECT,
                    value = subject.name,
                )
            }

            val existingTopics = existingSubject?.let { storedSubject -> existing.topicsFor(storedSubject.id) }
                .orEmpty()
                .mapTo(mutableSetOf()) { topic -> canonical(topic.name) }
            val importedTopics = mutableSetOf<String>()
            subject.topics.forEach { topic ->
                val normalizedTopic = canonical(topic.name)
                if (!importedTopics.add(normalizedTopic) || normalizedTopic in existingTopics) {
                    issues += ExamNoticeImportIssue(
                        kind = ExamNoticeImportIssueKind.DUPLICATE_TOPIC,
                        value = topic.name,
                        context = subject.name,
                    )
                }
            }
        }
        return issues
    }

    private fun usesIndentation(line: String): Boolean =
        line.isNotBlank() && line.firstOrNull()?.isWhitespace() == true

    private fun isSectionHeading(line: String): Boolean = canonical(line.removeSuffix(":")) in sectionHeading

    private fun looksLikeSubject(line: String): Boolean {
        val letters = line.filter(Char::isLetter)
        if (letters.length < 3 || line.length > 100) return false
        val uppercaseLetters = letters.count(Char::isUpperCase)
        return uppercaseLetters.toDouble() / letters.length >= 0.8
    }

    private fun canonical(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()
        .replace(Regex("\\s+"), " ")

    private data class ParsedDraft(
        val draft: SyllabusImportDraft,
        val issues: List<ExamNoticeImportIssue>,
    )

    private data class MutableExamNoticeSubject(
        val key: String,
        val name: String,
        val topics: MutableList<SyllabusTopicDraft> = mutableListOf(),
    ) {
        fun freeze() = SyllabusSubjectDraft(key = key, name = name, topics = topics.toList())
    }
}
