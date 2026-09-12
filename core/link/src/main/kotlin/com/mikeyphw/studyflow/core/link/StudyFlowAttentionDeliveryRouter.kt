package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultAttentionRouterStaleAfterMillis = 180_000L
private const val DefaultAttentionRouterMirrorLimit = 2

@Serializable
enum class StudyFlowAttentionDeliverySurface {
    @SerialName("android_alarm") ANDROID_ALARM,
    @SerialName("system_notification") SYSTEM_NOTIFICATION,
    @SerialName("desktop_tray") DESKTOP_TRAY,
    @SerialName("web_push") WEB_PUSH,
    @SerialName("foreground_banner") FOREGROUND_BANNER,
    @SerialName("widget_or_badge") WIDGET_OR_BADGE,
    @SerialName("ledger_only") LEDGER_ONLY,
}

@Serializable
enum class StudyFlowAttentionDeviceAvailability {
    @SerialName("online") ONLINE,
    @SerialName("foreground") FOREGROUND,
    @SerialName("away") AWAY,
    @SerialName("offline") OFFLINE,
}

@Serializable
enum class StudyFlowAttentionDeliveryRole {
    @SerialName("owner") OWNER,
    @SerialName("mirror") MIRROR,
}

@Serializable
enum class StudyFlowAttentionRouteReason {
    @SerialName("explicit_target") EXPLICIT_TARGET,
    @SerialName("local_only") LOCAL_ONLY,
    @SerialName("primary_device") PRIMARY_DEVICE,
    @SerialName("active_foreground") ACTIVE_FOREGROUND,
    @SerialName("phone_desktop_pair") PHONE_DESKTOP_PAIR,
    @SerialName("all_capable") ALL_CAPABLE,
    @SerialName("fallback_best_capable") FALLBACK_BEST_CAPABLE,
    @SerialName("no_capable_device") NO_CAPABLE_DEVICE,
    @SerialName("ledger_already_handled") LEDGER_ALREADY_HANDLED,
}

