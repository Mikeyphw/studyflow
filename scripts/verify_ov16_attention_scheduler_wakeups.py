#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

checks: list[tuple[str, str, str]] = [
    (
        "S7-01",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "rearmFiredQuietCandidate",
    ),
    (
        "S7-01-snooze",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "rawSnoozed",
    ),
    (
        "S7-01-break",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "rawBreakReminder",
    ),
    (
        "S7-02",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "applyAuthoritativeQuietHours",
    ),
    (
        "S7-02-contract",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderWakeupContracts.kt",
        "quiet.nextEndEpochMillis",
    ),
    (
        "S7-03",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "mirrorEscalationEpochMillis",
    ),
    (
        "S7-04",
        "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyReminderPlanner.kt",
        "nextFutureReviewCandidate",
    ),
    (
        "S7-05-router",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionRouterBridge.kt",
        "if (capability.notificationsAllowed) add(StudyFlowAttentionDeliverySurface.SYSTEM_NOTIFICATION)",
    ),
    (
        "S7-05-retry",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "permissionDeniedRetryCandidate",
    ),
    (
        "S7-06",
        "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt",
        "setExactAndAllowWhileIdle",
    ),
    (
        "S7-06-permission",
        "app/src/main/AndroidManifest.xml",
        "android.permission.SCHEDULE_EXACT_ALARM",
    ),
    (
        "S7-07",
        "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetModels.kt",
        "takeIf { it >= nowEpochMillis }",
    ),
    (
        "S7-07-test",
        "app/src/test/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetSnapshotProjectorAtt03Test.kt",
        "keepsSubMinutePlannedBoundaryForExactFreshnessScheduler",
    ),
    (
        "S7-08",
        "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetFreshnessScheduler.kt",
        "requiresExactWidgetRefresh",
    ),
    (
        "S7-08-exact",
        "app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetFreshnessScheduler.kt",
        "setExactAndAllowWhileIdle",
    ),
    (
        "validation-task",
        "build.gradle.kts",
        "verifyOv16AttentionSchedulerDurableWakeups",
    ),
    (
        "validation-runner",
        "scripts/validate_ov16_attention_scheduler_wakeups.sh",
        "verify_ov16_attention_scheduler_wakeups.py",
    ),
]

errors: list[str] = []
for ident, rel, needle in checks:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"{ident}: missing {rel}")
        continue
    text = path.read_text(errors="replace")
    if needle not in text:
        errors.append(f"{ident}: {rel} does not contain {needle!r}")

# The Devtool artifact exists only while an overlay transaction is active.
# Hosted GitHub checkouts validate the permanent source-owned runner instead.
runner = (ROOT / "scripts/validate_ov16_attention_scheduler_wakeups.sh").read_text(errors="replace")
if "gradle --stop" in runner or "./gradlew --stop" in runner:
    errors.append("permanent OV16 runner must not stop Gradle daemons")
active_hook_path = ROOT / ".devtool-artifact/validation/validate.sh"
if active_hook_path.is_file():
    active_hook = active_hook_path.read_text(errors="replace")
    if "gradle --stop" in active_hook or "./gradlew --stop" in active_hook:
        errors.append("active Devtool hook must not stop Gradle daemons")

if errors:
    print("OV16 attention scheduler verifier: FAIL", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("OV16 attention scheduler verifier: PASS (8/8 findings mapped to production paths)")
