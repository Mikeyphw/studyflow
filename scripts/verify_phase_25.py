#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
failures: list[str] = []

def text(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"missing {path}")
        return ""
    return target.read_text(encoding="utf-8")

def require(path: str, *needles: str) -> None:
    content = text(path)
    for needle in needles:
        if needle not in content:
            failures.append(f"{path} missing marker: {needle}")

if not phase_version_at_least(ROOT, 25):
    failures.append("app version is below Phase 25")

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
if manifest_path.exists():
    root = ET.parse(manifest_path).getroot()
    permissions = {node.attrib.get(f"{ANDROID_NS}name") for node in root.findall("uses-permission")}
    if "android.permission.RECEIVE_BOOT_COMPLETED" not in permissions:
        failures.append("manifest missing boot-completed permission")
    receivers = {
        node.attrib.get(f"{ANDROID_NS}name"): node for node in root.findall("application/receiver")
    }
    if ".reminders.StudyReminderReceiver" not in receivers:
        failures.append("manifest missing StudyReminderReceiver")
    boot = receivers.get(".reminders.StudyReminderBootReceiver")
    if boot is None:
        failures.append("manifest missing StudyReminderBootReceiver")
    elif boot.attrib.get(f"{ANDROID_NS}exported") != "false":
        failures.append("StudyReminderBootReceiver must not be exported")

require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
    "NotificationChannel",
    "NotificationCompat.Builder",
    "AlarmManager.RTC_WAKEUP",
    "setWindow(",
    "StudyPlanStatus.ACTIVE",
    "notificationsAllowed()",
    "cancelPlanAlarms",
    "StudyReminderPlanner.shouldRescheduleAfterDelivery",
    "StudyFlowWidgetUpdateCoordinator.refreshAll",
)
coordinator = text("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt")
for forbidden in ("setExact(", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM"):
    if forbidden in coordinator:
        failures.append(f"ordinary reminders must not use exact-alarm contract: {forbidden}")

planner = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt")
if "suppressedByQuietHours = false" in planner:
    failures.append("daily summary and all ordinary reminder candidates must honor quiet hours")
if planner.count("suppressedByQuietHours = suppressed") < 4:
    failures.append("all four reminder candidate types must carry quiet-hour suppression")
if "fun shouldRescheduleAfterDelivery" not in planner or "ReminderNotificationType.DUE_REVIEWS -> false" not in planner:
    failures.append("planner missing due-review anti-spam reschedule guard")

require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt",
    "goAsync()",
    "context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.deliver",
    "context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.reconcileAll",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt",
    "AppWidgetManager.getInstance",
    "TodayWidgetSummary",
    "StudyFlowTodayWidgetProvider.updateWidget",
    "StudyPlanStatus.ACTIVE",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt",
    "TodayWidgetSummary?",
    "widget_due_reviews_format",
    "android.R.id.background",
)
require("app/src/main/res/layout/widget_today.xml", "@android:id/background", "@drawable/widget_background")
require("app/src/main/res/drawable/widget_background.xml", "<shape", "<corners")
require("app/src/main/res/values/strings.xml", "notification_channel_study_reminders_name")
require("app/src/main/res/values-pt-rBR/strings.xml", "notification_channel_study_reminders_name")
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "reminderDeliveryCoordinator.reconcileAll()",
    "createNotificationChannels()",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    "reminderDeliveryCoordinator.reconcilePlan(updatedSettings.studyPlanId)",
    "reminderDeliveryCoordinator.reconcilePlan(plan.id)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt",
    "ReleaseReadinessPhase(25",
    "Post-MVP gap closure seal",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSealPhase25Test.kt",
    "evaluateStillRepresentsPhaseTwentyFiveInsideCurrentDeliveryScope",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlannerPhase25Test.kt",
    "quietHoursSuppressEveryReminderCandidateIncludingDailySummary",
    "dueReviewsAreNotImmediatelyRescheduledAfterDelivery",
    "futureCadenceRemindersRemainEligibleForRescheduleAfterDelivery",
)
require("docs/quality/PHASE_25_GAP_CLOSURE_SEAL.md", "Phase 21 delivery closure", "Phase 24 provider gate")
require("docs/quality/PHASE_25_PROMISE_AUDIT.md", "PROMISE_AUDIT_STATUS: PASSED")

if failures:
    for failure in failures:
        print(f"PHASE_25_FAILURE: {failure}", file=sys.stderr)
    raise SystemExit(1)
print("PHASE_25_CONTRACT: PASSED (quiet-hour-safe reminders, anti-spam delivery, dynamic widget, final gap closure seal)")
