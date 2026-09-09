#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        FAILURES.append(f"missing required file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def require_tokens(rel: str, *tokens: str) -> str:
    text = read(rel)
    for token in tokens:
        require(token in text, f"{rel} missing token: {token}")
    return text


schema = require_tokens(
    "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentation.kt",
    "object StudyFlowTvPresentationProtocol",
    "data class StudyFlowTvPresentationSnapshot",
    "StudyFlowTvTodayPresentation",
    "StudyFlowTvStudySessionPresentation",
    "StudyFlowTvQuestionSetPresentation",
    "StudyFlowTvMockExamPresentation",
    "StudyFlowTvReviewPresentation",
    "StudyFlowTvProgressPresentation",
    "StudyFlowTvSchedulePresentation",
    "StudyFlowTvLibraryPresentation",
    "rawDatabaseRowsForbidden = true",
    "arbitraryUiTreesForbidden = true",
    "transportStillDeferred = false",
)
require("@Serializable" in schema, "typed TV presentation schema must remain serializable")
require_tokens(
    "core/tvcontrol/src/test/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvPresentationTest.kt",
    "typedPresentationSnapshotRoundTrips",
    "waitingSnapshotIsExplicitAndContainsNoFakeStudyContent",
)

projector = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjector.kt",
    'const val overlay = "TVOV21"',
    "projectsCanonicalDomainModels = true",
    "databaseRowsNeverCrossBoundary = true",
    "transportDeferred = false",
    "fun project(input: StudyFlowTvPresentationProjectionInput)",
    "input.schedule.plannedActivities",
    "input.analytics",
    "input.questions",
    "input.materials",
)
source = require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationSource.kt",
    "class StudyFlowTvPresentationSource",
    "StudyPlanRepository",
    "StudyContentRepository",
    "StudyScheduleRepository",
    "StudySessionRepository",
    "StudyPracticeRepository",
    "StudyReviewRepository",
    "StudyAnalyticsRepository",
    "StudyLibraryRepository",
    "StudyFlowTvPresentationProjector.project",
)
require("Socket" not in source and "WebSocket" not in source and "CastContext" not in source, "TVOV21 source must stay transport-free")
require_tokens(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "val tvPresentationSource: StudyFlowTvPresentationSource",
    "override val tvPresentationSource = StudyFlowTvPresentationSource(",
)
require_tokens(
    "app/src/test/java/com/mikeyphw/studyflow/tvcontrol/StudyFlowTvPresentationProjectorTest.kt",
    "projectsCanonicalDomainIdsAndContentIntoTypedSnapshot",
    "missingPlanProducesWaitingSnapshotWithoutStudyFixtures",
)

adapter = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapter.kt",
    'const val overlay = "TVOV21"',
    "consumesTypedPresentationOnly = true",
    "runtimeFixtureDefaultsForbidden = true",
    "databaseAndRepositoryDependenciesForbidden = true",
    "class StudyFlowTvPresentationInbox",
    "snapshot.generatedAtEpochMillis < previous.generatedAtEpochMillis",
)
activity = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
    "StudyFlowTvPresentationAdapter.waitingState()",
    "private val presentationInbox = StudyFlowTvPresentationInbox()",
    "internal fun applyPresentationSnapshot(snapshot: StudyFlowTvPresentationSnapshot): Boolean",
    "private fun hasPresentationFor(destination: StudyFlowTvDestination): Boolean",
    "private fun renderPresentationUnavailableDestination()",
)
for forbidden in (
    "StudyFlowTvTodayWorkspace.defaultSnapshot()",
    "StudyFlowTvStudySessionExperience.defaultState()",
    "StudyFlowTvQuestionPractice.defaultState()",
    "StudyFlowTvMockExamStudio.defaultState()",
    "StudyFlowTvReviewNotebook.defaultState()",
    "StudyFlowTvProgressAnalytics.defaultSnapshot()",
    "StudyFlowTvScheduleSurface.defaultSnapshot()",
    "StudyFlowTvLibrarySurface.defaultSnapshot()",
):
    require(forbidden not in activity, f"TV Activity must not use runtime fixture source: {forbidden}")

app_tv_build = read("app-tv/build.gradle.kts")
require('implementation(project(":core:tvcontrol"))' in app_tv_build, "app-tv must keep shared presentation protocol dependency")
for forbidden in (':core:data', ':core:database', ':feature:', 'androidx.tv', 'play-services-cast'):
    require(forbidden not in app_tv_build, f"app-tv dependency boundary regressed: {forbidden}")
require('versionCode = 26' in app_tv_build and 'versionName = "0.1.0-tv26"' in app_tv_build, "TVOV21 version not installed")
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/presentation/StudyFlowTvPresentationAdapterTest.kt",
    "typedSnapshotKeepsCanonicalSourceIdsWhenAdaptedToTvSlots",
    "inboxRejectsDuplicateAndOlderPresentationSnapshots",
    "waitingStateIsExplicitAndContainsNoHistoricalExamFixture",
)

require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
    'currentTvOverlay = "TVOV26"',
    "object StudyFlowTvPresentationBuildContract",
    "typedPresentationProtocolIntroduced = true",
    "phoneOwnsCanonicalProjection = true",
    "tvRuntimeFixtureDefaultsDisabled = true",
    "presentationTransportStillDeferred = false",
    "projectedSurfaceCount = 8",
)
require_tokens(
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt",
    "typedPresentationProjectionUsesCanonicalPhoneDataWithoutTvRepositories",
)

home = require_tokens(
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/home/StudyFlowTvHomeSurface.kt",
    "StudyFlowTvHomeAction.PAIR_PHONE",
    "surface = StudyFlowTvSurface.SETTINGS",
    'targetId = "manage-pairing"',
)
require('targetId = "pair-phone"' not in home, "PAIR_PHONE protocol command must not route back to HOME")

root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
quality = read("docs/quality/TVOV21_TYPED_PRESENTATION_PROJECTION_CONTRACTS.md")
notes = read("TVOV21_ANDROID_TV_TYPED_PRESENTATION_PROJECTION.md")
require('tasks.register<Exec>("verifyStudyFlowTvPresentationProjection")' in root_gradle, "missing TVOV21 Gradle verifier task")
require(devtool.count('"verifyStudyFlowTvPresentationProjection"') >= 8, "studyflow_tv must wire TVOV21 across all validation phases")
require("TVOV21  Typed presentation snapshots and canonical data projection [implemented]" in roadmap, "roadmap must mark TVOV21 implemented")
require("TVOV22  Local pairing, discovery, and transport [implemented]" in roadmap, "roadmap must mark TVOV22 implemented")
require("TVOV21  Typed presentation snapshots and canonical data projection [implemented]" in rules, "working rules must mark TVOV21 implemented")
require("TVOV22  Local pairing, discovery, and transport [implemented]" in rules, "working rules must mark TVOV22 implemented")
require("TVOV00 through TVOV26" in rules, "working rules must record TVOV00 through TVOV26")
require("typed presentation" in readme.lower(), "README missing TVOV21 typed-presentation summary")
require("canonical" in quality.lower() and "fixture" in quality.lower() and "transport" in quality.lower(), "TVOV21 quality contract incomplete")
require("demo fixture" in notes.lower() and "TVOV22" in notes, "TVOV21 implementation note incomplete")

if FAILURES:
    print("TVOV21_PRESENTATION_PROJECTION: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV21_PRESENTATION_PROJECTION: PASS")
