package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWorkbenchSealTest {
    @Test
    fun `self check history retains newest bounded sanitized terminal runs`() {
        val baseResult = DebugProbeResult(
            id = DebugProbeId.RECORDER_HEALTH,
            status = DebugProbeStatus.PASSED,
            safeFacts = mapOf("reason" to "Private algebra plan"),
        )
        val history = (1L..12L).fold(RuntimeSelfCheckHistory(retainedRunCount = 3)) { current, index ->
            current.record(
                RuntimeSelfCheckRun(
                    startedAtEpochMillis = index,
                    completedAtEpochMillis = index + 1L,
                    attemptedProbeIds = listOf(DebugProbeId.RECORDER_HEALTH),
                    results = mapOf(DebugProbeId.RECORDER_HEALTH to baseResult),
                ),
            )
        }

        assertEquals(3, history.runs.size)
        assertEquals(13L, history.runs.first().completedAtEpochMillis)
        assertFalse(history.toClipboardReport().contains("algebra", ignoreCase = true))
        assertFalse(
            history.runs.first().results.getValue(DebugProbeId.RECORDER_HEALTH)
                .safeFacts.values.joinToString().contains("algebra", ignoreCase = true),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `self check run rejects visible side-effect probes`() {
        RuntimeSelfCheckRun(
            startedAtEpochMillis = 1L,
            completedAtEpochMillis = 2L,
            attemptedProbeIds = listOf(DebugProbeId.REMINDER_RECONCILIATION),
            results = mapOf(
                DebugProbeId.REMINDER_RECONCILIATION to DebugProbeResult(
                    DebugProbeId.REMINDER_RECONCILIATION,
                    DebugProbeStatus.PASSED,
                ),
            ),
        )
    }

    @Test
    fun `self check verdict distinguishes warning blocked failure and invariant failure`() {
        fun run(status: DebugProbeStatus, invariants: List<String> = emptyList()) = RuntimeSelfCheckRun(
            startedAtEpochMillis = 1L,
            completedAtEpochMillis = 2L,
            attemptedProbeIds = listOf(DebugProbeId.RECORDER_HEALTH),
            results = mapOf(
                DebugProbeId.RECORDER_HEALTH to DebugProbeResult(
                    id = DebugProbeId.RECORDER_HEALTH,
                    status = status,
                ),
            ),
            invariantFailures = invariants,
        )

        assertEquals(RuntimeSelfCheckVerdict.PASS, run(DebugProbeStatus.PASSED).verdict)
        assertEquals(RuntimeSelfCheckVerdict.PASS_WITH_WARNINGS, run(DebugProbeStatus.WARNING).verdict)
        assertEquals(RuntimeSelfCheckVerdict.BLOCKED, run(DebugProbeStatus.BLOCKED).verdict)
        assertEquals(RuntimeSelfCheckVerdict.FAIL, run(DebugProbeStatus.FAILED).verdict)
        assertEquals(RuntimeSelfCheckVerdict.CANCELLED, run(DebugProbeStatus.CANCELLED).verdict)
        assertEquals(RuntimeSelfCheckVerdict.FAIL, run(DebugProbeStatus.PASSED, listOf("cross-probe-mismatch")).verdict)
    }

    @Test
    fun `cross probe invariants reject false green behavioral claims`() {
        val results = mapOf(
            DebugProbeId.SEARCH_QUERY to DebugProbeResult(
                id = DebugProbeId.SEARCH_QUERY,
                status = DebugProbeStatus.PASSED,
                safeFacts = mapOf("behavior_verified" to "false"),
            ),
            DebugProbeId.FILE_PROVIDER_CONFIGURATION to DebugProbeResult(
                id = DebugProbeId.FILE_PROVIDER_CONFIGURATION,
                status = DebugProbeStatus.PASSED,
                safeFacts = mapOf("allowed_root" to "true", "private_root_rejected" to "false"),
            ),
        )

        val failures = RuntimeSelfCheckInvariantPolicy.evaluate(results)

        assertTrue("search-canary-not-behavioral" in failures)
        assertTrue("file-provider-policy-mismatch" in failures)
    }

    @Test
    fun `support bundle seal allows only recorder support entries`() {
        val report = SupportBundleSealPolicy.inspect(
            entryNames = listOf(
                "events/retained-000.jsonl",
                "events/current.jsonl",
                "environment.json",
                "manifest.json",
                "redaction-report.txt",
                "checksums.sha256",
            ),
            checkedAtEpochMillis = 10L,
        )

        assertEquals(SupportBundleSealStatus.PASSED, report.status)
        assertTrue(report.passed)
        assertEquals(6, report.entryCount)
    }

    @Test
    fun `support bundle seal rejects traversal and fingerprints the path`() {
        val report = SupportBundleSealPolicy.inspect(
            entryNames = listOf("manifest.json", "../private.db", "checksums.sha256", "redaction-report.txt"),
            checkedAtEpochMillis = 10L,
        )

        assertEquals(SupportBundleSealStatus.FAILED, report.status)
        assertEquals(SupportBundleSealMessage.ENTRY_TRAVERSAL, report.message)
        assertFalse(report.failingEntryFingerprint.orEmpty().contains("private"))
        assertFalse(report.failingEntryFingerprint.orEmpty().contains("/"))
    }

    @Test
    fun `support bundle seal rejects database payloads`() {
        val report = SupportBundleSealPolicy.inspect(
            entryNames = listOf("manifest.json", "checksums.sha256", "redaction-report.txt", "studyflow.db"),
            checkedAtEpochMillis = 10L,
        )

        assertEquals(SupportBundleSealStatus.FAILED, report.status)
        assertEquals(SupportBundleSealMessage.ENTRY_NOT_ALLOWED, report.message)
    }
}