@Serializable
data class StudyFlowAttentionDeviceCapability(
    val peer: StudyFlowLinkPeerDescriptor,
    val availability: StudyFlowAttentionDeviceAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
    val deliverySurfaces: List<StudyFlowAttentionDeliverySurface>,
    val foregroundActive: Boolean = availability == StudyFlowAttentionDeviceAvailability.FOREGROUND,
    val notificationPermissionGranted: Boolean = true,
    val primaryUserDevice: Boolean = false,
    val designatedNotifier: Boolean = false,
    val batteryConstrained: Boolean = false,
    val lastSeenAtEpochMillis: Long? = null,
    val mutedUntilEpochMillis: Long? = null,
) {
    init {
        require(deliverySurfaces.distinct().size == deliverySurfaces.size) {
            "Attention delivery surfaces must be unique"
        }
        require(deliverySurfaces.isNotEmpty()) { "Attention device must declare at least one surface" }
        require(lastSeenAtEpochMillis == null || lastSeenAtEpochMillis >= 0L) {
            "Last-seen timestamp must not be negative"
        }
        require(mutedUntilEpochMillis == null || mutedUntilEpochMillis >= 0L) {
            "Muted-until timestamp must not be negative"
        }
    }

    val deviceId: StudyFlowDeviceId get() = peer.deviceId
    val clientKind: StudyFlowLinkClientKind get() = peer.clientKind

    fun isMuted(nowEpochMillis: Long): Boolean = mutedUntilEpochMillis?.let { it > nowEpochMillis } == true

    fun isStale(nowEpochMillis: Long, staleAfterMillis: Long): Boolean =
        lastSeenAtEpochMillis?.let { nowEpochMillis - it > staleAfterMillis } == true

    fun bestSurface(
        nowEpochMillis: Long,
        context: StudyFlowAttentionRoutingContext,
    ): StudyFlowAttentionDeliverySurface? {
        if (availability == StudyFlowAttentionDeviceAvailability.OFFLINE) return null
        if (isMuted(nowEpochMillis)) return null
        if (isStale(nowEpochMillis, context.staleAfterMillis)) return null
        if (StudyFlowAttentionDeliverySurface.ANDROID_ALARM in deliverySurfaces) {
            return StudyFlowAttentionDeliverySurface.ANDROID_ALARM
        }
        if (
            StudyFlowAttentionDeliverySurface.WEB_PUSH in deliverySurfaces &&
            notificationPermissionGranted
        ) {
            return StudyFlowAttentionDeliverySurface.WEB_PUSH
        }
        if (
            StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION in deliverySurfaces &&
            notificationPermissionGranted
        ) {
            return StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION
        }
        if (
            StudyFlowAttentionDeliverySurface.DESKTOP_TRAY in deliverySurfaces &&
            foregroundActive
        ) {
            return StudyFlowAttentionDeliverySurface.DESKTOP_TRAY
        }
        if (
            StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER in deliverySurfaces &&
            foregroundActive
        ) {
            return StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER
        }
        if (StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE in deliverySurfaces) {
            return StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE
        }
        return null
    }

    fun canOwn(
        occurrence: StudyFlowAttentionOccurrence,
        nowEpochMillis: Long,
        context: StudyFlowAttentionRoutingContext,
    ): Boolean {
        if (occurrence.isExpired(nowEpochMillis)) return false
        val surface = bestSurface(nowEpochMillis, context) ?: return false
        if (
            !context.allowTvForegroundOwners &&
            clientKind.isTv() &&
            surface == StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER
        ) {
            return false
        }
        return true
    }

    fun score(
        occurrence: StudyFlowAttentionOccurrence,
        nowEpochMillis: Long,
        context: StudyFlowAttentionRoutingContext,
    ): Int {
        val surface = bestSurface(nowEpochMillis, context) ?: return Int.MIN_VALUE
        var score = when (surface) {
            StudyFlowAttentionDeliverySurface.ANDROID_ALARM -> 1000
            StudyFlowAttentionDeliverySurface.WEB_PUSH -> 940
            StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION -> 900
            StudyFlowAttentionDeliverySurface.DESKTOP_TRAY -> 820
            StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER -> 700
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE -> 220
            StudyFlowAttentionDeliverySurface.LEDGER_ONLY -> 0
        }
        score += when (clientKind) {
            StudyFlowLinkClientKind.ANDROID -> 80
            StudyFlowLinkClientKind.DESKTOP -> 70
            StudyFlowLinkClientKind.WEB -> 50
            StudyFlowLinkClientKind.ANDROID_TV -> 20
            StudyFlowLinkClientKind.WEB_TV -> 10
        }
        if (deviceId == context.primaryDeviceId) score += 160
        if (deviceId == context.localDeviceId) score += 25
        if (foregroundActive || availability == StudyFlowAttentionDeviceAvailability.FOREGROUND) score += 90
        if (designatedNotifier || deviceId in context.designatedNotifierDeviceIds) score += 120
        if (deviceId in occurrence.targetDeviceIds) score += 240
        if (batteryConstrained) score -= 80
        score += when (occurrence.priority) {
            StudyFlowAttentionPriority.LOW -> -20
            StudyFlowAttentionPriority.NORMAL -> 0
            StudyFlowAttentionPriority.HIGH -> 40
            StudyFlowAttentionPriority.TIME_SENSITIVE -> 80
        }
        return score
    }
}

