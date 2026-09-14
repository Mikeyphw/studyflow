package com.mikeyphw.studyflow.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import com.mikeyphw.studyflow.link.AndroidStudyFlowLinkPeer
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mikeyphw.studyflow.R
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContent
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.ReminderPlatformRuntimeEvidence
import com.mikeyphw.studyflow.core.domain.StudyPracticeRepository
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlan
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.domain.StudyReviewRepository
import com.mikeyphw.studyflow.core.domain.StudyScheduleRepository
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.domain.StudySessionRepository
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionAction
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionActionKind
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryState
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPriority
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityAnalyzer
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionWorkspace
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarEventKind
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportFeed
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarExportPreferences
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationScheduleReducer
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationKind
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionNotificationDeliveryFactory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityEvidenceSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicyEngine
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPolicyOutcome
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.model.PlannedActivityId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySessionId
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MinimumFutureDelayMillis = 60_000L
private const val ReconciliationDeliverySlackMillis =
    MinimumFutureDelayMillis + StudyReminderPlanner.PlannedActivityDeliveryGraceMillis

const val StudyReminderAction = "com.mikeyphw.studyflow.action.STUDY_REMINDER"
const val StudyReminderUserAction = "com.mikeyphw.studyflow.action.STUDY_REMINDER_USER_ACTION"
const val StudyReminderExtraPlanId = "plan_id"
const val StudyReminderExtraType = "reminder_type"
const val StudyReminderExtraOccurrenceId = "reminder_occurrence_id"
const val StudyReminderExtraTargetId = "reminder_target_id"
const val StudyReminderExtraSessionId = "reminder_session_id"
const val StudyReminderExtraUserAction = "reminder_user_action"
const val StudyReminderExtraContentKind = "reminder_content_kind"
const val StudyReminderExtraContentCount = "reminder_content_count"
const val StudyReminderExtraUserLabel = "reminder_user_label"
const val StudyReminderExtraScheduledAt = "reminder_scheduled_at"
const val StudyReminderExtraFireAt = "reminder_fire_at"
const val StudyReminderExtraDelayedByQuietHours = "reminder_delayed_by_quiet_hours"

const val StudyReminderLegacyChannelId = "study_reminders"
const val StudyReminderScheduleChannelId = "study_schedule"
const val StudyReminderReviewsChannelId = "study_reviews"
const val StudyReminderSessionChannelId = "study_session_timers"
const val StudyReminderSummaryChannelId = "study_summaries"
/** Compatibility alias for pre-ATT02 diagnostics. */
const val StudyReminderChannelId = StudyReminderScheduleChannelId

enum class StudyReminderNotificationAction {
    START_NOW,
    SNOOZE,
    MARK_DONE,
    DISMISS,
    START_BREAK,
}

data class StudyReminderPlatformCapability(
    val notificationsAllowed: Boolean,
    val exactAlarmCapabilityAvailable: Boolean,
    val exactAlarmRequired: Boolean = false,
)

