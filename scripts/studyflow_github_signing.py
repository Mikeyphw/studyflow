#!/usr/bin/env python3
"""REL-HOSTED-02: review-gated GitHub signing of exact unsigned candidate artifacts.

No Gradle; no release-ready assertion; never print or upload signing secrets.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from typing import Any

import studyflow_hosted_candidate as candidate

REPOSITORY = "Mikeyphw/studyflow"
ENVIRONMENT = "studyflow-signing"
HOSTED_WORKFLOW_PATH = ".github/workflows/studyflow-hosted-release.yml"
SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
FINGERPRINT_PATTERN = re.compile(r"[0-9a-f]{64}\Z")
RUN_PATTERN = re.compile(r"[1-9][0-9]*\Z")
MODULES = (("phone", "app"), ("tv", "app-tv"))


class SigningBlocked(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SigningBlocked(message)


def check_identity(sha: str, run: str) -> None:
    require(SHA_PATTERN.fullmatch(sha) is not None, "expected source SHA must be 40 lowercase hex")
    require(RUN_PATTERN.fullmatch(run) is not None, "candidate run ID must be positive decimal")


def gh_api(path: str, *, method: str = "GET", data: dict[str, Any] | None = None) -> dict[str, Any]:
    require(shutil.which("gh") is not None, "GitHub CLI (gh) required")
    argv = ["gh", "api", "--method", method, path]
    stdin = None
    if data is not None:
        argv += ["--input", "-"]
        stdin = json.dumps(data)
    result = subprocess.run(argv, input=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    require(result.returncode == 0, f"GitHub API request failed ({method} {path}); verify gh auth/permissions")
    try:
        response = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise SigningBlocked(f"GitHub API returned invalid JSON: {path}") from exc
    require(isinstance(response, dict), f"GitHub API returned non-object JSON: {path}")
    return response


def environment_is_protected() -> None:
    base = f"repos/{REPOSITORY}/environments/{ENVIRONMENT}"
    env = gh_api(base)
    reviewers = [r for r in env.get("protection_rules", []) if r.get("type") == "required_reviewers"]
    require(any(r.get("reviewers") for r in reviewers),
            "signing environment has no required reviewer; refusing secret upload")
    branch = env.get("deployment_branch_policy") or {}
    require(branch.get("custom_branch_policies") is True,
            "signing environment lacks custom branch policy")
    policies = gh_api(base + "/deployment-branch-policies?per_page=100")
    names = [p.get("name") for p in policies.get("branch_policies", [])]
    require(names == ["main"], f"signing environment must permit ONLY main, got {names!r}")
    print("PASS: GitHub environment requires review and main-only deployments")


def setup_environment() -> None:
    """Initialize a new environment; never weaken/overwrite an existing one."""
    base = f"repos/{REPOSITORY}/environments/{ENVIRONMENT}"
    try:
        env = gh_api(base)
    except SigningBlocked:
        # Any failed request could be lack of permissions. Do not rely on this
        # exception to decide whether to update an existing environment.
        result = subprocess.run(["gh", "api", "--include", base], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(re.search(r"HTTP/[0-9.]+\s+404\b", result.stdout + "\n" + result.stderr) is not None,
                "Cannot inspect signing environment; refusing to update it")
        env = None
    if env is not None:
        environment_is_protected()
        print("PASS: Existing signing environment retained unchanged")
        return
    user = gh_api("user")
    uid = user.get("id")
    require(isinstance(uid, int) and uid > 0, "cannot read current GitHub user ID")
    gh_api(base, method="PUT", data={
        "wait_timer": 0,
        "reviewers": [{"type": "User", "id": uid}],
        "prevent_self_review": False,
        "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True},
    })
    gh_api(base + "/deployment-branch-policies", method="POST", data={"name": "main", "type": "branch"})
    environment_is_protected()
    print("PASS: Review-gated signing environment created. You must approve each signing deployment.")


def keystore_fingerprint(props: dict[str, str]) -> str:
    env = dict(os.environ)
    env["STUDYFLOW_KEYTOOL_PASSWORD"] = props["storePassword"]
    cmd = ["keytool", "-list", "-v", "-storetype", "PKCS12", "-keystore", props["storeFile"],
           "-alias", props["keyAlias"], "-storepass:env", "STUDYFLOW_KEYTOOL_PASSWORD"]
    res = subprocess.run(cmd, env=env, capture_output=True, text=True)
    require(res.returncode == 0, "keytool failed to inspect existing signing key")
    found = re.findall(r"\bSHA256:\s*([0-9A-Fa-f:]{64,95})", res.stdout)
    require(len(found) == 1, "could not determine unique certificate SHA-256")
    digest = found[0].replace(":", "").lower()
    require(FINGERPRINT_PATTERN.fullmatch(digest) is not None, "invalid certificate SHA-256")
    return digest


def set_secret(name: str, value: str) -> None:
    result = subprocess.run(["gh", "secret", "set", "--env", ENVIRONMENT,
                             "-R", REPOSITORY, name], input=value, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, f"Failed to store environment secret {name}")
    print(f"Configured {name}")


def configure_secrets(properties: Path) -> None:
    environment_is_protected()  # fail closed before reading or exporting secrets
    props = candidate.java_properties(properties)
    cert = keystore_fingerprint(props)
    data = Path(props["storeFile"]).read_bytes()
    require(0 < len(data) < 32000, "PKCS#12 key is empty or too large for GitHub encrypted secrets")
    secrets = {
        "STUDYFLOW_SIGNING_P12_BASE64": base64.b64encode(data).decode("ascii"),
        "STUDYFLOW_SIGNING_STORE_PASSWORD": props["storePassword"],
        "STUDYFLOW_SIGNING_KEY_PASSWORD": props["keyPassword"],
        "STUDYFLOW_SIGNING_KEY_ALIAS": props["keyAlias"],
        "STUDYFLOW_SIGNING_CERT_SHA256": cert,
    }
    for name, secret in secrets.items():
        set_secret(name, secret)
    environment_is_protected()
    print("PASS: Five environment secrets configured; no key bytes or passwords printed")
    print("Signing certificate SHA-256:", cert)


def check_run_via_api(source_sha: str, run_id: str, token: str, api_root: str = "https://api.github.com") -> None:
    check_identity(source_sha, run_id)
    require(bool(token), "GitHub Actions read-only token missing")
    require(api_root == "https://api.github.com", "untrusted GitHub API host")
    endpoint = f"{api_root}/repos/{REPOSITORY}/actions/runs/{run_id}"
    request = urllib.request.Request(endpoint, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "StudyFlow-reviewed-candidate-signing",
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        run = json.load(response)
    require(run.get("id") == int(run_id), "candidate run ID mismatch")
    require(run.get("head_sha") == source_sha, "candidate run SHA mismatch")
    require(run.get("status") == "completed" and run.get("conclusion") == "success",
            "candidate run did not succeed")
    require(run.get("event") == "workflow_dispatch" and run.get("head_branch") == "main",
            "candidate run was not manually dispatched from main")
    require(run.get("path") == HOSTED_WORKFLOW_PATH,
            "candidate run came from the wrong workflow")
    require((run.get("repository") or {}).get("full_name", "").lower() == REPOSITORY.lower(),
            "candidate run belongs to a different repository")
    print(f"PASS: Immutable unsigned candidate run {run_id} at source SHA {source_sha}")


def verify_candidates(inputs: Path, sha: str, run: str, apksigner: str, apkanalyzer: str) -> None:
    check_identity(sha, run)
    require(inputs.is_dir() and not inputs.is_symlink(), "unsigned artifact root missing or symlinked")
    for name, module in MODULES:
        manifest, apk, aab = candidate.load_manifest(inputs / name, expected_sha=sha, expected_run_id=run)
        require(manifest["module"] == module, f"wrong module in {name} artifact")
        require(manifest.get("buildType") == "hostedCandidate" and manifest.get("signing") == "unsigned" and
                manifest.get("releaseReady") is False, f"invalid unsigned candidate state for {module}")
        candidate.ensure_unsigned(apk, aab, apksigner)
        candidate.inspect_apk(apk, module, apkanalyzer)
        print(f"PASS: {module} unchanged and unsigned; identity and SHA-256 verified")


def property_escape(value: str) -> str:
    result: list[str] = []
    for i, ch in enumerate(value):
        if ch == "\\": result.append("\\\\")
        elif ch == "\n": result.append("\\n")
        elif ch == "\r": result.append("\\r")
        elif ch == "\t": result.append("\\t")
        elif ch in "=:": result.append("\\" + ch)
        elif ch == " " and i == 0: result.append("\\ ")
        elif ord(ch) > 0x7F:
            cp = ord(ch)
            if cp > 0xFFFF:
                cp -= 0x10000
                result.extend((f"\\u{0xD800+(cp>>10):04x}", f"\\u{0xDC00+(cp&0x3ff):04x}"))
            else: result.append(f"\\u{cp:04x}")
        else: result.append(ch)
    return "".join(result)


def github_sign(inputs: Path, outputs: Path, sha: str, run: str,
                apksigner: str, apkanalyzer: str, zipalign: str, jarsigner: str) -> None:
    check_identity(sha, run)
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and
            os.environ.get("GITHUB_REF") == "refs/heads/main" and
            os.environ.get("GITHUB_REPOSITORY", "").lower() == REPOSITORY.lower(),
            "secret-bearing execution is not an approved main-branch GitHub dispatch")
    require(not outputs.exists(), "signed output root already exists; refusing overwrite")
    require(not outputs.resolve().is_relative_to(candidate.ROOT.resolve()), "signed output would be inside repository")
    need = ("STUDYFLOW_SIGNING_P12_BASE64", "STUDYFLOW_SIGNING_STORE_PASSWORD",
            "STUDYFLOW_SIGNING_KEY_PASSWORD", "STUDYFLOW_SIGNING_KEY_ALIAS", "STUDYFLOW_SIGNING_CERT_SHA256")
    for key in need:
        require(bool(os.environ.get(key)), f"Missing protected environment secret: {key}")
    expected_cert = os.environ["STUDYFLOW_SIGNING_CERT_SHA256"].lower().replace(":", "")
    require(FINGERPRINT_PATTERN.fullmatch(expected_cert) is not None, "invalid pinned certificate SHA-256")
    try:
        key_bytes = base64.b64decode(os.environ["STUDYFLOW_SIGNING_P12_BASE64"], validate=True)
    except (binascii.Error, ValueError) as exc:
        raise SigningBlocked("protected PKCS#12 secret is not valid base64") from exc
    require(0 < len(key_bytes) < 32000, "unexpected PKCS#12 payload length")
    verify_candidates(inputs, sha, run, apksigner, apkanalyzer)
    old_umask = os.umask(0o077)
    try:
        with tempfile.TemporaryDirectory(prefix="studyflow-signing-") as tmp:
            tmp_dir = Path(tmp)
            key_path = tmp_dir / "private.p12"
            key_path.write_bytes(key_bytes)
            key_path.chmod(0o600)
            properties = tmp_dir / "android-signing.properties"
            values = {
                "storeFile": str(key_path),
                "storePassword": os.environ["STUDYFLOW_SIGNING_STORE_PASSWORD"],
                "keyAlias": os.environ["STUDYFLOW_SIGNING_KEY_ALIAS"],
                "keyPassword": os.environ["STUDYFLOW_SIGNING_KEY_PASSWORD"],
            }
            properties.write_text("".join(f"{k}={property_escape(v)}\n" for k, v in values.items()), encoding="utf-8")
            properties.chmod(0o600)
            props = candidate.java_properties(properties)
            require(keystore_fingerprint(props) == expected_cert, "keystore certificate differs from pinned fingerprint")
            outputs.mkdir(parents=True, mode=0o700)
            for subdir, module in MODULES:
                args = argparse.Namespace(bundle_dir=str(inputs / subdir), out_dir=str(outputs / subdir),
                    expected_sha=sha, expected_run_id=run, signing_properties=str(properties),
                    apksigner=apksigner, apkanalyzer=apkanalyzer, zipalign=zipalign,
                    jarsigner=jarsigner, report_dir=str(tmp_dir / "reports"))
                candidate.sign(args)
                receipt_path = outputs / subdir / "hosted-candidate-signing-receipt.json"
                receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
                require(receipt.get("status") == "SIGNED_STAGE_ONLY" and receipt.get("releaseReady") is False and
                        receipt.get("releaseOpsReady") is False,
                        "signing receipt falsely claims release authorization")
                require(receipt.get("module") == module and receipt.get("sourceSha") == sha and
                        receipt.get("githubRunId") == run, "signed receipt identity mismatch")
                require(receipt.get("certificateSha256", "").lower().replace(":", "") == expected_cert,
                        "signed APK fingerprint differs from pinned certificate")
                for kind in ("apk", "aab"):
                    record = receipt["signedArtifacts"][kind]
                    path = outputs / subdir / record["file"]
                    require(path.is_file() and not path.is_symlink() and candidate.digest(path) == record["sha256"] and
                            path.stat().st_size == record["bytes"], f"signed {kind} hash mismatch")
                print(f"PASS: {module} signed and verified at immutable candidate SHA {sha}")
    except BaseException:
        if outputs.exists():
            shutil.rmtree(outputs)
        raise
    finally:
        os.umask(old_umask)
    print("HOSTED_GITHUB_SIGNING: SIGNED_STAGE_ONLY (NOT REL-FINAL)")
    print("Certificate SHA-256:", expected_cert)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    sp = p.add_subparsers(dest="command", required=True)
    sp.add_parser("setup-environment")
    cs = sp.add_parser("configure-secrets")
    cs.add_argument("--signing-properties", default=str(Path.home() / ".config/studyflow/android-signing.properties"))
    cr = sp.add_parser("check-run")
    cr.add_argument("--candidate-run-id", required=True)
    cr.add_argument("--candidate-sha", required=True)
    vp = sp.add_parser("verify-candidates")
    vp.add_argument("--input-root", required=True)
    vp.add_argument("--candidate-run-id", required=True)
    vp.add_argument("--candidate-sha", required=True)
    gs = sp.add_parser("sign")
    gs.add_argument("--input-root", required=True)
    gs.add_argument("--output-root", required=True)
    gs.add_argument("--candidate-run-id", required=True)
    gs.add_argument("--candidate-sha", required=True)
    for q in (vp, gs):
        q.add_argument("--apksigner", default="apksigner")
        q.add_argument("--apkanalyzer", default="apkanalyzer")
    gs.add_argument("--zipalign", default="zipalign")
    gs.add_argument("--jarsigner", default="jarsigner")
    args = p.parse_args()
    try:
        if args.command == "setup-environment": setup_environment()
        elif args.command == "configure-secrets": configure_secrets(Path(args.signing_properties).expanduser())
        elif args.command == "check-run": check_run_via_api(args.candidate_sha, args.candidate_run_id, os.environ.get("GH_TOKEN", ""))
        elif args.command == "verify-candidates": verify_candidates(Path(args.input_root), args.candidate_sha, args.candidate_run_id, args.apksigner, args.apkanalyzer)
        elif args.command == "sign": github_sign(Path(args.input_root), Path(args.output_root), args.candidate_sha,
            args.candidate_run_id, args.apksigner, args.apkanalyzer, args.zipalign, args.jarsigner)
    except (SigningBlocked, candidate.UnsafeCandidate, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f"HOSTED_GITHUB_SIGNING_BLOCKED: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
