#!/usr/bin/env python3
from __future__ import annotations

import re
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


def tv_target_section(devtool: str) -> str:
    start = devtool.find("[targets.studyflow_tv]")
    require(start >= 0, "missing studyflow_tv target")
    if start < 0:
        return ""
    end = devtool.find("[targets.studyflow_android.validation.phase_profiles.preflight]", start)
    return devtool[start:] if end < 0 else devtool[start:end]

focus_contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/focus/StudyFlowTvFocusContract.kt")
focus_view = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/focus/StudyFlowTvFocusView.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
focus_background = read("app-tv/src/main/res/drawable/studyflow_tv_focus_background.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
focus_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/focus/StudyFlowTvFocusPolicyTest.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
app_build = read("app-tv/build.gradle.kts")
build = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV04_FOCUS_PRIMITIVES_CONTRACTS.md")
notes = read("TVOV04_ANDROID_TV_FOCUS_PRIMITIVES.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for rel in (
    "app-tv/src/main/res/values/strings.xml",
    "app-tv/src/main/res/drawable/studyflow_tv_focus_background.xml",
):
    ET.fromstring(read(rel))

for token in (
    "enum class StudyFlowTvRemoteCommand",
    "UP",
    "DOWN",
    "LEFT",
    "RIGHT",
    "SELECT",
    "BACK",
    "PLAY_PAUSE",
    "enum class StudyFlowTvFocusRole",
    "PRIMARY_ACTION",
    "NAVIGATION_ITEM",
    "CONTENT_CARD",
    "QUESTION_CHOICE",
    "DIALOG_ACTION",
    "data class StudyFlowTvFocusItem",
    "object StudyFlowTvRemoteKeyMapper",
    "fun commandForKeyCode",
    "keyCodeDpadCenter = 23",
    "keyCodeEnter = 66",
    "object StudyFlowTvFocusPolicy",
    "const val minimumFocusTargetDp = 64",
    "const val visibleFocusRequired = true",
    "const val deterministicInitialFocusRequired = true",
    "const val restoresFocusAfterNavigation = true",
    "const val directionalNavigationMustNotWrap = true",
    "fun initialFocusId",
    "fun nextFocusId",
):
    require(token in focus_contract, f"focus contract missing token: {token}")

for token in (
    "fun View.applyStudyFlowTvDpadFocus()",
    "isFocusable = true",
    "isFocusableInTouchMode = false",
    "minimumWidth = maxOf",
    "minimumHeight = maxOf",
    "setBackgroundResource(R.drawable.studyflow_tv_focus_background)",
):
    require(token in focus_view, f"focus view helper missing token: {token}")

for token in (
    '<selector xmlns:android="http://schemas.android.com/apk/res/android">',
    'android:state_focused="true"',
    'android:width="3dp"',
    'android:color="#FFFFFF"',
):
    require(token in focus_background, f"focus background missing visible-focus token: {token}")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "com.google.android.gms.cast",
    "ComponentActivity",
    "@Composable",
):
    require(forbidden not in focus_contract + focus_view + app_build + activity, f"TVOV04 focus primitives must stay dependency-budgeted; found {forbidden}")

for token in (
    "tvActionButton",
    "applyStudyFlowTvDpadFocus()",
    "View.generateViewId()",
    "nextFocusRightId",
    "nextFocusLeftId",
    "post { primaryAction.requestFocus() }",
    "R.string.tv_app_primary_action",
    "R.string.tv_app_pair_phone_action",
    "R.string.tv_app_practice_action",
):
    require(token in activity, f"TV Activity must expose deterministic D-pad focus token: {token}")
for token in (
    "tv_app_primary_action",
    "tv_app_pair_phone_action",
    "tv_app_practice_action",
    "tv_home_section_title",
):
    require(token in strings, f"TV strings missing token: {token}")

for token in (
    "dpadFocusPrimitivesIntroduced = true",
    "focusPrimitivesRequireNoComposeRuntime = true",
    "minimumFocusTargetDp = 64",
    "deterministicInitialFocusRequired = true",
):
    require(token in contract, f"TV build contract missing focus token: {token}")
    require(token.split(" = ")[0] in build_test, f"TV build contract test missing focus assertion for {token}")

for token in (
    "initialFocusIsDeterministicAndSkipsDisabledItems",
    "dpadNavigationMovesAcrossRowsAndColumnsWithoutWrapping",
    "selectBackAndPlayPauseKeepCurrentFocus",
    "androidRemoteKeyCodesMapToSmallCommands",
    "StudyFlowTvRemoteCommand.RIGHT",
    "StudyFlowTvRemoteCommand.DOWN",
    "StudyFlowTvRemoteKeyMapper.keyCodeDpadCenter",
):
    require(token in focus_test, f"focus policy unit test missing token: {token}")

require('versionName = "0.1.0-tv08"' in app_build, "TV app versionName must mark the current TVOV08 app module")
require('tasks.register<Exec>("verifyStudyFlowTvFocusPrimitives")' in build, "missing Gradle TVOV04 focus verifier task")
studyflow_tv = tv_target_section(devtool)
for task in (
    "verifyStudyFlowTvFocusPrimitives",
    ":app-tv:compileDebugKotlin",
    ":app-tv:testDebugUnitTest",
    ":app-tv:lintDebug",
):
    require(task in studyflow_tv, f"studyflow_tv target must include {task}")
require(":app:" not in studyflow_tv, "studyflow_tv target must not run phone app tasks")

for token in (
    "TVOV04  D-pad and focus primitives [implemented]",
    "No Compose Runtime in TVOV04",
    "deterministic initial focus",
):
    require(token in roadmap, f"roadmap missing TVOV04 token: {token}")
for token in (
    "The focus policy is pure Kotlin",
    "Focus targets use a 64dp minimum",
    "No Compose Runtime is introduced",
    "Visible focus must be provided by native drawable state",
):
    require(token in quality, f"TVOV04 quality contract missing token: {token}")
require("TVOV04 adds pure Kotlin D-pad/focus primitives" in notes, "TVOV04 notes missing summary")
require("TVOV04 adds native D-pad/focus primitives" in readme, "README missing TVOV04 summary")
require("TVOV04 - D-pad and focus primitives" in changelog, "CHANGELOG missing TVOV04 entry")

# Guard that the app-tv source payload remains tiny even after focus primitives.
max_source_bytes = 160 * 1024
source_bytes = 0
for path in (ROOT / "app-tv").rglob("*"):
    if path.is_file() and "build" not in path.parts:
        source_bytes += path.stat().st_size
require(source_bytes <= max_source_bytes, f"app-tv source/resource payload is unexpectedly large: {source_bytes} bytes")

if FAILURES:
    print("TVOV04_FOCUS_PRIMITIVES: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV04_FOCUS_PRIMITIVES: PASS")
