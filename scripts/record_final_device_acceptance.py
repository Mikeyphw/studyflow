#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build/reports/studyflow"
APPLICATION_ID = "com.mikeyphw.studyflow"
REQUIRED = {
    "fresh_install",
    "upgrade_install",
    "upgrade_data_survival",
    "process_restart",
    "notifications",
    "today_widget",
    "restore_create_as_new",
    "restore_replace_existing",
    "locale_en_ptbr",
    "theme_light_dark_system",
    "accessibility_talkback_focus_touch_target",
}


def fail(message: str) -> None:
    print(f"DEVICE_ACCEPTANCE: BLOCKED — {message}", file=sys.stderr)
    raise SystemExit(1)


def adb_text(*args: str) -> str:
    completed = subprocess.run(["adb", *args], text=True, capture_output=True)
    if completed.returncode:
        fail((completed.stderr or completed.stdout).strip() or f"adb {' '.join(args)} failed")
    return completed.stdout.strip()


def installed_apk_sha256(package_name: str) -> str:
    package_paths = [
        line.removeprefix("package:").strip()
        for line in adb_text("shell", "pm", "path", package_name).splitlines()
        if line.startswith("package:")
    ]
    base_path = next((path for path in package_paths if path.endswith("/base.apk")), None)
    if base_path is None and len(package_paths) == 1:
        base_path = package_paths[0]
    if not base_path:
        fail(f"unable to resolve installed base APK for {package_name}")
    process = subprocess.Popen(["adb", "exec-out", "cat", base_path], stdout=subprocess.PIPE)
    assert process.stdout is not None
    digest = hashlib.sha256()
    for chunk in iter(lambda: process.stdout.read(1024 * 1024), b""):
        digest.update(chunk)
    return_code = process.wait()
    if return_code:
        fail(f"unable to read installed base APK; adb exited {return_code}")
    return digest.hexdigest()


def load(path: Path) -> dict:
    if not path.is_file():
        fail(f"prerequisite evidence missing: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"prerequisite evidence is not an object: {path}")
    return value


def parse_evidence(values: list[str]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for raw in values:
        check, separator, note = raw.partition("=")
        check = check.strip()
        note = note.strip()
        if not separator or check not in REQUIRED:
            fail(f"--evidence must use REQUIRED_CHECK=observation; got {raw!r}")
        if check in parsed:
            fail(f"duplicate --evidence entry for {check}")
        if not note:
            fail(f"empty observation evidence for {check}")
        parsed[check] = note
    return parsed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pass-check", action="append", default=[], choices=sorted(REQUIRED))
    parser.add_argument(
        "--evidence",
        action="append",
        default=[],
        metavar="CHECK=OBSERVATION",
        help="Required human/device observation text for each passed check.",
    )
    parser.add_argument("--artifact", default=str(REPORT_DIR / "release-artifact-identity.json"))
    parser.add_argument("--journey", default=str(REPORT_DIR / "full-journey.json"))
    parser.add_argument("--report", default=str(REPORT_DIR / "device-acceptance.json"))
    args = parser.parse_args()

    artifact = load(Path(args.artifact).resolve())
    journey = load(Path(args.journey).resolve())
    if artifact.get("verified") is not True or journey.get("status") != "PASS":
        fail("prerequisite evidence is not PASS")
    artifact_session = str(artifact.get("validationSession") or "").strip()
    journey_session = str(journey.get("validationSession") or "").strip()
    if not artifact_session or artifact_session != journey_session:
        fail("artifact and full-journey evidence are not from the same validation session")

    devices = [
        line.split("\t", 1)[0]
        for line in adb_text("devices").splitlines()[1:]
        if line.endswith("\tdevice")
    ]
    if len(devices) != 1:
        fail(f"expected one adb device, found {len(devices)}")
    if journey.get("deviceSerial") != devices[0]:
        fail("device acceptance must run on the same device as the full journey")

    expected_hash = str(artifact.get("artifact", {}).get("sha256") or "").lower()
    installed_hash = installed_apk_sha256(APPLICATION_ID)
    if installed_hash != expected_hash:
        fail("device is not running the exact signed APK bound to this validation session")

    confirmed = set(args.pass_check)
    if len(confirmed) != len(args.pass_check):
        fail("duplicate --pass-check values are not allowed")
    evidence = parse_evidence(args.evidence)
    unexpected_evidence = set(evidence) - confirmed
    if unexpected_evidence:
        fail(f"evidence supplied for checks not passed: {', '.join(sorted(unexpected_evidence))}")
    missing = sorted(REQUIRED - confirmed)
    missing_evidence = sorted(check for check in confirmed if not evidence.get(check, "").strip())
    checks = [
        {
            "id": check,
            "status": "PASS" if check in confirmed and check in evidence else "BLOCKED",
            "evidence": evidence.get(check, ""),
        }
        for check in sorted(REQUIRED)
    ]
    report = {
        "schemaVersion": 2,
        "status": "PASS" if not missing and not missing_evidence else "BLOCKED",
        "validationSession": artifact_session,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "artifactSha256": expected_hash,
        "installedArtifactSha256": installed_hash,
        "applicationId": artifact.get("identity", {}).get("applicationId"),
        "versionCode": artifact.get("identity", {}).get("versionCode"),
        "versionName": artifact.get("identity", {}).get("versionName"),
        "device": {
            "serial": devices[0],
            "manufacturer": adb_text("shell", "getprop", "ro.product.manufacturer"),
            "model": adb_text("shell", "getprop", "ro.product.model"),
            "apiLevel": adb_text("shell", "getprop", "ro.build.version.sdk"),
            "fingerprint": adb_text("shell", "getprop", "ro.build.fingerprint"),
        },
        "journeyEvidence": str(Path(args.journey).resolve()),
        "checks": checks,
    }
    out = Path(args.report).resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if missing or missing_evidence:
        print("DEVICE_ACCEPTANCE: BLOCKED — explicit observations are incomplete:", file=sys.stderr)
        for check in missing:
            print(f"- missing PASS confirmation: {check}", file=sys.stderr)
        for check in missing_evidence:
            print(f"- missing observation evidence: {check}", file=sys.stderr)
        raise SystemExit(1)
    print("DEVICE_ACCEPTANCE: PASS")
    print(f"Evidence: {out}")


if __name__ == "__main__":
    main()
