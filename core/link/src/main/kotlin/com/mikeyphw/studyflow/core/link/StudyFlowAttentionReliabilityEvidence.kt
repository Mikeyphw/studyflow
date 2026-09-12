package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultReliabilityStaleAfterMillis = 5 * 60_000L

@Serializable
enum class StudyFlowAttentionEvidenceKind {
    @SerialName("planned") PLANNED,
    @SerialName("scheduled") SCHEDULED,
    @SerialName("delivery_pending") DELIVERY_PENDING,
    @SerialName("delivered") DELIVERED,
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("started") STARTED,
    @SerialName("completed") COMPLETED,
    @SerialName("snoozed") SNOOZED,
    @SerialName("rescheduled") RESCHEDULED,
    @SerialName("dismissed") DISMISSED,
    @SerialName("missed") MISSED,
    @SerialName("expired") EXPIRED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class StudyFlowAttentionPlatformReliabilityStatus {
    @SerialName("ready") READY,
    @SerialName("degraded") DEGRADED,
    @SerialName("foreground_only") FOREGROUND_ONLY,
    @SerialName("blocked") BLOCKED,
    @SerialName("offline") OFFLINE,
}

@Serializable
data class StudyFlowAttentionRouteAuditTarget(
    val deviceId: StudyFlowDeviceId,
    val clientKind: StudyFlowLinkClientKind,
    val surface: StudyFlowAttentionDeliverySurface,
    val role: StudyFlowAttentionDeliveryRole,
)

@Serializable
data class StudyFlowAttentionRouteAudit(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val reason: StudyFlowAttentionRouteReason,
    val decidedAtEpochMillis: Long,
    val owner: StudyFlowAttentionRouteAuditTarget? = null,
    val mirrors: List<StudyFlowAttentionRouteAuditTarget> = emptyList(),
    val candidateCount: Int,
    val eligibleCount: Int,
    val suppressedDeviceIds: List<StudyFlowDeviceId> = emptyList(),
    val policyOutcome: StudyFlowAttentionPolicyOutcome? = null,
    val explanation: String,
) {
    init {
        require(decidedAtEpochMillis >= 0L) { "Route-audit timestamp must not be negative" }
        require(candidateCount >= 0) { "Route-audit candidate count must not be negative" }
        require(eligibleCount >= 0) { "Route-audit eligible count must not be negative" }
        require(mirrors.distinctBy { it.deviceId }.size == mirrors.size) { "Route-audit mirrors must be unique" }
        require(owner == null || mirrors.none { it.deviceId == owner.deviceId }) { "Route-audit owner cannot also be a mirror" }
        require(explanation.isNotBlank()) { "Route-audit explanation must not be blank" }
    }

    companion object {
        fun from(route: StudyFlowAttentionDeliveryRoute): StudyFlowAttentionRouteAudit = StudyFlowAttentionRouteAudit(
            occurrenceId = route.occurrenceId,
            reason = route.reason,
            decidedAtEpochMillis = route.decidedAtEpochMillis,
            owner = route.owner?.toAuditTarget(),
            mirrors = route.mirrors.map(StudyFlowAttentionDeliveryTarget::toAuditTarget),
            candidateCount = route.diagnostics.candidateCount,
            eligibleCount = route.diagnostics.eligibleCount,
            suppressedDeviceIds = route.diagnostics.suppressedDeviceIds,
            policyOutcome = route.policyOutcome ?: route.diagnostics.policyOutcome,
            explanation = route.diagnostics.explanation,
        )
    }
}

@Serializable
data class StudyFlowAttentionAnalytics(
    val totalCount: Int,
    val activeCount: Int,
    val deliveredCount: Int,
    val missedCount: Int,
    val deferredCount: Int,
    val snoozedCount: Int,
    val dismissedCount: Int,
    val doneCount: Int,
    val silentCount: Int,
    val compactOnlyCount: Int,
    val routedToPhoneCount: Int,
    val escalatedMirrorCount: Int,
    val duplicateSuppressedCount: Int,
    val userActionRequiredCount: Int,
    val deliveryAttemptCount: Int,
    val auditedRouteCount: Int,
    val averageDeliveryDelayMillis: Long? = null,
) {
    init {
        listOf(
            totalCount, activeCount, deliveredCount, missedCount, deferredCount, snoozedCount,
            dismissedCount, doneCount, silentCount, compactOnlyCount, routedToPhoneCount,
            escalatedMirrorCount, duplicateSuppressedCount, userActionRequiredCount,
            deliveryAttemptCount, auditedRouteCount,
        ).forEach { require(it >= 0) { "Attention analytics counts must not be negative" } }
        require(averageDeliveryDelayMillis == null || averageDeliveryDelayMillis >= 0L) {
            "Average attention delivery delay must not be negative"
        }
    }
}

@Serializable
data class StudyFlowAttentionCategoryAnalytics(
    val category: StudyFlowAttentionCategory,
    val totalCount: Int,
    val deliveredCount: Int,
    val missedCount: Int,
    val doneCount: Int,
) {
    init {
        require(totalCount >= 0 && deliveredCount >= 0 && missedCount >= 0 && doneCount >= 0) {
            "Category analytics counts must not be negative"
        }
    }
}

@Serializable
data class StudyFlowAttentionEvidenceRecord(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val workspaceId: StudyFlowWorkspaceId,
    val category: StudyFlowAttentionCategory,
    val state: StudyFlowAttentionDeliveryState,
    val evidenceKind: StudyFlowAttentionEvidenceKind,
    val scheduledAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deliveredAtEpochMillis: Long? = null,
    val acknowledgedAtEpochMillis: Long? = null,
    val deliveryAttempts: Int,
    val sourceDeviceId: StudyFlowDeviceId? = null,
    val targetDeviceId: StudyFlowDeviceId? = null,
    val policyAudit: StudyFlowAttentionPolicyAudit? = null,
    val routeAudit: StudyFlowAttentionRouteAudit? = null,
)

@Serializable
data class StudyFlowAttentionPlatformReliabilityDiagnostic(
    val deviceId: StudyFlowDeviceId,
    val clientKind: StudyFlowLinkClientKind,
    val status: StudyFlowAttentionPlatformReliabilityStatus,
    val availability: StudyFlowAttentionDeviceAvailability,
    val deliverySurfaces: List<StudyFlowAttentionDeliverySurface>,
    val notificationPermissionGranted: Boolean,
    val truthfulBackgroundDelivery: Boolean,
    val ownerRouteCount: Int,
    val mirrorRouteCount: Int,
    val targetedEntryCount: Int,
    val lastSeenAtEpochMillis: Long? = null,
    val explanation: String,
) {
    init {
        require(ownerRouteCount >= 0 && mirrorRouteCount >= 0 && targetedEntryCount >= 0) {
            "Platform reliability counts must not be negative"
        }
        require(explanation.isNotBlank()) { "Platform reliability explanation must not be blank" }
    }
}

@Serializable
data class StudyFlowAttentionReliabilityEvidenceSnapshot(
    val generatedAtEpochMillis: Long,
    val workspaceId: StudyFlowWorkspaceId? = null,
    val analytics: StudyFlowAttentionAnalytics,
    val categoryAnalytics: List<StudyFlowAttentionCategoryAnalytics>,
    val platforms: List<StudyFlowAttentionPlatformReliabilityDiagnostic>,
    val evidence: List<StudyFlowAttentionEvidenceRecord>,
    val routeAudits: List<StudyFlowAttentionRouteAudit>,
) {
    init {
        require(generatedAtEpochMillis >= 0L) { "Reliability-evidence generation time must not be negative" }
        require(categoryAnalytics.distinctBy { it.category }.size == categoryAnalytics.size) {
            "Category analytics must be unique"
        }
        require(platforms.distinctBy { it.deviceId }.size == platforms.size) {
            "Platform reliability diagnostics must be unique by device"
        }
    }
}

object StudyFlowAttentionReliabilityAnalyzer {
    fun analyze(
        ledger: StudyFlowAttentionLedgerSnapshot,
        devices: List<StudyFlowAttentionDeviceCapability>,
        nowEpochMillis: Long,
        workspaceId: StudyFlowWorkspaceId? = null,
        staleAfterMillis: Long = DefaultReliabilityStaleAfterMillis,
    ): StudyFlowAttentionReliabilityEvidenceSnapshot {
        require(nowEpochMillis >= 0L) { "Reliability inspection time must not be negative" }
        require(staleAfterMillis > 0L) { "Reliability stale threshold must be positive" }
        require(devices.distinctBy { it.deviceId }.size == devices.size) { "Reliability devices must be unique" }
        val entries = ledger.entries
            .asSequence()
            .filter { workspaceId == null || it.occurrence.workspaceId == workspaceId }
            .sortedByDescending(StudyFlowAttentionLedgerEntry::updatedAtEpochMillis)
            .toList()
        val deliveredStates = setOf(
            StudyFlowAttentionDeliveryState.DELIVERED,
            StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            StudyFlowAttentionDeliveryState.STARTED,
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.SNOOZED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.DISMISSED,
        )
        val activeStates = setOf(
            StudyFlowAttentionDeliveryState.PLANNED,
            StudyFlowAttentionDeliveryState.SCHEDULED,
            StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
            StudyFlowAttentionDeliveryState.SNOOZED,
            StudyFlowAttentionDeliveryState.RESCHEDULED,
            StudyFlowAttentionDeliveryState.MISSED,
        )
        val deliveryDelays = entries.mapNotNull { entry ->
            entry.deliveredAtEpochMillis?.let { delivered ->
                (delivered - entry.occurrence.originalScheduledAtEpochMillis).coerceAtLeast(0L)
            }
        }
        val analytics = StudyFlowAttentionAnalytics(
            totalCount = entries.size,
            activeCount = entries.count { it.state in activeStates },
            deliveredCount = entries.count { it.state in deliveredStates },
            missedCount = entries.count { it.state == StudyFlowAttentionDeliveryState.MISSED },
            deferredCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END },
            snoozedCount = entries.count { it.state == StudyFlowAttentionDeliveryState.SNOOZED },
            dismissedCount = entries.count { it.state == StudyFlowAttentionDeliveryState.DISMISSED },
            doneCount = entries.count { it.state == StudyFlowAttentionDeliveryState.COMPLETED },
            silentCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.SILENT_DELIVERY },
            compactOnlyCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.COMPACT_ONLY },
            routedToPhoneCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE },
            escalatedMirrorCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR },
            duplicateSuppressedCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE },
            userActionRequiredCount = entries.count { it.policyAudit?.outcome == StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION },
            deliveryAttemptCount = entries.sumOf(StudyFlowAttentionLedgerEntry::deliveryAttempts),
            auditedRouteCount = entries.count { it.routeAudit != null },
            averageDeliveryDelayMillis = deliveryDelays.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
        val categories = StudyFlowAttentionCategory.entries.mapNotNull { category ->
            val selected = entries.filter { it.occurrence.category == category }
            if (selected.isEmpty()) return@mapNotNull null
            StudyFlowAttentionCategoryAnalytics(
                category = category,
                totalCount = selected.size,
                deliveredCount = selected.count { it.state in deliveredStates },
                missedCount = selected.count { it.state == StudyFlowAttentionDeliveryState.MISSED },
                doneCount = selected.count { it.state == StudyFlowAttentionDeliveryState.COMPLETED },
            )
        }
        val routeAudits = entries.mapNotNull(StudyFlowAttentionLedgerEntry::routeAudit)
            .sortedByDescending(StudyFlowAttentionRouteAudit::decidedAtEpochMillis)
        val platforms = devices.map { device ->
            platformDiagnostic(device, entries, routeAudits, nowEpochMillis, staleAfterMillis)
        }.sortedBy { it.clientKind.name }
        val evidence = entries.map { entry ->
            StudyFlowAttentionEvidenceRecord(
                occurrenceId = entry.occurrence.occurrenceId,
                workspaceId = entry.occurrence.workspaceId,
                category = entry.occurrence.category,
                state = entry.state,
                evidenceKind = entry.state.toEvidenceKind(),
                scheduledAtEpochMillis = entry.occurrence.scheduledAtEpochMillis,
                updatedAtEpochMillis = entry.updatedAtEpochMillis,
                deliveredAtEpochMillis = entry.deliveredAtEpochMillis,
                acknowledgedAtEpochMillis = entry.acknowledgedAtEpochMillis,
                deliveryAttempts = entry.deliveryAttempts,
                sourceDeviceId = entry.sourceDeviceId,
                targetDeviceId = entry.targetDeviceId,
                policyAudit = entry.policyAudit,
                routeAudit = entry.routeAudit,
            )
        }
        return StudyFlowAttentionReliabilityEvidenceSnapshot(
            generatedAtEpochMillis = nowEpochMillis,
            workspaceId = workspaceId,
            analytics = analytics,
            categoryAnalytics = categories,
            platforms = platforms,
            evidence = evidence,
            routeAudits = routeAudits,
        )
    }

    private fun platformDiagnostic(
        device: StudyFlowAttentionDeviceCapability,
        entries: List<StudyFlowAttentionLedgerEntry>,
        audits: List<StudyFlowAttentionRouteAudit>,
        nowEpochMillis: Long,
        staleAfterMillis: Long,
    ): StudyFlowAttentionPlatformReliabilityDiagnostic {
        val ownerCount = audits.count { it.owner?.deviceId == device.deviceId }
        val mirrorCount = audits.count { audit -> audit.mirrors.any { it.deviceId == device.deviceId } }
        val targeted = entries.count { it.targetDeviceId == device.deviceId || device.deviceId in it.occurrence.targetDeviceIds }
        val surfaces = device.deliverySurfaces
        val stale = device.isStale(nowEpochMillis, staleAfterMillis)
        val tv = device.clientKind == StudyFlowLinkClientKind.ANDROID_TV || device.clientKind == StudyFlowLinkClientKind.WEB_TV
        val background = when (device.clientKind) {
            StudyFlowLinkClientKind.ANDROID -> device.notificationPermissionGranted && (
                StudyFlowAttentionDeliverySurface.ANDROID_ALARM in surfaces ||
                    StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION in surfaces
                )
            StudyFlowLinkClientKind.WEB -> device.notificationPermissionGranted && StudyFlowAttentionDeliverySurface.WEB_PUSH in surfaces
            StudyFlowLinkClientKind.DESKTOP,
            StudyFlowLinkClientKind.ANDROID_TV,
            StudyFlowLinkClientKind.WEB_TV -> false
        }
        val status = when {
            device.availability == StudyFlowAttentionDeviceAvailability.OFFLINE || stale -> StudyFlowAttentionPlatformReliabilityStatus.OFFLINE
            tv -> StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY
            background -> StudyFlowAttentionPlatformReliabilityStatus.READY
            device.clientKind == StudyFlowLinkClientKind.WEB -> StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY
            StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE in surfaces || StudyFlowAttentionDeliverySurface.DESKTOP_TRAY in surfaces -> StudyFlowAttentionPlatformReliabilityStatus.DEGRADED
            !device.notificationPermissionGranted -> StudyFlowAttentionPlatformReliabilityStatus.BLOCKED
            else -> StudyFlowAttentionPlatformReliabilityStatus.DEGRADED
        }
        val explanation = when (status) {
            StudyFlowAttentionPlatformReliabilityStatus.READY -> "A durable supported delivery surface is currently available."
            StudyFlowAttentionPlatformReliabilityStatus.FOREGROUND_ONLY -> if (tv) {
                "TV attention is foreground-only; closed-TV delivery requires a capable phone, desktop, or Web owner."
            } else {
                "Browser attention is foreground-only unless an actual Web Push subscription is available."
            }
            StudyFlowAttentionPlatformReliabilityStatus.BLOCKED -> "The platform cannot currently surface a normal notification; ATT01 ledger evidence is still retained."
            StudyFlowAttentionPlatformReliabilityStatus.OFFLINE -> "The device is offline or stale and is not a reliable current delivery owner."
            StudyFlowAttentionPlatformReliabilityStatus.DEGRADED -> "Attention can still be represented, but delivery depends on an in-app, tray, badge, or other limited surface."
        }
        return StudyFlowAttentionPlatformReliabilityDiagnostic(
            deviceId = device.deviceId,
            clientKind = device.clientKind,
            status = status,
            availability = device.availability,
            deliverySurfaces = surfaces,
            notificationPermissionGranted = device.notificationPermissionGranted,
            truthfulBackgroundDelivery = background,
            ownerRouteCount = ownerCount,
            mirrorRouteCount = mirrorCount,
            targetedEntryCount = targeted,
            lastSeenAtEpochMillis = device.lastSeenAtEpochMillis,
            explanation = explanation,
        )
    }
}

