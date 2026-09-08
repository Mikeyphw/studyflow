#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV10_MOCK_EXAM_STUDIO: FAIL: {message}")


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

mock = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/mockexam/StudyFlowTvMockExamStudio.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/mockexam/StudyFlowTvMockExamStudioTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV10_MOCK_EXAM_STUDIO_CONTRACTS.md")
notes = read("TVOV10_ANDROID_TV_MOCK_EXAM_STUDIO.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV10"',
    "ownsMockExamSurface = true",
    "timedSectionsVisible = true",
    "remoteCanStartPauseSubmitAndReview = true",
    "pairedPhoneCanControlMockExam = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvMockExamStatus.READY",
    "StudyFlowTvMockExamStatus.RUNNING",
    "StudyFlowTvMockExamStatus.PAUSED",
    "StudyFlowTvMockExamStatus.SUBMITTED",
    "StudyFlowTvMockExamStatus.RESULTS",
    "StudyFlowTvMockExamAction.MARK_FOR_REVIEW",
    "StudyFlowTvMockExamAction.SUBMIT",
    "fun applyAction",
    "fun focusItems",
    "fun commandForAction",
    "StudyFlowTvControlAction.MARK_FOR_REVIEW",
    "StudyFlowTvControlAction.SUBMIT",
    "fun castStateFor",
    "StudyFlowTvSurface.MOCK_EXAM",
):
    require(mock, token, "mock exam model")

for token in (
    "private fun renderMockExamDestination()",
    "StudyFlowTvDestination.MOCK_EXAM -> renderMockExamDestination()",
    "StudyFlowTvMockExamStudio.defaultState()",
    "StudyFlowTvMockExamStudio.availableActions",
    "StudyFlowTvMockExamStudio.applyAction",
    "private fun toggleMockExamPlayback()",
    "private fun tvMockExamSection(section: StudyFlowTvMockExamSection): Button",
    "private fun tvMockExamAction(action: StudyFlowTvMockExamAction): Button",
):
    require(activity, token, "TV activity mock rendering")

for token in (
    "tv_mock_section_title",
    "tv_mock_status_format",
    "tv_mock_section_body_format",
    "tv_mock_result_format",
    "tv_mock_action_start",
    "tv_mock_action_pause",
    "tv_mock_action_resume",
    "tv_mock_action_next_section",
    "tv_mock_action_mark_review",
    "tv_mock_action_submit",
    "tv_mock_action_results",
):
    require(strings, token, "mock strings")

for token in (
    "object StudyFlowTvMockExamBuildContract",
    "mockExamStudioIntroduced = true",
    "mockExamRequiresNoComposeRuntime = true",
    "mockExamUsesSharedControlProtocol = true",
    "mockExamSupportsRemoteAndPhoneControls = true",
    "mockExamEmitsCastState = true",
    "mockExamSectionCount = 4",
    "mockExamActionCount = 8",
):
    require(contract, token, "mock build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "mock build contract test")

for token in (
    "mockExamOwnsTimedSectionsRemoteControlsAndPhoneControl",
    "defaultMockExamContainsSectionsTimeAndQuestions",
    "mockExamStateStartsPausesFlagsSubmitsAndShowsResults",
    "mockExamFocusCommandsAndCastStateUseSharedProtocols",
):
    require(test, token, "mock tests")

for token in ("MARK_FOR_REVIEW", "SUBMIT", "REVIEW_RESULTS"):
    require(protocol, token, "shared control protocol")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvMockExamStudio")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov10_mock_exam_studio.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvMockExamStudio", "studyflow_tv target")
require(roadmap, "TVOV10  Mock Exam Studio [implemented]", "roadmap status")
require(roadmap, "## TVOV10 — Mock Exam Studio", "roadmap implementation note")
require(quality, "timed sections", "quality contract")
require(notes, "TVOV10 implements the Android TV Mock Exam Studio", "implementation note")
require(readme, "TVOV09 and TVOV10 add the Android TV Question Practice", "README")
require(changelog, "TVOV09/TVOV10 - Android TV Question Practice", "CHANGELOG")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    "androidx.room",
    "RoomDatabase",
    "@Dao",
    "project(\":core:data\")",
    "project(\":core:database\")",
    "project(\":feature:",
):
    forbid(mock + app_build, forbidden, "TVOV10 dependency boundary")

print("TVOV10_MOCK_EXAM_STUDIO: PASS")
