#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def run(command: list[str]) -> int:
    print("$ " + " ".join(command), flush=True)
    return subprocess.run(command, cwd=ROOT).returncode


def load(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def is_final_ready(report_dir: Path) -> tuple[bool, dict[str, Any]]:
    verdict = load(report_dir / "final-release-verdict.json") or {}
    ops = load(report_dir / "release-ops-final-seal.json") or {}
    ready = verdict.get("releaseReady") is True and ops.get("releaseOpsReady") is True
    return ready, {"verdict": verdict, "releaseOps": ops}


def write_status(report_dir: Path, mode: str, commands: list[dict[str, Any]]) -> bool:
    ready, evidence = is_final_ready(report_dir)
    value = {
        "schemaVersion": 1,
        "kind": "studyflow.rel_final.release_campaign_status",
        "status": "PASS" if ready else "PENDING_TARGET_EXECUTION",
        "releaseReady": ready,
        "releaseOpsReady": ready,
        "generatedAtUtc": utc_now(),
        "mode": mode,
        "commands": commands,
        "finalEvidence": evidence,
        "reportDir": str(report_dir),
    }
    out = report_dir / "rel-final-release-campaign-status.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"REL_FINAL_RELEASE_CAMPAIGN: {'PASS' if ready else 'PENDING_TARGET_EXECUTION'}")
    print(f"Status: {out}")
    return ready


def execute_steps(steps: list[list[str]], continue_on_failure: bool = True) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for command in steps:
        code = run(command)
        results.append({"command": command, "exitCode": code})
        if code and not continue_on_failure:
            break
    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="Orchestrate the StudyFlow REL-FINAL evidence campaign.")
    parser.add_argument("--mode", choices=["plan", "execute", "finalize", "status"], default="plan")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--channel", choices=["internal", "beta", "production"], default="production")
    args = parser.parse_args()
    report_dir = Path(args.report_dir).expanduser().resolve()
    report_dir.mkdir(parents=True, exist_ok=True)
    py = sys.executable
    report_arg = str(report_dir)

    if args.mode == "status":
        ready = write_status(report_dir, args.mode, [])
        return 0 if ready else 1

    if args.mode == "plan":
        steps = [
            [py, "scripts/run_target_release_validation.py", "--mode", "plan", "--report-dir", report_arg],
            [py, "scripts/run_target_release_validation.py", "--mode", "device-template", "--report-dir", report_arg],
            [py, "scripts/collect_install_smoke_evidence.py", "--mode", "template", "--report-dir", report_arg],
            [py, "scripts/check_release_operator_checklist.py", "--report-dir", report_arg],
        ]
        results = execute_steps(steps)
        write_status(report_dir, args.mode, results)
        return 0

    if args.mode == "execute":
        steps = [
            [py, "scripts/run_target_release_validation.py", "--mode", "automated", "--report-dir", report_arg],
            [py, "scripts/run_target_release_validation.py", "--mode", "device-template", "--report-dir", report_arg],
            [py, "scripts/collect_release_artifact_identity.py", "--report-dir", report_arg],
            [py, "scripts/collect_install_smoke_evidence.py", "--mode", "adb", "--report-dir", report_arg],
            [py, "scripts/export_release_evidence_report.py", "--mode", "all", "--report-dir", report_arg],
            [py, "scripts/build_release_channel_manifest.py", "--channel", args.channel, "--report-dir", report_arg],
            [py, "scripts/export_release_notes.py", "--channel", args.channel, "--report-dir", report_arg],
            [py, "scripts/build_rollback_package_manifest.py", "--report-dir", report_arg],
            [py, "scripts/check_release_operator_checklist.py", "--report-dir", report_arg],
            [py, "scripts/build_release_ops_final_seal.py", "--channel", args.channel, "--report-dir", report_arg],
            [py, "scripts/run_target_release_validation.py", "--mode", "verdict", "--report-dir", report_arg],
        ]
        results = execute_steps(steps)
        ready = write_status(report_dir, args.mode, results)
        if not ready:
            print("REL-FINAL remains pending. Complete the generated device/operator evidence templates, then use --mode finalize.")
        return 0 if ready else 1

    steps = [
        [py, "scripts/export_release_evidence_report.py", "--mode", "all", "--report-dir", report_arg],
        [py, "scripts/collect_release_artifact_identity.py", "--report-dir", report_arg],
        [py, "scripts/collect_install_smoke_evidence.py", "--mode", "adb", "--report-dir", report_arg],
        [py, "scripts/build_release_channel_manifest.py", "--channel", args.channel, "--report-dir", report_arg],
        [py, "scripts/export_release_notes.py", "--channel", args.channel, "--report-dir", report_arg],
        [py, "scripts/build_rollback_package_manifest.py", "--report-dir", report_arg],
        [py, "scripts/check_release_operator_checklist.py", "--report-dir", report_arg],
        [py, "scripts/build_release_ops_final_seal.py", "--channel", args.channel, "--report-dir", report_arg],
        [py, "scripts/run_target_release_validation.py", "--mode", "verdict", "--report-dir", report_arg],
    ]
    results = execute_steps(steps)
    ready = write_status(report_dir, args.mode, results)
    return 0 if ready else 1


if __name__ == "__main__":
    raise SystemExit(main())
