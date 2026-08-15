#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build/reports/studyflow"
EXPECTED_APPLICATION_ID = "com.mikeyphw.studyflow"
EXPECTED_VERSION_CODE = 37
EXPECTED_VERSION_NAME = "1.0.0-rc1"
EXPECTED_TEST_CLASS = "com.mikeyphw.studyflow.StudyFlowFullJourneyAcceptanceTest"
EXPECTED_INITIAL_TEST = "planThroughRealRestorePersistsAcrossProductionRepositories"
EXPECTED_POST_RESTART_TEST = "restoredJourneySurvivesProcessRestartAndCleansUp"
REQUIRED_HOST_GATES = {
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
REQUIRED_CONNECTED_GATES = {
    "core_data",
    "core_database_migrations",
    "feature_plans",
    "feature_practice",
    "feature_schedule",
    "feature_workspace",
    "app_release_suite",
}
REQUIRED_DEVICE_CHECKS = {
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


def load(path: Path) -> dict:
    if not path.is_file():
        raise RuntimeError(f"missing evidence: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError(f"evidence is not an object: {path}")
    return value


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def gate_map(value: object) -> dict[str, str]:
    if not isinstance(value, list):
        return {}
    return {
        str(item.get("name")): str(item.get("status"))
        for item in value
        if isinstance(item, dict) and item.get("name") is not None
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=str(REPORT_DIR / "release-host-gates.json"))
    parser.add_argument("--artifact", default=str(REPORT_DIR / "release-artifact-identity.json"))
    parser.add_argument("--connected", default=str(REPORT_DIR / "connected-test-gates.json"))
    parser.add_argument("--journey", default=str(REPORT_DIR / "full-journey.json"))
    parser.add_argument("--device", default=str(REPORT_DIR / "device-acceptance.json"))
    parser.add_argument("--report", default=str(REPORT_DIR / "final-release-verdict.json"))
    args = parser.parse_args()
    paths = {
        name: Path(value).expanduser().resolve()
        for name, value in {
            "host": args.host,
            "artifact": args.artifact,
            "connected": args.connected,
            "journey": args.journey,
            "device": args.device,
        }.items()
    }
    loaded: dict[str, dict] = {}
    errors: list[str] = []
    for name, path in paths.items():
        try:
            loaded[name] = load(path)
        except Exception as exc:
            errors.append(str(exc))

    host = loaded.get("host", {})
    artifact = loaded.get("artifact", {})
    connected = loaded.get("connected", {})
    journey = loaded.get("journey", {})
    device = loaded.get("device", {})
    sessions = {
        str(value.get("validationSession") or "").strip()
        for value in (host, artifact, connected, journey, device)
        if value
    }
    if len(sessions) != 1 or "" in sessions:
        errors.append("all evidence must share one non-empty validation session")
    validation_session = next(iter(sessions), None) if len(sessions) == 1 else None

    host_apk_sha = ""
    host_aab_sha = ""
    host_aab_path: Path | None = None
    if host:
        if host.get("status") != "PASS":
            errors.append("host validation evidence is not PASS")
        host_gates = gate_map(host.get("gates"))
        if set(host_gates) != REQUIRED_HOST_GATES:
            errors.append("host evidence does not contain the exact required gate set")
        for gate in sorted(REQUIRED_HOST_GATES):
            if host_gates.get(gate) != "PASS":
                errors.append(f"required host gate is not PASS: {gate}")
        host_artifacts = host.get("artifacts", {})
        if not isinstance(host_artifacts, dict):
            host_artifacts = {}
        for key, label in (("releaseApk", "Release APK"), ("releaseAab", "Release AAB")):
            entry = host_artifacts.get(key, {})
            entry = entry if isinstance(entry, dict) else {}
            recorded_sha = str(entry.get("sha256") or "").lower()
            if not re.fullmatch(r"[0-9a-f]{64}", recorded_sha):
                errors.append(f"host evidence does not bind a {label} SHA-256")
                continue
            path = Path(str(entry.get("path") or ""))
            if not path.is_file():
                errors.append(f"host {label} path no longer exists")
            elif sha256_file(path) != recorded_sha:
                errors.append(f"host {label} file no longer matches recorded SHA-256")
            if key == "releaseApk":
                host_apk_sha = recorded_sha
            else:
                host_aab_sha = recorded_sha
                host_aab_path = path

    artifact_sha = str(artifact.get("artifact", {}).get("sha256") or "").lower()
    artifact_aab_sha = str(artifact.get("releaseAab", {}).get("sha256") or "").lower()
    if artifact:
        if artifact.get("verified") is not True:
            errors.append("signed artifact identity is not verified")
        identity = artifact.get("identity", {})
        identity = identity if isinstance(identity, dict) else {}
        if identity.get("applicationId") != EXPECTED_APPLICATION_ID:
            errors.append("signed artifact application ID is wrong")
        if identity.get("versionCode") != EXPECTED_VERSION_CODE:
            errors.append("signed artifact version code is wrong")
        if identity.get("versionName") != EXPECTED_VERSION_NAME:
            errors.append("signed artifact version name is wrong")
        if identity.get("debuggable") is not False:
            errors.append("signed artifact must be non-debuggable")
        certs = identity.get("signerCertificateSha256")
        if not isinstance(certs, list) or not certs or any(
            not re.fullmatch(r"[0-9a-f]{64}", str(cert).lower()) for cert in certs
        ):
            errors.append("signed artifact certificate SHA-256 evidence is invalid")
        apk_path = Path(str(artifact.get("artifact", {}).get("path") or ""))
        if not apk_path.is_file():
            errors.append("signed artifact path no longer exists")
        elif sha256_file(apk_path) != artifact_sha:
            errors.append("signed artifact file no longer matches recorded SHA-256")
        if artifact_sha != host_apk_sha:
            errors.append("signed artifact is not the APK produced by the host validation session")

        aab_entry = artifact.get("releaseAab", {})
        aab_entry = aab_entry if isinstance(aab_entry, dict) else {}
        if aab_entry.get("signatureVerified") is not True:
            errors.append("Release AAB signature verification evidence is not PASS")
        artifact_aab_path = Path(str(aab_entry.get("path") or ""))
        if not artifact_aab_path.is_file():
            errors.append("verified Release AAB path no longer exists")
        elif sha256_file(artifact_aab_path) != artifact_aab_sha:
            errors.append("verified Release AAB file no longer matches recorded SHA-256")
        if artifact_aab_sha != host_aab_sha:
            errors.append("verified Release AAB is not the AAB produced by the host validation session")
        if host_aab_path is not None and artifact_aab_path.is_file():
            if artifact_aab_path.resolve() != host_aab_path.resolve():
                errors.append("artifact and host evidence refer to different Release AAB paths")

    if connected:
        if connected.get("status") != "PASS":
            errors.append("connected Android test evidence is not PASS")
        connected_gates = gate_map(connected.get("gates"))
        if set(connected_gates) != REQUIRED_CONNECTED_GATES:
            errors.append("connected evidence does not contain the exact required gate set")
        for gate in sorted(REQUIRED_CONNECTED_GATES):
            if connected_gates.get(gate) != "PASS":
                errors.append(f"required connected Android gate is not PASS: {gate}")
        if connected.get("artifactSha256") != artifact_sha:
            errors.append("connected Android tests are not bound to the signed artifact SHA-256")

    if journey:
        if journey.get("status") != "PASS":
            errors.append("full Android journey evidence is not PASS")
        if journey.get("testClass") != EXPECTED_TEST_CLASS:
            errors.append("full journey evidence is not for the authoritative acceptance class")
        if journey.get("initialTestName") != EXPECTED_INITIAL_TEST:
            errors.append("full journey evidence is missing the authoritative initial restore test")
        if journey.get("postRestartTestName") != EXPECTED_POST_RESTART_TEST:
            errors.append("full journey evidence is missing the authoritative post-restart test")
        if journey.get("processRestartVerified") is not True:
            errors.append("full journey did not verify a process restart")
        if journey.get("realRestoreVerified") is not True:
            errors.append("full journey did not verify real restore behavior")
        if journey.get("phase63_64InitialVerified") is not True:
            errors.append("full journey did not emit the initial Phase 63/64 verification marker")
        if journey.get("phase63_64PostRestartVerified") is not True:
            errors.append("full journey did not emit the post-restart Phase 63/64 verification marker")
        if journey.get("phase63_64Verified") is not True:
            errors.append("full journey did not verify Phase 63/64 runbook and sprint behavior")
        if journey.get("artifactSha256") != artifact_sha:
            errors.append("full journey is not bound to the signed artifact SHA-256")
        if journey.get("installedArtifactSha256") != artifact_sha:
            errors.append("full journey device did not contain the exact signed artifact")
        if not str(journey.get("deviceSerial") or "").strip():
            errors.append("full journey evidence is missing the device serial")

    if device:
        if device.get("status") != "PASS":
            errors.append("device acceptance evidence is not PASS")
        checks = device.get("checks")
        check_entries = [item for item in checks if isinstance(item, dict)] if isinstance(checks, list) else []
        check_map = {str(item.get("id")): item for item in check_entries}
        if set(check_map) != REQUIRED_DEVICE_CHECKS:
            errors.append("device acceptance evidence does not contain the exact required check set")
        for check in sorted(REQUIRED_DEVICE_CHECKS):
            entry = check_map.get(check, {})
            if entry.get("status") != "PASS":
                errors.append(f"required device acceptance check is not PASS: {check}")
            if not str(entry.get("evidence") or "").strip():
                errors.append(f"required device acceptance check has no observation evidence: {check}")
        if device.get("artifactSha256") != artifact_sha:
            errors.append("device acceptance is not bound to the signed artifact SHA-256")
        if device.get("installedArtifactSha256") != artifact_sha:
            errors.append("device acceptance did not observe the exact signed artifact installed")
        if device.get("applicationId") != EXPECTED_APPLICATION_ID:
            errors.append("device acceptance application ID is wrong")
        if device.get("versionCode") != EXPECTED_VERSION_CODE or device.get("versionName") != EXPECTED_VERSION_NAME:
            errors.append("device acceptance version identity is wrong")
        if journey and device.get("device", {}).get("serial") != journey.get("deviceSerial"):
            errors.append("device acceptance and full journey were not run on the same device")

    verdict = {
        "schemaVersion": 2,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "validationSession": validation_session,
        "releaseReady": not errors,
        "artifactSha256": artifact_sha or None,
        "releaseAabSha256": artifact_aab_sha or None,
        "signerCertificateSha256": artifact.get("identity", {}).get("signerCertificateSha256", []),
        "evidence": {name: str(path) for name, path in paths.items()},
        "errors": errors,
    }
    out = Path(args.report).resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(verdict, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        print("FINAL_RELEASE_VERDICT: BLOCKED", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        raise SystemExit(1)
    print(f"FINAL_RELEASE_VERDICT: PASS — exact artifact sha256={artifact_sha}")
    print(f"Validation session: {validation_session}")


if __name__ == "__main__":
    main()
