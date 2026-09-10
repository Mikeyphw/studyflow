#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
SECRET_WORDS = {
    "password", "passwd", "secret", "token", "credential", "keystore",
    "storepass", "keypass", "private_key", "access_key", "refresh_token",
}
FINAL_INPUTS = {
    "rel01TargetValidation": "target-release-validation-summary.json",
    "rel02EvidenceSummary": "release-evidence-export/release-evidence-summary.json",
    "rel02FailureTriage": "release-evidence-export/release-failure-triage.json",
    "rel03ArtifactIdentity": "release-artifact-signature-identity.json",
    "rel03InstallSmoke": "install-smoke-evidence.json",
    "rel04ChannelManifest": "release-channel-manifest.json",
    "rel04RolloutReadiness": "rollout-readiness.json",
    "rel05ReleaseNotes": "release-notes.json",
    "rel06RollbackManifest": "release-rollback-manifest.json",
    "rel07ChecklistGate": "release-checklist-gate.json",
}
BUNDLE_FILES = [
    *FINAL_INPUTS.values(),
    "release-checklist-gate.md",
    "release-checklist-command-template.sh",
    "release-notes.md",
    "release-notes-play-store-draft.txt",
    "rollback-package-manifest.md",
    "rollback-command-template.sh",
    "studyflow-rollback-package-bundle.zip",
    "release-ops-final-seal.json",
    "release-ops-final-seal.md",
]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def redact(value: Any, key_hint: str = "") -> Any:
    lowered = key_hint.lower()
    if any(word in lowered for word in SECRET_WORDS):
        return "<redacted>"
    if isinstance(value, dict):
        return {str(k): redact(v, str(k)) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, key_hint) for v in value]
    if isinstance(value, str):
        lowered_value = value.lower()
        if any(word in lowered_value for word in ("storepass=", "keypass=", "password=", "token=", "secret=")):
            return "<redacted>"
    return value


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"status": STATUS_BLOCKED, "reason": f"invalid JSON: {path}"}


def status_of(value: dict[str, Any] | None) -> str:
    if value is None:
        return STATUS_PENDING
    raw = str(value.get("status") or STATUS_PENDING).upper()
    if raw in {"PASS", "PASSED", "OK"}:
        return STATUS_PASS
    if raw in {"FAIL", "FAILED", "ERROR", "BLOCKED"}:
        return STATUS_BLOCKED
    return STATUS_PENDING


def derive_final_status(statuses: list[str]) -> str:
    if any(status == STATUS_BLOCKED for status in statuses):
        return STATUS_BLOCKED
    if any(status == STATUS_PENDING for status in statuses):
        return STATUS_PENDING
    return STATUS_PASS


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(redact(value), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def build_final_seal(report_dir: Path, channel: str) -> dict[str, Any]:
    inputs: dict[str, Any] = {}
    statuses: list[str] = []
    for name, rel in FINAL_INPUTS.items():
        value = load_json(report_dir / rel)
        status = status_of(value)
        statuses.append(status)
        inputs[name] = value or {"status": STATUS_PENDING, "reason": f"missing {rel}"}
    final_status = derive_final_status(statuses)
    checklist = inputs.get("rel07ChecklistGate", {})
    checklist_ok = isinstance(checklist, dict) and checklist.get("readyForFinalReleaseOpsSeal") is True
    if final_status == STATUS_PASS and not checklist_ok:
        final_status = STATUS_PENDING
    seal = {
        "schema": "studyflow.rel08.final_release_ops_seal.v1",
        "marker": "REL08_FINAL_RELEASE_OPS_SEAL",
        "status": final_status,
        "generatedAt": utc_now(),
        "channel": channel,
        "releaseOpsReady": final_status == STATUS_PASS,
        "inputs": inputs,
        "inputStatuses": {name: status_of(value if isinstance(value, dict) else None) for name, value in inputs.items()},
        "blockedBy": [name for name, value in inputs.items() if status_of(value if isinstance(value, dict) else None) != STATUS_PASS],
        "outputs": {
            "json": str(report_dir / "release-ops-final-seal.json"),
            "markdown": str(report_dir / "release-ops-final-seal.md"),
            "bundle": str(report_dir / "studyflow-release-ops-final-bundle.zip"),
        },
        "notes": [
            "REL08 is the release-ops evidence seal only; it does not upload, publish, install, sign, delete, or roll back artifacts.",
            "releaseOpsReady becomes true only when REL01 through REL07 evidence has status PASS and the REL07 checklist is complete.",
        ],
    }
    return redact(seal)


def write_markdown(report_dir: Path, seal: dict[str, Any]) -> None:
    lines = [
        "# StudyFlow REL08 final release-ops seal",
        "",
        f"Status: `{seal['status']}`",
        f"Release-ops ready: `{seal['releaseOpsReady']}`",
        f"Channel: `{seal['channel']}`",
        "",
        "## Inputs",
    ]
    for name, status in seal.get("inputStatuses", {}).items():
        lines.append(f"- {name}: `{status}`")
    if seal.get("blockedBy"):
        lines.extend(["", "## Blocked by"])
        for name in seal["blockedBy"]:
            lines.append(f"- {name}")
    lines.extend([
        "",
        "The final release-ops seal is evidence only. Operator publishing remains outside this tool.",
    ])
    (report_dir / "release-ops-final-seal.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def bundle_outputs(report_dir: Path, output_zip: Path) -> None:
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as archive:
        for rel in BUNDLE_FILES:
            path = report_dir / rel
            if path.is_file():
                archive.write(path, rel)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build the StudyFlow REL08 final release-ops evidence seal.")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--channel", default="internal", choices=["internal", "beta", "production"])
    args = parser.parse_args(argv)
    report_dir = Path(args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    seal = build_final_seal(report_dir, args.channel)
    write_json(report_dir / "release-ops-final-seal.json", seal)
    write_markdown(report_dir, seal)
    bundle_outputs(report_dir, report_dir / "studyflow-release-ops-final-bundle.zip")
    print(f"REL08_FINAL_RELEASE_OPS_SEAL: {seal['status']}")
    return 0 if seal["status"] in {STATUS_PASS, STATUS_PENDING} else 1


if __name__ == "__main__":
    raise SystemExit(main())
