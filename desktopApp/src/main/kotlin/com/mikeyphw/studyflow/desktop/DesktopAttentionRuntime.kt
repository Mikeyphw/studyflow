package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionAction
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionActionKind
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryState
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliverySurface
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPriority
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicyEngine
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionPolicyOutcome
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityAnalyzer
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityEvidenceSnapshot
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionWorkspace
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarEventKind
import com.mikeyphw.studyflow.core.link.StudyFlowCalendarScheduleItem
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryStatus
import com.mikeyphw.studyflow.core.link.StudyFlowPeerClock
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationScheduleReducer
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDeliveryAck
import com.mikeyphw.studyflow.core.link.StudyFlowNotificationDelivery
import com.mikeyphw.studyflow.core.link.StudyFlowMessageId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionNotificationDeliveryFactory
import com.mikeyphw.studyflow.core.link.StudyFlowLinkClientKind
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionCard
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DesktopAttentionPollSeconds = 30L
private const val DesktopAttentionExpiryMillis = 2 * 60 * 60 * 1_000L

enum class DesktopReminderCategoryPreference { PLANNED, REVIEWS, MISSED, SUMMARY, SESSIONS, EXAM }

data class DesktopAttentionRuntimeSnapshot(
    val running: Boolean,
    val nativeNotificationsSupported: Boolean,
    val traySupported: Boolean,
    val remindersEnabled: Boolean,
    val nativeNotificationsEnabled: Boolean,
    val trayDashboardEnabled: Boolean,
    val paused: Boolean,
    val scheduledCount: Int,
    val ledgerEntryCount: Int,
    val nextReminderAtEpochMillis: Long?,
    val lastReconciledAtEpochMillis: Long?,
    val lastDeliveredAtEpochMillis: Long?,
    val lastDeliveredTitle: String?,
    val recoveryCount: Int,
    val plannedStudyEnabled: Boolean,
    val dueReviewEnabled: Boolean,
    val missedActivityEnabled: Boolean,
    val dailySummaryEnabled: Boolean,
    val sessionTimerEnabled: Boolean,
    val examCountdownEnabled: Boolean,
    val focusProfile: StudyFlowAttentionFocusProfile,
    val quietHoursEnabled: Boolean,
    val quietHoursLabel: String,
    val lastPolicyOutcome: StudyFlowAttentionPolicyOutcome?,
    val reliabilityEvidence: StudyFlowAttentionReliabilityEvidenceSnapshot,
    val capabilityMessage: String,
)