@Serializable
data class StudyFlowAttentionRoutingContext(
    val localDeviceId: StudyFlowDeviceId? = null,
    val primaryDeviceId: StudyFlowDeviceId? = null,
    val foregroundDeviceId: StudyFlowDeviceId? = null,
    val designatedNotifierDeviceIds: List<StudyFlowDeviceId> = emptyList(),
    val mutedDeviceIds: List<StudyFlowDeviceId> = emptyList(),
    val maxMirrors: Int = DefaultAttentionRouterMirrorLimit,
    val staleAfterMillis: Long = DefaultAttentionRouterStaleAfterMillis,
    val allowTvForegroundOwners: Boolean = true,
) {
    init {
        require(designatedNotifierDeviceIds.distinct().size == designatedNotifierDeviceIds.size) {
            "Designated notifier IDs must be unique"
        }
        require(mutedDeviceIds.distinct().size == mutedDeviceIds.size) {
            "Muted device IDs must be unique"
        }
        require(maxMirrors in 0..8) { "Attention mirror limit must be between 0 and 8" }
        require(staleAfterMillis > 0L) { "Attention stale threshold must be positive" }
    }
}

@Serializable
data class StudyFlowAttentionDeliveryTarget(
    val deviceId: StudyFlowDeviceId,
    val displayName: String,
    val clientKind: StudyFlowLinkClientKind,
    val role: StudyFlowAttentionDeliveryRole,
    val surface: StudyFlowAttentionDeliverySurface,
    val score: Int,
) {
    init {
        require(displayName.isNotBlank()) { "Attention route display name must not be blank" }
    }
}

@Serializable
data class StudyFlowAttentionRouteDiagnostics(
    val candidateCount: Int,
    val eligibleCount: Int,
    val suppressedDeviceIds: List<StudyFlowDeviceId> = emptyList(),
    val ledgerState: StudyFlowAttentionDeliveryState? = null,
    val policyOutcome: StudyFlowAttentionPolicyOutcome? = null,
    val deferredUntilEpochMillis: Long? = null,
    val explanation: String,
) {
    init {
        require(candidateCount >= 0) { "Candidate count must not be negative" }
        require(eligibleCount >= 0) { "Eligible count must not be negative" }
        require(suppressedDeviceIds.distinct().size == suppressedDeviceIds.size) {
            "Suppressed route devices must be unique"
        }
        require(deferredUntilEpochMillis == null || deferredUntilEpochMillis >= 0L) {
            "Attention route deferral timestamp must not be negative"
        }
        require(explanation.isNotBlank()) { "Route diagnostics explanation must not be blank" }
    }
}

@Serializable
data class StudyFlowAttentionDeliveryRoute(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val workspaceId: StudyFlowWorkspaceId,
    val policy: StudyFlowAttentionDeliveryPolicy,
    val reason: StudyFlowAttentionRouteReason,
    val decidedAtEpochMillis: Long,
    val owner: StudyFlowAttentionDeliveryTarget? = null,
    val mirrors: List<StudyFlowAttentionDeliveryTarget> = emptyList(),
    val diagnostics: StudyFlowAttentionRouteDiagnostics,
    val policyOutcome: StudyFlowAttentionPolicyOutcome? = null,
    val deliverSilently: Boolean = false,
    val compactOnly: Boolean = false,
    val requiresUserAction: Boolean = false,
) {
    init {
        require(decidedAtEpochMillis >= 0L) { "Attention route decision time must not be negative" }
        require(mirrors.distinctBy(StudyFlowAttentionDeliveryTarget::deviceId).size == mirrors.size) {
            "Attention route mirrors must be unique"
        }
        require(owner == null || mirrors.none { it.deviceId == owner.deviceId }) {
            "Attention route owner cannot also be a mirror"
        }
    }

    val deliverable: Boolean get() = owner != null
    val targetDeviceIds: List<StudyFlowDeviceId> get() = listOfNotNull(owner?.deviceId) + mirrors.map { it.deviceId }
}

@Serializable
data class StudyFlowAttentionHandoffRequest(
    val handoffId: String,
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val workspaceId: StudyFlowWorkspaceId,
    val fromDeviceId: StudyFlowDeviceId,
    val toDeviceId: StudyFlowDeviceId,
    val action: StudyFlowAttentionActionKind,
    val requestedAtEpochMillis: Long,
) {
    init {
        require(handoffId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            "Attention handoff ID must be a bounded wire token"
        }
        require(fromDeviceId != toDeviceId) { "Attention handoff requires two distinct devices" }
        require(requestedAtEpochMillis >= 0L) { "Attention handoff time must not be negative" }
    }
}

