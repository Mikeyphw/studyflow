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


def tv_target_section(devtool: str) -> str:
    start = devtool.find("[targets.studyflow_tv]")
    require(start >= 0, "missing studyflow_tv target")
    if start < 0:
        return ""
    end = devtool.find("[targets.studyflow_android.validation.phase_profiles.preflight]", start)
    return devtool[start:] if end < 0 else devtool[start:end]

settings = read("settings.gradle.kts")
protocol_build = read("core/tvcontrol/build.gradle.kts")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
protocol_test = read("core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocolTest.kt")
app_build = read("app/build.gradle.kts")
app_tv_build = read("app-tv/build.gradle.kts")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
build = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV03_PAIRING_CONTROL_CAST_PROTOCOL_CONTRACTS.md")
notes = read("TVOV03_ANDROID_TV_PAIRING_CONTROL_CAST_PROTOCOL.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

require('":core:tvcontrol"' in settings, "settings.gradle.kts must include :core:tvcontrol")
require('id("studyflow.kotlin.library.serialization")' in protocol_build, "core:tvcontrol must use the serialization Kotlin library convention")
for token in ("kotlinx.serialization.core", "kotlinx.serialization.json", "testImplementation(libs.junit4)"):
    require(token in protocol_build, f"core:tvcontrol build missing dependency token: {token}")

for token in (
    "object StudyFlowTvControlProtocol",
    "const val version = 1",
    "const val pairingCodeLength = 6",
    "const val pairingCodeTtlSeconds = 120",
    "const val requiresRevocablePairing = true",
    "const val transportsDeferredUntilImplementation = false",
    "enum class StudyFlowTvSurface",
    "STUDY_SESSION",
    "QUESTION_SET",
    "REVIEW",
    "MOCK_EXAM",
    "PROGRESS",
    "SCHEDULE",
    "enum class StudyFlowTvControlAction",
    "SELECT_ANSWER",
    "SHOW_EXPLANATION",
    "STOP_CAST",
    "enum class StudyFlowTvPairingState",
    "PAIRING_CODE_VISIBLE",
    "REVOKED",
    "data class StudyFlowTvPairingToken",
    "data class StudyFlowTvControlCommand",
    "data class StudyFlowTvCastState",
    "requiresUserPresence: Boolean = true",
    "fun isControllable",
    "fun isCastable",
):
    require(token in protocol, f"TV control protocol missing token: {token}")

for forbidden in ("Room", "Dao", "Database", "ContentResolver", "DocumentFile", "MediaRouter", "CastContext", "androidx.compose", "androidx.tv"):
    require(forbidden not in protocol + protocol_build, f"core:tvcontrol must stay data-only and dependency-light; found {forbidden}")

for token in (
    "pairingTokenRequiresRevocableShortLivedExplicitPairing",
    "controlCommandRoundTripsAsIntentNotDatabaseRows",
    "castStateAllowsLearningSurfacesButRejectsSettings",
    "Json { ignoreUnknownKeys = false }",
    "assertFalse(encoded.contains(\"database\"))",
):
    require(token in protocol_test, f"TV control protocol test missing token: {token}")

require('implementation(project(":core:tvcontrol"))' in app_tv_build, "app-tv must depend on the shared TV control protocol")
require('implementation(project(":core:tvcontrol"))' in app_build, "mobile app must depend on the shared TV control protocol foundation")

for forbidden in (
    'project(":feature:',
    'project(":core:data")',
    'project(":core:database")',
    "androidx.compose",
    "androidx.tv",
    "androidx.mediarouter",
    "com.google.android.gms.cast",
):
    require(forbidden not in app_tv_build, f"app-tv must not add heavy dependency while introducing protocol foundation; found {forbidden}")

for token in (
    'const val sharedControlProtocolModule = ":core:tvcontrol"',
    "const val controlProtocolVersion = 1",
    "const val castTransportDeferred = false",
):
    require(token in contract, f"TV build contract missing TVOV03 token: {token}")
for token in (
    "StudyFlowTvBuildContract.sharedControlProtocolModule",
    "StudyFlowTvBuildContract.controlProtocolVersion",
    "StudyFlowTvBuildContract.castTransportDeferred",
):
    require(token in build_test, f"TV build contract test missing assertion for {token}")
require("tv_app_pair_phone_action" in strings and "tv_home_section_title" in strings, "app-tv Home must keep paired-phone entry visible after protocol foundation")

require('tasks.register<Exec>("verifyStudyFlowTvPairingControlCastProtocol")' in build, "missing Gradle TVOV03 verifier task")
studyflow_tv = tv_target_section(devtool)
for task in (
    "verifyStudyFlowTvPairingControlCastProtocol",
    ":core:tvcontrol:compileKotlin",
    ":core:tvcontrol:test",
    ":app-tv:compileDebugKotlin",
    ":app-tv:testDebugUnitTest",
    ":app-tv:lintDebug",
):
    require(task in studyflow_tv, f"studyflow_tv target must include {task}")
require(":app:" not in studyflow_tv, "studyflow_tv target must not run phone app tasks")

for token in (
    "TVOV03  Phone-to-TV pairing, protocol, and cast foundation [implemented]",
    "mobile app depends on `:core:tvcontrol`",
    "transport remains deferred",
):
    require(token in roadmap, f"roadmap missing TVOV03 token: {token}")
for token in (
    "The protocol module is pure Kotlin",
    "The mobile app and TV app both depend on `:core:tvcontrol`",
    "No Cast SDK is introduced",
    "No raw database rows",
):
    require(token in quality, f"TVOV03 quality contract missing token: {token}")
require("TVOV03 adds the shared phone-to-TV control protocol" in notes, "TVOV03 notes missing summary")
require("TVOV03 adds the shared phone-to-TV control protocol" in readme, "README missing TVOV03 summary")
require("TVOV03 - Pairing, control, and cast protocol foundation" in changelog, "CHANGELOG missing TVOV03 entry")

# Cumulative checked-in source guard. Later TV overlays add real receiver/presentation code; the
# Release APK budget remains the authoritative artifact-size gate.
source_bytes = 0
for root in (ROOT / "core" / "tvcontrol", ROOT / "app-tv"):
    if root.exists():
        for path in root.rglob("*"):
            if path.is_file() and "build" not in path.parts:
                source_bytes += path.stat().st_size
require(source_bytes <= 768 * 1024, f"TV protocol/app-tv source payload is unexpectedly large: {source_bytes} bytes")

if FAILURES:
    print("TVOV03_PAIRING_CONTROL_CAST_PROTOCOL: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV03_PAIRING_CONTROL_CAST_PROTOCOL: PASS")
