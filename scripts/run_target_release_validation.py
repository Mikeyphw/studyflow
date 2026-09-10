#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build/reports/studyflow"
AUTOMATED_SCRIPT = ROOT / "scripts/run_final_release_acceptance.sh"
VERDICT_SCRIPT = ROOT / "scripts/verify_final_release_verdict.py"
DEVICE_SCRIPT = ROOT / "scripts/record_final_device_acceptance.py"
REQUIRED_SIGNING_ENV = [
    "STUDYFLOW_RELEASE_STORE_FILE",
    "STUDYFLOW_RELEASE_STORE_PASSWORD",
    "STUDYFLOW_RELEASE_KEY_ALIAS",
    "STUDYFLOW_RELEASE_KEY_PASSWORD",
]
REQUIRED_REPORTS = {
    "host": "release-host-gates.json",
    "artifact": "release-artifact-identity.json",
    "connected": "connected-test-gates.json",
    "journey": "full-journey.json",
    "device": "device-acceptance.json",
    "verdict": "final-release-verdict.json",
}
REQUIRED_DEVICE_CHECKS = [
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
]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def report_state(report_dir: Path) -> dict[str, Any]:
    reports: dict[str, Any] = {}
    missing: list[str] = []
    failed: list[str] = []
    for key, name in REQUIRED_REPORTS.items():
        path = report_dir / name
        value = load_json(path)
        if value is None:
            missing.append(key)
            reports[key] = {"path": str(path), "status": "MISSING"}
            continue
        status = "PASS"
        if key == "artifact":
            status = "PASS" if value.get("verified") is True else "FAIL"
        elif key == "verdict":
            status = "PASS" if value.get("releaseReady") is True else "BLOCKED"
        else:
            status = str(value.get("status") or "UNKNOWN")
        if status != "PASS" and key != "verdict":
            failed.append(key)
        reports[key] = {"path": str(path), "status": status}
    return {"reports": reports, "missing": missing, "failed": failed}


def adb_devices() -> list[str]:
    if shutil.which("adb") is None:
        return []
    completed = subprocess.run(["adb", "devices"], text=True, capture_output=True)
    if completed.returncode:
        return []
    return [
        line.split("\t", 1)[0]
        for line in completed.stdout.splitlines()[1:]
        if line.endswith("\tdevice")
    ]


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def plan(args: argparse.Namespace) -> int:
    report_dir = Path(args.report_dir).expanduser().resolve()
    devices = adb_devices()
    missing_signing = [name for name in REQUIRED_SIGNING_ENV if not os.getenv(name)]
    state = report_state(report_dir)
    value = {
        "schemaVersion": 1,
        "kind": "studyflow.target_release_validation_plan",
        "status": "PENDING_TARGET_EXECUTION",
        "releaseReady": False,
        "generatedAtUtc": utc_now(),
        "validationSession": args.session or f"rel01-{uuid.uuid4()}",
        "repository": str(ROOT),
        "reportDir": str(report_dir),
        "missingSigningEnvironment": missing_signing,
        "adbDevices": devices,
        "requiredReports": state["reports"],
        "commands": [
            "python3 scripts/run_target_release_validation.py --mode automated",
            "python3 scripts/run_target_release_validation.py --mode device-template",
            "python3 scripts/record_final_device_acceptance.py --pass-check CHECK --evidence CHECK=OBSERVED",
            "python3 scripts/run_target_release_validation.py --mode verdict",
        ],
        "gradleFinalGates": [
            "verifyFinalReleaseAutomatedGate",
            "verifyFinalReleaseArtifact",
            "recordConnectedReleaseTestGates",
            "recordFullJourneyEvidence",
            "verifyFinalReleaseVerdict",
        ],
        "notes": [
            "REL01 is not Phase 65 and does not extend SFLP beyond SFLP24.",
            "Missing or BLOCKED evidence must remain non-release-ready.",
        ],
    }
    out = report_dir / "target-release-validation-plan.json"
    write_json(out, value)
    print("TARGET_RELEASE_VALIDATION_PLAN: PENDING_TARGET_EXECUTION")
    print(f"Evidence plan: {out}")
    if missing_signing:
        print("Missing signing environment: " + ", ".join(missing_signing))
    if len(devices) != 1:
        print(f"ADB device count is {len(devices)}; final automated validation requires exactly one.")
    return 0


