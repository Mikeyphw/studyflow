#!/usr/bin/env python3
"""SFDT16-R2 source-only adversarial run, artifact and workflow identity tests."""
from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.sfdt16_cutover_probe import (LANES, CutoverRefused, assess,
                                         validate_identities, GitHubReadOnly)

SHA = "a" * 40
REPO = "Mikeyphw/studyflow"
IDS = {lane: i + 100 for i, lane in enumerate(LANES)}
PATHS = {"legacy": "studyflow-ci.yml", "source_shadow": "source-shadow.yml",
         "expanded_shadow": "expanded-native-shadow.yml",
         "native_fast": "native-sfdt08.yml", "native_deep": "native-sfdt08.yml"}
WIDS = {"legacy": 42, "source_shadow": 43, "expanded_shadow": 44,
        "native_fast": 45, "native_deep": 45}
DIGEST = "sha256:" + "1" * 64


def identities():
    return {lane: {"workflow_id": WIDS[lane],
                   "workflow_path": ".github/workflows/" + PATHS[lane],
                   "attempt": 1} for lane in LANES}


def fixture():
    items = {}
    for lane in LANES:
        jobs = [{"status": "completed", "conclusion": "success", "name": f"{lane}-{n}",
                 "run_id": IDS[lane], "head_sha": SHA}
                for n in range({"legacy": 5, "source_shadow": 1, "expanded_shadow": 4,
                                "native_fast": 3, "native_deep": 5}[lane])]
        items[lane] = {
            "run": {"id": IDS[lane], "head_sha": SHA, "status": "completed",
                    "conclusion": "success", "run_attempt": 1, "name": lane,
                    "workflow_id": WIDS[lane],
                    "path": ".github/workflows/" + PATHS[lane],
                    "head_branch": "main", "repository": {"full_name": REPO},
                    "event": "push" if lane == "legacy" else "workflow_dispatch"},
            "jobs": jobs,
            "artifacts": [{"id": IDS[lane] * 10, "name": lane, "expired": False,
                           "size_in_bytes": 123, "digest": DIGEST,
                           "workflow_run": {"id": IDS[lane], "head_sha": SHA}}],
        }
    return items


def check(f=None, ids=None, contracts=None):
    return assess(SHA, ids if ids is not None else IDS,
                  f if f is not None else fixture(),
                  contracts if contracts is not None else identities(), REPO)


