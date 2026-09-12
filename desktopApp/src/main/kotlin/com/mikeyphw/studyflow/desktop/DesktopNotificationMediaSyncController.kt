package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDiagnosticsSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapabilities
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialKind
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamOffer
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationRoute
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationScheduleReducer
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowScheduleReminderChange
import com.mikeyphw.studyflow.core.link.StudyFlowScheduleSyncChangeKind
import com.mikeyphw.studyflow.core.link.StudyFlowScheduleSyncId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class DesktopNotificationMediaSyncController(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
    private val attentionLedger: StudyFlowAttentionLedger = StudyFlowAttentionLedger(),
) {
    private val notifications = StudyFlowNotificationScheduleReducer(localDevice, clock, attentionLedger)

    fun scheduleReminderUpsert(
        changeId: StudyFlowScheduleSyncId,
        workspaceId: StudyFlowWorkspaceId,
        scheduleEntityId: StudyFlowEntityId,
        sequence: Long,
        reminderAtEpochMillis: Long,
    ): StudyFlowScheduleReminderChange = StudyFlowScheduleReminderChange(
        changeId = changeId,
        workspaceId = workspaceId,
        sourceDeviceId = localDevice.deviceId,
        sequence = sequence,
        scheduleEntityId = scheduleEntityId,
        changeKind = StudyFlowScheduleSyncChangeKind.REMINDER_UPSERTED,
        reminderAtEpochMillis = reminderAtEpochMillis,
        occurredAtEpochMillis = clock.nowEpochMillis(),
    )

    fun receiveNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck =
        notifications.apply(delivery)

    fun attentionDiagnostics(): StudyFlowAttentionDiagnosticsSnapshot = attentionLedger.diagnostics()

    fun materialStreamOffer(
        streamId: StudyFlowMaterialStreamId,
        workspaceId: StudyFlowWorkspaceId,
        materialId: StudyFlowMaterialId,
        target: StudyFlowLinkPeerDescriptor,
        title: String,
        mimeType: String,
        totalBytes: Long,
        sha256: String,
    ): StudyFlowMaterialStreamOffer = StudyFlowMaterialStreamOffer(
        streamId = streamId,
        workspaceId = workspaceId,
        materialId = materialId,
        sourceDevice = localDevice,
        targetDeviceId = target.deviceId,
        materialKind = if (mimeType == "application/pdf") StudyFlowMaterialKind.PDF else StudyFlowMaterialKind.OPAQUE_BLOB,
        title = title,
        mimeType = mimeType,
        totalBytes = totalBytes,
        chunkSize = 262_144,
        contentSha256 = sha256,
        requiredCapabilities = listOf(StudyFlowLinkCapabilities.LibraryRead, StudyFlowLinkCapabilities.DocumentPresent),
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun routeFor(target: StudyFlowLinkPeerDescriptor, workspaceId: StudyFlowWorkspaceId): StudyFlowNotificationRoute =
        StudyFlowNotificationRoute(workspaceId = workspaceId, targetDeviceId = target.deviceId)

    companion object {
        fun persistent(
            localDevice: StudyFlowLinkPeerDescriptor,
            clock: StudyFlowPeerClock,
        ): DesktopNotificationMediaSyncController = DesktopNotificationMediaSyncController(
            localDevice = localDevice,
            clock = clock,
            attentionLedger = StudyFlowAttentionLedger(DesktopStudyFlowAttentionLedgerStorage()),
        )
    }
}