class StudyFlowAttentionDeliveryRouter {
    fun route(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        nowEpochMillis: Long,
        context: StudyFlowAttentionRoutingContext = StudyFlowAttentionRoutingContext(),
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowAttentionDeliveryRoute {
        require(devices.distinctBy { it.deviceId }.size == devices.size) {
            "Attention router devices must be unique"
        }
        require(nowEpochMillis >= 0L) { "Attention route time must not be negative" }
        if (ledgerEntry?.state?.suppressesRedelivery() == true) {
            return noRoute(
                occurrence = occurrence,
                policy = occurrence.deliveryPolicy,
                reason = StudyFlowAttentionRouteReason.LEDGER_ALREADY_HANDLED,
                nowEpochMillis = nowEpochMillis,
                candidateCount = devices.size,
                ledgerState = ledgerEntry.state,
                explanation = "Already handled by ${ledgerEntry.targetDeviceId?.value ?: "ledger"}.",
            )
        }

        val available = devices
            .filterNot { it.deviceId in context.mutedDeviceIds }
            .filter { it.canOwn(occurrence, nowEpochMillis, context) }
        val explicitTargets = occurrence.targetDeviceIds
        if (explicitTargets.isNotEmpty()) {
            val explicit = available.filter { it.deviceId in explicitTargets }
            return chooseRoute(
                occurrence = occurrence,
                devices = explicit,
                allCandidates = devices,
                nowEpochMillis = nowEpochMillis,
                context = context,
                reason = StudyFlowAttentionRouteReason.EXPLICIT_TARGET,
                mirrorPool = emptyList(),
                explanation = "Explicit target device list controls ATT07 ownership.",
            )
        }

        return when (occurrence.deliveryPolicy) {
            StudyFlowAttentionDeliveryPolicy.LOCAL_ONLY -> {
                val local = context.localDeviceId?.let { localId -> available.filter { it.deviceId == localId } }
                    ?: occurrence.sourceDeviceId?.let { sourceId -> available.filter { it.deviceId == sourceId } }
                    ?: emptyList()
                chooseRoute(
                    occurrence = occurrence,
                    devices = local,
                    allCandidates = devices,
                    nowEpochMillis = nowEpochMillis,
                    context = context,
                    reason = StudyFlowAttentionRouteReason.LOCAL_ONLY,
                    mirrorPool = emptyList(),
                    explanation = "Local-only reminders never mirror to another StudyFlow client.",
                )
            }
            StudyFlowAttentionDeliveryPolicy.PRIMARY_DEVICE -> {
                val primary = context.primaryDeviceId?.let { id -> available.filter { it.deviceId == id } }
                    ?: emptyList()
                chooseRoute(
                    occurrence = occurrence,
                    devices = primary.ifEmpty { preferredFallback(available, context) },
                    allCandidates = devices,
                    nowEpochMillis = nowEpochMillis,
                    context = context,
                    reason = if (primary.isNotEmpty()) {
                        StudyFlowAttentionRouteReason.PRIMARY_DEVICE
                    } else {
                        StudyFlowAttentionRouteReason.FALLBACK_BEST_CAPABLE
                    },
                    mirrorPool = emptyList(),
                    explanation = "Primary-device routing falls back only when the primary cannot deliver.",
                )
            }
            StudyFlowAttentionDeliveryPolicy.ACTIVE_THEN_PRIMARY -> {
                val active = context.foregroundDeviceId?.let { id -> available.filter { it.deviceId == id } }
                    ?: available.filter { it.foregroundActive }
                if (active.isNotEmpty()) {
                    chooseRoute(
                        occurrence = occurrence,
                        devices = active,
                        allCandidates = devices,
                        nowEpochMillis = nowEpochMillis,
                        context = context,
                        reason = StudyFlowAttentionRouteReason.ACTIVE_FOREGROUND,
                        mirrorPool = available.filterNot { device ->
                            device.deviceId in active.map(StudyFlowAttentionDeviceCapability::deviceId)
                        },
                        explanation = "The foreground StudyFlow client owns the reminder first.",
                    )
                } else {
                    val primary = context.primaryDeviceId?.let { id -> available.filter { it.deviceId == id } }
                        ?: emptyList()
                    chooseRoute(
                        occurrence = occurrence,
                        devices = primary.ifEmpty { preferredFallback(available, context) },
                        allCandidates = devices,
                        nowEpochMillis = nowEpochMillis,
                        context = context,
                        reason = if (primary.isNotEmpty()) {
                            StudyFlowAttentionRouteReason.PRIMARY_DEVICE
                        } else {
                            StudyFlowAttentionRouteReason.FALLBACK_BEST_CAPABLE
                        },
                        mirrorPool = emptyList(),
                        explanation = "No active foreground client was available; ATT07 used a durable notifier.",
                    )
                }
            }
            StudyFlowAttentionDeliveryPolicy.PHONE_AND_DESKTOP -> {
                val phoneDesktop = available.filter {
                    it.clientKind == StudyFlowLinkClientKind.ANDROID || it.clientKind == StudyFlowLinkClientKind.DESKTOP
                }
                chooseRoute(
                    occurrence = occurrence,
                    devices = phoneDesktop.ifEmpty { preferredFallback(available, context) },
                    allCandidates = devices,
                    nowEpochMillis = nowEpochMillis,
                    context = context,
                    reason = StudyFlowAttentionRouteReason.PHONE_DESKTOP_PAIR,
                    mirrorPool = phoneDesktop,
                    explanation = "Phone/desktop policy owns once and mirrors to the other capable personal device.",
                )
            }
            StudyFlowAttentionDeliveryPolicy.ALL_CAPABLE -> chooseRoute(
                occurrence = occurrence,
                devices = available,
                allCandidates = devices,
                nowEpochMillis = nowEpochMillis,
                context = context,
                reason = StudyFlowAttentionRouteReason.ALL_CAPABLE,
                mirrorPool = available,
                explanation = "All capable policy routes one owner and bounded mirror surfaces.",
            )
        }
    }

    fun routeWithPolicy(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        nowEpochMillis: Long,
        focusPolicy: StudyFlowAttentionFocusPolicy,
        context: StudyFlowAttentionRoutingContext = StudyFlowAttentionRoutingContext(),
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
        localClientKind: StudyFlowLinkClientKind? = null,
    ): StudyFlowAttentionDeliveryRoute {
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence = occurrence,
            policy = focusPolicy,
            nowEpochMillis = nowEpochMillis,
            ledgerEntry = ledgerEntry,
            localClientKind = localClientKind,
        )
        if (decision.outcome == StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE ||
            decision.outcome == StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END
        ) {
            return noRoute(
                occurrence = occurrence,
                policy = occurrence.deliveryPolicy,
                reason = if (decision.outcome == StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE) {
                    StudyFlowAttentionRouteReason.LEDGER_ALREADY_HANDLED
                } else {
                    StudyFlowAttentionRouteReason.NO_CAPABLE_DEVICE
                },
                nowEpochMillis = nowEpochMillis,
                candidateCount = devices.size,
                ledgerState = ledgerEntry?.state,
                explanation = decision.explanation,
            ).copy(
                policyOutcome = decision.outcome,
                diagnostics = StudyFlowAttentionRouteDiagnostics(
                    candidateCount = devices.size,
                    eligibleCount = 0,
                    suppressedDeviceIds = devices.map(StudyFlowAttentionDeviceCapability::deviceId),
                    ledgerState = ledgerEntry?.state,
                    policyOutcome = decision.outcome,
                    deferredUntilEpochMillis = decision.deferredUntilEpochMillis,
                    explanation = decision.explanation,
                ),
            )
        }

        val policyDevices = when (decision.outcome) {
            StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE -> devices.filter { it.clientKind == StudyFlowLinkClientKind.ANDROID }
            StudyFlowAttentionPolicyOutcome.COMPACT_ONLY -> devices.filter {
                StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE in it.deliverySurfaces
            }
            else -> devices
        }
        val policyContext = context.copy(
            maxMirrors = if (decision.outcome == StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR) {
                minOf(context.maxMirrors, decision.requestedMirrorCount).coerceIn(0, 3)
            } else context.maxMirrors,
        )
        val routed = route(
            occurrence = occurrence,
            devices = policyDevices,
            nowEpochMillis = nowEpochMillis,
            context = policyContext,
            ledgerEntry = ledgerEntry,
        )
        val owner = if (decision.compactOnly) routed.owner?.copy(surface = StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE) else routed.owner
        val compactMirrors = if (decision.compactOnly) {
            routed.mirrors.map { it.copy(surface = StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE) }
        } else routed.mirrors
        val mirrors = if (decision.outcome == StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR && owner != null) {
            val explicitTargets = occurrence.targetDeviceIds.toSet()
            val allowed = policyDevices
                .filter { it.deviceId != owner.deviceId }
                .filter { explicitTargets.isEmpty() || it.deviceId in explicitTargets }
                .filter { it.canOwn(occurrence, nowEpochMillis, policyContext) }
                .rankedFor(occurrence, nowEpochMillis, policyContext)
            val existingIds = compactMirrors.mapTo(linkedSetOf(), StudyFlowAttentionDeliveryTarget::deviceId)
            compactMirrors + allowed
                .filterNot { it.deviceId in existingIds }
                .take((decision.requestedMirrorCount - compactMirrors.size).coerceAtLeast(0))
                .map {
                    it.toTarget(
                        occurrence = occurrence,
                        nowEpochMillis = nowEpochMillis,
                        context = policyContext,
                        role = StudyFlowAttentionDeliveryRole.MIRROR,
                    )
                }
        } else compactMirrors
        return routed.copy(
            owner = owner,
            mirrors = mirrors,
            policyOutcome = decision.outcome,
            deliverSilently = decision.deliverSilently,
            compactOnly = decision.compactOnly,
            requiresUserAction = decision.requiresUserAction,
            diagnostics = routed.diagnostics.copy(
                policyOutcome = decision.outcome,
                deferredUntilEpochMillis = decision.deferredUntilEpochMillis,
                explanation = "${routed.diagnostics.explanation} ${decision.explanation}",
            ),
        )
    }

