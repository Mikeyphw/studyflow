#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV12_PROGRESS_ANALYTICS: FAIL: {message}")


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

progress = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/progress/StudyFlowTvProgressAnalytics.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/progress/StudyFlowTvProgressAnalyticsTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV12_PROGRESS_ANALYTICS_CONTRACTS.md")
notes = read("TVOV12_ANDROID_TV_PROGRESS_ANALYTICS.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    ('const val overlay = "TVOV12"' if 'const val overlay = "TVOV12"' in progress else 'const val overlay = "TVOV28"'),
    "ownsProgressAnalyticsSurface = true",
    "weeklyProgressAccuracyVolumeReadinessVisible = true",
    "weakSubjectSummaryVisible = true",
    "remoteCanOpenRecommendedNextActions = true",
    "pairedPhoneCanOpenProgressDestinations = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvProgressMetricId.WEEKLY_PROGRESS",
    "StudyFlowTvProgressMetricId.ACCURACY",
    "StudyFlowTvProgressMetricId.QUESTION_VOLUME",
    "StudyFlowTvProgressMetricId.READINESS",
    "weakestSubject",
    "fun focusItems",
    "fun destinationForAction",
    "fun commandForAction",
    "fun castStateFor",
    "StudyFlowTvSurface.PROGRESS",
):
    require(progress, token, "progress analytics model")

for token in (
    "private fun renderProgressDestination()",
    "StudyFlowTvDestination.PROGRESS -> renderProgressDestination()",
    "private var progressSnapshot: StudyFlowTvProgressSnapshot",
    "StudyFlowTvPresentationAdapter.waitingState()",
    "StudyFlowTvProgressAnalytics.actions",
    "private fun tvProgressMetric(metric: StudyFlowTvProgressMetric): Button",
    "private fun tvProgressSubject(subject: StudyFlowTvProgressSubject): Button",
    "private fun tvProgressAction(action: StudyFlowTvProgressAction): Button",
):
    require(activity, token, "TV activity progress rendering")

for token in (
    "tv_progress_section_title",
    "tv_progress_weak_subjects_title",
    "tv_progress_status_format",
    "tv_progress_metric_body_format",
    "tv_progress_subject_body_format",
    "tv_progress_action_reviews",
    "tv_progress_action_mock",
):
    require(strings, token, "progress strings")

for token in (
    "object StudyFlowTvProgressAnalyticsBuildContract",
    "progressAnalyticsIntroduced = true",
    "progressAnalyticsRequiresNoComposeRuntime = true",
    "progressAnalyticsUsesSharedControlProtocol = true",
    "progressAnalyticsSupportsRemoteAndPhoneControls = true",
    "progressAnalyticsEmitsCastState = true",
    "progressAnalyticsMetricCount = 4",
    "progressAnalyticsSubjectCount = 4",
    "progressAnalyticsActionCount = 4",
):
    require(contract, token, "progress build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "progress build contract test")

for token in (
    "progressAnalyticsOwnsRoadmapMetricAndWeakSubjectContract",
    "defaultProgressSnapshotContainsMetricsSubjectsAndWeakestSubject",
    "progressActionsMapToConcreteTvDestinations",
    "progressFocusCommandsAndCastStateUseSharedProtocols",
):
    require(test, token, "progress tests")

for token in ("PROGRESS", "OPEN"):
    require(protocol, token, "shared control protocol")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvProgressAnalytics")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov12_progress_analytics.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvProgressAnalytics", "studyflow_tv target")
require(roadmap, "TVOV12  Progress and analytics [implemented]", "roadmap status")
require(roadmap, "## TVOV12 — Progress and analytics", "roadmap implementation note")
require(quality, "weekly progress, accuracy, question-volume, readiness", "quality contract")
require(notes, "TVOV12 implements the Android TV Progress and analytics surface", "implementation note")
require(readme, "TVOV11 and TVOV12 add the Android TV Review/Error Notebook", "README")
require(changelog, "TVOV11/TVOV12 - Android TV Review Notebook", "CHANGELOG")

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
    forbid(progress + app_build, forbidden, "TVOV12 dependency boundary")

print("TVOV12_PROGRESS_ANALYTICS: PASS")
