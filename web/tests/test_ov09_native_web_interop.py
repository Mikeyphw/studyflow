import subprocess
import unittest
from pathlib import Path


class Ov09NativeWebInteropTest(unittest.TestCase):
    def test_runner(self):
        web = Path(__file__).resolve().parents[1]
        result = subprocess.run(["node", "tests/ov09_interop_runner.mjs"], cwd=web, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + "\n" + result.stderr)
        self.assertIn("OV09_WEB_NATIVE_INTEROP: PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