    fun createHandoffRequest(
        occurrence: StudyFlowAttentionOccurrence,
        fromDeviceId: StudyFlowDeviceId,
        toDeviceId: StudyFlowDeviceId,
        requestedAtEpochMillis: Long,
        action: StudyFlowAttentionActionKind = StudyFlowAttentionActionKind.MOVE_TO_DEVICE,
    ): StudyFlowAttentionHandoffRequest = StudyFlowAttentionHandoffRequest(
        handoffId = StudyFlowAttentionOccurrenceId.deterministic(
            "handoff",
            occurrence.occurrenceId.value,
            fromDeviceId.value,
            toDeviceId.value,
            requestedAtEpochMillis.toString(),
        ).value,
        occurrenceId = occurrence.occurrenceId,
        workspaceId = occurrence.workspaceId,
        fromDeviceId = fromDeviceId,
        toDeviceId = toDeviceId,
        action = action,
        requestedAtEpochMillis = requestedAtEpochMillis,
    )

    private fun chooseRoute(
        occurrence: StudyFlowAttentionOccurrence,
        devices: List<StudyFlowAttentionDeviceCapability>,
        allCandidates: List<StudyFlowAttentionDeviceCapability>,
        nowEpochMillis: Long,
        context: StudyFlowAttentionRoutingContext,
        reason: StudyFlowAttentionRouteReason,
        mirrorPool: List<StudyFlowAttentionDeviceCapability>,
        explanation: String,
    ): StudyFlowAttentionDeliveryRoute {
        val ranked = devices.rankedFor(occurrence, nowEpochMillis, context)
        val ownerDevice = ranked.firstOrNull()
        if (ownerDevice == null) {
            return noRoute(
                occurrence = occurrence,
                policy = occurrence.deliveryPolicy,
                reason = StudyFlowAttentionRouteReason.NO_CAPABLE_DEVICE,
                nowEpochMillis = nowEpochMillis,
                candidateCount = allCandidates.size,
                explanation = explanation,
            )
        }
        val owner = ownerDevice.toTarget(
            occurrence = occurrence,
            nowEpochMillis = nowEpochMillis,
            context = context,
            role = StudyFlowAttentionDeliveryRole.OWNER,
        )
        val mirrorIds = linkedSetOf<StudyFlowDeviceId>()
        val mirrors = mirrorPool
            .filter { it.deviceId != owner.deviceId }
            .filter { it.canOwn(occurrence, nowEpochMillis, context) }
            .rankedFor(occurrence, nowEpochMillis, context)
            .filter { mirrorIds.add(it.deviceId) }
            .take(context.maxMirrors)
            .map {
                it.toTarget(
                    occurrence = occurrence,
                    nowEpochMillis = nowEpochMillis,
                    context = context,
                    role = StudyFlowAttentionDeliveryRole.MIRROR,
                )
            }
        val eligibleIds = (ranked + mirrorPool.filter { it.canOwn(occurrence, nowEpochMillis, context) })
            .map(StudyFlowAttentionDeviceCapability::deviceId)
            .toSet()
        return StudyFlowAttentionDeliveryRoute(
            occurrenceId = occurrence.occurrenceId,
            workspaceId = occurrence.workspaceId,
            policy = occurrence.deliveryPolicy,
            reason = reason,
            decidedAtEpochMillis = nowEpochMillis,
            owner = owner,
            mirrors = mirrors,
            diagnostics = StudyFlowAttentionRouteDiagnostics(
                candidateCount = allCandidates.size,
                eligibleCount = eligibleIds.size,
                suppressedDeviceIds = allCandidates
                    .map(StudyFlowAttentionDeviceCapability::deviceId)
                    .filterNot { it in eligibleIds },
                explanation = explanation,
            ),
        )
    }

