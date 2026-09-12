import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Att10FocusPolicyTests(unittest.TestCase):
    def test_focus_policy_runtime(self):
        result = subprocess.run(
            ["node", "tests/att10_focus_policy_runner.mjs"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("ATT10 Web focus policy tests passed", result.stdout)

    def test_notification_center_exposes_focus_controls_and_truthful_web_copy(self):
        source = (ROOT / "src/react/App.tsx").read_text()
        self.assertIn("ATT10 quiet hours and focus policy", source)
        self.assertIn("A closed browser is not treated as a guaranteed alarm owner", source)
        self.assertIn("Per-category escalation", source)
        self.assertIn("runtime.setFocusCategoryMode", source)

    def test_service_worker_caches_focus_policy_runtime(self):
        sw = (ROOT / "service-worker.js").read_text()
        self.assertIn("./runtime/attention_focus_policy.js", sw)

if __name__ == "__main__":
    unittest.main()
