package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostV2ReleaseSealTest {
    @Test
    fun postV2IntegrationSealRequiresEveryPromisedArea() {
        val report = PostV2IntegrationSeal.evaluate(
            PostV2IntegrationEvidence.verifiedFromRepositoryContracts(),
        )

        assertTrue(report.releaseReady)
        assertEquals(PostV2IntegrationArea.entries.size, report.totalCount)
        assertEquals(100, report.readyPercent)
    }

    @Test
    fun postV2IntegrationSealBlocksWhenAnyWorkspaceContractIsMissing() {
        val report = PostV2IntegrationSeal.evaluate(
            PostV2IntegrationEvidence.verifiedFromRepositoryContracts().copy(
                reminderAndTimelineVerified = false,
            ),
        )

        assertFalse(report.releaseReady)
        assertEquals(1, report.blockingCount)
        assertTrue(report.findings.any { finding ->
            finding.area == PostV2IntegrationArea.REMINDER_AND_TIMELINE && !finding.complete
        })
    }

    @Test
    fun deviceReadinessSealRequiresEveryRealDevicePolishArea() {
        val report = DeviceReadinessPolishSeal.evaluate(
            DeviceReadinessPolishEvidence.verifiedFromDevtoolGate(),
        )

        assertTrue(report.releaseReady)
        assertEquals(DeviceReadinessPolishArea.entries.size, report.totalCount)
        assertEquals(100, report.readyPercent)
    }

    @Test
    fun deviceReadinessSealBlocksWhenWarningBoundaryRegresses() {
        val report = DeviceReadinessPolishSeal.evaluate(
            DeviceReadinessPolishEvidence.verifiedFromDevtoolGate().copy(
                warningBoundaryVerified = false,
            ),
        )

        assertFalse(report.releaseReady)
        assertEquals(1, report.blockingCount)
        assertTrue(report.findings.any { finding ->
            finding.area == DeviceReadinessPolishArea.WARNING_BOUNDARY && !finding.complete
        })
    }
}
