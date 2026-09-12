package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DefaultCompactCompanionStaleAfterMillis = 120_000L
private const val DefaultCompactCompanionLimit = 4

@Serializable
@JvmInline
value class StudyFlowCompactCompanionId(val value: String) {
    init {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            "Compact companion ID must be a bounded wire token"
        }
    }
}

@Serializable
enum class StudyFlowCompactCompanionFormFactor {
    @SerialName("wearable") WEARABLE,
    @SerialName("lock_screen") LOCK_SCREEN,
    @SerialName("notification_shade") NOTIFICATION_SHADE,
    @SerialName("quick_tile") QUICK_TILE,
    @SerialName("desktop_mini") DESKTOP_MINI,
    @SerialName("web_compact") WEB_COMPACT,
}

@Serializable
enum class StudyFlowCompactCompanionAvailability {
    @SerialName("connected") CONNECTED,
    @SerialName("nearby") NEARBY,
    @SerialName("disconnected") DISCONNECTED,
}

@Serializable
enum class StudyFlowCompactAttentionRenderMode {
    @SerialName("glance") GLANCE,
    @SerialName("quick_action") QUICK_ACTION,
    @SerialName("mirror_only") MIRROR_ONLY,
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
data class StudyFlowCompactCompanionProfile(
    val companionId: StudyFlowCompactCompanionId,
    val displayName: String,
    val parentDeviceId: StudyFlowDeviceId,
    val formFactor: StudyFlowCompactCompanionFormFactor,
    val availability: StudyFlowCompactCompanionAvailability = StudyFlowCompactCompanionAvailability.CONNECTED,
    val maxActions: Int = 3,
    val canHandoff: Boolean = true,
    val canVibrate: Boolean = true,
    val canShowSensitiveText: Boolean = false,
    val lastSeenAtEpochMillis: Long? = null,
    val mutedUntilEpochMillis: Long? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Compact companion display name must not be blank" }
        require(maxActions in 1..3) { "Compact companion action count must be 1..3" }
        require(lastSeenAtEpochMillis == null || lastSeenAtEpochMillis >= 0L) {
            "Compact companion last-seen timestamp must not be negative"
        }
        require(mutedUntilEpochMillis == null || mutedUntilEpochMillis >= 0L) {
            "Compact companion mute timestamp must not be negative"
        }
    }

    fun isAvailable(nowEpochMillis: Long, staleAfterMillis: Long): Boolean {
        if (availability == StudyFlowCompactCompanionAvailability.DISCONNECTED) return false
        if (mutedUntilEpochMillis?.let { it > nowEpochMillis } == true) return false
        if (lastSeenAtEpochMillis?.let { nowEpochMillis - it > staleAfterMillis } == true) return false
        return true
    }
}

@Serializable
data class StudyFlowCompactAttentionAction(
    val kind: StudyFlowAttentionActionKind,
    val label: String,
    val destructive: Boolean = false,
    val requiresParentDevice: Boolean = true,
) {
    init {
        require(label.isNotBlank()) { "Compact companion action label must not be blank" }
    }
}

@Serializable
data class StudyFlowCompactAttentionCard(
    val companionId: StudyFlowCompactCompanionId,
    val companionDisplayName: String,
    val parentDeviceId: StudyFlowDeviceId,
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val category: StudyFlowAttentionCategory,
    val priority: StudyFlowAttentionPriority,
    val title: String,
    val body: String,
    val scheduledAtEpochMillis: Long,
    val renderMode: StudyFlowCompactAttentionRenderMode,
    val parentRouteRole: StudyFlowAttentionDeliveryRole,
    val ownerDeviceId: StudyFlowDeviceId?,
    val actions: List<StudyFlowCompactAttentionAction>,
    val sensitiveTextHidden: Boolean,
    val hapticHint: Boolean,
) {
    init {
        require(title.isNotBlank()) { "Compact companion title must not be blank" }
        require(body.isNotBlank()) { "Compact companion body must not be blank" }
        require(actions.distinctBy(StudyFlowCompactAttentionAction::kind).size == actions.size) {
            "Compact companion actions must be unique"
        }
        require(actions.size <= 3) { "Compact companion cards support at most three actions" }
    }
}

