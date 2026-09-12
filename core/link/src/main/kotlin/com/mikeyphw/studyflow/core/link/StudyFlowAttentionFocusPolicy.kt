package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private const val DefaultQuietStartMinute = 22 * 60
private const val DefaultQuietEndMinute = 7 * 60
private const val DefaultMirrorEscalationDelayMinutes = 15

@Serializable
enum class StudyFlowAttentionFocusProfile {
    @SerialName("balanced") BALANCED,
    @SerialName("exam_mode") EXAM_MODE,
    @SerialName("work_mode") WORK_MODE,
    @SerialName("sleep_mode") SLEEP_MODE,
    @SerialName("travel_offline") TRAVEL_OFFLINE,
    @SerialName("minimal_tv") MINIMAL_TV,
}

@Serializable
enum class StudyFlowAttentionPolicyOutcome {
    @SerialName("deliver_now") DELIVER_NOW,
    @SerialName("defer_until_quiet_end") DEFER_UNTIL_QUIET_END,
    @SerialName("silent_delivery") SILENT_DELIVERY,
    @SerialName("compact_only") COMPACT_ONLY,
    @SerialName("route_to_phone") ROUTE_TO_PHONE,
    @SerialName("escalate_mirror") ESCALATE_MIRROR,
    @SerialName("suppress_duplicate") SUPPRESS_DUPLICATE,
    @SerialName("require_user_action") REQUIRE_USER_ACTION,
}

@Serializable
enum class StudyFlowAttentionEscalationMode {
    @SerialName("normal") NORMAL,
    @SerialName("silent") SILENT,
    @SerialName("defer") DEFER,
    @SerialName("compact_only") COMPACT_ONLY,
    @SerialName("route_to_phone") ROUTE_TO_PHONE,
    @SerialName("mirror_if_unhandled") MIRROR_IF_UNHANDLED,
    @SerialName("require_user_action") REQUIRE_USER_ACTION,
}

@Serializable
data class StudyFlowAttentionQuietWindow(
    val enabled: Boolean = true,
    val startMinute: Int = DefaultQuietStartMinute,
    val endMinute: Int = DefaultQuietEndMinute,
    val timezoneId: String? = null,
) {
    init {
        require(startMinute in 0..1439) { "Quiet-window start minute out of range" }
        require(endMinute in 0..1439) { "Quiet-window end minute out of range" }
        require(startMinute != endMinute) { "Quiet-window start and end must differ" }
        require(timezoneId == null || timezoneId.isNotBlank()) { "Quiet-window timezone must not be blank" }
    }

    fun contains(epochMillis: Long, fallbackTimezoneId: String? = null): Boolean {
        if (!enabled) return false
        val zone = zone(fallbackTimezoneId)
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val minute = local.hour * 60 + local.minute
        return if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            minute >= startMinute || minute < endMinute
        }
    }

    fun nextEndEpochMillis(epochMillis: Long, fallbackTimezoneId: String? = null): Long {
        val zone = zone(fallbackTimezoneId)
        val now = Instant.ofEpochMilli(epochMillis).atZone(zone)
        if (!contains(epochMillis, fallbackTimezoneId)) return epochMillis
        val endTime = LocalTime.of(endMinute / 60, endMinute % 60)
        val endDate = if (startMinute >= endMinute && now.hour * 60 + now.minute >= startMinute) {
            now.toLocalDate().plusDays(1)
        } else {
            now.toLocalDate()
        }
        // Resolve the requested local wall-clock end through ZoneRules so DST gaps/overlaps
        // preserve the configured local quiet-hours boundary rather than adding elapsed minutes
        // from local midnight.
        return endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
    }

    private fun zone(fallbackTimezoneId: String?): ZoneId = sequenceOf(timezoneId, fallbackTimezoneId, "UTC")
        .filterNotNull()
        .mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
        .first()
}

