package com.mikeyphw.studyflow.reminders

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionRoutingContext
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor

private const val AndroidAttentionDeviceId = "android-local"

object AndroidStudyFlowAttentionRouterBridge {
    val localDeviceId: StudyFlowDeviceId = StudyFlowDeviceId(AndroidAttentionDeviceId)

    fun localCapability(
        context: Context,
        coordinator: StudyReminderDeliveryCoordinator,
        nowEpochMillis: Long,
    ): StudyFlowAttentionDeviceCapability {
        val capability = coordinator.platformCapability()
        return StudyFlowAttentionDeviceCapability(
            peer = StudyFlowLinkPeerDescriptor(
                deviceId = localDeviceId,
                displayName = context.applicationInfo.loadLabel(context.packageManager).toString(),
                clientKind = StudyFlowLinkClientKind.ANDROID,
                capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.ANDROID),
            ),
            availability = StudyFlowAttentionDeviceAvailability.ONLINE,
            deliverySurfaces = listOf(
                StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
                StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            ),
            notificationPermissionGranted = capability.notificationsAllowed,
            primaryUserDevice = true,
            designatedNotifier = true,
            lastSeenAtEpochMillis = nowEpochMillis,
        )
    }

    fun defaultRoutingContext(nowEpochMillis: Long): StudyFlowAttentionRoutingContext =
        StudyFlowAttentionRoutingContext(
            localDeviceId = localDeviceId,
            primaryDeviceId = localDeviceId,
            designatedNotifierDeviceIds = listOf(localDeviceId),
            staleAfterMillis = 10 * 60_000L,
            allowTvForegroundOwners = true,
        )

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}
