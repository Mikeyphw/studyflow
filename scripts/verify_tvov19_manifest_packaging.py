#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.mikeyphw.studyflow.tv"
VERSION_NAME = "0.1.0-tv23"
VERSION_CODE = "23"


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def run_capture(command: list[str], timeout: int = 30) -> subprocess.CompletedProcess[str] | None:
    try:
        return subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return None


def check_target_isolation() -> None:
    devtool_path = ROOT / ".devtool.toml"
    with devtool_path.open("rb") as handle:
        config = tomllib.load(handle)
    target = config.get("targets", {}).get("studyflow_tv", {})
    require(bool(target), "studyflow_tv target must exist")

    task_strings: list[str] = []

    def collect(value: object) -> None:
        if isinstance(value, str):
            task_strings.append(value)
        elif isinstance(value, list):
            for item in value:
                collect(item)
        elif isinstance(value, dict):
            for item in value.values():
                collect(item)

    collect(target.get("tasks", {}))
    collect(target.get("validation", {}).get("phases", {}))
    require(bool(task_strings), "studyflow_tv target must declare focused tasks")
    for task in task_strings:
        if task.startswith(":") or task.startswith("verify"):
            allowed = (
                task.startswith("verifyStudyFlowTv")
                or task.startswith(":app-tv:")
                or task.startswith(":core:tvcontrol:")
                or task.startswith(":core:tvlink:")
            )
            require(allowed, f"studyflow_tv target leaks non-TV task: {task}")
    require("verifyFormatting" not in task_strings, "studyflow_tv target must not run repository-wide verifyFormatting")


def check_apk(apk: Path) -> None:
    require(apk.is_file(), f"missing packaged APK: {apk.relative_to(ROOT)}")
    if not apk.is_file():
        return
    try:
        with zipfile.ZipFile(apk) as archive:
            require(archive.testzip() is None, f"APK ZIP integrity failed: {apk.relative_to(ROOT)}")
            names = set(archive.namelist())
            require("AndroidManifest.xml" in names, f"APK missing AndroidManifest.xml: {apk.relative_to(ROOT)}")
            require(any(name.endswith("classes.dex") for name in names), f"APK missing classes.dex: {apk.relative_to(ROOT)}")
    except zipfile.BadZipFile:
        require(False, f"invalid APK ZIP: {apk.relative_to(ROOT)}")
        return

    aapt2 = shutil.which("aapt2")
    if aapt2:
        result = run_capture([aapt2, "dump", "badging", str(apk)])
        require(result is not None and result.returncode == 0, f"aapt2 could not inspect {apk.relative_to(ROOT)}")
        if result is not None and result.returncode == 0:
            badging = result.stdout
            require(f"package: name='{PACKAGE}'" in badging, "packaged APK applicationId mismatch")
            require(f"versionCode='{VERSION_CODE}'" in badging, "packaged APK versionCode mismatch")
            require(f"versionName='{VERSION_NAME}'" in badging, "packaged APK versionName mismatch")
            require("android.software.leanback" in badging, "packaged APK missing Leanback feature")
            require("android.hardware.touchscreen" in badging, "packaged APK must declare touchscreen optional")
            require("StudyFlowTvActivity" in badging, "packaged APK missing TV launcher activity")
    else:
        print("TVOV19_AAPT2_BADGING: SKIP (aapt2 not available in this environment)")


def check_optional_release_signing(release_apks: list[Path]) -> None:
    keys = (
        "STUDYFLOW_RELEASE_STORE_FILE",
        "STUDYFLOW_RELEASE_STORE_PASSWORD",
        "STUDYFLOW_RELEASE_KEY_ALIAS",
        "STUDYFLOW_RELEASE_KEY_PASSWORD",
    )
    configured = all(os.environ.get(key, "").strip() for key in keys)
    if not configured:
        print("TVOV19_RELEASE_SIGNING: SKIP (STUDYFLOW_RELEASE_* credentials not configured)")
        return
    require(bool(release_apks), "configured release signing requires a built Release APK")
    apksigner = shutil.which("apksigner")
    require(apksigner is not None, "configured release signing requires apksigner on PATH")
    if apksigner and release_apks:
        result = run_capture([apksigner, "verify", "--verbose", str(release_apks[0])])
        require(result is not None and result.returncode == 0, "configured TV Release APK failed apksigner verification")


