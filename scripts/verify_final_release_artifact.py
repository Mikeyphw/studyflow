#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_APPLICATION_ID = "com.mikeyphw.studyflow"
EXPECTED_VERSION_CODE = "37"
EXPECTED_VERSION_NAME = "1.0.0-rc1"
REPORT_DIR = ROOT / "build/reports/studyflow"
DEFAULT_REPORT = REPORT_DIR / "release-artifact-identity.json"


def fail(message: str, code: int = 1) -> None:
    print(f"RELEASE_ARTIFACT_IDENTITY: FAILED — {message}", file=sys.stderr)
    raise SystemExit(code)


def load_json(path: Path) -> dict:
    if not path.is_file():
        fail(f"missing prerequisite host evidence: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"prerequisite evidence is not an object: {path}")
    return value


def sdk_root() -> Path | None:
    raw = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    return Path(raw).expanduser().resolve() if raw else None


def find_android_tool(name: str) -> Path:
    direct = shutil.which(name)
    if direct:
        return Path(direct)
    sdk = sdk_root()
    if not sdk:
        fail(f"{name} is not on PATH and ANDROID_HOME/ANDROID_SDK_ROOT is unset")
    if name == "apkanalyzer":
        candidates = list((sdk / "cmdline-tools").glob("*/bin/apkanalyzer"))
    else:
        candidates = list((sdk / "build-tools").glob(f"*/{name}"))
    existing = [path for path in candidates if path.is_file() and os.access(path, os.X_OK)]
    if not existing:
        fail(f"unable to locate {name} in Android SDK {sdk}")
    return sorted(existing, key=lambda path: path.as_posix())[-1]


def find_host_tool(name: str) -> Path:
    direct = shutil.which(name)
    if not direct:
        fail(f"required host tool is missing: {name}")
    return Path(direct)


def run(*args: object) -> str:
    command = [str(arg) for arg in args]
    completed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if completed.returncode:
        detail = (completed.stderr or completed.stdout).strip()
        fail(f"command failed ({completed.returncode}): {' '.join(command)}\n{detail}")
    return (completed.stdout or completed.stderr).strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk")
    parser.add_argument("--aab")
    parser.add_argument("--host", default=str(REPORT_DIR / "release-host-gates.json"))
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    args = parser.parse_args()

    host_path = Path(args.host).expanduser().resolve()
    host = load_json(host_path)
    host_session = str(host.get("validationSession") or "").strip()
    if host.get("status") != "PASS" or not host_session:
        fail("host-gate evidence is not a current PASS with a validation session")
    requested_session = (os.environ.get("STUDYFLOW_RELEASE_VALIDATION_SESSION") or "").strip()
    if requested_session and requested_session != host_session:
        fail("requested validation session does not match current host-gate evidence")

    host_artifacts = host.get("artifacts", {})
    host_apk = host_artifacts.get("releaseApk", {}) if isinstance(host_artifacts, dict) else {}
    host_aab = host_artifacts.get("releaseAab", {}) if isinstance(host_artifacts, dict) else {}
    raw_apk = args.apk or os.environ.get("STUDYFLOW_RELEASE_APK") or host_apk.get("path")
    raw_aab = args.aab or os.environ.get("STUDYFLOW_RELEASE_AAB") or host_aab.get("path")
    if not raw_apk or not raw_aab:
        fail("host evidence must provide both Release APK and AAB paths")
    apk = Path(str(raw_apk)).expanduser().resolve()
    aab = Path(str(raw_aab)).expanduser().resolve()
    if not apk.is_file():
        fail(f"release APK does not exist: {apk}")
    if not aab.is_file():
        fail(f"release AAB does not exist: {aab}")
    if "unsigned" in apk.name.lower():
        fail("unsigned APK names are never eligible for final release identity")

    apk_sha256 = sha256_file(apk)
    aab_sha256 = sha256_file(aab)
    if apk_sha256 != str(host_apk.get("sha256") or "").lower():
        fail("signed APK does not match the APK produced by the current host validation session")
    if aab_sha256 != str(host_aab.get("sha256") or "").lower():
        fail("Release AAB does not match the AAB produced by the current host validation session")

    apkanalyzer = find_android_tool("apkanalyzer")
    apksigner = find_android_tool("apksigner")
    jarsigner = find_host_tool("jarsigner")
    parts = run(apkanalyzer, "apk", "summary", apk).split(maxsplit=2)
    if len(parts) != 3:
        fail(f"unexpected apkanalyzer summary output: {parts!r}")
    app_id, version_code, version_name = parts
    if app_id != EXPECTED_APPLICATION_ID:
        fail(f"application id mismatch: {app_id}")
    if version_code != EXPECTED_VERSION_CODE:
        fail(f"version code mismatch: {version_code}")
    if version_name != EXPECTED_VERSION_NAME:
        fail(f"version name mismatch: {version_name}")
    debuggable = run(apkanalyzer, "manifest", "debuggable", apk).lower()
    if debuggable != "false":
        fail(f"release APK must not be debuggable; got {debuggable!r}")
    signer = run(apksigner, "verify", "--verbose", "--print-certs", "-Werr", apk)
    certs = sorted(
        set(
            match.replace(":", "").lower()
            for match in re.findall(
                r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)",
                signer,
            )
        ),
    )
    if not certs or any(not re.fullmatch(r"[0-9a-f]{64}", cert) for cert in certs):
        fail("apksigner did not report a valid signer certificate SHA-256 digest")

    aab_signature = run(jarsigner, "-verify", "-certs", aab)
    signature_text = aab_signature.lower()
    if (
        "jar verified." not in signature_text
        or "jar is unsigned" in signature_text
        or "unsigned entries" in signature_text
    ):
        fail("jarsigner did not confirm complete Release AAB signature integrity")
    report = {
        "schemaVersion": 2,
        "verified": True,
        "validationSession": host_session,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "artifact": {
            "path": str(apk),
            "fileName": apk.name,
            "byteSize": apk.stat().st_size,
            "sha256": apk_sha256,
        },
        "releaseAab": {
            "path": str(aab),
            "fileName": aab.name,
            "byteSize": aab.stat().st_size,
            "sha256": aab_sha256,
            "signatureVerified": True,
        },
        "identity": {
            "applicationId": app_id,
            "versionCode": int(version_code),
            "versionName": version_name,
            "debuggable": False,
            "signerCertificateSha256": certs,
        },
        "tools": {
            "apkanalyzer": str(apkanalyzer),
            "apksigner": str(apksigner),
            "jarsigner": str(jarsigner),
        },
    }
    destination = Path(args.report).expanduser().resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"RELEASE_ARTIFACT_IDENTITY: PASSED — {apk.name} sha256={apk_sha256}")
    print(f"RELEASE_AAB_SIGNATURE: PASSED — {aab.name} sha256={aab_sha256}")
    print(f"Evidence: {destination}")


if __name__ == "__main__":
    main()
