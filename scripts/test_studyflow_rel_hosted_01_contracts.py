#!/usr/bin/env python3
"""Fail-closed REL-HOSTED-01 source checks and adversarial receipt validation; no Gradle."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("candidate", ROOT / "scripts/studyflow_hosted_candidate.py")
assert SPEC is not None and SPEC.loader is not None
candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(candidate)
SHA = "abcdef0123456789abcdef0123456789abcdef01"
RUN = "12345678"


def file_text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def fixture(folder: Path) -> None:
    for filename, member in (("unsigned.apk", "AndroidManifest.xml"),
                             ("unsigned.aab", "base/manifest/AndroidManifest.xml")):
        with zipfile.ZipFile(folder / filename, "w", compression=zipfile.ZIP_STORED) as z:
            z.writestr(member, b"fake-manifest" * 240)
    files = {}
    for name, filename in (("apk", "unsigned.apk"), ("aab", "unsigned.aab")):
        p = folder / filename
        files[name] = {"file": filename, "sha256": candidate.digest(p), "bytes": p.stat().st_size}
    payload = {
        "schema": candidate.SCHEMA, "module": "app", "sourceSha": SHA, "githubRunId": RUN,
        "applicationId": "com.mikeyphw.studyflow", "versionCode": 37,
        "versionName": "1.0.0-rc1", "buildType": "hostedCandidate", "signing": "unsigned",
        "releaseReady": False, "artifacts": files,
    }
    (folder / "hosted-candidate-manifest.json").write_text(json.dumps(payload))


class HostedCandidateContracts(unittest.TestCase):
    def test_source_workflow_security_and_production_release_gates_remain_separate(self):
        flow = file_text(".github/workflows/studyflow-hosted-release.yml")
        for token in ("workflow_dispatch:", "expected_sha:", "permissions:\n  contents: read",
                      "persist-credentials: false", "test \"$EXPECTED_SHA\" = \"$GITHUB_SHA\"",
                      "testReleaseUnitTest", "lintRelease", "assembleHostedCandidate",
                      "bundleHostedCandidate", "studyflow_hosted_candidate.py stage", "upload-artifact@v4"):
            self.assertIn(token, flow)
        for forbidden in ("secrets.", "assembleRelease\"", "bundleRelease\"",
                          "gradle publish", "STUDYFLOW_RELEASE_STORE_PASSWORD:", "repository_dispatch"):
            self.assertNotIn(forbidden, flow)
        for module in ("app", "app-tv"):
            gradle = file_text(f"{module}/build.gradle.kts")
            for token in ("GITHUB_ACTIONS", "STUDYFLOW_HOSTED_UNSIGNED_CANDIDATE",
                          'create("hostedCandidate")', 'initWith(getByName("release"))',
                          "signingConfig = null", "isDebuggable = false", 'matchingFallbacks += listOf("release")'):
                self.assertIn(token, gradle)
            self.assertIn('name == "assembleRelease"', gradle)
            self.assertIn('name == "bundleRelease"', gradle)
        self.assertIn('dependsOn(verifyPhoneReleaseSigningPreflight)', file_text("app/build.gradle.kts"))
        self.assertIn('require(studyFlowTvPersonalSigningConfigured || tvReleaseSigningConfigured)',
                      file_text("app-tv/build.gradle.kts"))

    def test_stage_and_private_sign_require_complete_receipt_and_never_claim_release(self):
        source = file_text("scripts/studyflow_hosted_candidate.py")
        for token in ("ensure_unsigned(apk, aab", "inspect_apk(apk, module",
                      "githubRunId", "expected_run_id", "certificateSha256"):
            self.assertIn(token, source)
        self.assertIn('"releaseReady": False', source)
        self.assertIn('"connectedTests": "NOT_RUN"', source)
        self.assertIn('"restoreRestart": "NOT_RUN"', source)
        self.assertIn('"physicalDevice": "NOT_VERIFIED"', source)
        self.assertNotIn("subprocess.run(['./gradlew'", source)
        self.assertIn('"signing properties must be outside repository"', source)
        plan = file_text("scripts/run_target_release_validation.py")
        self.assertIn("hostedSignedCandidateReceipts", plan)
        self.assertIn("Legacy STUDYFLOW_RELEASE_* variables apply only", plan)
        self.assertIn("REL-HOSTED-01", file_text("docs/release/REL_FINAL_RELEASE_EVIDENCE_CAMPAIGN.md"))

    def test_valid_manifest(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d);fixture(p)
            receipt, apk, aab = candidate.load_manifest(p, expected_sha=SHA, expected_run_id=RUN)
            self.assertEqual(receipt["module"], "app")
            self.assertEqual(candidate.digest(apk), receipt["artifacts"]["apk"]["sha256"])
            self.assertEqual(candidate.digest(aab), receipt["artifacts"]["aab"]["sha256"])

    def test_mismatched_sha_and_run_id_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);fixture(p)
            for sha, run in (("0"*40,RUN),(SHA,"9876"),("invalid",RUN),(SHA,"-1")):
                with self.subTest(sha=sha,run=run),self.assertRaises(candidate.UnsafeCandidate):
                    candidate.load_manifest(p,expected_sha=sha,expected_run_id=run)

    def test_tampered_binary_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);fixture(p)
            with zipfile.ZipFile(p/"unsigned.apk", "a") as archive:archive.writestr("tamper", "data")
            with self.assertRaises(candidate.UnsafeCandidate):
                candidate.load_manifest(p,expected_sha=SHA,expected_run_id=RUN)

    def test_swapped_package_name_and_fake_release_authority_rejected(self):
        for field,value in (("applicationId","org.evil"),("releaseReady",True),("signing","signed")):
            with tempfile.TemporaryDirectory() as d:
                p=Path(d);fixture(p);manifest=p/"hosted-candidate-manifest.json"
                j=json.loads(manifest.read_text());j[field]=value;manifest.write_text(json.dumps(j))
                with self.subTest(field=field),self.assertRaises(candidate.UnsafeCandidate):
                    candidate.load_manifest(p,expected_sha=SHA,expected_run_id=RUN)

    def test_manifest_path_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);fixture(p);manifest=p/"hosted-candidate-manifest.json"
            j=json.loads(manifest.read_text());j['artifacts']['apk']['file']='../../evil.apk'
            manifest.write_text(json.dumps(j))
            with self.assertRaises(candidate.UnsafeCandidate):
                candidate.load_manifest(p,expected_sha=SHA,expected_run_id=RUN)

    def test_symlink_binary_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);fixture(p);(p/'unsigned.apk').rename(p/'actual.apk');(p/'unsigned.apk').symlink_to(p/'actual.apk')
            with self.assertRaises(candidate.UnsafeCandidate):
                candidate.load_manifest(p,expected_sha=SHA,expected_run_id=RUN)

    def test_signed_aab_marker_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);fixture(p)
            with zipfile.ZipFile(p/'unsigned.aab','a') as z:z.writestr('META-INF/CERT.SF','bogus')
            with self.assertRaises(candidate.UnsafeCandidate):
                candidate.load_manifest(p,expected_sha=SHA,expected_run_id=RUN)

    def test_no_local_build_commands_in_new_tool(self):
        source=file_text('scripts/studyflow_hosted_candidate.py')
        self.assertNotIn('gradlew',source.lower())
        self.assertNotIn('devtool --target',source.lower())
        self.assertNotIn('"releaseReady": True',source)
        self.assertNotIn('"releaseOpsReady": True',source)

if __name__=='__main__':
    unittest.main(verbosity=2)
