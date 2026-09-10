package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyFlowAccountBackupValidationTest {
    private val workspace = StudyFlowWorkspaceId("workspace-main")

    @Test
    fun accountHandoffIsTargetGatedTtlBoundAndDuplicateSafe() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val offer = StudyFlowAccountHandoffOffer(
            handoffId = StudyFlowAccountHandoffId("handoff-1"),
            workspaceId = workspace,
            sourceDevice = phone,
            targetDeviceId = desktop.deviceId,
            purpose = StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT,
            manifestSha256 = StudyFlowAccountBackupDefaults.sha256Hex("manifest"),
            requiredCapabilities = StudyFlowAccountBackupDefaults.handoffCapabilitiesFor(StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT),
            createdAtEpochMillis = 1_000L,
        )
        val reducer = StudyFlowAccountHandoffReducer(desktop, StudyFlowPeerClock { 2_000L })
        val delivery = StudyFlowAccountHandoffDelivery(
            messageId = StudyFlowMessageId("message-1"),
            sourceDeviceId = phone.deviceId,
            targetDeviceId = desktop.deviceId,
            sequence = 1L,
            offer = offer,
            sentAtEpochMillis = 1_100L,
        )
        assertTrue(offer.accepts(desktop, nowEpochMillis = 2_000L))
        assertEquals(StudyFlowAccountHandoffStatus.ACCEPTED, reducer.apply(delivery).status)
        assertEquals(StudyFlowAccountHandoffStatus.DUPLICATE, reducer.apply(delivery.copy(messageId = StudyFlowMessageId("message-2"))).status)
        val op = offer.toDomainOperation(StudyFlowEntityId("account-handoff"), StudyFlowEntityRevision(1), 1L, 1L, 2_000L)
        assertEquals(StudyFlowDomainOperationTypes.AccountHandoffAccepted, op.operationType)
    }

    @Test
    fun tvClassPeersCanAuditButCannotRestoreOrTakeAccountOwnership() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val tv = descriptor("tv", StudyFlowLinkClientKind.WEB_TV, StudyFlowClientProfiles.WebTv)
        val restore = StudyFlowAccountHandoffOffer(
            handoffId = StudyFlowAccountHandoffId("handoff-tv-restore"),
            workspaceId = workspace,
            sourceDevice = phone,
            targetDeviceId = tv.deviceId,
            purpose = StudyFlowAccountHandoffPurpose.BACKUP_RESTORE,
            requiredCapabilities = listOf(StudyFlowLinkCapabilities.WorkspaceSync),
            createdAtEpochMillis = 1_000L,
        )
        val audit = restore.copy(
            handoffId = StudyFlowAccountHandoffId("handoff-tv-audit"),
            purpose = StudyFlowAccountHandoffPurpose.AUDIT,
        )
        assertFalse(restore.accepts(tv, nowEpochMillis = 2_000L))
        assertTrue(audit.accepts(tv, nowEpochMillis = 2_000L))
    }

    @Test
    fun backupRestorePlanRejectsTvWriteRestoreButAllowsDesktopRestore() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val tv = descriptor("tv", StudyFlowLinkClientKind.ANDROID_TV, StudyFlowClientProfiles.AndroidTv)
        val bundle = StudyFlowBackupBundleManifest(
            bundleId = StudyFlowBackupBundleId("bundle-main"),
            workspaceId = workspace,
            sourceDevice = phone,
            kind = StudyFlowBackupBundleKind.FULL_WORKSPACE,
            totalBytes = 4096L,
            itemCount = 12,
            contentSha256 = StudyFlowAccountBackupDefaults.sha256Hex("backup"),
            encryptionMode = "local-passphrase-v1",
            createdAtEpochMillis = 1_000L,
        )
        val desktopPlan = StudyFlowBackupRestorePlan(
            restoreId = StudyFlowBackupRestoreId("restore-desktop"),
            bundle = bundle,
            targetDeviceId = desktop.deviceId,
            mode = StudyFlowBackupRestoreMode.RESTORE,
            sequence = 1L,
            createdAtEpochMillis = 1_100L,
        )
        val tvPlan = desktopPlan.copy(
            restoreId = StudyFlowBackupRestoreId("restore-tv"),
            targetDeviceId = tv.deviceId,
        )
        assertTrue(desktopPlan.accepts(desktop, nowEpochMillis = 2_000L))
        assertFalse(tvPlan.accepts(tv, nowEpochMillis = 2_000L))
        val reducer = StudyFlowBackupRestoreReducer(desktop, StudyFlowPeerClock { 2_000L })
        assertEquals(StudyFlowBackupRestoreStatus.ACCEPTED, reducer.apply(desktopPlan).status)
    }

    @Test
    fun releaseGateConsolidatesTheFullSflpValidationChain() {
        val plan = StudyFlowSflp23ReleaseGate.defaultFinalPlan(createdAtEpochMillis = 3_000L)
        assertTrue(plan.containsCompleteSflpChain())
        assertTrue("verifySflp22Sflp23AccountBackupValidation" in plan.requiredTaskNames())
        assertTrue(":app:testReleaseUnitTest" in plan.requiredTaskNames())
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
