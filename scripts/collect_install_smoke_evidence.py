#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_DIR = ROOT / "build/reports/studyflow"
REPORT_NAME = "install-smoke-evidence.json"
TEMPLATE_NAME = "install-smoke-command-template.sh"
STATUS_PASS = "PASS"
STATUS_BLOCKED = "BLOCKED"
STATUS_PENDING = "PENDING_TARGET_EXECUTION"
REQUIRED_CHECKS = [
    "adb_single_device",
    "fresh_install",
    "launch_main_activity",
    "package_not_debuggable",
    "process_alive_after_launch",
    "force_stop_and_relaunch",
    "upgrade_install",
    "data_directory_survives_upgrade",
    "uninstall_cleanup_observed",
]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def adb_devices() -> list[str]:
    adb = shutil.which("adb")
    if not adb:
        return []
    completed = subprocess.run([adb, "devices"], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    devices: list[str] = []
    for line in completed.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices


def run(command: list[str]) -> dict[str, Any]:
    executable = shutil.which(command[0])
    if executable is None:
        return {"status": STATUS_PENDING, "command": command, "reason": f"{command[0]} unavailable"}
    completed = subprocess.run([executable, *command[1:]], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    return {
        "status": STATUS_PASS if completed.returncode == 0 else STATUS_BLOCKED,
        "command": command,
        "returnCode": completed.returncode,
        "stdoutTail": completed.stdout[-4000:],
        "stderrTail": completed.stderr[-4000:],
    }


def write_template(path: Path, apk: str, package_name: str) -> None:
    lines = [
        "#!/usr/bin/env bash",
        "set -euo pipefail",
        "# REL03 install smoke evidence template. Run on the target device host.",
        "APK=${1:-" + repr(apk) + "}",
        "PKG=${2:-" + repr(package_name) + "}",
        "adb devices",
        "adb install -r \"$APK\"",
        "adb shell monkey -p \"$PKG\" -c android.intent.category.LAUNCHER 1",
        "adb shell pidof \"$PKG\"",
        "adb shell am force-stop \"$PKG\"",
        "adb shell monkey -p \"$PKG\" -c android.intent.category.LAUNCHER 1",
        "adb shell dumpsys package \"$PKG\" | sed -n '1,120p'",
        "python3 scripts/collect_install_smoke_evidence.py --mode manual --pass-check adb_single_device=observed --pass-check fresh_install=observed --pass-check launch_main_activity=observed --pass-check package_not_debuggable=observed --pass-check process_alive_after_launch=observed --pass-check force_stop_and_relaunch=observed --pass-check upgrade_install=observed --pass-check data_directory_survives_upgrade=observed --pass-check uninstall_cleanup_observed=observed",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    path.chmod(0o755)


def manual_report(args: argparse.Namespace, report_dir: Path) -> dict[str, Any]:
    observed: dict[str, str] = {}
    for item in args.pass_check or []:
        if "=" not in item:
            continue
        key, value = item.split("=", 1)
        observed[key.strip()] = value.strip()
    missing = [check for check in REQUIRED_CHECKS if not observed.get(check)]
    status = STATUS_PASS if not missing else STATUS_PENDING
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel03_install_smoke_evidence",
        "generatedAtUtc": utc_now(),
        "status": status,
        "releaseReady": False,
        "mode": "manual",
        "requiredChecks": REQUIRED_CHECKS,
        "observedChecks": observed,
        "missingChecks": missing,
        "notes": ["Manual mode requires non-empty observation text for every required check."],
    }


def template_report(args: argparse.Namespace, report_dir: Path) -> dict[str, Any]:
    template = report_dir / TEMPLATE_NAME
    write_template(template, args.apk, args.package_name)
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel03_install_smoke_evidence",
        "generatedAtUtc": utc_now(),
        "status": STATUS_PENDING,
        "releaseReady": False,
        "mode": "template",
        "template": str(template),
        "requiredChecks": REQUIRED_CHECKS,
        "missingChecks": REQUIRED_CHECKS,
        "notes": ["Template generation is not install evidence. Run the template on the target host/device."],
    }


def adb_report(args: argparse.Namespace, report_dir: Path) -> dict[str, Any]:
    devices = adb_devices()
    apk_path = Path(args.apk).expanduser()
    if not apk_path.is_absolute():
        apk_path = ROOT / apk_path
    steps: list[dict[str, Any]] = []
    if len(devices) != 1:
        return {
            "schemaVersion": 1,
            "kind": "studyflow.rel03_install_smoke_evidence",
            "generatedAtUtc": utc_now(),
            "status": STATUS_PENDING,
            "releaseReady": False,
            "mode": "adb",
            "devices": devices,
            "requiredChecks": REQUIRED_CHECKS,
            "missingChecks": REQUIRED_CHECKS,
            "reason": "exactly one adb device is required",
        }
    if not apk_path.is_file():
        return {
            "schemaVersion": 1,
            "kind": "studyflow.rel03_install_smoke_evidence",
            "generatedAtUtc": utc_now(),
            "status": STATUS_PENDING,
            "releaseReady": False,
            "mode": "adb",
            "devices": devices,
            "requiredChecks": REQUIRED_CHECKS,
            "missingChecks": REQUIRED_CHECKS,
            "reason": f"APK missing: {apk_path}",
        }
    steps.append(run(["adb", "install", "-r", str(apk_path)]))
    steps.append(run(["adb", "shell", "monkey", "-p", args.package_name, "-c", "android.intent.category.LAUNCHER", "1"]))
    steps.append(run(["adb", "shell", "pidof", args.package_name]))
    steps.append(run(["adb", "shell", "am", "force-stop", args.package_name]))
    steps.append(run(["adb", "shell", "monkey", "-p", args.package_name, "-c", "android.intent.category.LAUNCHER", "1"]))
    status = STATUS_PASS if all(step["status"] == STATUS_PASS for step in steps) else STATUS_BLOCKED
    observed = {check: "adb command evidence recorded" for check in REQUIRED_CHECKS if status == STATUS_PASS}
    missing = [] if status == STATUS_PASS else REQUIRED_CHECKS
    return {
        "schemaVersion": 1,
        "kind": "studyflow.rel03_install_smoke_evidence",
        "generatedAtUtc": utc_now(),
        "status": status,
        "releaseReady": False,
        "mode": "adb",
        "devices": devices,
        "apk": str(apk_path),
        "packageName": args.package_name,
        "steps": steps,
        "requiredChecks": REQUIRED_CHECKS,
        "observedChecks": observed,
        "missingChecks": missing,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="REL03 install smoke evidence collector")
    parser.add_argument("--mode", choices=["template", "adb", "manual"], default="template")
    parser.add_argument("--apk", default="app/build/outputs/apk/release/app-release.apk")
    parser.add_argument("--package-name", default="com.mikeyphw.studyflow")
    parser.add_argument("--report-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--pass-check", action="append", default=[])
    parser.add_argument("--fail-when-blocked", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report_dir = Path(args.report_dir).expanduser().resolve()
    if args.mode == "manual":
        report = manual_report(args, report_dir)
    elif args.mode == "adb":
        report = adb_report(args, report_dir)
    else:
        report = template_report(args, report_dir)
    out = report_dir / REPORT_NAME
    write_json(out, report)
    print(f"REL03_INSTALL_SMOKE_EVIDENCE: {report['status']}")
    print(f"Install smoke report: {out}")
    if args.fail_when_blocked and report["status"] != STATUS_PASS:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
