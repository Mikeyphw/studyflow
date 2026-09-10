#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
DEFAULT_EXPORT_DIR = DEFAULT_REPORT_DIR / "release-evidence-export"

REQUIRED_REPORTS = {
    "plan": "target-release-validation-plan.json",
    "summary": "target-release-validation-summary.json",
    "host": "release-host-gates.json",
    "artifact": "release-artifact-identity.json",
    "connected": "connected-test-gates.json",
    "journey": "full-journey.json",
    "device": "device-acceptance.json",
    "verdict": "final-release-verdict.json",
}

SAFE_TEXT_EXTENSIONS = {".json", ".md", ".txt", ".log", ".html", ".xml", ".csv", ".sh"}
REDACTION_KEYS = {
    "password",
    "passwd",
    "token",
    "secret",
    "credential",
    "key_password",
    "store_password",
    "signing_password",
}

STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
STATUS_MISSING = "MISSING"
STATUS_FAIL = "FAIL"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def redact_value(key: str, value: Any) -> Any:
    lowered = key.lower()
    if any(token in lowered for token in REDACTION_KEYS):
        return "<redacted>"
    if isinstance(value, dict):
        return {str(child_key): redact_value(str(child_key), child_value) for child_key, child_value in value.items()}
    if isinstance(value, list):
        return [redact_value(key, child) for child in value]
    return value


def normalize_status(name: str, value: dict[str, Any] | None) -> str:
    if value is None:
        return STATUS_MISSING
    if name == "artifact":
        return STATUS_PASS if value.get("verified") is True else STATUS_FAIL
    if name == "verdict":
        return STATUS_PASS if value.get("releaseReady") is True else str(value.get("status") or STATUS_BLOCKED)
    status = str(value.get("status") or "UNKNOWN").upper()
    if status == STATUS_PASS:
        return STATUS_PASS
    if status in {STATUS_BLOCKED, STATUS_PENDING, STATUS_FAIL, STATUS_MISSING}:
        return status
    if value.get("passed") is True or value.get("ok") is True:
        return STATUS_PASS
    return status


def collect_reports(report_dir: Path) -> dict[str, Any]:
    reports: dict[str, Any] = {}
    missing: list[str] = []
    failed: list[str] = []
    pending: list[str] = []
    blocked: list[str] = []

    for name, filename in REQUIRED_REPORTS.items():
        path = report_dir / filename
        value = load_json(path)
        status = normalize_status(name, value)
        entry: dict[str, Any] = {
            "name": name,
            "file": filename,
            "path": str(path),
            "status": status,
            "present": value is not None,
        }
        if value is not None:
            entry["sha256"] = sha256_file(path)
            entry["sizeBytes"] = path.stat().st_size
            entry["redacted"] = redact_value(name, value)
        reports[name] = entry
        if status == STATUS_MISSING:
            missing.append(name)
        elif status == STATUS_PENDING:
            pending.append(name)
        elif status == STATUS_BLOCKED:
            blocked.append(name)
        elif status != STATUS_PASS:
            failed.append(name)

    return {
        "reports": reports,
        "missingEvidence": missing,
        "failedEvidence": failed,
        "pendingEvidence": pending,
        "blockedEvidence": blocked,
    }


def derive_verdict(collected: dict[str, Any]) -> dict[str, Any]:
    reports = collected["reports"]
    verdict_entry = reports.get("verdict", {})
    verdict_json = verdict_entry.get("redacted") if isinstance(verdict_entry.get("redacted"), dict) else {}
    explicit_ready = bool(verdict_json.get("releaseReady") is True)
    every_required_passed = all(entry["status"] == STATUS_PASS for entry in reports.values())
    release_ready = explicit_ready and every_required_passed
    if release_ready:
        status = STATUS_PASS
    elif collected["missingEvidence"] or collected["pendingEvidence"]:
        status = STATUS_PENDING
    else:
        status = STATUS_BLOCKED
    return {
        "status": status,
        "releaseReady": release_ready,
        "explicitVerdictReady": explicit_ready,
        "everyRequiredReportPassed": every_required_passed,
        "reason": (
            "final verdict and all required reports passed"
            if release_ready else
            "missing, pending, failed or blocked evidence remains"
        ),
    }


