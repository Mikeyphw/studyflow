import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Att05WebAttentionRuntimeTests(unittest.TestCase):
    def text(self, rel):
        return (ROOT / rel).read_text(encoding="utf-8")

    def test_attention_runtime_has_durable_foreground_scheduling_and_actions(self):
        src = self.text("web/src/attention.ts")
        for token in [
            "StudyFlowWebAttentionRuntime",
            "StudyFlowBrowserAttentionLedgerStorage",
            "WEB_ATTENTION_SCAN_INTERVAL_MS",
            "planned_study",
            "due_review",
            "missed_activity",
            "daily_summary",
            "session_ending",
            "session_completed",
            "exam_countdown",
            "applyAction",
            "snoozed",
            "updateBadge",
            "ingestRemoteDelivery",
            "StudyFlowNotificationMediaRuntime",
        ]:
            self.assertIn(token, src)

    def test_service_worker_handles_push_click_close_and_drain(self):
        sw = self.text("web/service-worker.js")
        for token in [
            "runtime/attention.js",
            'addEventListener("push"',
            "registration.showNotification",
            "notificationclick",
            "notificationclose",
            "studyflow:attention-drain",
            "studyflow:attention-event",
            "indexedDB.open",
        ]:
            self.assertIn(token, sw)

    def test_react_shell_exposes_notification_center_and_user_permission(self):
        app = self.text("web/src/react/App.tsx")
        main = self.text("web/src/main.tsx")
        for token in [
            "attentionRuntime",
            "Notification Center",
            "Enable browser notifications",
            "Reminder categories",
            "Optional Web Push foundation",
            "foreground reminders",
            "runtime.requestBrowserNotifications",
            "runtime.subscribePush",
            "runtime.applyAction",
        ]:
            self.assertIn(token, app)
        self.assertIn("new StudyFlowWebAttentionRuntime", main)
        self.assertIn("installStudyFlowWebAttentionBridge", main)

    def test_runtime_artifacts_are_committed_for_offline_shell(self):
        for rel in [
            "web/runtime/attention.js",
            "web/runtime/main.js",
            "web/runtime/pwa.js",
            "web/runtime/react/App.js",
        ]:
            self.assertTrue((ROOT / rel).is_file(), rel)
        runtime = self.text("web/runtime/attention.js")
        self.assertIn("StudyFlowWebAttentionRuntime", runtime)
        self.assertIn("exports.StudyFlowWebAttentionRuntime", runtime)

if __name__ == "__main__":
    unittest.main()
