package com.mikeyphw.studyflow.core.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowClientProfilesTest {
    @Test
    fun webAndroidTvAndDesktopArePeersNotHubSpokes() {
        val web = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.WEB)
        val webTv = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.WEB_TV)
        val androidTv = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID_TV)
        val desktop = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP)

        assertTrue(web.contains(StudyFlowLinkCapabilities.WorkspaceSync))
        assertTrue(desktop.contains(StudyFlowLinkCapabilities.WorkspaceSync))
        assertTrue(androidTv.contains(StudyFlowLinkCapabilities.WorkspaceSync))
        assertTrue(webTv.contains(StudyFlowLinkCapabilities.WorkspaceSync))
        assertTrue(androidTv.contains(StudyFlowLinkCapabilities.DevicePresentationSink))
        assertTrue(webTv.contains(StudyFlowLinkCapabilities.DevicePresentationSink))
        assertFalse(androidTv.contains(StudyFlowLinkCapabilities.PlanWrite))
        assertFalse(webTv.contains(StudyFlowLinkCapabilities.PlanWrite))
        assertTrue(web.contains(StudyFlowLinkCapabilities.PlanWrite))
    }
}
