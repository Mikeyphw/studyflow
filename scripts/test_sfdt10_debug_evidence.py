#!/usr/bin/env python3
"""Source-only negative and positive tests for SFDT10 evidence capture."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sfdt10_debug_evidence import (DEVTOOL_SHA, EvidenceError, collect, parsed_junit,
                                    native_record, sha)

SOURCE = "a" * 40
TREE = "b" * 40


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.handle = tempfile.TemporaryDirectory(prefix="sfdt10-tests-")
        self.base = Path(self.handle.name)
        self.root = self.base / "source"
        self.temp = self.base / "runner"
        self.root.mkdir()
        self.temp.mkdir()
        self.signer = self.base / "apksigner"
        self.signer.write_text('#!/bin/sh\nprintf "Signer #1 certificate DN: debug\\n"\n')
        self.signer.chmod(0o700)

    def tearDown(self):
        self.handle.cleanup()

    def file(self, root: Path, name: str, payload: bytes):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        return path

    def receipt(self, lane: str, *, typed_total: int = 1):
        is_release = lane == "phone-release-jvm"
        typed = {"source_commit": SOURCE, "run_id": 42, "attempt": 1,
                 "graph_sha256": "c" * 64, "release_qualified": False,
                 "authenticated_remote": False,
                 "typed_evidence": {"tests": {"total": typed_total}}}
        digest = sha(json.dumps(typed, sort_keys=True,
                                separators=(",", ":"), ensure_ascii=True).encode())
        typed["receipt_sha256"] = digest
        self.file(self.temp, "rm06-evidence/node-01/node-evidence.json",
                  json.dumps(typed).encode())
        source = {"source_sha": SOURCE, "source_tree": TREE, "runtime_sha": DEVTOOL_SHA,
                  "run_id": 42, "attempt": 1}
        if is_release:
            receipt = {"kind": "studyflow.sfdt08.native-coverage", "status": "NATIVE_COVERAGE_PASSED",
                       "lane": lane, "native_graph_executed": True, "source": source,
                       "job_id": "node-01", "native_receipt_sha256": digest,
                       "provider": "android-gradle", "test_gate": "PASS",
                       "junit": {"local_verified": True}}
            rel = "sfdt08/receipt.json"
        else:
            receipt = {"kind": "studyflow.sfdt07.native-hosted-shadow",
                       "status": "NATIVE_SHADOW_PASSED", "lane": lane,
                       "native_graph_executed": True, "source": source,
                       "node": {"job_id": "node-01", "native_receipt_sha256": digest,
                                "provider": "android-gradle", "remote_authenticated": False}}
            rel = "sfdt07/receipt.json"
        receipt.update(legacy_equivalent=False, release_qualified=False,
                       device_qualified=False, cutover_authorized=False)
        self.file(self.temp, rel, json.dumps(receipt).encode())

    def android(self, lane: str, *, xml=True):
        module = "app-tv" if lane == "tv-debug" else "app"
        self.file(self.root, f"{module}/build/outputs/apk/debug/{module}-debug.apk", b"PKabcdef")
        if xml:
            self.file(self.root, f"{module}/build/reports/lint-results-debug.xml",
                      b'<issues format="6" by="lint" />')
        if lane == "tv-debug":
            self.file(self.root, "app-tv/build/test-results/testDebugUnitTest/TEST-Demo.xml",
                      b'<testsuite tests="1" failures="0" errors="0" skipped="0">'
                      b'<testcase name="works" classname="Demo" /></testsuite>')

    def release_tests(self):
        self.file(self.root, "app/build/test-results/testReleaseUnitTest/TEST-Demo.xml",
                  b'<testsuite tests="1" failures="0" errors="0" skipped="0">'
                  b'<testcase name="works" classname="Demo" /></testsuite>')

    def run_collect(self, lane: str):
        return collect(self.root, self.temp, self.temp / "capture", lane,
                       source=SOURCE, run_id=42, attempt=1, apksigner=self.signer)

    def test_phone_debug_collects_apk_and_lint_but_not_invented_junit(self):
        self.receipt("phone-debug")
        self.android("phone-debug")
        result = self.run_collect("phone-debug")
        self.assertEqual(result["tests"]["gate"], "NOT_RUN")
        self.assertEqual({x["role"] for x in result["members"]}, {"debug-apk", "lint-xml"})
        self.assertIs(result["cutover_authorized"], False)

    def test_tv_debug_collects_apk_lint_and_real_junit(self):
        self.receipt("tv-debug")
        self.android("tv-debug")
        result = self.run_collect("tv-debug")
        self.assertEqual(result["tests"], {"total": 1, "failed": 0, "skipped": 0, "gate": "PASS"})

    def test_release_jvm_bound_to_typed_test_count(self):
        self.receipt("phone-release-jvm")
        self.release_tests()
        result = self.run_collect("phone-release-jvm")
        self.assertEqual(result["tests"]["gate"], "PASS")
        self.assertFalse(any(x["role"] == "debug-apk" for x in result["members"]))

    def test_missing_lint_xml_fails_closed(self):
        self.receipt("phone-debug")
        self.android("phone-debug", xml=False)
        with self.assertRaises(EvidenceError): self.run_collect("phone-debug")

    def test_failed_or_missing_typed_receipt_fails_closed(self):
        self.receipt("phone-debug")
        self.android("phone-debug")
        path = self.temp / "rm06-evidence/node-01/node-evidence.json"
        obj = json.loads(path.read_text())
        obj["release_qualified"] = True
        path.write_text(json.dumps(obj))
        with self.assertRaises(EvidenceError): self.run_collect("phone-debug")

    def test_invalid_source_or_run_refused(self):
        self.receipt("phone-debug")
        self.android("phone-debug")
        with self.assertRaises(EvidenceError):
            collect(self.root, self.temp, self.temp / "capture", "phone-debug",
                    source="f" * 40, run_id=42, attempt=1, apksigner=self.signer)

    def test_report_counter_or_failure_cannot_pass(self):
        with self.assertRaises(EvidenceError):
            parsed_junit(b'<testsuite tests="3"><testcase name="one" /></testsuite>')
        with self.assertRaises(EvidenceError):
            parsed_junit(b'<testsuite tests="1" failures="1"><testcase name="one" /></testsuite>')

    def test_failed_apksigner_refused(self):
        self.receipt("phone-debug")
        self.android("phone-debug")
        self.signer.write_text("#!/bin/sh\nexit 1\n")
        with self.assertRaises(EvidenceError): self.run_collect("phone-debug")

    def test_stale_output_refused(self):
        self.receipt("phone-debug")
        self.android("phone-debug")
        (self.temp / "capture").mkdir()
        with self.assertRaises(EvidenceError): self.run_collect("phone-debug")


if __name__ == "__main__":
    unittest.main(verbosity=2)
