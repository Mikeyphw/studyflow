package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTimelineTest {
    @Test
    fun `reader parses escaped redacted recorder events newest first`() {
        val first = DiagnosticEvent(
            sessionId = "session-a",
            sequence = 1,
            timestampEpochMillis = 100,
            area = DiagnosticArea.DATABASE,
            severity = DiagnosticSeverity.INFO,
            action = "database-open",
            outcome = DiagnosticOutcome.SUCCEEDED,
            safeDetails = mapOf("status" to "ready\nnow"),
        ).toJsonLine()
        val second = DiagnosticEvent(
            sessionId = "session-a",
            sequence = 2,
            timestampEpochMillis = 200,
            area = DiagnosticArea.REMINDERS,
            severity = DiagnosticSeverity.ERROR,
            action = "reminder-reconcile",
            outcome = DiagnosticOutcome.FAILED,
            exception = DiagnosticExceptionSummary("IllegalStateException", "0123456789abcdef"),
        ).toJsonLine()

        val snapshot = DiagnosticTimelineReader.read("$first\n$second\n")

        assertEquals(2, snapshot.eventCount)
        assertEquals(2L, snapshot.entries.first().sequence)
        assertEquals(DiagnosticArea.REMINDERS, snapshot.entries.first().area)
        assertEquals(1, snapshot.errorCount)
        assertEquals(1, snapshot.failedCount)
        assertEquals(200L, snapshot.latestTimestampEpochMillis)
        assertEquals("<fingerprint:0fb1d0f5e01ebc58>", snapshot.entries.last().safeDetails["status"])
    }

    @Test
    fun `reader bounds entries and counts malformed lines`() {
        val valid = DiagnosticEvent(
            sessionId = "session-a",
            sequence = 3,
            timestampEpochMillis = 300,
            area = DiagnosticArea.VALIDATION,
            severity = DiagnosticSeverity.WARNING,
            action = "validation-warning",
            outcome = DiagnosticOutcome.BLOCKED,
        ).toJsonLine()

        val snapshot = DiagnosticTimelineReader.read("not-json\n$valid\n", maxEntries = 1)

        assertEquals(1, snapshot.eventCount)
        assertEquals(1, snapshot.malformedLineCount)
        assertEquals(1, snapshot.warningCount)
    }

    @Test
    fun `reader re-sanitizes untrusted timeline fields and rejects pathological nesting`() {
        val malicious = """
            {"schemaVersion":1,"sequence":9,"timestampEpochMillis":10,"area":"VALIDATION","severity":"INFO",
            "action":"safe-action","outcome":"SUCCEEDED","correlationId":"private-plan-id",
            "safeDetails":{"reason":"Private calculus note","plan_id":"plan-secret"}}
        """.trimIndent().replace("\n", "")
        val deeplyNested = buildString {
            append("{\"sequence\":1,\"timestampEpochMillis\":1,")
            append("\"area\":\"VALIDATION\",\"severity\":\"INFO\",")
            append("\"action\":\"safe-action\",\"outcome\":\"SUCCEEDED\",")
            append("\"ignored\":")
            repeat(12) { append('{').append("\"a\":") }
            append("null")
            repeat(12) { append('}') }
            append('}')
        }

        val snapshot = DiagnosticTimelineReader.read("$malicious\n$deeplyNested\n")

        assertEquals(1, snapshot.eventCount)
        assertEquals(1, snapshot.malformedLineCount)
        val event = snapshot.entries.single()
        assertFalse(event.safeDetails.values.any { value -> value.contains("calculus", ignoreCase = true) })
        assertFalse(event.safeDetails.values.any { value -> value.contains("plan-secret") })
        assertFalse(event.correlationFingerprint.orEmpty().contains("private-plan-id"))
    }

    @Test
    fun `shell policy reports degraded and never claims automatic upload`() {
        val store = object : DiagnosticStore {
            override val enabled = true
            override val droppedEventCount = 4L
            override val writerFailureFingerprint: String? = null
            override fun record(event: DiagnosticEvent) = Unit
            override fun copySanitizedTimeline(maxChars: Int) = ""
            override fun clear() = true
            override fun exportSupportBundle(
                destinationZip: java.io.File,
                metadata: Map<String, String>,
            ): java.io.File = destinationZip
        }
        val timeline = DiagnosticTimelineSnapshot(
            entries = emptyList(),
            malformedLineCount = 0,
            sourceCharacterCount = 0,
        )

        val report = DebugWorkbenchShellPolicy.evaluate(store, timeline)

        assertEquals(DiagnosticRecorderHealth.DEGRADED, report.recorderHealth)
        assertFalse(report.canCopyTimeline)
        assertTrue(report.canClearHistory)
        assertTrue(report.canExportSupportBundle)
        assertTrue(report.toClipboardReport().contains("Automatic upload: false"))
    }
}
