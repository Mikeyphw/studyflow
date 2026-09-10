#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
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
    "password",
    "passwd",
    "secret",
    "token",
    "credential",
    "keystore",
    "storepass",
    "keypass",
    "private_key",
    "access_key",
}


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
        if any(word in lowered_value for word in ("storepass=", "keypass=", "password=", "token=")):
            return "<redacted>"
    return value


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"status": STATUS_BLOCKED, "reason": f"invalid JSON: {path}"}


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(redact(value), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def evidence_status(value: dict[str, Any] | None, missing_status: str = STATUS_PENDING) -> str:
    if value is None:
        return missing_status
    return str(value.get("status") or STATUS_PENDING)


def derive_status(statuses: list[str]) -> str:
    if any(status == STATUS_BLOCKED for status in statuses):
        return STATUS_BLOCKED
    if any(status == STATUS_PENDING for status in statuses):
        return STATUS_PENDING
    if statuses and all(status == STATUS_PASS for status in statuses):
        return STATUS_PASS
    return STATUS_PENDING


def build_command_template(package_name: str | None, previous_apk: str | None) -> str:
    package = package_name or "com.mikeyphw.studyflow"
    apk = previous_apk or "/sdcard/Download/studyflow-previous.apk"
    return f"""#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME={package!r}
PREVIOUS_APK={apk!r}

adb devices
adb shell am force-stop "$PACKAGE_NAME" || true
adb install -r -d "$PREVIOUS_APK"
adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
adb shell pidof "$PACKAGE_NAME" >/dev/null
printf 'Rollback smoke launched %s from %s\\n' "$PACKAGE_NAME" "$PREVIOUS_APK"
"""


def write_markdown(report_dir: Path, manifest: dict[str, Any]) -> None:
    lines = [
        "# StudyFlow rollback package manifest",
        "",
        f"Status: `{manifest['status']}`",
        f"Rollback ready: `{manifest['rollbackReady']}`",
        "",
        "## Inputs",
    ]
    for key, value in manifest.get("inputs", {}).items():
        lines.append(f"- {key}: `{value.get('status')}`")
    lines.extend([
        "",
        "## Required operator evidence",
    ])
    for item in manifest.get("operatorChecklist", []):
        lines.append(f"- {item}")
    lines.extend([
        "",
        "## Command template",
        "",
        "```bash",
        (report_dir / "rollback-command-template.sh").read_text(encoding="utf-8").rstrip(),
        "```",
        "",
        "This manifest never performs rollback automatically; it records the reversible plan and evidence requirements.",
    ])
    (report_dir / "rollback-package-manifest.md").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def bundle_outputs(report_dir: Path, output_zip: Path) -> None:
    names = [
        "release-rollback-manifest.json",
        "rollback-package-manifest.md",
        "rollback-command-template.sh",
        "release-notes.json",
        "release-notes.md",
        "release-notes-play-store-draft.txt",
    ]
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            path = report_dir / name
            if path.is_file():
                archive.write(path, name)


def build_manifest(report_dir: Path, package_name: str | None, previous_version_name: str | None, previous_version_code: str | None, previous_apk: str | None) -> dict[str, Any]:
    rel01 = load_json(report_dir / "target-release-validation-summary.json")
    rel02 = load_json(report_dir / "release-evidence-export" / "release-evidence-summary.json")
    rel03 = load_json(report_dir / "release-artifact-signature-identity.json")
    rel04 = load_json(report_dir / "rollout-readiness.json")
    rel05 = load_json(report_dir / "release-notes.json")
    input_statuses = [
        evidence_status(rel01),
        evidence_status(rel02),
        evidence_status(rel03),
        evidence_status(rel04),
        evidence_status(rel05),
    ]
    status = derive_status(input_statuses)
    command_template = build_command_template(package_name, previous_apk)
    (report_dir / "rollback-command-template.sh").write_text(command_template, encoding="utf-8")
    os.chmod(report_dir / "rollback-command-template.sh", 0o755)
    manifest = {
        "schema": "studyflow.rel06.rollback_manifest.v1",
        "marker": "REL06_ROLLBACK_PACKAGE_MANIFEST",
        "status": status,
        "rollbackReady": False,
        "generatedAt": utc_now(),
        "packageName": package_name or "com.mikeyphw.studyflow",
        "previousVersionName": previous_version_name,
        "previousVersionCode": previous_version_code,
        "previousApk": previous_apk,
        "inputs": {
            "rel01TargetValidation": rel01 or {"status": STATUS_PENDING, "reason": "target-release-validation-summary.json missing"},
            "rel02EvidenceSummary": rel02 or {"status": STATUS_PENDING, "reason": "release-evidence-summary.json missing"},
            "rel03ArtifactIdentity": rel03 or {"status": STATUS_PENDING, "reason": "release-artifact-signature-identity.json missing"},
            "rel04RolloutReadiness": rel04 or {"status": STATUS_PENDING, "reason": "rollout-readiness.json missing"},
            "rel05ReleaseNotes": rel05 or {"status": STATUS_PENDING, "reason": "release-notes.json missing"},
        },
        "operatorChecklist": [
            "Confirm the previous APK path and version metadata before rollback.",
            "Capture adb install output and launch smoke evidence after rollback.",
            "Record user-data migration risk before downgrading across schema versions.",
            "Keep rollout halted until rollback smoke evidence is attached to the release evidence bundle.",
        ],
        "outputs": {
            "json": str(report_dir / "release-rollback-manifest.json"),
            "markdown": str(report_dir / "rollback-package-manifest.md"),
            "commandTemplate": str(report_dir / "rollback-command-template.sh"),
            "bundle": str(report_dir / "studyflow-rollback-package-bundle.zip"),
        },
        "notes": [
            "rollbackReady remains false until a target operator executes and records the rollback smoke path.",
            "This tool emits a plan and evidence manifest only; it does not install, downgrade, publish, or delete anything.",
        ],
    }
    return redact(manifest)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build StudyFlow rollback package manifest from release evidence.")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--package-name", default="com.mikeyphw.studyflow")
    parser.add_argument("--previous-version-name")
    parser.add_argument("--previous-version-code")
    parser.add_argument("--previous-apk")
    args = parser.parse_args(argv)
    report_dir = Path(args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    manifest = build_manifest(
        report_dir=report_dir,
        package_name=args.package_name,
        previous_version_name=args.previous_version_name,
        previous_version_code=args.previous_version_code,
        previous_apk=args.previous_apk,
    )
    write_json(report_dir / "release-rollback-manifest.json", manifest)
    write_markdown(report_dir, manifest)
    bundle_outputs(report_dir, report_dir / "studyflow-rollback-package-bundle.zip")
    print(f"REL06_ROLLBACK_PACKAGE_MANIFEST: {manifest['status']}")
    return 0 if manifest["status"] in {STATUS_PASS, STATUS_PENDING} else 1


if __name__ == "__main__":
    raise SystemExit(main())
