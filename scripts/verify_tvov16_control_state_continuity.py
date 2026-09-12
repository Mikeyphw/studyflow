#!/usr/bin/env python3
from __future__ import annotations

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


protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
continuity = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlContinuity.kt")
core_test = read("core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlContinuityTest.kt")
reducer = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducer.kt")
reducer_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/control/StudyFlowTvControlStateReducerTest.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
build_contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
notes = read("TVOV16_ANDROID_TV_CONTROL_STATE_CONTINUITY.md")
quality = read("docs/quality/TVOV16_CONTROL_STATE_CONTINUITY_CONTRACTS.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")
app_build = read("app-tv/build.gradle.kts")

for token in (
    'enum class StudyFlowTvControlOrigin',
    'LOCAL_REMOTE',
    'PAIRED_PHONE',
    'enum class StudyFlowTvPlaybackState',
    'data class StudyFlowTvControlSession',
    'acceptsPairedPhoneCommands',
    'fun revoke',
    'data class StudyFlowTvControlContinuityState',
    'currentDestination: String',
    'activeSurfaceTitle: String',
    'activeItemId: String?',
    'commandRevision: Long',
    'sessionExpiresAtEpochMillis: Long?',
):
    require(token in continuity, f"TVOV16 shared continuity schema missing token: {token}")
require("REVOKE_PAIRING" in protocol, "shared TV protocol must model pairing revocation intent")

for token in (
    'object StudyFlowTvControlStateReducer',
    'const val overlay = "TVOV16"',
    'localAndPairedPhoneUseSameReducer = true',
    'transportDeferred = false',
    'monotonicRevisionRequired = true',
    'staleAndDuplicateCommandsRejected = true',
    'expiredAndRevokedPairingsRejected = true',
    'fun reduce(',
    'fun continuityState(',
    'StudyFlowTvControlOrigin.PAIRED_PHONE',
    'duplicate-command',
    'stale-command',
    'expired-pairing',
):
    require(token in reducer, f"TVOV16 reducer missing token: {token}")
for surface in ("HOME", "TODAY", "STUDY_SESSION", "QUESTION_SET", "REVIEW", "MOCK_EXAM", "PROGRESS", "SCHEDULE", "LIBRARY", "SETTINGS"):
    require(f"StudyFlowTvSurface.{surface}" in reducer, f"TVOV16 reducer must map surface {surface}")

for token in (
    'StudyFlowTvPresentationAdapter.waitingState()',
    'applyPresentationSnapshot',
    'StudyFlowTvControlOrigin.LOCAL_REMOTE',
    'StudyFlowTvControlOrigin.PAIRED_PHONE',
    'applyPairedPhoneCommand',
    'StudyFlowTvControlStateReducer.continuityState(controlState)',
    'tv_control_continuity_status_format',
):
    require(token in activity, f"TVOV16 Activity integration missing token: {token}")

for token in (
    'localRemoteAndPairedPhoneDriveTheSameReducerState',
    'openCommandsCoverEveryImplementedTvDestination',
    'surfaceCommandsPreserveActiveQuestionSectionReviewScheduleAndLibraryState',
    'duplicateStaleExpiredAndRevokedPhoneCommandsFailClosed',
    'settingsCanRevokePairingThroughTheSameSharedCommandLayer',
):
    require(token in reducer_test, f"TVOV16 app-tv unit test missing token: {token}")
for token in (
    'pairedSessionExpiresAndCanBeRevokedWithoutTransportState',
    'continuityStateRoundTripsWithRevisionAndActivePresentation',
    'revokePairingIntentIsPartOfSharedProtocol',
):
    require(token in core_test, f"TVOV16 core unit test missing token: {token}")

for token in (
    'object StudyFlowTvControlContinuityBuildContract',
    'controlStateContinuityIntroduced = true',
    'localAndPairedPhoneUseSameReducer = true',
    'controlTransportStillDeferred = false',
    'continuitySurfaceCount = 10',
):
    require(token in build_contract, f"TVOV16 build contract missing token: {token}")

for forbidden in ('androidx.mediarouter', 'com.google.android.gms.cast', 'project(":core:data")', 'project(":core:database")', 'project(":feature:', 'androidx.compose', 'androidx.tv'):
    require(forbidden not in app_build, f"TVOV16 must not add transport/heavy TV dependencies; found {forbidden}")
for forbidden in ("java.net.Socket", "ServerSocket", "WebSocket", "CastContext", "MediaRouter"):
    require(forbidden not in reducer + continuity, f"TVOV16 reducer/schema must remain transport-free; found {forbidden}")

require('tasks.register<Exec>("verifyStudyFlowTvControlStateContinuity")' in build, "missing Gradle TVOV16 verifier task")
require("verifyStudyFlowTvAllContracts" in devtool, "studyflow_tv target must run aggregate TV contracts")
tv_aggregate = build.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1].split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
require("verifyStudyFlowTvControlStateContinuity" in tv_aggregate, "aggregate TV contracts must own TVOV16 verifier")
require("TVOV16  Cross-device control/state continuity [implemented]" in roadmap, "roadmap must mark TVOV16 implemented")
require("TVOV16" in notes and "transport" in notes and "deferred" in notes, "TVOV16 implementation note is incomplete")
require("TVOV16" in quality and "monotonic" in quality, "TVOV16 quality contract is incomplete")
require("TVOV16 adds deterministic cross-device control/state continuity" in readme, "README missing TVOV16 summary")
require("TVOV16 - Cross-device control/state continuity" in changelog, "CHANGELOG missing TVOV16 entry")

if FAILURES:
    print("TVOV16_CONTROL_STATE_CONTINUITY: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV16_CONTROL_STATE_CONTINUITY: PASS")
