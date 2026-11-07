#!/usr/bin/env python3
"""Offline SFDT18-HF01 hosted guard failure-path tests (no Gradle)."""
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.sfdt18_phone_debug_guard import (is_progress, stop_reason, monitor)


class HostedGuardTests(unittest.TestCase):
    def test_devtool_heartbeat_not_counted(self):
        self.assertFalse(is_progress(""))
        self.assertFalse(is_progress("Devtool heartbeat: job:studyflow_android.sfdt07-phone-debug (gradlew) is still running after 3345s without child output."))
        self.assertTrue(is_progress("> Task :app:lintDebug"))
        self.assertTrue(is_progress("BUILD SUCCESSFUL in 5m"))

    def test_bounded_thresholds(self):
        self.assertIsNone(stop_reason(elapsed=10, quiet_for=5, quiet_limit=8, wall_limit=30))
        self.assertEqual(stop_reason(elapsed=10, quiet_for=9, quiet_limit=8, wall_limit=30), "gradle_output_stalled")
        self.assertEqual(stop_reason(elapsed=31, quiet_for=1, quiet_limit=8, wall_limit=30), "wall_timeout")

    def test_successful_child_no_promotion(self):
        with tempfile.TemporaryDirectory() as temp:
            dst=Path(temp)
            rc=monitor([sys.executable,"-c","print('BUILD SUCCESSFUL')"],dst,
                       quiet_limit=2,wall_limit=5,diagnostics=False)
            self.assertEqual(rc,0)
            report=json.loads((dst/"hf01-guard.json").read_text())
            self.assertEqual(report['status'], 'NATIVE_EXIT_ZERO')
            self.assertFalse(report['cutover_authorized'])

    def test_child_failure_stays_failed(self):
        with tempfile.TemporaryDirectory() as temp:
            dst=Path(temp)
            rc=monitor([sys.executable,"-c","import sys; sys.exit(42)"],dst,
                       quiet_limit=2,wall_limit=5,diagnostics=False)
            self.assertEqual(rc,42)
            self.assertEqual(json.loads((dst/"hf01-guard.json").read_text())['status'],'NATIVE_EXIT_NONZERO')

    def test_heartbeat_only_is_stalled(self):
        with tempfile.TemporaryDirectory() as temp:
            dst=Path(temp)
            code="import time; print('Devtool heartbeat: job:studyflow_android.sfdt07-phone-debug (gradlew) still running',flush=True); time.sleep(6)"
            t=time.monotonic()
            rc=monitor([sys.executable,"-c",code],dst,
                       quiet_limit=0.7,wall_limit=5,diagnostics=False)
            self.assertEqual(rc,124)
            self.assertLess(time.monotonic()-t,4)
            self.assertEqual(json.loads((dst/"hf01-guard.json").read_text())['reason'],'gradle_output_stalled')

    def test_wall_timeout_even_after_output(self):
        with tempfile.TemporaryDirectory() as temp:
            dst=Path(temp)
            code="import time; [print('Task running',flush=True) or time.sleep(.11) for _ in range(50)]"
            rc=monitor([sys.executable,"-c",code],dst,
                       quiet_limit=1,wall_limit=1.5,diagnostics=False)
            self.assertEqual(rc,124)
            self.assertEqual(json.loads((dst/"hf01-guard.json").read_text())['reason'],'wall_timeout')

    def test_reject_invalid_limits(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                monitor([sys.executable,"-c","pass"],Path(temp),quiet_limit=4,wall_limit=2)


if __name__=='__main__':
    unittest.main()
