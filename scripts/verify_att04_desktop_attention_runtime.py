#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path):
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path, *tokens):
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")

prefs = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionPreferences.kt"
runtime = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt"
ports = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlatformPorts.kt"
native = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopNativePlatform.kt"
services = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt"
state = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt"
models = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkflowUiModels.kt"
screen = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt"

require(prefs, "attention-preferences-v1.properties", "StudyReminderPolicy", "nativeNotificationsEnabled", "trayDashboardEnabled", "paused")
require(runtime, "StudyReminderPlanner.buildPlan", "DesktopAttentionPollSeconds", "StudyFlowAttentionLedger", "reconcileNow", "deliverDueEntries", "SNOOZE_CURRENT", "START_NEXT", "REVIEW_DUE", "Desktop reminders are available while StudyFlow is running")
require(ports, "DesktopNotificationCapabilities", "DesktopTrayDashboard", "DesktopAttentionSurfaceAction", "setActionHandler")
require(native, "SystemTray", "PopupMenu", "Start next study block", "Review due", "Snooze current reminder", "Pause / resume reminders", "createStudyFlowTrayImage")
require(services, "attentionRuntime", "DesktopAttentionPreferences.open", "DesktopStudyFlowAttentionLedgerStorage")
require("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt", "attentionRuntime?.bind", "attentionRuntime?.start")
require(state, "toggleDesktopReminders", "toggleDesktopNativeNotifications", "toggleDesktopTrayDashboard", "toggleDesktopReminderPause", "reconcileDesktopAttention", "testDesktopNotification", "attentionRuntime?.close")
require(models, "DesktopAttentionSettingsState", "nativeNotificationsSupported", "scheduledCount", "recoveryCount")
require(screen, "Desktop reminders and tray", "Reconcile reminders now", "Send test notification", "Launch-on-login is not enabled automatically")
require("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionPreferencesTest.kt", "persists global and per-plan attention policy")
require("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntimeTest.kt", "test notification uses rich desktop port", "startup reconciliation")
require("ATT04_DESKTOP_NATIVE_ATTENTION_TRAY.md", "restart recovery", "tray mini-dashboard", "while StudyFlow is running")
require("docs/product/DESKTOP_ATTENTION_RUNTIME.md", "AWT", "action buttons", "persistent attention ledger")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "FULL (ATT04")
require("build.gradle.kts", "verifyAtt04DesktopAttentionRuntime")
require("desktopApp/build.gradle.kts", "verifyAtt04DesktopAttentionRuntime")
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

if errors:
    print("ATT04 Desktop Attention Runtime verification FAILED")
    for e in errors: print("-", e)
    sys.exit(1)
print("ATT04 Desktop Attention Runtime verification PASSED")
