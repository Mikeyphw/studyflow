package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAccountBackupValidationControllerTest {
    @Test
    fun desktopControllerBuildsCompleteFinalReleasePlan() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val controller = DesktopAccountBackupValidationController(desktop, StudyFlowPeerClock { 1_000L })
        val offer = controller.accountRecoveryOffer(
            StudyFlowAccountHandoffId("handoff-desktop"),
            StudyFlowWorkspaceId("workspace-main"),
            phone,
            "manifest",
        )
        assertTrue(offer.accepts(phone, nowEpochMillis = 1_001L))
        assertTrue(controller.finalReleaseValidationPlan().containsCompleteSflpChain())
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