object StudyFlowAttentionReliabilityExport {
    fun toJson(snapshot: StudyFlowAttentionReliabilityEvidenceSnapshot): String =
        StudyFlowAttentionWireJson.encodeToString(StudyFlowAttentionReliabilityEvidenceSnapshot.serializer(), snapshot)

    fun toCsv(snapshot: StudyFlowAttentionReliabilityEvidenceSnapshot): String = buildString {
        appendLine("occurrence_id,workspace_id,category,state,policy_outcome,route_reason,owner_device,mirror_devices,scheduled_at,updated_at,delivered_at,delivery_attempts")
        snapshot.evidence.forEach { record ->
            appendLine(
                listOf(
                    record.occurrenceId.value,
                    record.workspaceId.value,
                    record.category.name.lowercase(),
                    record.state.name.lowercase(),
                    record.policyAudit?.outcome?.name?.lowercase().orEmpty(),
                    record.routeAudit?.reason?.name?.lowercase().orEmpty(),
                    record.routeAudit?.owner?.deviceId?.value.orEmpty(),
                    record.routeAudit?.mirrors?.joinToString("|") { it.deviceId.value }.orEmpty(),
                    record.scheduledAtEpochMillis.toString(),
                    record.updatedAtEpochMillis.toString(),
                    record.deliveredAtEpochMillis?.toString().orEmpty(),
                    record.deliveryAttempts.toString(),
                ).joinToString(",", transform = ::csvCell),
            )
        }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '\"' }) "\"$escaped\"" else escaped
    }
}

