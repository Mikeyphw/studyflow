#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
REQUIRED_REPORTS = {
    "rel01Plan": "target-release-validation-plan.json",
    "rel01Summary": "target-release-validation-summary.json",
    "rel02Summary": "release-evidence-export/release-evidence-summary.json",
    "rel02Triage": "release-evidence-export/release-failure-triage.json",
    "rel03ArtifactIdentity": "release-artifact-signature-identity.json",
    "rel03InstallSmoke": "install-smoke-evidence.json",
}
SECRET_WORDS = {"password", "secret", "token", "credential", "keystore", "keypass", "storepass"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def redact(value: Any, key_hint: str = "") -> Any:
    if any(word in key_hint.lower() for word in SECRET_WORDS):
        return "<redacted>"
    if isinstance(value, dict):
        return {str(k): redact(v, str(k)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, key_hint) for v in value]
    return value


def normalize_status(name: str, value: dict[str, Any] | None) -> str:
    if value is None:
        return STATUS_PENDING
    status = str(value.get("status") or "UNKNOWN").upper()
    if name == "rel02Summary" and value.get("releaseReady") is True:
        return STATUS_PASS
    if name == "rel03ArtifactIdentity" and value.get("verified") is True:
        return STATUS_PASS
    if status == STATUS_PASS:
        return STATUS_PASS
    if status in {STATUS_BLOCKED, STATUS_PENDING, "MISSING", "FAIL"}:
        return STATUS_PENDING if status == "MISSING" else status
    return STATUS_BLOCKED


def collect(report_dir: Path) -> dict[str, Any]:
    entries: dict[str, Any] = {}
    for name, filename in REQUIRED_REPORTS.items():
        path = report_dir / filename
        value = read_json(path)
        status = normalize_status(name, value)
        entries[name] = {
            "name": name,
            "file": filename,
            "path": str(path),
            "present": value is not None,
            "status": status,
            "sha256": sha256_file(path) if value is not None else None,
            "redacted": redact(value) if value is not None else None,
        }
    return entries


def channel_policy(channel: str) -> dict[str, Any]:
    if channel == "internal":
        required = ["rel02Summary", "rel03ArtifactIdentity", "rel03InstallSmoke"]
    elif channel == "beta":
        required = ["rel01Summary", "rel02Summary", "rel02Triage", "rel03ArtifactIdentity", "rel03InstallSmoke"]
    else:
        required = list(REQUIRED_REPORTS.keys())
    return {
        "channel": channel,
        "requiredReports": required,
        "notes": [
            "Channel manifest is evidence-derived only; it does not sign or upload artifacts.",
            "Production requires every REL01-REL03 report and REL02 releaseReady=true.",
        ],
    }


def readiness(channel: str, entries: dict[str, Any]) -> dict[str, Any]:
    policy = channel_policy(channel)
    missing = [name for name in policy["requiredReports"] if not entries[name]["present"]]
    pending = [name for name in policy["requiredReports"] if entries[name]["status"] == STATUS_PENDING]
    blocked = [name for name in policy["requiredReports"] if entries[name]["status"] == STATUS_BLOCKED]
    failed = [name for name in policy["requiredReports"] if entries[name]["status"] not in {STATUS_PASS, STATUS_PENDING, STATUS_BLOCKED}]
    all_passed = not missing and not pending and not blocked and not failed
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel04_rollout_readiness",
        "generatedAtUtc": utc_now(),
        "channel": channel,
        "status": STATUS_PASS if all_passed else (STATUS_PENDING if missing or pending else STATUS_BLOCKED),
        "releaseReady": bool(all_passed),
        "requiredReports": policy["requiredReports"],
        "missingEvidence": missing,
        "pendingEvidence": pending,
        "blockedEvidence": blocked,
        "failedEvidence": failed,
        "reason": "all required evidence passed" if all_passed else "missing, pending, blocked or failed evidence remains",
    }


def build_manifest(channel: str, entries: dict[str, Any], ready: dict[str, Any]) -> dict[str, Any]:
    artifact_identity = entries.get("rel03ArtifactIdentity", {}).get("redacted") or {}
    artifacts = artifact_identity.get("artifacts", []) if isinstance(artifact_identity, dict) else []
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel04_release_channel_manifest",
        "generatedAtUtc": utc_now(),
        "channel": channel,
        "status": ready["status"],
        "releaseReady": ready["releaseReady"],
        "artifacts": artifacts,
        "evidence": entries,
        "policy": channel_policy(channel),
        "commands": [
            "python3 scripts/run_target_release_validation.py --mode plan",
            "python3 scripts/run_target_release_validation.py --mode automated",
            "python3 scripts/run_target_release_validation.py --mode device-template",
            "python3 scripts/run_target_release_validation.py --mode verdict",
            "python3 scripts/export_release_evidence_report.py --mode all",
            "python3 scripts/collect_release_artifact_identity.py --fail-when-blocked",
            "python3 scripts/collect_install_smoke_evidence.py --mode adb --fail-when-blocked",
            f"python3 scripts/build_release_channel_manifest.py --channel {channel} --fail-when-blocked",
        ],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="REL04 release channel manifest and rollout readiness gate")
    parser.add_argument("--channel", choices=["internal", "beta", "production"], default="internal")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--fail-when-blocked", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report_dir = Path(args.report_dir).expanduser().resolve()
    entries = collect(report_dir)
    ready = readiness(args.channel, entries)
    manifest = build_manifest(args.channel, entries, ready)
    manifest_path = report_dir / "release-channel-manifest.json"
    readiness_path = report_dir / "rollout-readiness.json"
    write_json(manifest_path, manifest)
    write_json(readiness_path, ready)
    print(f"REL04_RELEASE_CHANNEL_MANIFEST: {ready['status']}")
    print(f"Channel manifest: {manifest_path}")
    print(f"Rollout readiness: {readiness_path}")
    if args.fail_when_blocked and ready["status"] != STATUS_PASS:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
