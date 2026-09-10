package com.mikeyphw.studyflow

import com.mikeyphw.studyflow.core.link.StudyFlowActivityId
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkCapability
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationId
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.link.AndroidStudyFlowNotificationMediaBridge
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyFlowAndroidNotificationMediaBridgeTest {
    @Test
    fun androidBridgeCreatesTargetedReviewNotificationDelivery() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val bridge = AndroidStudyFlowNotificationMediaBridge(phone, StudyFlowPeerClock { 10_000L })
        val notification = bridge.dueReviewNotification(
            notificationId = StudyFlowNotificationId("notification-1"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = desktop,
            activityId = StudyFlowActivityId("activity-review-1"),
            title = "Review due",
            body = "Your review queue is ready.",
        )
        val delivery = bridge.deliverNotification(StudyFlowMessageId("notification-message-1"), desktop, 1, notification)
        assertEquals(phone.deviceId, delivery.sourceDeviceId)
        assertEquals(desktop.deviceId, delivery.targetDeviceId)
        assertEquals(desktop.deviceId, notification.route.targetDeviceId)
    }

    @Test
    fun androidBridgeReducerAcknowledgesIncomingNotification() {
        val phone = descriptor("phone", StudyFlowLinkClientKind.ANDROID, StudyFlowClientProfiles.AndroidPhone)
        val desktop = descriptor("desktop", StudyFlowLinkClientKind.DESKTOP, StudyFlowClientProfiles.Desktop)
        val sender = AndroidStudyFlowNotificationMediaBridge(phone, StudyFlowPeerClock { 10_000L })
        val receiver = AndroidStudyFlowNotificationMediaBridge(desktop, StudyFlowPeerClock { 10_000L })
        val notification = sender.dueReviewNotification(
            notificationId = StudyFlowNotificationId("notification-2"),
            workspaceId = StudyFlowWorkspaceId("workspace-main"),
            target = desktop,
            activityId = StudyFlowActivityId("activity-review-2"),
            title = "Review due",
            body = "Your review queue is ready.",
        )
        val delivery = sender.deliverNotification(StudyFlowMessageId("notification-message-2"), desktop, 1, notification)
        assertEquals(StudyFlowNotificationDeliveryStatus.ACCEPTED, receiver.receiveNotification(delivery).status)
    }

    private fun descriptor(id: String, kind: StudyFlowLinkClientKind, capabilities: List<StudyFlowLinkCapability>) =
        StudyFlowLinkPeerDescriptor(StudyFlowDeviceId(id), id, kind, capabilities = capabilities)
}