class DesktopAttentionRuntime(
    private val services: DesktopStudyFlowServices,
    private val preferences: DesktopAttentionPreferences,
    private val attentionLedger: StudyFlowAttentionLedger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "studyflow-desktop-attention").apply { isDaemon = true }
    },
) : AutoCloseable {
    @Volatile private var started = false
    @Volatile private var lastReconciledAt: Long? = null
    @Volatile private var lastDeliveredAt: Long? = null
    @Volatile private var lastDeliveredTitle: String? = null
    @Volatile private var recoveryCount: Int = 0
    @Volatile private var lastPolicyOutcome: StudyFlowAttentionPolicyOutcome? = null
    @Volatile private var lastCompactCard: StudyFlowCompactAttentionCard? = null
    @Volatile private var appState: DesktopAppState? = null

    fun bind(appState: DesktopAppState) {
        this.appState = appState
        services.platformPorts.notifications.setActionHandler(::handleSurfaceAction)
        services.linkService?.runtime?.setNotificationDeliveryHandler(::receiveRemoteNotification)
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        reconcileNow(recovery = true)
        scheduler.scheduleWithFixedDelay(
            { runCatching { reconcileNow() } },
            DesktopAttentionPollSeconds,
            DesktopAttentionPollSeconds,
            TimeUnit.SECONDS,
        )
    }

    fun reconcileNow(recovery: Boolean = false) {
        val now = clock()
        val global = preferences.global()
        if (recovery) recoveryCount += 1
        lastReconciledAt = now
        if (!global.remindersEnabled || global.paused) {
            updateTray(now)
            return
        }
        val plan = services.currentDefaultPlanForDesktop() ?: run {
            updateTray(now)
            return
        }
        val settings = services.planRepository.currentSettings()[plan.id]
            ?: com.mikeyphw.studyflow.core.model.StudyPlanSettings.defaults(plan.id)
        val policy = preferences.policy(plan.id, settings)
        val queue = runBlocking { services.manageStudyReview.observeQueue(plan.id).first() }
        val schedule = services.scheduleRepository.currentSchedule(plan.id)
        val sessions = services.sessionRepository.currentSessions(plan.id)
        val ledgerSnapshot = attentionLedger.snapshot()
        val delivered = ledgerSnapshot.entries
            .filter { it.state in handledStates }
            .map { it.occurrence.occurrenceId.value }
            .toSet() + ledgerSnapshot.legacyDeliveredOccurrenceIds
        val existingScheduled = ledgerSnapshot.entries.associate {
            it.occurrence.occurrenceId.value to it.occurrence.scheduledAtEpochMillis
        }
        val planResult = StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = settings,
                schedule = schedule,
                reviewQueue = queue,
                nowEpochMillis = now,
                minuteOfDay = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute },
                apiLevel = 0,
                notificationPermissionGranted = true,
                deliveredOccurrenceIds = delivered,
                persistedScheduledAtByOccurrenceId = existingScheduled,
                policy = policy,
                activeSessions = sessions,
            ),
        )
        planResult.notificationCandidates.forEach { candidate ->
            val occurrence = candidate.toDesktopAttentionOccurrence(plan.id)
            val existing = attentionLedger.entry(occurrence.occurrenceId)
            if (existing == null || existing.state in setOf(
                    StudyFlowAttentionDeliveryState.PLANNED,
                    StudyFlowAttentionDeliveryState.SNOOZED,
                    StudyFlowAttentionDeliveryState.RESCHEDULED,
                )
            ) {
                attentionLedger.upsertScheduled(occurrence, now)
            }
        }
        deliverDueEntries(now, global, plan.id)
        updateTray(now)
    }

    fun snapshot(): DesktopAttentionRuntimeSnapshot {
        val global = preferences.global()
        val caps = services.platformPorts.notifications.capabilities()
        val ledger = attentionLedger.snapshot()
        val scheduled = ledger.entries.filter { it.state == StudyFlowAttentionDeliveryState.SCHEDULED }
        val plan = services.currentDefaultPlanForDesktop()
        val settings = plan?.id?.let { services.planRepository.currentSettings()[it] }
        val policy = if (plan != null && settings != null) preferences.policy(plan.id, settings) else StudyReminderPolicy()
        val focusPolicy = if (plan != null) preferences.focusPolicy(plan.id, settings) else com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy()
        val now = clock()
        val linkService = services.linkService
        val localPeer = linkService?.runtime?.localIdentity?.descriptor
            ?: DesktopAttentionDeliveryRouterBridge.standalonePeer()
        val workspaceId = plan?.id?.let { StudyFlowAttentionWorkspace.forPlanId(it.value) }
        val reliabilityDevices = DesktopAttentionDeliveryRouterBridge.topologyCapabilities(
            localPeer = localPeer,
            nativeNotificationsAvailable = global.nativeNotificationsEnabled && caps.nativeNotificationsSupported,
            trayAvailable = global.trayDashboardEnabled && caps.traySupported,
            trustedPeers = linkService?.trustedPeers()?.filter { trusted ->
                linkService.runtime.bestEndpoint(trusted.peer.deviceId) != null
            }.orEmpty(),
            presenceViews = workspaceId?.let { linkService?.presenceViews(it) }.orEmpty(),
            nowEpochMillis = now,
        )
        val reliabilityEvidence = StudyFlowAttentionReliabilityAnalyzer.analyze(
            ledger = ledger,
            devices = reliabilityDevices,
            nowEpochMillis = now,
            workspaceId = workspaceId,
        )
        return DesktopAttentionRuntimeSnapshot(
            running = started,
            nativeNotificationsSupported = caps.nativeNotificationsSupported,
            traySupported = caps.traySupported,
            remindersEnabled = global.remindersEnabled,
            nativeNotificationsEnabled = global.nativeNotificationsEnabled,
            trayDashboardEnabled = global.trayDashboardEnabled,
            paused = global.paused,
            scheduledCount = scheduled.size,
            ledgerEntryCount = ledger.entries.size,
            nextReminderAtEpochMillis = scheduled.minOfOrNull { it.occurrence.scheduledAtEpochMillis },
            lastReconciledAtEpochMillis = lastReconciledAt,
            lastDeliveredAtEpochMillis = lastDeliveredAt,
            lastDeliveredTitle = lastDeliveredTitle,
            recoveryCount = recoveryCount,
            plannedStudyEnabled = policy.plannedStudyEnabled,
            dueReviewEnabled = policy.dueReviewEnabled,
            missedActivityEnabled = policy.missedActivityEnabled,
            dailySummaryEnabled = policy.dailySummaryEnabled,
            sessionTimerEnabled = policy.sessionTimerEnabled,
            examCountdownEnabled = policy.examCountdownEnabled,
            focusProfile = focusPolicy.profile,
            quietHoursEnabled = focusPolicy.quietWindow?.enabled == true,
            quietHoursLabel = focusPolicy.quietWindow?.let { quiet ->
                "%02d:%02d–%02d:%02d %s".format(
                    quiet.startMinute / 60,
                    quiet.startMinute % 60,
                    quiet.endMinute / 60,
                    quiet.endMinute % 60,
                    quiet.timezoneId ?: ZoneId.systemDefault().id,
                )
            } ?: "Off",
            lastPolicyOutcome = lastPolicyOutcome,
            reliabilityEvidence = reliabilityEvidence,
            capabilityMessage = when {
                !caps.traySupported && !caps.nativeNotificationsSupported ->
                    "Native notification/tray APIs are unavailable; in-app state remains authoritative."
                !caps.nativeNotificationsSupported ->
                    "System notifications are unavailable; the tray dashboard remains available."
                else -> "Desktop reminders are available while StudyFlow is running."
            },
        )
    }

    fun toggleReminders() = preferences.updateGlobal { it.copy(remindersEnabled = !it.remindersEnabled) }.also { reconcileNow() }
    fun toggleNativeNotifications() = preferences.updateGlobal {
        it.copy(nativeNotificationsEnabled = !it.nativeNotificationsEnabled)
    }.also { reconcileNow() }
    fun toggleTrayDashboard() = preferences.updateGlobal { it.copy(trayDashboardEnabled = !it.trayDashboardEnabled) }.also { reconcileNow() }

    /** ATT09 user-initiated Desktop ICS export from the canonical schedule repository. */
    fun exportCalendar(): Path? {
        val plan = services.currentDefaultPlanForDesktop() ?: return null
        val workspaceId = StudyFlowAttentionWorkspace.forPlanId(plan.id.value)
        val items = services.scheduleRepository.currentActivities(plan.id).mapNotNull { activity ->
            val start = activity.scheduledStartEpochMillis ?: return@mapNotNull null
            val kind = when (activity.type.name) {
                "REVIEW", "FLASHCARDS" -> StudyFlowCalendarEventKind.REVIEW
                "MOCK_EXAM" -> StudyFlowCalendarEventKind.MOCK_EXAM
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
                timezoneId = ZoneId.systemDefault().id,
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
            )
        }
        val bridge = DesktopCalendarAttentionExportBridge()
        val feed = bridge.buildExport(items, System.currentTimeMillis())
        val destination = services.platformPorts.fileDialog
            .chooseExportPath(bridge.suggestedFilename(feed))
            ?: return null
        return Path.of(destination).also { Files.writeString(it, feed.icsText) }
    }
    fun togglePaused() = preferences.updateGlobal { it.copy(paused = !it.paused) }.also { reconcileNow() }

    fun cycleFocusProfile() {
        val plan = services.currentDefaultPlanForDesktop() ?: return
        val settings = services.planRepository.currentSettings()[plan.id]
        val current = preferences.focusPolicy(plan.id, settings)
        val profiles = StudyFlowAttentionFocusProfile.entries
        val next = profiles[(profiles.indexOf(current.profile) + 1) % profiles.size]
        preferences.saveFocusPolicy(
            plan.id,
            current.copy(
                profile = next,
                quietWindow = current.quietWindow?.copy(timezoneId = ZoneId.systemDefault().id),
            ),
        )
        reconcileNow()
    }

    fun toggleFocusQuietHours() {
        val plan = services.currentDefaultPlanForDesktop() ?: return
        val settings = services.planRepository.currentSettings()[plan.id]
            ?: com.mikeyphw.studyflow.core.model.StudyPlanSettings.defaults(plan.id)
        val current = preferences.focusPolicy(plan.id, settings)
        val nextQuiet = if (current.quietWindow?.enabled == true) {
            null
        } else {
            com.mikeyphw.studyflow.core.link.StudyFlowAttentionQuietWindow(
                enabled = true,
                timezoneId = ZoneId.systemDefault().id,
            )
        }
        preferences.saveFocusPolicy(plan.id, current.copy(quietWindow = nextQuiet))
        // Keep the pre-ATT10 planner quiet-hours fields aligned with ATT10 so desktop does not
        // accidentally maintain two independent quiet-hours truths.
        runBlocking {
            services.planRepository.updatePlanSettings(
                settings.copy(
                    quietHoursStartMinute = nextQuiet?.startMinute,
                    quietHoursEndMinute = nextQuiet?.endMinute,
                ),
            )
        }
        reconcileNow()
    }

    fun toggleCategory(category: DesktopReminderCategoryPreference) {
        val plan = services.currentDefaultPlanForDesktop() ?: return
        val settings = services.planRepository.currentSettings()[plan.id]
            ?: com.mikeyphw.studyflow.core.model.StudyPlanSettings.defaults(plan.id)
        val current = preferences.policy(plan.id, settings)
        val updated = when (category) {
            DesktopReminderCategoryPreference.PLANNED -> current.copy(plannedStudyEnabled = !current.plannedStudyEnabled)
            DesktopReminderCategoryPreference.REVIEWS -> current.copy(dueReviewEnabled = !current.dueReviewEnabled)
            DesktopReminderCategoryPreference.MISSED -> current.copy(missedActivityEnabled = !current.missedActivityEnabled)
            DesktopReminderCategoryPreference.SUMMARY -> current.copy(dailySummaryEnabled = !current.dailySummaryEnabled)
            DesktopReminderCategoryPreference.SESSIONS -> current.copy(sessionTimerEnabled = !current.sessionTimerEnabled)
            DesktopReminderCategoryPreference.EXAM -> current.copy(examCountdownEnabled = !current.examCountdownEnabled)
        }
        preferences.savePolicy(plan.id, updated)
        reconcileNow()
    }

    fun showTestNotification() {
        services.platformPorts.notifications.show(
            DesktopNotification(
                id = "attention-test",
                title = "StudyFlow reminder test",
                message = "Desktop Attention is ready. Reminder delivery works while StudyFlow is running.",
                defaultAction = DesktopAttentionSurfaceAction.OPEN_ATTENTION,
            ),
        )
    }

    private fun deliverDueEntries(now: Long, global: DesktopAttentionGlobalPreferences, planId: StudyPlanId) {
        val capabilities = services.platformPorts.notifications.capabilities()
        val linkService = services.linkService
        val localPeer = linkService?.runtime?.localIdentity?.descriptor
            ?: DesktopAttentionDeliveryRouterBridge.standalonePeer()
        val workspaceId = StudyFlowAttentionWorkspace.forPlanId(planId.value)
        val routingDevices = DesktopAttentionDeliveryRouterBridge.topologyCapabilities(
            localPeer = localPeer,
            nativeNotificationsAvailable = global.nativeNotificationsEnabled && capabilities.nativeNotificationsSupported,
            trayAvailable = global.trayDashboardEnabled && capabilities.traySupported,
            trustedPeers = linkService?.trustedPeers()?.filter { trusted ->
                linkService.runtime.bestEndpoint(trusted.peer.deviceId) != null
            }.orEmpty(),
            presenceViews = linkService?.presenceViews(workspaceId).orEmpty(),
            nowEpochMillis = now,
        )
        attentionLedger.snapshot().entries
            .filter { it.state == StudyFlowAttentionDeliveryState.SCHEDULED }
            .filter { it.occurrence.scheduledAtEpochMillis <= now }
            .sortedBy { it.occurrence.scheduledAtEpochMillis }
            .forEach { entry ->
                val occurrence = entry.occurrence
                if (occurrence.expiresAtEpochMillis?.let { it < now } == true) {
                    runCatching { attentionLedger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.EXPIRED, now) }
                    return@forEach
                }
                val focusPolicy = preferences.focusPolicy(planId, services.planRepository.currentSettings()[planId])
                var eligibleDevices = routingDevices
                var route = DesktopAttentionDeliveryRouterBridge.routeWithPolicy(
                    occurrence = occurrence,
                    devices = eligibleDevices,
                    localDeviceId = localPeer.deviceId,
                    focusPolicy = focusPolicy,
                    nowEpochMillis = now,
                    ledgerEntry = entry,
                )
                val title = occurrence.payload["title"]?.toString()?.trim('"') ?: occurrence.category.desktopTitle()
                val body = occurrence.payload["body"]?.toString()?.trim('"') ?: occurrence.category.desktopBody()
                var remoteOwnerDelivered: Boolean
                while (true) {
                    runCatching { attentionLedger.recordRouteDecision(route) }
                    remoteOwnerDelivered = dispatchRemoteRouteTargets(
                        linkService = linkService,
                        route = route,
                        occurrence = occurrence,
                        localDeviceId = localPeer.deviceId,
                        title = title,
                        body = body,
                        nowEpochMillis = now,
                    )
                    val failedRemoteOwner = route.owner?.deviceId
                        ?.takeIf { it != localPeer.deviceId && !remoteOwnerDelivered }
                        ?: break
                    eligibleDevices = eligibleDevices.filterNot { it.peer.deviceId == failedRemoteOwner }
                    if (eligibleDevices.isEmpty()) break
                    route = DesktopAttentionDeliveryRouterBridge.routeWithPolicy(
                        occurrence = occurrence,
                        devices = eligibleDevices,
                        localDeviceId = localPeer.deviceId,
                        focusPolicy = focusPolicy,
                        nowEpochMillis = now,
                        ledgerEntry = entry,
                    )
                }
                lastCompactCard = DesktopCompactCompanionAttentionBridge.projectMiniDashboard(
                    occurrence = occurrence,
                    route = route,
                    parentDeviceId = localPeer.deviceId,
                    nowEpochMillis = now,
                    ledgerEntry = entry,
                ).cards.firstOrNull()
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
                    lastDeliveredAt = now
                    lastDeliveredTitle = title
                    return@forEach
                }
                // ATT12: ATT07 explicit target ownership is authoritative. A Desktop
                // process that merely has a local ledger copy must not notify when the
                // route selected another device. Leave the entry scheduled for its owner.
                if (!DesktopAttentionDeliveryRouterBridge.isLocalTarget(route, localPeer.deviceId)) return@forEach
                val decision = StudyFlowAttentionFocusPolicyEngine.evaluate(
                    occurrence = occurrence,
                    policy = focusPolicy,
                    nowEpochMillis = now,
                    ledgerEntry = entry,
                    localClientKind = StudyFlowLinkClientKind.DESKTOP,
                )
                lastPolicyOutcome = decision.outcome
                runCatching { attentionLedger.recordPolicyDecision(decision) }
                when (decision.outcome) {
                    StudyFlowAttentionPolicyOutcome.SUPPRESS_DUPLICATE -> return@forEach
                    StudyFlowAttentionPolicyOutcome.DEFER_UNTIL_QUIET_END -> {
                        val deferred = decision.deferredUntilEpochMillis ?: return@forEach
                        val deferredOccurrence = occurrence.copy(scheduledAtEpochMillis = deferred)
                        attentionLedger.upsertScheduled(deferredOccurrence, now)
                        runCatching { attentionLedger.recordPolicyDecision(decision) }
                        return@forEach
                    }
                    StudyFlowAttentionPolicyOutcome.ROUTE_TO_PHONE -> return@forEach
                    StudyFlowAttentionPolicyOutcome.COMPACT_ONLY,
                    StudyFlowAttentionPolicyOutcome.SILENT_DELIVERY -> {
                        // Desktop compact/silent outcomes are consumed by the in-app/tray projection
                        // without raising a native system notification. The ATT01 ledger still
                        // advances so the same occurrence is not re-evaluated every reconcile tick.
                        runCatching {
                            attentionLedger.transition(
                                occurrence.occurrenceId,
                                StudyFlowAttentionDeliveryState.DELIVERY_PENDING,
                                now,
                            )
                            attentionLedger.transition(
                                occurrence.occurrenceId,
                                StudyFlowAttentionDeliveryState.DELIVERED,
                                now,
                            )
                        }
                        lastDeliveredAt = now
                        lastDeliveredTitle = occurrence.payload["title"]?.toString()?.trim('"')
                            ?: occurrence.category.desktopTitle()
                        return@forEach
                    }
                    else -> Unit
                }
                if (!global.nativeNotificationsEnabled || !capabilities.nativeNotificationsSupported) {
                    return@forEach
                }
                runCatching { attentionLedger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERY_PENDING, now) }
                services.platformPorts.notifications.show(
                    DesktopNotification(
                        id = occurrence.occurrenceId.value,
                        title = title,
                        message = body,
                        defaultAction = occurrence.defaultSurfaceAction(),
                    ),
                )
                runCatching { attentionLedger.transition(occurrence.occurrenceId, StudyFlowAttentionDeliveryState.DELIVERED, now) }
                lastDeliveredAt = now
                lastDeliveredTitle = title
            }
    }

    private fun receiveRemoteNotification(delivery: StudyFlowNotificationDelivery): StudyFlowNotificationDeliveryAck {
        val linkService = services.linkService
            ?: return rejectedRemoteNotification(delivery, "link_service_unavailable")
        val global = preferences.global()
        val capabilities = services.platformPorts.notifications.capabilities()
        val localPeer = linkService.runtime.localIdentity.descriptor
        val transportedRoute = delivery.notification.attentionRoute
        val selectedSurface = transportedRoute?.targetFor(localPeer.deviceId)?.surface
        val compactDelivery = transportedRoute?.compactOnly == true ||
            selectedSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE
        if (compactDelivery && (!global.trayDashboardEnabled || !capabilities.traySupported)) {
            return rejectedRemoteNotification(delivery, "compact_surface_unavailable")
        }
        if (
            !compactDelivery &&
            transportedRoute?.deliverSilently != true &&
            (!global.nativeNotificationsEnabled || !capabilities.nativeNotificationsSupported)
        ) {
            return rejectedRemoteNotification(delivery, "native_notifications_unavailable")
        }
        val reducer = StudyFlowNotificationScheduleReducer(
            localDevice = localPeer,
            clock = StudyFlowPeerClock(clock),
            attentionLedger = attentionLedger,
        )
        val ack = reducer.apply(delivery)
        if (ack.status != StudyFlowNotificationDeliveryStatus.ACCEPTED) return ack

        val occurrence = delivery.notification.canonicalAttentionOccurrence(delivery.sourceDeviceId)
        val compactOrSilentRoute = transportedRoute?.takeIf { compactDelivery || it.deliverSilently }
        if (compactOrSilentRoute != null) {
            lastCompactCard = DesktopCompactCompanionAttentionBridge.projectMiniDashboard(
                occurrence = occurrence,
                route = compactOrSilentRoute,
                parentDeviceId = localPeer.deviceId,
                nowEpochMillis = clock(),
                // SFLP admission already performed duplicate suppression and moved the
                // local ledger to DELIVERED; render this just-accepted compact packet once.
                ledgerEntry = null,
            ).cards.firstOrNull()
            lastDeliveredAt = clock()
            lastDeliveredTitle = delivery.notification.title
            updateTray(lastDeliveredAt ?: clock())
            return ack
        }

        services.platformPorts.notifications.show(
            DesktopNotification(
                id = occurrence.occurrenceId.value,
                title = delivery.notification.title,
                message = delivery.notification.body,
                defaultAction = occurrence.defaultSurfaceAction(),
            ),
        )
        lastDeliveredAt = clock()
        lastDeliveredTitle = delivery.notification.title
        updateTray(lastDeliveredAt ?: clock())
        return ack
    }

    private fun rejectedRemoteNotification(
        delivery: StudyFlowNotificationDelivery,
        reason: String,
    ): StudyFlowNotificationDeliveryAck = StudyFlowNotificationDeliveryAck(
        messageId = StudyFlowMessageId("ack-${delivery.messageId.value}"),
        notificationId = delivery.notification.notificationId,
        targetDeviceId = delivery.targetDeviceId,
        acknowledgedSequence = delivery.sequence,
        status = StudyFlowNotificationDeliveryStatus.REJECTED,
        reason = reason,
        acknowledgedAtEpochMillis = clock(),
    )

    private fun dispatchRemoteRouteTargets(
        linkService: DesktopStudyFlowLinkService?,
        route: com.mikeyphw.studyflow.core.link.StudyFlowAttentionDeliveryRoute,
        occurrence: StudyFlowAttentionOccurrence,
        localDeviceId: com.mikeyphw.studyflow.core.link.StudyFlowDeviceId,
        title: String,
        body: String,
        nowEpochMillis: Long,
    ): Boolean {
        if (linkService == null) return route.owner?.deviceId == localDeviceId
        var ownerDelivered = route.owner?.deviceId == localDeviceId
        route.targetDeviceIds.filter { it != localDeviceId }.forEach { targetDeviceId ->
            val endpoint = linkService.runtime.bestEndpoint(targetDeviceId) ?: return@forEach
            val delivery = StudyFlowAttentionNotificationDeliveryFactory.create(
                occurrence = occurrence,
                sourceDeviceId = localDeviceId,
                targetDeviceId = targetDeviceId,
                title = title,
                body = body,
                nowEpochMillis = nowEpochMillis,
                deliveryRoute = route,
            )
            val ack = runCatching {
                linkService.runtime.deliverNotification(occurrence.workspaceId, targetDeviceId, delivery, endpoint)
            }.getOrNull()
            val accepted = ack?.status in setOf(
                StudyFlowNotificationDeliveryStatus.ACCEPTED,
                StudyFlowNotificationDeliveryStatus.DUPLICATE,
            )
            if (route.owner?.deviceId == targetDeviceId) ownerDelivered = accepted
        }
        return ownerDelivered
    }

    private fun updateTray(now: Long) {
        val global = preferences.global()
        if (!global.trayDashboardEnabled) {
            services.platformPorts.notifications.updateTray(null)
            return
        }
        val plan = services.currentDefaultPlanForDesktop()
        val planId = plan?.id
        val activities = planId?.let(services.scheduleRepository::currentActivities).orEmpty()
        val next = activities
            .filter { it.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED) }
            .filter { activity ->
                val scheduledStart = activity.scheduledStartEpochMillis
                scheduledStart == null || scheduledStart >= now - 2 * 60 * 60 * 1_000L
            }
            .minByOrNull { it.scheduledStartEpochMillis ?: Long.MAX_VALUE }
        val activeSession = planId?.let(services.sessionRepository::currentSessions)
            ?.firstOrNull { it.status in setOf(StudySessionStatus.RUNNING, StudySessionStatus.PAUSED) }
        val dueReviews = planId?.let { runBlocking { services.manageStudyReview.observeQueue(it).first().dueCount } } ?: 0
        services.platformPorts.notifications.updateTray(
            DesktopTrayDashboard(
                headline = plan?.name ?: "StudyFlow",
                nextStudy = lastCompactCard?.let { "Attention: ${it.title} · ${it.body}" }
                    ?: next?.let { item ->
                        val time = item.scheduledStartEpochMillis
                            ?.let(::formatDesktopTime)
                            ?.let { " · $it" }
                            .orEmpty()
                        "Next: ${item.title}$time"
                    }
                    ?: "No upcoming study block",
                dueReviews = "$dueReviews review${if (dueReviews == 1) "" else "s"} due",
                activeSession = activeSession?.let { "${it.status.name.lowercase().replaceFirstChar(Char::uppercase)} session" },
                progress = plan?.let { "${it.progressPercent}% plan progress" } ?: "No active plan",
                remindersPaused = global.paused || !global.remindersEnabled,
            ),
        )
    }

    private fun handleSurfaceAction(action: DesktopAttentionSurfaceAction, notificationId: String?) {
        val state = appState
        when (action) {
            DesktopAttentionSurfaceAction.OPEN_ATTENTION -> openOccurrence(notificationId)
            DesktopAttentionSurfaceAction.OPEN_TODAY -> state?.selectRoute(DesktopRoute.DASHBOARD)
            DesktopAttentionSurfaceAction.START_NEXT -> state?.startNextSessionActivity()
            DesktopAttentionSurfaceAction.REVIEW_DUE -> state?.selectRoute(DesktopRoute.REVIEWS)
            DesktopAttentionSurfaceAction.TOGGLE_PAUSE -> togglePaused()
            DesktopAttentionSurfaceAction.OPEN_NOTIFICATION_CENTER -> state?.selectRoute(DesktopRoute.SETTINGS)
            DesktopAttentionSurfaceAction.SNOOZE_CURRENT -> notificationId?.let(::snooze)
            DesktopAttentionSurfaceAction.DISMISS_CURRENT -> notificationId?.let(::dismiss)
        }
    }

    private fun openOccurrence(notificationId: String?) {
        val occurrence = notificationId?.let { attentionLedger.entry(StudyFlowAttentionOccurrenceId.fromLegacy(it))?.occurrence }
        when (occurrence?.category) {
            StudyFlowAttentionCategory.DUE_REVIEW -> appState?.selectRoute(DesktopRoute.REVIEWS)
            StudyFlowAttentionCategory.EXAM_COUNTDOWN -> appState?.selectRoute(DesktopRoute.PUBLIC_EXAM)
            StudyFlowAttentionCategory.DAILY_SUMMARY -> appState?.selectRoute(DesktopRoute.DASHBOARD)
            else -> appState?.selectRoute(DesktopRoute.PLANNER)
        }
        notificationId?.let { id ->
            val ledgerId = StudyFlowAttentionOccurrenceId.fromLegacy(id)
            val current = attentionLedger.entry(ledgerId)
            if (current?.state == StudyFlowAttentionDeliveryState.DELIVERED) {
                runCatching { attentionLedger.transition(ledgerId, StudyFlowAttentionDeliveryState.ACKNOWLEDGED, clock()) }
            }
        }
    }

    private fun snooze(notificationId: String) {
        val id = StudyFlowAttentionOccurrenceId.fromLegacy(notificationId)
        val current = attentionLedger.entry(id) ?: return
        val now = clock()
        if (current.state !in setOf(StudyFlowAttentionDeliveryState.DELIVERED, StudyFlowAttentionDeliveryState.ACKNOWLEDGED)) return
        runCatching { attentionLedger.transition(id, StudyFlowAttentionDeliveryState.SNOOZED, now) }
        val planId = services.currentDefaultPlanForDesktop()?.id
        val settings = planId?.let { services.planRepository.currentSettings()[it] }
        val minutes = if (planId != null && settings != null) preferences.policy(planId, settings).defaultSnoozeMinutes else 10
        val snoozeAt = now + minutes * 60_000L
        val next = current.occurrence.copy(
            occurrenceId = StudyFlowAttentionOccurrenceId.deterministic("snooze", id.value, snoozeAt.toString()),
            scheduledAtEpochMillis = snoozeAt,
            originalScheduledAtEpochMillis = current.occurrence.originalScheduledAtEpochMillis,
            payload = JsonObject(current.occurrence.payload + ("snoozedFrom" to JsonPrimitive(id.value))),
        )
        attentionLedger.upsertScheduled(next, now)
        services.platformPorts.notifications.dismiss(notificationId)
    }

    private fun dismiss(notificationId: String) {
        val id = StudyFlowAttentionOccurrenceId.fromLegacy(notificationId)
        val current = attentionLedger.entry(id) ?: return
        if (current.state in setOf(StudyFlowAttentionDeliveryState.DELIVERED, StudyFlowAttentionDeliveryState.ACKNOWLEDGED)) {
            runCatching { attentionLedger.transition(id, StudyFlowAttentionDeliveryState.DISMISSED, clock()) }
        }
        services.platformPorts.notifications.dismiss(notificationId)
    }

    override fun close() {
        started = false
        services.linkService?.runtime?.setNotificationDeliveryHandler(null)
        scheduler.shutdownNow()
        services.platformPorts.notifications.close()
    }

    private companion object {
        val handledStates = setOf(
            StudyFlowAttentionDeliveryState.DELIVERED,
            StudyFlowAttentionDeliveryState.ACKNOWLEDGED,
            StudyFlowAttentionDeliveryState.STARTED,
            StudyFlowAttentionDeliveryState.COMPLETED,
            StudyFlowAttentionDeliveryState.DISMISSED,
            StudyFlowAttentionDeliveryState.EXPIRED,
            StudyFlowAttentionDeliveryState.CANCELLED,
        )
    }
}

