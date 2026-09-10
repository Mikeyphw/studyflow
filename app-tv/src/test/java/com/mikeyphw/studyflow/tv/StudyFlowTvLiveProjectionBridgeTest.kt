package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowActivityCursor
import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionCursor
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionId
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionParticipant
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionRole
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowLiveSessionState
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationContentKind
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationFrame
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPresentationProjectionPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowProjectionId
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvLiveProjectionBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvLiveProjectionBridgeTest {
    @Test
    fun tvObservesLiveSessionsAndAcknowledgesProjectionFrames() {
        val tv = peer("tv-sink", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val source = peer("desktop-source", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val bridge = StudyFlowTvLiveProjectionBridge(tv, StudyFlowPeerClock { 9_000L })
        val session = StudyFlowLiveSessionSnapshot(
            sessionId = StudyFlowLiveSessionId("session-live"),
            workspaceId = StudyFlowWorkspaceId("workspace-live"),
            activityId = StudyFlowActivityId("activity-live"),
            state = StudyFlowLiveSessionState.RUNNING,
            activeExecutorDeviceId = source.deviceId,
            fencingToken = "fence-tv",
            sessionEpoch = 1,
            lastSequence = 1,
            cursor = StudyFlowLiveSessionCursor(StudyFlowActivityId("activity-live"), "slide-1"),
            participants = listOf(StudyFlowLiveSessionParticipant(source, StudyFlowLiveSessionRole.EXECUTOR, 1_000L)),
            updatedAtEpochMillis = 2_000L,
        )
        assertTrue(bridge.canObserveLiveSession(session))

        val offer = StudyFlowPresentationProjectionOffer(
            projectionId = StudyFlowProjectionId("projection-tv"),
            workspaceId = StudyFlowWorkspaceId("workspace-live"),
            sourceDevice = source,
            targetDeviceId = tv.deviceId,
            contentKind = StudyFlowPresentationContentKind.DOCUMENT,
            entityId = StudyFlowEntityId("doc-1"),
            title = "Document",
            contentHash = "d".repeat(64),
            requiredCapabilities = StudyFlowPresentationProjectionPolicy.defaultSinkCapabilities(
                StudyFlowPresentationContentKind.DOCUMENT,
            ),
            createdAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 10_000L,
        )
        val frame = StudyFlowPresentationFrame(
            projectionId = StudyFlowProjectionId("projection-tv"),
            frameId = StudyFlowMessageId("frame-tv"),
            workspaceId = StudyFlowWorkspaceId("workspace-live"),
            sourceDeviceId = source.deviceId,
            sequence = 1,
            contentRevision = StudyFlowEntityRevision(1),
            contentHash = "d".repeat(64),
            cursor = StudyFlowActivityCursor("slide-1"),
        )
        assertTrue(bridge.acceptPresentationProjection(offer))
        assertTrue(bridge.applyProjectionFrame(frame))
        assertEquals(1, bridge.acknowledge(StudyFlowProjectionId("projection-tv"))!!.acknowledgedSequence)
    }

    private fun peer(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability>,
    ): StudyFlowLinkPeerDescriptor = StudyFlowLinkPeerDescriptor(
        StudyFlowDeviceId(id),
        id,
        kind,
        capabilities = capabilities,
    )
}
