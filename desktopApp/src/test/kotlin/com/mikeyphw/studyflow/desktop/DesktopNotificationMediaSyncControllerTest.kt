package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowScheduleSyncChangeKind
import com.mikeyphw.studyflow.core.link.StudyFlowScheduleSyncId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopNotificationMediaSyncControllerTest {
    @Test
    fun desktopCreatesScheduleReminderChangeAndMaterialOfferWithoutAndroidDependencies() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val controller = DesktopNotificationMediaSyncController(desktop, StudyFlowPeerClock { 42_000L })
        val change = controller.scheduleReminderUpsert(
            changeId = StudyFlowScheduleSyncId("schedule-change-1"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            scheduleEntityId = StudyFlowEntityId("schedule-item-1"),
            sequence = 1,
            reminderAtEpochMillis = 50_000L,
        )
        assertEquals(StudyFlowScheduleSyncChangeKind.REMINDER_UPSERTED, change.changeKind)
        val offer = controller.materialStreamOffer(
            streamId = StudyFlowMaterialStreamId("stream-material-1"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            materialId = StudyFlowMaterialId("material-1"),
            target = tv,
            title = "PDF notes",
            mimeType = "application/pdf",
            totalBytes = 1024L,
            sha256 = "a".repeat(64),
        )
        assertEquals(tv.deviceId, offer.targetDeviceId)
        assertTrue(offer.requiredCapabilities.isNotEmpty())
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
