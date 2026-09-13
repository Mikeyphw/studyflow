#!/usr/bin/env python3
"""StudyFlow v4 Gradle/Devtool task-graph optimization contract.

This gate protects validation strength while rejecting the expensive historical shape:
repeated split Gradle launches, repeated TV verifiers in every phase, focused Attention
Web suites in the terminal lane, explicit compile/resource phases already implied by
assemble/test/lint, and duplicated Desktop Exec verifier implementations.
"""
from __future__ import annotations

import re
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def fail(message: str) -> None:
    FAILURES.append(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def target(config: dict, name: str) -> dict:
    value = config.get("targets", {}).get(name)
    require(isinstance(value, dict), f"missing Devtool target {name}")
    return value if isinstance(value, dict) else {}


def phase_list(t: dict, name: str) -> list[str]:
    phases = t.get("validation", {}).get("phases", {})
    value = phases.get(name, []) if isinstance(phases, dict) else []
    return list(value) if isinstance(value, list) else []


def task_list(t: dict, name: str) -> list[str]:
    tasks = t.get("tasks", {})
    value = tasks.get(name, []) if isinstance(tasks, dict) else []
    return list(value) if isinstance(value, list) else []


devtool_text = text(".devtool.toml")
try:
    config = tomllib.loads(devtool_text)
except tomllib.TOMLDecodeError as exc:
    fail(f"invalid .devtool.toml: {exc}")
    config = {}

project = config.get("project", {})
workspace = config.get("workspace", {})
require(project.get("runner") == "android", "[project].runner must use the canonical Android runner")
require(project.get("platform") == "android", "[project].platform must be android")
require("kind" not in project, "legacy [project].kind must be removed")
require("default_target" not in project, "legacy [project].default_target must be removed")
require(workspace.get("default_target") == "studyflow_android", "workspace default_target must be studyflow_android")
groups = workspace.get("groups", {})
require(groups.get("clients") == ["studyflow_android", "studyflow_desktop", "studyflow_web", "studyflow_tv"], "workspace clients group drifted")
require("studyflow_tv_release" in groups.get("release", []), "workspace release group must contain the TV release target")
require(groups.get("quality") == ["studyflow_quality"], "workspace quality group must isolate repository-wide formatting")
require(groups.get("everything") == ["@clients", "@release", "@quality"], "workspace everything group must include quality")
scheduler = workspace.get("scheduler", {})
require(scheduler.get("parallel") is True, "workspace scheduler should opt into safe parallel DAG execution")
require(scheduler.get("max_gradle_nodes") == 1, "workspace scheduler must cap Gradle-heavy concurrency at one")

android = target(config, "studyflow_android")
desktop = target(config, "studyflow_desktop")
tv = target(config, "studyflow_tv")
tv_release = target(config, "studyflow_tv_release")
quality = target(config, "studyflow_quality")
parity = target(config, "studyflow_parity")
web = target(config, "studyflow_web")

for name, t, runner, platform in (
    ("studyflow_android", android, "android", "android"),
    ("studyflow_desktop", desktop, "gradle", "desktop"),
    ("studyflow_tv", tv, "android", "tv"),
    ("studyflow_tv_release", tv_release, "android", "tv"),
    ("studyflow_quality", quality, "gradle", "shared"),
    ("studyflow_parity", parity, "gradle", "shared"),
    ("studyflow_web", web, "node", "web"),
):
    require(t.get("runner") == runner, f"{name} must use canonical runner={runner}")
    require(t.get("platform") == platform, f"{name} must declare platform={platform}")
    require("type" not in t, f"{name} must not retain legacy target.type")

for name, t in (("android", android), ("desktop", desktop), ("tv", tv), ("tv_release", tv_release), ("quality", quality), ("parity", parity)):
    validation = t.get("validation", {})
    require(validation.get("split_gradle") is False, f"{name} validation must coalesce Gradle with split_gradle=false")
    require(validation.get("parallel") is True, f"{name} validation must enable Gradle parallel execution")
    require(validation.get("memory_guard_mb") == 0, f"{name} validation must not add a second memory guard")
    for phase in ("dependencies", "compile", "resources"):
        require(phase_list(t, phase) == [], f"{name} redundant {phase} phase must be explicitly disabled")

for name, t in (("android", android), ("tv", tv), ("tv_release", tv_release)):
    gradle = t.get("gradle", {})
    require(gradle.get("max_workers") == 4, f"{name} Gradle max_workers must be 4")
    require(gradle.get("parallel") is True, f"{name} Gradle parallel flag must be true")
    args = gradle.get("args", [])
    require("-Dorg.gradle.parallel=true" not in args, f"{name} must not duplicate parallel policy in raw Gradle args")
    validation = t.get("validation", {})
    require(validation.get("gradle_heap_mb") == 2048, f"{name} validation heap must be 2048 MiB")
    require(validation.get("gradle_metaspace_mb") == 768, f"{name} validation metaspace must be 768 MiB")

require(phase_list(android, "preflight") == ["verifyStudyFlowAndroidPreflightContracts"], "Android preflight must be one aggregate gate")
require(phase_list(tv, "preflight") == ["verifyStudyFlowTvAllContracts"], "TV preflight must be one aggregate gate")
require(phase_list(parity, "unit-test") == ["verifyRelFinalTerminalSeal"], "parity unit-test lane must use only the terminal seal")
require(phase_list(desktop, "package") == [], "Desktop must not launch a redundant package/compile phase")
require("verifyOv20FinalIntegration" not in phase_list(desktop, "preflight"), "Desktop routine preflight must not rerun the overlapping OV20 meta-seal")

android_test = task_list(android, "test")
android_unit = phase_list(android, "unit-test")
require(android_test.count("verifyAttentionRoadmapPostsealCompletion") == 1, "Android default test lane must contain one terminal Attention gate")
require(android_unit.count("verifyAttentionRoadmapPostsealCompletion") == 1, "Android resumable unit-test lane must contain one terminal Attention gate")
for focused in (
    "verifyAtt01UnifiedAttentionFoundation", "verifyAtt02AndroidAttentionSystem", "verifyAtt03AndroidWidgetSuite",
    "verifyAtt04DesktopAttentionRuntime", "verifyAtt05WebPwaAttentionRuntime", "verifyAtt06TvAttentionExperience",
    "verifyAtt07CrossDeviceDeliveryRouter", "verifyAtt08CompactCompanionAttention", "verifyAtt09CalendarScheduleExportAttention",
    "verifyAtt10QuietHoursFocusProfilesEscalationPolicy", "verifyAtt11NotificationReliabilityEvidenceAudit",
    "verifyAtt12AttentionReleaseFinalSeal",
):
    require(focused not in android_test and focused not in android_unit, f"focused Attention gate leaked into default terminal lane: {focused}")
for predecessor in (
    "verifySflp01ProtocolBaseline", "verifySflp02Sflp03JournalReconciliation", "verifySflp04Sflp05IdentityTransport",
    "verifySflp06Sflp07TopologyOfflineQueue", "verifySflp08Sflp09AndroidDesktopIntegration",
    "verifySflp10Sflp11WebTvGeneralization", "verifySflp12Sflp13WebTvHandoff",
    "verifySflp14Sflp15LiveSessionProjection", "verifySflp16Sflp17ControlInputDelegation",
    "verifySflp18Sflp19NotificationMediaSync", "verifySflp20Sflp21ShareImportConflict",
    "verifySflp22Sflp23AccountBackupValidation",
):
    require(predecessor not in android_test and predecessor not in android_unit, f"SFLP predecessor should be owned by verifySflp24FinalReleaseSeal: {predecessor}")
require(android_test.count("verifySflp24FinalReleaseSeal") == 1, "Android default lane must contain one SFLP24 seal")
require(android_unit.count("verifySflp24FinalReleaseSeal") == 1, "Android resumable lane must contain one SFLP24 seal")

require(task_list(android, "lint") == [":app:lintDebug", ":app:lintRelease"], "Android lint must use app-level dependency lint without repository-wide formatting")
require(phase_list(android, "lint") == [":app:lintDebug", ":app:lintRelease"], "Android resumable lint lane drifted")
require(task_list(desktop, "lint") == [], "Desktop routine target must not run repository-wide formatting")
require(phase_list(desktop, "lint") == [], "Desktop routine lint phase must stay empty")
require(task_list(tv, "lint") == [":app-tv:lintDebug"], "routine TV lint must remain Debug-only")
require(phase_list(tv, "lint") == [":app-tv:lintDebug"], "routine TV resumable lint must remain Debug-only")
require(task_list(tv_release, "lint") == [":app-tv:lintRelease"], "TV release target must own Release lint")
require(phase_list(tv_release, "lint") == [":app-tv:lintRelease"], "TV release validation must own Release lint")
require(task_list(quality, "lint") == ["verifyFormatting"], "quality target must own repository-wide formatting")
require(phase_list(quality, "lint") == ["verifyFormatting"], "quality validation must own repository-wide formatting")
require(android.get("validation", {}).get("affected_module_lint") is False, "Android affected_module_lint must be disabled for the two-variant app-level lint lane")
require(tv.get("validation", {}).get("affected_module_lint") is False, "TV affected_module_lint must be disabled for the two-variant app-level lint lane")

build = text("build.gradle.kts")
require(task_list(tv, "build") == ["verifyStudyFlowTvAllContracts", ":app-tv:assembleDebug"], "routine TV build must stay debug-only and aggregate contracts")
require("verifyStudyFlowTvOptimizedReleaseArtifact" not in task_list(tv, "build"), "routine TV target must not run release-artifact gates")
require(task_list(tv_release, "build") == ["verifyStudyFlowTvAllContracts", "verifyStudyFlowTvFinalReleaseSeal"], "TV release build must use aggregate contracts plus the final release seal")
require("verifyStudyFlowTvFinalReleaseSeal" in task_list(tv_release, "build"), "TV release target must own the final release seal")
release_seal = build.split('tasks.register<Exec>("verifyStudyFlowTvFinalReleaseSeal")', 1)[1]
release_seal = release_seal.split('tasks.register<Exec>("verifySflp01ProtocolBaseline")', 1)[0]
for owned in ("verifyStudyFlowTvOptimizedReleaseArtifact", "verifyStudyFlowTvPackagedArtifacts"):
    require(owned in release_seal, f"TV final release seal must own {owned}")
require(phase_list(tv, "package") == [":app-tv:assembleDebug"], "routine TV package phase must only assemble Debug")

require('tasks.register("verifyAttentionAllContracts")' in build, "Attention contract aggregate missing")
require('tasks.register("verifyStudyFlowWebReleaseGate")' in build, "shared Web release aggregate missing")
require('tasks.register<Exec>("verifyStudyFlowWebFullRegression")' in build, "shared Web regression task missing")
require('environment("STUDYFLOW_EXTERNAL_RUNTIME_PARITY", "1")' in build, "shared Web regression must reuse external runtime parity")
require('tasks.register("verifyCpovAllContracts")' in build, "CPOV aggregate missing")
require('tasks.register("verifyStudyFlowDesktopAllContracts")' in build, "Desktop aggregate missing")
require('tasks.register("verifyStudyFlowTvAllContracts")' in build, "TV aggregate missing")
require('tasks.register("verifyStudyFlowAndroidPreflightContracts")' in build, "Android preflight aggregate missing")
optimizer_block = build.split('tasks.register<Exec>("verifyStudyFlowGradleTaskGraphOptimizationV4")', 1)[1].split('tasks.register("checkAll")', 1)[0]
require("dependsOn(" not in optimizer_block, "optimizer verifier must remain static-only and not execute client preflight graphs")
android_preflight = build.split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[1].split('tasks.register<Exec>("verifyStudyFlowGradleTaskGraphOptimizationV4")', 1)[0]
require("verifyOv20FinalIntegration" not in android_preflight, "Android routine preflight must not rerun the overlapping OV20 meta-seal")
require('tasks.register("verifyFinalAppAcceptanceSeal")' in build, "final acceptance compatibility alias must be a dependency-only task")
require('tasks.register<Exec>("verifyFinalAppAcceptanceSeal")' not in build, "final acceptance compatibility alias must not duplicate the Python Exec")
require("find . -path '*/build/reports/lint*'" not in build, "repository contracts must not delete lint reports and defeat incremental lint")
phase15 = text("scripts/verify_phase_15.py")
require("Formatting gate mirror" not in phase15, "Phase 15 must not duplicate the canonical formatting scan")
require("studyflow_quality must own the canonical formatting gate" in phase15, "Phase 15 must verify canonical formatting ownership")

for rel in ("app/build.gradle.kts", "app-tv/build.gradle.kts"):
    require("checkDependencies = true" in text(rel), f"{rel} must enable dependency lint for app-level lint aggregation")

package_json = text("web/package.json")
require("tsc -p tsconfig.json --noEmit && node scripts/compile_runtime.mjs" not in package_json, "Web scripts must not compile TypeScript twice")
require("node scripts/compile_runtime.mjs" in package_json, "Web runtime compiler must remain authoritative")

desktop_build = text("desktopApp/build.gradle.kts")
for alias in (
    "verifyAtt04DesktopAttentionContracts", "verifyAtt04DesktopAttentionRuntime", "verifyDesktopDov12QuestionPracticeExecution",
    "verifyDesktopDov13MockExamStudio", "verifyDesktopDov14ReviewsSrsErrorNotebook", "verifyDesktopDov15LibraryFilesMaterialViewers",
    "verifyDesktopDov16WritingWorkspace", "verifyDesktopDov17ContentModesExecution", "verifyDesktopDov18PublicExamParity",
    "verifyDesktopDov19ReadinessCoachingReports", "verifyDesktopDov20PortabilityBackupMigration", "verifyDesktopDov21EnPtBrLocalization",
    "verifyDesktopDov22SystemLightDarkAppearance", "verifyDesktopDov23NativeDesktopIntegration", "verifyDesktopDov24FullApplicableParitySeal",
):
    require(f'tasks.register<Exec>("{alias}")' not in desktop_build, f"Desktop duplicate Exec implementation retained: {alias}")
    require(f'tasks.register("{alias}")' in desktop_build, f"Desktop compatibility alias missing: {alias}")

# Regression guard for the real Termux failure that triggered v4.
tv_test = text("app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionRuntimeTest.kt")
require(tv_test.count("localPeer = tv") >= 2, "TV Attention runtime tests must pass the required localPeer constructor argument")

# Avoid reintroducing the compiler warnings seen in the failed real Gradle run.
android_delivery = text("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt")
desktop_runtime = text("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt")
require("compactDelivery && transportedRoute != null" not in android_delivery, "Android compact route retained an always-true null condition")
require("&& transportedRoute != null" not in desktop_runtime, "Desktop compact route retained an always-true null condition")

if FAILURES:
    print("STUDYFLOW_GRADLE_TASK_GRAPH_OPTIMIZATION_V4: FAIL")
    for item in FAILURES:
        print(f"- {item}")
    raise SystemExit(1)
print("STUDYFLOW_GRADLE_TASK_GRAPH_OPTIMIZATION_V4: PASS")