class SFDT16R2Tests(unittest.TestCase):
    def test_green_is_never_cutover_authority(self):
        e = check()
        self.assertEqual(e["status"], "SAME_SHA_SHADOW_INVENTORY_PASS")
        self.assertFalse(e["cutover_authorized"])
        self.assertEqual(e["artifact_attempt_provenance"], "NOT_VERIFIED")
        self.assertEqual(e["artifact_byte_parity"], "NOT_VERIFIED")
        self.assertEqual(len(e["lanes"]), 5)

    def test_sha_mismatch(self):
        f = fixture()
        f["expanded_shadow"]["run"]["head_sha"] = "b" * 40
        with self.assertRaisesRegex(CutoverRefused, "source SHA mismatch"):
            check(f)

    def test_reused_run_id(self):
        ids = dict(IDS)
        ids["native_fast"] = ids["legacy"]
        with self.assertRaisesRegex(CutoverRefused, "Reused run ID"):
            check(ids=ids)

    def test_incomplete_and_failure(self):
        for status, conclusion in (("in_progress", None), ("completed", "failure"),
                                   ("completed", "cancelled")):
            f = fixture()
            f["native_fast"]["run"].update(status=status, conclusion=conclusion)
            with self.subTest(status=status), self.assertRaises(CutoverRefused):
                check(f)

    def test_shadow_skipped_job(self):
        f = fixture()
        f["native_deep"]["jobs"].append({"status": "completed", "conclusion": "skipped", "name": "hidden"})
        with self.assertRaisesRegex(CutoverRefused, "skipped shadow job"):
            check(f)

    def test_legacy_skip_is_observable(self):
        f = fixture()
        f["legacy"]["jobs"].append({"status": "completed", "conclusion": "skipped", "name": "deep"})
        self.assertEqual(check(f)["lanes"][0]["skipped_jobs"], ["deep"])

    def test_missing_and_expired_artifacts(self):
        for mutation in (lambda f: f["native_fast"].update(artifacts=[]),
                         lambda f: f["native_fast"]["artifacts"][0].update(expired=True)):
            f = fixture()
            mutation(f)
            with self.assertRaises(CutoverRefused):
                check(f)

    def test_missing_jobs_and_attempt(self):
        f = fixture(); f["source_shadow"]["jobs"] = []
        with self.assertRaises(CutoverRefused): check(f)
        f = fixture(); f["source_shadow"]["run"].pop("run_attempt")
        with self.assertRaises(CutoverRefused): check(f)

    def test_source_shadow_without_artifact_allowed(self):
        f = fixture(); f["source_shadow"]["artifacts"] = []
        self.assertFalse(check(f)["cutover_authorized"])

    def test_wrong_workflow_id_refused(self):
        f = fixture(); f["expanded_shadow"]["run"]["workflow_id"] = 12345
        with self.assertRaisesRegex(CutoverRefused, "workflow ID mismatch"):
            check(f)

    def test_wrong_workflow_path_refused(self):
        f = fixture(); f["native_fast"]["run"]["path"] = ".github/workflows/evil.yml"
        with self.assertRaisesRegex(CutoverRefused, "workflow path mismatch"):
            check(f)

    def test_wrong_branch_ref_refused_separately_from_workflow_path(self):
        f = fixture(); f["native_deep"]["run"]["head_branch"] = "develop"
        with self.assertRaisesRegex(CutoverRefused, "run is not on main"):
            check(f)

    def test_legacy_qualified_path_is_not_actual_github_rest_schema(self):
        f = fixture()
        f["legacy"]["run"]["path"] = f"{REPO}/.github/workflows/studyflow-ci.yml@refs/heads/main"
        with self.assertRaisesRegex(CutoverRefused, "workflow path mismatch"):
            check(f)

    def test_github_rest_workflow_run_path_is_relative(self):
        f = fixture()
        self.assertEqual(f["legacy"]["run"]["path"], ".github/workflows/studyflow-ci.yml")
        self.assertFalse(check(f)["cutover_authorized"])

    def test_wrong_attempt_refused(self):
        f = fixture(); f["source_shadow"]["run"]["run_attempt"] = 2
        with self.assertRaisesRegex(CutoverRefused, "run attempt"):
            check(f)

    def test_manifest_attempt_not_positive_refused(self):
        c = identities(); c["source_shadow"]["attempt"] = 0
        with self.assertRaises(CutoverRefused): check(contracts=c)

    def test_invalid_manifest_workflow_id_refused(self):
        c = identities(); c["expanded_shadow"]["workflow_id"] = "123"
        with self.assertRaises(CutoverRefused): check(contracts=c)

    def test_manifest_fast_deep_mismatch_refused(self):
        c = identities(); c["native_deep"]["workflow_id"] = 9898
        with self.assertRaisesRegex(CutoverRefused, "SFDT08 fast/deep"):
            check(contracts=c)

    def test_legacy_workflow_cannot_be_replaced(self):
        c = identities(); c["legacy"]["workflow_path"] = ".github/workflows/evil.yml"
        with self.assertRaisesRegex(CutoverRefused, "Authoritative legacy"):
            check(contracts=c)

    def test_bad_artifact_digest(self):
        for digest in (None, "sha256:abc", "md5:" + "0" * 32, "sha256:" + "g" * 64):
            f = fixture(); f["native_fast"]["artifacts"][0]["digest"] = digest
            with self.subTest(digest=digest), self.assertRaisesRegex(CutoverRefused, "digest"):
                check(f)

    def test_missing_artifact_size_or_id(self):
        for mutation in (lambda a: a.update(size_in_bytes=0),
                         lambda a: a.update(id=0)):
            f = fixture(); mutation(f["legacy"]["artifacts"][0])
            with self.assertRaises(CutoverRefused): check(f)

    def test_duplicate_artifact_id(self):
        f = fixture(); f["native_deep"]["artifacts"][0]["id"] = f["legacy"]["artifacts"][0]["id"]
        with self.assertRaisesRegex(CutoverRefused, "duplicate artifact"):
            check(f)

    def test_wrong_producer(self):
        f = fixture(); f["native_fast"]["artifacts"][0]["workflow_run"]["id"] = IDS["legacy"]
        with self.assertRaisesRegex(CutoverRefused, "artifact producer run mismatch"):
            check(f)

    def test_wrong_run_repository(self):
        f = fixture(); f["native_fast"]["run"]["repository"]["full_name"] = "other/repo"
        with self.assertRaisesRegex(CutoverRefused, "source repository mismatch"):
            check(f)

    def test_wrong_event(self):
        f = fixture(); f["native_fast"]["run"]["event"] = "pull_request"
        with self.assertRaisesRegex(CutoverRefused, "unexpected trigger"):
            check(f)

    def test_wrong_job_sha(self):
        f = fixture(); f["native_fast"]["jobs"][0]["head_sha"] = "c" * 40
        with self.assertRaisesRegex(CutoverRefused, "job SHA mismatch"):
            check(f)

    def test_missing_required_job_count(self):
        f = fixture(); f["native_deep"]["jobs"] = f["native_deep"]["jobs"][:3]
        with self.assertRaisesRegex(CutoverRefused, "too few jobs"):
            check(f)

    def test_api_path_is_read_only(self):
        api = GitHubReadOnly(REPO, "token-placeholder")
        with self.assertRaisesRegex(CutoverRefused, "Unexpected GitHub API path"):
            api.get("/git/refs/heads/main")


if __name__ == "__main__":
    unittest.main()
