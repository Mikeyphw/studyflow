#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
REPORT = ROOT / "build/reports/studyflow/tvov26-two-device-acceptance.json"
REQUIRED = {
    "two_devices_classified",
    "signed_release_artifacts_installed",
    "tv_leanback_launch_dpad",
    "phone_controller_launch",
    "fresh_pairing",
    "authenticated_navigation",
    "live_cast_acknowledged",
    "canonical_domain_mutation",
    "tv_restart_resync",
    "phone_restart_resync",
    "stop_cast_waiting_state",
    "revoke_blocks_control",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def verify_evidence(path: Path) -> None:
    require(path.is_file(), f"TVOV26 physical evidence missing: {path}")
    if not path.is_file():
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as error:
        require(False, f"TVOV26 evidence is not valid JSON: {error}")
        return
    require(data.get("overlay") == "TVOV26", "physical evidence overlay identity mismatch")
    require(data.get("status") == "PASS", "physical evidence is not PASS")
    require(data.get("physicalRun") is True, "physical evidence must be a real device run")
    require(data.get("phonePackage") == "com.mikeyphw.studyflow", "phone package evidence mismatch")
    require(data.get("tvPackage") == "com.mikeyphw.studyflow.tv", "TV package evidence mismatch")
    require(data.get("tvVersionCode") == 26, "TV physical evidence versionCode mismatch")
    require(data.get("tvVersionName") == "0.1.0-tv26", "TV physical evidence versionName mismatch")
    checks = {entry.get("id"): entry for entry in data.get("checks", []) if isinstance(entry, dict)}
    require(set(checks) == REQUIRED, "physical evidence required-check set mismatch")
    for check in REQUIRED:
        entry = checks.get(check, {})
        require(entry.get("status") == "PASS", f"physical acceptance check is not PASS: {check}")
        require(bool(str(entry.get("evidence") or "").strip()), f"physical acceptance check lacks evidence: {check}")
    devices = data.get("devices") or {}
    phone = devices.get("phone") or {}
    tv = devices.get("tv") or {}
    require(bool(phone.get("serial")) and bool(tv.get("serial")), "physical evidence must bind both device serials")
    require(phone.get("serial") != tv.get("serial"), "phone and TV evidence serials must differ")
    artifacts = data.get("artifacts") or {}
    installed = data.get("installed") or {}
    for role, installed_key in (("phone", "phoneSha256"), ("tv", "tvSha256")):
        artifact = artifacts.get(role) or {}
        artifact_hash = str(artifact.get("sha256") or "").lower()
        installed_hash = str(installed.get(installed_key) or "").lower()
        require(len(artifact_hash) == 64 and artifact_hash == installed_hash, f"{role} installed artifact hash mismatch")
        path_text = str(artifact.get("path") or "")
        if path_text:
            artifact_path = Path(path_text)
            if artifact_path.is_file():
                require(sha256(artifact_path) == artifact_hash, f"{role} evidence artifact changed after acceptance")


parser = argparse.ArgumentParser()
parser.add_argument("--require-evidence", action="store_true")
parser.add_argument("--report", default=str(REPORT))
args = parser.parse_args()

policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvEndToEndSealPolicy.kt")
policy_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvEndToEndSealPolicyTest.kt")
runner = read("scripts/run_tvov26_two_device_acceptance.py")
main_activity = read("app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt")
app_shell = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
v2_nav = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
manifest = read("app/src/main/AndroidManifest.xml")
cast_store = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvCastIntentStore.kt")
controller = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneController.kt")
controller_screen = read("app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPhoneControllerScreen.kt")
reducer_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducerTest.kt")
tv_build = read("app-tv/build.gradle.kts")
packaging_policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvPackagingPolicy.kt")
build_contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
notes = read("TVOV26_ANDROID_TV_END_TO_END_PHYSICAL_DEVICE_FINAL_SEAL.md")
quality = read("docs/quality/TVOV26_END_TO_END_PHYSICAL_DEVICE_FINAL_SEAL_CONTRACTS.md")
readme = read("README.md")

# Every module using the shared Android-library convention must provide the
# consumer ProGuard file that AndroidConvention.kt declares unconditionally.
android_library_modules: list[Path] = []
for build_file in ROOT.rglob("build.gradle.kts"):
    if any(part in {"build", ".gradle", ".idea"} for part in build_file.parts):
        continue
    build_text = build_file.read_text(encoding="utf-8")
    if 'id("studyflow.android.library")' in build_text:
        android_library_modules.append(build_file.parent)
for module in android_library_modules:
    rules_file = module / "consumer-rules.pro"
    require(
        rules_file.is_file(),
        f"Android library convention requires consumer-rules.pro: {module.relative_to(ROOT)}",
    )
require(
    (ROOT / "core/tvlink/consumer-rules.pro").is_file(),
    ":core:tvlink Release packaging requires consumer-rules.pro",
)

for token in (
    'const val overlay = "TVOV26"',
    '"two_devices_classified"',
    '"fresh_pairing"',
    '"live_cast_acknowledged"',
    '"canonical_domain_mutation"',
    '"tv_restart_resync"',
    '"phone_restart_resync"',
    '"revoke_blocks_control"',
    'physicalRun &&',
    'completedChecks == requiredChecks',
):
    require(token in policy, f"TVOV26 fail-closed policy missing token: {token}")
for token in ("source-only or skipped device run can never seal", "every physical check and exact installed artifacts are mandatory"):
    require(token in policy_test, f"TVOV26 policy test missing {token}")
for token in (
    "STUDYFLOW_PHONE_SERIAL",
    "STUDYFLOW_TV_SERIAL",
    "android.software.leanback",
    "uiautomator",
    "Pairing code:",
    "Start cast",
    "Cast status: LIVE",
    "domain-persisted",
    "force-stop",
    "Stop cast",
    "pairing=revoked",
    "installed_apk_sha256",
    "TVOV26_TWO_DEVICE_ACCEPTANCE: PASS",
):
    require(token in runner, f"TVOV26 real-device runner missing token: {token}")
require("SKIP" not in runner, "TVOV26 physical runner must never skip missing devices")
for token in (
    'const val ACTION_OPEN_TV_CONTROLLER = "com.mikeyphw.studyflow.action.TV_CONTROLLER"',
    "tvControllerLaunchRequested",
):
    require(token in main_activity, f"phone controller deterministic launch wiring missing: {token}")
require("tvControllerLaunchRequested" in app_shell, "StudyFlowApp must carry TV-controller launch intent")
require("navController.navigate(TvControllerRoute)" in v2_nav, "TV controller launch action must reach TvControllerRoute")
require('android:name="com.mikeyphw.studyflow.action.TV_CONTROLLER"' in manifest, "phone manifest missing TV controller action")
for token in ("StudyFlowTvCastIntentStore", "studyflow_tv_cast_intent", "KEY_PLAN_ID", "STOP_PENDING"):
    require(token in cast_store, f"TVOV26 cast-intent persistence missing token: {token}")
for token in (
    "castIntentStore.saveActive(planId)",
    "castIntentStore.saveStopPending(castPlanId)",
    "resumePersistedCastIfEligible()",
    'lastMessage = "cast-resumed-after-restart"',
    "castIntentStore.clear()",
):
    require(token in controller, f"phone restart cast convergence missing token: {token}")
require("SharedPreferencesStudyFlowTvCastIntentStore(context)" in controller_screen, "controller UI must use private cast-intent store")
require(('versionCode = 26' in tv_build and 'versionName = "0.1.0-tv26"' in tv_build) or ('versionCode = 31' in tv_build and 'versionName = "1.0.0-tv31"' in tv_build), "TV package must retain TVOV26 compatibility or TVOV31 terminal identity")
require((('const val versionCode = 26' in packaging_policy and 'const val versionName = "0.1.0-tv26"' in packaging_policy) or ('const val versionCode = 31' in packaging_policy and 'const val versionName = "1.0.0-tv31"' in packaging_policy)), "TV packaging policy must retain TVOV26 compatibility or advance to TVOV31 terminal identity")
require(re.search(r'currentTvOverlay = "TVOV(?:2[6-9]|3[01])"', build_contract) is not None, "TV build contract must identify TVOV26 or newer through TVOV31")
manifest_packaging_verifier = read("scripts/verify_tvov19_manifest_packaging.py")
require(('VERSION_CODE = "26"' in manifest_packaging_verifier) or ('VERSION_CODE = "31"' in manifest_packaging_verifier), "packaged-artifact verifier must inspect the active TV package versionCode")
require('VERSION_CODE = "25"' not in manifest_packaging_verifier, "packaged-artifact verifier must not retain stale TVOV25 versionCode")
for token in (
    "assertEquals(StudyFlowTvPlaybackState.READY, continuity.playbackState)",
    "assertEquals(practiceBeforeAnswer, state.questionPractice)",
    "assertFalse(state.questionPractice.explanationVisible)",
):
    require(token in reducer_test, f"TVOV26 reducer regression contract missing token: {token}")
for token in (
    'tasks.register<Exec>("verifyStudyFlowTvEndToEndPhysicalSealContracts")',
    'tasks.register<Exec>("verifyStudyFlowTvPhysicalTwoDeviceAcceptance")',
    'tasks.register<Exec>("verifyStudyFlowTvFinalProductSeal")',
):
    require(token in root_gradle, f"TVOV26 Gradle wiring missing {token}")
require("verifyStudyFlowTvAllContracts" in devtool, "studyflow_tv Devtool target missing aggregate TV contract gate")
tv_aggregate = root_gradle.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1]
tv_aggregate = tv_aggregate.split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
for token in ("verifyStudyFlowTvEndToEndPhysicalSealContracts", "verifyStudyFlowTvRoadmapInstructions"):
    require(token in tv_aggregate, f"aggregate TV gate missing TVOV26 contract task: {token}")
