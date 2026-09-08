#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"TVOV09_QUESTION_PRACTICE: FAIL: {message}")


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

practice = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/practice/StudyFlowTvQuestionPractice.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/practice/StudyFlowTvQuestionPracticeTest.kt")
protocol = read("core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt")
app_build = read("app-tv/build.gradle.kts")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV09_QUESTION_PRACTICE_CONTRACTS.md")
notes = read("TVOV09_ANDROID_TV_QUESTION_PRACTICE.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    'const val overlay = "TVOV09"',
    "ownsQuestionPracticeSurface = true",
    "remoteCanSelectAnswersAndRevealExplanation = true",
    "pairedPhoneCanSelectAnswersAndNavigateQuestions = true",
    "emitsCastStateWithoutMirroring = true",
    "StudyFlowTvPracticeAction.SELECT_A",
    "StudyFlowTvPracticeAction.SHOW_EXPLANATION",
    "StudyFlowTvPracticeAction.FINISH_SET",
    "fun applyAction",
    "fun focusItems",
    "StudyFlowTvFocusRole.QUESTION_CHOICE",
    "fun commandForAction",
    "StudyFlowTvControlAction.SELECT_ANSWER",
    "StudyFlowTvControlAction.SHOW_EXPLANATION",
    "fun castStateFor",
    "StudyFlowTvSurface.QUESTION_SET",
):
    require(practice, token, "question practice model")

for token in (
    "private fun renderQuestionPracticeDestination()",
    "StudyFlowTvDestination.PRACTICE -> renderQuestionPracticeDestination()",
    "StudyFlowTvQuestionPractice.defaultState()",
    "StudyFlowTvQuestionPractice.availableActions",
    "StudyFlowTvQuestionPractice.applyAction",
    "private fun tvPracticeChoice(choice: StudyFlowTvPracticeChoice): Button",
    "private fun tvPracticeAction(action: StudyFlowTvPracticeAction): Button",
):
    require(activity, token, "TV activity practice rendering")

for token in (
    "tv_practice_section_title",
    "tv_practice_status_format",
    "tv_practice_answer_pending",
    "tv_practice_answer_correct",
    "tv_practice_answer_incorrect",
    "tv_practice_choice_format",
    "tv_practice_explanation_format",
    "tv_practice_action_select_a",
    "tv_practice_action_explanation",
    "tv_practice_action_finish",
):
    require(strings, token, "practice strings")

for token in (
    "object StudyFlowTvQuestionPracticeBuildContract",
    "questionPracticeIntroduced = true",
    "questionPracticeRequiresNoComposeRuntime = true",
    "questionPracticeUsesSharedControlProtocol = true",
    "questionPracticeSupportsRemoteAndPhoneControls = true",
    "questionPracticeEmitsCastState = true",
    "questionPracticeQuestionCount = 3",
    "questionPracticeChoiceCount = 4",
    "questionPracticeActionCount = 8",
):
    require(contract, token, "practice build contract")
    if " = " in token:
        require(build_test, token.split(" = ")[0], "practice build contract test")

for token in (
    "questionPracticeOwnsAnswerSelectionExplanationAndPhoneControl",
    "defaultPracticeSetContainsRenderableQuestionsAndChoices",
    "practiceStateSelectsRevealsAdvancesAndCompletes",
    "practiceFocusCommandsAndCastStateUseSharedProtocols",
):
    require(test, token, "practice tests")

require(protocol, "SHOW_EXPLANATION", "shared control protocol")
require(root_gradle, 'tasks.register<Exec>("verifyStudyFlowTvQuestionPractice")', "root Gradle task")
require(root_gradle, "scripts/verify_tvov09_question_practice.py", "root Gradle task")
require(devtool, "verifyStudyFlowTvQuestionPractice", "studyflow_tv target")
require(roadmap, "TVOV09  Question practice [implemented]", "roadmap status")
require(roadmap, "## TVOV09 — Question practice", "roadmap implementation note")
require(quality, "Answer choices are D-pad focusable", "quality contract")
require(notes, "TVOV09 implements the Android TV question-practice surface", "implementation note")
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
    forbid(practice + app_build, forbidden, "TVOV09 dependency boundary")

print("TVOV09_QUESTION_PRACTICE: PASS")
