#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV13_READ_ONLY_SCHEDULE: FAIL: {message}")


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

schedule = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/schedule/StudyFlowTvScheduleSurface.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/schedule/StudyFlowTvScheduleSurfaceTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
nav = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShell.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV13_READ_ONLY_SCHEDULE_CONTRACTS.md")
notes = read("TVOV13_ANDROID_TV_READ_ONLY_SCHEDULE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV13"',
    "ownsReadOnlyScheduleSurface = true",
    "todayAndWeekScheduleVisible = true",
    "reviewWindowsAndMockExamsVisible = true",
    "scheduleEditingDisabledOnTv = true",
    "pairedPhoneCanNavigateScheduleButNotMutate = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvScheduleItemKind.REVIEW_WINDOW",
    "StudyFlowTvScheduleItemKind.MOCK_EXAM",
    "fun applyAction",
    "fun destinationForAction",
    "fun focusItems",
    "fun commandForAction",
    "fun castStateFor",
    "StudyFlowTvSurface.SCHEDULE",
):
    require(schedule, token, "schedule model")


if activity.count("private var scheduleSnapshot: StudyFlowTvScheduleSnapshot") != 1:
    fail("TV activity must own exactly one schedule snapshot property")
if activity.count("private var librarySnapshot: StudyFlowTvLibrarySnapshot") != 1:
    fail("TV activity must own exactly one library snapshot property")

for token in (
    "private fun renderScheduleDestination()",
    "StudyFlowTvDestination.SCHEDULE -> renderScheduleDestination()",
    "StudyFlowTvScheduleSurface.defaultSnapshot()",
    "StudyFlowTvScheduleSurface.actions",
    "private fun tvScheduleItem(item: StudyFlowTvScheduleItem): Button",
    "private fun tvScheduleAction(action: StudyFlowTvScheduleAction): Button",
):
    require(activity, token, "TV activity schedule rendering")

for token in (
    "tv_nav_schedule",
    "tv_content_title_schedule",
    "tv_content_body_schedule",
    "tv_schedule_section_title",
    "tv_schedule_status_format",
    "tv_schedule_item_body_format",
    "tv_schedule_read_only_note_format",
    "tv_schedule_action_open_active",
    "tv_schedule_action_mock",
):
    require(strings, token, "schedule strings")

for token in (
    "object StudyFlowTvScheduleBuildContract",
    "readOnlyScheduleIntroduced = true",
    "scheduleRequiresNoComposeRuntime = true",
    "scheduleUsesSharedControlProtocol = true",
    "scheduleSupportsRemoteAndPhoneNavigation = true",
    "scheduleEditingDisabledOnTv = true",
    "scheduleEmitsCastState = true",
    "scheduleItemCount = 5",
    "scheduleActionCount = 5",
):
    require(contract, token, "schedule build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "schedule build contract test")

for token in (
    "scheduleOwnsRoadmapReadOnlyContract",
    "defaultScheduleShowsTodayWeekReviewWindowsAndMockExam",
    "scheduleNavigationDoesNotMutatePlanData",
    "scheduleFocusCommandsAndCastStateUseSharedProtocol",
):
    require(test, token, "schedule tests")

require(protocol, "SCHEDULE", "shared control protocol")
require(nav, "StudyFlowTvDestination.SCHEDULE", "navigation model")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvReadOnlySchedule")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov13_read_only_schedule.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvReadOnlySchedule", "studyflow_tv target")
require(roadmap, "TVOV13  Read-only schedule [implemented]", "roadmap status")
require(roadmap, "## TVOV13 — Read-only schedule", "roadmap implementation note")
require(quality, "read-only schedule", "quality contract")
require(notes, "TVOV13 implements the Android TV read-only schedule surface", "implementation note")
require(readme, "TVOV13 and TVOV14 add the Android TV read-only Schedule", "README")
require(changelog, "TVOV13/TVOV14 - Android TV Schedule", "CHANGELOG")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    "androidx.room",
    "RoomDatabase",
    "@Dao",
    "ContentResolver",
    "ACTION_OPEN_DOCUMENT",
    "ACTION_CREATE_DOCUMENT",
    'project(":core:data")',
    'project(":core:database")',
    'project(":feature:',
):
    forbid(schedule + app_build, forbidden, "TVOV13 dependency/read-only boundary")

print("TVOV13_READ_ONLY_SCHEDULE: PASS")
