package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAttentionDeliveryRouterBridgeTest {
    @Test
    fun `travel offline owns the desktop mini surface when native notifications are unavailable`() {
        val now = 10_000L
        val peer = StudyFlowLinkPeerDescriptor(
            deviceId = StudyFlowDeviceId("desktop-real-id"),
            displayName = "Desktop",
            clientKind = StudyFlowLinkClientKind.DESKTOP,
            capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
        )
        val devices = DesktopAttentionDeliveryRouterBridge.topologyCapabilities(
            localPeer = peer,
            nativeNotificationsAvailable = false,
            trayAvailable = true,
            trustedPeers = emptyList(),
            presenceViews = emptyList(),
            nowEpochMillis = now,
        )
        val local = devices.single()
        assertTrue(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE in local.deliverySurfaces)

        val occurrence = StudyFlowAttentionOccurrence(
            occurrenceId = StudyFlowAttentionOccurrenceId("desktop-compact-only"),
            workspaceId = StudyFlowWorkspaceId("plan-main"),
            category = StudyFlowAttentionCategory.PLANNED_STUDY,
            scheduledAtEpochMillis = now,
        )
        val route = DesktopAttentionDeliveryRouterBridge.routeWithPolicy(
            occurrence = occurrence,
            devices = devices,
            localDeviceId = peer.deviceId,
            focusPolicy = StudyFlowAttentionFocusPolicy(profile = StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE),
            nowEpochMillis = now,
        )
        assertTrue(route.deliverable)
        assertTrue(route.compactOnly)
        assertEquals(peer.deviceId, route.owner?.deviceId)
        assertEquals(StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE, route.owner?.surface)
    }
}
