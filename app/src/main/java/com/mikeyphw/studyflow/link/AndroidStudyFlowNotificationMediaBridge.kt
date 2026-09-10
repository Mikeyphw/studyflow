package com.mikeyphw.studyflow.link

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
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
) {
    private val notifications = StudyFlowNotificationScheduleReducer(localDevice, clock)
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

    fun acceptMaterialStream(offer: StudyFlowMaterialStreamOffer): Boolean = materialStreams.accept(offer)

    fun receiveMaterialChunk(chunk: StudyFlowMaterialChunk): StudyFlowMaterialChunkAck = materialStreams.apply(chunk)

    fun streamIdFor(materialId: String): StudyFlowMaterialStreamId = StudyFlowMaterialStreamId("stream-$materialId")
}
