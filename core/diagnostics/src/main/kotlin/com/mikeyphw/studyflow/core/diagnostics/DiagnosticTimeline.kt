package com.mikeyphw.studyflow.core.diagnostics

/** A redacted event prepared for the in-app Debug Workbench timeline. */
data class DiagnosticTimelineEntry(
    val sequence: Long,
    val timestampEpochMillis: Long,
    val area: DiagnosticArea,
    val severity: DiagnosticSeverity,
    val action: String,
    val outcome: DiagnosticOutcome,
    val durationMillis: Long?,
    val correlationFingerprint: String?,
    val safeDetails: Map<String, String>,
    val exception: DiagnosticExceptionSummary?,
)

data class DiagnosticTimelineSnapshot(
    val entries: List<DiagnosticTimelineEntry>,
    val malformedLineCount: Int,
    val sourceCharacterCount: Int,
) {
    val eventCount: Int = entries.size
    val errorCount: Int = entries.count { entry -> entry.severity == DiagnosticSeverity.ERROR }
    val warningCount: Int = entries.count { entry -> entry.severity == DiagnosticSeverity.WARNING }
    val failedCount: Int = entries.count { entry -> entry.outcome == DiagnosticOutcome.FAILED }
    val latestTimestampEpochMillis: Long? = entries.maxOfOrNull(DiagnosticTimelineEntry::timestampEpochMillis)
}

object DiagnosticTimelineReader {
    fun read(
        jsonl: String,
        maxEntries: Int = 250,
    ): DiagnosticTimelineSnapshot {
        val boundedMaxEntries = maxEntries.coerceIn(1, 1_000)
        var malformed = 0
        val entries = jsonl
            .lineSequence()
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                runCatching { decodeLine(line) }
                    .getOrElse {
                        malformed += 1
                        null
                    }
            }
            .toList()
            .takeLast(boundedMaxEntries)
            .asReversed()
        return DiagnosticTimelineSnapshot(
            entries = entries,
            malformedLineCount = malformed,
            sourceCharacterCount = jsonl.length,
        )
    }

    private fun decodeLine(line: String): DiagnosticTimelineEntry {
        val root = JsonObjectReader(line).readObject()
        return DiagnosticTimelineEntry(
            sequence = root.requireLong("sequence"),
            timestampEpochMillis = root.requireLong("timestampEpochMillis"),
            area = enumValueOf(root.requireString("area")),
            severity = enumValueOf(root.requireString("severity")),
            action = DiagnosticRedactor.safeAction(root.requireString("action")),
            outcome = enumValueOf(root.requireString("outcome")),
            durationMillis = root.optionalLong("durationMillis"),
            correlationFingerprint = root.optionalString("correlationId")
                ?.let(DiagnosticRedactor::fingerprint),
            safeDetails = DiagnosticRedactor.redactDetails(
                root.optionalObject("safeDetails")
                    ?.entries
                    ?.associateTo(linkedMapOf()) { (key, value) -> key to (value as? String).orEmpty() }
                    .orEmpty(),
            ),
            exception = root.optionalObject("exception")?.let { exception ->
                DiagnosticExceptionSummary(
                    type = exception.requireString("type"),
                    fingerprint = exception.requireString("fingerprint"),
                ).let(DiagnosticRedactor::sanitizeExceptionSummary)
            },
        )
    }
}

private fun Map<String, Any?>.requireString(key: String): String =
    this[key] as? String ?: error("Missing string field: $key")

private fun Map<String, Any?>.optionalString(key: String): String? = this[key] as? String

private fun Map<String, Any?>.requireLong(key: String): Long = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    else -> error("Missing numeric field: $key")
}

private fun Map<String, Any?>.optionalLong(key: String): Long? = when (val value = this[key]) {
    null -> null
    is Long -> value
    is Int -> value.toLong()
    else -> error("Invalid numeric field: $key")
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.optionalObject(key: String): Map<String, Any?>? =
    this[key] as? Map<String, Any?>

/** Minimal object-only JSON reader for the recorder's fixed JSONL schema. */
private class JsonObjectReader(private val source: String) {
    private var index: Int = 0
    private var depth: Int = 0

    fun readObject(): Map<String, Any?> {
        val result = readObjectValue()
        skipWhitespace()
        check(index == source.length) { "Trailing JSON content" }
        return result
    }

    private fun readObjectValue(): Map<String, Any?> {
        check(depth < MaxDepth) { "JSON nesting is too deep" }
        depth += 1
        skipWhitespace()
        expect('{')
        val result = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (consume('}')) {
            depth -= 1
            return result
        }
        while (true) {
            check(result.size < MaxObjectMembers) { "JSON object has too many members" }
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        depth -= 1
        return result
    }

    private fun readValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '"' -> readString()
            '{' -> readObjectValue()
            'n' -> readLiteral("null", null)
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            '-', in '0'..'9' -> readLong()
            else -> error("Unsupported JSON value at $index")
        }
    }

    private fun readString(): String {
        skipWhitespace()
        expect('"')
        return buildString {
            while (index < source.length) {
                val character = source[index++]
                when (character) {
                    '"' -> return@buildString
                    '\\' -> append(readEscape())
                    else -> {
                        check(length < MaxStringLength) { "JSON string is too long" }
                        append(character)
                    }
                }
            }
            error("Unterminated JSON string")
        }
    }

    private fun readEscape(): Char {
        check(index < source.length) { "Incomplete JSON escape" }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                check(index + 4 <= source.length) { "Incomplete unicode escape" }
                val value = source.substring(index, index + 4).toInt(16).toChar()
                index += 4
                value
            }
            else -> error("Unsupported JSON escape: $escaped")
        }
    }

    private fun readLong(): Long {
        val start = index
        if (peek() == '-') index += 1
        while (peekOrNull()?.isDigit() == true) index += 1
        return source.substring(start, index).toLong()
    }

    private fun <T> readLiteral(literal: String, value: T): T {
        check(source.startsWith(literal, index)) { "Expected $literal" }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (peekOrNull()?.isWhitespace() == true) index += 1
    }

    private fun expect(expected: Char) {
        skipWhitespace()
        check(peek() == expected) { "Expected $expected at $index" }
        index += 1
    }

    private fun consume(expected: Char): Boolean {
        skipWhitespace()
        if (peekOrNull() != expected) return false
        index += 1
        return true
    }

    private fun peek(): Char = peekOrNull() ?: error("Unexpected end of JSON")

    private fun peekOrNull(): Char? = source.getOrNull(index)

    private companion object {
        const val MaxDepth = 8
        const val MaxObjectMembers = 64
        const val MaxStringLength = 4_096
    }
}
