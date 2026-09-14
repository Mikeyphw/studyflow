from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Ov11ReconciliationRecoveryTests(unittest.TestCase):
    def test_behavior_runner(self):
        result = subprocess.run(
            ["node", str(WEB / "tests" / "ov11_reconciliation_runner.mjs")],
            cwd=WEB,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("OV11_WEB_RECONCILIATION_RECOVERY: PASS", result.stdout)

    def test_source_contracts(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "StudyFlowWebSyncIntegrityError",
            "peerRetryStates",
            "verifiedRemoteFrontiers",
            "validateSyncBatch",
            "SFLP sync batch declares a spoofed or gapped through frontier",
            "deferred",
            "quarantineReference",
        ]:
            self.assertIn(token, source)

if __name__ == "__main__":
    unittest.main()
