#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")

require("web/src/attention.ts",
    "StudyFlowWebAttentionRuntime",
    "StudyFlowBrowserAttentionLedgerStorage",
    "StudyFlowNotificationMediaRuntime",
    "StudyFlowWebPushRegistry",
    "requestBrowserNotifications",
    "subscribePush",
    "unsubscribePush",
    "ingestRemoteDelivery",
    "applyAction",
    "WEB_ATTENTION_SCAN_INTERVAL_MS",
    "planned_study",
    "due_review",
    "missed_activity",
    "daily_summary",
    "session_ending",
    "session_completed",
    "exam_countdown",
    "foreground-only",
    "push-ready",
    "push-subscribed",
)
require("web/src/pwa.ts",
    "StudyFlowPwaCapabilities",
    "notifications",
    "push",
    "badge",
    "api.permission",
    "api.requestPermission",
    "showStudyFlowBrowserNotification",
    "studyFlowServiceWorkerReady",
)
require("web/service-worker.js",
    "runtime/attention.js",
    "ATTENTION_DB_NAME",
    "persistAttentionEvent",
    "drainAttentionEvents",
    'addEventListener("push"',
    "registration.showNotification",
    "notificationclick",
    "notificationclose",
    "studyflow:attention-drain",
    "studyflow:attention-event",
)
require("web/src/main.tsx",
    "new StudyFlowWebAttentionRuntime",
    "installStudyFlowWebAttentionBridge",
    "attentionRuntime",
    "pagehide",
)
require("web/src/react/App.tsx",
    "useWebAttentionSnapshot",
    "Notification Center",
    "Enable browser notifications",
    "Use in-app only",
    "Reminder categories",
    "Optional Web Push foundation",
    "foreground reminders",
    "runtime.requestBrowserNotifications",
    "runtime.subscribePush",
    "runtime.unsubscribePush",
    "runtime.applyAction",
)
require("web/runtime/attention.js", "StudyFlowWebAttentionRuntime", "exports.StudyFlowWebAttentionRuntime")
require("web/runtime/main.js", "StudyFlowWebAttentionRuntime", "attentionRuntime")
require("web/runtime/pwa.js", "showStudyFlowBrowserNotification", "studyFlowPwaCapabilities")
require("web/runtime/react/App.js", "Notification Center", "Web Push subscription requested")
require("web/tests/test_att05_web_attention_runtime.py", "Att05WebAttentionRuntimeTests", "studyflow:attention-drain")
require("ATT05_WEB_PWA_NOTIFICATION_RUNTIME.md", "foreground-only", "Notification Center", "verifyAtt05WebPwaAttentionRuntime")
require("docs/product/WEB_PWA_ATTENTION_RUNTIME.md", "foreground-only", "ATT01", "Progressive enhancement")
require("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md", "FULL (ATT05", "Notification Center")
require("build.gradle.kts", "verifyAtt05WebAttentionContracts", "verifyAtt05WebPwaAttentionRuntime")
require(".devtool.toml", '"verifyAttentionRoadmapPostsealCompletion"')

if errors:
    print("ATT05 Web/PWA Attention Runtime verification FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("ATT05 Web/PWA Attention Runtime verification PASSED")
