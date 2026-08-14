#!/usr/bin/env python3
from pathlib import Path
import re
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


require(phase_version_at_least(ROOT, 36), "app version must be release-candidate Phase 36 or later")
app_build = read("app/build.gradle.kts")
require('versionCode = 37' in app_build, "app versionCode must advance to 37")
require('versionName = "1.0.0-rc1"' in app_build, "app versionName must be release-shaped 1.0.0-rc1")
require("phase35" not in app_build and "0.35.0-phase35" not in app_build,
        "app build must not keep phase-style versionName")
require("isMinifyEnabled = false" in app_build, "release minification decision must remain explicit")

proguard = read("app/proguard-rules.pro")
for required in ["1.0.0-rc1", "Shrinking remains disabled", "R8", "keep rules"]:
    require(required in proguard, f"proguard rules missing release packaging marker: {required}")
require("Future release phases" not in proguard, "proguard rules must not keep stale future-phase copy")

phase_version = read("scripts/phase_version.py")
for required in ["_RELEASE_CANDIDATE_PATTERN", r"1\.0\.0-rc", "versionCode", "version_code >= minimum_phase"]:
    require(required in phase_version, f"phase version helper missing release-candidate support: {required}")

release_doc = read("docs/release/RELEASE_PACKAGING.md")
for required in [
    "Version code: `37`",
    "Version name: `1.0.0-rc1`",
    "Signing boundary",
    "Do not commit",
    "Minification decision",
    "isMinifyEnabled = false",
]:
    require(required in release_doc, f"release packaging doc missing marker: {required}")
for forbidden in ["keystore password:", "storePassword", "keyPassword", "service-account.json"]:
    require(forbidden not in release_doc, f"release packaging doc must not include secret-like marker: {forbidden}")

notes = read("docs/release/RELEASE_NOTES_1.0.0_RC1.md")
require("StudyFlow 1.0.0-rc1" in notes and "offline-first" in notes,
        "release notes must describe the rc1 offline-first release")

readme = read("README.md")
for required in ["implemented through **Phase 37**", "1.0.0-rc1", "Release candidate package"]:
    require(required in readme, f"README missing Phase 36 marker: {required}")

factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require(
    "CurrentPhase = 37" in factory
    and "fun sourceOnlySeal(" in factory
    and "fun evidenceSeal(" in factory
    and "Required evidence was not supplied." in factory,
    "release validation factory must advance through Phase 37",
)
readiness = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
require("ReleaseReadinessPhase(36" in readiness and "ReleaseReadinessPhase(37" in readiness,
        "release readiness seal must include Phases 36 and 37")
build = read("build.gradle.kts")
require("through Phase 37" in build and "for phase in {0..37}; do" in build,
        "Gradle repository contract task must cover Phase 37")
validate = read("scripts/validate_repo.sh")
require("python3 scripts/verify_phase_36.py" in validate and "python3 scripts/verify_phase_37.py" in validate,
        "validate_repo must run Phase 36 and Phase 37 verifiers")

if errors:
    print("PHASE_36_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_36_CONTRACT: PASSED (release identity and packaging policy)")