parser = argparse.ArgumentParser()
parser.add_argument("--target-isolation", action="store_true")
parser.add_argument("--require-packaged-artifacts", action="store_true")
args = parser.parse_args()

manifest_text = read("app-tv/src/main/AndroidManifest.xml")
app_build = read("app-tv/build.gradle.kts")
banner_text = read("app-tv/src/main/res/drawable/studyflow_tv_banner.xml")
backup = read("app-tv/src/main/res/xml/studyflow_tv_backup_rules.xml")
data_rules = read("app-tv/src/main/res/xml/studyflow_tv_data_extraction_rules.xml")
policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvPackagingPolicy.kt")
policy_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/release/StudyFlowTvPackagingPolicyTest.kt")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
root_gradle = read("build.gradle.kts")
devtool_text = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
quality = read("docs/quality/TVOV19_MANIFEST_PACKAGING_DEVTOOL_HARDENING_CONTRACTS.md")
notes = read("TVOV19_ANDROID_TV_MANIFEST_PACKAGING_DEVTOOL_HARDENING.md")
readme = read("README.md")

try:
    manifest_root = ET.fromstring(manifest_text)
except ET.ParseError as exc:
    manifest_root = ET.Element("manifest")
    require(False, f"AndroidManifest.xml is not well-formed: {exc}")
features = {
    node.attrib.get(ANDROID + "name"): node.attrib.get(ANDROID + "required")
    for node in manifest_root.findall("uses-feature")
}
require(features.get("android.software.leanback") == "true", "Leanback feature must be required")
require(features.get("android.hardware.touchscreen") == "false", "touchscreen must be optional")
application = manifest_root.find("application")
require(application is not None, "TV manifest must contain application")
if application is not None:
    expected_attrs = {
        "allowBackup": "false",
        "banner": "@drawable/studyflow_tv_banner",
        "dataExtractionRules": "@xml/studyflow_tv_data_extraction_rules",
        "fullBackupContent": "@xml/studyflow_tv_backup_rules",
        "icon": "@drawable/studyflow_tv_launcher",
        "usesCleartextTraffic": "false",
        "appCategory": "productivity",
    }
    for key, value in expected_attrs.items():
        require(application.attrib.get(ANDROID + key) == value, f"manifest application {key} must be {value}")
    activities = application.findall("activity")
    activity = next((node for node in activities if node.attrib.get(ANDROID + "name") == ".StudyFlowTvActivity"), None)
    require(activity is not None, "StudyFlowTvActivity missing from manifest")
    if activity is not None:
        require(activity.attrib.get(ANDROID + "exported") == "true", "TV launcher activity must be exported")
        require(activity.attrib.get(ANDROID + "launchMode") == "singleTask", "TV launcher must use singleTask")
        categories = {node.attrib.get(ANDROID + "name") for node in activity.findall("intent-filter/category")}
        require("android.intent.category.LEANBACK_LAUNCHER" in categories, "Leanback launcher category missing")
        require("android.intent.category.LAUNCHER" not in categories, "phone LAUNCHER category must stay out of TV APK")

require('<layer-list' in banner_text, 'TV banner must remain the lightweight layer-list accepted by the dependency-budget lint contract')
for token in ('android:width="160dp"', 'android:height="90dp"'):
    require(token in banner_text, f"TV banner contract missing {token}")
