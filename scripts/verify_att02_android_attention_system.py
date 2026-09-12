#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    if not target.is_file():
        errors.append(f"missing file: {path}")
        return ""
    return target.read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> str:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing token {token!r}")
    return text

policy = require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPolicy.kt",
    "plannedStudyEnabled",
    "dueReviewEnabled",
    "missedActivityEnabled",
    "dailySummaryEnabled",
    "sessionTimerEnabled",
    "examCountdownEnabled",
    "dailySummaryMinuteOfDay: Int = 8 * 60",
    "ReminderPlatformRuntimeEvidence",
)
planner = require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt",
    "ReminderNotificationType.SESSION_ENDING",
    "ReminderNotificationType.SESSION_COMPLETED",
    "ReminderNotificationType.EXAM_COUNTDOWN",
    "BREAK_COMPLETED,",
    "policy.dailySummaryMinuteOfDay",
    "policy.plannedStudyLeadMinutes",
    "policy.missedActivityDelayMinutes",
    "policy.sessionEndingLeadMinutes",
    "examCountdownCandidate",
    "nextLocalTime",
)
if "dailyScheduledAt = startOfNextLocalDay" in planner:
    errors.append("planner still hard-wires daily summary to midnight")

coordinator = require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    'const val StudyReminderScheduleChannelId = "study_schedule"',
    'const val StudyReminderReviewsChannelId = "study_reviews"',
    'const val StudyReminderSessionChannelId = "study_session_timers"',
    'const val StudyReminderSummaryChannelId = "study_summaries"',
    "alarmManager.setWindow(",
    "StudyReminderNotificationAction.START_NOW",
    "StudyReminderNotificationAction.SNOOZE",
    "StudyReminderNotificationAction.MARK_DONE",
    "StudyReminderNotificationAction.START_BREAK",
    "runtimeEvidence(): ReminderPlatformRuntimeEvidence",
    "deleteNotificationChannel(StudyReminderLegacyChannelId)",
    "POST_NOTIFICATIONS only gates native notification surfaces",
    "localSurface == StudyFlowAttentionDeliverySurface.WIDGET_OR_BADGE",
    "StudyReminderExtraContentKind",
    "StudyReminderExtraScheduledAt",
    "StudyReminderExtraFireAt",
    "ReminderNotificationContentKind.BREAK_COMPLETED",
)
if "setExact(" in coordinator or "setExactAndAllowWhileIdle(" in coordinator:
    errors.append("ATT02 must not make exact alarms the default delivery path")

require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyReminderPolicyStore.kt",
    "studyflow_android_attention_policy_v1",
    "StudyReminderPolicy.fromLegacy",
    "StudyReminderRuntimeStatusStore",
    "dailySummaryMinuteOfDay",
)
occurrences = require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt",
    "NewEncodingFieldCount = 11",
    "if (parts.size == 4)",
    "snoozedFromOccurrenceId",
    "targetId",
    "delayedByQuietHours",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt",
    "class StudyReminderActionReceiver",
    "parseReminderCandidate",
    "sourceCandidate = intent.parseReminderCandidate(parsed)",
    "Intent.ACTION_BOOT_COMPLETED",
    "Intent.ACTION_TIME_CHANGED",
    "Intent.ACTION_TIMEZONE_CHANGED",
    "Intent.ACTION_DATE_CHANGED",
    "Intent.ACTION_MY_PACKAGE_REPLACED",
)
require(
    "app/src/main/AndroidManifest.xml",
    ".reminders.StudyReminderActionReceiver",
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.TIME_SET",
    "android.intent.action.DATE_CHANGED",
    "android.intent.action.MY_PACKAGE_REPLACED",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt",
    'reconcileAll("activity-resume")',
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "startReminderInvalidationObserver",
    'reconcileAll("domain-change")',
    "migrateLegacyReminderStateToAttentionLedger",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt",
    "launch.targetId",
    "FocusSessionRoute",
    "ReminderNotificationType.EXAM_COUNTDOWN",
)
screen = require(
    "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
    "plannedStudyEnabled",
    "dueReviewEnabled",
    "missedActivityEnabled",
    "dailySummaryEnabled",
    "sessionTimerEnabled",
    "examCountdownEnabled",
    "plannedStudyLeadMinutes",
    "dailySummaryMinuteOfDay",
    "missedActivityDelayMinutes",
    "defaultSnoozeMinutes",
    "sessionEndingLeadMinutes",
    "examCountdownDays",
    "openAppNotificationSettings",
    "openBatteryOptimizationSettings",
    "openAppDetailsSettings",
    "ReminderReliabilityCenter.inspect",
    ".semantics(mergeDescendants = true) {}",
    "role = Role.Switch",
)

