#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


policy = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/accessibility/StudyFlowTvAccessibilityPolicy.kt")
view_helper = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/accessibility/StudyFlowTvAccessibilityView.kt")
focus_view = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/focus/StudyFlowTvFocusView.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
high_focus = read("app-tv/src/main/res/drawable/studyflow_tv_focus_background_high_visibility.xml")
contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
policy_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/accessibility/StudyFlowTvAccessibilityPolicyTest.kt")
build_test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContractTest.kt")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
quality = read("docs/quality/TVOV17_ACCESSIBILITY_10_FOOT_UI_CONTRACTS.md")
notes = read("TVOV17_ANDROID_TV_ACCESSIBILITY_10_FOOT_UI_SEAL.md")
readme = read("README.md")

for token in (
    'const val overlay = "TVOV17"',
    "tenFootUiSealed = true",
    "scrollableFocusedContentRequired = true",
    "semanticHeadingsRequired = true",
    "politeLiveStatusRequired = true",
    "highVisibilityFocusMustAffectRendering = true",
    "minimumFocusTargetDp = 64",
    "minimumTitleTextSp = 34",
    "minimumBodyTextSp = 20",
    "minimumControlTextSp = 18",
    "minimumContrastRatio = 4.5",
    "highVisibilityFocusStrokeDp = 5",
    "fun contrastRatio",
    "fun meetsTextContrast",
):
    require(token in policy, f"TVOV17 accessibility policy missing token: {token}")

for token in (
    "fun TextView.applyStudyFlowTvTenFootText",
    "IMPORTANT_FOR_ACCESSIBILITY_YES",
    "isAccessibilityHeading = true",
):
    require(token in view_helper, f"TVOV17 native accessibility helper missing token: {token}")

for token in (
    "fun View.applyStudyFlowTvDpadFocus(highVisibility: Boolean)",
    "studyflow_tv_focus_background_high_visibility",
):
    require(token in focus_view, f"TVOV17 focus rendering missing token: {token}")

for token in (
    "ScrollView(this)",
    "isFillViewport = true",
    "descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS",
    "applyStudyFlowTvTenFootText(StudyFlowTvTextRole.TITLE, heading = true)",
    "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE",
    "applyStudyFlowTvDpadFocus(highVisibility = settingsState.highVisibilityFocus)",
    "refreshAccessibilityProfile()",
):
    require(token in activity, f"TVOV17 shell integration missing token: {token}")

for token in (
    'android:state_focused="true"',
    'android:width="5dp"',
    'android:color="#FDE047"',
):
    require(token in high_focus, f"TVOV17 high-visibility drawable missing token: {token}")

for token in (
    "accessibilityTenFootSealIntroduced = true",
    "scrollableFocusedContentRequired = true",
    "highVisibilityFocusAffectsRendering = true",
    "minimumBodyTextSp = 20",
    "minimumControlTextSp = 18",
):
    require(token in contract, f"TVOV17 build contract missing token: {token}")
    require(token.split(" = ")[0] in build_test, f"TVOV17 build contract test missing assertion: {token}")

for token in (
    "tenFootTypographyAndTargetsStayAboveTheSealMinimums",
    "shellPaletteMeetsTextContrastAndHighVisibilityFocusIsStronger",
    "meetsTextContrast",
):
    require(token in policy_test, f"TVOV17 policy test missing token: {token}")

for forbidden in (
    "androidx.compose",
    "androidx.tv",
    "CastContext",
    "androidx.mediarouter",
    'project(":core:data")',
    'project(":core:database")',
    'project(":feature:',
):
    require(forbidden not in policy + view_helper + activity, f"TVOV17 dependency boundary violated by {forbidden}")

require('tasks.register<Exec>("verifyStudyFlowTvAccessibilityTenFootUi")' in root_gradle, "missing TVOV17 Gradle verifier task")
require("verifyStudyFlowTvAccessibilityTenFootUi" in devtool, "studyflow_tv target missing TVOV17 verifier")
require("TVOV17  Accessibility and 10-foot UI seal [implemented]" in roadmap, "roadmap must mark TVOV17 implemented")
require("TVOV17  Accessibility and 10-foot UI seal [implemented]" in rules, "working rules must mark TVOV17 implemented")
require("## TVOV17" in roadmap, "roadmap missing TVOV17 implementation note")
require("10-foot" in quality and "64dp" in quality and "5dp yellow focus ring" in quality, "TVOV17 quality contract incomplete")
require("TVOV17 converts the earlier focus baseline" in notes, "TVOV17 implementation note incomplete")
require("TVOV17 and TVOV18" in readme, "README missing TVOV17/18 summary")

if FAILURES:
    print("TVOV17_ACCESSIBILITY_10_FOOT_UI: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV17_ACCESSIBILITY_10_FOOT_UI: PASS")
