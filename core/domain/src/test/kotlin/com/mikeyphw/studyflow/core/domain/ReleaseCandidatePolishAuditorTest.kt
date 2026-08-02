package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCandidatePolishAuditorTest {
    @Test
    fun releaseCandidatePolishCoversEveryPromisedAreaWhenEvidenceIsPresent() {
        val report = ReleaseCandidatePolishAuditor.evaluate(
            ReleaseCandidatePolishEvidence.verifiedForReleaseCandidateDocs(),
        )

        assertEquals(ReleaseCandidatePolishArea.entries.size, report.totalCount)
        assertEquals(ReleaseCandidatePolishArea.entries.toSet(), report.findings.map { it.area }.toSet())
        assertEquals(0, report.blockingCount)
        assertEquals(100, report.readyPercent)
        assertTrue(report.releaseReady)
    }

    @Test
    fun releaseCandidatePolishBlocksWhenEvidenceIsMissing() {
        val report = ReleaseCandidatePolishAuditor.evaluate()

        assertEquals(ReleaseCandidatePolishArea.entries.size, report.blockingCount)
        assertEquals(0, report.readyPercent)
        assertFalse(report.releaseReady)
    }

    @Test
    fun releaseCandidatePolishBlocksPartialEvidence() {
        val report = ReleaseCandidatePolishAuditor.evaluate(
            ReleaseCandidatePolishEvidence(settingsPolishVerified = true),
        )

        assertEquals(1, report.readyCount)
        assertEquals(ReleaseCandidatePolishArea.entries.size - 1, report.blockingCount)
        assertFalse(report.releaseReady)
    }
}