@Serializable
data class StudyFlowCompactCompanionDiagnostics(
    val candidateCount: Int,
    val eligibleCount: Int,
    val projectedCount: Int,
    val suppressedCompanionIds: List<StudyFlowCompactCompanionId>,
    val parentRouteDeliverable: Boolean,
    val explanation: String,
) {
    init {
        require(candidateCount >= 0) { "Compact companion candidate count must not be negative" }
        require(eligibleCount >= 0) { "Compact companion eligible count must not be negative" }
        require(projectedCount >= 0) { "Compact companion projected count must not be negative" }
        require(suppressedCompanionIds.distinct().size == suppressedCompanionIds.size) {
            "Suppressed compact companion IDs must be unique"
        }
        require(explanation.isNotBlank()) { "Compact companion diagnostics explanation must not be blank" }
    }
}

@Serializable
data class StudyFlowCompactAttentionProjection(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val ownerDeviceId: StudyFlowDeviceId?,
    val cards: List<StudyFlowCompactAttentionCard>,
    val diagnostics: StudyFlowCompactCompanionDiagnostics,
) {
    init {
        require(cards.distinctBy(StudyFlowCompactAttentionCard::companionId).size == cards.size) {
            "Compact companion projection must not duplicate companions"
        }
    }

    val deliverable: Boolean get() = cards.isNotEmpty()
}

class StudyFlowCompactCompanionProjector(
    private val staleAfterMillis: Long = DefaultCompactCompanionStaleAfterMillis,
    private val maxCompanions: Int = DefaultCompactCompanionLimit,
) {
    init {
        require(staleAfterMillis > 0L) { "Compact companion stale threshold must be positive" }
        require(maxCompanions in 1..8) { "Compact companion projection limit must be 1..8" }
    }

    fun project(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        companions: List<StudyFlowCompactCompanionProfile>,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
    ): StudyFlowCompactAttentionProjection {
        require(companions.distinctBy(StudyFlowCompactCompanionProfile::companionId).size == companions.size) {
            "Compact companion profiles must be unique"
        }
        require(nowEpochMillis >= 0L) { "Compact companion projection time must not be negative" }
        val handled = ledgerEntry?.state?.suppressesCompactProjection() == true
        if (!route.deliverable || handled) {
            return noCards(
                occurrence = occurrence,
                route = route,
                companions = companions,
                explanation = if (handled) {
                    "ATT01 ledger already handled this occurrence; compact companions suppress replay."
                } else {
                    "No parent device owns the route, so compact companions cannot independently deliver."
                },
            )
        }
        val targetDevices = route.targetDeviceIds.toSet()
        val eligible = companions
            .filter { it.parentDeviceId in targetDevices }
            .filter { it.isAvailable(nowEpochMillis, staleAfterMillis) }
            .sortedWith(compareBy<StudyFlowCompactCompanionProfile> { it.parentDeviceId.value }
                .thenBy { it.companionId.value })
        val cards = eligible.take(maxCompanions).map { profile ->
            profile.toCard(occurrence, route)
        }
        return StudyFlowCompactAttentionProjection(
            occurrenceId = occurrence.occurrenceId,
            ownerDeviceId = route.owner?.deviceId,
            cards = cards,
            diagnostics = StudyFlowCompactCompanionDiagnostics(
                candidateCount = companions.size,
                eligibleCount = eligible.size,
                projectedCount = cards.size,
                suppressedCompanionIds = companions
                    .filterNot { profile -> eligible.any { it.companionId == profile.companionId } }
                    .map(StudyFlowCompactCompanionProfile::companionId),
                parentRouteDeliverable = route.deliverable,
                explanation = "Compact companions mirror ATT07 parent ownership; they never become owners.",
            ),
        )
    }

    private fun noCards(
        occurrence: StudyFlowAttentionOccurrence,
        route: StudyFlowAttentionDeliveryRoute,
        companions: List<StudyFlowCompactCompanionProfile>,
        explanation: String,
    ): StudyFlowCompactAttentionProjection = StudyFlowCompactAttentionProjection(
        occurrenceId = occurrence.occurrenceId,
        ownerDeviceId = route.owner?.deviceId,
        cards = emptyList(),
        diagnostics = StudyFlowCompactCompanionDiagnostics(
            candidateCount = companions.size,
            eligibleCount = 0,
            projectedCount = 0,
            suppressedCompanionIds = companions.map(StudyFlowCompactCompanionProfile::companionId),
            parentRouteDeliverable = route.deliverable,
            explanation = explanation,
        ),
    )
}

