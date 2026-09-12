package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPlatformReliabilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StudyFlowTvAttentionReliabilityEvidenceTest {
    @Test
    fun tvReliabilityProjectionStaysForegroundOnlyAndReadOnly() {
        val snapshot = StudyFlowTvAttentionDeliveryRouterBridge.reliabilityProjection(
            ledger = StudyFlowAttentionLedgerSnapshot(),
            nowEpochMillis = 5_000L,
        )
        val platform = snapshot.platforms.single()
        assertEquals(StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY, platform.status)
        assertFalse(platform.truthfulBackgroundDelivery)
    }
}
