package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDefaults
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportId
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportIntent
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportOffer
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportPayloadKind
import com.mikeyphw.studyflow.core.link.StudyFlowShareImportStatus
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvShareImportConflictReceiver
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyFlowTvShareImportConflictReceiverTest {
    @Test
    fun tvAcceptsPreviewImportAndRejectsWriteImport() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val receiver = StudyFlowTvShareImportConflictReceiver(tv, StudyFlowPeerClock { 2_000L })
        assertEquals(StudyFlowShareImportStatus.ACCEPTED, receiver.receiveImport(delivery(phone, tv, StudyFlowShareImportIntent.CLIPBOARD_PREVIEW, 1L)))
        assertEquals(StudyFlowShareImportStatus.REJECTED, receiver.receiveImport(delivery(phone, tv, StudyFlowShareImportIntent.SHARE_IMPORT, 2L)))
    }

    private fun delivery(
        source: StudyFlowLinkPeerDescriptor,
        target: StudyFlowLinkPeerDescriptor,
        intent: StudyFlowShareImportIntent,
        sequence: Long,
    ) = StudyFlowShareImportDelivery(
        messageId = StudyFlowMessageId("tv-share-message-$sequence"),
        sourceDeviceId = source.deviceId,
        targetDeviceId = target.deviceId,
        sequence = sequence,
        offer = StudyFlowShareImportOffer(
            importId = StudyFlowShareImportId("tv-import-$sequence"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            sourceDevice = source,
            targetDeviceId = target.deviceId,
            intent = intent,
            payloadKind = StudyFlowShareImportPayloadKind.PLAIN_TEXT,
            title = "Preview",
            mimeType = "text/plain",
            previewText = "TV preview only",
            contentSha256 = StudyFlowShareImportDefaults.sha256Hex("TV preview only"),
            requiredCapabilities = StudyFlowShareImportDefaults.textCapabilitiesFor(intent),
            createdAtEpochMillis = 1_000L,
        ),
        sentAtEpochMillis = 1_100L,
    )

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
