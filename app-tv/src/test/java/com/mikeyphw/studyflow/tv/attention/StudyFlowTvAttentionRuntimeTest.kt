package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationKind
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationPayload
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationRoute
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvNotificationMediaReceiver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowTvAttentionRuntimeTest {
    @Test
    fun acceptedRemoteDeliveryCreatesForegroundBannerAndLedgerEntry() {
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV)
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID)
        val ledger = StudyFlowAttentionLedger()
        val runtime = StudyFlowTvAttentionRuntime(
            receiver = StudyFlowTvNotificationMediaReceiver(tv, StudyFlowPeerClock { 2_000L }, ledger),
            attentionLedger = ledger,
            clock = StudyFlowPeerClock { 3_000L },
        )
        val result = runtime.ingest(delivery(phone, tv, allowTv = true))
        assertEquals(StudyFlowNotificationDeliveryStatus.ACCEPTED, result.ack.status)
        assertTrue(result.foregroundBannerShown)
        assertTrue(runtime.snapshot().hasForegroundBanner)
        assertEquals(1, runtime.diagnostics().attentionLedger.entryCount)
    }

    @Test
    fun deliveriesThatDisallowTvSurfacesAreAcknowledgedWithoutBanner() {
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV)
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID)
        val ledger = StudyFlowAttentionLedger()
        val runtime = StudyFlowTvAttentionRuntime(
            receiver = StudyFlowTvNotificationMediaReceiver(tv, StudyFlowPeerClock { 2_000L }, ledger),
            attentionLedger = ledger,
            clock = StudyFlowPeerClock { 3_000L },
        )
        val result = runtime.ingest(delivery(phone, tv, allowTv = false))
        assertEquals(StudyFlowNotificationDeliveryStatus.REJECTED, result.ack.status)
        assertFalse(result.foregroundBannerShown)
        assertFalse(runtime.snapshot().hasForegroundBanner)
    }

    private fun delivery(source: StudyFlowLinkPeerDescriptor, tv: StudyFlowLinkPeerDescriptor, allowTv: Boolean) =
        StudyFlowNotificationDelivery(
            messageId = StudyFlowMessageId("message-1"),
            sourceDeviceId = source.deviceId,
            targetDeviceId = tv.deviceId,
            sequence = 1L,
            sentAtEpochMillis = 1_500L,
            notification = StudyFlowNotificationPayload(
                notificationId = StudyFlowNotificationId("notification-1"),
                kind = StudyFlowNotificationKind.REVIEW_DUE,
                title = "Review ready",
                body = "Constitutional Law is ready on TV.",
                route = StudyFlowNotificationRoute(
                    workspaceId = StudyFlowWorkspaceId("workspace-main"),
                    targetDeviceId = tv.deviceId,
                    allowTvSurfaces = allowTv,
                ),
                createdAtEpochMillis = 1_000L,
                dueAtEpochMillis = 1_500L,
                expiresAtEpochMillis = 10_000L,
            ),
        )

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind) = StudyFlowLinkPeerDescriptor(
        deviceId = StudyFlowDeviceId(id),
        displayName = id,
        clientKind = kind,
        capabilities = StudyFlowClientProfiles.capabilitiesFor(kind),
    )
}
