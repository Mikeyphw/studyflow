#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV14_LIBRARY_REFERENCE: FAIL: {message}")


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

library = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/library/StudyFlowTvLibrarySurface.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/library/StudyFlowTvLibrarySurfaceTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
nav = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShell.kt")
nav_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/navigation/StudyFlowTvNavigationShellTest.kt")
protocol_test = read("core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocolTest.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV14_LIBRARY_REFERENCE_CONTRACTS.md")
notes = read("TVOV14_ANDROID_TV_LIBRARY_REFERENCE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV14"',
    "ownsLimitedLibraryReferenceSurface = true",
    "curatedReferenceCardsVisible = true",
    "shortReadableExcerptsVisible = true",
    "fileManagementDisabledOnTv = true",
    "pairedPhoneCanOpenReferencesButNotManageFiles = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvLibraryReferenceKind.LEGISLATION",
    "StudyFlowTvLibraryReferenceKind.ERROR_NOTEBOOK_EXCERPT",
    "fun applyAction",
    "fun destinationForAction",
    "fun focusItems",
    "fun commandForAction",
    "fun castStateFor",
    "StudyFlowTvSurface.LIBRARY",
):
    require(library, token, "library model")


if activity.count("private var scheduleSnapshot: StudyFlowTvScheduleSnapshot") != 1:
    fail("TV activity must own exactly one schedule snapshot property")
if activity.count("private var librarySnapshot: StudyFlowTvLibrarySnapshot") != 1:
    fail("TV activity must own exactly one library snapshot property")

for token in (
    "private fun renderLibraryDestination()",
    "StudyFlowTvDestination.LIBRARY -> renderLibraryDestination()",
    "StudyFlowTvLibrarySurface.defaultSnapshot()",
    "StudyFlowTvLibrarySurface.actions",
    "private fun tvLibraryReference(reference: StudyFlowTvLibraryReference): Button",
    "private fun tvLibraryAction(action: StudyFlowTvLibraryAction): Button",
):
    require(activity, token, "TV activity library rendering")

for token in (
    "tv_nav_library",
    "tv_content_title_library",
    "tv_content_body_library",
    "tv_library_section_title",
    "tv_library_status_format",
    "tv_library_reference_body_format",
    "tv_library_active_excerpt_format",
    "tv_library_action_open",
    "tv_library_action_practice",
):
    require(strings, token, "library strings")

for token in (
    "object StudyFlowTvLibraryBuildContract",
    "limitedLibraryReferenceIntroduced = true",
    "libraryRequiresNoComposeRuntime = true",
    "libraryUsesSharedControlProtocol = true",
    "librarySupportsRemoteAndPhoneNavigation = true",
    "libraryFileManagementDisabledOnTv = true",
    "libraryEmitsCastState = true",
    "libraryReferenceCount = 4",
    "libraryActionCount = 5",
):
    require(contract, token, "library build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "library build contract test")

for token in (
    "libraryOwnsRoadmapLimitedReferenceContract",
    "defaultLibraryContainsCuratedReferenceMaterialOnly",
    "libraryActionsNavigateWithoutManagingFiles",
    "libraryFocusCommandsAndCastStateUseSharedProtocol",
):
    require(test, token, "library tests")

for token in (
    'LIBRARY("library", StudyFlowTvSurface.LIBRARY)',
    "destinationCount = 9",
    "allDestinationCount = 10",
    "StudyFlowTvDestination.LIBRARY",
):
    require(nav, token, "navigation model")
require(nav_test, "StudyFlowTvDestination.LIBRARY", "navigation test")
require(protocol, "StudyFlowTvSurface.LIBRARY", "shared control protocol")
require(protocol_test, "StudyFlowTvSurface.LIBRARY", "protocol test")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvLibraryReference")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov14_library_reference.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvLibraryReference", "studyflow_tv target")
require(roadmap, "TVOV14  Limited library/reference material [implemented]", "roadmap status")
require(roadmap, "## TVOV14 — Limited library/reference material", "roadmap implementation note")
require(quality, "limited library/reference", "quality contract")
require(notes, "TVOV14 implements the Android TV limited library/reference material surface", "implementation note")
require(readme, "TVOV13 and TVOV14 add the Android TV read-only Schedule", "README")
require(changelog, "TVOV13/TVOV14 - Android TV Schedule", "CHANGELOG")
require(app_build, 'versionName = "0.1.0-tv14"', "app-tv version")

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
    "DocumentFile",
    'project(":core:data")',
    'project(":core:database")',
    'project(":feature:',
):
    forbid(library + app_build, forbidden, "TVOV14 dependency/file-management boundary")

print("TVOV14_LIBRARY_REFERENCE: PASS")
