import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Att11AttentionReliabilityTests(unittest.TestCase):
    def test_reliability_analyzer_and_exports(self):
        result = subprocess.run(
            ["node", "tests/att11_reliability_runner.mjs"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("ATT11 Web reliability evidence tests passed", result.stdout)

    def test_notification_center_exposes_audit_and_truthful_push_copy(self):
        source = (ROOT / "src/react/App.tsx").read_text()
        self.assertIn("ATT11 delivery reliability and audit", source)
        self.assertIn("actual Web Push subscription", source)
        self.assertIn("runtime.exportReliabilityJson()", source)
        self.assertIn("runtime.exportReliabilityCsv()", source)

    def test_web_tv_projection_is_read_only(self):
        source = (ROOT / "src/tv_attention.ts").read_text()
        self.assertIn("reliabilityReadOnly: true", source)
        self.assertIn('source: "att11-web-runtime"', source)
        self.assertIn("studyflowTvAttentionReliability", source)

if __name__ == "__main__":
    unittest.main()
