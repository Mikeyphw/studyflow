package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLiveSessionProjectionControllerTest {
    @Test
    fun desktopCanOwnLiveSessionAndProjectToAnySink() {
        val desktop = StudyFlowLinkPeerDescriptor(
            StudyFlowDeviceId("desktop-live"),
            "Desktop",
            StudyFlowLinkClientKind.DESKTOP,
            capabilities = StudyFlowClientProfiles.Desktop,
        )
        val controller = DesktopLiveSessionProjectionController(desktop, StudyFlowPeerClock { 8_000L })
        val session = controller.createLiveSnapshot(
            StudyFlowWorkspaceId("workspace-live"),
            StudyFlowLiveSessionId("session-live"),
            StudyFlowActivityId("activity-live"),
            "fence-desktop",
        )
        assertEquals(StudyFlowLiveSessionState.RUNNING, session.state)
        assertEquals(desktop.deviceId, session.activeExecutorDeviceId)
        assertTrue(controller.controlLiveSession(session).snapshot().participants.isNotEmpty())

        val offer = controller.createProjectionOffer(
            StudyFlowWorkspaceId("workspace-live"),
            StudyFlowProjectionId("projection-desktop"),
            targetDeviceId = null,
            entityId = StudyFlowEntityId("doc-1"),
            title = "Document",
            contentHash = "c".repeat(64),
        )
        assertTrue(offer.targetDeviceId == null)
        assertEquals(desktop.deviceId, offer.sourceDevice.deviceId)
    }
}