private fun ReminderNotificationCandidate.toDesktopAttentionOccurrence(planId: StudyPlanId): StudyFlowAttentionOccurrence {
    val (title, body) = desktopReminderCopy()
    return StudyFlowAttentionOccurrence(
        occurrenceId = StudyFlowAttentionOccurrenceId.fromLegacy(occurrenceId),
        workspaceId = StudyFlowAttentionWorkspace.forPlanId(planId.value),
        scheduleEntityId = content.targetId?.let(::StudyFlowEntityId),
        category = when (type) {
            ReminderNotificationType.DUE_REVIEWS -> StudyFlowAttentionCategory.DUE_REVIEW
            ReminderNotificationType.PLANNED_ACTIVITY -> StudyFlowAttentionCategory.PLANNED_STUDY
            ReminderNotificationType.MISSED_ACTIVITY -> StudyFlowAttentionCategory.MISSED_ACTIVITY
            ReminderNotificationType.DAILY_SUMMARY -> StudyFlowAttentionCategory.DAILY_SUMMARY
            ReminderNotificationType.SESSION_ENDING -> StudyFlowAttentionCategory.SESSION_ENDING
            ReminderNotificationType.SESSION_COMPLETED -> StudyFlowAttentionCategory.SESSION_COMPLETED
            ReminderNotificationType.EXAM_COUNTDOWN -> StudyFlowAttentionCategory.EXAM_COUNTDOWN
        },
        priority = if (type in setOf(ReminderNotificationType.SESSION_ENDING, ReminderNotificationType.SESSION_COMPLETED)) {
            StudyFlowAttentionPriority.TIME_SENSITIVE
        } else StudyFlowAttentionPriority.NORMAL,
        scheduledAtEpochMillis = fireAtEpochMillis,
        originalScheduledAtEpochMillis = scheduledAtEpochMillis,
        expiresAtEpochMillis = fireAtEpochMillis + DesktopAttentionExpiryMillis,
        timezoneId = ZoneId.systemDefault().id,
        actions = desktopAttentionActions(),
        payload = JsonObject(
            buildMap {
                put("source", JsonPrimitive("desktop-planner"))
                put("legacyType", JsonPrimitive(type.name))
                put("title", JsonPrimitive(title))
                put("body", JsonPrimitive(body))
                content.targetId?.let { put("targetId", JsonPrimitive(it)) }
                content.count?.let { put("count", JsonPrimitive(it)) }
            },
        ),
    )
}

