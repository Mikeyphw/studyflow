import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Att07AttentionRouterTests(unittest.TestCase):
    def test_attention_router_runtime(self):
        result = subprocess.run(
            ["node", "tests/att07_router_runner.mjs"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("ATT07 Web attention router tests passed", result.stdout)

if __name__ == "__main__":
    unittest.main()
