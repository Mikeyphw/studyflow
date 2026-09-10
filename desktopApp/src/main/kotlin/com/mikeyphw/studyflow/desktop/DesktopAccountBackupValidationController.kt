package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAccountBackupDefaults
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffOffer
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffPurpose
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffReducer
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleId
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleKind
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleManifest
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestorePlan
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestoreReducer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowSflp23ReleaseGate
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class DesktopAccountBackupValidationController(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val handoffs = StudyFlowAccountHandoffReducer(localDevice, clock)
    private val restores = StudyFlowBackupRestoreReducer(localDevice, clock)

    fun accountRecoveryOffer(
        handoffId: StudyFlowAccountHandoffId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        manifestText: String,
    ): StudyFlowAccountHandoffOffer = StudyFlowAccountHandoffOffer(
        handoffId = handoffId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        purpose = StudyFlowAccountHandoffPurpose.DESKTOP_RECOVERY,
        manifestSha256 = StudyFlowAccountBackupDefaults.sha256Hex(manifestText),
        requiredCapabilities = StudyFlowAccountBackupDefaults.handoffCapabilitiesFor(StudyFlowAccountHandoffPurpose.DESKTOP_RECOVERY),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun backupBundle(
        bundleId: StudyFlowBackupBundleId,
        workspaceId: StudyFlowWorkspaceId,
        contentSummary: String,
    ): StudyFlowBackupBundleManifest = StudyFlowBackupBundleManifest(
        bundleId = bundleId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        kind = StudyFlowBackupBundleKind.FULL_WORKSPACE,
        totalBytes = contentSummary.toByteArray().size.toLong(),
        itemCount = 1,
        contentSha256 = StudyFlowAccountBackupDefaults.sha256Hex(contentSummary),
        encryptionMode = "desktop-local-vault-v1",
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun receiveHandoff(delivery: StudyFlowAccountHandoffDelivery) = handoffs.apply(delivery)

    fun receiveRestorePlan(plan: StudyFlowBackupRestorePlan) = restores.apply(plan)

    fun finalReleaseValidationPlan() = StudyFlowSflp23ReleaseGate.defaultFinalPlan(createdAtEpochMillis = clock.nowEpochMillis())
}
