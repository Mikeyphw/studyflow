package com.mikeyphw.studyflow.core.diagnostics

internal object DiagnosticJson {
    fun encodeEvent(event: DiagnosticEvent): String = buildString {
        append('{')
        appendNumber("schemaVersion", 1)
        append(',')
        appendString("sessionId", event.sessionId)
        append(',')
        appendNumber("sequence", event.sequence)
        append(',')
        appendNumber("timestampEpochMillis", event.timestampEpochMillis)
        append(',')
        appendString("area", event.area.name)
        append(',')
        appendString("severity", event.severity.name)
        append(',')
        appendString("action", event.action)
        append(',')
        appendString("outcome", event.outcome.name)
        append(',')
        appendNullableNumber("durationMillis", event.durationMillis)
        append(',')
        appendNullableString("correlationId", event.correlationId)
        append(',')
        append("\"safeDetails\":{")
        event.safeDetails.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendQuoted(key)
            append(':')
            appendQuoted(value)
        }
        append('}')
        append(',')
        append("\"exception\":")
        val exception = event.exception
        if (exception == null) {
            append("null")
        } else {
            append('{')
            appendString("type", exception.type)
            append(',')
            appendString("fingerprint", exception.fingerprint)
            append('}')
        }
        append('}')
    }

    fun encodeFlatObject(values: Map<String, String>): String = buildString {
        append('{')
        values.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendQuoted(key)
            append(':')
            appendQuoted(value)
        }
        append('}')
    }

    private fun StringBuilder.appendString(key: String, value: String) {
        appendQuoted(key)
        append(':')
        appendQuoted(value)
    }

    private fun StringBuilder.appendNullableString(key: String, value: String?) {
        appendQuoted(key)
        append(':')
        if (value == null) append("null") else appendQuoted(value)
    }

    private fun StringBuilder.appendNumber(key: String, value: Number) {
        appendQuoted(key)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendNullableNumber(key: String, value: Number?) {
        appendQuoted(key)
        append(':')
        if (value == null) append("null") else append(value)
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        append(DiagnosticRedactor.jsonEscape(value))
        append('"')
    }
}
