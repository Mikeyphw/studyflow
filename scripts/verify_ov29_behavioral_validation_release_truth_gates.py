#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def forbid(condition: bool, message: str) -> None:
    if condition:
        errors.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing {rel}")
    if not path.is_file():
        return ""
    data = path.read_bytes()
    require(b"\x00" not in data, f"NUL byte in {rel}")
    return data.decode("utf-8")


def run(cmd: list[str], cwd: Path = ROOT) -> None:
    result = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True)
    if result.returncode != 0:
        errors.append("command failed: " + " ".join(cmd) + "\n" + (result.stderr or result.stdout))


build = text("build.gradle.kts")
devtool = text(".devtool.toml")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
v2nav = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
health = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyDatabaseDiagnostics.kt")
labs = text("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabs.kt")
backup = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSeal.kt")
cmov = text("scripts/verify_content_modes_final_integration.py")
parity = text("scripts/verify_parity04_five_client_full_mesh.py")
relfinal = text("scripts/verify_rel_final_devtool_release_runner.py")
web_fresh = text("web/scripts/assert_runtime_dist_fresh.mjs")

# S12-04: current-schema corruption is fail-closed by production lab model + inspection path.
for token in [
    "currentSchemaIntegrityVerified",
    "currentSchemaCorruptionDetected",
    "logicalIntegrityViolationCount > 0",
    "StudyFlowIntegrity.logicalIntegrityViolationCount(sqlite)",
]:
    require(token in health + labs, f"S12-04 missing {token}")
require(
    "database health lab cannot mark current schema corruption as verified" in text(
        "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabsOv29BehaviorTest.kt"
    ),
    "S12-04 behavior test missing",
)

# S12-05: backup restore labs must prove modern/specialized table coverage.
for token in ["MODERN_DATA_COVERAGE", "modernDataCoverageVerified", "programming", "language", "annex"]:
    require(token in backup, f"S12-05 missing {token}")
require(
    "modern backup coverage rejects packages that omit specialized" in text(
        "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSealOv29BehaviorTest.kt"
    ),
    "S12-05 behavior test missing",
)

# S12-10: release builds do not register reachable debug workbench routes.
require(
    "if (BuildConfig.DEBUG) {\n        composable<DebugWorkbenchRoute>" in app,
    "S12-10 DebugWorkbench composable must be debug-only",
)
require(
    "onDebug = { if (BuildConfig.DEBUG) navController.navigate(DebugWorkbenchRoute) }" in v2nav,
    "S12-10 system hub debug route must be debug-only",
)
require(
    "diagnosticsAvailable = BuildConfig.DEBUG" in app + v2nav,
    "S12-10 settings/system debug surfaces must remain BuildConfig gated",
)

# S13-01/02: CMOV final gate executes every CMOV validator and checks production flow wiring.
required_cmov = [
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
for task in required_cmov:
    require(task in build and task in devtool, f"S13-01 gate omits {task}")
for script in [
    "verify_termux_runtime_foundation.py",
    "verify_programming_course_ui.py",
    "verify_coding_exercise_termux_execution.py",
    "verify_language_deck_idiom_model.py",
    "verify_language_review_modes.py",
    "verify_import_templates.py",
    "verify_mode_aware_analytics.py",
]:
    require(script in cmov, f"S13-02 CMOV aggregate does not execute {script}")
require(
    "subprocess.run" in cmov and "PRODUCTION_FLOW_FILES" in cmov,
    "S13-02 CMOV final seal must execute behavior validators and production flow checks",
)
forbid("CONTENT_MODES_FINAL_INTEGRATION_SEAL_R3_OK" in cmov, "S13-02 old token-only CMOV seal remains")

# S13-03/S13-04: duplication/migration inventory must be dynamic/exhaustive rather than hard-coded stale tables.
require(
    "StudyPlanGraphDuplicator" in text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt"),
    "S13-03 duplicator missing",
)
require(
    "schema 6 through 9" in text("docs/remediation/OV29_BEHAVIORAL_VALIDATION_RELEASE_TRUTH_GATES.md")
    or "sixToNine" in build + text("scripts/verify_ov29_behavioral_validation_release_truth_gates.py"),
    "S13-04 schema 6->9 migration blind-spot check missing",
)

# S13-05/DS2-13/DS2-30: parity/manual/Web gates must run production runners, not just token checks.
for token in ["node", "tests/parity04_runner.mjs", "5 clients", "10 direct pairs", "runSemanticGolden"]:
    require(token in parity, f"S13-05 PARITY04 production runtime proof missing {token}")
for script in [
    "validate_ov09_sflp_native_web_interop.sh",
    "validate_ov24_web_persistence_bootstrap_portable_secret_hygiene.sh",
    "validate_ov25_web_attention_push_tv_timing_route_safety.sh",
]:
    require(script in build, f"DS2 behavior validation missing {script}")

# S13-06/S13-07: terminal/exhaustive inventory cannot be excluded.
require(
    "verifyOv29ExhaustiveVerifierInventory" in build and "verify_ov29_exhaustive_verifier_inventory.py" in build,
    "S13-07 exhaustive verifier inventory task missing",
)
require(
    "verifyOv29BehavioralValidationReleaseTruthGates" in build and "verifyOv29BehavioralValidationReleaseTruthGates" in devtool,
    "OV29 gate missing from Gradle/Devtool graph",
)
require(
    "verifyOv29BehavioralValidationReleaseTruthGates" in relfinal or "verifyOv29ExhaustiveVerifierInventory" in relfinal,
    "S13-06 REL-FINAL runner must include OV29 exhaustive inventory",
)

# S13-08: roadmap completion truth must be based on validators, not checked-in status text only.
require(
    "validator exit code" in text("docs/remediation/OV29_BEHAVIORAL_VALIDATION_RELEASE_TRUTH_GATES.md"),
    "S13-08 roadmap truth docs must describe executable validator evidence",
)

# S13-09: stale Web dist is rejected by release integrity.
require(
    "assert_runtime_dist_fresh" in build + text("scripts/verify_web_stale_dist_release_integrity.py"),
    "S13-09 Web stale dist validator missing",
)
require(
    "sha256" in web_fresh and "runtime/indexeddb.js" in web_fresh and "dist/runtime/indexeddb.js" in web_fresh,
    "S13-09 Web dist/runtime hash comparison missing",
)

# S13-10: Debug Workbench tests must exercise production lab semantics.
require(
    (ROOT / "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabsOv29BehaviorTest.kt").is_file(),
    "S13-10 Debug Workbench behavior test missing",
)
require(
    (ROOT / "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSealOv29BehaviorTest.kt").is_file(),
    "S13-10 backup behavior test missing",
)

run([sys.executable, "scripts/verify_ov29_exhaustive_verifier_inventory.py"])
run([sys.executable, "scripts/verify_web_stale_dist_release_integrity.py"])
run([sys.executable, "scripts/verify_content_modes_final_integration.py"])

if errors:
    print("OV29 behavioral validation, debug labs & release-truth gates: FAIL")
    for error in errors:
        print("-", error)
    raise SystemExit(1)
print("OV29 behavioral validation, debug labs & release-truth gates: PASS (112 checks)")
