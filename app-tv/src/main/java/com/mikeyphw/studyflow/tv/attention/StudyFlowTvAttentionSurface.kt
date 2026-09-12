package com.mikeyphw.studyflow.tv.attention

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionActionKind
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryState
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlCommand
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvSurface
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusItem
import com.mikeyphw.studyflow.tv.focus.StudyFlowTvFocusRole
import com.mikeyphw.studyflow.tv.navigation.StudyFlowTvDestination

/**
 * ATT06 foreground-only attention model for TV surfaces.
 *
 * Android TV does not pretend to be a background alarm hub. It shows non-blocking, D-pad safe
 * foreground banners while the TV app is open, keeps all acknowledgement state in the ATT01 ledger,
 * and asks a phone/desktop/Web notifier to handle closed-TV delivery in later routing overlays.
 */
enum class StudyFlowTvAttentionAction {
    OPEN,
    START_NOW,
    REVIEW_NOW,
    CONTINUE,
    SNOOZE,
    DISMISS,
    MOVE_TO_PHONE,
}

data class StudyFlowTvAttentionCapabilityState(
    val foregroundBanners: Boolean = true,
    val systemNotifications: Boolean = false,
    val backgroundAlarms: Boolean = false,
    val tvHomeRecommendations: Boolean = false,
    val closedAppDelivery: Boolean = false,
    val requiresDesignatedNotifierFallback: Boolean = true,
) {
    init {
        require(foregroundBanners) { "ATT06 TV attention must provide foreground banners." }
        require(!systemNotifications) { "TV system notifications stay disabled by default in ATT06." }
        require(!backgroundAlarms) { "TV background alarms are intentionally not claimed in ATT06." }
    }
}

data class StudyFlowTvAttentionBanner(
    val occurrenceId: String,
    val category: StudyFlowAttentionCategory,
    val title: String,
    val body: String,
    val route: StudyFlowTvDestination,
    val actions: List<StudyFlowTvAttentionAction>,
    val deliveredAtEpochMillis: Long,
    val sourceDeviceId: StudyFlowDeviceId? = null,
) {
    init {
        require(occurrenceId.isNotBlank()) { "TV attention banner occurrence ID must not be blank." }
        require(title.isNotBlank()) { "TV attention banner title must not be blank." }
        require(body.isNotBlank()) { "TV attention banner body must not be blank." }
        require(actions.isNotEmpty()) { "TV attention banner must expose at least one D-pad action." }
        require(actions.distinct().size == actions.size) { "TV attention banner actions must be unique." }
        require(deliveredAtEpochMillis >= 0L) { "TV attention banner delivery time must not be negative." }
    }
}

data class StudyFlowTvAttentionState(
    val capability: StudyFlowTvAttentionCapabilityState = StudyFlowTvAttentionCapabilityState(),
    val activeBanner: StudyFlowTvAttentionBanner? = null,
    val lastAcknowledgedOccurrenceId: String? = null,
    val lastAction: StudyFlowTvAttentionAction? = null,
    val updatedAtEpochMillis: Long = 0L,
) {
    val hasForegroundBanner: Boolean get() = activeBanner != null
}

data class StudyFlowTvAttentionActionResult(
    val state: StudyFlowTvAttentionState,
    val command: StudyFlowTvControlCommand? = null,
    val acknowledgementState: StudyFlowAttentionDeliveryState,
    val handledOnTv: Boolean,
)

object StudyFlowTvAttentionSurface {
    const val overlay = "ATT06"
    const val foregroundOnly = true
    const val dpadSafeBannerActions = true
    const val backgroundDeliveryRequiresDesignatedNotifier = true
    const val doesNotClaimSystemNotifications = true
    const val doesNotClaimBackgroundAlarms = true
    const val androidTvHomeRecommendationsAreOptInOnly = true
    const val focusRow = 0
    const val focusColumnStart = 4
    const val maximumActions = 3

    fun bannerFor(delivery: StudyFlowNotificationDelivery, ack: StudyFlowNotificationDeliveryAck): StudyFlowTvAttentionBanner? {
        if (ack.status != StudyFlowNotificationDeliveryStatus.ACCEPTED && ack.status != StudyFlowNotificationDeliveryStatus.DUPLICATE) {
            return null
        }
        if (!delivery.notification.route.allowTvSurfaces) return null
        val occurrence = delivery.notification.canonicalAttentionOccurrence(delivery.sourceDeviceId)
        return bannerFor(occurrence, delivery.notification.title, delivery.notification.body, ack.acknowledgedAtEpochMillis)
    }

