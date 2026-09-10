package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowLiveProjectionBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowAndroidLiveProjectionBridgeTest {
    @Test
    fun androidCreatesLiveSessionAndProjectionOfferWithoutBecomingHub() {
        val android = StudyFlowLinkPeerDescriptor(
            StudyFlowDeviceId("android-live"),
            "Android",
            StudyFlowLinkClientKind.ANDROID,
            capabilities = StudyFlowClientProfiles.AndroidPhone,
        )
        val bridge = AndroidStudyFlowLiveProjectionBridge(android, StudyFlowPeerClock { 7_000L })
        val snapshot = bridge.liveSessionSnapshot(
            StudyFlowWorkspaceId("workspace-live"),
            StudyFlowLiveSessionId("session-live"),
            StudyFlowActivityId("activity-live"),
            "question-1",
            "fence-android",
        )
        assertEquals(StudyFlowLiveSessionState.RUNNING, snapshot.state)
        assertEquals(android.deviceId, snapshot.activeExecutorDeviceId)

        val offer = bridge.projectionOffer(
            StudyFlowWorkspaceId("workspace-live"),
            StudyFlowProjectionId("projection-android"),
            targetDeviceId = null,
            entityId = StudyFlowEntityId("session-live"),
            title = "Live session",
            contentHash = "b".repeat(64),
        )
        assertTrue(offer.targetDeviceId == null)
        assertEquals(android.deviceId, offer.sourceDevice.deviceId)
    }
}