class StudyReminderDeliveryCoordinator(
    private val context: Context,
    private val studyPlanRepository: StudyPlanRepository,
    private val studyScheduleRepository: StudyScheduleRepository,
    private val studyReviewRepository: StudyReviewRepository,
    private val studyPracticeRepository: StudyPracticeRepository,
    private val studySessionRepository: StudySessionRepository,
    private val attentionLedger: StudyFlowAttentionLedger,
    private val linkPeerProvider: () -> AndroidStudyFlowLinkPeer? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val occurrenceStore: StudyReminderOccurrenceStore = StudyReminderOccurrenceStore(context),
    private val policyStore: AndroidStudyReminderPolicyStore = AndroidStudyReminderPolicyStore(context),
    private val focusPolicyStore: AndroidStudyFlowAttentionFocusPolicyStore =
        AndroidStudyFlowAttentionFocusPolicyStore(context),
    private val runtimeStatusStore: StudyReminderRuntimeStatusStore = StudyReminderRuntimeStatusStore(context),
) {
    private val appContext = context.applicationContext
    private val alarmManager = requireNotNull(appContext.getSystemService(AlarmManager::class.java))
    private val reviewManager = ManageStudyReview(studyReviewRepository, studyPracticeRepository, clock)
    private val sessionManager = ManageStudySession(
        repository = studySessionRepository,
        studyPlanRepository = studyPlanRepository,
        clock = clock,
        monotonicClockMillis = SystemClock::elapsedRealtime,
    )

    suspend fun reconcileAll(reason: String? = null) {
        createNotificationChannels()
        val plans = studyPlanRepository.plans.first()
        plans.forEach { plan -> reconcilePlanInternal(plan.id) }
        runtimeStatusStore.markReconciled(clock(), reason)
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    suspend fun reconcilePlan(planId: StudyPlanId, reason: String? = null) {
        reconcilePlanInternal(planId)
        runtimeStatusStore.markReconciled(clock(), reason)
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    suspend fun refreshLocalizedPresentation() {
        createNotificationChannels()
        reconcileAll("locale-refresh")
    }

    suspend fun policyFor(planId: StudyPlanId): StudyReminderPolicy {
        val settings = studyPlanRepository.observeStoredPlanSettings(planId).first()
            ?: error("Study Plan settings must exist before reminder policy is loaded.")
        return policyStore.load(planId, settings)
    }

    suspend fun updatePolicy(planId: StudyPlanId, policy: StudyReminderPolicy) {
        policyStore.save(planId, policy)
        reconcilePlan(planId, "policy-updated")
    }

    fun focusPolicyFor(planId: StudyPlanId, settings: StudyPlanSettings): StudyFlowAttentionFocusPolicy =
        focusPolicyStore.load(planId, settings.quietHoursStartMinute, settings.quietHoursEndMinute)

    suspend fun updateFocusPolicy(planId: StudyPlanId, policy: StudyFlowAttentionFocusPolicy) {
        focusPolicyStore.save(planId, policy)
        reconcilePlan(planId, "attention-focus-policy-updated")
    }

    /** User-initiated ATT09 export built from the canonical StudySchedule, never a shadow reminder store. */
    suspend fun buildCalendarExport(
        planId: StudyPlanId,
        preferences: StudyFlowCalendarExportPreferences = StudyFlowCalendarExportPreferences(),
    ): StudyFlowCalendarExportFeed {
        val workspaceId = StudyFlowAttentionWorkspace.forPlanId(planId.value)
        val items = studyScheduleRepository.observeSchedule(planId).first().plannedActivities.mapNotNull { activity ->
            val start = activity.scheduledStartEpochMillis ?: return@mapNotNull null
            val kind = when (activity.type) {
                StudyActivityType.REVIEW, StudyActivityType.FLASHCARDS -> StudyFlowCalendarEventKind.REVIEW
                StudyActivityType.MOCK_EXAM -> StudyFlowCalendarEventKind.MOCK_EXAM
                else -> StudyFlowCalendarEventKind.STUDY_BLOCK
            }
            StudyFlowCalendarScheduleItem(
                itemId = StudyFlowEntityId(
                    StudyFlowAttentionOccurrenceId.deterministic("calendar-item", activity.id.value).value,
                ),
                workspaceId = workspaceId,
                title = activity.title,
                body = "StudyFlow ${activity.type.name.lowercase().replace('_', ' ')}",
                kind = kind,
                startEpochMillis = start,
                durationMinutes = activity.plannedDurationMinutes,
                timezoneId = TimeZone.getDefault().id,
                completed = activity.status in setOf(
                    PlannedActivityStatus.COMPLETED,
                    PlannedActivityStatus.SKIPPED,
                    PlannedActivityStatus.CANCELLED,
                ),
                routeHash = "#schedule/${activity.id.value}",
                attentionCategory = when (kind) {
                    StudyFlowCalendarEventKind.REVIEW -> StudyFlowAttentionCategory.DUE_REVIEW
                    StudyFlowCalendarEventKind.MOCK_EXAM,
                    StudyFlowCalendarEventKind.EXAM_DATE -> StudyFlowAttentionCategory.EXAM_COUNTDOWN
                    else -> StudyFlowAttentionCategory.PLANNED_STUDY
                },
                attentionPriority = if (kind == StudyFlowCalendarEventKind.MOCK_EXAM) {
                    StudyFlowAttentionPriority.HIGH
                } else {
                    StudyFlowAttentionPriority.NORMAL
                },
                reminderLeadMinutes = 10,
            )
        }
        return AndroidStudyFlowCalendarAttentionBridge().buildExport(items, clock(), preferences)
    }

    fun runtimeStatus(): StudyReminderRuntimeStatus = runtimeStatusStore.snapshot()

    fun runtimeEvidence(): ReminderPlatformRuntimeEvidence {
        createNotificationChannels()
        val status = runtimeStatusStore.snapshot()
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channelIds = listOf(
            StudyReminderScheduleChannelId,
            StudyReminderReviewsChannelId,
            StudyReminderSessionChannelId,
            StudyReminderSummaryChannelId,
        )
        val channelCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelIds.count { manager?.getNotificationChannel(it) != null }
        } else {
            channelIds.size
        }
        val blocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelIds.count { id ->
                manager?.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
            }
        } else {
            0
        }
        val capability = platformCapability()
        return ReminderPlatformRuntimeEvidence(
            notificationChannelCount = channelCount,
            blockedNotificationChannelCount = blocked,
            exactAlarmCapabilityAvailable = capability.exactAlarmCapabilityAvailable,
            exactAlarmRequired = capability.exactAlarmRequired,
            lastReconciledAtEpochMillis = status.lastReconciledAtEpochMillis,
            lastDeliveredAtEpochMillis = status.lastDeliveredAtEpochMillis,
            lastDeliveredOccurrenceId = status.lastDeliveredOccurrenceId,
            lastRecoveryReason = status.lastRecoveryReason,
        )
    }

    fun attentionReliabilityEvidence(planId: StudyPlanId): StudyFlowAttentionReliabilityEvidenceSnapshot {
        val now = clock()
        val workspaceId = StudyFlowAttentionWorkspace.forPlanId(planId.value)
        val peer = linkPeerProvider()
        val devices = peer?.let { linkPeer ->
            AndroidStudyFlowAttentionRouterBridge.topologyCapabilities(
                context = appContext,
                coordinator = this,
                localPeer = linkPeer.runtime.localIdentity.descriptor,
                trustedPeers = linkPeer.trustedPeers().filter { trusted ->
                    linkPeer.runtime.bestEndpoint(trusted.peer.deviceId) != null
                },
                presenceViews = linkPeer.presenceViews(workspaceId),
                nowEpochMillis = now,
            )
        }.orEmpty()
        return StudyFlowAttentionReliabilityAnalyzer.analyze(
            ledger = attentionLedger.snapshot(),
            devices = devices,
            nowEpochMillis = now,
            workspaceId = workspaceId,
        )
    }

    fun platformCapability(): StudyReminderPlatformCapability = StudyReminderPlatformCapability(
        notificationsAllowed = notificationsAllowed(),
        exactAlarmCapabilityAvailable =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        exactAlarmRequired = false,
    )

    private suspend fun reconcilePlanInternal(planId: StudyPlanId) {
        val runtimePlan = buildRuntimePlan(planId) ?: run {
            cancelPlanAlarms(planId, clearBookkeeping = true)
            return
        }
        // POST_NOTIFICATIONS only gates native notification surfaces. ATT08 compact
        // widgets/tiles remain a valid ATT07 target, so due-time reconciliation must stay alive.
        val focusPolicy = focusPolicyStore.load(planId)
        val candidates = runtimePlan.notificationCandidates.map { candidate ->
            StudyReminderWakeupContracts.applyAuthoritativeQuietHours(candidate, focusPolicy, clock())
        }
        val previouslyScheduled = occurrenceStore.scheduled(planId)
        cancelPlanAlarms(planId, clearBookkeeping = false, retainCandidates = candidates)
        occurrenceStore.replaceScheduled(planId, candidates)
        candidates.forEach { candidate ->
            upsertAttentionScheduled(planId, candidate)
            if (previouslyScheduled.none { occurrence -> occurrence.matches(candidate) }) {
                scheduleCandidate(planId, candidate)
            }
        }
    }

    suspend fun deliver(planId: StudyPlanId, type: ReminderNotificationType, occurrenceId: String) {
        val scheduled = occurrenceStore.scheduled(planId).firstOrNull { occurrence ->
            occurrence.type == type && occurrence.occurrenceId == occurrenceId
        } ?: return

        val runtimePlan = buildRuntimePlan(planId, allowOccurrenceId = occurrenceId) ?: return
        val now = clock()
        val focusPolicy = focusPolicyStore.load(planId)
        if (scheduled.fireAtEpochMillis <= now) {
            val rearmed = StudyReminderWakeupContracts.rearmFiredQuietCandidate(
                candidate = scheduled.toCandidate(),
                focusPolicy = focusPolicy,
                nowEpochMillis = now,
            )
            if (rearmed != null) {
                occurrenceStore.addScheduled(planId, rearmed, snoozedFromOccurrenceId = scheduled.snoozedFromOccurrenceId)
                upsertAttentionScheduled(planId, rearmed)
                scheduleCandidate(planId, rearmed)
                StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
                return
            }
        }

        val candidate = runtimePlan.notificationCandidates.firstOrNull { current ->
            current.type == type && current.occurrenceId == occurrenceId
        }?.let { StudyReminderWakeupContracts.applyAuthoritativeQuietHours(it, focusPolicy, now) }
            ?: scheduled.toCandidate()

        val occurrence = candidate.toAttentionOccurrence(planId)
        val ledgerEntry = attentionLedger.entry(occurrence.occurrenceId)
        val linkPeer = linkPeerProvider() ?: return
        val localPeer = linkPeer.runtime.localIdentity.descriptor
        val devices = AndroidStudyFlowAttentionRouterBridge.topologyCapabilities(
            context = appContext,
            coordinator = this,
            localPeer = localPeer,
            trustedPeers = linkPeer.trustedPeers().filter { trusted ->
                linkPeer.runtime.bestEndpoint(trusted.peer.deviceId) != null
            },
            presenceViews = linkPeer.presenceViews(occurrence.workspaceId),
            nowEpochMillis = now,
        )
        if (devices.isEmpty()) return
        var eligibleDevices = devices
        var route = AndroidStudyFlowAttentionRouterBridge.routeWithPolicy(
            occurrence = occurrence,
            devices = eligibleDevices,
            localDeviceId = localPeer.deviceId,
            focusPolicy = focusPolicy,
            nowEpochMillis = now,
            ledgerEntry = ledgerEntry,
        )
        var remoteOwnerDelivered: Boolean
        while (true) {
            runCatching { attentionLedger.recordRouteDecision(route) }
            remoteOwnerDelivered = dispatchRemoteRouteTargets(
                linkPeer = linkPeer,
                route = route,
                occurrence = occurrence,
                candidate = candidate,
                localDeviceId = localPeer.deviceId,
                nowEpochMillis = now,
            )
            val failedRemoteOwner = route.owner?.deviceId
                ?.takeIf { it != localPeer.deviceId && !remoteOwnerDelivered }
                ?: break
            eligibleDevices = eligibleDevices.filterNot { it.peer.deviceId == failedRemoteOwner }
            if (eligibleDevices.isEmpty()) break
            route = AndroidStudyFlowAttentionRouterBridge.routeWithPolicy(
                occurrence = occurrence,
                devices = eligibleDevices,
                localDeviceId = localPeer.deviceId,
                focusPolicy = focusPolicy,
                nowEpochMillis = now,
                ledgerEntry = ledgerEntry,
            )
        }
        val compactProjection = AndroidCompactCompanionAttentionBridge.projectPhoneOwnedCompanions(
            occurrence = occurrence,
            route = route,
            parentDeviceId = localPeer.deviceId,
            nowEpochMillis = now,
            ledgerEntry = ledgerEntry,
        )
        AndroidCompactCompanionStateStore(appContext).update(compactProjection)
        StudyFlowAttentionTileService.requestRefresh(appContext)
        if (
            route.owner?.deviceId != localPeer.deviceId &&
            remoteOwnerDelivered &&
            localPeer.deviceId !in route.targetDeviceIds
        ) {
            runCatching {
                attentionLedger.transition(
                    occurrence.occurrenceId,
                    StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
                    now,
                )
            }
            runCatching {
                attentionLedger.transition(
                    occurrence.occurrenceId,
                    StudyFlowAttentionDeliveryState.DELIVERED,
                    now,
                )
            }
            occurrenceStore.markDelivered(planId, occurrenceId)
            occurrenceStore.removeScheduled(planId, occurrenceId)
            StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
            return
        }
        // ATT12 closes the ATT07 explicit-target gap: a local scheduler may evaluate a
        // remote-targeted occurrence, but it must never surface it unless ATT07 selected
        // this Android device as owner or bounded mirror. Keep the ledger scheduled so
        // the intended target can still deliver it.
        if (!AndroidStudyFlowAttentionRouterBridge.isLocalTarget(route, localPeer.deviceId)) {
            scheduleCandidate(planId, candidate)
            return
        }
        val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
            occurrence = occurrence,
            policy = focusPolicy,
            nowEpochMillis = now,
            ledgerEntry = ledgerEntry,
            localClientKind = com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind.ANDROID,
        )
        runCatching { attentionLedger.recordPolicyDecision(decision) }
        if (decision.outcome == StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END) {
            val deferredAt = decision.deferredUntilEpochMillis ?: return
            val deferredCandidate = candidate.copy(
                fireAtEpochMillis = deferredAt,
                delayedByQuietHours = true,
            )
            occurrenceStore.addScheduled(planId, deferredCandidate)
            upsertAttentionScheduled(planId, deferredCandidate)
            runCatching { attentionLedger.recordPolicyDecision(decision) }
            scheduleCandidate(planId, deferredCandidate)
            StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
            return
        }
        if (decision.outcome == StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE) {
            occurrenceStore.removeScheduled(planId, occurrenceId)
            return
        }

        val localSurface = route.targetFor(localPeer.deviceId)?.surface
        val compactOnly = decision.outcome == StudyFlowAttentionPolicyOutcome.COMPACT_ONLY ||
            localSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE
        if (!compactOnly && !postNotification(
                planId = planId,
                candidate = candidate,
                silent = decision.deliverSilently,
            )
        ) {
            val retry = StudyReminderWakeupContracts.permissionDeniedRetryCandidate(candidate, now)
            occurrenceStore.addScheduled(planId, retry)
            upsertAttentionScheduled(planId, retry)
            scheduleCandidate(planId, retry)
            StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
            return
        }
        val mirrorEscalationAt = StudyReminderWakeupContracts.mirrorEscalationEpochMillis(candidate, focusPolicy)
        if (mirrorEscalationAt != null && now < mirrorEscalationAt &&
            decision.outcome != StudyFlowAttentionPolicyOutcome.ESCALATE_MIRROR
        ) {
            val escalationCandidate = candidate.copy(fireAtEpochMillis = mirrorEscalationAt)
            occurrenceStore.addScheduled(planId, escalationCandidate)
            upsertAttentionScheduled(planId, escalationCandidate)
            scheduleCandidate(planId, escalationCandidate)
            transitionAttention(occurrenceId, StudyFlowAttentionDeliveryState.DELIVERY_PENDING)
            StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
            return
        }
        occurrenceStore.markDelivered(planId, occurrenceId)
        occurrenceStore.removeScheduled(planId, occurrenceId)
        markAttentionDelivered(candidate)
        runCatching { attentionLedger.recordPolicyDecision(decision) }
        runtimeStatusStore.markDelivered(occurrenceId, now)
        reconcilePlanInternal(planId)
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
    }

    /**
     * SFLP18/ATT07 inbound production path. The authenticated peer server has already
     * verified sender identity, workspace and route target before invoking this method.
     * The ATT01 ledger remains the duplicate/stale-sequence authority.
     */
    fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck {
        val localPeer = linkPeerProvider()?.runtime?.localIdentity?.descriptor
            ?: return remoteNotificationAck(
                delivery,
                StudyFlowNotificationDeliveryStatus.REJECTED,
                "local_peer_unavailable",
            )
        val notification = delivery.notification
        val transportedRoute = notification.attentionRoute
        val selectedSurface = transportedRoute?.targetFor(localPeer.deviceId)?.surface
        val compactDelivery = transportedRoute?.compactOnly == true ||
            selectedSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE
        if (!compactDelivery && !notificationsAllowed()) {
            return remoteNotificationAck(
                delivery,
                StudyFlowNotificationDeliveryStatus.REJECTED,
                "notifications_unavailable",
            )
        }
        val reducer = StudyFlowNotificationScheduleReducer(
            localDevice = localPeer,
            clock = StudyFlowPeerClock(clock),
            attentionLedger = attentionLedger,
        )
        val ack = reducer.apply(delivery)
        if (ack.status != StudyFlowNotificationDeliveryStatus.ACCEPTED) return ack

        val occurrence = notification.canonicalAttentionOccurrence(delivery.sourceDeviceId)
        if (compactDelivery) {
            val compactRoute = checkNotNull(transportedRoute) {
                "Compact delivery requires an ATT07 route"
            }
            val projection = AndroidCompactCompanionAttentionBridge.projectPhoneOwnedCompanions(
                occurrence = occurrence,
                route = compactRoute,
                parentDeviceId = localPeer.deviceId,
                nowEpochMillis = clock(),
                // SFLP admission already performed duplicate suppression and moved the
                // local ledger to DELIVERED; render this just-accepted compact packet once.
                ledgerEntry = null,
            )
            AndroidCompactCompanionStateStore(appContext).update(projection)
            StudyFlowAttentionTileService.requestRefresh(appContext)
            runtimeStatusStore.markDelivered(occurrence.occurrenceId.value, clock())
            StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
            return ack
        }

        val channelId = notification.kind.remoteChannelId()
        val openIntent = PendingIntent.getActivity(
            appContext,
            stableNotificationId("remote:${notification.notificationId.value}"),
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(occurrence.priority.compatPriority())
            .setPublicVersion(
                NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("StudyFlow reminder")
                    .setContentText("Open StudyFlow to view this reminder.")
                    .build(),
            )
        if (transportedRoute?.deliverSilently == true) builder.setSilent(true)
        NotificationManagerCompat.from(appContext).notify(
            stableNotificationId("remote:${notification.notificationId.value}"),
            builder.build(),
        )
        runtimeStatusStore.markDelivered(occurrence.occurrenceId.value, clock())
        StudyFlowWidgetUpdateCoordinator.refreshAll(appContext)
        return ack
    }

    private fun remoteNotificationAck(
        delivery: StudyFlowNotificationDelivery,
        status: StudyFlowNotificationDeliveryStatus,
        reason: String,
    ): StudyFlowNotificationDeliveryAck = StudyFlowNotificationDeliveryAck(
        messageId = StudyFlowMessageId("ack-${delivery.messageId.value}"),
        notificationId = delivery.notification.notificationId,
        targetDeviceId = delivery.targetDeviceId,
        acknowledgedSequence = delivery.sequence,
        status = status,
        reason = reason,
        acknowledgedAtEpochMillis = clock(),
    )

    suspend fun handleNotificationAction(
        planId: StudyPlanId,
        type: ReminderNotificationType,
        occurrenceId: String,
        action: StudyReminderNotificationAction,
        targetId: String?,
        sourceCandidate: ReminderNotificationCandidate? = null,
    ): StudyReminderActionResult {
        val scheduled = occurrenceStore.scheduled(planId).firstOrNull { it.occurrenceId == occurrenceId }
        val candidate = sourceCandidate ?: scheduled?.toCandidate()
        return when (action) {
            StudyReminderNotificationAction.SNOOZE -> {
                val source = candidate ?: buildRuntimePlan(planId, allowOccurrenceId = occurrenceId)
                    ?.notificationCandidates?.firstOrNull { it.occurrenceId == occurrenceId }
                    ?: return StudyReminderActionResult.None
                val policy = policyFor(planId)
                val snoozedAt = clock() + policy.defaultSnoozeMinutes * 60_000L
                val rawSnoozed = source.copy(
                    occurrenceId = "${source.occurrenceId}:snooze:$snoozedAt",
                    scheduledAtEpochMillis = snoozedAt,
                    fireAtEpochMillis = snoozedAt,
                    delayedByQuietHours = false,
                )
                val snoozed = StudyReminderWakeupContracts.applyAuthoritativeQuietHours(
                    rawSnoozed,
                    focusPolicyStore.load(planId),
                    clock(),
                )
                occurrenceStore.addScheduled(planId, snoozed, snoozedFromOccurrenceId = source.occurrenceId)
                scheduleCandidate(planId, snoozed)
                upsertAttentionScheduled(planId, snoozed)
                transitionAttention(source.occurrenceId, StudyFlowAttentionDeliveryState.SNOOZED)
                cancelPostedNotification(occurrenceId)
                StudyReminderActionResult.None
            }
            StudyReminderNotificationAction.START_NOW -> {
                val activityId = targetId ?: candidate?.content?.targetId ?: return StudyReminderActionResult.None
                val activeSession = studySessionRepository.findAnyActiveSession()
                if (activeSession != null) {
                    val requestedSessionAlreadyActive =
                        activeSession.studyPlanId == planId && activeSession.plannedActivityId?.value == activityId
                    transitionAttention(
                        occurrenceId,
                        if (requestedSessionAlreadyActive) {
                            StudyFlowAttentionDeliveryState.STARTED
                        } else {
                            StudyFlowAttentionDeliveryState.ACKNOWLEDGED
                        },
                    )
                    cancelPostedNotification(occurrenceId)
                    return StudyReminderActionResult.Open(
                        type = type,
                        targetId = activeSession.plannedActivityId?.value ?: activityId,
                        sessionId = activeSession.id.value,
                    )
                }
                val activity = studyScheduleRepository.observeSchedule(planId).first().plannedActivities
                    .firstOrNull { it.id.value == activityId } ?: return StudyReminderActionResult.None
                val session = sessionManager.startFromActivity(
                    sessionId = StudySessionId(UUID.randomUUID().toString()),
                    activity = activity,
                )
                transitionAttention(occurrenceId, StudyFlowAttentionDeliveryState.STARTED)
                cancelPostedNotification(occurrenceId)
                reconcilePlan(planId, "notification-start-now")
                StudyReminderActionResult.Open(
                    type = type,
                    targetId = activityId,
                    sessionId = session.id.value,
                )
            }
            StudyReminderNotificationAction.MARK_DONE -> {
                val activityId = targetId ?: candidate?.content?.targetId ?: return StudyReminderActionResult.None
                studyScheduleRepository.updatePlannedActivityStatus(
                    planId = planId,
                    activityId = PlannedActivityId(activityId),
                    status = com.mikeyphw.studyflow.core.model.PlannedActivityStatus.COMPLETED,
                    completedAtEpochMillis = clock(),
                )
                transitionAttention(occurrenceId, StudyFlowAttentionDeliveryState.COMPLETED)
                cancelPostedNotification(occurrenceId)
                reconcilePlan(planId, "notification-mark-done")
                StudyReminderActionResult.None
            }
            StudyReminderNotificationAction.DISMISS -> {
                transitionAttention(occurrenceId, StudyFlowAttentionDeliveryState.DISMISSED)
                StudyReminderActionResult.None
            }
            StudyReminderNotificationAction.START_BREAK -> {
                val source = candidate ?: return StudyReminderActionResult.None
                val breakEndsAt = clock() + 5 * 60_000L
                val rawBreakReminder = source.copy(
                    occurrenceId = "${source.occurrenceId}:break:$breakEndsAt",
                    type = ReminderNotificationType.SESSION_COMPLETED,
                    content = source.content.copy(
                        kind = ReminderNotificationContentKind.BREAK_COMPLETED,
                        userLabel = null,
                    ),
                    scheduledAtEpochMillis = breakEndsAt,
                    fireAtEpochMillis = breakEndsAt,
                    delayedByQuietHours = false,
                )
                val snoozed = StudyReminderWakeupContracts.applyAuthoritativeQuietHours(
                    rawBreakReminder,
                    focusPolicyStore.load(planId),
                    clock(),
                )
                occurrenceStore.addScheduled(planId, snoozed, snoozedFromOccurrenceId = source.occurrenceId)
                scheduleCandidate(planId, snoozed)
                upsertAttentionScheduled(planId, snoozed)
                transitionAttention(source.occurrenceId, StudyFlowAttentionDeliveryState.SNOOZED)
                cancelPostedNotification(occurrenceId)
                StudyReminderActionResult.None
            }
        }
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                StudyReminderScheduleChannelId,
                appContext.getString(R.string.notification_channel_study_schedule_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = appContext.getString(R.string.notification_channel_study_schedule_description) },
            NotificationChannel(
                StudyReminderReviewsChannelId,
                appContext.getString(R.string.notification_channel_reviews_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = appContext.getString(R.string.notification_channel_reviews_description) },
            NotificationChannel(
                StudyReminderSessionChannelId,
                appContext.getString(R.string.notification_channel_session_timers_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = appContext.getString(R.string.notification_channel_session_timers_description) },
            NotificationChannel(
                StudyReminderSummaryChannelId,
                appContext.getString(R.string.notification_channel_summaries_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = appContext.getString(R.string.notification_channel_summaries_description) },
        )
        manager.createNotificationChannels(channels)
        manager.deleteNotificationChannel(StudyReminderLegacyChannelId)
    }

    private suspend fun buildRuntimePlan(planId: StudyPlanId, allowOccurrenceId: String? = null): StudyReminderPlan? {
        val plan = studyPlanRepository.observePlan(planId).first() ?: return null
        if (plan.status != StudyPlanStatus.ACTIVE) return null
        val settings = studyPlanRepository.observeStoredPlanSettings(planId).first() ?: return null
        // ATT10 migration: preserve the user's existing quiet-hours opt-in/opt-out instead of
        // silently enabling a new 22:00–07:00 policy on upgrade.
        focusPolicyStore.load(planId, settings.quietHoursStartMinute, settings.quietHoursEndMinute)
        val now = clock()
        val scheduledOccurrences = occurrenceStore.scheduled(planId)
        val delivered = occurrenceStore.deliveredOccurrenceIds(planId).toMutableSet().apply {
            allowOccurrenceId?.let(::remove)
        }
        val persistedScheduledAt = scheduledOccurrences.associate { it.occurrenceId to it.scheduledAtEpochMillis }
        val eligiblePlanned = scheduledOccurrences.asSequence()
            .filter { it.type == ReminderNotificationType.PLANNED_ACTIVITY }
            .filter { it.isWithinReconciliationDeliveryWindow(now) }
            .mapTo(mutableSetOf(), ScheduledReminderOccurrence::occurrenceId)
        val base = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = studyScheduleRepository.observeSchedule(planId).first(),
                reviewQueue = reviewManager.observeQueue(planId).first(),
                nowEpochMillis = now,
                minuteOfDay = currentMinuteOfDay(),
                apiLevel = Build.VERSION.SDK_INT,
                notificationPermissionGranted = notificationsAllowed(),
                deliveredOccurrenceIds = delivered,
                reconciliationEligiblePlannedOccurrenceIds = eligiblePlanned,
                persistedScheduledAtByOccurrenceId = persistedScheduledAt,
                policy = policyStore.load(planId, settings),
                activeSessions = studySessionRepository.observeActiveSessions().first(),
            ),
        )
        val snoozes = scheduledOccurrences
            .filter { it.snoozedFromOccurrenceId != null }
            .filter { it.occurrenceId !in delivered }
            .filter { it.fireAtEpochMillis >= now - StudyReminderPlanner.PlannedActivityLateDeliveryGraceMillis }
            .map(ScheduledReminderOccurrence::toCandidate)
        if (snoozes.isEmpty()) return base
        val merged = (base.notificationCandidates + snoozes).distinctBy(ReminderNotificationCandidate::occurrenceId)
        return base.copy(notificationCandidates = merged)
    }

    private fun dispatchRemoteRouteTargets(
        linkPeer: AndroidStudyFlowLinkPeer,
        route: com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute,
        occurrence: StudyFlowAttentionOccurrence,
        candidate: ReminderNotificationCandidate,
        localDeviceId: com.mikeyphw.studyflow.core.link.StudyFlowDeviceId,
        nowEpochMillis: Long,
    ): Boolean {
        val text = localizedText(candidate)
        var ownerDelivered = route.owner?.deviceId == localDeviceId
        route.targetDeviceIds.filter { it != localDeviceId }.forEach { targetDeviceId ->
            val endpoint = linkPeer.runtime.bestEndpoint(targetDeviceId) ?: return@forEach
            val delivery = StudyFlowAttentionNotificationDeliveryFactory.create(
                occurrence = occurrence,
                sourceDeviceId = localDeviceId,
                targetDeviceId = targetDeviceId,
                title = text.title,
                body = text.body,
                nowEpochMillis = nowEpochMillis,
                deliveryRoute = route,
            )
            val ack = runCatching {
                linkPeer.runtime.deliverNotification(occurrence.workspaceId, targetDeviceId, delivery, endpoint)
            }.getOrNull()
            val accepted = ack?.status in setOf(
                StudyFlowNotificationDeliveryStatus.ACCEPTED,
                StudyFlowNotificationDeliveryStatus.DUPLICATE,
            )
            if (route.owner?.deviceId == targetDeviceId) ownerDelivered = accepted
        }
        return ownerDelivered
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleCandidate(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        val triggerAt = candidate.fireAtEpochMillis.coerceAtLeast(
            clock() + StudyReminderWakeupContracts.MinimumImmediateWakeupDelayMillis,
        )
        val operation = pendingIntent(planId, candidate.type, candidate.occurrenceId)
        if (StudyReminderWakeupContracts.requiresExactWakeup(candidate)) {
            scheduleExactOrShortestWindow(triggerAt, operation)
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                StudyReminderPlanner.PlannedActivityDeliveryGraceMillis,
                operation,
            )
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleExactOrShortestWindow(triggerAtEpochMillis: Long, operation: PendingIntent) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMillis,
                    StudyReminderWakeupContracts.NearTermWidgetExactThresholdMillis,
                    operation,
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
            }
            else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, operation)
        }
    }

    private fun cancelPlanAlarms(
        planId: StudyPlanId,
        clearBookkeeping: Boolean,
        retainCandidates: Collection<ReminderNotificationCandidate> = emptyList(),
        markAttentionCancelled: Boolean = true,
    ) {
        occurrenceStore.scheduled(planId)
            .filterNot { occurrence -> retainCandidates.any { candidate -> occurrence.matches(candidate) } }
            .forEach { occurrence ->
                alarmManager.cancel(pendingIntent(planId, occurrence.type, occurrence.occurrenceId))
                if (markAttentionCancelled) {
                    transitionAttention(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.CANCELLED)
                }
            }
        if (clearBookkeeping) occurrenceStore.clearScheduled(planId)
    }

    private fun ScheduledReminderOccurrence.matches(candidate: ReminderNotificationCandidate): Boolean =
        type == candidate.type && occurrenceId == candidate.occurrenceId &&
            scheduledAtEpochMillis == candidate.scheduledAtEpochMillis &&
            fireAtEpochMillis == candidate.fireAtEpochMillis

    private fun ScheduledReminderOccurrence.isWithinReconciliationDeliveryWindow(nowEpochMillis: Long): Boolean =
        StudyReminderPlanner.plannedActivityWithinReconciliationDeliveryWindow(
            fireAtEpochMillis = fireAtEpochMillis,
            nowEpochMillis = nowEpochMillis,
            reconciliationSlackMillis = ReconciliationDeliverySlackMillis,
        )

    @SuppressLint("MissingPermission")
    private fun postNotification(
        planId: StudyPlanId,
        candidate: ReminderNotificationCandidate,
        silent: Boolean = false,
    ): Boolean {
        if (!notificationsAllowed()) return false
        val text = localizedText(candidate)
        val builder = NotificationCompat.Builder(appContext, candidate.type.channelId())
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setContentIntent(openAppPendingIntent(planId, candidate))
            .setDeleteIntent(actionPendingIntent(planId, candidate, StudyReminderNotificationAction.DISMISS))
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(candidate.type.compatPriority())
            .setSilent(silent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
        builder.setPublicVersion(
            NotificationCompat.Builder(appContext, candidate.type.channelId())
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("StudyFlow reminder")
                .setContentText("Open StudyFlow to view this reminder.")
                .build(),
        )

        candidate.actions().take(3).forEach { action ->
            builder.addAction(
                action.icon,
                appContext.getString(action.labelRes),
                action.pendingIntent(planId, candidate),
            )
        }
        NotificationManagerCompat.from(appContext).notify(stableNotificationId(candidate.occurrenceId), builder.build())
        return true
    }

    private fun localizedText(candidate: ReminderNotificationCandidate): LocalizedReminderText =
        when (candidate.content.kind) {
        ReminderNotificationContentKind.DUE_REVIEWS -> {
            val count = candidate.content.count ?: 0
            LocalizedReminderText(
                appContext.getString(R.string.reminder_notification_due_reviews_title),
                appContext.resources.getQuantityString(R.plurals.reminder_notification_due_reviews_body, count, count),
            )
        }
        ReminderNotificationContentKind.PLANNED_ACTIVITY -> LocalizedReminderText(
            candidate.content.userLabel.orEmpty(),
            appContext.getString(R.string.reminder_notification_planned_activity_body),
        )
        ReminderNotificationContentKind.MISSED_ACTIVITY -> {
            val count = candidate.content.count ?: 0
            LocalizedReminderText(
                appContext.getString(R.string.reminder_notification_missed_activity_title),
                appContext.resources.getQuantityString(
                    R.plurals.reminder_notification_missed_activity_body,
                    count,
                    count,
                ),
            )
        }
        ReminderNotificationContentKind.DAILY_SUMMARY -> LocalizedReminderText(
            candidate.content.userLabel.orEmpty(),
            appContext.getString(R.string.reminder_notification_daily_summary_body),
        )
        ReminderNotificationContentKind.SESSION_ENDING -> LocalizedReminderText(
            appContext.getString(R.string.reminder_notification_session_ending_title),
            appContext.getString(
                R.string.reminder_notification_session_ending_body,
                candidate.content.userLabel.orEmpty(),
            ),
        )
        ReminderNotificationContentKind.SESSION_COMPLETED -> LocalizedReminderText(
            appContext.getString(R.string.reminder_notification_session_complete_title),
            appContext.getString(R.string.reminder_notification_session_complete_body),
        )
        ReminderNotificationContentKind.BREAK_COMPLETED -> LocalizedReminderText(
            appContext.getString(R.string.reminder_notification_break_complete_title),
            appContext.getString(R.string.reminder_notification_break_complete_body),
        )
        ReminderNotificationContentKind.EXAM_COUNTDOWN -> {
            val days = candidate.content.count ?: 0
            LocalizedReminderText(
                candidate.content.userLabel.orEmpty(),
                appContext.resources.getQuantityString(R.plurals.reminder_notification_exam_countdown_body, days, days),
            )
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(
        planId: StudyPlanId,
        type: ReminderNotificationType,
        occurrenceId: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            stableNotificationId("alarm:$occurrenceId"),
            reminderIntent(appContext, planId, type, occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppPendingIntent(planId: StudyPlanId, candidate: ReminderNotificationCandidate): PendingIntent {
        val intent = studyReminderOpenIntent(
            context = appContext,
            planId = planId,
            type = candidate.type,
            identity = studyReminderIdentityUri(
                appContext,
                planId,
                candidate.type,
                candidate.occurrenceId,
                openApp = true,
            ),
            targetId = candidate.content.targetId,
            sessionId = candidate.content.targetId.takeIf { candidate.type.isSessionType() },
        ).apply { putExtra(StudyReminderExtraOccurrenceId, candidate.occurrenceId) }
        return PendingIntent.getActivity(
            appContext,
            stableNotificationId("open:${candidate.occurrenceId}"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(
        planId: StudyPlanId,
        candidate: ReminderNotificationCandidate,
        action: StudyReminderNotificationAction,
    ): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        stableNotificationId("action:${action.name}:${candidate.occurrenceId}"),
        reminderActionIntent(appContext, planId, candidate, action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun NotificationActionSpec.pendingIntent(
        planId: StudyPlanId,
        candidate: ReminderNotificationCandidate,
    ): PendingIntent = when (kind) {
        NotificationActionSpecKind.OPEN -> openAppPendingIntent(planId, candidate)
        NotificationActionSpecKind.RESCHEDULE -> {
            val intent = studyReminderOpenIntent(
                appContext,
                planId,
                candidate.type,
                studyReminderIdentityUri(
                    appContext,
                    planId,
                    candidate.type,
                    "reschedule:${candidate.occurrenceId}",
                    true,
                ),
                targetId = candidate.content.targetId,
                sessionId = candidate.content.targetId.takeIf { candidate.type.isSessionType() },
            )
            PendingIntent.getActivity(
                appContext,
                stableNotificationId("reschedule:${candidate.occurrenceId}"),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        NotificationActionSpecKind.MUTATION -> actionPendingIntent(planId, candidate, requireNotNull(mutation))
    }

    private fun upsertAttentionScheduled(planId: StudyPlanId, candidate: ReminderNotificationCandidate) {
        attentionLedger.upsertScheduled(candidate.toAttentionOccurrence(planId), clock())
    }

    private fun markAttentionDelivered(candidate: ReminderNotificationCandidate) {
        val id = StudyFlowAttentionOccurrenceId.fromLegacy(candidate.occurrenceId)
        val current = attentionLedger.entry(id) ?: return
        runCatching {
            if (current.state == StudyFlowAttentionDeliveryState.SCHEDULED) {
                attentionLedger.transition(id, StudyFlowAttentionDeliveryState.DELIVERY_PENDING, clock())
            }
            attentionLedger.transition(id, StudyFlowAttentionDeliveryState.DELIVERED, clock())
        }
    }

    private fun transitionAttention(occurrenceId: String, state: StudyFlowAttentionDeliveryState) {
        val id = StudyFlowAttentionOccurrenceId.fromLegacy(occurrenceId)
        if (attentionLedger.entry(id) == null) return
        runCatching { attentionLedger.transition(id, state, clock()) }
    }

    private fun cancelPostedNotification(occurrenceId: String) {
        NotificationManagerCompat.from(appContext).cancel(stableNotificationId(occurrenceId))
    }

    private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}

sealed interface StudyReminderActionResult {
    data object None : StudyReminderActionResult
    data class Open(
        val type: ReminderNotificationType,
        val targetId: String? = null,
        val sessionId: String? = null,
    ) : StudyReminderActionResult
}

private data class LocalizedReminderText(val title: String, val body: String)
private enum class NotificationActionSpecKind { OPEN, RESCHEDULE, MUTATION }
private data class NotificationActionSpec(
    val labelRes: Int,
    val kind: NotificationActionSpecKind,
    val mutation: StudyReminderNotificationAction? = null,
    val icon: Int = 0,
)

private fun ReminderNotificationCandidate.actions(): List<NotificationActionSpec> {
    if (content.kind == ReminderNotificationContentKind.BREAK_COMPLETED) {
        return listOf(
            NotificationActionSpec(R.string.reminder_action_continue, NotificationActionSpecKind.OPEN),
        )
    }
    return when (type) {
    ReminderNotificationType.DUE_REVIEWS -> listOf(
        NotificationActionSpec(R.string.reminder_action_review_now, NotificationActionSpecKind.OPEN),
        NotificationActionSpec(
            R.string.reminder_action_snooze,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.SNOOZE,
        ),
    )
    ReminderNotificationType.PLANNED_ACTIVITY -> listOf(
        NotificationActionSpec(
            R.string.reminder_action_start_now,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.START_NOW,
        ),
        NotificationActionSpec(
            R.string.reminder_action_snooze,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.SNOOZE,
        ),
        NotificationActionSpec(R.string.reminder_action_reschedule, NotificationActionSpecKind.RESCHEDULE),
    )
    ReminderNotificationType.MISSED_ACTIVITY -> listOf(
        NotificationActionSpec(R.string.reminder_action_reschedule, NotificationActionSpecKind.RESCHEDULE),
        NotificationActionSpec(
            R.string.reminder_action_mark_done,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.MARK_DONE,
        ),
    )
    ReminderNotificationType.DAILY_SUMMARY -> listOf(
        NotificationActionSpec(R.string.reminder_action_open_today, NotificationActionSpecKind.OPEN),
    )
    ReminderNotificationType.SESSION_ENDING -> listOf(
        NotificationActionSpec(R.string.reminder_action_continue, NotificationActionSpecKind.OPEN),
        NotificationActionSpec(
            R.string.reminder_action_snooze,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.SNOOZE,
        ),
    )
    ReminderNotificationType.SESSION_COMPLETED -> listOf(
        NotificationActionSpec(R.string.reminder_action_finish_session, NotificationActionSpecKind.OPEN),
        NotificationActionSpec(R.string.reminder_action_continue, NotificationActionSpecKind.OPEN),
        NotificationActionSpec(
            R.string.reminder_action_start_break,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.START_BREAK,
        ),
    )
    ReminderNotificationType.EXAM_COUNTDOWN -> listOf(
        NotificationActionSpec(R.string.reminder_action_open_progress, NotificationActionSpecKind.OPEN),
        NotificationActionSpec(
            R.string.reminder_action_snooze,
            NotificationActionSpecKind.MUTATION,
            StudyReminderNotificationAction.SNOOZE,
        ),
    )
    }
}

private fun StudyFlowNotificationKind.remoteChannelId(): String = when (this) {
    StudyFlowNotificationKind.REVIEW_DUE -> StudyReminderReviewsChannelId
    StudyFlowNotificationKind.SESSION_RESUME -> StudyReminderSessionChannelId
    StudyFlowNotificationKind.STUDY_DUE -> StudyReminderScheduleChannelId
    StudyFlowNotificationKind.SYNC_ATTENTION,
    StudyFlowNotificationKind.HANDOFF_READY,
    StudyFlowNotificationKind.MATERIAL_READY,
    -> StudyReminderSummaryChannelId
}

private fun StudyFlowAttentionPriority.compatPriority(): Int = when (this) {
    StudyFlowAttentionPriority.LOW -> NotificationCompat.PRIORITY_LOW
    StudyFlowAttentionPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
    StudyFlowAttentionPriority.HIGH,
    StudyFlowAttentionPriority.TIME_SENSITIVE,
    -> NotificationCompat.PRIORITY_HIGH
}

private fun ReminderNotificationType.channelId(): String = when (this) {
    ReminderNotificationType.DUE_REVIEWS -> StudyReminderReviewsChannelId
    ReminderNotificationType.PLANNED_ACTIVITY,
    ReminderNotificationType.MISSED_ACTIVITY -> StudyReminderScheduleChannelId
    ReminderNotificationType.SESSION_ENDING,
    ReminderNotificationType.SESSION_COMPLETED -> StudyReminderSessionChannelId
    ReminderNotificationType.DAILY_SUMMARY,
    ReminderNotificationType.EXAM_COUNTDOWN -> StudyReminderSummaryChannelId
}

private fun ReminderNotificationType.compatPriority(): Int = when (this) {
    ReminderNotificationType.SESSION_ENDING,
    ReminderNotificationType.SESSION_COMPLETED -> NotificationCompat.PRIORITY_HIGH
    ReminderNotificationType.DAILY_SUMMARY,
    ReminderNotificationType.EXAM_COUNTDOWN -> NotificationCompat.PRIORITY_LOW
    else -> NotificationCompat.PRIORITY_DEFAULT
}

private fun ReminderNotificationType.isSessionType(): Boolean =
    this == ReminderNotificationType.SESSION_ENDING || this == ReminderNotificationType.SESSION_COMPLETED

private fun ReminderNotificationCandidate.toAttentionOccurrence(planId: StudyPlanId): StudyFlowAttentionOccurrence {
    val target = content.targetId?.let(::StudyFlowEntityId)
    val actions = when (type) {
        ReminderNotificationType.DUE_REVIEWS -> listOf(
            StudyFlowAttentionActionKind.REVIEW_NOW,
            StudyFlowAttentionActionKind.SNOOZE,
        )
        ReminderNotificationType.PLANNED_ACTIVITY -> listOf(
            StudyFlowAttentionActionKind.START_NOW,
            StudyFlowAttentionActionKind.SNOOZE,
            StudyFlowAttentionActionKind.RESCHEDULE,
        )
        ReminderNotificationType.MISSED_ACTIVITY -> listOf(
            StudyFlowAttentionActionKind.RESCHEDULE,
            StudyFlowAttentionActionKind.MARK_DONE,
        )
        ReminderNotificationType.DAILY_SUMMARY -> listOf(StudyFlowAttentionActionKind.OPEN)
        ReminderNotificationType.SESSION_ENDING,
        ReminderNotificationType.SESSION_COMPLETED -> listOf(
            StudyFlowAttentionActionKind.CONTINUE,
            StudyFlowAttentionActionKind.SNOOZE,
        )
        ReminderNotificationType.EXAM_COUNTDOWN -> listOf(
            StudyFlowAttentionActionKind.OPEN,
            StudyFlowAttentionActionKind.SNOOZE,
        )
    }.map(::StudyFlowAttentionAction)
    return StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.fromLegacy(occurrenceId),
        workspaceId = StudyFlowAttentionWorkspace.forPlanId(planId.value),
        scheduleEntityId = target,
        category = when (type) {
            ReminderNotificationType.DUE_REVIEWS -> StudyFlowAttentionCategory.DUE_REVIEW
            ReminderNotificationType.PLANNED_ACTIVITY -> StudyFlowAttentionCategory.PLANNED_STUDY
            ReminderNotificationType.MISSED_ACTIVITY -> StudyFlowAttentionCategory.MISSED_ACTIVITY
            ReminderNotificationType.DAILY_SUMMARY -> StudyFlowAttentionCategory.DAILY_SUMMARY
            ReminderNotificationType.SESSION_ENDING -> StudyFlowAttentionCategory.SESSION_ENDING
            ReminderNotificationType.SESSION_COMPLETED -> StudyFlowAttentionCategory.SESSION_COMPLETED
            ReminderNotificationType.EXAM_COUNTDOWN -> StudyFlowAttentionCategory.EXAM_COUNTDOWN
        },
        priority = when (type) {
            ReminderNotificationType.SESSION_ENDING,
            ReminderNotificationType.SESSION_COMPLETED -> StudyFlowAttentionPriority.TIME_SENSITIVE
            else -> StudyFlowAttentionPriority.NORMAL
        },
        scheduledAtEpochMillis = fireAtEpochMillis,
        originalScheduledAtEpochMillis = scheduledAtEpochMillis,
        timezoneId = TimeZone.getDefault().id,
        actions = actions,
        payload = JsonObject(
            buildMap {
                put("legacyType", JsonPrimitive(type.name))
                put("delayedByQuietHours", JsonPrimitive(delayedByQuietHours))
                content.targetId?.let { put("targetId", JsonPrimitive(it)) }
                content.count?.let { put("count", JsonPrimitive(it)) }
                content.userLabel?.let { put("label", JsonPrimitive(it)) }
            },
        ),
    )
}

internal fun reminderIntent(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
): Intent = Intent(context.applicationContext, StudyReminderReceiver::class.java).apply {
    action = StudyReminderAction
    data = studyReminderIdentityUri(context, planId, type, occurrenceId, openApp = false)
    putExtra(StudyReminderExtraPlanId, planId.value)
    putExtra(StudyReminderExtraType, type.name)
    putExtra(StudyReminderExtraOccurrenceId, occurrenceId)
}

internal fun reminderActionIntent(
    context: Context,
    planId: StudyPlanId,
    candidate: ReminderNotificationCandidate,
    userAction: StudyReminderNotificationAction,
): Intent = Intent(context.applicationContext, StudyReminderActionReceiver::class.java).apply {
    action = StudyReminderUserAction
    data = Uri.Builder()
        .scheme("studyflow")
        .authority("reminder-action")
        .appendPath(context.packageName)
        .appendPath(planId.value)
        .appendPath(userAction.name)
        .appendPath(candidate.occurrenceId)
        .build()
    putExtra(StudyReminderExtraPlanId, planId.value)
    putExtra(StudyReminderExtraType, candidate.type.name)
    putExtra(StudyReminderExtraOccurrenceId, candidate.occurrenceId)
    putExtra(StudyReminderExtraTargetId, candidate.content.targetId)
    putExtra(StudyReminderExtraContentKind, candidate.content.kind.name)
    candidate.content.count?.let { putExtra(StudyReminderExtraContentCount, it) }
    putExtra(StudyReminderExtraUserLabel, candidate.content.userLabel)
    putExtra(StudyReminderExtraScheduledAt, candidate.scheduledAtEpochMillis)
    putExtra(StudyReminderExtraFireAt, candidate.fireAtEpochMillis)
    putExtra(StudyReminderExtraDelayedByQuietHours, candidate.delayedByQuietHours)
    putExtra(StudyReminderExtraUserAction, userAction.name)
}

internal fun studyReminderIdentityUri(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
    openApp: Boolean,
): Uri = Uri.Builder()
    .scheme("studyflow")
    .authority(if (openApp) "reminder-open" else "reminder-alarm")
    .appendPath(context.packageName)
    .appendPath(planId.value)
    .appendPath(type.name)
    .appendPath(occurrenceId)
    .build()

internal fun studyReminderPendingIntentIdentityExists(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    occurrenceId: String,
): Boolean = PendingIntent.getBroadcast(
    context.applicationContext,
    stableNotificationId("alarm:$occurrenceId"),
    reminderIntent(context, planId, type, occurrenceId),
    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
) != null
