package com.mikeyphw.studyflow.core.model

enum class StudyFlowImportTemplateType {
    PROGRAMMING_COURSE_CSV,
    PROGRAMMING_COURSE_MARKDOWN,
    LANGUAGE_IDIOMS_CSV,
    LANGUAGE_IDIOMS_MARKDOWN,
}

enum class StudyFlowImportRowKind {
    PROGRAMMING_EXERCISE,
    PROGRAMMING_PROJECT,
    LANGUAGE_IDIOM,
}

data class StudyFlowImportTemplate(
    val type: StudyFlowImportTemplateType,
    val displayName: String,
    val fileName: String,
    val requiredColumns: List<String>,
    val samplePath: String,
) {
    init {
        require(displayName.isNotBlank()) { "Import template display name cannot be blank." }
        require(fileName.isNotBlank()) { "Import template filename cannot be blank." }
        require(requiredColumns.isNotEmpty()) { "Import template needs required columns." }
    }
}

data class StudyFlowImportIssue(
    val rowNumber: Int?,
    val column: String?,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Import issue message cannot be blank." }
    }
}

data class StudyFlowImportPreviewRow(
    val rowNumber: Int,
    val kind: StudyFlowImportRowKind,
    val title: String,
    val summary: String,
    val tags: List<String>,
    val raw: Map<String, String>,
) {
    init {
        require(rowNumber > 0) { "Preview row number must be positive." }
        require(title.isNotBlank()) { "Preview row title cannot be blank." }
    }
}

data class StudyFlowImportPreview(
    val templateType: StudyFlowImportTemplateType,
    val rows: List<StudyFlowImportPreviewRow>,
    val issues: List<StudyFlowImportIssue>,
) {
    val canImport: Boolean = issues.isEmpty() && rows.isNotEmpty()
}

object StudyFlowImportTemplates {
    val programmingCourseCsv = StudyFlowImportTemplate(
        type = StudyFlowImportTemplateType.PROGRAMMING_COURSE_CSV,
        displayName = "Programming course CSV",
        fileName = "programming_course_template.csv",
        requiredColumns = listOf("type", "module", "title", "prompt", "difficulty", "language"),
        samplePath = "samples/import/programming_course_template.csv",
    )

    val programmingCourseMarkdown = StudyFlowImportTemplate(
        type = StudyFlowImportTemplateType.PROGRAMMING_COURSE_MARKDOWN,
        displayName = "Programming course Markdown",
        fileName = "programming_course_template.md",
        requiredColumns = listOf("Module", "Exercise", "Prompt"),
        samplePath = "samples/import/programming_course_template.md",
    )

    val languageIdiomsCsv = StudyFlowImportTemplate(
        type = StudyFlowImportTemplateType.LANGUAGE_IDIOMS_CSV,
        displayName = "Language idioms CSV",
        fileName = "language_idioms_template.csv",
        requiredColumns = listOf("expression", "meaning", "example", "register", "difficulty"),
        samplePath = "samples/import/language_idioms_template.csv",
    )

    val languageIdiomsMarkdown = StudyFlowImportTemplate(
        type = StudyFlowImportTemplateType.LANGUAGE_IDIOMS_MARKDOWN,
        displayName = "Language idioms Markdown",
        fileName = "language_idioms_template.md",
        requiredColumns = listOf("Idiom", "Meaning", "Example"),
        samplePath = "samples/import/language_idioms_template.md",
    )

    val all: List<StudyFlowImportTemplate> = listOf(
        programmingCourseCsv,
        programmingCourseMarkdown,
        languageIdiomsCsv,
        languageIdiomsMarkdown,
    )
}

fun previewProgrammingCourseCsv(csv: String): StudyFlowImportPreview = previewCsv(
    csv = csv,
    template = StudyFlowImportTemplates.programmingCourseCsv,
    rowKind = { row ->
        when (row["type"]?.trim()?.lowercase()) {
            "project", "mini_project", "mini-project" -> StudyFlowImportRowKind.PROGRAMMING_PROJECT
            else -> StudyFlowImportRowKind.PROGRAMMING_EXERCISE
        }
    },
    title = { row -> row["title"].orEmpty() },
    summary = { row -> row["prompt"].orEmpty().ifBlank { row["module"].orEmpty() } },
)

fun previewLanguageIdiomsCsv(csv: String): StudyFlowImportPreview = previewCsv(
    csv = csv,
    template = StudyFlowImportTemplates.languageIdiomsCsv,
    rowKind = { StudyFlowImportRowKind.LANGUAGE_IDIOM },
    title = { row -> row["expression"].orEmpty() },
    summary = { row -> row["meaning"].orEmpty() },
)

