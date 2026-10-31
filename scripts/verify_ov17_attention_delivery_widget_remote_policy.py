#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

checks: list[tuple[str, str, str]] = [
    ("S7-09-widget-failure-count", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt", "failedInstanceCount"),
    ("S7-09-widget-isolation", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt", "TodayWidgetRenderState.DataUnavailable(fallbackConfiguration)"),
    ("S7-09-widget-runcatching", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt", "runCatching {"),
    ("S7-10-recipient-att10", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "recipient_quiet_hours_deferred"),
    ("S7-10-android-policy", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "localClientKind = StudyFlowLinkClientKind.ANDROID"),
    ("S7-10-remote-compact-policy", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "policyDecision?.outcome == StudyFlowAttentionPolicyOutcome.COMPACT_ONLY"),
    ("S7-11-timezone-refresh", "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionFocusPolicyStore.kt", "storedZone != currentZone"),
    ("S7-11-zone-write", "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionFocusPolicyStore.kt", "putString(\"$prefix.quiet.zone\", currentZone)"),
    ("S7-12-expired-snooze-purge", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt", "purgeExpiredSnoozes"),
    ("S7-12-reconcile-purge", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "occurrenceStore.purgeExpiredSnoozes(planId, clock())"),
    ("S7-13-sha256-ids", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt", "MessageDigest.getInstance(\"SHA-256\")"),
    ("S7-13-no-hashcode-id", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderOccurrenceStore.kt", "occurrenceId.hashCode()"),
    ("S7-14-remote-open-intent", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "remoteOpenIntent"),
    ("S7-14-canonical-destination", "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt", "studyReminderOpenIntent"),
    ("DS2-40-self-healing", "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt", "launchSelfHealingInvalidationObserver"),
    ("DS2-40-retry-delay", "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt", "delay(5_000L)"),
    ("validation-task", "build.gradle.kts", "verifyOv17AttentionDeliveryWidgetRemotePolicy"),
]

errors: list[str] = []
for ident, rel, needle in checks:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"{ident}: missing {rel}")
        continue
    text = path.read_text(errors="replace")
    if ident == "S7-13-no-hashcode-id":
        if needle in text:
            errors.append(f"{ident}: {rel} still contains collision-prone {needle!r}")
        continue
    if needle not in text:
        errors.append(f"{ident}: {rel} does not contain {needle!r}")

# Artifact hook is transaction-scoped: GitHub checkouts do not contain it.
active_path = ROOT / ".devtool-artifact/validation/validate.sh"
if active_path.is_file():
    validate = active_path.read_text(errors="replace")
    if "gradle --stop" in validate or "./gradlew --stop" in validate:
        errors.append("active Devtool hook must not stop Gradle daemons")
script = (ROOT / "scripts/validate_ov17_attention_delivery_widget_remote_policy.sh").read_text(errors="replace")
if "gradle " in script or "./gradlew" in script:
    errors.append("OV17 validation script must not nest Gradle")

if errors:
    print("OV17 attention delivery/widget/remote policy verifier: FAIL", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("OV17 attention delivery/widget/remote policy verifier: PASS (7/7 findings mapped to production paths)")
