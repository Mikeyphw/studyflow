#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
TARGET_MIB = 25
HARD_CEILING_MIB = 35


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


parser = argparse.ArgumentParser()
parser.add_argument("--require-release-artifact", action="store_true")
args = parser.parse_args()

app_build = read("app-tv/build.gradle.kts")
proguard = read("app-tv/proguard-rules.pro")
policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/optimization/StudyFlowTvReleaseOptimizationPolicy.kt")
policy_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/optimization/StudyFlowTvReleaseOptimizationPolicyTest.kt")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
quality = read("docs/quality/TVOV18_SIZE_R8_RESOURCE_SHRINKING_CONTRACTS.md")
notes = read("TVOV18_ANDROID_TV_SIZE_R8_RESOURCE_SHRINKING.md")
readme = read("README.md")

require('versionName = "0.1.0-tv26"' in app_build or 'versionName = "1.0.0-tv31"' in app_build, "TVOV18-compatible or TVOV31 terminal version missing")

for token in (
    "isMinifyEnabled = true",
    "isShrinkResources = true",
    'getDefaultProguardFile("proguard-android-optimize.txt")',
    '"proguard-rules.pro"',
    "dependenciesInfo",
    "includeInApk = false",
    "includeInBundle = false",
):
    require(token in app_build, f"TVOV18 app-tv release configuration missing token: {token}")

require("-keep class com.mikeyphw.studyflow.** { *; }" not in proguard, "TVOV18 must not add a broad keep-all rule")
require("-keepattributes SourceFile,LineNumberTable" in proguard, "TVOV18 rules must preserve actionable crash line metadata")

for token in (
    'const val overlay = "TVOV18"',
    "releaseOptimizationSealed = true",
    "r8Required = true",
    "resourceShrinkingRequired = true",
    "optimizedDefaultRulesRequired = true",
    "mappingEvidenceRequired = true",
    "targetMiB = 25",
    "hardCeilingMiB = 35",
    "fun evaluate",
):
    require(token in policy, f"TVOV18 optimization policy missing token: {token}")

for token in (
    "optimizedReleaseAtOrBelowTargetIsAccepted",
    "releaseAboveTargetOrWithoutR8EvidenceFailsTheTv18Seal",
    "hardCeilingStillFailsClosed",
):
    require(token in policy_test, f"TVOV18 optimization test missing token: {token}")

for token in (
    "sizeOptimizationSealIntroduced = true",
    "r8Required = true",
    "resourceShrinkingRequired = true",
    "mappingEvidenceRequired = true",
    "releaseTargetMiB = 25",
    "releaseHardCeilingMiB = 35",
):
    require(token in contract, f"TVOV18 build contract missing token: {token}")
    require(token.split(" = ")[0] in build_test, f"TVOV18 build contract test missing assertion: {token}")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    'project(":core:data")',
    'project(":core:database")',
    'project(":feature:',
):
    require(forbidden not in app_build, f"TVOV18 dependency budget violated by {forbidden}")

require('tasks.register<Exec>("verifyStudyFlowTvSizeOptimization")' in root_gradle, "missing TVOV18 source verifier task")
require('tasks.register<Exec>("verifyStudyFlowTvOptimizedReleaseArtifact")' in root_gradle, "missing TVOV18 optimized artifact verifier task")
require('dependsOn(":app-tv:assembleRelease")' in root_gradle, "optimized artifact verifier must build Release")
require("verifyStudyFlowTvAllContracts" in devtool, "studyflow_tv target missing aggregate TV contract gate")
tv_aggregate = root_gradle.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1]
tv_aggregate = tv_aggregate.split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
require("verifyStudyFlowTvSizeOptimization" in tv_aggregate, "aggregate TV gate missing TVOV18 source verifier")
require("verifyStudyFlowTvFinalReleaseSeal" in devtool, "TV release target missing final release seal")
release_seal = root_gradle.split('tasks.register<Exec>("verifyStudyFlowTvFinalReleaseSeal")', 1)[1]
release_seal = release_seal.split("tasks.register", 1)[0]
require("verifyStudyFlowTvOptimizedReleaseArtifact" in release_seal, "TV final release seal must own optimized artifact verifier")
require('[targets.studyflow_tv_release]' in devtool, "TV release target must exist")
release_block = devtool.split('[targets.studyflow_tv_release]', 1)[1].split('[targets.studyflow_release]', 1)[0]
require(':app-tv:lintRelease' in release_block, "TV release target must retain Release lint")
require("TVOV18  Size optimization, R8, and resource shrinking [implemented]" in roadmap, "roadmap must mark TVOV18 implemented")
require("TVOV18  Size optimization, R8, and resource shrinking [implemented]" in rules, "working rules must mark TVOV18 implemented")
require("## TVOV18" in roadmap, "roadmap missing TVOV18 implementation note")
require("25 MiB" in quality and "mapping.txt" in quality and "resource shrinking" in quality, "TVOV18 quality contract incomplete")
require("optimized-Release gate" in notes, "TVOV18 implementation note incomplete")
require("TVOV17 and TVOV18" in readme, "README missing TVOV17/18 summary")

release_apks = sorted((ROOT / "app-tv" / "build" / "outputs" / "apk" / "release").glob("*.apk"))
mapping = ROOT / "app-tv" / "build" / "outputs" / "mapping" / "release" / "mapping.txt"
if args.require_release_artifact:
    require(bool(release_apks), "TVOV18 requires a built app-tv Release APK")
    require(mapping.is_file(), "TVOV18 requires R8 mapping.txt evidence")

for apk in release_apks:
    size_mib = apk.stat().st_size / (1024 * 1024)
    require(size_mib <= HARD_CEILING_MIB, f"{apk.relative_to(ROOT)} is {size_mib:.2f} MiB, above hard ceiling")
    if args.require_release_artifact:
        require(size_mib <= TARGET_MIB, f"{apk.relative_to(ROOT)} is {size_mib:.2f} MiB, above TVOV18 25 MiB target")
        try:
            with zipfile.ZipFile(apk) as archive:
                bad = archive.testzip()
                require(bad is None, f"Release APK ZIP integrity failed at {bad}")
                names = set(archive.namelist())
                require("AndroidManifest.xml" in names, "Release APK missing AndroidManifest.xml")
                require(any(name.endswith("classes.dex") for name in names), "Release APK missing classes.dex")
        except zipfile.BadZipFile:
            require(False, f"Release APK is not a valid ZIP: {apk.relative_to(ROOT)}")

if FAILURES:
    print("TVOV18_SIZE_OPTIMIZATION: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV18_SIZE_OPTIMIZATION: PASS")
if release_apks:
    for apk in release_apks:
        print(f"TVOV18_RELEASE_APK_MIB={apk.stat().st_size / (1024 * 1024):.3f} path={apk.relative_to(ROOT)}")
    print(f"TVOV18_R8_MAPPING={'present' if mapping.is_file() else 'missing'}")