fun previewProgrammingCourseMarkdown(markdown: String): StudyFlowImportPreview = previewMarkdown(
    markdown = markdown,
    template = StudyFlowImportTemplates.programmingCourseMarkdown,
    kind = StudyFlowImportRowKind.PROGRAMMING_EXERCISE,
    headingPrefix = "### Exercise:",
)

fun previewLanguageIdiomsMarkdown(markdown: String): StudyFlowImportPreview = previewMarkdown(
    markdown = markdown,
    template = StudyFlowImportTemplates.languageIdiomsMarkdown,
    kind = StudyFlowImportRowKind.LANGUAGE_IDIOM,
    headingPrefix = "## Idiom:",
)

private fun previewCsv(
    csv: String,
    template: StudyFlowImportTemplate,
    rowKind: (Map<String, String>) -> StudyFlowImportRowKind,
    title: (Map<String, String>) -> String,
    summary: (Map<String, String>) -> String,
): StudyFlowImportPreview {
    val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) {
        return StudyFlowImportPreview(
            templateType = template.type,
            rows = emptyList(),
            issues = listOf(StudyFlowImportIssue(null, null, "Import file is empty.")),
        )
    }
    val header = parseCsvLine(lines.first()).map { it.trim() }
    val issues = missingColumns(template, header).toMutableList()
    val rows = mutableListOf<StudyFlowImportPreviewRow>()

    lines.drop(1).forEachIndexed { index, line ->
        val rowNumber = index + 2
        val values = parseCsvLine(line)
        val row = header.mapIndexed { columnIndex, column -> column to values.getOrElse(columnIndex) { "" } }.toMap()
        val rowTitle = title(row).trim()
        if (rowTitle.isBlank()) {
            issues += StudyFlowImportIssue(rowNumber, "title", "Required title/expression value is blank.")
        } else {
            rows += StudyFlowImportPreviewRow(
                rowNumber = rowNumber,
                kind = rowKind(row),
                title = rowTitle,
                summary = summary(row).trim(),
                tags = splitImportTags(row["tags"].orEmpty()),
                raw = row,
            )
        }
    }

    return StudyFlowImportPreview(template.type, rows, issues)
}

private fun previewMarkdown(
    markdown: String,
    template: StudyFlowImportTemplate,
    kind: StudyFlowImportRowKind,
    headingPrefix: String,
): StudyFlowImportPreview {
    val lines = markdown.lineSequence().map { it.trim() }.toList()
    val rows = mutableListOf<StudyFlowImportPreviewRow>()
    val issues = mutableListOf<StudyFlowImportIssue>()
    var index = 0
    while (index < lines.size) {
        val heading = lines[index]
        if (!heading.startsWith(headingPrefix)) {
            index++
            continue
        }
        val rowNumber = rows.size + 1
        val title = heading.removePrefix(headingPrefix).trim()
        val block = mutableListOf<String>()
        index++
        while (index < lines.size && !lines[index].startsWith(headingPrefix)) {
            if (lines[index].isNotBlank()) block += lines[index]
            index++
        }
        if (title.isBlank()) {
            issues += StudyFlowImportIssue(rowNumber, headingPrefix, "Import heading title is blank.")
        } else {
            val summary = block.firstOrNull { it.startsWith("Meaning:") || it.startsWith("Prompt:") }
                ?.substringAfter(':')
                ?.trim()
                .orEmpty()
            val tags = block.firstOrNull { it.startsWith("Tags:") }
                ?.substringAfter(':')
                ?.let(::splitImportTags)
                .orEmpty()
            rows += StudyFlowImportPreviewRow(
                rowNumber = rowNumber,
                kind = kind,
                title = title,
                summary = summary,
                tags = tags,
                raw = mapOf("heading" to heading) + block.associateBy { it.substringBefore(':', it) },
            )
        }
    }
    if (rows.isEmpty() && issues.isEmpty()) {
        issues += StudyFlowImportIssue(null, headingPrefix, "No import headings found.")
    }
    return StudyFlowImportPreview(template.type, rows, issues)
}

private fun missingColumns(
    template: StudyFlowImportTemplate,
    header: List<String>,
): List<StudyFlowImportIssue> {
    val normalizedHeader = header.map { it.lowercase() }.toSet()
    return template.requiredColumns
        .filter { required -> required.lowercase() !in normalizedHeader }
        .map { required -> StudyFlowImportIssue(null, required, "Missing required column: $required") }
}

private fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                values += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    values += current.toString()
    return values
}

fun splitImportTags(value: String): List<String> = value
    .split('|', ',', ';')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
