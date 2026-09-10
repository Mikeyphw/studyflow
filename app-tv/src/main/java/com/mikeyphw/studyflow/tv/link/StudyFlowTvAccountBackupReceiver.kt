package com.mikeyphw.studyflow.tv.link

import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowAccountHandoffReducer
import com.mikeyphw.studyflow.core.link.StudyFlowBackupBundleManifest
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestoreMode
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestorePlan
import com.mikeyphw.studyflow.core.link.StudyFlowBackupRestoreReducer
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowSflp23ReleaseGate

class StudyFlowTvAccountBackupReceiver(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
) {
    private val handoffs = StudyFlowAccountHandoffReducer(localDevice, clock)
    private val restores = StudyFlowBackupRestoreReducer(localDevice, clock)

    fun canPreviewBackup(manifest: StudyFlowBackupBundleManifest): Boolean =
        manifest.canBeRestoredBy(localDevice, StudyFlowBackupRestoreMode.VERIFY_ONLY, clock.nowEpochMillis())

    fun receiveHandoff(delivery: StudyFlowAccountHandoffDelivery) = handoffs.apply(delivery)

    fun receiveRestorePlan(plan: StudyFlowBackupRestorePlan) = restores.apply(plan)

    fun finalValidationTaskNames(): List<String> =
        StudyFlowSflp23ReleaseGate.defaultFinalPlan(createdAtEpochMillis = clock.nowEpochMillis()).requiredTaskNames()
}
