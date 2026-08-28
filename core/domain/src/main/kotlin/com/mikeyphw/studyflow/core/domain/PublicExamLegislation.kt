package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LegislationProvisionKind

enum class LegislationImportFormat {
    STRUCTURED_TEXT,
    MARKDOWN,
    CSV,
}

data class LegislationProvisionDraft(
    val key: String,
    val reference: String,
    val parentReference: String?,
    val kind: LegislationProvisionKind,
    val text: String,
)

data class LegislationImportPreview(
    val title: String,
    val sourceReference: String?,
    val format: LegislationImportFormat,
    val provisions: List<LegislationProvisionDraft>,
    val warnings: List<String>,
) {
    init {
        require(title.isNotBlank()) { "Legislation title cannot be blank." }
        require(provisions.isNotEmpty()) { "Legislation preview must contain at least one provision." }
    }
}

object LegislationImportParser {
    private val article = Regex("^(Art\\.?\\s*\\d+[A-Za-zº°.-]*)(?:\\s*[-–—.]?\\s*)(.*)$", RegexOption.IGNORE_CASE)
    private val paragraph = Regex(
        "^(§\\s*\\d+[A-Za-zº°.-]*|Parágrafo\\s+único)(?:\\s*[-–—.]?\\s*)(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val romanItem = Regex("^([IVXLCDM]+)\\s*[-–—)]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val letterItem = Regex("^([a-z])\\)\\s*(.*)$", RegexOption.IGNORE_CASE)

