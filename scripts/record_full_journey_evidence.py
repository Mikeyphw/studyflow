#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build/reports/studyflow"
TEST_CLASS = "com.mikeyphw.studyflow.StudyFlowFullJourneyAcceptanceTest"
INITIAL_TEST = "planThroughRealRestorePersistsAcrossProductionRepositories"
POST_RESTART_TEST = "restoredJourneySurvivesProcessRestartAndCleansUp"
APPLICATION_ID = "com.mikeyphw.studyflow"
INITIAL_MODE = "restart_initial"
POST_RESTART_MODE = "post_restart"


def fail(message: str) -> None:
    print(f"FULL_JOURNEY_EVIDENCE: FAILED — {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict:
    if not path.is_file():
        fail(f"missing prerequisite evidence: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"prerequisite evidence is not an object: {path}")
    return value


def command(*args: object, allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        [str(arg) for arg in args],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if completed.returncode and not allow_failure:
        detail = (completed.stderr or completed.stdout).strip()
        fail(f"command failed ({completed.returncode}): {' '.join(map(str, args))}\n{detail}")
    return completed


def adb(serial: str, *args: str, allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
    return command("adb", "-s", serial, *args, allow_failure=allow_failure)


def adb_text(serial: str, *args: str) -> str:
    return adb(serial, *args).stdout.strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def installed_apk_sha256(serial: str, package_name: str) -> str:
    package_paths = [
        line.removeprefix("package:").strip()
        for line in adb_text(serial, "shell", "pm", "path", package_name).splitlines()
        if line.startswith("package:")
    ]
    base_path = next((path for path in package_paths if path.endswith("/base.apk")), None)
    if base_path is None and len(package_paths) == 1:
        base_path = package_paths[0]
    if not base_path:
        fail(f"unable to resolve installed base APK for {package_name}")
    process = subprocess.Popen(
        ["adb", "-s", serial, "exec-out", "cat", base_path],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert process.stdout is not None
    digest = hashlib.sha256()
    for chunk in iter(lambda: process.stdout.read(1024 * 1024), b""):
        digest.update(chunk)
    _, stderr = process.communicate()
    if process.returncode:
        fail(
            f"unable to read installed base APK for {package_name}; "
            f"adb exited {process.returncode}: {stderr.decode(errors='replace').strip()}",
        )
    return digest.hexdigest()


def exactly_one_device() -> str:
    if not shutil.which("adb"):
        fail("adb is not available")
    completed = command("adb", "devices")
    devices = [
        line.split("\t", 1)[0]
        for line in completed.stdout.splitlines()[1:]
        if line.endswith("\tdevice")
    ]
    if len(devices) != 1:
        fail(f"expected exactly one adb device, found {len(devices)}")
    return devices[0]


def find_test_apk() -> Path:
    root = ROOT / "app/build/outputs/apk/androidTest/release"
    candidates = sorted(path for path in root.glob("*.apk") if path.is_file())
    if len(candidates) != 1:
        fail(f"expected one Release androidTest APK under {root}, found {len(candidates)}")
    return candidates[0].resolve()


def instrumentation_component(serial: str) -> str:
    lines = adb_text(serial, "shell", "pm", "list", "instrumentation").splitlines()
    matches: list[str] = []
    for line in lines:
        match = re.match(r"instrumentation:([^ ]+) \(target=([^\)]+)\)", line.strip())
        if match and match.group(2) == APPLICATION_ID:
            matches.append(match.group(1))
    if len(matches) != 1:
        fail(f"expected one instrumentation targeting {APPLICATION_ID}, found {matches!r}")
    return matches[0]


def assert_instrumentation_passed(result: subprocess.CompletedProcess[str], test_name: str) -> str:
    transcript = (result.stdout or "") + ("\n" + result.stderr if result.stderr else "")
    bad_markers = ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg=Process crashed")
    if result.returncode or any(marker in transcript for marker in bad_markers):
        fail(f"instrumentation {test_name} failed\n{transcript.strip()}")
    if "INSTRUMENTATION_CODE: -1" not in transcript or not re.search(r"\bOK \(\d+ test[s]?\)\b", transcript):
        fail(f"instrumentation {test_name} did not report an authoritative JUnit PASS\n{transcript.strip()}")
    if test_name not in transcript and TEST_CLASS not in transcript:
        fail(f"instrumentation transcript is not attributable to {TEST_CLASS}#{test_name}")
    return transcript


def run_targeted_instrumentation(
    serial: str,
    component: str,
    test_name: str,
    mode: str,
) -> str:
    result = adb(
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        f"{TEST_CLASS}#{test_name}",
        "-e",
        "studyflowJourneyMode",
        mode,
        component,
        allow_failure=True,
    )
    return assert_instrumentation_passed(result, test_name)


def main() -> None:
    host_path = REPORT_DIR / "release-host-gates.json"
    artifact_path = REPORT_DIR / "release-artifact-identity.json"
    connected_path = REPORT_DIR / "connected-test-gates.json"
    host = load_json(host_path)
    artifact = load_json(artifact_path)
    connected = load_json(connected_path)
    sessions = {
        str(value.get("validationSession") or "").strip()
        for value in (host, artifact, connected)
    }
    if len(sessions) != 1 or "" in sessions:
        fail("host, artifact, and connected evidence must share one non-empty validation session")
    validation_session = next(iter(sessions))
    if host.get("status") != "PASS" or artifact.get("verified") is not True or connected.get("status") != "PASS":
        fail("host, exact-artifact, and connected-test prerequisite evidence must all PASS")

    artifact_entry = artifact.get("artifact", {})
    raw_apk = artifact_entry.get("path") if isinstance(artifact_entry, dict) else None
    expected_hash = str(artifact_entry.get("sha256") or "").lower() if isinstance(artifact_entry, dict) else ""
    if not raw_apk or not re.fullmatch(r"[0-9a-f]{64}", expected_hash):
        fail("signed-artifact evidence does not contain a usable APK path and SHA-256")
    apk = Path(str(raw_apk)).expanduser().resolve()
    if not apk.is_file() or sha256_file(apk) != expected_hash:
        fail("signed APK bytes no longer match exact-artifact evidence")
    if connected.get("artifactSha256") != expected_hash:
        fail("connected-test evidence is not bound to the exact signed APK")

    serial = exactly_one_device()
    test_apk = find_test_apk()
    report_dir = REPORT_DIR
    report_dir.mkdir(parents=True, exist_ok=True)
    initial_transcript_path = report_dir / "release-journey-initial.txt"
    restart_transcript_path = report_dir / "release-journey-post-restart.txt"

    adb(serial, "uninstall", APPLICATION_ID, allow_failure=True)
    existing_instrumentation = adb_text(serial, "shell", "pm", "list", "instrumentation")
    for line in existing_instrumentation.splitlines():
        match = re.match(r"instrumentation:([^/]+)/[^ ]+ \(target=([^\)]+)\)", line.strip())
        if match and match.group(2) == APPLICATION_ID:
            adb(serial, "uninstall", match.group(1), allow_failure=True)

    adb(serial, "install", str(apk))
    if installed_apk_sha256(serial, APPLICATION_ID) != expected_hash:
        fail("fresh-installed application bytes do not match the exact signed candidate")
    adb(serial, "install", "-r", str(test_apk))
    component = instrumentation_component(serial)

    initial = run_targeted_instrumentation(serial, component, INITIAL_TEST, INITIAL_MODE)
    initial_transcript_path.write_text(initial, encoding="utf-8")
    if installed_apk_sha256(serial, APPLICATION_ID) != expected_hash:
        fail("initial full journey changed or replaced the installed candidate APK")

    adb(serial, "shell", "am", "force-stop", APPLICATION_ID)
    adb(serial, "shell", "am", "start", "-W", "-n", f"{APPLICATION_ID}/.MainActivity")
    adb(serial, "shell", "am", "force-stop", APPLICATION_ID)

    restarted = run_targeted_instrumentation(serial, component, POST_RESTART_TEST, POST_RESTART_MODE)
    restart_transcript_path.write_text(restarted, encoding="utf-8")
    installed_hash = installed_apk_sha256(serial, APPLICATION_ID)
    if installed_hash != expected_hash:
        fail("post-restart journey did not run against the exact signed candidate APK")

    out = REPORT_DIR / "full-journey.json"
    out.write_text(
        json.dumps(
            {
                "schemaVersion": 2,
                "status": "PASS",
                "validationSession": validation_session,
                "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
                "testClass": TEST_CLASS,
                "initialTestName": INITIAL_TEST,
                "postRestartTestName": POST_RESTART_TEST,
                "processRestartVerified": True,
                "realRestoreVerified": True,
                "artifactSha256": expected_hash,
                "installedArtifactSha256": installed_hash,
                "deviceSerial": serial,
                "instrumentationComponent": component,
                "testApk": str(test_apk),
                "transcripts": [str(initial_transcript_path), str(restart_transcript_path)],
                "connectedEvidence": str(connected_path),
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    print("FULL_JOURNEY_EVIDENCE: PASS — real restore survived a forced process restart")
    print(f"Evidence: {out}")


if __name__ == "__main__":
    main()