private fun StudyFlowAttentionDeliveryTarget.toAuditTarget(): StudyFlowAttentionRouteAuditTarget =
    StudyFlowAttentionRouteAuditTarget(
        deviceId = deviceId,
        clientKind = clientKind,
        surface = surface,
        role = role,
    )

private fun StudyFlowAttentionDeliveryState.toEvidenceKind(): StudyFlowAttentionEvidenceKind = when (this) {
    StudyFlowAttentionDeliveryState.PLANNED -> StudyFlowAttentionEvidenceKind.PLANNED
    StudyFlowAttentionDeliveryState.SCHEDULED -> StudyFlowAttentionEvidenceKind.SCHEDULED
    StudyFlowAttentionDeliveryState.DELIVERY_PENDING -> StudyFlowAttentionEvidenceKind.DELIVERY_PENDING
    StudyFlowAttentionDeliveryState.DELIVERED -> StudyFlowAttentionEvidenceKind.DELIVERED
    StudyFlowAttentionDeliveryState.ACKNOWLEDGED -> StudyFlowAttentionEvidenceKind.ACKNOWLEDGED
    StudyFlowAttentionDeliveryState.STARTED -> StudyFlowAttentionEvidenceKind.STARTED
    StudyFlowAttentionDeliveryState.COMPLETED -> StudyFlowAttentionEvidenceKind.COMPLETED
    StudyFlowAttentionDeliveryState.SNOOZED -> StudyFlowAttentionEvidenceKind.SNOOZED
    StudyFlowAttentionDeliveryState.RESCHEDULED -> StudyFlowAttentionEvidenceKind.RESCHEDULED
    StudyFlowAttentionDeliveryState.DISMISSED -> StudyFlowAttentionEvidenceKind.DISMISSED
    StudyFlowAttentionDeliveryState.MISSED -> StudyFlowAttentionEvidenceKind.MISSED
    StudyFlowAttentionDeliveryState.EXPIRED -> StudyFlowAttentionEvidenceKind.EXPIRED
    StudyFlowAttentionDeliveryState.CANCELLED -> StudyFlowAttentionEvidenceKind.CANCELLED
}