private fun ReminderNotificationCandidate.desktopAttentionActions(): List<StudyFlowAttentionAction> = when (type) {
    ReminderNotificationType.DUE_REVIEWS -> listOf(StudyFlowAttentionActionKind.REVIEW_NOW, StudyFlowAttentionActionKind.SNOOZE)
    ReminderNotificationType.PLANNED_ACTIVITY -> listOf(StudyFlowAttentionActionKind.START_NOW, StudyFlowAttentionActionKind.SNOOZE, StudyFlowAttentionActionKind.RESCHEDULE)
    ReminderNotificationType.MISSED_ACTIVITY -> listOf(StudyFlowAttentionActionKind.RESCHEDULE, StudyFlowAttentionActionKind.MARK_DONE)
    ReminderNotificationType.DAILY_SUMMARY -> listOf(StudyFlowAttentionActionKind.OPEN)
    ReminderNotificationType.SESSION_ENDING, ReminderNotificationType.SESSION_COMPLETED -> listOf(StudyFlowAttentionActionKind.CONTINUE, StudyFlowAttentionActionKind.SNOOZE)
    ReminderNotificationType.EXAM_COUNTDOWN -> listOf(StudyFlowAttentionActionKind.OPEN, StudyFlowAttentionActionKind.SNOOZE)
}.map(::StudyFlowAttentionAction)

