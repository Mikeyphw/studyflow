#!/usr/bin/env python3
"""Configuration-cache-safe release evidence/signing gates moved out of root Gradle doLast blocks."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "build/reports/studyflow"
SIGNING_VARS = (
    "STUDYFLOW_RELEASE_STORE_FILE",
    "STUDYFLOW_RELEASE_STORE_PASSWORD",
    "STUDYFLOW_RELEASE_KEY_ALIAS",
    "STUDYFLOW_RELEASE_KEY_PASSWORD",
)


def signing(surface: str) -> None:
    values = {name: os.environ.get(name, "").strip() for name in SIGNING_VARS}
    missing = [name for name, value in values.items() if not value]
    if missing:
        raise SystemExit(
            f"StudyFlow {surface} Release packaging requires all signing inputs; missing: {', '.join(missing)}"
        )
    store = Path(values["STUDYFLOW_RELEASE_STORE_FILE"]).expanduser()
    if not store.is_file() or store.stat().st_size <= 0:
        raise SystemExit(f"StudyFlow {surface} Release keystore is missing or empty: {store}")
    print(f"STUDYFLOW_{surface.upper()}_RELEASE_SIGNING_PREFLIGHT: PASS")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(path)


def host() -> None:
    signing("phone")
    subprocess.run(
        [sys.executable, str(ROOT / "scripts/verify_gradle_dependency_integrity.py"), "--require-materialized"],
        cwd=ROOT,
        check=True,
    )
    apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
    aab = ROOT / "app/build/outputs/bundle/release/app-release.aab"
    for path in (apk, aab):
        if not path.is_file():
            raise SystemExit(f"Signed Release output is missing: {path}")
    session = os.environ.get("STUDYFLOW_RELEASE_VALIDATION_SESSION", "").strip() or f"host-{uuid.uuid4()}"
    report = REPORT_DIR / "release-host-gates.json"
    write_json(report, {
        "schemaVersion": 2,
        "status": "PASS",
        "validationSession": session,
        "generatedAtUtcEpochMillis": int(time.time() * 1000),
        "gates": [
            {"name": name, "status": "PASS"}
            for name in (
                "gradle_configuration", "source_contracts", "main_compilation", "test_compilation",
                "unit_tests", "resource_merge", "lint", "release_assemble", "release_bundle",
            )
        ],
        "artifacts": {
            "releaseApk": {"path": str(apk.resolve()), "sha256": sha256(apk)},
            "releaseAab": {"path": str(aab.resolve()), "sha256": sha256(aab)},
        },
        "scope": "signed host Release gate; connected Android evidence is recorded separately",
    })
    print("FINAL_RELEASE_HOST_GATES: PASS")
    print(f"Evidence: {report}")


def connected() -> None:
    host_report = json.loads((REPORT_DIR / "release-host-gates.json").read_text(encoding="utf-8"))
    artifact = json.loads((REPORT_DIR / "release-artifact-identity.json").read_text(encoding="utf-8"))
    host_session = str(host_report.get("validationSession", ""))
    artifact_session = str(artifact.get("validationSession", ""))
    if not host_session or artifact_session != host_session:
        raise SystemExit("Connected test prerequisites came from mixed or missing validation sessions")
    artifact_sha = str(artifact.get("artifact", {}).get("sha256", ""))
    if len(artifact_sha) != 64 or any(ch not in "0123456789abcdef" for ch in artifact_sha):
        raise SystemExit("Connected test evidence cannot resolve signed APK SHA-256")
    report = REPORT_DIR / "connected-test-gates.json"
    write_json(report, {
        "schemaVersion": 1,
        "status": "PASS",
        "validationSession": host_session,
        "generatedAtUtcEpochMillis": int(time.time() * 1000),
        "artifactSha256": artifact_sha,
        "gates": [{"name": name, "status": "PASS"} for name in (
            "core_data", "core_database_migrations", "feature_plans", "feature_practice",
            "feature_schedule", "feature_workspace", "app_release_suite",
        )],
    })
    print("CONNECTED_RELEASE_TEST_GATES: PASS")
    print(f"Evidence: {report}")


def automated() -> None:
    journey = REPORT_DIR / "full-journey.json"
    if not journey.is_file():
        raise SystemExit("Full restore/restart journey evidence is missing")
    payload = json.loads(journey.read_text(encoding="utf-8"))
    if payload.get("status") != "PASS":
        raise SystemExit("Full restore/restart journey evidence is not PASS")
    print("FINAL_RELEASE_AUTOMATED_GATE: PASS")
    print("Manual real-device observations remain required before productSealed=true.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("signing", "host", "connected", "automated"))
    parser.add_argument("--surface", default="phone")
    args = parser.parse_args()
    {"signing": lambda: signing(args.surface), "host": host, "connected": connected, "automated": automated}[args.mode]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
