package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRouter
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerEntry
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceAvailability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeviceCapability
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionRoutingContext
import com.mikeyphw.studyflow.core.link.StudyFlowClientProfiles
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowLinkPeerDescriptor

private const val DesktopAttentionDeviceId = "desktop-local"

object DesktopAttentionDeliveryRouterBridge {
    val localDeviceId: StudyFlowDeviceId = StudyFlowDeviceId(DesktopAttentionDeviceId)

    fun localCapability(
        nativeNotificationsAvailable: Boolean,
        trayAvailable: Boolean,
        nowEpochMillis: Long,
    ): StudyFlowAttentionDeviceCapability = StudyFlowAttentionDeviceCapability(
        peer = StudyFlowLinkPeerDescriptor(
            deviceId = localDeviceId,
            displayName = "StudyFlow Desktop",
            clientKind = StudyFlowLinkClientKind.DESKTOP,
            capabilities = StudyFlowClientProfiles.capabilitiesFor(StudyFlowLinkClientKind.DESKTOP),
        ),
        availability = StudyFlowAttentionDeviceAvailability.FOREGROUND,
        deliverySurfaces = buildList {
            if (nativeNotificationsAvailable) add(StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION)
            if (trayAvailable) add(StudyFlowAttentionDeliverySurface.DESKTOP_TRAY)
            if (isEmpty()) add(StudyFlowAttentionDeliverySurface.LEDGER_ONLY)
        },
        foregroundActive = true,
        notificationPermissionGranted = nativeNotificationsAvailable,
        designatedNotifier = nativeNotificationsAvailable || trayAvailable,
        lastSeenAtEpochMillis = nowEpochMillis,
    )

    fun defaultRoutingContext(nowEpochMillis: Long): StudyFlowAttentionRoutingContext =
        StudyFlowAttentionRoutingContext(
            localDeviceId = localDeviceId,
            primaryDeviceId = localDeviceId,
            designatedNotifierDeviceIds = listOf(localDeviceId),
            staleAfterMillis = 5 * 60_000L,
        )


    fun routeWithPolicy(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute = router().routeWithPolicy(
        occurrence = occurrence,
        devices = devices,
        nowEpochMillis = nowEpochMillis,
        focusPolicy = focusPolicy,
        context = defaultRoutingContext(nowEpochMillis),
        ledgerEntry = ledgerEntry,
        localClientKind = StudyFlowLinkClientKind.DESKTOP,
    )

    fun router(): StudyFlowAttentionDeliveryRouter = StudyFlowAttentionDeliveryRouter()
}
