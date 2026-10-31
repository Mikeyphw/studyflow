#!/usr/bin/env python3
"""Fail-closed REL-HOSTED-01 source checks and adversarial receipt validation; no Gradle."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import re
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



def validate_hosted_candidate_test_matrix(flow: str, phone_gradle: str, tv_gradle: str) -> None:
    """Require module-specific JVM tests, not a common guessed Release task.

    The TV module currently has default debug JVM tests, unlike the phone
    module's explicit release testBuildType. Enforce this where the candidate
    tasks are selected, not just by searching for a task name somewhere in YAML.
    """
    if not re.search(r'testBuildType\s*=\s*"release"', phone_gradle):
        raise ValueError("Phone JVM test selection no longer matches testBuildType")
    if re.search(r'testBuildType\s*=\s*"release"', tv_gradle):
        raise ValueError("TV JVM test selection needs a deliberate source review")
    section = flow.split("  stage:\n", 1)
    if len(section) != 2:
        raise ValueError("Missing unique candidate staging job")
    stage = section[1]
    if "  stage:\n" in stage:
        raise ValueError("Duplicated staging job")
    matrix = stage.split("    strategy:\n", 1)
    if len(matrix) != 2 or "    env:\n" not in matrix[1]:
        raise ValueError("Missing candidate stage strategy/matrix")
    strategy = matrix[1].split("    env:\n", 1)[0]
    mapping = re.findall(r"^          - module: ([\w-]+)\n            test_task: '([^']+)'$", strategy, re.M)
    if mapping != [("app", ":app:testReleaseUnitTest"),
                   ("app-tv", ":app-tv:testDebugUnitTest")]:
        raise ValueError(f"Hosted candidate tests disagree with configured modules: {mapping}")
    if re.search(r"^        module:\s*\[", strategy, re.M):
        raise ValueError("Conflicting module-only matrix")
    for token in ("          MODULE: ${{ matrix.module }}\n",
                  "          TEST_TASK: ${{ matrix.test_task }}\n",
                  '            "$TEST_TASK" \\\n',
                  '            ":${MODULE}:lintRelease" \\\n',
                  '            ":${MODULE}:assembleHostedCandidate" \\\n',
                  '            ":${MODULE}:bundleHostedCandidate"\n'):
        if token not in stage:
            raise ValueError(f"Hosted candidate task execution drift: {token!r}")
    if '":${MODULE}:testReleaseUnitTest"' in stage or '"$TEST_TASK"' not in stage:
        raise ValueError("Candidate job must not hardcode a shared JVM test task")
    if stage.count('TEST_TASK: ${{ matrix.test_task }}') != 1:
        raise ValueError("Candidate JVM task binding must occur exactly once")


def validate_tv_signed_release_gate(gradle: str) -> None:
    """Release signing must protect final output tasks, never shared resource work.

    The prior TV guard used a doFirst closure on *every* packageRelease* task.
    packageReleaseResources is needed by lintRelease/hostedCandidate and the
    closure also broke Gradle configuration-cache serialization.
    """
    start = gradle.split("// REL-HOSTED-01-R2:", 1)
    if len(start) != 2:
        raise ValueError("Missing TV release guard revision")
    guard = start[1].split("// ATT12", 1)[0]
    required = (
        'tasks.register<Exec>("verifyTvReleaseSigningPreflight")',
        '"scripts/ov01_release_gate.py", "signing", "--surface", "tv"',
        'if (studyFlowTvPersonalSigningConfigured)',
        'environment("STUDYFLOW_RELEASE_STORE_FILE", studyFlowTvPersonalSigningStoreFile!!.absolutePath)',
        'environment("STUDYFLOW_RELEASE_STORE_PASSWORD", studyFlowTvSigningValue("storePassword"))',
        'environment("STUDYFLOW_RELEASE_KEY_ALIAS", studyFlowTvSigningValue("keyAlias"))',
        'environment("STUDYFLOW_RELEASE_KEY_PASSWORD", studyFlowTvSigningValue("keyPassword"))',
        'name == "assembleRelease"', 'name == "bundleRelease"',
        'name.startsWith("signRelease")',
        'name != "packageReleaseResources"',
        '!name.contains("UnitTest")',
        'dependsOn(verifyTvReleaseSigningPreflight)',
    )
    for token in required:
        if token not in guard:
            raise ValueError(f"TV signed Release guard lost protection: {token}")
    if re.search(r"\bdoFirst\s*\{|\bdoLast\s*\{", guard):
        raise ValueError("TV Release guard must not capture Gradle script in task actions")
    if not re.search(
        r'name\.startsWith\("packageRelease"\)\s*&&\s*'
        r'name != "packageReleaseResources"\s*&&\s*'
        r'!name\.contains\("UnitTest"\)', guard,
    ):
        raise ValueError("Shared TV release resource and test tasks must be excluded")
    if 'signingConfig = null' not in gradle or 'create("hostedCandidate")' not in gradle:
        raise ValueError("Unsigned hostedCandidate build type was removed")


class HostedCandidateContracts(unittest.TestCase):
    def test_source_workflow_security_and_production_release_gates_remain_separate(self):
        flow = file_text(".github/workflows/studyflow-hosted-release.yml")
        for token in ("workflow_dispatch:", "expected_sha:", "permissions:\n  contents: read",
                      "persist-credentials: false", "test \"$EXPECTED_SHA\" = \"$GITHUB_SHA\"",
                      "testReleaseUnitTest", "testDebugUnitTest", "lintRelease", "assembleHostedCandidate",
                      "bundleHostedCandidate", "studyflow_hosted_candidate.py stage", "upload-artifact@v4"):
            self.assertIn(token, flow)
        validate_hosted_candidate_test_matrix(
            flow, file_text("app/build.gradle.kts"), file_text("app-tv/build.gradle.kts"))
        self.assertIn("Preserve hosted candidate lint and JVM test diagnostics", flow)
        self.assertIn("if-no-files-found: warn", flow)
        self.assertIn("if: always()", flow)
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
        validate_tv_signed_release_gate(file_text("app-tv/build.gradle.kts"))

    def test_adversarial_candidate_test_matrix_drift_rejected(self):
        flow = file_text(".github/workflows/studyflow-hosted-release.yml")
        phone = file_text("app/build.gradle.kts")
        tv = file_text("app-tv/build.gradle.kts")
        mutations = {
            "tv_release_unit_test": flow.replace(":app-tv:testDebugUnitTest", ":app-tv:testReleaseUnitTest"),
            "phone_debug_unit_test": flow.replace(":app:testReleaseUnitTest", ":app:testDebugUnitTest"),
            "common_release_test": flow.replace('"$TEST_TASK"', '":${MODULE}:testReleaseUnitTest"'),
            "wrong_matrix_binding": flow.replace("TEST_TASK: ${{ matrix.test_task }}", "TEST_TASK: ${{ matrix.module }}"),
            "duplicate_tv_module": flow.replace("- module: app-tv", "- module: app"),
        }
        for label, bad_flow in mutations.items():
            self.assertNotEqual(bad_flow, flow, label)
            with self.subTest(label=label), self.assertRaises(ValueError):
                validate_hosted_candidate_test_matrix(bad_flow, phone, tv)
        with self.assertRaises(ValueError):
            validate_hosted_candidate_test_matrix(flow, phone, tv + '\ntestBuildType = "release"\n')

    def test_tv_release_guard_configuration_cache_and_candidate_regressions(self):
        tv = file_text("app-tv/build.gradle.kts")
        validate_tv_signed_release_gate(tv)
        mutations = {
            "resource_packaging_guard": tv.replace(
                '                name != "packageReleaseResources" &&\n', ''),
            "unit_test_guard": tv.replace('                !name.contains("UnitTest")', '                true'),
            "unsigned_release_possible": tv.replace(
                'dependsOn(verifyTvReleaseSigningPreflight)', 'println("gate skipped")'),
            "missing_apk_gate": tv.replace('name == "assembleRelease"', 'name == "assembleDebug"'),
            "missing_aab_gate": tv.replace('name == "bundleRelease"', 'name == "bundleDebug"'),
            "missing_sign_gate": tv.replace('name.startsWith("signRelease")', 'name.startsWith("signDebug")'),
            "configuration_cache_closure": tv.replace(
                'dependsOn(verifyTvReleaseSigningPreflight)',
                'doFirst { dependsOn(verifyTvReleaseSigningPreflight) }'),
            "missing_store_password_bridge": tv.replace(
                'environment("STUDYFLOW_RELEASE_STORE_PASSWORD", studyFlowTvSigningValue("storePassword"))', ''),
        }
        for label, damaged in mutations.items():
            self.assertNotEqual(damaged, tv, label)
            with self.subTest(label=label), self.assertRaises(ValueError):
                validate_tv_signed_release_gate(damaged)

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
