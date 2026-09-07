#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def resource_names(rel: str) -> set[str]:
    path = ROOT / rel
    require(path.is_file(), f"missing resource file: {rel}")
    if not path.is_file():
        return set()
    root = ET.parse(path).getroot()
    return {
        child.attrib["name"]
        for child in root
        if child.tag in {"string", "plurals", "string-array"} and "name" in child.attrib
    }


def run_verifier(rel: str) -> None:
    path = ROOT / rel
    require(path.is_file(), f"missing verifier: {rel}")
    if not path.is_file():
        return
    result = subprocess.run(
        [sys.executable, str(path)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        tail = "\n".join(result.stdout.strip().splitlines()[-8:])
        FAILURES.append(f"{rel} failed ({result.returncode}):\n{tail}")


# Stable target baseline and durable repository entry points.
wrapper = read("gradle/wrapper/gradle-wrapper.properties")
devtool = read(".devtool.toml")
build_gradle = read("build.gradle.kts")
require("gradle-9.7.1-bin.zip" in wrapper, "Gradle wrapper must remain pinned to 9.7.1")
require('ndk_version = "29.0.14206865"' in devtool, "Devtool Android target must remain pinned to NDK 29.0.14206865")
require('tasks.register<Exec>("verifyOv19PromiseAudit")' in build_gradle, "OV19 Gradle verifier task missing")
require('tasks.register<Exec>("verifyOv20FinalIntegration")' in build_gradle, "OV20 Gradle verifier task missing")
require(
    build_gradle.count('"verifyOv19PromiseAudit"') >= 1
    and build_gradle.count('"verifyOv20FinalIntegration"') >= 1,
    "checkAll must retain OV19/OV20 verifier dependencies",
)
require(devtool.count('"verifyOv19PromiseAudit"') >= 2, "Devtool must retain OV19 in default/preflight validation")
require(devtool.count('"verifyOv20FinalIntegration"') >= 2, "Devtool must retain OV20 in default/preflight validation")
require(
    'build = ["prepareDevtoolValidationArtifacts", ":app:assembleDebug"]' in devtool,
    "ordinary Devtool build must not create unsigned Release outputs",
)
require(
    'package = [\n  "prepareDevtoolValidationArtifacts",\n  ":app:assembleDebug",\n]' in devtool,
    "ordinary Devtool package phase must be Debug-only",
)
for token in (
    'tasks.register<Delete>("prepareDevtoolValidationArtifacts")',
    'layout.projectDirectory.dir("app/build/outputs/apk/release")',
    'layout.projectDirectory.dir("app/build/outputs/bundle/release")',
    'tasks.register("verifyFinalReleaseHostGate")',
    '":app:assembleRelease"',
    '":app:bundleRelease"',
):
    require(token in build_gradle, f"Release-validation boundary missing: {token}")

# Final documentation/release-evidence boundary.
readme = read("README.md")
changelog = read("CHANGELOG.md")
matrix = read("docs/roadmap/OV01_OV20_PUBLIC_EXAM_COMPLETION_MATRIX.md")
quality = read("docs/quality/OV20_FINAL_INTEGRATED_VALIDATION.md")
require("OV20 public-exam promise audit and integrated validation seal implemented" in readme, "README OV20 status missing")
require("OV20 final integrated public-exam validation seal" in changelog, "CHANGELOG OV20 entry missing")
for token in (
    "OV01",
    "OV10",
    "OV19",
    "OV20",
    "no claim of connected/manual device acceptance",
    "`--task` defines an exclusive validation plan",
):
    require(token.lower() in (matrix + quality).lower(), f"OV20 docs missing release/completion token: {token}")

# Literal promise-audit invariants retained after OV19/OV20.
schedule = read("feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt")
dialogs = read("feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt")
workspace = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
pre_edital = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPreEdital.kt")
runbook = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbook.kt")

for token in (
    "subjectId = draft.subjectId",
    "topicId = draft.topicId",
    "scheduledStartEpochMillis = draft.scheduledStartEpochMillis",
    "StudyStrategyType.entries.forEach",
    "scheduleSearchQuery",
    "showPastCalendarActivities",
):
    require(token in schedule, f"final schedule promise missing: {token}")
require("StudyStrategyType.entries.take(4)" not in schedule, "all seven strategy types must remain reachable")
require("enumValueOrDefault(typeName, StudyActivityType.THEORY)" in dialogs, "manual activity type restore must be safe")
require("DeleteOverflowMenu" in schedule and "confirm_delete_title" in schedule, "schedule delete confirmation must remain explicit")
require("StudyFlowSubjectAccordion" in workspace, "signal-first concurso subject cards missing")
require("show_dashboard_details" in workspace, "detailed edital matrix disclosure missing")
require("R.string.edital_matrix_row" not in workspace, "raw debug-like edital matrix row must not return")
require(
    "target?.confirmedExamDateIso," not in pre_edital,
    "unnecessary StudyTarget value safe-call regression returned",
)
require("PublicExamDateResolver.officialCountdownDate(plan, target)" in runbook, "runbook must use canonical official countdown resolver")

# Workspace lint-warning closure: count-bearing presentation strings must remain real plurals
# and their Compose call sites must select quantities observably.
workspace_strings = read("feature/workspace/src/main/res/values/strings.xml")
workspace_pt_strings = read("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
workspace_plan = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
workspace_practice = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
workspace_progress = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
plural_keys = (
    "metric_accuracy_evidence",
    "metric_schedule_evidence",
    "metric_goal_evidence",
    "pre_edital_cockpit_message",
    "pre_edital_cockpit_stale_message",
    "pre_edital_cockpit_unknown_date_message",
    "pre_edital_cockpit_missing_reference_message",
    "bb_mock_exam_reference",
    "bb_mock_exam_section",
    "bb_mock_exam_missing",
    "question_quota_next_7_days",
    "question_quota_daily",
    "question_quota_summary",
    "question_quota_subject_target",
    "dashboard_subject_questions",
)
for key in plural_keys:
    require(f'<plurals name="{key}">' in workspace_strings, f"workspace EN plural missing: {key}")
    require(f'<plurals name="{key}">' in workspace_pt_strings, f"workspace pt-BR plural missing: {key}")
    require(f'<string name="{key}">' not in workspace_strings, f"workspace EN count string regressed from plurals: {key}")
    require(f'<string name="{key}">' not in workspace_pt_strings, f"workspace pt-BR count string regressed from plurals: {key}")
require('<plurals name="pre_edital_reference_age_days">' in workspace_strings, "workspace EN reference-age plural missing")
require('<plurals name="pre_edital_reference_age_days">' in workspace_pt_strings, "workspace pt-BR reference-age plural missing")
require(workspace_pt_strings.count('<item quantity="many">') >= 16, "workspace pt-BR plural many forms missing")
for key in (
    "pre_edital_cockpit_message",
    "pre_edital_cockpit_stale_message",
    "pre_edital_cockpit_unknown_date_message",
    "pre_edital_cockpit_missing_reference_message",
    "dashboard_subject_questions",
):
    require(f"R.plurals.{key}" in workspace_plan, f"Plan workspace plural call missing: {key}")
for key in ("bb_mock_exam_reference", "bb_mock_exam_section", "bb_mock_exam_missing"):
    require(f"R.plurals.{key}" in workspace_practice, f"Practice workspace plural call missing: {key}")
for key in (
    "metric_accuracy_evidence",
    "metric_schedule_evidence",
    "metric_goal_evidence",
    "question_quota_next_7_days",
    "question_quota_daily",
    "question_quota_summary",
    "question_quota_subject_target",
):
    require(f"R.plurals.{key}" in workspace_progress, f"Progress workspace plural call missing: {key}")

# EN / pt-BR parity on the two surfaces changed by the promise audit.
for module in ("feature/schedule", "feature/workspace"):
    en = resource_names(f"{module}/src/main/res/values/strings.xml")
    pt = resource_names(f"{module}/src/main/res/values-pt-rBR/strings.xml")
    require(en == pt, f"{module} EN/pt-BR resource keys differ: EN-only={sorted(en-pt)[:8]} PT-only={sorted(pt-en)[:8]}")

# Source/static specialization + historical architecture contracts. Every specialization verifier
# must remain present. The highest-level seals below are executed to avoid recursively repeating
# dozens of nested validators during every overlay apply; the complete individual chain is run when
# OV20 itself is constructed/audited.
ov_verifiers = [f"scripts/verify_ov{n:02d}_{name}.py" for n, name in [
    (1, "today_priority"), (2, "evidence_readiness"), (3, "responsive_layout"),
    (4, "navigation_progress"), (5, "metric_truthfulness"), (6, "schedule_intelligence"),
    (7, "portable_studyplan_resources"), (8, "import_diff_rollback"), (9, "pre_edital_cockpit"),
    (10, "bb_agente_comercial_preset"), (11, "writing_workspace"), (12, "bb_mock_exam"),
    (13, "activity_deep_links"), (14, "study_phases"), (15, "reference_freshness"),
    (16, "question_quota"), (17, "readable_schedule"), (18, "public_exam_release_seal"),
    (19, "promise_audit"),
]]
for verifier in ov_verifiers:
    require((ROOT / verifier).is_file(), f"missing specialization verifier: {verifier}")

verifiers = [
    "scripts/verify_ov19_promise_audit.py",
    "scripts/verify_bh01_08_bug_hunt_audit.py",
    "scripts/verify_cpov00_toolchain.py",
    "scripts/verify_cpov13_public_exam_release_seal.py",
    "scripts/verify_cpov_roadmap_reconciliation.py",
    "scripts/verify_debug_workbench_d8.py",
    "scripts/verify_phase_15.py",
    "scripts/verify_phase_60_61.py",
    "scripts/verify_phase_62.py",
    "scripts/verify_phase_63_64.py",
    "scripts/verify_post_phase_64_final_validation.py",
    "scripts/verify_final_app_acceptance.py",
    "scripts/verify_full_app_final_user_readiness.py",
]
for verifier in verifiers:
    run_verifier(verifier)


# Navigation payloads must stay identifier/small-enum only. Mock Studio launch state is a small enum, not a Boolean.
routes = read("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
nav = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
require("enum class PracticeWorkspaceEntry" in routes, "PracticeWorkspaceEntry enum missing")
require(
    "val entry: PracticeWorkspaceEntry = PracticeWorkspaceEntry.DEFAULT" in routes,
    "Practice route entry enum missing",
)
require("openMockStudio: Boolean" not in routes, "Practice route must not carry Boolean launch state")
require(
    "entry = PracticeWorkspaceEntry.MOCK_EXAM_STUDIO" in nav,
    "Mock Studio deep link is not enum-backed",
)

if FAILURES:
    print("OV20_FINAL_INTEGRATION: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)
print(f"OV20_FINAL_INTEGRATION: PASS ({len(ov_verifiers)} OV verifier-presence guards + {len(verifiers)} executed seals + final invariants)")
