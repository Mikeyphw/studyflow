#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def fail(message: str) -> None:
    raise SystemExit(f"TVOV05_NAVIGATION_SHELL: FAIL: {message}")

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        fail(f"missing {path}")
    return p.read_text()

def require(text: str, needle: str, context: str) -> None:
    if needle not in text:
        fail(f"{context} missing {needle!r}")

nav = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShell.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
app_gradle = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
note = read("TVOV05_ANDROID_TV_NAVIGATION_SHELL.md")
quality = read("docs/quality/TVOV05_NAVIGATION_SHELL_CONTRACTS.md")
tests = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShellTest.kt")

for destination in ["HOME", "TODAY", "PRACTICE", "REVIEWS", "MOCK_EXAM", "PROGRESS", "SCHEDULE", "SETTINGS"]:
    require(nav, destination, "navigation destination set")
require(nav, "StudyFlowTvNavigationShell", "navigation shell")
require(nav, "StudyFlowTvControlCommand", "phone control command bridge")
require(nav, "StudyFlowTvFocusItem", "focus bridge")
require(nav, "routePayloadsUseSmallEnums = true", "route contract")
require(nav, "destinationCount = 8", "destination count contract")
require(nav, "openCommandFor", "paired-phone open command")

require(activity, "StudyFlowTvNavigationShell.primaryDestinations", "activity nav rail")
require(activity, "dispatchKeyEvent", "activity Back handling")
require(activity, "StudyFlowTvRemoteKeyMapper.commandForKeyCode", "activity remote command mapping")
require(activity, "button.isSelected = itemDestination == destination", "activity active destination state")
require(activity, "nextFocusRightId", "activity D-pad bridge")

require(contract, 'navigationShellIntroduced = true', "build contract")
require(contract, "navigationShellIntroduced = true", "build contract")
require(contract, "navigationShellRequiresNoComposeRuntime = true", "build contract")
require(contract, "navigationShellUsesSharedControlProtocol = true", "build contract")

for string_name in [
    "tv_nav_home", "tv_nav_today", "tv_nav_practice", "tv_nav_reviews",
    "tv_nav_mock_exam", "tv_nav_progress", "tv_nav_schedule", "tv_nav_settings",
    "tv_content_title_home", "tv_content_body_settings", "tv_shell_remote_hint",
]:
    require(strings, f'name="{string_name}"', "TV strings")

for forbidden in [
    "androidx.tv", "androidx.compose.runtime", "androidx.mediarouter", "play-services-cast",
    'project(":core:data")', 'project(":core:database")', 'project(":feature:',
]:
    if forbidden in app_gradle:
        fail(f"app-tv dependency budget regressed with {forbidden}")

require(root_gradle, "verifyStudyFlowTvNavigationShell", "root Gradle task")
require(devtool, "verifyStudyFlowTvNavigationShell", "studyflow_tv Devtool target")
require(roadmap, "TVOV05  TV navigation shell [implemented]", "roadmap status")
require(note, "TVOV05", "implementation note")
require(quality, "navigation shell", "quality contract")
require(tests, "StudyFlowTvNavigationShellTest", "unit tests")

print("TVOV05_NAVIGATION_SHELL: PASS")
