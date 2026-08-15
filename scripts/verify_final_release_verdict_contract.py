#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFIER = ROOT / "scripts/verify_final_release_verdict.py"
SESSION = "contract-session"
APK_BYTES = b"signed release apk contract bytes"
AAB_BYTES = b"signed release aab contract bytes"
APK_SHA = hashlib.sha256(APK_BYTES).hexdigest()
AAB_SHA = hashlib.sha256(AAB_BYTES).hexdigest()
CERT_SHA = "a" * 64
HOST_GATES = {
    "gradle_configuration",
    "source_contracts",
    "main_compilation",
    "test_compilation",
    "unit_tests",
    "resource_merge",
    "lint",
    "release_assemble",
    "release_bundle",
}
CONNECTED_GATES = {
    "core_data",
    "core_database_migrations",
    "feature_plans",
    "feature_practice",
    "feature_schedule",
    "feature_workspace",
    "app_release_suite",
}
DEVICE_CHECKS = {
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
}


def write(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value), encoding="utf-8")


def run(paths: dict[str, Path], report: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(VERIFIER),
            "--host",
            str(paths["host"]),
            "--artifact",
            str(paths["artifact"]),
            "--connected",
            str(paths["connected"]),
            "--journey",
            str(paths["journey"]),
            "--device",
            str(paths["device"]),
            "--report",
            str(report),
        ],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )


