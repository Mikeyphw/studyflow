from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Parity04WebAcceptanceTests(unittest.TestCase):
    def test_language_neutral_five_client_acceptance_runner(self):
        result = subprocess.run(
            ["node", "tests/parity04_runner.mjs"], cwd=WEB, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("PARITY04_TYPESCRIPT_ACCEPTANCE: PASS", result.stdout)

    def test_parity04_reuses_web20_and_production_runtimes(self):
        runner = (WEB / "tests" / "parity04_runner.mjs").read_text(encoding="utf-8")
        for token in [
            "protocol/parity/v1/five-client-scenarios.json",
            "protocol/semantic/v1/golden-vectors.json",
            "StudyFlowWebActivityHandoffController",
            "StudyFlowWebPresentationProjectionController",
            "StudyFlowWebPresenceRuntime",
            "runSemanticGolden",
            "stableStringify",
        ]:
            self.assertIn(token, runner)

if __name__ == "__main__":
    unittest.main()