    private fun noRoute(
        occurrence: StudyFlowAttentionOccurrence,
        policy: StudyFlowAttentionDeliveryPolicy,
        reason: StudyFlowAttentionRouteReason,
        nowEpochMillis: Long,
        candidateCount: Int,
        ledgerState: StudyFlowAttentionDeliveryState? = null,
        explanation: String,
    ): StudyFlowAttentionDeliveryRoute = StudyFlowAttentionDeliveryRoute(
        occurrenceId = occurrence.occurrenceId,
        workspaceId = occurrence.workspaceId,
        policy = policy,
        reason = reason,
        decidedAtEpochMillis = nowEpochMillis,
        diagnostics = StudyFlowAttentionRouteDiagnostics(
            candidateCount = candidateCount,
            eligibleCount = 0,
            ledgerState = ledgerState,
            explanation = explanation,
        ),
    )

    private fun preferredFallback(
        available: List<StudyFlowAttentionDeviceCapability>,
        context: StudyFlowAttentionRoutingContext,
    ): List<StudyFlowAttentionDeviceCapability> = available.filter {
        it.designatedNotifier || it.deviceId in context.designatedNotifierDeviceIds
    }.ifEmpty { available }
}

fun StudyFlowLinkPeerDescriptor.asAttentionDeviceCapability(
    availability: StudyFlowAttentionDeviceAvailability = StudyFlowAttentionDeviceAvailability.ONLINE,
    deliverySurfaces: List<StudyFlowAttentionDeliverySurface> = defaultAttentionSurfacesFor(clientKind),
    foregroundActive: Boolean = availability == StudyFlowAttentionDeviceAvailability.FOREGROUND,
    notificationPermissionGranted: Boolean = true,
    primaryUserDevice: Boolean = false,
    designatedNotifier: Boolean = false,
    batteryConstrained: Boolean = false,
    lastSeenAtEpochMillis: Long? = null,
    mutedUntilEpochMillis: Long? = null,
): StudyFlowAttentionDeviceCapability = StudyFlowAttentionDeviceCapability(
    peer = this,
    availability = availability,
    deliverySurfaces = deliverySurfaces,
    foregroundActive = foregroundActive,
    notificationPermissionGranted = notificationPermissionGranted,
    primaryUserDevice = primaryUserDevice,
    designatedNotifier = designatedNotifier,
    batteryConstrained = batteryConstrained,
    lastSeenAtEpochMillis = lastSeenAtEpochMillis,
    mutedUntilEpochMillis = mutedUntilEpochMillis,
)

