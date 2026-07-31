package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus

enum class SyllabusImportFormat {
    STRUCTURED_TEXT,
    CSV,
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
                val level = (indent / 2).coerceAtLeast(1)
                while (topicStack.isNotEmpty() && topicStack.last().first >= level) topicStack.removeLast()
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

    fun parseCsv(input: String): SyllabusImportDraft {
        val rows = input.lineSequence().filter { it.isNotBlank() }.map(::parseCsvLine).toList()
        require(rows.isNotEmpty()) { "The CSV syllabus is empty." }
        val header = rows.first().map { it.trim().lowercase() }
        val index = header.withIndex().associate { it.value to it.index }
        require("subject" in index && "topic" in index) { "CSV must include subject and topic columns." }
        val subjects = linkedMapOf<String, MutableSubject>()
        rows.drop(1).forEachIndexed { rowIndex, row ->
            fun value(name: String): String = index[name]?.let { row.getOrNull(it) }.orEmpty().trim()
            val subjectName = value("subject")
            require(subjectName.isNotBlank()) { "CSV row ${rowIndex + 2} has no subject." }
            val subject = subjects.getOrPut(subjectName.lowercase()) {
                MutableSubject(
                    key = "subject:${subjects.size}",
                    name = subjectName,
                    priority = value("subject_priority").toIntOrNull()?.coerceIn(1, 5) ?: 3,
                    examWeight = value("exam_weight").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0,
                    estimatedMinutes = value("subject_minutes").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                )
            }
            val topicName = value("topic")
            if (topicName.isBlank()) return@forEachIndexed
            val key = "${subject.key}:topic:${subject.topics.size}"
            val parentName = value("parent")
            val parentKey = parentName.takeIf(String::isNotBlank)?.let { name ->
                subject.topics.lastOrNull { it.name.equals(name, ignoreCase = true) }?.key
                    ?: error("CSV row ${rowIndex + 2} references unknown parent topic '$name'.")
            }
            subject.topics += SyllabusTopicDraft(
                key = key,
                parentKey = parentKey,
                name = topicName,
                priority = value("priority").toIntOrNull()?.coerceIn(1, 5) ?: 3,
                difficulty = value("difficulty").toIntOrNull()?.coerceIn(1, 5) ?: 3,
                examIncidencePercent = value("incidence").toIntOrNull()?.coerceIn(0, 100),
                estimatedMinutes = value("minutes").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                status = value("status").takeIf(String::isNotBlank)?.let {
                    runCatching { ContentProgressStatus.valueOf(it.uppercase()) }
                        .getOrDefault(ContentProgressStatus.NOT_STARTED)
                } ?: ContentProgressStatus.NOT_STARTED,
            )
        }
        return SyllabusImportDraft(subjects.values.map(MutableSubject::freeze))
    }

    private fun parseCsvLine(line: String): List<String> {
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
                character == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        require(!quoted) { "CSV contains an unclosed quoted value." }
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