require("[targets.studyflow_tv_release]" in devtool, "TV release target missing")
routine_start = devtool.index("[targets.studyflow_tv]")
routine_end = devtool.index("[targets.studyflow_tv_release]", routine_start)
routine_tv = devtool[routine_start:routine_end]
require("verifyStudyFlowTvPhysicalTwoDeviceAcceptance" not in routine_tv, "routine TV target must not pretend physical evidence exists")
require("TVOV26  End-to-end physical-device final seal [implemented]" in roadmap, "roadmap must mark TVOV26 implementation complete")
require("TVOV26  End-to-end physical-device final seal [implemented]" in rules, "working rules must mark TVOV26 implementation complete")
require("TVOV00 through TVOV26" in rules, "working rules must record complete TV chain")
for token in ("fail-closed", "two real devices", "optimized Release", "no skip", "physical evidence"):
    require(token.lower() in notes.lower(), f"TVOV26 implementation note incomplete: {token}")
    require(token.lower() in quality.lower(), f"TVOV26 quality contract incomplete: {token}")
require("TVOV26" in readme and "two-device" in readme.lower(), "README missing TVOV26 final-seal summary")

if args.require_evidence:
    verify_evidence(Path(args.report).resolve())

if FAILURES:
    print("TVOV26_END_TO_END_PHYSICAL_SEAL: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)
print("TVOV26_END_TO_END_PHYSICAL_SEAL: PASS")
