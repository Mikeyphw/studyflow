#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


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


manifest_text = read("app-tv/src/main/AndroidManifest.xml")
manifest = ET.fromstring(manifest_text)
application = manifest.find("application")
require(application is not None, "app-tv manifest must contain application")
if application is not None:
    require(application.get(ANDROID_NS + "dataExtractionRules") == "@xml/studyflow_tv_data_extraction_rules", "app-tv must declare Android 12+ data extraction rules")
    require(application.get(ANDROID_NS + "fullBackupContent") == "@xml/studyflow_tv_backup_rules", "app-tv must declare legacy full-backup rules")
    require(application.get(ANDROID_NS + "allowBackup") == "false", "app-tv backup must remain disabled")
    require(application.get(ANDROID_NS + "resizeableActivity") is None, "app-tv must not keep minSdk-guarded resizeableActivity lint warning")
    activity = application.find("activity")
    require(activity is not None, "app-tv manifest must contain launcher activity")
    if activity is not None:
        require(activity.get(ANDROID_NS + "screenOrientation") is None, "app-tv must not pin a fixed orientation")

for rel in (
    "app-tv/src/main/res/xml/studyflow_tv_data_extraction_rules.xml",
    "app-tv/src/main/res/xml/studyflow_tv_backup_rules.xml",
):
    text = read(rel)
    ET.fromstring(text)
    require('domain="root"' in text and 'path="."' in text, f"{rel} must exclude all app data")

banner = read("app-tv/src/main/res/drawable/studyflow_tv_banner.xml")
require("<layer-list" in banner, "TV banner must be a lightweight layer-list after TVOV02")
require("<vector" not in banner, "TV banner must not be a large vector that trips VectorRaster lint")
require("320dp" not in banner or "<vector" not in banner, "TVOV02 must remove the large vector banner")

app_build = read("app-tv/build.gradle.kts")
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
    require(forbidden not in app_build, f"app-tv must keep dependency budget clean in TVOV02; found {forbidden}")
require("isMinifyEnabled = true" in app_build, "app-tv release must keep minification enabled")
require("isShrinkResources = true" in app_build, "app-tv release must keep resource shrinking enabled")

convention = read("build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt")
require('val enableCompose = path != ":app-tv"' in convention, "app-tv must remain opted out of Compose compiler until TV UI adds Compose runtime")
require('buildFeatures.compose = enableCompose' in convention, "Compose build feature must be conditional")

build = read("build.gradle.kts")
require('tasks.register<Exec>("verifyStudyFlowTvDependencyBudget")' in build, "missing Gradle TVOV02 dependency-budget verifier task")

devtool = read(".devtool.toml")
studyflow_tv = devtool[devtool.find("[targets.studyflow_tv]") :]
next_android_profile = studyflow_tv.find("[targets.studyflow_android.validation.phase_profiles.preflight]")
if next_android_profile >= 0:
    studyflow_tv = studyflow_tv[:next_android_profile]
for task in (
    "verifyStudyFlowTvDependencyBudget",
    ":app-tv:lintDebug",
):
    require(task in studyflow_tv, f"studyflow_tv target must include {task}")
require(":app:" not in studyflow_tv, "studyflow_tv target must not run phone app tasks")

roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV02_DEPENDENCY_BUDGET_CONTRACTS.md")
notes = read("TVOV02_ANDROID_TV_DEPENDENCY_BUDGET.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")
for token in (
    "TVOV02  TV dependency budget and lint-clean app module [implemented]",
    "No Cast SDK before TVOV04",
    "No AndroidX TV UI before TVOV03",
):
    require(token in roadmap, f"roadmap missing TVOV02 token: {token}")
for token in (
    "Data extraction rules are explicit",
    "The TV banner is a layer-list",
    "No fixed screen orientation",
):
    require(token in quality, f"TVOV02 quality contract missing token: {token}")
require("TVOV02 removes the initial app-tv lint warnings" in notes, "TVOV02 notes missing summary")
require("TVOV02 keeps the TV APK dependency budget explicit" in readme, "README missing TVOV02 summary")
require("TVOV02 - TV dependency budget and lint cleanup" in changelog, "CHANGELOG missing TVOV02 entry")

# Opportunistic size check for any already-built TV APKs. Debug is informational but still must fit the hard ceiling.
HARD_CEILING_MIB = 35
for outputs in ((ROOT / "app-tv" / "build" / "outputs"),):
    if not outputs.exists():
        continue
    for apk in outputs.rglob("*.apk"):
        size_mib = apk.stat().st_size / (1024 * 1024)
        require(size_mib <= HARD_CEILING_MIB, f"{apk.relative_to(ROOT)} is {size_mib:.1f} MiB, above TV hard ceiling")

if FAILURES:
    print("TVOV02_DEPENDENCY_BUDGET: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV02_DEPENDENCY_BUDGET: PASS")