fun StudyFlowCompactCompanionProfile.toCard(
    occurrence: StudyFlowAttentionOccurrence,
    route: StudyFlowAttentionDeliveryRoute,
): StudyFlowCompactAttentionCard {
    val role = when (parentDeviceId) {
        route.owner?.deviceId -> StudyFlowAttentionDeliveryRole.OWNER
        else -> route.mirrors.firstOrNull { it.deviceId == parentDeviceId }?.role
            ?: StudyFlowAttentionDeliveryRole.MIRROR
    }
    val text = occurrence.compactText(canShowSensitiveText)
    val actions = occurrence.compactActions(maxActions, canHandoff)
    return StudyFlowCompactAttentionCard(
        companionId = companionId,
        companionDisplayName = displayName,
        parentDeviceId = parentDeviceId,
        occurrenceId = occurrence.occurrenceId,
        category = occurrence.category,
        priority = occurrence.priority,
        title = text.first,
        body = text.second,
        scheduledAtEpochMillis = occurrence.scheduledAtEpochMillis,
        renderMode = renderModeFor(formFactor, role),
        parentRouteRole = role,
        ownerDeviceId = route.owner?.deviceId,
        actions = actions,
        sensitiveTextHidden = !canShowSensitiveText,
        hapticHint = canVibrate && occurrence.priority != StudyFlowAttentionPriority.LOW,
    )
}

private fun renderModeFor(
    formFactor: StudyFlowCompactCompanionFormFactor,
    role: StudyFlowAttentionDeliveryRole,
): StudyFlowCompactAttentionRenderMode = when (formFactor) {
    StudyFlowCompactCompanionFormFactor.QUICK_TILE -> StudyFlowCompactAttentionRenderMode.QUICK_ACTION
    StudyFlowCompactCompanionFormFactor.NOTIFICATION_SHADE -> StudyFlowCompactAttentionRenderMode.MIRROR_ONLY
    StudyFlowCompactCompanionFormFactor.WEARABLE,
    StudyFlowCompactCompanionFormFactor.LOCK_SCREEN,
    StudyFlowCompactCompanionFormFactor.DESKTOP_MINI,
    StudyFlowCompactCompanionFormFactor.WEB_COMPACT -> if (role == StudyFlowAttentionDeliveryRole.OWNER) {
        StudyFlowCompactAttentionRenderMode.GLANCE
    } else {
        StudyFlowCompactAttentionRenderMode.MIRROR_ONLY
    }
}

private fun StudyFlowAttentionOccurrence.compactText(showSensitive: Boolean): Pair<String, String> {
    val title = payload["title"]?.toString()?.trim('"')?.takeIf(String::isNotBlank)
    val body = payload["body"]?.toString()?.trim('"')?.takeIf(String::isNotBlank)
    if (!showSensitive) {
        return category.defaultCompactTitle() to category.defaultCompactBody()
    }
    return (title ?: category.defaultCompactTitle()) to (body ?: category.defaultCompactBody())
}

private fun StudyFlowAttentionCategory.defaultCompactTitle(): String = when (this) {
    StudyFlowAttentionCategory.PLANNED_STUDY -> "Study soon"
    StudyFlowAttentionCategory.DUE_REVIEW -> "Review due"
    StudyFlowAttentionCategory.MISSED_ACTIVITY -> "Catch up"
    StudyFlowAttentionCategory.DAILY_SUMMARY -> "Today"
    StudyFlowAttentionCategory.SESSION_ENDING -> "Timer ending"
    StudyFlowAttentionCategory.SESSION_COMPLETED -> "Session done"
    StudyFlowAttentionCategory.EXAM_COUNTDOWN -> "Exam countdown"
    StudyFlowAttentionCategory.SCHEDULE_CHANGE -> "Schedule changed"
    StudyFlowAttentionCategory.PLAN_WARNING -> "Plan warning"
    StudyFlowAttentionCategory.SYNC_ATTENTION -> "StudyFlow update"
}

