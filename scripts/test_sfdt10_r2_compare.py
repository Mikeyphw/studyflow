#!/usr/bin/env python3
"""Source-only contract tests for SFDT10 R2; no gh, network, SDK or Gradle."""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfdt10_r2_compare as c

SOURCE = "a" * 40


class R2Contracts(unittest.TestCase):
    def test_matching_junit_identity(self):
        xml = b'<testsuite tests="2" failures="0" errors="0" skipped="1"><testcase name="one" classname="A" /><testcase name="two" classname="A"><skipped/></testcase></testsuite>'
        result = c.xml_junit(xml)
        self.assertEqual(result[("A", "one", "PASS")], 1)
        self.assertEqual(result[("A", "two", "SKIP")], 1)

    def test_malformed_junit_and_failures_refused(self):
        for blob in (b'<testsuite tests="4"><testcase name="one" classname="A" /></testsuite>',
                     b'<testsuite tests="1" failures="1"><testcase name="x" classname="A"><failure/></testcase></testsuite>',
                     b'<testsuite tests="1"><testcase name="x" /></testsuite>', b'<huh />'):
            with self.subTest(blob=blob), self.assertRaises(c.ComparisonError):
                c.xml_junit(blob)

    def test_native_manifest_binds_actual_members_and_receipt(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            path = root / 'sfdt10/members/app/build/reports/lint-results-debug.xml'
            path.parent.mkdir(parents=True)
            path.write_bytes(b'<issues />')
            raw = {"source_commit": SOURCE, "run_id": 77, "attempt": 1,
                   "release_qualified": False, "authenticated_remote": False}
            digest = c.sha(json.dumps(raw,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode())
            typed = dict(raw, receipt_sha256=digest)
            receipt = {"kind": "studyflow.sfdt07.native-hosted-shadow",
                       "status": "NATIVE_SHADOW_PASSED", "source": {"source_sha": SOURCE}}
            for name,obj in [('rm06-evidence/node-01/node-evidence.json',typed),('sfdt07/receipt.json',receipt)]:
                p = root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj))
            native = {"path":"sfdt07/receipt.json", "sha256":c.sha((root/'sfdt07/receipt.json').read_bytes()),
                      "typed_path":"rm06-evidence/node-01/node-evidence.json",
                      "typed_sha256":c.sha((root/'rm06-evidence/node-01/node-evidence.json').read_bytes()),
                      "typed_receipt_sha256":digest}
            rec = {"kind":"studyflow.sfdt10.native-debug-parity-evidence",
                   "status":"LOCAL_NATIVE_BYTES_CAPTURED_UNQUALIFIED", "lane":"phone-debug",
                   "source_sha":SOURCE,"github_run_id":77,"github_run_attempt":1,
                   "native":native,
                   "members":[{"path":"app/build/reports/lint-results-debug.xml", "size":len(path.read_bytes()),
                               "sha256":c.sha(path.read_bytes())}],
                   **{k:False for k in ('legacy_equivalent','deep_equivalent','remote_authenticated','cutover_authorized','release_qualified','device_qualified')}}
            (root/'sfdt10/manifest.json').write_text(json.dumps(rec))
            self.assertEqual(c.stage_manifest(root,'phone-debug',SOURCE,77,1)['lane'],'phone-debug')
            path.write_bytes(b'<issues><issue /></issues>')
            with self.assertRaises(c.ComparisonError):
                c.stage_manifest(root,'phone-debug',SOURCE,77,1)

    def test_lint_issue_counters(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'lint-results-debug.xml').write_bytes(b'<issues><issue id="SomeId" severity="Warning"/></issues>')
            self.assertEqual(c.lint_inventory(root), Counter({('SomeId','Warning'):1}))
            (root/'lint-results-debug.xml').write_bytes(b'<not-issues />')
            with self.assertRaises(c.ComparisonError):c.lint_inventory(root)

    def test_run_identity_refusals(self):
        r={"id":8,"head_sha":SOURCE,"head_branch":"main","repository":{"full_name":c.REPO},
           "path":c.SHADOW_WORKFLOW,"status":"completed","conclusion":"success",
           "event":"workflow_dispatch","run_attempt":1}
        self.assertEqual(c.run_check(r,number=8,workflow=c.SHADOW_WORKFLOW,source=SOURCE),1)
        for key, value in [("head_sha","f"*40), ("head_branch","feature"), ("event","push"),
                           ("conclusion","failure"),("path",c.LEGACY_WORKFLOW)]:
            with self.subTest(key=key), self.assertRaises(c.ComparisonError):
                c.run_check(dict(r,**{key:value}),number=8,workflow=c.SHADOW_WORKFLOW,source=SOURCE)

    def test_job_and_artifact_fail_closed(self):
        c.jobs_check({"total_count":1,"jobs":[{"name":"one","conclusion":"success"}]},{"one"})
        for obj in ({"total_count":1,"jobs":[]}, {"total_count":1,"jobs":[{"name":"one","conclusion":"failure"}]}):
            with self.assertRaises(c.ComparisonError):c.jobs_check(obj,{"one"})
        with self.assertRaises(c.ComparisonError):c.artifact_index({"total_count":2,"artifacts":[{"name":"x"},{"name":"x"}]})

    def test_artifact_member_guard(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'one.txt').write_text('hello')
            self.assertEqual(c.exact_file(root,'one.txt').name,'one.txt')
            with self.assertRaises(c.ComparisonError):c.exact_file(root,'missing.txt')


if __name__ == '__main__':
    unittest.main(verbosity=2)
