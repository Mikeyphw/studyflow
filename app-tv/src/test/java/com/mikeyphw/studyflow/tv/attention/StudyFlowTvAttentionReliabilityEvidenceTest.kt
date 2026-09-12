package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPlatformReliabilityStatus
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StudyFlowTvAttentionReliabilityEvidenceTest {
    @Test
    fun tvReliabilityProjectionStaysForegroundOnlyAndReadOnly() {
        val tv = StudyFlowLinkPeerDescriptor(
            StudyFlowDeviceId("tv-test"),
            "TV",
            StudyFlowLinkClientKind.ANDROID_TV,
            capabilities = emptyList(),
        )
        val snapshot = StudyFlowTvAttentionDeliveryRouterBridge.reliabilityProjection(
            ledger = StudyFlowAttentionLedgerSnapshot(),
            localPeer = tv,
            nowEpochMillis = 5_000L,
        )
        val platform = snapshot.platforms.single()
        assertEquals(StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY, platform.status)
        assertFalse(platform.truthfulBackgroundDelivery)
    }
}
