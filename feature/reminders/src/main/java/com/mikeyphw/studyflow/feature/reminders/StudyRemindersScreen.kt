package com.mikeyphw.studyflow.feature.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.studyflow.core.designsystem.StudyFlowSectionCard
import com.mikeyphw.studyflow.core.designsystem.StudyFlowStatusPill
import com.mikeyphw.studyflow.core.domain.ManageStudyReview
import com.mikeyphw.studyflow.core.domain.ManageStudySchedule
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityCenter
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityFallback
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityInput
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityIssue
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityIssueKind
import com.mikeyphw.studyflow.core.domain.ReminderReliabilityStatus
import com.mikeyphw.studyflow.core.domain.ReviewQueue
import com.mikeyphw.studyflow.core.domain.StudyReminderInput
import com.mikeyphw.studyflow.core.domain.StudyReminderPlanner
import com.mikeyphw.studyflow.core.domain.TodayWidgetSummary
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudySchedule
import java.util.Calendar
import java.util.Locale

private const val PostNotificationsPermission = "android.permission.POST_NOTIFICATIONS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyRemindersScreen(
    plan: StudyPlan,
    settings: StudyPlanSettings,
    schedule: ManageStudySchedule,
    review: ManageStudyReview,
    onSettingsChanged: (StudyPlanSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNotificationPermissionResult: (Boolean) -> Unit = {},
) {
    val currentSchedule by schedule.observeSchedule(plan.id).collectAsStateWithLifecycle(
        initialValue = StudySchedule(plan.id, null, emptyList(), emptyList()),
    )
    val queue by review.observeQueue(plan.id).collectAsStateWithLifecycle(
        initialValue = ReviewQueue(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
    )
    val context = LocalContext.current
    var notificationPermissionGranted by rememberSaveable {
        mutableStateOf(context.hasPostNotificationsPermission())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
        onNotificationPermissionResult(granted)
    }
    val now = remember(currentSchedule, queue, settings, notificationPermissionGranted) {
        System.currentTimeMillis()
    }
    val reminderPlan = StudyReminderPlanner.buildPlan(
        StudyReminderInput(
            plan = plan,
            settings = settings,
            schedule = currentSchedule,
            reviewQueue = queue,
            nowEpochMillis = now,
            minuteOfDay = currentMinuteOfDay(),
            apiLevel = Build.VERSION.SDK_INT,
            notificationPermissionGranted = notificationPermissionGranted,
        ),
    )
    val activeCandidates = reminderPlan.notificationCandidates.filter { candidate ->
        !candidate.suppressedByQuietHours
    }
    val reliabilityReport = remember(reminderPlan, notificationPermissionGranted, context) {
        ReminderReliabilityCenter.inspect(
            ReminderReliabilityInput(
                notificationPermissionGranted = notificationPermissionGranted,
                exactAlarmAvailable = context.canScheduleStudyFlowExactAlarms(),
                batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations(),
                quietHoursActive = reminderPlan.quietHoursActive,
                activeCandidateCount = reminderPlan.activeCandidateCount,
                dueReviewCount = reminderPlan.widgetSummary.dueReviewCount,
                missedActivityCount = reminderPlan.widgetSummary.missedActivityCount,
                plannedActivityCandidateCount = reminderPlan.notificationCandidates.count { candidate ->
                    candidate.type == ReminderNotificationType.PLANNED_ACTIVITY
                },
                nextCandidateAtEpochMillis = activeCandidates.minOfOrNull { candidate ->
                    candidate.scheduledAtEpochMillis
                },
                nowEpochMillis = now,
            ),
        )
    }
    var remindersEnabled by rememberSaveable(plan.id.value, settings.reviewNotificationsEnabled) {
        mutableStateOf(settings.reviewNotificationsEnabled)
    }
    var quietHoursEnabled by rememberSaveable(plan.id.value, settings.quietHoursStartMinute) {
        mutableStateOf(settings.quietHoursStartMinute != null)
    }
    var quietHoursStart by rememberSaveable(plan.id.value, settings.quietHoursStartMinute) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursStartMinute ?: 22 * 60))
    }
    var quietHoursEnd by rememberSaveable(plan.id.value, settings.quietHoursEndMinute) {
        mutableStateOf(formatMinuteOfDay(settings.quietHoursEndMinute ?: 7 * 60))
    }
    val startMinute = parseMinuteOfDay(quietHoursStart)
    val endMinute = parseMinuteOfDay(quietHoursEnd)
    val settingsValid = !quietHoursEnabled || (startMinute != null && endMinute != null)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
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
                    text = if (reminderPlan.permissionRequired) {
                        stringResource(R.string.notification_permission_needed)
                    } else {
                        stringResource(R.string.notification_permission_ready)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(PostNotificationsPermission)
                        }
                    },
                    enabled = reminderPlan.permissionRequired,
                ) {
                    Text(stringResource(R.string.request_notification_permission))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.reminder_reliability_title)) {
                Text(
                    text = stringResource(reliabilityReport.status.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(reliabilityReport.fallback.messageRes()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reliabilityReport.nextCandidateAtEpochMillis?.let { nextCandidateAt ->
                    Text(
                        text = stringResource(R.string.reminder_next_candidate_epoch, nextCandidateAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                reliabilityReport.issues.forEach { issue ->
                    ReminderReliabilityIssueRow(issue)
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.reminder_preferences_title)) {
                PreferenceSwitch(
                    title = stringResource(R.string.review_reminders),
                    description = stringResource(R.string.review_reminders_description),
                    checked = remindersEnabled,
                    onCheckedChange = { remindersEnabled = it },
                )
                PreferenceSwitch(
                    title = stringResource(R.string.quiet_hours),
                    description = stringResource(R.string.quiet_hours_description),
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                )
                if (quietHoursEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = quietHoursStart,
                            onValueChange = { quietHoursStart = it.take(5) },
                            label = { Text(stringResource(R.string.quiet_hours_start)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = startMinute == null,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = quietHoursEnd,
                            onValueChange = { quietHoursEnd = it.take(5) },
                            label = { Text(stringResource(R.string.quiet_hours_end)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = endMinute == null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(
                    onClick = {
                        onSettingsChanged(
                            settings.copy(
                                reviewNotificationsEnabled = remindersEnabled,
                                quietHoursStartMinute = if (quietHoursEnabled) startMinute else null,
                                quietHoursEndMinute = if (quietHoursEnabled) endMinute else null,
                            ),
                        )
                    },
                    enabled = settingsValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save_reminder_settings))
                }
            }
            StudyFlowSectionCard(title = stringResource(R.string.notification_preview_title)) {
                reminderPlan.notificationCandidates.forEach { candidate ->
                    ReminderCandidateRow(candidate)
                }
            }
            WidgetPreviewCard(reminderPlan.widgetSummary)
        }
    }
}

@Composable
private fun ReminderCandidateRow(candidate: ReminderNotificationCandidate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(candidate.type.labelRes()), style = MaterialTheme.typography.titleMedium)
        Text(text = candidate.title)
        Text(text = candidate.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (candidate.suppressedByQuietHours) {
            Text(
                text = stringResource(R.string.suppressed_by_quiet_hours),
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
        modifier = Modifier.fillMaxWidth(),
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ReminderNotificationType.labelRes(): Int = when (this) {
    ReminderNotificationType.DUE_REVIEWS -> R.string.candidate_due_reviews
    ReminderNotificationType.PLANNED_ACTIVITY -> R.string.candidate_planned_activity
    ReminderNotificationType.MISSED_ACTIVITY -> R.string.candidate_missed_activity
    ReminderNotificationType.DAILY_SUMMARY -> R.string.candidate_daily_summary
}

private fun Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, PostNotificationsPermission) == PackageManager.PERMISSION_GRANTED

internal fun parseMinuteOfDay(value: String): Int? {
    val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatMinuteOfDay(value: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    value / 60,
    value % 60,
)

private fun currentMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}


private fun ReminderReliabilityStatus.labelRes(): Int = when (this) {
    ReminderReliabilityStatus.READY -> R.string.reminder_reliability_ready
    ReminderReliabilityStatus.DEGRADED -> R.string.reminder_reliability_degraded
    ReminderReliabilityStatus.ACTION_NEEDED -> R.string.reminder_reliability_action_needed
    ReminderReliabilityStatus.BLOCKED -> R.string.reminder_reliability_blocked
}

private fun ReminderReliabilityFallback.messageRes(): Int = when (this) {
    ReminderReliabilityFallback.NONE -> R.string.reminder_fallback_none
    ReminderReliabilityFallback.WIDGET_AND_MANUAL_CHECK -> R.string.reminder_fallback_widget_manual
    ReminderReliabilityFallback.INEXACT_ALARM_WINDOW -> R.string.reminder_fallback_inexact
    ReminderReliabilityFallback.QUIET_HOURS_DELAY -> R.string.reminder_fallback_quiet_hours
    ReminderReliabilityFallback.BACKLOG_RECOVERY -> R.string.reminder_fallback_backlog
}

private fun ReminderReliabilityIssueKind.titleRes(): Int = when (this) {
    ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION -> R.string.reminder_issue_permission
    ReminderReliabilityIssueKind.EXACT_ALARM_ACCESS -> R.string.reminder_issue_exact_alarm
    ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION -> R.string.reminder_issue_battery
    ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE -> R.string.reminder_issue_quiet_hours
    ReminderReliabilityIssueKind.REVIEW_BACKLOG -> R.string.reminder_issue_reviews
    ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG -> R.string.reminder_issue_missed
    ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES -> R.string.reminder_issue_no_candidates
}

private fun ReminderReliabilityIssueKind.messageRes(): Int = when (this) {
    ReminderReliabilityIssueKind.NOTIFICATION_PERMISSION -> R.string.reminder_issue_permission_message
    ReminderReliabilityIssueKind.EXACT_ALARM_ACCESS -> R.string.reminder_issue_exact_alarm_message
    ReminderReliabilityIssueKind.BATTERY_OPTIMIZATION -> R.string.reminder_issue_battery_message
    ReminderReliabilityIssueKind.QUIET_HOURS_ACTIVE -> R.string.reminder_issue_quiet_hours_message
    ReminderReliabilityIssueKind.REVIEW_BACKLOG -> R.string.reminder_issue_reviews_message
    ReminderReliabilityIssueKind.MISSED_ACTIVITY_BACKLOG -> R.string.reminder_issue_missed_message
    ReminderReliabilityIssueKind.NO_SCHEDULED_CANDIDATES -> R.string.reminder_issue_no_candidates_message
}

private fun Context.canScheduleStudyFlowExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val alarmManager = getSystemService(AlarmManager::class.java) ?: return false
    return alarmManager.canScheduleExactAlarms()
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    if (Build.VERSION.SDK_INT < 23) return true
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}
