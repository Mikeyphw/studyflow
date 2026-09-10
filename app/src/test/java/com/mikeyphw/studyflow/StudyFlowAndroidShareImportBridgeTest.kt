package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportId
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportStatus
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowShareImportBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyFlowAndroidShareImportBridgeTest {
    @Test
    fun androidBridgeCreatesAndReceivesClipboardImportDelivery() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val sender = AndroidStudyFlowShareImportBridge(phone, StudyFlowPeerClock { 10_000L })
        val receiver = AndroidStudyFlowShareImportBridge(desktop, StudyFlowPeerClock { 10_000L })
        val offer = sender.clipboardTextOffer(
            importId = StudyFlowShareImportId("android-import-1"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = desktop,
            title = "Clipboard text",
            text = "Paste this into StudyFlow",
        )
        val delivery = sender.deliverImport(StudyFlowMessageId("android-import-message-1"), desktop, 1L, offer)
        assertEquals(phone.deviceId, delivery.sourceDeviceId)
        assertEquals(StudyFlowShareImportStatus.ACCEPTED, receiver.receiveImport(delivery).status)
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