    fun bannerFor(
        occurrence: StudyFlowAttentionOccurrence,
        title: String = titleFor(occurrence),
        body: String = bodyFor(occurrence),
        deliveredAtEpochMillis: Long = occurrence.scheduledAtEpochMillis,
    ): StudyFlowTvAttentionBanner = StudyFlowTvAttentionBanner(
        occurrenceId = occurrence.occurrenceId.value,
        category = occurrence.category,
        title = title.ifBlank { titleFor(occurrence) },
        body = body.ifBlank { bodyFor(occurrence) },
        route = routeFor(occurrence.category),
        actions = actionListFor(occurrence),
        deliveredAtEpochMillis = deliveredAtEpochMillis,
        sourceDeviceId = occurrence.sourceDeviceId,
    )

    fun present(state: StudyFlowTvAttentionState, banner: StudyFlowTvAttentionBanner): StudyFlowTvAttentionState =
        state.copy(activeBanner = banner, updatedAtEpochMillis = banner.deliveredAtEpochMillis)

    fun reduce(
        state: StudyFlowTvAttentionState,
        action: StudyFlowTvAttentionAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvAttentionActionResult {
        val banner = state.activeBanner
        val nextState = state.copy(
            activeBanner = if (action == StudyFlowTvAttentionAction.SNOOZE) banner else null,
            lastAcknowledgedOccurrenceId = banner?.occurrenceId ?: state.lastAcknowledgedOccurrenceId,
            lastAction = action,
            updatedAtEpochMillis = issuedAtEpochMillis,
        )
        val command = banner?.let { commandForAction(it, action, commandId, issuedAtEpochMillis) }
        val acknowledgement = when (action) {
            StudyFlowTvAttentionAction.OPEN,
            StudyFlowTvAttentionAction.CONTINUE,
            -> StudyFlowAttentionDeliveryState.ACKNOWLEDGED
            StudyFlowTvAttentionAction.START_NOW,
            StudyFlowTvAttentionAction.REVIEW_NOW,
            -> StudyFlowAttentionDeliveryState.STARTED
            StudyFlowTvAttentionAction.SNOOZE -> StudyFlowAttentionDeliveryState.SNOOZED
            StudyFlowTvAttentionAction.DISMISS -> StudyFlowAttentionDeliveryState.DISMISSED
            StudyFlowTvAttentionAction.MOVE_TO_PHONE -> StudyFlowAttentionDeliveryState.ACKNOWLEDGED
        }
        return StudyFlowTvAttentionActionResult(
            state = nextState,
            command = command,
            acknowledgementState = acknowledgement,
            handledOnTv = action != StudyFlowTvAttentionAction.MOVE_TO_PHONE,
        )
    }

    fun focusItems(state: StudyFlowTvAttentionState): List<StudyFlowTvFocusItem> =
        state.activeBanner?.actions.orEmpty().mapIndexed { index, action ->
            StudyFlowTvFocusItem(
                id = "attention-action-${action.name.lowercase()}",
                role = StudyFlowTvFocusRole.PRIMARY_ACTION,
                row = focusRow,
                column = focusColumnStart + index,
            )
        }

    fun capabilitySummary(state: StudyFlowTvAttentionCapabilityState = StudyFlowTvAttentionCapabilityState()): String =
        "foreground-banners=${state.foregroundBanners}; system-notifications=${state.systemNotifications}; " +
            "background-alarms=${state.backgroundAlarms}; closed-app-delivery=${state.closedAppDelivery}; " +
            "fallback-designated-notifier=${state.requiresDesignatedNotifierFallback}"

    private fun commandForAction(
        banner: StudyFlowTvAttentionBanner,
        action: StudyFlowTvAttentionAction,
        commandId: String,
        issuedAtEpochMillis: Long,
    ): StudyFlowTvControlCommand = StudyFlowTvControlCommand(
        commandId = commandId,
        surface = surfaceFor(banner.route),
        action = when (action) {
            StudyFlowTvAttentionAction.START_NOW,
            StudyFlowTvAttentionAction.REVIEW_NOW,
            StudyFlowTvAttentionAction.CONTINUE,
            StudyFlowTvAttentionAction.OPEN,
            -> StudyFlowTvControlAction.OPEN
            StudyFlowTvAttentionAction.SNOOZE,
            StudyFlowTvAttentionAction.DISMISS,
            StudyFlowTvAttentionAction.MOVE_TO_PHONE,
            -> StudyFlowTvControlAction.OPEN
        },
        targetId = banner.route.route,
        issuedAtEpochMillis = issuedAtEpochMillis,
    )

    private fun actionListFor(occurrence: StudyFlowAttentionOccurrence): List<StudyFlowTvAttentionAction> {
        val explicit = occurrence.actions.mapNotNull { action -> actionForKind(action.kind) }
        val fallback = when (occurrence.category) {
            StudyFlowAttentionCategory.PLANNED_STUDY -> listOf(StudyFlowTvAttentionAction.START_NOW, StudyFlowTvAttentionAction.SNOOZE, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
            StudyFlowAttentionCategory.DUE_REVIEW -> listOf(StudyFlowTvAttentionAction.REVIEW_NOW, StudyFlowTvAttentionAction.SNOOZE, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
            StudyFlowAttentionCategory.SESSION_ENDING -> listOf(StudyFlowTvAttentionAction.CONTINUE, StudyFlowTvAttentionAction.DISMISS, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
            StudyFlowAttentionCategory.SESSION_COMPLETED -> listOf(StudyFlowTvAttentionAction.CONTINUE, StudyFlowTvAttentionAction.DISMISS, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
            StudyFlowAttentionCategory.EXAM_COUNTDOWN -> listOf(StudyFlowTvAttentionAction.OPEN, StudyFlowTvAttentionAction.SNOOZE, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
            else -> listOf(StudyFlowTvAttentionAction.OPEN, StudyFlowTvAttentionAction.DISMISS, StudyFlowTvAttentionAction.MOVE_TO_PHONE)
        }
        return (explicit.ifEmpty { fallback }).distinct().take(maximumActions)
    }

    private fun actionForKind(kind: StudyFlowAttentionActionKind): StudyFlowTvAttentionAction? = when (kind) {
        StudyFlowAttentionActionKind.OPEN -> StudyFlowTvAttentionAction.OPEN
        StudyFlowAttentionActionKind.START_NOW -> StudyFlowTvAttentionAction.START_NOW
        StudyFlowAttentionActionKind.REVIEW_NOW -> StudyFlowTvAttentionAction.REVIEW_NOW
        StudyFlowAttentionActionKind.SNOOZE -> StudyFlowTvAttentionAction.SNOOZE
        StudyFlowAttentionActionKind.RESCHEDULE -> StudyFlowTvAttentionAction.MOVE_TO_PHONE
        StudyFlowAttentionActionKind.MARK_DONE -> StudyFlowTvAttentionAction.CONTINUE
        StudyFlowAttentionActionKind.DISMISS -> StudyFlowTvAttentionAction.DISMISS
        StudyFlowAttentionActionKind.CONTINUE -> StudyFlowTvAttentionAction.CONTINUE
        StudyFlowAttentionActionKind.MOVE_TO_DEVICE -> StudyFlowTvAttentionAction.MOVE_TO_PHONE
    }

    fun titleFor(occurrence: StudyFlowAttentionOccurrence): String = when (occurrence.category) {
        StudyFlowAttentionCategory.PLANNED_STUDY -> "Study block ready on TV"
        StudyFlowAttentionCategory.DUE_REVIEW -> "Review ready on TV"
        StudyFlowAttentionCategory.MISSED_ACTIVITY -> "Missed study block"
        StudyFlowAttentionCategory.DAILY_SUMMARY -> "Today in StudyFlow"
        StudyFlowAttentionCategory.SESSION_ENDING -> "Session ending soon"
        StudyFlowAttentionCategory.SESSION_COMPLETED -> "Session complete"
        StudyFlowAttentionCategory.EXAM_COUNTDOWN -> "Exam countdown"
        StudyFlowAttentionCategory.SCHEDULE_CHANGE -> "Schedule updated"
        StudyFlowAttentionCategory.PLAN_WARNING -> "Plan needs attention"
        StudyFlowAttentionCategory.SYNC_ATTENTION -> "StudyFlow update"
    }

    fun bodyFor(occurrence: StudyFlowAttentionOccurrence): String = when (occurrence.category) {
        StudyFlowAttentionCategory.PLANNED_STUDY -> "Open the current TV study surface or move this reminder to your phone."
        StudyFlowAttentionCategory.DUE_REVIEW -> "Use the TV review surface or move the action to a phone/desktop device."
        StudyFlowAttentionCategory.MISSED_ACTIVITY -> "This TV can show the reminder while open; closed-app recovery belongs to your designated notifier."
        StudyFlowAttentionCategory.DAILY_SUMMARY -> "Open Today to review the current plan on TV."
        StudyFlowAttentionCategory.SESSION_ENDING -> "Continue or dismiss without interrupting the TV session."
        StudyFlowAttentionCategory.SESSION_COMPLETED -> "Continue reviewing results or dismiss the foreground banner."
        StudyFlowAttentionCategory.EXAM_COUNTDOWN -> "Open progress on TV or let the phone handle background follow-up."
        StudyFlowAttentionCategory.SCHEDULE_CHANGE -> "Open the read-only schedule view for details."
        StudyFlowAttentionCategory.PLAN_WARNING -> "Open StudyFlow details on TV or continue on the phone."
        StudyFlowAttentionCategory.SYNC_ATTENTION -> "A paired StudyFlow device sent an attention update."
    }

    fun routeFor(category: StudyFlowAttentionCategory): StudyFlowTvDestination = when (category) {
        StudyFlowAttentionCategory.PLANNED_STUDY,
        StudyFlowAttentionCategory.SESSION_ENDING,
        StudyFlowAttentionCategory.SESSION_COMPLETED,
        -> StudyFlowTvDestination.STUDY_SESSION
        StudyFlowAttentionCategory.DUE_REVIEW -> StudyFlowTvDestination.REVIEWS
        StudyFlowAttentionCategory.EXAM_COUNTDOWN -> StudyFlowTvDestination.PROGRESS
        StudyFlowAttentionCategory.SCHEDULE_CHANGE -> StudyFlowTvDestination.SCHEDULE
        StudyFlowAttentionCategory.MISSED_ACTIVITY,
        StudyFlowAttentionCategory.DAILY_SUMMARY,
        StudyFlowAttentionCategory.PLAN_WARNING,
        StudyFlowAttentionCategory.SYNC_ATTENTION,
        -> StudyFlowTvDestination.TODAY
    }

    private fun surfaceFor(destination: StudyFlowTvDestination): StudyFlowTvSurface = when (destination) {
        StudyFlowTvDestination.HOME -> StudyFlowTvSurface.HOME
        StudyFlowTvDestination.TODAY -> StudyFlowTvSurface.TODAY
        StudyFlowTvDestination.STUDY_SESSION -> StudyFlowTvSurface.STUDY_SESSION
        StudyFlowTvDestination.PRACTICE -> StudyFlowTvSurface.QUESTION_SET
        StudyFlowTvDestination.MOCK_EXAM -> StudyFlowTvSurface.MOCK_EXAM
        StudyFlowTvDestination.REVIEWS -> StudyFlowTvSurface.REVIEW
        StudyFlowTvDestination.PROGRESS -> StudyFlowTvSurface.PROGRESS
        StudyFlowTvDestination.SCHEDULE -> StudyFlowTvSurface.SCHEDULE
        StudyFlowTvDestination.LIBRARY -> StudyFlowTvSurface.LIBRARY
        StudyFlowTvDestination.SETTINGS -> StudyFlowTvSurface.SETTINGS
    }

    fun kindForTv(notificationKind: StudyFlowNotificationKind): String = when (notificationKind) {
        StudyFlowNotificationKind.STUDY_DUE -> "foreground-planned-study"
        StudyFlowNotificationKind.REVIEW_DUE -> "foreground-due-review"
        StudyFlowNotificationKind.SESSION_RESUME -> "foreground-session"
        StudyFlowNotificationKind.SYNC_ATTENTION,
        StudyFlowNotificationKind.HANDOFF_READY,
        StudyFlowNotificationKind.MATERIAL_READY,
        -> "foreground-sync-attention"
    }
}