@Serializable
data class StudyFlowAttentionCategoryEscalationPolicy(
    val category: StudyFlowAttentionCategory,
    val mode: StudyFlowAttentionEscalationMode = StudyFlowAttentionEscalationMode.NORMAL,
    val bypassQuietHours: Boolean = false,
    val mirrorAfterMinutes: Int = DefaultMirrorEscalationDelayMinutes,
) {
    init {
        require(mirrorAfterMinutes in 0..720) { "Attention mirror escalation delay must be 0..720 minutes" }
    }
}

@Serializable
data class StudyFlowAttentionFocusPolicy(
    val version: Int = 1,
    val enabled: Boolean = true,
    val profile: StudyFlowAttentionFocusProfile = StudyFlowAttentionFocusProfile.BALANCED,
    val quietWindow: StudyFlowAttentionQuietWindow? = null,
    val categoryPolicies: List<StudyFlowAttentionCategoryEscalationPolicy> = defaultAttentionEscalationPolicies(),
    val allowHighPriorityQuietBypass: Boolean = true,
    val allowTimeSensitiveQuietBypass: Boolean = true,
    val maxEscalationMirrors: Int = 1,
) {
    init {
        require(version >= 1) { "Attention focus-policy version must be positive" }
        require(categoryPolicies.distinctBy { it.category }.size == categoryPolicies.size) {
            "Attention focus category policies must be unique"
        }
        require(maxEscalationMirrors in 0..3) { "Attention escalation mirror limit must be 0..3" }
    }

    fun categoryPolicy(category: StudyFlowAttentionCategory): StudyFlowAttentionCategoryEscalationPolicy =
        categoryPolicies.firstOrNull { it.category == category }
            ?: StudyFlowAttentionCategoryEscalationPolicy(category)
}

@Serializable
data class StudyFlowAttentionPolicyDecision(
    val occurrenceId: StudyFlowAttentionOccurrenceId,
    val profile: StudyFlowAttentionFocusProfile,
    val outcome: StudyFlowAttentionPolicyOutcome,
    val evaluatedAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long? = null,
    val quietHoursActive: Boolean = false,
    val deliverSilently: Boolean = false,
    val compactOnly: Boolean = false,
    val preferPhone: Boolean = false,
    val requestedMirrorCount: Int = 0,
    val requiresUserAction: Boolean = false,
    val explanation: String,
) {
    init {
        require(evaluatedAtEpochMillis >= 0L) { "Attention policy evaluation time must not be negative" }
        require(deferredUntilEpochMillis == null || deferredUntilEpochMillis >= evaluatedAtEpochMillis) {
            "Attention policy deferral must not be in the past"
        }
        require(requestedMirrorCount in 0..3) { "Attention requested mirror count must be 0..3" }
        require(explanation.isNotBlank()) { "Attention policy decision explanation must not be blank" }
    }
}

@Serializable
data class StudyFlowAttentionPolicyAudit(
    val profile: StudyFlowAttentionFocusProfile,
    val outcome: StudyFlowAttentionPolicyOutcome,
    val evaluatedAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long? = null,
    val explanation: String,
)

