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
        "validation-hook",
        ".devtool-artifact/validation/validate.sh",
        "verifyOv16AttentionSchedulerDurableWakeups",
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

validate = (ROOT / ".devtool-artifact/validation/validate.sh").read_text(errors="replace")
if "gradle --stop" in validate or "./gradlew --stop" in validate:
    errors.append("validation hook must not stop Gradle daemons")
if "bash scripts/validate_ov16" not in validate and "verifyOv16AttentionSchedulerDurableWakeups" not in validate:
    errors.append("validation hook is not wired to OV16 gate")

if errors:
    print("OV16 attention scheduler verifier: FAIL", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("OV16 attention scheduler verifier: PASS (8/8 findings mapped to production paths)")
