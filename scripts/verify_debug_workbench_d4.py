#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
required = {
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabs.kt": [
        "data class ReminderNotificationLabReport(",
        "data class ReminderCandidateDiagnostic(",
        "val exactAlarmUsed: Boolean = false",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowReminderNotificationLab.kt": [
        "StudyReminderPlanner.buildPlan(",
        "studyReminderPendingIntentIdentityExists(",
        "DIAGNOSTIC_NOTIFICATION_POSTED",
    ],
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/ReminderNotificationLabPanel.kt": [
        "fun ReminderNotificationLabPanel(",
        "debug_reminder_lab_exact_alarm_false",
    ],
    "app/src/test/java/com/mikeyphw/studyflow/StudyFlowReminderNotificationLabTest.kt": [
        "cancellation is never converted",
        "fatal errors are never converted",
        "reconcile blocks without default plan",
    ],
}
for relative, needles in required.items():
    path = root / relative
    assert path.is_file(), relative
    text = path.read_text()
    for needle in needles:
        assert needle in text, f"{relative}: missing {needle}"

coordinator = (root / "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt").read_text()
assert "AlarmManager.setWindow" not in coordinator
assert "alarmManager.setWindow(" in coordinator
assert "setExact" not in coordinator
assert "studyReminderRequestCode" in coordinator
assert "PendingIntent.FLAG_NO_CREATE" in coordinator


backup_seal = (root / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSeal.kt").read_text()
assert "dryRunValue?.databaseRowsChanged" not in backup_seal
assert "dryRunValue != null" in backup_seal
assert "dryRunValue.databaseRowsChanged == 0" in backup_seal

for relative in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
]:
    ET.parse(root / relative)

print("StudyFlow Debug Workbench D4 verifier passed")
