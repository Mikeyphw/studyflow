package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialKind
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamOffer
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvNotificationMediaReceiver
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowTvNotificationMediaReceiverTest {
    @Test
    fun tvReceiverAcceptsReadOnlyMaterialStreamAndRejectsImportOffers() {
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val receiver = StudyFlowTvNotificationMediaReceiver(tv, StudyFlowPeerClock { 2_000L })
        val readOnly = offer(desktop, tv, listOf(StudyFlowLinkCapabilities.LibraryRead, StudyFlowLinkCapabilities.DocumentPresent))
        val writeRequired = offer(desktop, tv, listOf(StudyFlowLinkCapabilities.LibraryImport))
        assertTrue(receiver.acceptMaterial(readOnly))
        assertFalse(receiver.acceptMaterial(writeRequired))
    }

    private fun offer(
        source: StudyFlowLinkPeerDescriptor,
        target: StudyFlowLinkPeerDescriptor,
        capabilities: List<StudyFlowLinkCapability>,
    ) = StudyFlowMaterialStreamOffer(
        streamId = StudyFlowMaterialStreamId("stream-${capabilities.first().value.replace('.', '-') }"),
        workspaceId = StudyFlowWorkspaceId("workspace-main"),
        materialId = StudyFlowMaterialId("material-1"),
        sourceDevice = source,
        targetDeviceId = target.deviceId,
        materialKind = StudyFlowMaterialKind.PDF,
        title = "Read-only PDF",
        mimeType = "application/pdf",
        totalBytes = 4L,
        chunkSize = 2,
        contentSha256 = "b".repeat(64),
        requiredCapabilities = capabilities,
        createdAtEpochMillis = 1_000L,
    )

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