def classify_triage(collected: dict[str, Any]) -> dict[str, Any]:
    reports = collected["reports"]
    findings: list[dict[str, Any]] = []
    priority = 1
    for name in collected["missingEvidence"]:
        findings.append({
            "priority": priority,
            "category": "missing_evidence",
            "evidence": name,
            "status": STATUS_MISSING,
            "action": "Run the producing gate or copy its JSON report into the release evidence directory.",
        })
        priority += 1
    for name in collected["pendingEvidence"]:
        findings.append({
            "priority": priority,
            "category": "pending_target_execution",
            "evidence": name,
            "status": reports[name]["status"],
            "action": "Complete the target-device or target-machine step, then re-export REL02.",
        })
        priority += 1
    for name in collected["failedEvidence"]:
        findings.append({
            "priority": priority,
            "category": "failed_gate",
            "evidence": name,
            "status": reports[name]["status"],
            "action": "Open the referenced report, fix the failed gate, and re-run REL01 before exporting REL02 again.",
        })
        priority += 1
    for name in collected["blockedEvidence"]:
        findings.append({
            "priority": priority,
            "category": "blocked_verdict",
            "evidence": name,
            "status": reports[name]["status"],
            "action": "Do not ship. Resolve all blockers and regenerate the final release verdict.",
        })
        priority += 1
    return {
        "schemaVersion": 1,
        "kind": "studyflow.release_failure_triage",
        "generatedAtUtc": utc_now(),
        "status": STATUS_PASS if not findings else STATUS_BLOCKED,
        "findings": findings,
        "nextCommands": [
            "python3 scripts/run_target_release_validation.py --mode plan",
            "python3 scripts/run_target_release_validation.py --mode automated",
            "python3 scripts/run_target_release_validation.py --mode device-template",
            "python3 scripts/run_target_release_validation.py --mode verdict",
            "python3 scripts/export_release_evidence_report.py --mode all",
        ],
    }


def report_markdown(collected: dict[str, Any], verdict: dict[str, Any], triage: dict[str, Any]) -> str:
    lines = [
        "# StudyFlow release evidence report",
        "",
        f"Generated: `{utc_now()}`",
        "",
        "## Verdict",
        "",
        f"- Status: `{verdict['status']}`",
        f"- Release ready: `{str(verdict['releaseReady']).lower()}`",
        f"- Reason: {verdict['reason']}",
        "",
        "## Required evidence",
        "",
        "| Evidence | Status | File | SHA-256 |",
        "| --- | --- | --- | --- |",
    ]
    for name, entry in collected["reports"].items():
        lines.append(
            f"| `{name}` | `{entry['status']}` | `{entry['file']}` | `{entry.get('sha256', 'n/a')}` |"
        )
    lines.extend(["", "## Triage", ""])
    findings = triage.get("findings") or []
    if not findings:
        lines.append("No blockers were found in the exported evidence set.")
    else:
        for finding in findings:
            lines.extend([
                f"### {finding['priority']}. {finding['category']} / {finding['evidence']}",
                "",
                f"- Status: `{finding['status']}`",
                f"- Action: {finding['action']}",
                "",
            ])
    lines.extend([
        "## Safety notes",
        "",
        "- REL02 never turns missing or blocked evidence into a passing verdict.",
        "- Sensitive credential-looking keys are redacted in the exported JSON summary.",
        "- The bundle manifest stores hashes and relative paths, not plaintext signing secrets.",
    ])
    return "\n".join(lines).rstrip() + "\n"


def collect_bundle_files(report_dir: Path, export_dir: Path) -> list[Path]:
    candidates: list[Path] = []
    for path in report_dir.rglob("*"):
        if not path.is_file():
            continue
        if export_dir in path.parents:
            continue
        if path.suffix.lower() in SAFE_TEXT_EXTENSIONS:
            candidates.append(path)
    return sorted(candidates)


