package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun sensitiveKeysAreRedactedAcrossCaseAndCommonShapes() {
        val redacted = DiagnosticRedactor.redactDetails(
            linkedMapOf(
                "Authorization" to "Bearer abcdefghijklmnop",
                "COOKIE" to "sid=secret-cookie",
                "accessToken" to "secret-access-token",
                "refresh_token" to "secret-refresh-token",
                "api-key" to "secret-api-key",
                "passphrase" to "secret-passphrase",
            ),
        )

        assertEquals(6, redacted.size)
        assertTrue(redacted.values.all { value -> value == "<redacted>" })
    }

    @Test
    fun privateContentUrisPathsEmailsAndIdentifiersDoNotLeak() {
        val planId = "7d9b66ba-199d-4c38-980d-5cc0e3b9bc70"
        val redacted = DiagnosticRedactor.redactDetails(
            mapOf(
                "planName" to "My private exam plan",
                "questionText" to "What is the answer?",
                "sourceUri" to "content://com.example.provider/private/annex.pdf",
                "absolutePath" to "/data/user/0/com.example/files/private.txt",
                "planId" to planId,
                "contact" to "student@example.com",
            ),
        )
        val rendered = redacted.toString()

        assertEquals("<redacted-content>", redacted.getValue("plan_name"))
        assertEquals("<redacted-content>", redacted.getValue("question_text"))
        assertEquals("<redacted-content-uri>", redacted.getValue("source_uri"))
        assertEquals(
            redacted.getValue("source_uri"),
            DiagnosticRedactor.redactUri(redacted.getValue("source_uri")),
        )
        assertEquals(
            redacted.getValue("source_uri"),
            DiagnosticRedactor.redactDetails(
                mapOf("sourceUri" to redacted.getValue("source_uri")),
            ).getValue("source_uri"),
        )
        assertEquals("<redacted-path>", redacted.getValue("absolute_path"))
        assertNotEquals(planId, redacted.getValue("plan_id"))
        assertFalse(rendered.contains("student@example.com"))
        assertFalse(redacted.keys.any { key -> "contact" in key })
    }

    @Test
    fun genericDetailKeysFingerprintFreeFormStudyContent() {
        val redacted = DiagnosticRedactor.redactDetails(
            mapOf(
                "reason" to "Algebra finals plan failed for Mike",
                "status" to "Private note about calculus",
                "result" to "Question answer was Quadratic Formula",
                "source" to "My secret annex",
                "task" to "Review Biology chapter seven",
            ),
        )
        val rendered = redacted.toString()

        assertTrue(redacted.values.all { value -> value.startsWith("<fingerprint:") })
        assertFalse(rendered.contains("Algebra"))
        assertFalse(rendered.contains("Mike"))
        assertFalse(rendered.contains("calculus"))
        assertFalse(rendered.contains("Quadratic"))
        assertFalse(rendered.contains("Biology"))
    }

    @Test
    fun narrowlyTypedMachineDetailsRemainUseful() {
        val redacted = DiagnosticRedactor.redactDetails(
            mapOf(
                "version" to "1.2.3-beta01",
                "buildType" to "debug",
                "count" to "42",
                "enabled" to "true",
                "locale" to "pt-BR",
                "timezone" to "America/Sao_Paulo",
            ),
        )

        assertEquals("1.2.3-beta01", redacted.getValue("version"))
        assertEquals("debug", redacted.getValue("build_type"))
        assertEquals("42", redacted.getValue("count"))
        assertEquals("true", redacted.getValue("enabled"))
        assertEquals("pt-BR", redacted.getValue("locale"))
        assertEquals("America/Sao_Paulo", redacted.getValue("timezone"))
    }

    @Test
    fun eventJsonContainsOnlySanitizedValues() {
        val event = DiagnosticEvent(
            sessionId = "debug session/unsafe",
            sequence = 3L,
            timestampEpochMillis = 4L,
            area = DiagnosticArea.BACKUP_RESTORE,
            severity = DiagnosticSeverity.ERROR,
            action = "Export content://provider/private?token=secret-token",
            outcome = DiagnosticOutcome.FAILED,
            correlationId = "7d9b66ba-199d-4c38-980d-5cc0e3b9bc70",
            safeDetails = mapOf(
                "Authorization" to "Bearer abcdefghijklmnop",
                "note" to "Private study note",
            ),
            exception = DiagnosticRedactor.summarizeThrowable(
                IllegalStateException("Private path /data/user/0/com.example/private"),
            ),
        )

        val json = event.toJsonLine()

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"authorization\":\"<redacted>\""))
        assertTrue(json.contains("\"note\":\"<redacted-content>\""))
        assertFalse(json.contains("secret-token"))
        assertFalse(json.contains("Private study note"))
        assertFalse(json.contains("/data/user"))
        assertFalse(json.contains("debug session/unsafe"))
    }

    @Test
    fun forgedExceptionSummariesCannotBypassSanitization() {
        val event = DiagnosticEvent(
            sessionId = "session",
            sequence = 1L,
            timestampEpochMillis = 2L,
            area = DiagnosticArea.VALIDATION,
            severity = DiagnosticSeverity.ERROR,
            action = "validation-failed",
            outcome = DiagnosticOutcome.FAILED,
            exception = DiagnosticExceptionSummary(
                type = "Private Algebra Plan Exception",
                fingerprint = "secret-token-for-mike",
            ),
        )

        val json = event.toJsonLine()

        assertFalse(json.contains("Private Algebra"))
        assertFalse(json.contains("secret-token"))
        assertFalse(json.contains("mike"))
        assertTrue(json.contains("\"type\":\"Exception-"))
        assertTrue(Regex("\\\"fingerprint\\\":\\\"[0-9a-f]{16}\\\"").containsMatchIn(json))
    }

    @Test
    fun dynamicKeysAndNonMachineActionsAreFingerprinted() {
        val redacted = DiagnosticRedactor.redactDetails(
            mapOf("Algebra finals private key" to "harmless"),
        )
        val event = DiagnosticEvent(
            sessionId = "session",
            sequence = 1L,
            timestampEpochMillis = 2L,
            area = DiagnosticArea.STUDY_PLANS,
            severity = DiagnosticSeverity.INFO,
            action = "Open Algebra finals plan",
            outcome = DiagnosticOutcome.STARTED,
        )

        assertTrue(redacted.keys.single().startsWith("detail_"))
        assertTrue(redacted.values.single().startsWith("<fingerprint:"))
        assertFalse(redacted.keys.single().contains("algebra"))
        assertTrue(event.toJsonLine().contains("\"action\":\"action-"))
        assertFalse(event.toJsonLine().contains("Algebra"))
    }

    @Test
    fun throwableSummaryUsesSafeTypeAndFingerprintWithoutMessage() {
        val summary = DiagnosticRedactor.summarizeThrowable(
            IllegalArgumentException("token=secret-token at /data/user/private"),
        )

        assertEquals("IllegalArgumentException", summary.type)
        assertEquals(16, summary.fingerprint.length)
        assertFalse(summary.fingerprint.contains("secret-token"))
    }
}