def fixture(directory: Path) -> tuple[dict[str, Path], Path, Path]:
    apk = directory / "candidate.apk"
    aab = directory / "candidate.aab"
    apk.write_bytes(APK_BYTES)
    aab.write_bytes(AAB_BYTES)
    paths = {
        name: directory / f"{name}.json"
        for name in ("host", "artifact", "connected", "journey", "device")
    }
    write(
        paths["host"],
        {
            "status": "PASS",
            "validationSession": SESSION,
            "gates": [{"name": name, "status": "PASS"} for name in sorted(HOST_GATES)],
            "artifacts": {
                "releaseApk": {"path": str(apk), "sha256": APK_SHA},
                "releaseAab": {"path": str(aab), "sha256": AAB_SHA},
            },
        },
    )
    write(
        paths["artifact"],
        {
            "verified": True,
            "validationSession": SESSION,
            "artifact": {"path": str(apk), "sha256": APK_SHA},
            "releaseAab": {"path": str(aab), "sha256": AAB_SHA, "signatureVerified": True},
            "identity": {
                "applicationId": "com.mikeyphw.studyflow",
                "versionCode": 37,
                "versionName": "1.0.0-rc1",
                "debuggable": False,
                "signerCertificateSha256": [CERT_SHA],
            },
        },
    )
    write(
        paths["connected"],
        {
            "status": "PASS",
            "validationSession": SESSION,
            "artifactSha256": APK_SHA,
            "gates": [{"name": name, "status": "PASS"} for name in sorted(CONNECTED_GATES)],
        },
    )
    write(
        paths["journey"],
        {
            "status": "PASS",
            "validationSession": SESSION,
            "testClass": "com.mikeyphw.studyflow.StudyFlowFullJourneyAcceptanceTest",
            "initialTestName": "planThroughRealRestorePersistsAcrossProductionRepositories",
            "postRestartTestName": "restoredJourneySurvivesProcessRestartAndCleansUp",
            "processRestartVerified": True,
            "realRestoreVerified": True,
            "phase63_64InitialVerified": True,
            "phase63_64PostRestartVerified": True,
            "phase63_64Verified": True,
            "artifactSha256": APK_SHA,
            "installedArtifactSha256": APK_SHA,
            "deviceSerial": "contract-device",
        },
    )
    write(
        paths["device"],
        {
            "status": "PASS",
            "validationSession": SESSION,
            "artifactSha256": APK_SHA,
            "installedArtifactSha256": APK_SHA,
            "applicationId": "com.mikeyphw.studyflow",
            "versionCode": 37,
            "versionName": "1.0.0-rc1",
            "device": {"serial": "contract-device"},
            "checks": [
                {"id": check, "status": "PASS", "evidence": f"observed {check}"}
                for check in sorted(DEVICE_CHECKS)
            ],
        },
    )
    return paths, apk, aab


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="studyflow-ov13-verdict-") as raw:
        directory = Path(raw)
        paths, _, _ = fixture(directory)
        report = directory / "verdict.json"
        passed = run(paths, report)
        require(passed.returncode == 0, passed.stderr or "valid evidence did not pass")
        require(json.loads(report.read_text())["releaseReady"] is True, "valid evidence did not seal")

        paths, _, _ = fixture(directory)
        host = json.loads(paths["host"].read_text())
        host["gates"] = [item for item in host["gates"] if item["name"] != "release_bundle"]
        write(paths["host"], host)
        require(run(paths, report).returncode != 0, "missing Release AAB gate falsely passed")

        paths, _, _ = fixture(directory)
        connected = json.loads(paths["connected"].read_text())
        connected["gates"] = connected["gates"][:-1]
        write(paths["connected"], connected)
        require(run(paths, report).returncode != 0, "missing connected Android gate falsely passed")

        paths, _, _ = fixture(directory)
        journey = json.loads(paths["journey"].read_text())
        journey["validationSession"] = "stale-session"
        write(paths["journey"], journey)
        require(run(paths, report).returncode != 0, "mixed validation sessions falsely passed")

        paths, _, _ = fixture(directory)
        journey = json.loads(paths["journey"].read_text())
        journey["processRestartVerified"] = False
        write(paths["journey"], journey)
        require(run(paths, report).returncode != 0, "missing process restart falsely passed")

        paths, _, _ = fixture(directory)
        journey = json.loads(paths["journey"].read_text())
        journey["phase63_64InitialVerified"] = False
        write(paths["journey"], journey)
        require(run(paths, report).returncode != 0, "missing initial Phase 63/64 marker falsely passed")

        paths, _, _ = fixture(directory)
        journey = json.loads(paths["journey"].read_text())
        journey["phase63_64PostRestartVerified"] = False
        write(paths["journey"], journey)
        require(run(paths, report).returncode != 0, "missing post-restart Phase 63/64 marker falsely passed")

        paths, _, _ = fixture(directory)
        journey = json.loads(paths["journey"].read_text())
        journey["phase63_64Verified"] = False
        write(paths["journey"], journey)
        require(run(paths, report).returncode != 0, "missing Phase 63/64 journey evidence falsely passed")

        paths, _, _ = fixture(directory)
        device = json.loads(paths["device"].read_text())
        device["checks"] = device["checks"][:-1]
        write(paths["device"], device)
        require(run(paths, report).returncode != 0, "incomplete device acceptance falsely passed")

        paths, _, _ = fixture(directory)
        device = json.loads(paths["device"].read_text())
        device["checks"][0]["evidence"] = ""
        write(paths["device"], device)
        require(run(paths, report).returncode != 0, "evidence-free manual PASS falsely passed")

        paths, apk, _ = fixture(directory)
        apk.write_bytes(b"different artifact")
        require(run(paths, report).returncode != 0, "mutated signed artifact falsely passed")

        paths, _, aab = fixture(directory)
        aab.write_bytes(b"different app bundle")
        require(run(paths, report).returncode != 0, "mutated Release AAB falsely passed")

        paths, _, _ = fixture(directory)
        artifact = json.loads(paths["artifact"].read_text())
        artifact["releaseAab"]["signatureVerified"] = False
        write(paths["artifact"], artifact)
        require(run(paths, report).returncode != 0, "unverified AAB signature falsely passed")

    print("FINAL_RELEASE_VERDICT_CONTRACT: PASSED")


if __name__ == "__main__":
    main()
