#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_CMOV_TASKS = [
    "verifyContentModeFoundation",
    "verifyProgrammingCourseModel",
    "verifyCodeBlocksSyntax",
    "verifyTermuxRuntimeFoundation",
    "verifyProgrammingCourseUi",
    "verifyCodingExerciseTermuxExecution",
    "verifyLanguageDeckIdiomModel",
    "verifyLanguageReviewModes",
    "verifyImportTemplates",
    "verifyModeAwareAnalytics",
    "verifyContentModesFinalIntegrationSeal",
]
REQUIRED_DEBUG_TASKS = [
    "verifyHistoricalDebugWorkbenchD1R2",
    "verifyHistoricalDebugWorkbenchD2",
    "verifyHistoricalDebugWorkbenchD3",
    "verifyHistoricalDebugWorkbenchD2D3CorrectiveSeal",
    "verifyHistoricalDebugWorkbenchD4",
    "verifyHistoricalDebugWorkbenchD5",
    "verifyHistoricalDebugWorkbenchD6",
    "verifyHistoricalDebugWorkbenchD7",
    "verifyHistoricalDebugWorkbenchD8",
]

errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""

build = text("build.gradle.kts")
devtool = text(".devtool.toml")
registered_tasks = set(re.findall(r'tasks\.register(?:<[^>]+>)?\("([^"]+)"', build))
script_refs = set(re.findall(r'"scripts/([^"]+\.py)"', build)) | set(
    re.findall(r'commandLine\("bash", "scripts/([^"]+\.sh)"', build),
)

for task in REQUIRED_CMOV_TASKS + REQUIRED_DEBUG_TASKS:
    require(task in registered_tasks or task in build, f"missing registered verifier task {task}")
    require(task in build, f"verifier task {task} not wired into a gate")

for script in [
    "verify_content_modes_final_integration.py",
    "verify_parity04_five_client_full_mesh.py",
    "verify_rel_final_devtool_release_runner.py",
    "verify_web_stale_dist_release_integrity.py",
    "verify_ov29_behavioral_validation_release_truth_gates.py",
]:
    require((ROOT / "scripts" / script).is_file(), f"missing exhaustive script {script}")

for target in ["studyflow_android", "studyflow_quality", "studyflow_parity"]:
    require(
        "verifyOv29BehavioralValidationReleaseTruthGates" in devtool,
        f"Devtool graph omits OV29 gate for {target}",
    )

inventory = {
    "schemaVersion": 1,
    "overlay": "OV29",
    "cmovTasks": REQUIRED_CMOV_TASKS,
    "debugTasks": REQUIRED_DEBUG_TASKS,
    "registeredGradleTasks": sorted(registered_tasks),
    "referencedVerifierScripts": sorted(script_refs),
}
out = ROOT / "docs/validation/OV29_EXHAUSTIVE_VERIFIER_INVENTORY.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(inventory, indent=2, sort_keys=True) + "\n", encoding="utf-8")

if errors:
    print("OV29_EXHAUSTIVE_VERIFIER_INVENTORY: FAIL")
    for error in errors:
        print("-", error)
    raise SystemExit(1)
print(f"OV29_EXHAUSTIVE_VERIFIER_INVENTORY: PASS ({len(registered_tasks)} Gradle tasks, {len(script_refs)} scripts)")