fun defaultAttentionSurfacesFor(kind: StudyFlowLinkClientKind): List<StudyFlowAttentionDeliverySurface> = when (kind) {
    StudyFlowLinkClientKind.ANDROID -> listOf(
        StudyFlowAttentionDeliverySurface.ANDROID_ALARM,
        StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
        StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
    )
    StudyFlowLinkClientKind.DESKTOP -> listOf(
        StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
        StudyFlowAttentionDeliverySurface.DESKTOP_TRAY,
    )
    StudyFlowLinkClientKind.WEB -> listOf(
        StudyFlowAttentionDeliverySurface.WEB_PUSH,
        StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION,
        StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE,
    )
    StudyFlowLinkClientKind.ANDROID_TV,
    StudyFlowLinkClientKind.WEB_TV -> listOf(
        StudyFlowAttentionDeliverySurface.FOREGROUND_BANNER,
        StudyFlowAttentionDeliverySurface.LEDGER_ONLY,
    )
}

private fun List<StudyFlowAttentionDeviceCapability>.rankedFor(
    occurrence: StudyFlowAttentionOccurrence,
    nowEpochMillis: Long,
    context: StudyFlowAttentionRoutingContext,
): List<StudyFlowAttentionDeviceCapability> = sortedWith(
    compareByDescending<StudyFlowAttentionDeviceCapability> { it.score(occurrence, nowEpochMillis, context) }
        .thenByDescending { it.lastSeenAtEpochMillis ?: 0L }
        .thenBy { it.deviceId.value },
)

