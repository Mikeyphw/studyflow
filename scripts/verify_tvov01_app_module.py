#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
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


def section(text: str, header: str) -> str:
    marker = f"[{header}]"
    start = text.find(marker)
    require(start >= 0, f"missing TOML section: {header}")
    if start < 0:
        return ""
    end_match = re.search(r"\n\[[^\n]+\]", text[start + 1 :])
    end = len(text) if end_match is None else start + 1 + end_match.start()
    return text[start:end]

settings = read("settings.gradle.kts")
devtool = read(".devtool.toml")
build = read("build.gradle.kts")
app_build = read("app-tv/build.gradle.kts")
manifest = read("app-tv/src/main/AndroidManifest.xml")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV01_APP_MODULE_CONTRACTS.md")
notes = read("TVOV01_ANDROID_TV_APP_MODULE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

require('":app-tv"' in settings, "settings.gradle.kts must include :app-tv")
require('id("studyflow.android.application")' in app_build, "app-tv must use the shared Android application convention")
require('namespace = "com.mikeyphw.studyflow.tv"' in app_build, "app-tv namespace must be TV-specific")
require('applicationId = "com.mikeyphw.studyflow.tv"' in app_build, "app-tv applicationId must be TV-specific")
require('applicationId = "com.mikeyphw.studyflow"' not in app_build, "app-tv must not reuse the phone applicationId")
require("isMinifyEnabled = true" in app_build, "app-tv release must enable R8 minification")
require("isShrinkResources = true" in app_build, "app-tv release must enable resource shrinking")

for forbidden in (
    'project(":app")',
    'project(":feature:',
    'project(":core:data")',
    'project(":core:database")',
    "androidx.compose",
    "androidx.tv",
    "tv-material",
    "androidx.mediarouter",
    "com.google.android.gms.cast",
    "appcompat",
):
    require(forbidden not in app_build, f"app-tv build must stay dependency-light in TVOV01; found {forbidden}")

for token in (
    'android.software.leanback',
    'android.hardware.touchscreen',
    'android:required="false"',
    'android.intent.category.LEANBACK_LAUNCHER',
    'android:banner="@drawable/studyflow_tv_banner"',
    'android:exported="true"',
    'android:screenOrientation="landscape"',
):
    require(token in manifest, f"app-tv manifest missing TV token: {token}")

for token in (
    "class StudyFlowTvActivity : Activity()",
    "createContentView",
    "Minimal TV shell installed",
):
    require(token in activity or token in read("app-tv/src/main/res/values/strings.xml"), f"TV Activity/resources missing token: {token}")

for forbidden in ("ComponentActivity", "setContent(", "@Composable", "androidx.compose", "MediaRouter", "Cast"):
    require(forbidden not in activity, f"TVOV01 Activity must not add heavy UI/cast dependency token {forbidden}")

for token in (
    'const val tvOverlay = "TVOV01"',
    'const val applicationId = "com.mikeyphw.studyflow.tv"',
    'const val phoneApplicationId = "com.mikeyphw.studyflow"',
    'const val sizeTargetMiB = 25',
    'const val sizeHardCeilingMiB = 35',
    'startsWithoutFeatureModules = true',
    'startsWithoutCastSdk = true',
    'pairedPhoneControlRequiredByRoadmap = true',
):
    require(token in contract, f"TV build contract missing token: {token}")
require("minimalTvModuleStartsWithSeparatePackageAndStrictSizeBudget" in test, "TV build contract unit test is missing")

studyflow_tv = devtool[devtool.find("[targets.studyflow_tv]") :]
next_android_profile = studyflow_tv.find("[targets.studyflow_android.validation.phase_profiles.preflight]")
if next_android_profile >= 0:
    studyflow_tv = studyflow_tv[:next_android_profile]
for task in (
    ":app-tv:compileDebugKotlin",
    ":app-tv:testDebugUnitTest",
    ":app-tv:assembleDebug",
    ":app-tv:lintDebug",
):
    require(task in studyflow_tv, f"studyflow_tv target must include {task}")
require(":app:" not in studyflow_tv, "studyflow_tv target must not run phone app tasks")
require('tasks.register<Exec>("verifyStudyFlowTvAppModule")' in build, "Gradle TVOV01 verifier task is missing")
require("verifyStudyFlowTvAppModule" in studyflow_tv, "studyflow_tv target must run TVOV01 verifier")
require('"app-tv"' in build, "root quality source roots must include app-tv")

for token in (
    "TVOV01  Minimal app-tv application module [implemented]",
    "first real `:app-tv` Android application module",
    "`:app-tv:compileDebugKotlin`",
):
    require(token in roadmap, f"roadmap missing TVOV01 token: {token}")
for token in (
    "`app-tv` is an Android application module",
    "no phone feature-module dependencies",
    "Release configuration enables minification",
):
    require(token in quality, f"TVOV01 quality doc missing token: {token}")
for token in (
    "Added `:app-tv`",
    "Leanback launcher activity",
    "TVOV02 should measure",
):
    require(token in notes, f"TVOV01 notes missing token: {token}")
require("TVOV01 adds a real `:app-tv` module" in readme, "README missing TVOV01 summary")
require("TVOV01 - Minimal Android TV application module" in changelog, "CHANGELOG missing TVOV01 entry")

max_source_bytes = 96 * 1024
source_bytes = 0
for path in (ROOT / "app-tv").rglob("*"):
    if path.is_file() and "build" not in path.parts:
        source_bytes += path.stat().st_size
require(source_bytes <= max_source_bytes, f"app-tv source/resource payload is unexpectedly large: {source_bytes} bytes")

if FAILURES:
    print("TVOV01_APP_MODULE: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV01_APP_MODULE: PASS")