for res in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
):
    require(
        res,
        "notification_channel_study_schedule_name",
        "notification_channel_reviews_name",
        "notification_channel_session_timers_name",
        "notification_channel_summaries_name",
        "reminder_action_start_now",
        "reminder_action_snooze",
        "reminder_action_reschedule",
        "reminder_action_mark_done",
        "reminder_notification_break_complete_title",
    )
for res in (
    "feature/reminders/src/main/res/values/strings.xml",
    "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
):
    require(
        res,
        "reminder_categories_title",
        "daily_summary_time",
        "reminder_reliability_title",
        "reminder_channels_status",
        "reminder_exact_alarm_not_required",
        "candidate_session_ending",
        "candidate_exam_countdown",
    )

require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReminderReliabilityCenter.kt",
    "SYSTEM_NOTIFICATIONS_DISABLED",
    "NOTIFICATION_CHANNEL_BLOCKED",
    "RECONCILIATION_STALE",
    "exactAlarmRequired",
    "lastReconciledAtEpochMillis",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowReminderNotificationLab.kt",
    "channelCount",
    "blockedChannelCount",
    "lastRecoveryReason",
    "StudyReminderSessionChannelId",
)
require(
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/ReminderNotificationLabPanel.kt",
    "debug_reminder_lab_channel_count",
    "debug_reminder_lab_last_reconciled",
    "debug_reminder_lab_last_recovery_reason",
)

for test in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerAtt02Test.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPolicyAtt02Test.kt",
    "app/src/test/java/com/mikeyphw/studyflow/reminders/StudyReminderAtt02SourceContractTest.kt",
    "app/src/androidTest/java/com/mikeyphw/studyflow/reminders/AndroidStudyReminderPolicyStoreAtt02Test.kt",
):
    read(test)

require("ATT02_ANDROID_ATTENTION_SYSTEM_2_0.md", "verifyAtt02AndroidAttentionSystem", "08:00", "Start break")
require("docs/product/ANDROID_ATTENTION_SYSTEM.md", "Exact-alarm permission required", "ATT01 durable ledger integration")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "FULL (ATT02)", "Android ATT02 reminder delivery")
require("README.md", "ATT02 Android Attention System 2.0")
require("CHANGELOG.md", "ATT02 — Android Attention System 2.0")

for path in (
    "web/src/sflp_link.ts",
    "web/runtime/sflp_link.js",
    "web/dist/runtime/sflp_link.js",
):
    require(path, "StudyFlowAttentionLedgerSnapshotSchemaVersion")

build = require(
    "build.gradle.kts",
    '"verifyAtt02AndroidAttentionContracts"',
    '"verifyAtt02AndroidAttentionSystem"',
)
config = require(".devtool.toml", "verifyAttentionRoadmapPostsealCompletion")

# Reject obvious incomplete implementation markers in ATT02-owned production files.
owned = [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPolicy.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt",
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyReminderPolicyStore.kt",
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
]
for path in owned:
    text = read(path)
    if re.search(r"\b(TODO|FIXME)\b", text):
        errors.append(f"{path}: contains TODO/FIXME in ATT02 production scope")

if errors:
    print("ATT02 Android Attention System verification FAILED", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("ATT02 Android Attention System verification passed")
print("- six category policy + configurable timing + 08:00 summary default")
print("- inexact scheduling/recovery + four channels + self-contained actions")
print("- deep links + reliability center + debug evidence + ATT01 ledger lifecycle")
print("- legacy persistence compatibility + tests/docs/Devtool wiring present")
