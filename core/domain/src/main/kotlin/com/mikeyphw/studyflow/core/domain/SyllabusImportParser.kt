package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus

enum class SyllabusImportFormat {
    STRUCTURED_TEXT,
    CSV,
    TSV,
}

data class SyllabusImportDraft(
    val subjects: List<SyllabusSubjectDraft>,
    val warnings: List<String> = emptyList(),
)

data class SyllabusSubjectDraft(
    val key: String,
    val name: String,
    val priority: Int = 3,
    val examWeight: Double = 1.0,
    val estimatedMinutes: Int = 0,
    val topics: List<SyllabusTopicDraft> = emptyList(),
)

data class SyllabusTopicDraft(
    val key: String,
    val parentKey: String?,
    val name: String,
    val priority: Int = 3,
    val difficulty: Int = 3,
    val examIncidencePercent: Int? = null,
    val estimatedMinutes: Int = 0,
    val progressPercent: Int = 0,
    val masteryPercent: Int = 0,
    val status: ContentProgressStatus = ContentProgressStatus.NOT_STARTED,
)

data class SyllabusImportResult(
    val importedSubjects: Int,
    val importedTopics: Int,
)

object SyllabusImportParser {
    fun parse(input: String, format: SyllabusImportFormat): SyllabusImportDraft = when (format) {
        SyllabusImportFormat.STRUCTURED_TEXT -> parseStructuredText(input)
        SyllabusImportFormat.CSV -> parseCsv(input)
        SyllabusImportFormat.TSV -> parseTsv(input)
    }

