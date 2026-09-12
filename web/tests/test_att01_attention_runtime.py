import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class Att01AttentionRuntimeTest(unittest.TestCase):
    def run_node(self, script: str):
        result = subprocess.run(
            ["node", "-e", script],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        return json.loads(result.stdout)

    def test_attention_ledger_survives_runtime_recreation_and_deduplicates(self):
        result = self.run_node(
            r"""
const { StudyFlowNotificationMediaRuntime, StudyFlowMemoryAttentionLedgerStorage } = require('./runtime/sflp_link.js');
Date.now = () => 2000;
const storage = new StudyFlowMemoryAttentionLedgerStorage();
const peer = { deviceId: 'web', capabilities: ['workspace.sync'] };
const delivery = {
  messageId: 'message-1', sourceDeviceId: 'phone', targetDeviceId: 'web', sequence: 1,
  notification: {
    notificationId: 'notification-1', kind: 'review_due', title: 'Review', body: 'Ready',
    route: { workspaceId: 'workspace-main', targetDeviceId: 'web' },
    createdAtEpochMillis: 1000, expiresAtEpochMillis: 10000,
    attentionOccurrence: {
      occurrenceId: 'due:5000:review-1:1', workspaceId: 'workspace-main', category: 'due_review',
      scheduledAtEpochMillis: 1500, originalScheduledAtEpochMillis: 1500,
      deliveryPolicy: 'active_then_primary', sourceDeviceId: 'phone', targetDeviceIds: ['web']
    }
  }
};
const first = new StudyFlowNotificationMediaRuntime(peer, storage).applyNotificationDelivery(delivery);
const secondRuntime = new StudyFlowNotificationMediaRuntime(peer, storage);
const duplicate = secondRuntime.applyNotificationDelivery({ ...delivery, messageId: 'message-2', sequence: 2 });
const stale = secondRuntime.applyNotificationDelivery({
  ...delivery,
  messageId: 'message-3',
  sequence: 1,
  notification: {
    ...delivery.notification,
    notificationId: 'notification-2',
    attentionOccurrence: { ...delivery.notification.attentionOccurrence, occurrenceId: 'due:6000:review-2:1' }
  }
});
console.log(JSON.stringify({ first, duplicate, stale, diagnostics: secondRuntime.attentionDiagnostics() }));
"""
        )
        self.assertEqual("accepted", result["first"]["status"])
        self.assertEqual("duplicate", result["duplicate"]["status"])
        self.assertEqual("rejected", result["stale"]["status"])
        self.assertEqual("stale_sequence", result["stale"]["reason"])
        self.assertEqual(1, result["diagnostics"]["entryCount"])
        self.assertEqual(1, result["diagnostics"]["sourceSequenceCount"])

    def test_schedule_change_operation_preserves_full_attention_semantics(self):
        result = self.run_node(
            r"""
const { StudyFlowNotificationMediaRuntime, StudyFlowMemoryAttentionLedgerStorage } = require('./runtime/sflp_link.js');
const runtime = new StudyFlowNotificationMediaRuntime(
  { deviceId: 'web', capabilities: ['workspace.sync'] },
  new StudyFlowMemoryAttentionLedgerStorage()
);
const change = {
  changeId: 'change-1', workspaceId: 'workspace-main', sourceDeviceId: 'phone', sequence: 7,
  scheduleEntityId: 'activity-1', changeKind: 'reminder_upserted', reminderAtEpochMillis: 5000,
  quietHoursStartMinute: 1320, quietHoursEndMinute: 420, occurredAtEpochMillis: 4000,
  attentionOccurrence: {
    occurrenceId: 'planned:activity-1:5000', workspaceId: 'workspace-main', scheduleEntityId: 'activity-1',
    category: 'planned_study', priority: 'high', scheduledAtEpochMillis: 5000,
    originalScheduledAtEpochMillis: 4500, timezoneId: 'America/Sao_Paulo',
    quietHours: { startMinute: 1320, endMinute: 420 }, deliveryPolicy: 'active_then_primary',
    actions: [{ kind: 'start_now' }], payload: { planId: 'plan-main' }
  },
  payload: { legacyKey: 'legacy-value' }
};
const operation = runtime.scheduleChangeOperation(change);
const canonical = runtime.canonicalScheduleOccurrence(change);
console.log(JSON.stringify({ operation, canonical }));
"""
        )
        payload = result["operation"]["payload"]
        self.assertEqual("sflp18", payload["notificationScheduleSync"])
        self.assertEqual("v1", payload["attentionSchema"])
        self.assertEqual(5000, payload["attentionScheduleChange"]["reminderAtEpochMillis"])
        self.assertEqual(1320, payload["attentionScheduleChange"]["quietHoursStartMinute"])
        self.assertEqual("America/Sao_Paulo", payload["attentionScheduleChange"]["attentionOccurrence"]["timezoneId"])
        self.assertEqual("legacy-value", payload["attentionScheduleChange"]["payload"]["legacyKey"])
        self.assertEqual("planned:activity-1:5000", result["canonical"]["occurrenceId"])


if __name__ == "__main__":
    unittest.main()
