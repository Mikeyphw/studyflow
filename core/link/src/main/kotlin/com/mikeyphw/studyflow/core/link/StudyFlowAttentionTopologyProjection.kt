package com.mikeyphw.studyflow.core.link

/** Canonical plan -> SFLP workspace mapping used by Attention and peer presence. */
object StudyFlowAttentionWorkspace {
    fun forPlanId(planId: String): StudyFlowWorkspaceId {
        require(planId.isNotBlank()) { "Plan ID must not be blank" }
        val readable = "plan-$planId"
        return runCatching { StudyFlowWorkspaceId(readable) }.getOrElse {
            StudyFlowWorkspaceId(StudyFlowAttentionOccurrenceId.deterministic("workspace", planId).value)
        }
    }
}

/**
 * Converts the trusted SFLP peer/presence topology into ATT07 delivery candidates.
 * This intentionally stays conservative: Web/TV peers only advertise foreground
 * surfaces when their ephemeral presence is fresh, while Android/Desktop peers may
 * advertise durable native surfaces from trusted recent identity evidence.
 */
object StudyFlowAttentionTopologyProjector {
    fun project(
        localPeer: StudyFlowLinkPeerDescriptor,
        localSurfaces: List<StudyFlowAttentionDeliverySurface>,
        localAvailability: StudyFlowAttentionDeviceAvailability,
        localNotificationPermissionGranted: Boolean,
        trustedPeers: List<StudyFlowTrustedPeerRecord>,
        presenceViews: List<StudyFlowPresenceView>,
        nowEpochMillis: Long,
        localPrimaryUserDevice: Boolean = localPeer.clientKind == StudyFlowLinkClientKind.ANDROID,
        localDesignatedNotifier: Boolean = localPeer.clientKind in nativeNotifierKinds,
    ): List<StudyFlowAttentionDeviceCapability> {
        require(nowEpochMillis >= 0L) { "Projection time must not be negative" }
        val result = linkedMapOf<StudyFlowDeviceId, StudyFlowAttentionDeviceCapability>()
        result[localPeer.deviceId] = StudyFlowAttentionDeviceCapability(
            peer = localPeer,
            availability = localAvailability,
            deliverySurfaces = localSurfaces.distinct(),
            foregroundActive = localAvailability == StudyFlowAttentionDeviceAvailability.FOREGROUND,
            notificationPermissionGranted = localNotificationPermissionGranted,
            primaryUserDevice = localPrimaryUserDevice,
            designatedNotifier = localDesignatedNotifier,
            lastSeenAtEpochMillis = nowEpochMillis,
        )

        val newestPresence = presenceViews
            .filter { it.sourceDevice.deviceId != localPeer.deviceId }
            .groupBy { it.sourceDevice.deviceId }
            .mapValues { (_, values) -> values.maxByOrNull(StudyFlowPresenceView::observedAtEpochMillis) }

        trustedPeers.asSequence()
            .filter { it.state == StudyFlowTrustState.TRUSTED }
            .filter { it.peer.deviceId != localPeer.deviceId }
            .sortedBy { it.peer.deviceId.value }
            .forEach { trusted ->
                val presence = newestPresence[trusted.peer.deviceId]
                val availability = remoteAvailability(trusted.peer.clientKind, presence)
                val foreground = availability == StudyFlowAttentionDeviceAvailability.FOREGROUND
                result[trusted.peer.deviceId] = StudyFlowAttentionDeviceCapability(
                    peer = trusted.peer,
                    availability = availability,
                    deliverySurfaces = remoteSurfaces(trusted.peer.clientKind, foreground),
                    foregroundActive = foreground,
                    notificationPermissionGranted = trusted.peer.clientKind in nativeNotifierKinds,
                    primaryUserDevice = trusted.peer.clientKind == StudyFlowLinkClientKind.ANDROID,
                    designatedNotifier = trusted.peer.clientKind in nativeNotifierKinds,
                    lastSeenAtEpochMillis = presence?.observedAtEpochMillis
                        ?: trusted.lastSeenAtEpochMillis
                        ?: trusted.updatedAtEpochMillis,
                )
            }
        return result.values.toList()
    }

    fun routingContext(
        localDeviceId: StudyFlowDeviceId,
        devices: List<StudyFlowAttentionDeviceCapability>,
        staleAfterMillis: Long,
        allowTvForegroundOwners: Boolean = true,
    ): StudyFlowAttentionRoutingContext {
        val foreground = devices
            .filter { it.foregroundActive && it.availability == StudyFlowAttentionDeviceAvailability.FOREGROUND }
            .maxByOrNull { it.lastSeenAtEpochMillis ?: 0L }
            ?.deviceId
        val primary = devices.firstOrNull { it.primaryUserDevice }?.deviceId ?: localDeviceId
        val designated = devices.filter { it.designatedNotifier }.map { it.deviceId }.distinct()
        return StudyFlowAttentionRoutingContext(
            localDeviceId = localDeviceId,
            primaryDeviceId = primary,
            foregroundDeviceId = foreground,
            designatedNotifierDeviceIds = designated,
            staleAfterMillis = staleAfterMillis,
            allowTvForegroundOwners = allowTvForegroundOwners,
        )
    }

    private fun remoteAvailability(
        clientKind: StudyFlowLinkClientKind,
        presence: StudyFlowPresenceView?,
    ): StudyFlowAttentionDeviceAvailability {
        if (presence != null) {
            return when (presence.status) {
                StudyFlowPresenceStatus.ONLINE -> if (clientKind in foregroundOnlyKinds) {
                    StudyFlowAttentionDeviceAvailability.FOREGROUND
                } else {
                    StudyFlowAttentionDeviceAvailability.ONLINE
                }
                StudyFlowPresenceStatus.AWAY -> StudyFlowAttentionDeviceAvailability.AWAY
                StudyFlowPresenceStatus.OFFLINE,
                StudyFlowPresenceStatus.STALE,
                -> StudyFlowAttentionDeviceAvailability.OFFLINE
            }
        }
        return if (clientKind in nativeNotifierKinds) {
            StudyFlowAttentionDeviceAvailability.ONLINE
        } else {
            StudyFlowAttentionDeviceAvailability.OFFLINE
        }
    }

    private fun remoteSurfaces(
        clientKind: StudyFlowLinkClientKind,
        foreground: Boolean,
    ): List<StudyFlowAttentionDeliverySurface> = when (clientKind) {
        StudyFlowLinkClientKind.ANDROID -> listOf(
            StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
            StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
        )
        StudyFlowLinkClientKind.DESKTOP -> listOf(
            StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
            StudyFlowAttentionDeliverySurface.DESKTOP_TRAY,
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
        )
        StudyFlowLinkClientKind.WEB -> if (foreground) {
            listOf(
                StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
            )
        } else listOf(StudyFlowAttentionDeliverySurface.LEDGER_ONLY)
        StudyFlowLinkClientKind.ANDROID_TV,
        StudyFlowLinkClientKind.WEB_TV,
        -> if (foreground) {
            listOf(
                StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
                StudyFlowAttentionDeliverySurface.LEDGER_ONLY,
            )
        } else listOf(StudyFlowAttentionDeliverySurface.LEDGER_ONLY)
    }

    private val nativeNotifierKinds = setOf(
        StudyFlowLinkClientKind.ANDROID,
        StudyFlowLinkClientKind.DESKTOP,
    )
    private val foregroundOnlyKinds = setOf(
        StudyFlowLinkClientKind.WEB,
        StudyFlowLinkClientKind.ANDROID_TV,
        StudyFlowLinkClientKind.WEB_TV,
    )
}