    fun preview(
        title: String,
        sourceReference: String?,
        rawText: String,
        format: LegislationImportFormat,
    ): LegislationImportPreview {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotBlank()) { "Legislation title cannot be blank." }
        val provisions = when (format) {
            LegislationImportFormat.CSV -> parseCsv(rawText)
            LegislationImportFormat.STRUCTURED_TEXT,
            LegislationImportFormat.MARKDOWN,
            -> parseStructured(rawText, markdown = format == LegislationImportFormat.MARKDOWN)
        }
        require(provisions.isNotEmpty()) { "No legislation provisions were recognized." }
        val duplicateRefs = provisions.groupingBy { it.reference.lowercase() }.eachCount().filterValues { it > 1 }.keys
        val warnings = buildList {
            if (duplicateRefs.isNotEmpty()) {
                add("Duplicate references: ${duplicateRefs.sorted().joinToString()}")
            }
            if (provisions.none { it.kind == LegislationProvisionKind.ARTICLE }) {
                add("No article boundary was recognized; verify the preview before applying.")
            }
        }
        return LegislationImportPreview(
            title = normalizedTitle,
            sourceReference = sourceReference?.trim()?.takeIf(String::isNotEmpty),
            format = format,
            provisions = provisions,
            warnings = warnings,
        )
    }

    private fun parseStructured(rawText: String, markdown: Boolean): List<LegislationProvisionDraft> {
        val result = mutableListOf<LegislationProvisionDraft>()
        val keyOccurrences = mutableMapOf<String, Int>()
        var currentArticleReference: String? = null
        var currentArticleKey: String? = null
        var currentParentReference: String? = null
        var currentParentKey: String? = null
        rawText.lineSequence().forEach { rawLine ->
            var line = rawLine.trim()
            if (markdown) line = line.removePrefix("#").trim().removePrefix("-").trim()
            if (line.isBlank()) return@forEach
            val match = article.matchEntire(line)
                ?: paragraph.matchEntire(line)
                ?: romanItem.matchEntire(line)
                ?: letterItem.matchEntire(line)
            if (match == null) {
                val last = result.lastOrNull()
                if (last != null) {
                    result[result.lastIndex] = last.copy(text = "${last.text} $line".trim())
                }
                return@forEach
            }
            val reference = match.groupValues[1].trim()
            val body = match.groupValues.getOrNull(2).orEmpty().trim().ifBlank { reference }
            val kind = when {
                article.matches(line) -> LegislationProvisionKind.ARTICLE
                paragraph.matches(line) -> LegislationProvisionKind.PARAGRAPH
                romanItem.matches(line) -> LegislationProvisionKind.ITEM
                letterItem.matches(line) -> LegislationProvisionKind.SUBITEM
                else -> LegislationProvisionKind.OTHER
            }
            val parentReference = when (kind) {
                LegislationProvisionKind.ARTICLE -> null
                LegislationProvisionKind.PARAGRAPH -> currentArticleReference
                LegislationProvisionKind.ITEM,
                LegislationProvisionKind.SUBITEM,
                -> currentParentReference ?: currentArticleReference
                LegislationProvisionKind.OTHER -> currentArticleReference
            }
            val canonicalReference = canonicalKeyPart(reference)
            val baseKey = when (kind) {
                LegislationProvisionKind.ARTICLE -> "article/$canonicalReference"
                LegislationProvisionKind.PARAGRAPH ->
                    "${currentArticleKey ?: "root"}/paragraph/$canonicalReference"
                LegislationProvisionKind.ITEM ->
                    "${currentParentKey ?: currentArticleKey ?: "root"}/item/$canonicalReference"
                LegislationProvisionKind.SUBITEM ->
                    "${currentParentKey ?: currentArticleKey ?: "root"}/subitem/$canonicalReference"
                LegislationProvisionKind.OTHER ->
                    "${currentArticleKey ?: "root"}/other/$canonicalReference"
            }
            val occurrence = keyOccurrences.merge(baseKey, 1, Int::plus) ?: 1
            val key = if (occurrence == 1) baseKey else "$baseKey#$occurrence"
            result += LegislationProvisionDraft(
                key = key,
                reference = reference,
                parentReference = parentReference,
                kind = kind,
                text = body,
            )
            when (kind) {
                LegislationProvisionKind.ARTICLE -> {
                    currentArticleReference = reference
                    currentArticleKey = key
                    currentParentReference = reference
                    currentParentKey = key
                }
                LegislationProvisionKind.PARAGRAPH,
                LegislationProvisionKind.ITEM,
                -> {
                    currentParentReference = reference
                    currentParentKey = key
                }
                LegislationProvisionKind.SUBITEM,
                LegislationProvisionKind.OTHER,
                -> Unit
            }
        }
        return result
    }

    private fun parseCsv(rawText: String): List<LegislationProvisionDraft> {
        val rows = rawText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(rows.isNotEmpty()) { "Legislation CSV is empty." }
        val header = parseCsvLine(rows.first()).map { it.trim().lowercase() }
        val refIndex = header.indexOf("reference")
        val textIndex = header.indexOf("text")
        require(refIndex >= 0 && textIndex >= 0) { "Legislation CSV requires reference and text columns." }
        val parentIndex = header.indexOf("parentreference")
        val kindIndex = header.indexOf("kind")
        val keyOccurrences = mutableMapOf<String, Int>()
        return rows.drop(1).mapIndexed { index, row ->
            val values = parseCsvLine(row)
            val reference = values.getOrNull(refIndex).orEmpty().trim()
            val text = values.getOrNull(textIndex).orEmpty().trim()
            require(reference.isNotBlank() && text.isNotBlank()) {
                "Legislation CSV row ${index + 2} is incomplete."
            }
            val parentReference = values.getOrNull(parentIndex)?.trim()?.takeIf(String::isNotEmpty)
            val kind = values.getOrNull(kindIndex)?.trim()?.takeIf(String::isNotEmpty)?.let {
                runCatching { LegislationProvisionKind.valueOf(it.uppercase()) }.getOrNull()
            } ?: inferKind(reference)
            val baseKey = listOfNotNull(
                "csv",
                parentReference?.let(::canonicalKeyPart),
                kind.name.lowercase(),
                canonicalKeyPart(reference),
            ).joinToString("/")
            val occurrence = keyOccurrences.merge(baseKey, 1, Int::plus) ?: 1
            LegislationProvisionDraft(
                key = if (occurrence == 1) baseKey else "$baseKey#$occurrence",
                reference = reference,
                parentReference = parentReference,
                kind = kind,
                text = text,
            )
        }
    }

    private fun canonicalKeyPart(value: String): String = value
        .lowercase()
        .replace('º', 'o')
        .replace('°', 'o')
        .replace(Regex("[^a-z0-9§]+"), "-")
        .trim('-')

    private fun inferKind(reference: String): LegislationProvisionKind = when {
        reference.startsWith("Art", ignoreCase = true) -> LegislationProvisionKind.ARTICLE
        reference.startsWith("§") || reference.startsWith("Parágrafo", ignoreCase = true) ->
            LegislationProvisionKind.PARAGRAPH
        reference.matches(Regex("[IVXLCDM]+", RegexOption.IGNORE_CASE)) -> LegislationProvisionKind.ITEM
        reference.matches(Regex("[a-z]", RegexOption.IGNORE_CASE)) -> LegislationProvisionKind.SUBITEM
        else -> LegislationProvisionKind.OTHER
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val c = line[index]
            when {
                c == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"'); index++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            index++
        }
        require(!quoted) { "Legislation CSV contains an unclosed quoted field." }
        out += current.toString()
        return out
    }
}
