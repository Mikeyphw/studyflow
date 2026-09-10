#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "scripts/run_target_release_validation.py",
    "docs/validation/REL01_TARGET_RELEASE_VALIDATION_RUNNER.md",
    "REL01_TARGET_RELEASE_VALIDATION_RUNNER.md",
]
TOKENS = [
    "PENDING_TARGET_EXECUTION",
    "releaseReady",
    "verifyFinalReleaseAutomatedGate",
    "verifyFinalReleaseArtifact",
    "recordConnectedReleaseTestGates",
    "recordFullJourneyEvidence",
    "record_final_device_acceptance.py",
    "verifyFinalReleaseVerdict",
    "not Phase 65",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def text(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    require(value.endswith("\n"), f"{path} must end with a newline")
    return value


def main() -> None:
    for path in REQUIRED:
        require((ROOT / path).is_file(), f"missing REL01 file: {path}")

    runner = text("scripts/run_target_release_validation.py")
    docs = text("docs/validation/REL01_TARGET_RELEASE_VALIDATION_RUNNER.md")
    note = text("REL01_TARGET_RELEASE_VALIDATION_RUNNER.md")
    gradle = text("build.gradle.kts")
    devtool = text(".devtool.toml")
    readme = text("README.md")
    changelog = text("CHANGELOG.md")
    final_index = text("docs/roadmap/FINAL_ROADMAP_INDEX.md")
    packaging = text("docs/release/RELEASE_PACKAGING.md")
    matrix = text("docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md")

    combined = "\n".join([runner, docs, note, final_index, packaging, matrix])
    for token in TOKENS:
        require(token in combined, f"REL01 token missing: {token}")

    require('tasks.register<Exec>("verifyRel01TargetReleaseValidationRunner")' in gradle,
            "Gradle REL01 verifier task missing")
    require('commandLine("python3", "scripts/verify_rel01_target_release_validation_runner.py")' in gradle,
            "Gradle REL01 verifier command missing")
    require('"verifyRel01TargetReleaseValidationRunner"' in devtool,
            "Devtool test lane must include REL01 verifier")
    require('"verifyRel01TargetReleaseValidationRunner"' in gradle,
            "checkAll must include REL01 verifier")
    require("REL01 target release validation runner" in readme,
            "README must expose REL01 status")
    require("# REL01" in changelog,
            "CHANGELOG must include REL01 entry")
    require("PHASE_65" not in runner and "Phase 65:" not in runner,
            "runner must not create a Phase 65 milestone")
    require("releaseReady\": True" not in runner and "releaseReady=True" not in runner,
            "runner must not synthesize releaseReady=true")
    require("studyflow_rel01_target_release_validation_runner_v1.zip" in docs,
            "docs must include the overlay filename")
    require("--memory-guard-mb 0" in docs and "--no-validate" in docs,
            "docs must keep the intermediate Devtool flags")
    require(re.search(r"REQUIRED_DEVICE_CHECKS\s*=\s*\[", runner),
            "runner must enumerate device checks for template generation")

    print("REL01_TARGET_RELEASE_VALIDATION_RUNNER: PASS")


if __name__ == "__main__":
    main()