object StudyFlowAttentionFocusPolicyEngine {
    fun evaluate(
        occurrence: StudyFlowAttentionOccurrence,
        policy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        ledgerEntry: StudyFlowAttentionLedgerEntry? = null,
        localClientKind: StudyFlowLinkClientKind? = null,
    ): StudyFlowAttentionPolicyDecision {
        require(nowEpochMillis >= 0L) { "Attention policy evaluation time must not be negative" }
        if (ledgerEntry?.state?.att10SuppressesRedelivery() == true) {
            return decision(occurrence, policy, nowEpochMillis, StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE,
                explanation = "ATT01 ledger already records this occurrence as handled.")
        }
        if (!policy.enabled) {
            return decision(occurrence, policy, nowEpochMillis, StudyFlowAttentionPolicyOutcome.DELIVER_NOW,
                explanation = "ATT10 focus policy is disabled; existing ATT01/ATT07 behavior is preserved.")
        }

        val categoryPolicy = effectiveCategoryPolicy(policy, occurrence.category)
        val quiet = policy.quietWindow?.takeIf { it.enabled }
        val inQuiet = quiet?.contains(nowEpochMillis, occurrence.timezoneId) == true
        val quietBypass = categoryPolicy.bypassQuietHours ||
            (policy.allowTimeSensitiveQuietBypass && occurrence.priority == StudyFlowAttentionPriority.TIME_SENSITIVE) ||
            (policy.allowHighPriorityQuietBypass && occurrence.priority == StudyFlowAttentionPriority.HIGH)

        if (inQuiet && !quietBypass) {
            val activeQuiet = requireNotNull(quiet)
            val quietOutcome = when (policy.profile) {
                StudyFlowAttentionFocusProfile.SLEEP_MODE -> StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END
                StudyFlowAttentionFocusProfile.MINIMAL_TV -> StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE
                StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE -> StudyFlowAttentionPolicyOutcome.COMPACT_ONLY
                else -> when (categoryPolicy.mode) {
                    StudyFlowAttentionEscalationMode.SILENT -> StudyFlowAttentionPolicyOutcome.SILENT_DELIVERY
                    StudyFlowAttentionEscalationMode.COMPACT_ONLY -> StudyFlowAttentionPolicyOutcome.COMPACT_ONLY
                    StudyFlowAttentionEscalationMode.ROUTE_TO_PHONE -> StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE
                    StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION -> StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION
                    else -> StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END
                }
            }
            val deferredUntil = if (quietOutcome == StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END) {
                activeQuiet.nextEndEpochMillis(nowEpochMillis, occurrence.timezoneId)
            } else null
            return decision(
                occurrence, policy, nowEpochMillis, quietOutcome,
                deferredUntil = deferredUntil,
                quietHoursActive = true,
                explanation = "Quiet hours are active in ${activeQuiet.timezoneId ?: occurrence.timezoneId ?: "UTC"}; ATT10 applied ${quietOutcome.name.lowercase()}.",
            )
        }

        val outcome = when {
            policy.profile == StudyFlowAttentionFocusProfile.MINIMAL_TV && localClientKind in setOf(
                StudyFlowLinkClientKind.ANDROID_TV,
                StudyFlowLinkClientKind.WEB_TV,
            ) ->
                StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE
            policy.profile == StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE ->
                StudyFlowAttentionPolicyOutcome.COMPACT_ONLY
            policy.profile == StudyFlowAttentionFocusProfile.EXAM_MODE && occurrence.category == StudyFlowAttentionCategory.EXAM_COUNTDOWN ->
                StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.SILENT -> StudyFlowAttentionPolicyOutcome.SILENT_DELIVERY
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.COMPACT_ONLY -> StudyFlowAttentionPolicyOutcome.COMPACT_ONLY
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.ROUTE_TO_PHONE -> StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED &&
                nowEpochMillis >= occurrence.originalScheduledAtEpochMillis + categoryPolicy.mirrorAfterMinutes * 60_000L ->
                StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION -> StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION
            categoryPolicy.mode == StudyFlowAttentionEscalationMode.DEFER -> StudyFlowAttentionPolicyOutcome.DELIVER_NOW
            else -> StudyFlowAttentionPolicyOutcome.DELIVER_NOW
        }
        return decision(
            occurrence, policy, nowEpochMillis, outcome,
            explanation = "ATT10 ${policy.profile.name.lowercase()} profile evaluated ${occurrence.category.name.lowercase()} as ${outcome.name.lowercase()}.",
        )
    }

