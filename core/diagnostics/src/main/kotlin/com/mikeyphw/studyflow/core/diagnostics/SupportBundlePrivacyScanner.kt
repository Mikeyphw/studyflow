package com.mikeyphw.studyflow.core.diagnostics

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class SupportBundlePayloadScan(
    val sanitizedBytes: ByteArray,
    val timelineLineCount: Int = 0,
    val privacyViolationCount: Int = 0,
    val schemaViolationCount: Int = 0,
) {
    val passed: Boolean get() = privacyViolationCount == 0 && schemaViolationCount == 0
}

/** Final outbound privacy/schema gate for every text entry in a support bundle. */
object SupportBundlePrivacyScanner {
    private val eventEntry = Regex("events/(?:current|retained-[0-9]{3})\\.jsonl")
    private val credential = Regex("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val uri = Regex("(?i)\\b(?:https?|content|file)://[^\\s,;]+")
    private val path = Regex("(?<![A-Za-z0-9])/(?:data|storage|sdcard|home|mnt|system|vendor|apex|product)/[^\\s,;]+")
    private val jwt = Regex("\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b")
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val rawSecretAssignment = Regex(
        "(?i)(?:password|passphrase|token|secret|cookie|authorization|api[_-]?key|access[_-]?token|refresh[_-]?token)" +
            "[\\\"' ]*[:=][ ]*[\\\"']?(?!<redacted(?:-[a-z]+)?(?:>|-content>))[^\\\"'\\s,}]{4,}",
    )

    fun sanitize(name: String, bytes: ByteArray): SupportBundlePayloadScan {
        val text = decodeUtf8(bytes) ?: return SupportBundlePayloadScan(
            sanitizedBytes = ByteArray(0),
            schemaViolationCount = 1,
        )
        val sanitized = if (eventEntry.matches(name)) {
            val result = DiagnosticTimelineReader.resanitizeCompleteJsonl(text)
            SupportBundlePayloadScan(
                sanitizedBytes = result.jsonl.toByteArray(Charsets.UTF_8),
                timelineLineCount = result.lineCount,
                schemaViolationCount = result.malformedLineCount,
            )
        } else {
            val schemaViolations = when {
                name.endsWith(".json") && !(text.trim().startsWith("{") && text.trim().endsWith("}")) -> 1
                name == "redaction-report.txt" && !text.endsWith("\n") -> 1
                else -> 0
            }
            SupportBundlePayloadScan(
                sanitizedBytes = bytes,
                schemaViolationCount = schemaViolations,
            )
        }
        val sanitizedText = decodeUtf8(sanitized.sanitizedBytes).orEmpty()
        val privacyViolations = listOf(credential, email, uri, path, jwt, uuid, rawSecretAssignment)
            .count { pattern -> pattern.containsMatchIn(sanitizedText) }
        return sanitized.copy(privacyViolationCount = privacyViolations)
    }

    fun redactionReportIsValid(text: String): Boolean {
        val values = text.lineSequence()
            .filter { it.contains('=') }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        return values["status"] == "PASS" &&
            values["privacyViolations"] == "0" &&
            values["schemaViolations"] == "0" &&
            values["automaticUpload"] == "false"
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        null
    }
}
