#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV08_STUDY_SESSION: FAIL: {message}")


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        fail(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(text: str, needle: str, context: str) -> None:
    if needle not in text:
        fail(f"{context} missing {needle!r}")


def forbid(text: str, needle: str, context: str) -> None:
    if needle in text:
        fail(f"{context} has forbidden token {needle!r}")


session = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/session/StudyFlowTvStudySessionExperience.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/session/StudyFlowTvStudySessionExperienceTest.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
nav = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShell.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV08_STUDY_SESSION_CONTRACTS.md")
notes = read("TVOV08_ANDROID_TV_STUDY_SESSION_EXPERIENCE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV08"',
    "StudyFlowTvSessionStatus.READY",
    "StudyFlowTvSessionStatus.RUNNING",
    "StudyFlowTvSessionStatus.PAUSED",
    "StudyFlowTvSessionStatus.COMPLETED",
    "StudyFlowTvSessionAction.START",
    "StudyFlowTvSessionAction.PAUSE",
    "StudyFlowTvSessionAction.RESUME",
    "StudyFlowTvSessionAction.NEXT_SEGMENT",
    "StudyFlowTvSessionAction.COMPLETE",
    "fun applyAction",
    "fun commandForAction",
    "fun castStateFor",
    "StudyFlowTvCastState(",
    "StudyFlowTvSurface.STUDY_SESSION",
    "StudyFlowTvControlAction.PAUSE",
):
    require(session, token, "Study session model")

for token in (
    "private fun renderStudySessionDestination()",
    "StudyFlowTvStudySessionExperience.defaultState()",
    "StudyFlowTvStudySessionExperience.availableActions",
    "StudyFlowTvStudySessionExperience.commandForAction",
    "private fun toggleStudySessionPlayback()",
    "StudyFlowTvRemoteCommand.PLAY_PAUSE",
    "private fun tvSessionSegment(segment: StudyFlowTvSessionSegment): Button",
    "private fun tvSessionAction(action: StudyFlowTvSessionAction): Button",
):
    require(activity, token, "TV Activity session rendering")

for token in (
    "tv_content_title_study_session",
    "tv_session_section_title",
    "tv_session_status_format",
    "tv_session_segment_warmup_title",
    "tv_session_segment_focus_title",
    "tv_session_segment_checkpoint_title",
    "tv_session_segment_summary_title",
    "tv_session_action_start",
    "tv_session_action_pause",
    "tv_session_action_resume",
    "tv_session_action_next",
    "tv_session_action_complete",
    "tv_session_action_return_today",
):
    require(strings, token, "TV session strings")

for token in (
    "object StudyFlowTvStudySessionBuildContract",
    "studySessionExperienceIntroduced = true",
    "studySessionUsesSharedControlProtocol = true",
    "studySessionSupportsRemoteAndPhoneControls = true",
    "studySessionEmitsCastState = true",
    "studySessionSegmentCount = 4",
    "studySessionActionCount = 6",
):
    require(contract, token, "TV build contract")
    if token.startswith("object "):
        continue
    require(build_test, token.split(" = ")[0] if " = " in token else token, "TV build contract test")

for token in (
    "studySessionOwnsRemoteAndPhoneControlledSessionSurface",
    "defaultSessionContainsACompleteTvRunPlan",
    "sessionStateMachineStartsPausesResumesAdvancesAndCompletes",
    "sessionFocusCommandsAndCastStateUseSharedControlProtocol",
):
    require(test, token, "TV session unit tests")

require(nav, "secondaryDestinations", "navigation secondary destination list")
require(nav, "StudyFlowTvDestination.STUDY_SESSION", "navigation secondary destination")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvStudySessionExperience")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov08_study_session.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvStudySessionExperience", "studyflow_tv target")
require(roadmap, "TVOV08  Study session experience [implemented]", "roadmap status")
require(roadmap, "## TVOV08 — Study session experience", "roadmap implementation note")
require(quality, "state machine", "quality contract")
require(quality, "cast state", "quality contract")
require(notes, "TVOV08 implements the Study session experience", "implementation note")
require(readme, "TVOV08 adds the Android TV Study session experience", "README")
require(changelog, "TVOV08 - Android TV Study session experience", "CHANGELOG")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    "androidx.room",
    "RoomDatabase",
    "@Dao",
):
    forbid(session + app_build, forbidden, "TVOV08 dependency boundary")

print("TVOV08_STUDY_SESSION: PASS")
