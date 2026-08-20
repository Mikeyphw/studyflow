#!/usr/bin/env python3
"""Fail-closed source contract for the StudyFlow Android Focus Shell UXOV-01..04-R2 series."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"ANDROID_FOCUS_SHELL_R2_FAIL: missing {relative}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ANDROID_FOCUS_SHELL_R2_FAIL: {message}")


def absent(text: str, tokens: tuple[str, ...], owner: str) -> None:
    for token in tokens:
        require(token not in text, f"{owner} still contains retired token {token!r}")


navigation = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
shell = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
motion = read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowMotion.kt"
)
shell_primitives = read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt"
)
workspace_components = read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt"
)
progress_primitives = read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowProgressPrimitives.kt"
)
preferences = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/AppPreferences.kt")
preferences_repo = read(
    "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt"
)
focus = read(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/FocusSessionScreen.kt"
)
common = read(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt"
)
utility = read(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceUtilityScreens.kt"
)
progress = read(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt"
)
restoration_test = read(
    "feature/workspace/src/androidTest/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceAccessibilityTest.kt"
)
theme = read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowTheme.kt"
)

# UXOV-01: custom root shell and canonical workspace contract.
absent(navigation, ("NavigationSuiteScaffold", "NavigationBar(", "NavigationRail("), "navigation")
for root in ("TODAY", "PLAN", "PRACTICE", "PROGRESS"):
    require(f"StudyFlowWorkspaceRoot.{root}" in navigation, f"missing canonical root {root}")
require("StudyFlowCompactDock(" in shell, "custom compact dock is not wired")
require("StudyFlowExpandedRail(" in shell, "custom expanded rail is not wired")
require("Role.Tab" in shell_primitives, "custom navigation lacks tab semantics")

# UXOV-02/03/04: workspace roots are shell-native rather than nested Material scaffolds.
for relative in (
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
):
    source = read(relative)
    absent(source, ("Scaffold(",), relative)

# Retire the visible legacy workspace wrappers and stock Material tab language.
absent(
    workspace_components,
    (
        "StudyFlowPrimaryActionCard",
        "StudyFlowCoachCard",
        "StudyFlowWorkspaceTabs",
        "PrimaryScrollableTabRow(",
        "AssistChip(",
        "ElevatedCard(",
    ),
    "StudyFlowWorkspaceComponents",
)
require("StudyFlowTabStrip(" in read(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowFocusWorkspacePrimitives.kt"
), "custom StudyFlow tab strip is missing")
absent(common, ("WorkspaceUtilityScaffold", "Scaffold(", "TopAppBar("), "WorkspaceCommon")
require("WorkspaceUtilityPane(" in common, "utility pane replacement is missing")

# UXOV-03: session state is authoritative, Focus is immersive, and Quick Add is shell-native.
require("observeActiveSessions()" in navigation, "active-session shell is not repository-backed")
require("StudyFlowActiveSessionBar(" in shell, "active-session bar is not wired")
require("studyflow-session-dock-morph" in shell, "active-session dock morph animation is missing")
require("import androidx.compose.runtime.getValue" in shell, "Compose State delegation import is missing")
require("private fun StudyFlowExpandedSessionBar(" in shell, "expanded session visibility must be isolated from RowScope")
require("PredictiveBackHandler(" in focus, "Focus does not use predictive back")
absent(focus, ("TopAppBar(", "Scaffold("), "FocusSessionScreen")
require("AnimatedVisibility(" in utility, "Quick Add entrance animation is missing")
require("slideInVertically(" in utility, "Quick Add does not expand from the lower shell")

# UXOV-04-R2: custom Progress + supporting pane + accessible progress semantics.
absent(progress, ("FilterChip(", "Scaffold("), "ProgressWorkspaceScreen")
for token in (
    "StudyFlowPeriodStrip(",
    "StudyFlowAdaptiveWorkspaceFrame(",
    "StudyFlowProgressBand(",
    "StudyFlowInsightPanel(",
):
    require(token in progress, f"Progress is missing {token}")
require("SupportingPaneBreakpoint = 760.dp" in progress_primitives, "expanded-pane breakpoint missing")
require("progressBarRangeInfo" in progress_primitives, "progress semantics missing")
require("Role.Tab" in progress_primitives, "period selector lacks tab semantics")

# Motion is deliberate and respects the system animator scale.
require("ANIMATOR_DURATION_SCALE" in motion, "reduced-motion system setting is not honored")
require("snap()" in motion, "reduced-motion fallback is not instantaneous")
require("slideInHorizontally(" in app, "workspace transition motion is missing")
require("studyflow-plan-switcher-scale" in workspace_components, "plan-switcher motion is missing")
require("studyflow-plan-switcher-arrow" in workspace_components, "plan-switcher affordance motion is missing")
require("studyflow-plan-switcher-expansion" in workspace_components, "plan-switcher expansion motion is missing")

# Branded StudyFlow palette is the default; dynamic color remains an explicit optional preference.
require("dynamicColorEnabled: Boolean = false" in preferences, "dynamic color is still default-on")
require("?: false" in preferences_repo, "stored-preference fallback still defaults dynamic color on")
require("dynamicColorEnabled && Build.VERSION.SDK_INT" in theme, "dynamic color is no longer optional")

# State restoration and accessibility coverage remain present after the redesign.
require("StateRestorationTester" in restoration_test, "workspace state-restoration instrumentation is missing")
require("emulateSavedInstanceStateRestore" in restoration_test, "restoration test does not exercise saved state")
require("StudyFlowTabStrip" in restoration_test, "workspace accessibility test does not exercise custom tabs")
require("assertIsSelected" in restoration_test, "workspace accessibility test does not assert selected semantics")

# Android/JVM unit-test source readers must avoid java.nio.file.Files.readString,
# which is unavailable on the Android compile stubs used by these modules.
for relative in (
    "app/src/test/java/com/mikeyphw/studyflow/StudyFlowShellFoundationContractTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/DomainLocalizationGuardTest.kt",
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/AndroidFocusShellFinalSealContractTest.kt",
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/FocusWorkspaceRedesignContractTest.kt",
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/PracticeFocusExperienceContractTest.kt",
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceRedesignContractTest.kt",
):
    test_source = read(relative)
    absent(test_source, ("Files.readString",), relative)
    require("readText()" in test_source, f"{relative} does not use the compatible File.readText reader")

print("ANDROID_FOCUS_SHELL_R2_VALIDATION_OK")
print(
    "custom shell, branded default, motion/reduced-motion, predictive back, "
    "adaptive Progress, and restoration contracts verified"
)
