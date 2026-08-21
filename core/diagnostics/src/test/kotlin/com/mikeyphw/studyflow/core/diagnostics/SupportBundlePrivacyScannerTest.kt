package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportBundlePrivacyScannerTest {
    @Test
    fun rawCredentialsAndIdentifiersFailClosed() {
        val scan = SupportBundlePrivacyScanner.sanitize(
            "environment.json",
            "{\"Authorization\":\"Bearer abcdefghijklmnop\",\"email\":\"private@example.com\"}\n"
                .toByteArray(),
        )
        assertFalse(scan.passed)
        assertTrue(scan.privacyViolationCount > 0)
    }

    @Test
    fun malformedTimelineSchemaFailsClosed() {
        val scan = SupportBundlePrivacyScanner.sanitize(
            "events/current.jsonl",
            "{not-json}\n".toByteArray(),
        )
        assertFalse(scan.passed)
        assertEquals(1, scan.schemaViolationCount)
    }

    @Test
    fun canonicalSanitizedTimelinePassesOutboundGate() {
        val event = DiagnosticEvent(
            sessionId = "privacy-scan-test",
            sequence = 1L,
            timestampEpochMillis = 1_000L,
            area = DiagnosticArea.SEARCH_RECOMMENDATIONS,
            severity = DiagnosticSeverity.INFO,
            action = "query",
            outcome = DiagnosticOutcome.SUCCEEDED,
            safeDetails = mapOf("query" to "private study words"),
        ).sanitized()
        val scan = SupportBundlePrivacyScanner.sanitize(
            "events/current.jsonl",
            (event.toJsonLine() + "\n").toByteArray(),
        )
        assertTrue(scan.passed)
        assertEquals(1, scan.timelineLineCount)
        assertFalse(scan.sanitizedBytes.toString(Charsets.UTF_8).contains("private study words"))
    }

    @Test
    fun redactionReportMustProveZeroViolationsAndNoUpload() {
        assertTrue(
            SupportBundlePrivacyScanner.redactionReportIsValid(
                "status=PASS\nprivacyViolations=0\nschemaViolations=0\nautomaticUpload=false\n",
            ),
        )
        assertFalse(
            SupportBundlePrivacyScanner.redactionReportIsValid(
                "status=PASS\nprivacyViolations=1\nschemaViolations=0\nautomaticUpload=false\n",
            ),
        )
    }
}