require('<vector' not in banner_text, 'TV banner must not use a VectorRaster-prone large vector')
for domain in ("root", "file", "database", "sharedpref", "external"):
    require(f'domain="{domain}"' in backup, f"legacy backup rules must exclude {domain}")
    require(data_rules.count(f'domain="{domain}"') >= 2, f"data-extraction rules must exclude {domain} from cloud and device transfer")

for token in (
    'applicationId = "com.mikeyphw.studyflow.tv"',
    'namespace = "com.mikeyphw.studyflow.tv"',
    'versionCode = 23',
    'versionName = "0.1.0-tv23"',
    'STUDYFLOW_RELEASE_STORE_FILE',
    'studyFlowTvRelease',
    'TV_RELEASE_SIGNING_CONFIGURED',
):
    require(token in app_build, f"TVOV19 app-tv packaging config missing token: {token}")
for forbidden in ('project(":core:data")', 'project(":core:database")', 'project(":feature:', 'androidx.tv', 'androidx.compose', 'androidx.mediarouter'):
    require(forbidden not in app_build, f"TVOV19 dependency boundary violated by {forbidden}")

for token in ('const val overlay = "TVOV19"', 'versionCode = 23', 'bannerWidthDp = 160', 'bannerHeightDp = 90', 'fun isAllowedTvTargetTask'):
    require(token in policy, f"TVOV19 packaging policy missing token: {token}")
for token in ('packageIdentityAndTvLauncherContractAreStable', 'devtoolTvTargetRejectsPhoneTasks'):
    require(token in policy_test, f"TVOV19 packaging policy test missing {token}")
for token in ('packagingHardeningIntroduced = true', 'tvTargetRejectsPhoneTasks = true', 'conditionalReleaseSigningSupported = true', 'packageVersionCode = 23'):
    require(token in contract, f"TVOV19 build contract missing {token}")
    require(token.split(" = ")[0] in build_test, f"TVOV19 build contract test missing {token}")

for token in (
    'tasks.register<Exec>("verifyStudyFlowTvManifestPackaging")',
    'tasks.register<Exec>("verifyStudyFlowTvTargetIsolation")',
    'tasks.register<Exec>("verifyStudyFlowTvPackagedArtifacts")',
):
    require(token in root_gradle, f"TVOV19 Gradle wiring missing {token}")
for token in ("verifyStudyFlowTvManifestPackaging", "verifyStudyFlowTvTargetIsolation", "verifyStudyFlowTvPackagedArtifacts"):
    require(token in devtool_text, f"studyflow_tv target missing TVOV19 token: {token}")

check_target_isolation()
require("TVOV19  Android TV manifest, packaging, and Devtool TV target hardening [implemented]" in roadmap, "roadmap must mark TVOV19 implemented")
require("TVOV19  Android TV manifest, packaging, and Devtool TV target hardening [implemented]" in rules, "working rules must mark TVOV19 implemented")
require("## TVOV19" in roadmap, "roadmap missing TVOV19 implementation note")
require("Leanback" in quality and "signing" in quality and "studyflow_tv" in quality, "TVOV19 quality contract incomplete")
require("target isolation" in notes and "package identity" in notes, "TVOV19 implementation note incomplete")
require("TVOV19 and TVOV20" in readme, "README missing TVOV19/20 summary")

if args.target_isolation:
    check_target_isolation()

release_apks = sorted((ROOT / "app-tv/build/outputs/apk/release").glob("*.apk"))
debug_apks = sorted((ROOT / "app-tv/build/outputs/apk/debug").glob("*.apk"))
if args.require_packaged_artifacts:
    require(bool(debug_apks), "TVOV19 packaged-artifact gate requires a Debug APK")
    require(bool(release_apks), "TVOV19 packaged-artifact gate requires a Release APK")
    for apk in debug_apks + release_apks:
        check_apk(apk)
    check_optional_release_signing(release_apks)

if FAILURES:
    print("TVOV19_MANIFEST_PACKAGING: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV19_MANIFEST_PACKAGING: PASS")
