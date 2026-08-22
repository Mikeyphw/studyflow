#!/usr/bin/env python3
"""Optional connected-device instrumentation runner for StudyFlow.

This runner is intentionally optional: it records SKIPPED when adb or a device is not available,
unless STUDYFLOW_REQUIRE_CONNECTED_DEVICE=true is set. When a device is available, connected
instrumentation failures are real failures.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

REPORT = Path("build/reports/studyflow/optional-instrumentation-validation.json")
LOCALHOST_SERIAL = "localhost:5555"
DEFAULT_TASKS = [":app:connectedDebugAndroidTest"]


def truthy(value: str | None) -> bool:
    return str(value or "").strip().lower() in {"1", "true", "yes", "y", "on"}


def run(command: list[str], timeout: int = 90) -> dict[str, Any]:
    started = time.time()
    try:
        completed = subprocess.run(
            command,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
        )
        return {
            "command": command,
            "exitCode": completed.returncode,
            "stdout": completed.stdout.strip(),
            "stderr": completed.stderr.strip(),
            "durationSeconds": round(time.time() - started, 3),
        }
    except FileNotFoundError as error:
        return {
            "command": command,
            "exitCode": 127,
            "stdout": "",
            "stderr": str(error),
            "durationSeconds": round(time.time() - started, 3),
        }
    except subprocess.TimeoutExpired as error:
        return {
            "command": command,
            "exitCode": 124,
            "stdout": (error.stdout or "").strip() if isinstance(error.stdout, str) else "",
            "stderr": (error.stderr or "").strip() if isinstance(error.stderr, str) else "timeout",
            "durationSeconds": round(time.time() - started, 3),
        }


def write_report(report: dict[str, Any]) -> None:
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(f"OPTIONAL_INSTRUMENTATION_REPORT: {REPORT}")
    print(f"OPTIONAL_INSTRUMENTATION_STATUS: {report['status']}")


def adb_devices(adb: str) -> tuple[list[str], dict[str, Any]]:
    result = run([adb, "devices"], timeout=30)
    devices: list[str] = []
    if result["exitCode"] == 0:
        for line in result["stdout"].splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                devices.append(parts[0])
    return devices, result


def choose_device(adb: str, actions: list[dict[str, Any]]) -> str | None:
    devices, devices_result = adb_devices(adb)
    actions.append({"name": "adb devices", "result": devices_result, "devices": devices})
    requested = os.environ.get("ANDROID_SERIAL", "").strip()
    if requested and requested in devices:
        return requested
    if LOCALHOST_SERIAL in devices:
        return LOCALHOST_SERIAL

    autoconnect = truthy(os.environ.get("STUDYFLOW_ADB_AUTOCONNECT_LOCALHOST", "true"))
    if autoconnect and not requested:
        connect_result = run([adb, "connect", LOCALHOST_SERIAL], timeout=45)
        actions.append({"name": "adb connect localhost", "result": connect_result})
        devices, devices_result = adb_devices(adb)
        actions.append({"name": "adb devices after connect", "result": devices_result, "devices": devices})
        if LOCALHOST_SERIAL in devices:
            return LOCALHOST_SERIAL

    if requested and requested in devices:
        return requested
    if devices:
        return devices[0]
    return None


def root_probe(adb: str, serial: str, actions: list[dict[str, Any]]) -> dict[str, Any]:
    probe = run([adb, "-s", serial, "shell", "su", "-c", "id"], timeout=30)
    actions.append({"name": "non-invasive su root probe", "result": probe})
    available = probe["exitCode"] == 0 and "uid=0" in (probe["stdout"] + probe["stderr"])
    if available:
        return {"status": "AVAILABLE", "method": "su -c id", "details": probe["stdout"]}

    if truthy(os.environ.get("STUDYFLOW_ADB_TRY_ROOT")):
        adb_root = run([adb, "-s", serial, "root"], timeout=45)
        actions.append({"name": "adb root optional probe", "result": adb_root})
        retry = run([adb, "-s", serial, "shell", "id"], timeout=30)
        actions.append({"name": "adb root id retry", "result": retry})
        adb_root_available = retry["exitCode"] == 0 and "uid=0" in (retry["stdout"] + retry["stderr"])
        if adb_root_available:
            return {"status": "AVAILABLE", "method": "adb root", "details": retry["stdout"]}

    return {"status": "UNAVAILABLE", "method": "su -c id", "details": probe["stderr"] or probe["stdout"]}


def device_props(adb: str, serial: str, actions: list[dict[str, Any]]) -> dict[str, str]:
    props = {}
    for key in ["ro.product.manufacturer", "ro.product.model", "ro.build.version.sdk", "ro.debuggable"]:
        result = run([adb, "-s", serial, "shell", "getprop", key], timeout=30)
        actions.append({"name": f"getprop {key}", "result": result})
        props[key] = result["stdout"].strip()
    return props


def gradle_tasks() -> list[str]:
    configured = os.environ.get("STUDYFLOW_OPTIONAL_CONNECTED_TASKS", "").strip()
    if not configured:
        return DEFAULT_TASKS
    return [task for task in configured.replace(",", " ").split() if task]


def main() -> int:
    actions: list[dict[str, Any]] = []
    required = truthy(os.environ.get("STUDYFLOW_REQUIRE_CONNECTED_DEVICE"))
    adb = os.environ.get("ADB", "").strip() or shutil.which("adb")
    if not adb:
        report = {
            "schemaVersion": 1,
            "status": "SKIPPED",
            "reason": "adb not found",
            "optional": True,
            "required": required,
            "generatedAtEpochMillis": int(time.time() * 1000),
            "actions": actions,
        }
        write_report(report)
        return 1 if required else 0

    serial = choose_device(adb, actions)
    if not serial:
        report = {
            "schemaVersion": 1,
            "status": "SKIPPED",
            "reason": "no connected adb device; localhost:5555 was not online",
            "optional": True,
            "required": required,
            "adb": adb,
            "preferredSerial": os.environ.get("ANDROID_SERIAL") or LOCALHOST_SERIAL,
            "generatedAtEpochMillis": int(time.time() * 1000),
            "actions": actions,
        }
        write_report(report)
        return 1 if required else 0

    root = root_probe(adb, serial, actions)
    props = device_props(adb, serial, actions)
    tasks = gradle_tasks()
    gradle_command = ["./gradlew", *tasks, "--no-daemon", "--max-workers=1", "--console=plain"]
    gradle_result = run(gradle_command, timeout=int(os.environ.get("STUDYFLOW_OPTIONAL_GRADLE_TIMEOUT", "1800")))
    actions.append({"name": "gradle connected instrumentation", "result": gradle_result, "tasks": tasks})

    status = "PASS" if gradle_result["exitCode"] == 0 else "FAIL"
    report = {
        "schemaVersion": 1,
        "status": status,
        "optional": True,
        "required": required,
        "adb": adb,
        "serial": serial,
        "localhostPreferred": serial == LOCALHOST_SERIAL,
        "root": root,
        "device": props,
        "gradleTasks": tasks,
        "generatedAtEpochMillis": int(time.time() * 1000),
        "actions": actions,
    }
    write_report(report)
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
