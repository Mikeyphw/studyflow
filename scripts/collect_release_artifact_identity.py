#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
REPORT_NAME = "release-artifact-signature-identity.json"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
STATUS_MISSING = "MISSING"
STATUS_FAIL = "FAIL"
SECRET_WORDS = {
    "password",
    "passwd",
    "secret",
    "token",
    "credential",
    "keystore",
    "storepass",
    "keypass",
    "private_key",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def redact(value: Any, key_hint: str = "") -> Any:
    lowered = key_hint.lower()
    if any(word in lowered for word in SECRET_WORDS):
        return "<redacted>"
    if isinstance(value, dict):
        return {str(k): redact(v, str(k)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, key_hint) for v in value]
    if isinstance(value, str) and any(word in value.lower() for word in ["storepass=", "keypass=", "password="]):
        return "<redacted>"
    return value


def zip_entries(path: Path, limit: int = 40) -> list[str]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
    except zipfile.BadZipFile:
        return []
    safe = [name for name in names if not name.endswith("/")]
    return safe[:limit]


def classify_artifact(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".apk":
        return "apk"
    if suffix == ".aab":
        return "aab"
    return "unknown"


def structure_checks(path: Path, artifact_kind: str) -> dict[str, Any]:
    entries = zip_entries(path, limit=10_000)
    entry_set = set(entries)
    if artifact_kind == "apk":
        required = ["AndroidManifest.xml", "classes.dex"]
    elif artifact_kind == "aab":
        required = ["BundleConfig.pb", "base/manifest/AndroidManifest.xml"]
    else:
        required = []
    missing = [item for item in required if item not in entry_set]
    return {
        "status": STATUS_PASS if entries and not missing else STATUS_BLOCKED,
        "requiredEntries": required,
        "missingEntries": missing,
        "sampleEntries": entries[:40],
        "zipReadable": bool(entries),
    }


def run_tool(command: list[str]) -> dict[str, Any]:
    executable = shutil.which(command[0])
    if executable is None:
        return {
            "status": STATUS_PENDING,
            "available": False,
            "tool": command[0],
            "reason": f"{command[0]} is not available on this host",
        }
    completed = subprocess.run(
        [executable, *command[1:]],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    output = (completed.stdout + "\n" + completed.stderr).strip()
    return {
        "status": STATUS_PASS if completed.returncode == 0 else STATUS_BLOCKED,
        "available": True,
        "tool": command[0],
        "returnCode": completed.returncode,
        "output": redact(output, "tool_output"),
    }


def inspect_apk(apk: Path, package_name: str | None) -> dict[str, Any]:
    apksigner = run_tool(["apksigner", "verify", "--verbose", "--print-certs", str(apk)])
    apkanalyzer_args = ["apkanalyzer", "manifest", "application-id", str(apk)]
    application_id = run_tool(apkanalyzer_args)
    identity_status = STATUS_PENDING
    package_matches = None
    parsed_package = None
    if application_id.get("status") == STATUS_PASS:
        parsed_package = str(application_id.get("output") or "").splitlines()[0].strip()
        package_matches = package_name is None or parsed_package == package_name
    if apksigner.get("status") == STATUS_PASS and (package_matches is not False):
        identity_status = STATUS_PASS
    elif apksigner.get("status") == STATUS_PENDING or application_id.get("status") == STATUS_PENDING:
        identity_status = STATUS_PENDING
    else:
        identity_status = STATUS_BLOCKED
    return {
        "status": identity_status,
        "signingVerification": apksigner,
        "applicationIdCheck": application_id,
        "expectedPackageName": package_name,
        "parsedPackageName": parsed_package,
        "packageNameMatches": package_matches,
        "notes": [
            "APK identity is PASS only when apksigner succeeds and the optional package-name check matches.",
            "Missing Android SDK host tools keep this gate PENDING_TARGET_EXECUTION instead of synthetic PASS.",
        ],
    }


def inspect_aab(aab: Path) -> dict[str, Any]:
    jarsigner = run_tool(["jarsigner", "-verify", str(aab)])
    return {
        "status": jarsigner["status"],
        "signatureVerification": jarsigner,
        "notes": [
            "AAB signature evidence uses jarsigner availability on the target host.",
            "CA-chain strictness is intentionally not inferred from this source-only exporter.",
        ],
    }


def artifact_record(path: Path, role: str, expected_package: str | None) -> dict[str, Any]:
    if not path.is_file():
        return {
            "role": role,
            "path": str(path),
            "kind": classify_artifact(path),
            "status": STATUS_MISSING,
            "present": False,
            "reason": "artifact file is missing",
        }
    kind = classify_artifact(path)
    base: dict[str, Any] = {
        "role": role,
        "path": str(path),
        "kind": kind,
        "status": STATUS_PENDING,
        "present": True,
        "sizeBytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "structure": structure_checks(path, kind),
    }
    if kind == "apk":
        base["identity"] = inspect_apk(path, expected_package)
    elif kind == "aab":
        base["identity"] = inspect_aab(path)
    else:
        base["identity"] = {"status": STATUS_BLOCKED, "reason": "unknown artifact extension"}
    statuses = [base["structure"]["status"], base["identity"]["status"]]
    if all(status == STATUS_PASS for status in statuses):
        base["status"] = STATUS_PASS
    elif any(status == STATUS_BLOCKED for status in statuses):
        base["status"] = STATUS_BLOCKED
    elif any(status == STATUS_MISSING for status in statuses):
        base["status"] = STATUS_MISSING
    else:
        base["status"] = STATUS_PENDING
    return base


def derive_report(records: list[dict[str, Any]]) -> dict[str, Any]:
    statuses = [record["status"] for record in records]
    if not records or any(status == STATUS_MISSING for status in statuses):
        status = STATUS_PENDING
    elif any(status == STATUS_BLOCKED for status in statuses):
        status = STATUS_BLOCKED
    elif all(status == STATUS_PASS for status in statuses):
        status = STATUS_PASS
    else:
        status = STATUS_PENDING
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel03_artifact_signature_identity",
        "generatedAtUtc": utc_now(),
        "status": status,
        "releaseReady": False,
        "verified": status == STATUS_PASS,
        "artifacts": records,
        "requiredFollowUp": [] if status == STATUS_PASS else [
            "Run on the target build host with apksigner, apkanalyzer, jarsigner and real signed artifacts.",
            "Keep this gate pending or blocked until the exact APK/AAB bytes verify.",
        ],
    }


def default_path(name: str) -> Path:
    return ROOT / name


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="REL03 signed artifact identity collector")
    parser.add_argument("--apk", default="app/build/outputs/apk/release/app-release.apk")
    parser.add_argument("--aab", default="app/build/outputs/bundle/release/app-release.aab")
    parser.add_argument("--package-name", default="com.mikeyphw.studyflow")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--fail-when-blocked", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report_dir = Path(args.report_dir).expanduser().resolve()
    records = [
        artifact_record(default_path(args.apk) if not os.path.isabs(args.apk) else Path(args.apk), "releaseApk", args.package_name),
        artifact_record(default_path(args.aab) if not os.path.isabs(args.aab) else Path(args.aab), "releaseAab", None),
    ]
    report = derive_report(records)
    out = report_dir / REPORT_NAME
    write_json(out, redact(report))
    print(f"REL03_ARTIFACT_SIGNATURE_IDENTITY: {report['status']}")
    print(f"Artifact identity report: {out}")
    if args.fail_when_blocked and report["status"] != STATUS_PASS:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