def write_bundle(report_dir: Path, export_dir: Path, collected: dict[str, Any]) -> Path:
    bundle_dir = export_dir / "bundle"
    if bundle_dir.exists():
        shutil.rmtree(bundle_dir)
    bundle_dir.mkdir(parents=True, exist_ok=True)

    included: list[dict[str, Any]] = []
    for source in collect_bundle_files(report_dir, export_dir):
        relative = source.relative_to(report_dir)
        target = bundle_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix.lower() == ".json":
            value = load_json(source)
            if value is not None:
                write_json(target, redact_value(source.name, value))
            else:
                shutil.copy2(source, target)
        else:
            shutil.copy2(source, target)
        included.append({
            "path": str(relative),
            "sha256": sha256_file(source),
            "sizeBytes": source.stat().st_size,
        })

    manifest = {
        "schemaVersion": 1,
        "kind": "studyflow.release_evidence_bundle_manifest",
        "generatedAtUtc": utc_now(),
        "reportDir": str(report_dir),
        "bundleDir": str(bundle_dir),
        "includedFiles": included,
        "requiredReports": collected["reports"],
    }
    write_json(export_dir / "release-evidence-bundle-manifest.json", manifest)
    zip_path = export_dir / "studyflow-release-evidence-bundle.zip"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(bundle_dir.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(bundle_dir).as_posix())
        archive.write(export_dir / "release-evidence-summary.json", "release-evidence-summary.json")
        archive.write(export_dir / "release-failure-triage.json", "release-failure-triage.json")
        archive.write(export_dir / "release-evidence-report.md", "release-evidence-report.md")
        archive.write(export_dir / "release-evidence-bundle-manifest.json", "release-evidence-bundle-manifest.json")
    return zip_path


def export_all(args: argparse.Namespace) -> int:
    report_dir = Path(args.report_dir).expanduser().resolve()
    export_dir = Path(args.export_dir).expanduser().resolve() if args.export_dir else report_dir / "release-evidence-export"
    export_dir.mkdir(parents=True, exist_ok=True)

    collected = collect_reports(report_dir)
    verdict = derive_verdict(collected)
    triage = classify_triage(collected)
    summary = {
        "schemaVersion": 1,
        "kind": "studyflow.release_evidence_summary",
        "generatedAtUtc": utc_now(),
        "reportDir": str(report_dir),
        "exportDir": str(export_dir),
        "status": verdict["status"],
        "releaseReady": verdict["releaseReady"],
        "verdict": verdict,
        **collected,
    }

    write_json(export_dir / "release-evidence-summary.json", summary)
    write_json(export_dir / "release-failure-triage.json", triage)
    (export_dir / "release-evidence-report.md").write_text(
        report_markdown(collected, verdict, triage),
        encoding="utf-8",
    )

    bundle_path: Path | None = None
    if args.mode in {"all", "bundle"}:
        bundle_path = write_bundle(report_dir, export_dir, collected)

    print(f"REL02_RELEASE_EVIDENCE_EXPORT: {verdict['status']}")
    print(f"Summary: {export_dir / 'release-evidence-summary.json'}")
    print(f"Report: {export_dir / 'release-evidence-report.md'}")
    print(f"Triage: {export_dir / 'release-failure-triage.json'}")
    if bundle_path:
        print(f"Bundle: {bundle_path}")
    return 0 if verdict["releaseReady"] else 1 if args.fail_when_blocked else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Export StudyFlow release evidence and a failure triage bundle.")
    parser.add_argument("--mode", choices=["summary", "triage", "bundle", "all"], default="all")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--export-dir", default="")
    parser.add_argument(
        "--fail-when-blocked",
        action="store_true",
        help="Return exit 1 when evidence is blocked or pending. Default is report-only success.",
    )
    args = parser.parse_args()
    return export_all(args)


if __name__ == "__main__":
    raise SystemExit(main())