    private fun effectiveCategoryPolicy(
        policy: StudyFlowAttentionFocusPolicy,
        category: StudyFlowAttentionCategory,
    ): StudyFlowAttentionCategoryEscalationPolicy {
        val base = policy.categoryPolicy(category)
        return when (policy.profile) {
            StudyFlowAttentionFocusProfile.EXAM_MODE -> when (category) {
                StudyFlowAttentionCategory.EXAM_COUNTDOWN, StudyFlowAttentionCategory.DUE_REVIEW,
                StudyFlowAttentionCategory.PLANNED_STUDY -> base.copy(
                    mode = if (category == StudyFlowAttentionCategory.EXAM_COUNTDOWN) StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION else StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED,
                    bypassQuietHours = category == StudyFlowAttentionCategory.EXAM_COUNTDOWN,
                )
                else -> base
            }
            StudyFlowAttentionFocusProfile.WORK_MODE -> if (category in setOf(
                    StudyFlowAttentionCategory.DAILY_SUMMARY,
                    StudyFlowAttentionCategory.SCHEDULE_CHANGE,
                    StudyFlowAttentionCategory.PLAN_WARNING,
                )) base.copy(mode = StudyFlowAttentionEscalationMode.SILENT) else base
            StudyFlowAttentionFocusProfile.SLEEP_MODE -> base.copy(mode = StudyFlowAttentionEscalationMode.DEFER)
            StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE -> base.copy(mode = StudyFlowAttentionEscalationMode.COMPACT_ONLY)
            StudyFlowAttentionFocusProfile.MINIMAL_TV -> base.copy(mode = StudyFlowAttentionEscalationMode.ROUTE_TO_PHONE)
            StudyFlowAttentionFocusProfile.BALANCED -> base
        }
    }

    private fun decision(
        occurrence: StudyFlowAttentionOccurrence,
        policy: StudyFlowAttentionFocusPolicy,
        nowEpochMillis: Long,
        outcome: StudyFlowAttentionPolicyOutcome,
        deferredUntil: Long? = null,
        quietHoursActive: Boolean = false,
        explanation: String,
    ) = StudyFlowAttentionPolicyDecision(
        occurrenceId = occurrence.occurrenceId,
        profile = policy.profile,
        outcome = outcome,
        evaluatedAtEpochMillis = nowEpochMillis,
        deferredUntilEpochMillis = deferredUntil,
        quietHoursActive = quietHoursActive,
        deliverSilently = outcome == StudyFlowAttentionPolicyOutcome.SILENT_DELIVERY,
        compactOnly = outcome == StudyFlowAttentionPolicyOutcome.COMPACT_ONLY,
        preferPhone = outcome == StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE,
        requestedMirrorCount = if (outcome == StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR) policy.maxEscalationMirrors else 0,
        requiresUserAction = outcome == StudyFlowAttentionPolicyOutcome.REQUIRE_USER_ACTION,
        explanation = explanation,
    )
}

fun defaultAttentionEscalationPolicies(): List<StudyFlowAttentionCategoryEscalationPolicy> = listOf(
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.PLANNED_STUDY, StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED, mirrorAfterMinutes = 15),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.DUE_REVIEW, StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED, mirrorAfterMinutes = 30),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.MISSED_ACTIVITY, StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED, mirrorAfterMinutes = 20),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.EXAM_COUNTDOWN, StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION, bypassQuietHours = true),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.DAILY_SUMMARY, StudyFlowAttentionEscalationMode.SILENT),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.SESSION_ENDING, StudyFlowAttentionEscalationMode.NORMAL, bypassQuietHours = true),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.SESSION_COMPLETED, StudyFlowAttentionEscalationMode.NORMAL, bypassQuietHours = true),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.SCHEDULE_CHANGE, StudyFlowAttentionEscalationMode.SILENT),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.PLAN_WARNING, StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION),
    StudyFlowAttentionCategoryEscalationPolicy(StudyFlowAttentionCategory.SYNC_ATTENTION, StudyFlowAttentionEscalationMode.COMPACT_ONLY),
)

internal fun StudyFlowAttentionDeliveryState.att10SuppressesRedelivery(): Boolean = when (this) {
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
