#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV11_REVIEW_NOTEBOOK: FAIL: {message}")


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

review = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebook.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/reviews/StudyFlowTvReviewNotebookTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV11_REVIEW_ERROR_NOTEBOOK_CONTRACTS.md")
notes = read("TVOV11_ANDROID_TV_REVIEW_ERROR_NOTEBOOK.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV11"',
    "ownsReviewAndErrorNotebookSurface = true",
    "spacedReviewQueueVisible = true",
    "errorNotebookCanBeOpenedFromReview = true",
    "remoteCanReviewMarkKnownExplainAndComplete = true",
    "pairedPhoneCanControlReviewNotebook = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvReviewStatus.READY",
    "StudyFlowTvReviewStatus.REVIEWING",
    "StudyFlowTvReviewStatus.EXPLANATION",
    "StudyFlowTvReviewStatus.ERROR_NOTEBOOK",
    "StudyFlowTvReviewStatus.COMPLETED",
    "StudyFlowTvReviewAction.ADD_ERROR_NOTE",
    "StudyFlowTvReviewAction.SHOW_EXPLANATION",
    "fun applyAction",
    "fun focusItems",
    "fun commandForAction",
    "StudyFlowTvControlAction.MARK_FOR_REVIEW",
    "fun castStateFor",
    "StudyFlowTvSurface.REVIEW",
):
    require(review, token, "review notebook model")

for token in (
    "private fun renderReviewDestination()",
    "StudyFlowTvDestination.REVIEWS -> renderReviewDestination()",
    "private var reviewState: StudyFlowTvReviewState",
    "StudyFlowTvPresentationAdapter.waitingState()",
    "StudyFlowTvReviewNotebook.availableActions",
    "StudyFlowTvReviewNotebook.commandForAction",
    "private fun tvReviewItem(item: StudyFlowTvReviewItem): Button",
    "private fun tvReviewAction(action: StudyFlowTvReviewAction): Button",
):
    require(activity, token, "TV activity review rendering")

for token in (
    "tv_reviews_section_title",
    "tv_reviews_status_format",
    "tv_reviews_card_body_format",
    "tv_reviews_note_format",
    "tv_reviews_action_error_note",
    "tv_reviews_action_explain",
    "tv_reviews_action_complete",
):
    require(strings, token, "review strings")

for token in (
    "object StudyFlowTvReviewNotebookBuildContract",
    "reviewNotebookIntroduced = true",
    "reviewNotebookRequiresNoComposeRuntime = true",
    "reviewNotebookUsesSharedControlProtocol = true",
    "reviewNotebookSupportsRemoteAndPhoneControls = true",
    "reviewNotebookEmitsCastState = true",
    "reviewNotebookItemCount = 4",
    "reviewNotebookActionCount = 7",
):
    require(contract, token, "review build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "review build contract test")

for token in (
    "reviewNotebookOwnsSpacedReviewAndErrorNotebookRoadmapContract",
    "defaultReviewNotebookContainsDueItemsConfidenceAndReasons",
    "reviewNotebookStateStartsExplainsAddsErrorNotesAdvancesAndCompletes",
    "reviewNotebookFocusCommandsCastStateAndDestinationMappingUseSharedProtocols",
):
    require(test, token, "review tests")

for token in ("REVIEW", "MARK_FOR_REVIEW", "SHOW_EXPLANATION", "COMPLETE"):
    require(protocol, token, "shared control protocol")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvReviewNotebook")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov11_review_notebook.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvReviewNotebook", "studyflow_tv target")
require(roadmap, "TVOV11  Review and error notebook [implemented]", "roadmap status")
require(roadmap, "## TVOV11 — Review and error notebook", "roadmap implementation note")
require(quality, "ready/reviewing/explanation/error-notebook/completed", "quality contract")
require(notes, "TVOV11 implements the Android TV Review and error-notebook surface", "implementation note")
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
    forbid(review + app_build, forbidden, "TVOV11 dependency boundary")

print("TVOV11_REVIEW_NOTEBOOK: PASS")
