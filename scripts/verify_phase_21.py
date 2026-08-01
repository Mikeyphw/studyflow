#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures = []

def text(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"missing {path}")
        return ""
    return target.read_text(encoding="utf-8")

app_build = text("app/build.gradle.kts")
if 'versionName = "0.21.0-phase21"' not in app_build:
    failures.append("app version is not Phase 21")

settings = text("settings.gradle.kts")
if '":feature:reminders"' not in settings:
    failures.append("feature reminders module not included")

reminder_planner = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt")
for marker in (
    "object StudyReminderPlanner",
    "StudyReminderInput",
    "StudyReminderPlan",
    "ReminderNotificationType",
    "TodayWidgetSummary",
    "notificationPermissionRequired",
    "isQuietNow",
):
    if marker not in reminder_planner:
        failures.append(f"reminder planner missing marker: {marker}")

reminders_screen = text("feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt")
for marker in (
    "StudyRemindersScreen",
    "PostNotificationsPermission",
    "ActivityResultContracts.RequestPermission",
    "StudyReminderPlanner.buildPlan",
    "WidgetPreviewCard",
    "ContextCompat.checkSelfPermission",
):
    if marker not in reminders_screen:
        failures.append(f"reminders screen missing marker: {marker}")

for path in (
    "feature/reminders/build.gradle.kts",
    "feature/reminders/consumer-rules.pro",
    "feature/reminders/src/main/AndroidManifest.xml",
):
    if not (ROOT / path).exists():
        failures.append(f"missing reminders module file: {path}")

manifest = text("app/src/main/AndroidManifest.xml")
for marker in (
    "android.permission.POST_NOTIFICATIONS",
    ".widget.StudyFlowTodayWidgetProvider",
    "android.appwidget.action.APPWIDGET_UPDATE",
    "@xml/studyflow_today_widget_info",
):
    if marker not in manifest:
        failures.append(f"manifest missing reminder/widget marker: {marker}")

for path in (
    "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowTodayWidgetProvider.kt",
    "app/src/main/res/layout/widget_today.xml",
    "app/src/main/res/xml/studyflow_today_widget_info.xml",
):
    if not (ROOT / path).exists():
        failures.append(f"missing widget artifact: {path}")

nav = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
dashboard = text("feature/dashboard/src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt")
settings_screen = text("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
for marker, source, label in (
    ("RemindersRoute", nav, "navigation route"),
    ("StudyRemindersScreen", app, "app nav host"),
    ("onOpenReminders", dashboard, "dashboard entry"),
    ("onOpenDefaultPlanReminders", settings_screen, "settings entry"),
):
    if marker not in source:
        failures.append(f"missing {label}: {marker}")

if "LocalContext.current.getString" in reminders_screen or ".resources.getString" in reminders_screen:
    failures.append("reminders screen queries localized resources through LocalContext")

for xml_path in (
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/layout/widget_today.xml",
    "app/src/main/res/xml/studyflow_today_widget_info.xml",
    "feature/reminders/src/main/res/values/strings.xml",
    "feature/reminders/src/main/res/values-pt-rBR/strings.xml",
):
    try:
        ET.parse(ROOT / xml_path)
    except Exception as exc:
        failures.append(f"XML parse failed for {xml_path}: {exc}")

if "python3 scripts/verify_phase_21.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 21 verifier")

if failures:
    print("PHASE_21_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_21_CONTRACT: PASSED (reminders, notifications, and widgets)")
