package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowAccountBackupDefaults
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffAck
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffId
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffOffer
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffPurpose
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffReducer
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleId
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleKind
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleManifest
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestoreAck
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestorePlan
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestoreReducer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowSflp23ReleaseGate
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class AndroidStudyFlowAccountBackupBridge(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val handoffs = StudyFlowAccountHandoffReducer(localDevice, clock)
    private val restores = StudyFlowBackupRestoreReducer(localDevice, clock)

    fun createDeviceReplacementHandoff(
        handoffId: StudyFlowAccountHandoffId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        manifestText: String,
    ): StudyFlowAccountHandoffOffer = StudyFlowAccountHandoffOffer(
        handoffId = handoffId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        purpose = StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT,
        manifestSha256 = StudyFlowAccountBackupDefaults.sha256Hex(manifestText),
        requiredCapabilities = StudyFlowAccountBackupDefaults.handoffCapabilitiesFor(
            StudyFlowAccountHandoffPurpose.DEVICE_REPLACEMENT,
        ),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun backupManifest(
        bundleId: StudyFlowBackupBundleId,
        workspaceId: StudyFlowWorkspaceId,
        itemCount: Int,
        contentSummary: String,
    ): StudyFlowBackupBundleManifest = StudyFlowBackupBundleManifest(
        bundleId = bundleId,
        workspaceId = workspaceId,
        sourceDevice = localDevice,
        kind = StudyFlowBackupBundleKind.FULL_WORKSPACE,
        totalBytes = contentSummary.toByteArray().size.toLong(),
        itemCount = itemCount,
        contentSha256 = StudyFlowAccountBackupDefaults.sha256Hex(contentSummary),
        encryptionMode = "local-passphrase-v1",
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun receiveAccountHandoff(
        delivery: StudyFlowAccountHandoffDelivery,
    ): StudyFlowAccountHandoffAck = handoffs.apply(delivery)

    fun receiveRestorePlan(
        plan: StudyFlowBackupRestorePlan,
    ): StudyFlowBackupRestoreAck = restores.apply(plan)

    fun finalReleaseValidationPlan() = StudyFlowSflp23ReleaseGate.defaultFinalPlan(
        createdAtEpochMillis = clock.nowEpochMillis(),
    )
}
