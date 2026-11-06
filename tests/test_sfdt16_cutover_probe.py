#!/usr/bin/env python3
"""Source-only adverse tests for the independent SFDT16 cutover probe."""
from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.sfdt16_cutover_probe import LANES, CutoverRefused, assess

SHA = "a" * 40
IDS = {lane: i + 100 for i, lane in enumerate(LANES)}


def fixture():
    items = {}
    for lane in LANES:
        items[lane] = {
            "run": {"id": IDS[lane], "head_sha": SHA, "status": "completed",
                    "conclusion": "success", "run_attempt": 1, "name": lane, "event": "workflow_dispatch"},
            "jobs": [{"status": "completed", "conclusion": "success", "name": "job-" + lane}],
            "artifacts": [{"id": IDS[lane] * 10, "name": lane, "expired": False,
                           "size_in_bytes": 123, "digest": "sha256:abc"}],
        }
    return items


class SFDT16Tests(unittest.TestCase):
    def test_green_is_not_cutover_authority(self):
        evidence = assess(SHA, IDS, fixture())
        self.assertEqual(evidence["status"], "SAME_SHA_SHADOW_INVENTORY_PASS")
        self.assertFalse(evidence["cutover_authorized"])
        self.assertEqual(evidence["artifact_byte_parity"], "NOT_VERIFIED")
        self.assertEqual(len(evidence["lanes"]), 5)

    def test_sha_mismatch_refused(self):
        f = fixture()
        f["expanded_shadow"]["run"]["head_sha"] = "b" * 40
        with self.assertRaisesRegex(CutoverRefused, "source SHA mismatch"):
            assess(SHA, IDS, f)

    def test_reused_run_refused(self):
        ids = dict(IDS)
        ids["native_fast"] = ids["legacy"]
        with self.assertRaisesRegex(CutoverRefused, "Reused run ID"):
            assess(SHA, ids, fixture())

    def test_incomplete_and_failure_refused(self):
        for status, conclusion in (("in_progress", None), ("completed", "failure"),
                                   ("completed", "cancelled")):
            with self.subTest(status=status, conclusion=conclusion):
                f = fixture()
                f["native_fast"]["run"].update(status=status, conclusion=conclusion)
                with self.assertRaises(CutoverRefused):
                    assess(SHA, IDS, f)

    def test_shadow_skipped_job_refused(self):
        f = fixture()
        f["native_deep"]["jobs"].append({"status": "completed", "conclusion": "skipped", "name": "hidden"})
        with self.assertRaisesRegex(CutoverRefused, "skipped shadow job"):
            assess(SHA, IDS, f)

    def test_legacy_skip_is_observable(self):
        f = fixture()
        f["legacy"]["jobs"].append({"status": "completed", "conclusion": "skipped", "name": "deep"})
        result = assess(SHA, IDS, f)
        self.assertEqual(result["lanes"][0]["skipped_jobs"], ["deep"])

    def test_missing_or_expired_artifacts_refused(self):
        for mutate in (lambda f: f["native_fast"].update(artifacts=[]),
                       lambda f: f["native_fast"]["artifacts"][0].update(expired=True)):
            f = fixture()
            mutate(f)
            with self.assertRaises(CutoverRefused):
                assess(SHA, IDS, f)

    def test_zero_jobs_and_missing_attempt_refused(self):
        f = fixture()
        f["source_shadow"]["jobs"] = []
        with self.assertRaises(CutoverRefused):
            assess(SHA, IDS, f)
        f = fixture()
        f["source_shadow"]["run"].pop("run_attempt")
        with self.assertRaises(CutoverRefused):
            assess(SHA, IDS, f)

    def test_source_only_does_not_require_gradle(self):
        f = fixture()
        f["source_shadow"]["artifacts"] = []
        self.assertFalse(assess(SHA, IDS, f)["cutover_authorized"])


if __name__ == "__main__":
    unittest.main()