def run_command(command: list[str], env: dict[str, str] | None = None) -> int:
    printable = " ".join(command)
    print(f"$ {printable}")
    completed = subprocess.run(command, cwd=ROOT, env=env)
    return completed.returncode


def automated(args: argparse.Namespace) -> int:
    if not AUTOMATED_SCRIPT.is_file():
        print(f"missing automation script: {AUTOMATED_SCRIPT}", file=sys.stderr)
        return 2
    env = os.environ.copy()
    env["STUDYFLOW_RELEASE_VALIDATION_SESSION"] = args.session or env.get(
        "STUDYFLOW_RELEASE_VALIDATION_SESSION",
        f"rel01-{uuid.uuid4()}",
    )
    code = run_command(["bash", str(AUTOMATED_SCRIPT)], env=env)
    summary_path = Path(args.report_dir).expanduser().resolve() / "target-release-validation-summary.json"
    state = report_state(Path(args.report_dir).expanduser().resolve())
    write_json(
        summary_path,
        {
            "schemaVersion": 1,
            "kind": "studyflow.target_release_validation_summary",
            "status": "PASS" if code == 0 else "BLOCKED",
            "releaseReady": False,
            "generatedAtUtc": utc_now(),
            "validationSession": env["STUDYFLOW_RELEASE_VALIDATION_SESSION"],
            "automatedExitCode": code,
            "requiredReports": state["reports"],
            "missingEvidence": state["missing"],
            "failedEvidence": state["failed"],
            "nextStep": "record manual device acceptance, then run verifyFinalReleaseVerdict",
        },
    )
    print(f"Target validation summary: {summary_path}")
    return code


def device_template(args: argparse.Namespace) -> int:
    report_dir = Path(args.report_dir).expanduser().resolve()
    template = report_dir / "device-acceptance-command-template.sh"
    lines = [
        "#!/usr/bin/env bash",
        "set -euo pipefail",
        "# Replace every OBSERVED_* value with real observation text before running.",
        "python3 scripts/record_final_device_acceptance.py \\",
    ]
    for index, check in enumerate(REQUIRED_DEVICE_CHECKS):
        suffix = " \\" if index != len(REQUIRED_DEVICE_CHECKS) - 1 else ""
        lines.append(f"  --pass-check {check} --evidence '{check}=OBSERVED_{check}'{suffix}")
    lines.append("./gradlew --no-daemon verifyFinalReleaseVerdict")
    template.parent.mkdir(parents=True, exist_ok=True)
    template.write_text("\n".join(lines) + "\n", encoding="utf-8")
    template.chmod(0o755)
    print("DEVICE_ACCEPTANCE_TEMPLATE: PENDING_TARGET_EXECUTION")
    print(f"Template: {template}")
    return 0


def verdict(args: argparse.Namespace) -> int:
    command = [sys.executable, str(VERDICT_SCRIPT)]
    if args.report_dir:
        report_dir = Path(args.report_dir).expanduser().resolve()
        command.extend([
            "--host", str(report_dir / "release-host-gates.json"),
            "--artifact", str(report_dir / "release-artifact-identity.json"),
            "--connected", str(report_dir / "connected-test-gates.json"),
            "--journey", str(report_dir / "full-journey.json"),
            "--device", str(report_dir / "device-acceptance.json"),
            "--report", str(report_dir / "final-release-verdict.json"),
        ])
    code = run_command(command)
    if code != 0:
        print("FINAL_RELEASE_VERDICT: BLOCKED")
    return code


def main() -> int:
    parser = argparse.ArgumentParser(description="Run or prepare the StudyFlow target release validation pass.")
    parser.add_argument("--mode", choices=["plan", "automated", "device-template", "verdict"], default="plan")
    parser.add_argument("--report-dir", default=str(REPORT_DIR))
    parser.add_argument("--session", default="")
    args = parser.parse_args()
    if args.mode == "plan":
        return plan(args)
    if args.mode == "automated":
        return automated(args)
    if args.mode == "device-template":
        return device_template(args)
    if args.mode == "verdict":
        return verdict(args)
    raise AssertionError(args.mode)


if __name__ == "__main__":
    raise SystemExit(main())
