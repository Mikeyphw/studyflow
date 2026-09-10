package com.mikeyphw.studyflow.tv

import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleId
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleKind
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleManifest
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.tv.link.StudyFlowTvAccountBackupReceiver
import kotlin.test.Test
import kotlin.test.assertTrue

class StudyFlowTvAccountBackupReceiverTest {
    @Test
    fun tvReceiverExposesReleaseTaskNamesAndBackupPreview() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val receiver = StudyFlowTvAccountBackupReceiver(tv, StudyFlowPeerClock { 1_000L })
        val manifest = StudyFlowBackupBundleManifest(
            bundleId = StudyFlowBackupBundleId("bundle-tv"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            sourceDevice = phone,
            kind = StudyFlowBackupBundleKind.FULL_WORKSPACE,
            totalBytes = 1L,
            itemCount = 1,
            contentSha256 = "0".repeat(64),
            encryptionMode = "local-passphrase-v1",
            createdAtEpochMillis = 1L,
            requiredRestoreCapabilities = listOf(com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities.BackupExport),
        )
        assertTrue(receiver.canPreviewBackup(manifest))
        assertTrue("verifySflp22Sflp23AccountBackupValidation" in receiver.finalValidationTaskNames())
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