    fun parseStructuredText(input: String): SyllabusImportDraft {
        val subjects = mutableListOf<MutableSubject>()
        var currentSubject: MutableSubject? = null
        val topicStack = mutableListOf<Pair<Int, SyllabusTopicDraft>>()

        input.lineSequence().forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            val expanded = raw.replace("\t", "  ")
            val indent = expanded.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val name = expanded.trim().removePrefix("- ").removePrefix("* ").trim()
            require(name.isNotBlank()) { "Line ${index + 1} is empty after removing its marker." }
            if (indent == 0) {
                currentSubject = MutableSubject(key = "subject:${subjects.size}", name = name).also(subjects::add)
                topicStack.clear()
            } else {
                val subject = requireNotNull(currentSubject) {
                    "Line ${index + 1} defines a topic before any subject."
                }
                require(indent % 2 == 0) { "Line ${index + 1} uses partial indentation; use two spaces per level." }
                val level = indent / 2
                require(level >= 1) { "Line ${index + 1} has invalid topic indentation." }
                val previousLevel = topicStack.lastOrNull()?.first ?: 0
                require(level <= previousLevel + 1) {
                    "Line ${index + 1} jumps from indentation level $previousLevel to $level."
                }
                while (topicStack.isNotEmpty() && topicStack.last().first >= level) topicStack.removeLast()
                require(level == 1 || topicStack.lastOrNull()?.first == level - 1) {
                    "Line ${index + 1} has no parent at indentation level ${level - 1}."
                }
                val key = "${subject.key}:topic:${subject.topics.size}"
                val topic = SyllabusTopicDraft(
                    key = key,
                    parentKey = topicStack.lastOrNull()?.second?.key,
                    name = name,
                )
                subject.topics += topic
                topicStack += level to topic
            }
        }
        require(subjects.isNotEmpty()) { "The syllabus text does not contain any subjects." }
        return SyllabusImportDraft(subjects.map(MutableSubject::freeze))
    }

    fun parseCsv(input: String): SyllabusImportDraft = parseDelimited(input, ',', "CSV")

    fun parseTsv(input: String): SyllabusImportDraft = parseDelimited(input, '\t', "TSV")

    private fun parseDelimited(input: String, delimiter: Char, label: String): SyllabusImportDraft {
        val rows = input.lineSequence()
            .filter { it.isNotBlank() }
            .map { line -> parseDelimitedLine(line, delimiter) }
            .toList()
        require(rows.isNotEmpty()) { "The $label syllabus is empty." }
        val header = rows.first().mapIndexed { column, value ->
            value.trim().let { normalized -> if (column == 0) normalized
                .removePrefix("\uFEFF") else normalized }.lowercase()
        }
        val index = header.withIndex().associate { it.value to it.index }
        require("subject" in index && "topic" in index) { "$label must include subject and topic columns." }

        data class RawTopic(
            val rowNumber: Int,
            val key: String,
            val name: String,
            val parentName: String?,
            val priority: Int,
            val difficulty: Int,
            val incidence: Int?,
            val minutes: Int,
            val status: ContentProgressStatus,
        )
        data class RawSubject(
            val mutable: MutableSubject,
            val topics: MutableList<RawTopic> = mutableListOf(),
        )

        val subjects = linkedMapOf<String, RawSubject>()
        rows.drop(1).forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 2
            fun value(name: String): String = index[name]?.let { row.getOrNull(it) }.orEmpty().trim()
            val subjectName = value("subject")
            require(subjectName.isNotBlank()) { "$label row $rowNumber has no subject." }
            val subjectKey = subjectName.lowercase()
            val rawSubject = subjects.getOrPut(subjectKey) {
                RawSubject(
                    MutableSubject(
                        key = "subject:${subjects.size}",
                        name = subjectName,
                        priority = strictInt(value("subject_priority"), "subject_priority", rowNumber, 1..5, 3),
                        examWeight = strictDouble(value("exam_weight"), "exam_weight", rowNumber,
                            minimum = 0.0, default = 1.0),
                        estimatedMinutes = strictInt(value("subject_minutes"), "subject_minutes", rowNumber,
                            0..Int.MAX_VALUE, 0),
                    ),
                )
            }
            val topicName = value("topic")
            if (topicName.isBlank()) return@forEachIndexed
            rawSubject.topics += RawTopic(
                rowNumber = rowNumber,
                key = "${rawSubject.mutable.key}:topic:${rawSubject.topics.size}",
                name = topicName,
                parentName = value("parent").takeIf(String::isNotBlank),
                priority = strictInt(value("priority"), "priority", rowNumber, 1..5, 3),
                difficulty = strictInt(value("difficulty"), "difficulty", rowNumber, 1..5, 3),
                incidence = strictNullableInt(value("incidence"), "incidence", rowNumber, 0..100),
                minutes = strictInt(value("minutes"), "minutes", rowNumber, 0..Int.MAX_VALUE, 0),
                status = strictStatus(value("status"), rowNumber),
            )
        }

        subjects.values.forEach { rawSubject ->
            val byName = rawSubject.topics.groupBy { it.name.trim().lowercase() }
            rawSubject.topics.forEach { rawTopic ->
                val parentKey = rawTopic.parentName?.let { parentName ->
                    val matches = byName[parentName.trim().lowercase()].orEmpty()
                    require(matches.isNotEmpty()) {
                        "$label row ${rawTopic.rowNumber} references unknown parent topic '$parentName'."
                    }
                    require(matches.size == 1) {
                        "$label row ${rawTopic.rowNumber} references ambiguous parent topic '$parentName'."
                    }
                    require(matches.single().key != rawTopic.key) {
                        "$label row ${rawTopic.rowNumber} cannot make a topic its own parent."
                    }
                    matches.single().key
                }
                rawSubject.mutable.topics += SyllabusTopicDraft(
                    key = rawTopic.key,
                    parentKey = parentKey,
                    name = rawTopic.name,
                    priority = rawTopic.priority,
                    difficulty = rawTopic.difficulty,
                    examIncidencePercent = rawTopic.incidence,
                    estimatedMinutes = rawTopic.minutes,
                    status = rawTopic.status,
                )
            }
        }
        require(subjects.isNotEmpty()) { "The $label syllabus does not contain any subjects." }
        return SyllabusImportDraft(subjects.values.map { it.mutable.freeze() })
    }

    private fun strictInt(value: String, field: String, row: Int, range: IntRange, default: Int): Int {
        if (value.isBlank()) return default
        val parsed = value.toIntOrNull() ?: error("Delimited row $row has invalid integer '$value' for $field.")
        require(parsed in range) { "Delimited row $row has out-of-range value '$value' for $field." }
        return parsed
    }

    private fun strictNullableInt(value: String, field: String, row: Int, range: IntRange): Int? {
        if (value.isBlank()) return null
        return strictInt(value, field, row, range, range.first)
    }

    private fun strictDouble(value: String, field: String, row: Int, minimum: Double, default: Double): Double {
        if (value.isBlank()) return default
        val parsed = value.toDoubleOrNull() ?: error("Delimited row $row has invalid number '$value' for $field.")
        require(parsed.isFinite() && parsed >= minimum) {
            "Delimited row $row has out-of-range value '$value' for $field."
        }
        return parsed
    }

    private fun strictStatus(value: String, row: Int): ContentProgressStatus {
        if (value.isBlank()) return ContentProgressStatus.NOT_STARTED
        return runCatching { ContentProgressStatus.valueOf(value.uppercase()) }
            .getOrElse { error("Delimited row $row has invalid status '$value'.") }
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == delimiter && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        require(!quoted) { "Delimited input contains an unclosed quoted value." }
        values += current.toString()
        return values
    }

    private data class MutableSubject(
        val key: String,
        val name: String,
        val priority: Int = 3,
        val examWeight: Double = 1.0,
        val estimatedMinutes: Int = 0,
        val topics: MutableList<SyllabusTopicDraft> = mutableListOf(),
    ) {
        fun freeze() = SyllabusSubjectDraft(key, name, priority, examWeight, estimatedMinutes, topics.toList())
    }
}
