#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV07_TODAY_WORKSPACE: FAIL: {message}")


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


today = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/today/StudyFlowTvTodayWorkspace.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/today/StudyFlowTvTodayWorkspaceTest.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
nav = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShell.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV07_TODAY_WORKSPACE_CONTRACTS.md")
notes = read("TVOV07_ANDROID_TV_TODAY_WORKSPACE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV07"',
    "StudyFlowTvTodaySnapshot",
    "StudyFlowTvTodayBlockId.NEXT_STUDY_BLOCK",
    "StudyFlowTvTodayBlockId.DUE_REVIEWS",
    "StudyFlowTvTodayBlockId.QUESTION_PRACTICE",
    "StudyFlowTvTodayBlockId.MOCK_EXAM_PREP",
    "StudyFlowTvTodayAction.START_NEXT_BLOCK",
    "StudyFlowTvTodayAction.START_REVIEWS",
    "StudyFlowTvTodayAction.START_PRACTICE",
    "StudyFlowTvTodayAction.OPEN_SCHEDULE",
    "StudyFlowTvDestination.STUDY_SESSION",
    "StudyFlowTvSurface.STUDY_SESSION",
    "StudyFlowTvNavigationShell.openCommandFor",
    "fun focusItems()",
):
    require(today, token, "Today workspace model")

for token in (
    "private fun renderTodayDestination()",
    "StudyFlowTvTodayWorkspace.workspaceForSnapshot",
    "private var todaySnapshot: StudyFlowTvTodaySnapshot",
    "StudyFlowTvPresentationAdapter.waitingState()",
    "workspace.blocks.chunked(2)",
    "private fun tvTodayBlock(block: StudyFlowTvTodayBlock): Button",
    "StudyFlowTvTodayWorkspace.destinationForAction(block.action)",
):
    require(activity, token, "TV Activity Today rendering")

for token in (
    "tv_today_section_title",
    "tv_today_status_format",
    "tv_today_block_next_title",
    "tv_today_block_reviews_title",
    "tv_today_block_practice_title",
    "tv_today_block_mock_title",
):
    require(strings, token, "TV Today strings")

for token in (
    "object StudyFlowTvTodayBuildContract",
    "todayWorkspaceIntroduced = true",
    "todayWorkspaceUsesNavigationShell = true",
    "todayWorkspaceUsesSharedControlProtocol = true",
    "nextBlockReviewPracticeAndScheduleVisible = true",
    "todayBlockCount = 4",
):
    require(contract, token, "TV build contract")
    if token.startswith("object "):
        continue
    require(build_test, token.split(" = ")[0] if " = " in token else token, "TV build contract test")

for token in (
    "todayWorkspaceOwnsTheTodayDestinationAndCoreWorkload",
    "todaySnapshotValidatesUserVisibleStudyLoad",
    "todayActionsRouteToConcreteTvDestinations",
    "todayFocusAndPhoneCommandsUseSharedProtocols",
):
    require(test, token, "TV Today unit tests")

require(nav, "STUDY_SESSION(\"session\", StudyFlowTvSurface.STUDY_SESSION)", "navigation secondary destination")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvTodayWorkspace")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov07_today_workspace.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvAllContracts", "studyflow_tv aggregate contract gate")
require(root_gradle.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1].split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0], "verifyStudyFlowTvTodayWorkspace", "aggregate TV contract ownership")
require(roadmap, "TVOV07  Today workspace for TV [implemented]", "roadmap status")
require(roadmap, "## TVOV07 — Today workspace", "roadmap implementation note")
require(quality, "next study block", "quality contract")
require(quality, "paired-phone control", "quality contract")
require(notes, "TVOV07 implements the Today workspace", "implementation note")
require(readme, "TVOV07 adds the Android TV Today workspace", "README")
require(changelog, "TVOV07 - Android TV Today workspace", "CHANGELOG")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    "androidx.room",
    "RoomDatabase",
    "@Dao",
):
    forbid(today + app_build, forbidden, "TVOV07 dependency boundary")

print("TVOV07_TODAY_WORKSPACE: PASS")