private fun StudyFlowAttentionDeviceCapability.toTarget(
    occurrence: StudyFlowAttentionOccurrence,
    nowEpochMillis: Long,
    context: StudyFlowAttentionRoutingContext,
    role: StudyFlowAttentionDeliveryRole,
): StudyFlowAttentionDeliveryTarget = StudyFlowAttentionDeliveryTarget(
    deviceId = deviceId,
    displayName = peer.displayName,
    clientKind = clientKind,
    role = role,
    surface = requireNotNull(bestSurface(nowEpochMillis, context)) {
        "Eligible attention device lost its route surface"
    },
    score = score(occurrence, nowEpochMillis, context),
)

private fun StudyFlowLinkClientKind.isTv(): Boolean =
    this == StudyFlowLinkClientKind.ANDROID_TV || this == StudyFlowLinkClientKind.WEB_TV

private fun StudyFlowAttentionDeliveryState.suppressesRedelivery(): Boolean = when (this) {
    StudyFlowAttentionDeliveryState.DELIVERED,
    StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
    StudyFlowAttentionDeliveryState.STARTED,
    StudyFlowAttentionDeliveryState.COMPLETED,
    StudyFlowAttentionDeliveryState.SNOOZED,
    StudyFlowAttentionDeliveryState.RESCHEDULED,
    StudyFlowAttentionDeliveryState.DISMISSED,
    StudyFlowAttentionDeliveryState.MISSED,
    StudyFlowAttentionDeliveryState.EXPIRED,
    StudyFlowAttentionDeliveryState.CANCELLED -> true
    StudyFlowAttentionDeliveryState.PLANNED,
    StudyFlowAttentionDeliveryState.SCHEDULED,
    StudyFlowAttentionDeliveryState.DELIVERY_PENDING -> false
}