private fun StudyFlowAttentionCategory.defaultCompactBody(): String = when (this) {
    StudyFlowAttentionCategory.PLANNED_STUDY -> "Start from your phone or paired device."
    StudyFlowAttentionCategory.DUE_REVIEW -> "A review is ready."
    StudyFlowAttentionCategory.MISSED_ACTIVITY -> "A study block needs attention."
    StudyFlowAttentionCategory.DAILY_SUMMARY -> "Open your daily plan."
    StudyFlowAttentionCategory.SESSION_ENDING -> "Your session is almost complete."
    StudyFlowAttentionCategory.SESSION_COMPLETED -> "Choose a next step."
    StudyFlowAttentionCategory.EXAM_COUNTDOWN -> "Check your remaining plan."
    StudyFlowAttentionCategory.SCHEDULE_CHANGE -> "Review the updated schedule."
    StudyFlowAttentionCategory.PLAN_WARNING -> "Open StudyFlow to fix it."
    StudyFlowAttentionCategory.SYNC_ATTENTION -> "A connected device sent an update."
}

private fun StudyFlowAttentionOccurrence.compactActions(
    limit: Int,
    canHandoff: Boolean,
): List<StudyFlowCompactAttentionAction> {
    val explicit = actions.map(StudyFlowAttentionAction::kind).takeIf { it.isNotEmpty() }
    val defaults = when (category) {
        StudyFlowAttentionCategory.DUE_REVIEW -> listOf(
            StudyFlowAttentionActionKind.REVIEW_NOW,
            StudyFlowAttentionActionKind.SNOOZE,
            StudyFlowAttentionActionKind.MOVE_TO_DEVICE,
        )
        StudyFlowAttentionCategory.PLANNED_STUDY,
        StudyFlowAttentionCategory.MISSED_ACTIVITY -> listOf(
            StudyFlowAttentionActionKind.START_NOW,
            StudyFlowAttentionActionKind.SNOOZE,
            StudyFlowAttentionActionKind.MOVE_TO_DEVICE,
        )
        StudyFlowAttentionCategory.SESSION_ENDING,
        StudyFlowAttentionCategory.SESSION_COMPLETED -> listOf(
            StudyFlowAttentionActionKind.CONTINUE,
            StudyFlowAttentionActionKind.SNOOZE,
            StudyFlowAttentionActionKind.MOVE_TO_DEVICE,
        )
        else -> listOf(
            StudyFlowAttentionActionKind.OPEN,
            StudyFlowAttentionActionKind.DISMISS,
            StudyFlowAttentionActionKind.MOVE_TO_DEVICE,
        )
    }
    val source = (explicit ?: defaults)
        .filter { canHandoff || it != StudyFlowAttentionActionKind.MOVE_TO_DEVICE }
        .distinct()
    return source.take(limit).map { kind ->
        StudyFlowCompactAttentionAction(
            kind = kind,
            label = kind.compactLabel(),
            destructive = kind == StudyFlowAttentionActionKind.DISMISS,
            requiresParentDevice = true,
        )
    }
}

private fun StudyFlowAttentionActionKind.compactLabel(): String = when (this) {
    StudyFlowAttentionActionKind.OPEN -> "Open"
    StudyFlowAttentionActionKind.START_NOW -> "Start"
    StudyFlowAttentionActionKind.REVIEW_NOW -> "Review"
    StudyFlowAttentionActionKind.SNOOZE -> "Snooze"
    StudyFlowAttentionActionKind.RESCHEDULE -> "Move"
    StudyFlowAttentionActionKind.MARK_DONE -> "Done"
    StudyFlowAttentionActionKind.DISMISS -> "Dismiss"
    StudyFlowAttentionActionKind.CONTINUE -> "Continue"
    StudyFlowAttentionActionKind.MOVE_TO_DEVICE -> "Phone"
}

private fun StudyFlowAttentionDeliveryState.suppressesCompactProjection(): Boolean = when (this) {
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