private fun ReminderNotificationCandidate.desktopReminderCopy(): Pair<String, String> = when (content.kind) {
    ReminderNotificationContentKind.DUE_REVIEWS -> "Reviews due" to "${content.count ?: 0} review(s) are ready."
    ReminderNotificationContentKind.PLANNED_ACTIVITY -> "Study block coming up" to (content.userLabel ?: "Your next study block is ready.")
    ReminderNotificationContentKind.MISSED_ACTIVITY -> "Missed study activity" to "${content.count ?: 1} activity item(s) need attention."
    ReminderNotificationContentKind.DAILY_SUMMARY -> "StudyFlow daily brief" to "Open Today to review your plan and due work."
    ReminderNotificationContentKind.SESSION_ENDING -> "Session ending soon" to "Your current StudyFlow session is nearing its target."
    ReminderNotificationContentKind.SESSION_COMPLETED -> "Session target reached" to "Finish, continue, or start a break in StudyFlow."
    ReminderNotificationContentKind.BREAK_COMPLETED -> "Break complete" to "Ready to continue studying?"
    ReminderNotificationContentKind.EXAM_COUNTDOWN -> "Exam countdown" to "${content.count ?: 0} day(s) remaining for ${content.userLabel ?: "your exam"}."
}

private fun StudyFlowAttentionOccurrence.defaultSurfaceAction(): DesktopAttentionSurfaceAction = when (category) {
    StudyFlowAttentionCategory.DUE_REVIEW -> DesktopAttentionSurfaceAction.REVIEW_DUE
    StudyFlowAttentionCategory.PLANNED_STUDY -> DesktopAttentionSurfaceAction.START_NEXT
    else -> DesktopAttentionSurfaceAction.OPEN_ATTENTION
}

private fun StudyFlowAttentionCategory.desktopTitle(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun StudyFlowAttentionCategory.desktopBody(): String = "Open StudyFlow to continue."
private fun formatDesktopTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
