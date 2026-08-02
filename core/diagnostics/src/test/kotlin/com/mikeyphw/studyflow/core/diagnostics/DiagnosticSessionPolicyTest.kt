package com.mikeyphw.studyflow.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSessionPolicyTest {
    @Test
    fun disabledPolicyNeverRecords() {
        assertFalse(DiagnosticSessionPolicy.disabled().allowsRecording(1L))
    }

    @Test
    fun debugPolicyRemainsEnabled() {
        assertTrue(DiagnosticSessionPolicy.debug("debug-session").allowsRecording(Long.MAX_VALUE))
    }

    @Test
    fun timeLimitedPolicyStopsAtExpiry() {
        val policy = DiagnosticSessionPolicy.timeLimited(
            sessionId = "release-session",
            nowEpochMillis = 100L,
            durationMillis = 50L,
        )

        assertTrue(policy.allowsRecording(149L))
        assertFalse(policy.allowsRecording(150L))
    }
}
