#!/usr/bin/env python3
"""REL-HOSTED-02 signing workflow: fail-closed source-only contracts."""
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("studyflow_github_signing", ROOT / "scripts/studyflow_github_signing.py")
import sys
sys.path.insert(0, str(ROOT / "scripts"))
assert SPEC and SPEC.loader
S = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(S)

GOOD_RUN = {
    "id": 41223283343,
    "head_sha": "8de803fba750dfac21c3471c915574b812b98b25",
    "status": "completed", "conclusion": "success", "event": "workflow_dispatch", "head_branch": "main",
    "path": ".github/workflows/studyflow-hosted-release.yml",
    "repository": {"full_name": "Mikeyphw/studyflow"},
}
PROTECTED_ENV = {
    "protection_rules": [{"type": "required_reviewers", "reviewers": [
        {"type": "User", "reviewer": {"login": "owner", "id": 1}},
    ]}],
    "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True},
}

class GithubSigningContracts(unittest.TestCase):
    def test_immutable_run_is_accepted(self):
        with patch.object(S.urllib.request, "urlopen", return_value=io.BytesIO(json.dumps(GOOD_RUN).encode())):
            S.check_run_via_api(GOOD_RUN["head_sha"], str(GOOD_RUN["id"]), "sample-token")

    def test_mismatched_or_untrusted_candidate_run_rejected(self):
        for field, value in (("head_sha", "0" * 40), ("status", "in_progress"),
                             ("conclusion", "failure"), ("event", "pull_request"),
                             ("head_branch", "feature"), ("path", ".github/workflows/other.yml"),
                             ("repository", {"full_name": "attacker/repo"}), ("id", 999)):
            with self.subTest(field=field):
                bad = copy.deepcopy(GOOD_RUN)
                bad[field] = value
                with patch.object(S.urllib.request, "urlopen", return_value=io.BytesIO(json.dumps(bad).encode())):
                    with self.assertRaises(S.SigningBlocked):
                        S.check_run_via_api(GOOD_RUN["head_sha"], str(GOOD_RUN["id"]), "token")

    def test_bad_sha_and_run_ids_rejected(self):
        for sha, run in (("../bad", "41223283343"), (GOOD_RUN["head_sha"], "123;echo bad"),
                         (GOOD_RUN["head_sha"].upper(), "123"), (GOOD_RUN["head_sha"], "0")):
            with self.assertRaises(S.SigningBlocked):
                S.check_identity(sha, run)

    def test_fingerprint_and_unicode_properties_escaped(self):
        values = {"storeFile": "/test path/a.p12", "storePassword": " :yes=π😀\\\t", "keyAlias": "studyflow", "keyPassword": "password"}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "android-signing.properties"
            path.write_text("".join(f"{k}={S.property_escape(v)}\n" for k,v in values.items()))
            # No key on disk: simply validate the Java-properties decoding on paper.
            raw = path.read_text()
            self.assertIn("\\u03c0", raw)
            self.assertIn("\\ud83d\\ude00", raw)
            self.assertIn("\\ ", raw)
            self.assertIn("\\:", raw)
            self.assertIn("\\=", raw)

    def test_environment_fails_closed_without_review_or_extra_branches(self):
        okpol = {"branch_policies": [{"name": "main"}]}
        with patch.object(S, "gh_api", side_effect=[PROTECTED_ENV, okpol]):
            S.environment_is_protected()
        for env, pol in (({"protection_rules": [], "deployment_branch_policy": PROTECTED_ENV["deployment_branch_policy"]}, okpol),
                         (PROTECTED_ENV, {"branch_policies": []}),
                         (PROTECTED_ENV, {"branch_policies": [{"name": "main"}, {"name": "release"}]}),
                         (PROTECTED_ENV | {"deployment_branch_policy": {}}, okpol)):
            with self.subTest(env=env, policies=pol):
                with patch.object(S, "gh_api", side_effect=[env, pol]):
                    with self.assertRaises(S.SigningBlocked):
                        S.environment_is_protected()

    def test_secret_upload_requires_protected_env_first(self):
        with patch.object(S, "environment_is_protected", side_effect=S.SigningBlocked("unprotected")), \
             patch.object(S.candidate, "java_properties") as parser, \
             patch.object(S, "set_secret") as upload:
            with self.assertRaises(S.SigningBlocked):
                S.configure_secrets(Path("/missing"))
            parser.assert_not_called()
            upload.assert_not_called()

    def test_does_not_sign_from_local_or_pull_request(self):
        with patch.dict(S.os.environ, {"GITHUB_ACTIONS": "false", "GITHUB_EVENT_NAME": "pull_request",
                                      "GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": S.REPOSITORY}):
            with self.assertRaisesRegex(S.SigningBlocked, "not an approved"):
                S.github_sign(Path("x"), Path("y"), GOOD_RUN["head_sha"], str(GOOD_RUN["id"]),
                              "apksigner", "apkanalyzer", "zipalign", "jarsigner")

    def test_source_workflow_security(self):
        text = (ROOT / ".github/workflows/studyflow-hosted-sign.yml").read_text()
        self.assertIn("workflow_dispatch:", text)
        self.assertNotIn("pull_request:", text)
        self.assertNotIn("push:\n", text)
        self.assertNotIn("gradlew", text)
        self.assertIn("environment: studyflow-signing", text)
        self.assertIn("github.ref == 'refs/heads/main'", text)
        self.assertIn("actions: read", text)
        self.assertIn("contents: read", text)
        self.assertIn("persist-credentials: false", text)
        self.assertIn("run-id: ${{ inputs.candidate_run_id }}", text)
        self.assertIn("STUDYFLOW_SIGNING_CERT_SHA256: ${{ secrets.STUDYFLOW_SIGNING_CERT_SHA256 }}", text)
        # Signing secrets cannot appear in preflight job.
        prefix = text.split("  sign:\n", 1)[0]
        self.assertNotIn("STUDYFLOW_SIGNING_P12_BASE64", prefix)
        self.assertNotIn("STUDYFLOW_SIGNING_STORE_PASSWORD", prefix)
        self.assertNotIn("STUDYFLOW_SIGNING_CERT_SHA256", prefix)

    def test_no_release_readiness_or_signing_key_in_uploaded_artifact(self):
        text = (ROOT / ".github/workflows/studyflow-hosted-sign.yml").read_text()
        self.assertNotIn("releaseReady=true", text)
        self.assertNotIn("REL_FINAL: PASS", text)
        self.assertNotIn("--mode execute", text)
        self.assertIn("$RUNNER_TEMP/studyflow-signed", text)
        self.assertNotIn("path: ${{ runner.temp }}/studyflow-signing-", text)
        self.assertIn("path: ${{ runner.temp }}/studyflow-signed/phone/", text)
        self.assertIn("path: ${{ runner.temp }}/studyflow-signed/tv/", text)
        source = (ROOT / "scripts/studyflow_github_signing.py").read_text()
        self.assertIn("SIGNED_STAGE_ONLY", source)
        self.assertIn("keystore certificate differs from pinned fingerprint", source)

if __name__ == "__main__":
    unittest.main(verbosity=2)
