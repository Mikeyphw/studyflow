package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.tv.link.StudyFlowTvSflpPeer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvSflpGeneralizationContractTest {
    @Test
    fun androidTvIsGenericPresentationPeerNotPhoneHub() {
        val capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV)
        assertTrue(StudyFlowTvSflpGeneralizationBuildContract.androidTvGenericSflpPeerIntroduced)
        assertTrue(StudyFlowTvSflpPeer.GenericSflpTvPeerEnabled)
        assertTrue(StudyFlowTvSflpPeer.LegacyPhoneTvTransportRemainsCompatibilityAdapter)
        assertTrue(StudyFlowTvSflpPeer.AndroidTvIsNotPhoneHub)
        assertTrue(StudyFlowTvSflpPeer.PairingWorksWithAndroidDesktopWebAndWebTv)
        assertTrue(capabilities.contains(StudyFlowLinkCapabilities.WorkspaceSync))
        assertTrue(capabilities.contains(StudyFlowLinkCapabilities.DevicePresentationSink))
        assertTrue(capabilities.contains(StudyFlowLinkCapabilities.DpadInput))
        assertFalse(capabilities.contains(StudyFlowLinkCapabilities.PlanWrite))
    }
}
