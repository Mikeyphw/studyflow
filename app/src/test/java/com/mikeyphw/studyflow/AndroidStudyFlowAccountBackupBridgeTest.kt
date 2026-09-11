package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowAccountBackupBridge
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidStudyFlowAccountBackupBridgeTest {
    @Test
    fun androidBridgeCreatesDeviceReplacementOffer() {
        val local = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val target = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val bridge = AndroidStudyFlowAccountBackupBridge(local, StudyFlowPeerClock { 1_000L })
        val offer = bridge.createDeviceReplacementHandoff(
            StudyFlowAccountHandoffId("handoff-android"),
            StudyFlowWorkspaceId("workspace-main"),
            target,
            "manifest",
        )
        assertTrue(offer.accepts(target, nowEpochMillis = 1_001L))
    }

    private fun descriptor(
        id: String,
        kind: StudyFlowLinkClientKind,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
