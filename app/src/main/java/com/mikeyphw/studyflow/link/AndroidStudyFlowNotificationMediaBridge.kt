package com.mikeyphw.studyflow.link

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDiagnosticsSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialChunk
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialChunkAck
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamId
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamOffer
import com.mikeyphw.studyflow.core.link.StudyFlowMaterialStreamReducer
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationKind
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationPayload
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationRoute
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationScheduleReducer
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId

class AndroidStudyFlowNotificationMediaBridge(
    private val localDevice: StudyFlowLinkPeerDescriptor,
    private val clock: StudyFlowPeerClock,
    private val attentionLedger: StudyFlowAttentionLedger = StudyFlowAttentionLedger(),
) {
    private val notifications = StudyFlowNotificationScheduleReducer(localDevice, clock, attentionLedger)
    private val materialStreams = StudyFlowMaterialStreamReducer(localDevice, clock)

    fun dueReviewNotification(
        notificationId: StudyFlowNotificationId,
        workspaceId: StudyFlowWorkspaceId,
        target: StudyFlowLinkPeerDescriptor,
        activityId: StudyFlowActivityId,
        title: String,
        body: String,
    ): StudyFlowNotificationPayload = StudyFlowNotificationPayload(
        notificationId = notificationId,
        kind = StudyFlowNotificationKind.REVIEW_DUE,
        title = title,
        body = body,
        route = StudyFlowNotificationRoute(workspaceId, targetDeviceId = target.deviceId),
        scheduleActivityId = activityId,
        createdAtEpochMillis = clock.nowEpochMillis(),
    )

    fun deliverNotification(
        messageId: StudyFlowMessageId,
        target: StudyFlowLinkPeerDescriptor,
        sequence: Long,
        notification: StudyFlowNotificationPayload,
    ): StudyFlowNotificationDelivery = StudyFlowNotificationDelivery(
        messageId = messageId,
        sourceDeviceId = localDevice.deviceId,
        targetDeviceId = target.deviceId,
        sequence = sequence,
        notification = notification,
        sentAtEpochMillis = clock.nowEpochMillis(),
    )

    fun receiveNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck =
        notifications.apply(delivery)

    fun attentionDiagnostics(): StudyFlowAttentionDiagnosticsSnapshot = attentionLedger.diagnostics()

    fun acceptMaterialStream(offer: StudyFlowMaterialStreamOffer): Boolean = materialStreams.accept(offer)

    fun receiveMaterialChunk(chunk: StudyFlowMaterialChunk): StudyFlowMaterialChunkAck = materialStreams.apply(chunk)

    fun streamIdFor(materialId: String): StudyFlowMaterialStreamId = StudyFlowMaterialStreamId("stream-$materialId")

    companion object {
        fun persistent(
            context: Context,
            localDevice: StudyFlowLinkPeerDescriptor,
            clock: StudyFlowPeerClock,
        ): AndroidStudyFlowNotificationMediaBridge = AndroidStudyFlowNotificationMediaBridge(
            localDevice = localDevice,
            clock = clock,
            attentionLedger = StudyFlowAttentionLedger(AndroidStudyFlowAttentionLedgerStorage(context)),
        )
    }
}
