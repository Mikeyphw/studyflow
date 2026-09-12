import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Att09CalendarAttentionTest(unittest.TestCase):
    def test_calendar_runner(self):
        result = subprocess.run(
            ["node", "tests/att09_calendar_runner.mjs"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("ATT09 calendar runner OK", result.stdout)

if __name__ == "__main__":
    unittest.main()
