package com.mikeyphw.studyflow.feature.reminders

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ManageStudySession
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.ReminderPlatformRuntimeEvidence
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityCenter
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityFallback
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityInput
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityIssue
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityIssueKind
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityStatus
import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.StudyReminderPolicy
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionEscalationMode
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusPolicy
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionFocusProfile
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionQuietWindow
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionReliabilityEvidenceSnapshot
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySchedule
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val PostNotificationsPermission = "android.permission.POST_NOTIFICATIONS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// ATT10 focus policy plus ATT11 notification reliability evidence and delivery-audit projection.
fun StudyRemindersScreen(
    plan: StudyPlan,
    settings: StudyPlanSettings,
    reminderPolicy: StudyReminderPolicy,
    attentionFocusPolicy: StudyFlowAttentionFocusPolicy,
    runtimeEvidence: ReminderPlatformRuntimeEvidence,
    attentionReliabilityEvidence: StudyFlowAttentionReliabilityEvidenceSnapshot,
    schedule: ManageStudySchedule,
    review: ManageStudyReview,
    sessions: ManageStudySession,
    onReminderSettingsChanged: (StudyPlanSettings, StudyReminderPolicy) -> Unit,
    onAttentionFocusPolicyChanged: (StudyFlowAttentionFocusPolicy) -> Unit,
    onRefreshReliability: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNotificationPermissionResult: (Boolean) -> Unit = {},
    onCopyAttentionAuditJson: () -> Unit = {},
    onCopyAttentionAuditCsv: () -> Unit = {},
) {
    val currentSchedule by schedule.observeSchedule(plan.id).collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val queue by review.observeQueue(plan.id).collectAsStateWithLifecycle(
        initialValue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
    )
    val activeSessions by sessions.observeSessions(plan.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    var notificationPermissionGranted by rememberSaveable {
        mutableStateOf(context.hasPostNotificationsPermission())
    }
    val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val notificationDeliveryAllowed = notificationPermissionGranted && appNotificationsEnabled
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
        onNotificationPermissionResult(granted)
    }

    var draftPolicy by remember(plan.id.value, reminderPolicy) { mutableStateOf(reminderPolicy) }
    var draftFocusPolicy by remember(plan.id.value, attentionFocusPolicy) { mutableStateOf(attentionFocusPolicy) }
    var quietHoursEnabled by rememberSaveable(plan.id.value, settings.quietHoursStartMinute) {
        mutableStateOf(settings.quietHoursStartMinute != null)
    }
    var quietHoursStart by rememberSaveable(plan.id.value, settings.quietHoursStartMinute) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursStartMinute ?: 22 * 60))
    }
    var quietHoursEnd by rememberSaveable(plan.id.value, settings.quietHoursEndMinute) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursEndMinute ?: 7 * 60))
    }
    var leadMinutes by rememberSaveable(plan.id.value, reminderPolicy.plannedStudyLeadMinutes) {
        mutableStateOf(reminderPolicy.plannedStudyLeadMinutes.toString())
    }
    var dailySummaryTime by rememberSaveable(plan.id.value, reminderPolicy.dailySummaryMinuteOfDay) {
        mutableStateOf(formatMinuteOfDay(reminderPolicy.dailySummaryMinuteOfDay))
    }
    var missedDelayMinutes by rememberSaveable(plan.id.value, reminderPolicy.missedActivityDelayMinutes) {
        mutableStateOf(reminderPolicy.missedActivityDelayMinutes.toString())
    }
    var snoozeMinutes by rememberSaveable(plan.id.value, reminderPolicy.defaultSnoozeMinutes) {
        mutableStateOf(reminderPolicy.defaultSnoozeMinutes.toString())
    }
    var sessionLeadMinutes by rememberSaveable(plan.id.value, reminderPolicy.sessionEndingLeadMinutes) {
        mutableStateOf(reminderPolicy.sessionEndingLeadMinutes.toString())
    }
    var examCountdownDays by rememberSaveable(plan.id.value, reminderPolicy.examCountdownDays) {
        mutableStateOf(reminderPolicy.examCountdownDays.toString())
    }

    val startMinute = parseMinuteOfDay(quietHoursStart)
    val endMinute = parseMinuteOfDay(quietHoursEnd)
    val parsedLead = leadMinutes.toIntOrNull()?.takeIf { it in 0..120 }
    val parsedSummaryMinute = parseMinuteOfDay(dailySummaryTime)
    val parsedMissedDelay = missedDelayMinutes.toIntOrNull()?.takeIf { it in 5..720 }
    val parsedSnooze = snoozeMinutes.toIntOrNull()?.takeIf { it in 5..240 }
    val parsedSessionLead = sessionLeadMinutes.toIntOrNull()?.takeIf { it in 1..30 }
    val parsedExamDays = examCountdownDays.toIntOrNull()?.takeIf { it in 1..60 }
    val settingsValid = (!quietHoursEnabled || (startMinute != null && endMinute != null && startMinute != endMinute)) &&
        parsedLead != null && parsedSummaryMinute != null && parsedMissedDelay != null &&
        parsedSnooze != null && parsedSessionLead != null && parsedExamDays != null

    val previewPolicy = if (settingsValid) {
        draftPolicy.copy(
            plannedStudyLeadMinutes = requireNotNull(parsedLead),
            dailySummaryMinuteOfDay = requireNotNull(parsedSummaryMinute),
            missedActivityDelayMinutes = requireNotNull(parsedMissedDelay),
            defaultSnoozeMinutes = requireNotNull(parsedSnooze),
            sessionEndingLeadMinutes = requireNotNull(parsedSessionLead),
            examCountdownDays = requireNotNull(parsedExamDays),
        )
    } else {
        draftPolicy
    }
    val previewSettings = settings.copy(
        reviewNotificationsEnabled = previewPolicy.dueReviewEnabled,
        quietHoursStartMinute = if (quietHoursEnabled) startMinute else null,
        quietHoursEndMinute = if (quietHoursEnabled) endMinute else null,
    )
    val now = remember(currentSchedule, queue, activeSessions, previewSettings, previewPolicy, notificationDeliveryAllowed) {
        System.currentTimeMillis()
    }
    val reminderPlan = remember(currentSchedule, queue, activeSessions, previewSettings, previewPolicy, now) {
        StudyReminderPlanner.buildPlan(
            StudyReminderInput(
                plan = plan,
                settings = previewSettings,
                schedule = currentSchedule,
                reviewQueue = queue,
                nowEpochMillis = now,
                minuteOfDay = currentMinuteOfDay(),
                apiLevel = Build.VERSION.SDK_INT,
                notificationPermissionGranted = notificationDeliveryAllowed,
                policy = previewPolicy,
                activeSessions = activeSessions,
            ),
        )
    }
    val reliabilityReport = remember(
        reminderPlan,
        notificationPermissionGranted,
        appNotificationsEnabled,
        runtimeEvidence,
        now,
    ) {
        ReminderReliabilityCenter.inspect(
            ReminderReliabilityInput(
                notificationPermissionGranted = notificationPermissionGranted,
                appNotificationsEnabled = appNotificationsEnabled,
                notificationChannelCount = runtimeEvidence.notificationChannelCount,
                blockedNotificationChannelCount = runtimeEvidence.blockedNotificationChannelCount,
                batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations(),
                exactAlarmCapabilityAvailable = runtimeEvidence.exactAlarmCapabilityAvailable,
                exactAlarmRequired = runtimeEvidence.exactAlarmRequired,
                quietHoursActive = reminderPlan.quietHoursActive,
                activeCandidateCount = reminderPlan.activeCandidateCount,
                dueReviewCount = reminderPlan.widgetSummary.dueReviewCount,
                missedActivityCount = reminderPlan.widgetSummary.missedActivityCount,
                plannedActivityCandidateCount = reminderPlan.notificationCandidates.count { it.type == ReminderNotificationType.PLANNED_ACTIVITY },
                nextCandidateAtEpochMillis = reminderPlan.notificationCandidates.minOfOrNull(ReminderNotificationCandidate::fireAtEpochMillis),
                lastReconciledAtEpochMillis = runtimeEvidence.lastReconciledAtEpochMillis,
                lastDeliveredAtEpochMillis = runtimeEvidence.lastDeliveredAtEpochMillis,
                lastDeliveredOccurrenceId = runtimeEvidence.lastDeliveredOccurrenceId,
                lastRecoveryReason = runtimeEvidence.lastRecoveryReason,
                nowEpochMillis = now,
            ),
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StudyFlowSectionCard(title = plan.name) {
                Text(
                    text = pluralStringResource(
                        R.plurals.reminders_summary,
                        reminderPlan.activeCandidateCount,
                        reminderPlan.activeCandidateCount,
                        reminderPlan.widgetSummary.dueReviewCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reminderPlan.quietHoursActive) {
                    StudyFlowStatusPill(
                        text = stringResource(R.string.quiet_hours_active),
                        leadingIcon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    )
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.notification_permission_title)) {
                Text(
                    text = when {
                        !notificationPermissionGranted -> stringResource(R.string.notification_permission_needed)
                        !appNotificationsEnabled -> stringResource(R.string.notification_permission_disabled_by_system)
                        else -> stringResource(R.string.notification_permission_ready)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { permissionLauncher.launch(PostNotificationsPermission) }) {
                        Text(stringResource(R.string.request_notification_permission))
                    }
                }
                OutlinedButton(onClick = { context.openAppNotificationSettings() }) {
                    Text(stringResource(R.string.open_notification_settings))
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.reminder_categories_title)) {
                PreferenceSwitch(
                    title = stringResource(R.string.planned_study_reminders),
                    description = stringResource(R.string.planned_study_reminders_description),
                    checked = draftPolicy.plannedStudyEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(plannedStudyEnabled = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.review_reminders),
                    description = stringResource(R.string.review_reminders_description),
                    checked = draftPolicy.dueReviewEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(dueReviewEnabled = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.missed_activity_reminders),
                    description = stringResource(R.string.missed_activity_reminders_description),
                    checked = draftPolicy.missedActivityEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(missedActivityEnabled = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.daily_summary_reminders),
                    description = stringResource(R.string.daily_summary_reminders_description),
                    checked = draftPolicy.dailySummaryEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(dailySummaryEnabled = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.session_timer_reminders),
                    description = stringResource(R.string.session_timer_reminders_description),
                    checked = draftPolicy.sessionTimerEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(sessionTimerEnabled = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.exam_countdown_reminders),
                    description = stringResource(R.string.exam_countdown_reminders_description),
                    checked = draftPolicy.examCountdownEnabled,
                    onCheckedChange = { draftPolicy = draftPolicy.copy(examCountdownEnabled = it) },
                )
            }

            StudyFlowSectionCard(title = stringResource(R.string.att10_focus_policy_title)) {
                Text(
                    text = stringResource(R.string.att10_focus_policy_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StudyFlowAttentionFocusProfile.entries.forEach { profile ->
                    OutlinedButton(
                        onClick = { draftFocusPolicy = draftFocusPolicy.copy(profile = profile) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (draftFocusPolicy.profile == profile) {
                                "✓ ${focusProfileLabel(profile)}"
                            } else {
                                focusProfileLabel(profile)
                            },
                        )
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.att10_high_priority_bypass),
                    description = stringResource(R.string.att10_high_priority_bypass_description),
                    checked = draftFocusPolicy.allowHighPriorityQuietBypass,
                    onCheckedChange = { draftFocusPolicy = draftFocusPolicy.copy(allowHighPriorityQuietBypass = it) },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.att10_time_sensitive_bypass),
                    description = stringResource(R.string.att10_time_sensitive_bypass_description),
                    checked = draftFocusPolicy.allowTimeSensitiveQuietBypass,
                    onCheckedChange = { draftFocusPolicy = draftFocusPolicy.copy(allowTimeSensitiveQuietBypass = it) },
                )
                Text(
                    text = stringResource(R.string.att10_category_escalation_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                att10EditableCategories.forEach { category ->
                    val current = draftFocusPolicy.categoryPolicy(category)
                    OutlinedButton(
                        onClick = {
                            val next = current.copy(mode = current.mode.nextAtt10Mode())
                            draftFocusPolicy = draftFocusPolicy.copy(
                                categoryPolicies = draftFocusPolicy.categoryPolicies.map { if (it.category == category) next else it },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${attentionCategoryLabel(category)} · ${escalationModeLabel(current.mode)}")
                    }
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.reminder_timing_title)) {
                NumberPreferenceField(
                    label = stringResource(R.string.planned_study_lead_minutes),
                    value = leadMinutes,
                    onValueChange = { leadMinutes = it },
                    valid = parsedLead != null,
                    supporting = stringResource(R.string.planned_study_lead_minutes_hint),
                )
                OutlinedTextField(
                    value = dailySummaryTime,
                    onValueChange = { dailySummaryTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.daily_summary_time)) },
                    supportingText = { Text(stringResource(R.string.daily_summary_time_hint)) },
                    isError = parsedSummaryMinute == null,
                    singleLine = true,
                )
                NumberPreferenceField(
                    label = stringResource(R.string.missed_activity_delay_minutes),
                    value = missedDelayMinutes,
                    onValueChange = { missedDelayMinutes = it },
                    valid = parsedMissedDelay != null,
                    supporting = stringResource(R.string.missed_activity_delay_minutes_hint),
                )
                NumberPreferenceField(
                    label = stringResource(R.string.default_snooze_minutes),
                    value = snoozeMinutes,
                    onValueChange = { snoozeMinutes = it },
                    valid = parsedSnooze != null,
                    supporting = stringResource(R.string.default_snooze_minutes_hint),
                )
                NumberPreferenceField(
                    label = stringResource(R.string.session_ending_lead_minutes),
                    value = sessionLeadMinutes,
                    onValueChange = { sessionLeadMinutes = it },
                    valid = parsedSessionLead != null,
                    supporting = stringResource(R.string.session_ending_lead_minutes_hint),
                )
                NumberPreferenceField(
                    label = stringResource(R.string.exam_countdown_days),
                    value = examCountdownDays,
                    onValueChange = { examCountdownDays = it },
                    valid = parsedExamDays != null,
                    supporting = stringResource(R.string.exam_countdown_days_hint),
                )
            }

            StudyFlowSectionCard(title = stringResource(R.string.quiet_hours)) {
                PreferenceSwitch(
                    title = stringResource(R.string.quiet_hours),
                    description = stringResource(R.string.quiet_hours_description),
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                )
                if (quietHoursEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = quietHoursStart,
                            onValueChange = { quietHoursStart = it },
                            label = { Text(stringResource(R.string.quiet_hours_start)) },
                            isError = startMinute == null,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = quietHoursEnd,
                            onValueChange = { quietHoursEnd = it },
                            label = { Text(stringResource(R.string.quiet_hours_end)) },
                            isError = endMinute == null,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (!settingsValid) return@Button
                    val savedPolicy = draftPolicy.copy(
                        plannedStudyLeadMinutes = requireNotNull(parsedLead),
                        dailySummaryMinuteOfDay = requireNotNull(parsedSummaryMinute),
                        missedActivityDelayMinutes = requireNotNull(parsedMissedDelay),
                        defaultSnoozeMinutes = requireNotNull(parsedSnooze),
                        sessionEndingLeadMinutes = requireNotNull(parsedSessionLead),
                        examCountdownDays = requireNotNull(parsedExamDays),
                    )
                    draftPolicy = savedPolicy
                    onReminderSettingsChanged(
                        settings.copy(
                            reviewNotificationsEnabled = savedPolicy.dueReviewEnabled,
                            quietHoursStartMinute = if (quietHoursEnabled) requireNotNull(startMinute) else null,
                            quietHoursEndMinute = if (quietHoursEnabled) requireNotNull(endMinute) else null,
                        ),
                        savedPolicy,
                    )
                    val savedFocusPolicy = draftFocusPolicy.copy(
                        quietWindow = if (quietHoursEnabled) {
                            StudyFlowAttentionQuietWindow(
                                enabled = true,
                                startMinute = requireNotNull(startMinute),
                                endMinute = requireNotNull(endMinute),
                                timezoneId = TimeZone.getDefault().id,
                            )
                        } else null,
                    )
                    draftFocusPolicy = savedFocusPolicy
                    onAttentionFocusPolicyChanged(savedFocusPolicy)
                },
                enabled = settingsValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_reminder_settings))
            }

            StudyFlowSectionCard(title = stringResource(R.string.reminder_reliability_title)) {
                StudyFlowStatusPill(text = stringResource(reliabilityReport.status.labelRes()))
                Text(
                    text = stringResource(reliabilityReport.fallback.messageRes()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reliabilityReport.nextCandidateAtEpochMillis?.let {
                    Text(stringResource(R.string.reminder_next_candidate, formatEpoch(it)))
                }
                Text(
                    stringResource(
                        R.string.reminder_channels_status,
                        runtimeEvidence.notificationChannelCount - runtimeEvidence.blockedNotificationChannelCount,
                        runtimeEvidence.notificationChannelCount,
                    ),
                )
                Text(
                    if (runtimeEvidence.exactAlarmRequired) {
                        stringResource(R.string.reminder_exact_alarm_required)
                    } else {
                        stringResource(R.string.reminder_exact_alarm_not_required)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                runtimeEvidence.lastReconciledAtEpochMillis?.let {
                    Text(stringResource(R.string.reminder_last_reconciled, formatEpoch(it)))
                }
                runtimeEvidence.lastDeliveredAtEpochMillis?.let {
                    Text(stringResource(R.string.reminder_last_delivered, formatEpoch(it)))
                }
                runtimeEvidence.lastRecoveryReason?.let {
                    Text(stringResource(R.string.reminder_last_recovery_reason, it))
                }
                reliabilityReport.issues.forEach { ReminderReliabilityIssueRow(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefreshReliability) {
                        Text(stringResource(R.string.refresh_reliability))
                    }
                    OutlinedButton(onClick = { context.openBatteryOptimizationSettings() }) {
                        Text(stringResource(R.string.open_battery_settings))
                    }
                }
                OutlinedButton(onClick = { context.openAppDetailsSettings() }) {
                    Text(stringResource(R.string.open_app_settings))
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.att11_delivery_audit_title)) {
                val analytics = attentionReliabilityEvidence.analytics
                Text(
                    text = stringResource(R.string.att11_delivery_audit_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.att11_delivery_metrics,
                        analytics.totalCount,
                        analytics.deliveredCount,
                        analytics.deferredCount,
                        analytics.missedCount,
                        analytics.snoozedCount,
                        analytics.dismissedCount,
                        analytics.doneCount,
                    ),
                )
                attentionReliabilityEvidence.platforms.firstOrNull()?.let { platform ->
                    Text(
                        stringResource(
                            R.string.att11_platform_reliability,
                            platform.clientKind.name.replace('_', ' '),
                            platform.status.name.replace('_', ' '),
                            platform.ownerRouteCount,
                            platform.mirrorRouteCount,
                        ),
                    )
                    Text(
                        platform.explanation,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (attentionReliabilityEvidence.routeAudits.isEmpty()) {
                    Text(
                        stringResource(R.string.att11_no_route_audit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    attentionReliabilityEvidence.routeAudits.take(5).forEach { audit ->
                        Text(
                            stringResource(
                                R.string.att11_route_audit_line,
                                audit.reason.name.replace('_', ' '),
                                audit.owner?.deviceId?.value ?: "ledger-only",
                                audit.mirrors.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCopyAttentionAuditJson) {
                        Text(stringResource(R.string.att11_copy_json))
                    }
                    OutlinedButton(onClick = onCopyAttentionAuditCsv) {
                        Text(stringResource(R.string.att11_copy_csv))
                    }
                }
            }

            StudyFlowSectionCard(title = stringResource(R.string.notification_preview_title)) {
                if (reminderPlan.notificationCandidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_notification_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    reminderPlan.notificationCandidates.forEach { ReminderCandidateRow(it) }
                }
            }
            WidgetPreviewCard(reminderPlan.widgetSummary)
        }
    }
}

private val att10EditableCategories = listOf(
    StudyFlowAttentionCategory.PLANNED_STUDY,
    StudyFlowAttentionCategory.DUE_REVIEW,
    StudyFlowAttentionCategory.MISSED_ACTIVITY,
    StudyFlowAttentionCategory.DAILY_SUMMARY,
    StudyFlowAttentionCategory.SESSION_ENDING,
    StudyFlowAttentionCategory.EXAM_COUNTDOWN,
)

private fun StudyFlowAttentionEscalationMode.nextAtt10Mode(): StudyFlowAttentionEscalationMode {
    val order = listOf(
        StudyFlowAttentionEscalationMode.NORMAL,
        StudyFlowAttentionEscalationMode.SILENT,
        StudyFlowAttentionEscalationMode.DEFER,
        StudyFlowAttentionEscalationMode.COMPACT_ONLY,
        StudyFlowAttentionEscalationMode.ROUTE_TO_PHONE,
        StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED,
        StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION,
    )
    return order[(order.indexOf(this).coerceAtLeast(0) + 1) % order.size]
}

private fun focusProfileLabel(profile: StudyFlowAttentionFocusProfile): String = when (profile) {
    StudyFlowAttentionFocusProfile.BALANCED -> "Balanced"
    StudyFlowAttentionFocusProfile.EXAM_MODE -> "Exam mode"
    StudyFlowAttentionFocusProfile.WORK_MODE -> "Work mode"
    StudyFlowAttentionFocusProfile.SLEEP_MODE -> "Sleep mode"
    StudyFlowAttentionFocusProfile.TRAVEL_OFFLINE -> "Travel / offline"
    StudyFlowAttentionFocusProfile.MINIMAL_TV -> "Minimal / TV"
}

private fun attentionCategoryLabel(category: StudyFlowAttentionCategory): String = when (category) {
    StudyFlowAttentionCategory.PLANNED_STUDY -> "Study blocks"
    StudyFlowAttentionCategory.DUE_REVIEW -> "Reviews"
    StudyFlowAttentionCategory.MISSED_ACTIVITY -> "Missed activities"
    StudyFlowAttentionCategory.DAILY_SUMMARY -> "Daily summary"
    StudyFlowAttentionCategory.SESSION_ENDING, StudyFlowAttentionCategory.SESSION_COMPLETED -> "Sessions"
    StudyFlowAttentionCategory.EXAM_COUNTDOWN -> "Exam countdown"
    StudyFlowAttentionCategory.SCHEDULE_CHANGE -> "Schedule changes"
    StudyFlowAttentionCategory.PLAN_WARNING -> "Plan warnings"
    StudyFlowAttentionCategory.SYNC_ATTENTION -> "Sync attention"
}

private fun escalationModeLabel(mode: StudyFlowAttentionEscalationMode): String = when (mode) {
    StudyFlowAttentionEscalationMode.NORMAL -> "Normal"
    StudyFlowAttentionEscalationMode.SILENT -> "Silent"
    StudyFlowAttentionEscalationMode.DEFER -> "Defer"
    StudyFlowAttentionEscalationMode.COMPACT_ONLY -> "Compact only"
    StudyFlowAttentionEscalationMode.ROUTE_TO_PHONE -> "Route to phone"
    StudyFlowAttentionEscalationMode.MIRROR_IF_UNHANDLED -> "Mirror if unhandled"
    StudyFlowAttentionEscalationMode.REQUIRE_USER_ACTION -> "Require action"
}

@Composable
private fun NumberPreferenceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    valid: Boolean,
    supporting: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supporting) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !valid,
        singleLine = true,
    )
}

@Composable
private fun ReminderCandidateRow(candidate: ReminderNotificationCandidate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(candidate.type.labelRes()), style = MaterialTheme.typography.titleMedium)
        Text(text = reminderCandidateTitle(candidate))
        Text(
            text = reminderCandidateBody(candidate),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.reminder_candidate_time, formatEpoch(candidate.fireAtEpochMillis)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (candidate.delayedByQuietHours) {
            Text(
                text = stringResource(R.string.delayed_by_quiet_hours),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun reminderCandidateTitle(candidate: ReminderNotificationCandidate): String = when (candidate.content.kind) {
    ReminderNotificationContentKind.DUE_REVIEWS -> stringResource(R.string.reminder_due_reviews_title)
    ReminderNotificationContentKind.PLANNED_ACTIVITY -> candidate.content.userLabel.orEmpty()
    ReminderNotificationContentKind.MISSED_ACTIVITY -> stringResource(R.string.reminder_missed_activity_title)
    ReminderNotificationContentKind.DAILY_SUMMARY -> candidate.content.userLabel.orEmpty()
    ReminderNotificationContentKind.SESSION_ENDING -> stringResource(R.string.reminder_session_ending_title)
    ReminderNotificationContentKind.SESSION_COMPLETED -> stringResource(R.string.reminder_session_completed_title)
    ReminderNotificationContentKind.BREAK_COMPLETED -> stringResource(R.string.reminder_break_completed_title)
    ReminderNotificationContentKind.EXAM_COUNTDOWN -> candidate.content.userLabel.orEmpty()
}

@Composable
private fun reminderCandidateBody(candidate: ReminderNotificationCandidate): String = when (candidate.content.kind) {
    ReminderNotificationContentKind.DUE_REVIEWS -> {
        val count = candidate.content.count ?: 0
        pluralStringResource(R.plurals.reminder_due_reviews_body, count, count)
    }
    ReminderNotificationContentKind.PLANNED_ACTIVITY -> stringResource(R.string.reminder_planned_activity_body)
    ReminderNotificationContentKind.MISSED_ACTIVITY -> {
        val count = candidate.content.count ?: 0
        pluralStringResource(R.plurals.reminder_missed_activity_body, count, count)
    }
    ReminderNotificationContentKind.DAILY_SUMMARY -> stringResource(R.string.reminder_daily_summary_body)
    ReminderNotificationContentKind.SESSION_ENDING -> stringResource(
        R.string.reminder_session_ending_body,
        candidate.content.userLabel.orEmpty(),
    )
    ReminderNotificationContentKind.SESSION_COMPLETED -> stringResource(R.string.reminder_session_completed_body)
    ReminderNotificationContentKind.BREAK_COMPLETED -> stringResource(R.string.reminder_break_completed_body)
    ReminderNotificationContentKind.EXAM_COUNTDOWN -> {
        val days = candidate.content.count ?: 0
        pluralStringResource(R.plurals.reminder_exam_countdown_body, days, days)
    }
}

@Composable
private fun WidgetPreviewCard(summary: TodayWidgetSummary) {
    StudyFlowSectionCard(title = stringResource(R.string.widget_preview_title)) {
        Text(text = stringResource(R.string.widget_plan_name, summary.studyPlanName))
        Text(text = stringResource(R.string.widget_due_reviews, summary.dueReviewCount))
        Text(text = stringResource(R.string.widget_planned_today, summary.plannedActivityCount))
        Text(text = stringResource(R.string.widget_missed, summary.missedActivityCount))
        Text(
            text = stringResource(
                R.string.widget_next_activity,
                summary.nextActivityTitle ?: stringResource(R.string.no_activity_scheduled),
            ),
        )
        Text(text = stringResource(R.string.widget_progress, summary.progressPercent))
    }
}

@Composable
private fun ReminderReliabilityIssueRow(issue: ReminderReliabilityIssue) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = stringResource(issue.kind.titleRes()), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(issue.kind.messageRes()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title)
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun ReminderNotificationType.labelRes(): Int = when (this) {
    ReminderNotificationType.DUE_REVIEWS -> R.string.candidate_due_reviews
    ReminderNotificationType.PLANNED_ACTIVITY -> R.string.candidate_planned_activity
    ReminderNotificationType.MISSED_ACTIVITY -> R.string.candidate_missed_activity
    ReminderNotificationType.DAILY_SUMMARY -> R.string.candidate_daily_summary
    ReminderNotificationType.SESSION_ENDING -> R.string.candidate_session_ending
    ReminderNotificationType.SESSION_COMPLETED -> R.string.candidate_session_completed
    ReminderNotificationType.EXAM_COUNTDOWN -> R.string.candidate_exam_countdown
}

private fun Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, PostNotificationsPermission) == PackageManager.PERMISSION_GRANTED

internal fun parseMinuteOfDay(value: String): Int? {
    val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatMinuteOfDay(value: Int): String = String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)

private fun currentMinuteOfDay(): Int = Calendar.getInstance().run {
    get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
}

private fun formatEpoch(epochMillis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(epochMillis))

private fun ReminderReliabilityStatus.labelRes(): Int = when (this) {
    ReminderReliabilityStatus.READY -> R.string.reminder_reliability_ready
    ReminderReliabilityStatus.DEGRADED -> R.string.reminder_reliability_degraded
    ReminderReliabilityStatus.ACTION_NEEDED -> R.string.reminder_reliability_action_needed
    ReminderReliabilityStatus.BLOCKED -> R.string.reminder_reliability_blocked
}

private fun ReminderReliabilityFallback.messageRes(): Int = when (this) {
    ReminderReliabilityFallback.NONE -> R.string.reminder_fallback_none
    ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK -> R.string.reminder_fallback_widget_manual
    ReminderReliabilityFallback.QUIET_HOURS_DELAY -> R.string.reminder_fallback_quiet_hours
    ReminderReliabilityFallback.BACKLOG_RECOVERY -> R.string.reminder_fallback_backlog
}

private fun ReminderReliabilityIssueKind.titleRes(): Int = when (this) {
    ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION -> R.string.reminder_issue_permission
    ReminderReliabilityIssueKind.SYSTEM_NOTIFICATIONS_DISABLED -> R.string.reminder_issue_system_notifications
    ReminderReliabilityIssueKind.NOTIFICATION_CHANNEL_BLOCKED -> R.string.reminder_issue_channel
    ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION -> R.string.reminder_issue_battery
    ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE -> R.string.reminder_issue_quiet_hours
    ReminderReliabilityIssueKind.REVIEW_BACKLOG -> R.string.reminder_issue_reviews
    ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG -> R.string.reminder_issue_missed
    ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES -> R.string.reminder_issue_no_candidates
    ReminderReliabilityIssueKind.RECONCILIATION_STALE -> R.string.reminder_issue_reconciliation_stale
}

private fun ReminderReliabilityIssueKind.messageRes(): Int = when (this) {
    ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION -> R.string.reminder_issue_permission_message
    ReminderReliabilityIssueKind.SYSTEM_NOTIFICATIONS_DISABLED -> R.string.reminder_issue_system_notifications_message
    ReminderReliabilityIssueKind.NOTIFICATION_CHANNEL_BLOCKED -> R.string.reminder_issue_channel_message
    ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION -> R.string.reminder_issue_battery_message
    ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE -> R.string.reminder_issue_quiet_hours_message
    ReminderReliabilityIssueKind.REVIEW_BACKLOG -> R.string.reminder_issue_reviews_message
    ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG -> R.string.reminder_issue_missed_message
    ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES -> R.string.reminder_issue_no_candidates_message
    ReminderReliabilityIssueKind.RECONCILIATION_STALE -> R.string.reminder_issue_reconciliation_stale_message
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.openAppNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.openBatteryOptimizationSettings() {
    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
