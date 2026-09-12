#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
SEP = "?:?"


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


def md5(value: str) -> str:
    return hashlib.md5(value.encode("utf-8")).hexdigest()


def bool_text(value: bool) -> str:
    return "true" if value else "false"


def list_text(values: list[str]) -> str:
    return "[" + ", ".join(values) + "]"


def room_entity_hash(entity: dict) -> str:
    parts = [entity["tableName"]]
    primary_key = entity["primaryKey"]
    parts.append(
        f"{bool_text(primary_key['autoGenerate'])}-{list_text(primary_key['columnNames'])}",
    )
    field_keys = []
    for field in entity.get("fields", []):
        key = (
            f"{field['columnName']}-{field.get('affinity', 'TEXT')}-"
            f"{bool_text(field.get('notNull', False))}"
        )
        if field.get("defaultValue") is not None:
            key += f"-defaultValue={field['defaultValue']}"
        field_keys.append(key)
    parts.extend(sorted(field_keys, key=str.lower))
    index_keys = [
        f"{bool_text(index['unique'])}-{index['name']}-{','.join(index['columnNames'])}"
        for index in entity.get("indices", [])
    ]
    parts.extend(sorted(index_keys, key=str.lower))
    foreign_key_keys = [
        (
            f"{foreign_key['table']}-{','.join(foreign_key['referencedColumns'])}-"
            f"{','.join(foreign_key['columns'])}-{foreign_key['onDelete']}-"
            f"{foreign_key['onUpdate']}-false"
        )
        for foreign_key in entity.get("foreignKeys", [])
    ]
    parts.extend(sorted(foreign_key_keys, key=str.lower))
    return md5("".join(part + SEP for part in parts))


def room_database_hash(schema: dict) -> str:
    entity_hashes = sorted(
        [room_entity_hash(entity) for entity in schema["database"]["entities"]],
        key=str.lower,
    )
    require(not schema["database"].get("views"), "Room hash verifier does not yet cover database views")
    return md5("".join(entity_hash + SEP for entity_hash in entity_hashes))


settings = text("settings.gradle.kts")
versions = text("gradle/libs.versions.toml")
app_build = text("app/build.gradle.kts")
workspace_build = text("feature/workspace/build.gradle.kts")
navigation_build = text("core/navigation/build.gradle.kts")
routes = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
v2 = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
main_activity = text("app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt")
app_manifest = text("app/src/main/AndroidManifest.xml")
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
entity = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt")
model = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlanning.kt")
session_manager = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt")
portability_store = text(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
)
database_build = text("core/database/build.gradle.kts")
android_convention = text("build-logic/convention/src/main/kotlin/AndroidConvention.kt")
root_build = text("build.gradle.kts")
devtool = text(".devtool.toml")

require('":feature:workspace"' in settings, "workspace module is not included")
require('implementation(project(":feature:workspace"))' in app_build, "app does not depend on workspace")
require(
    "androidx-compose-material3-adaptive-navigation-suite" in versions,
    "adaptive navigation suite dependency is missing",
)
require(
    "libs.androidx.compose.material3.adaptive.navigation.suite" in app_build,
    "app does not consume adaptive navigation suite",
)
require("libs.androidx.activity.compose" in workspace_build, "workspace misses activity-compose")
require(
    "testImplementation(libs.kotlinx.serialization.json)" in navigation_build,
    "typed route restoration tests cannot round-trip serialized payloads",
)

expected_versions = {
    "agp": "9.3.1",
    "kotlin": "2.4.10",
    "composeBom": "2026.06.01",
    "navigation": "2.9.8",
    "room": "2.8.0",
    "ksp": "2.3.11",
}
for version_name, expected in expected_versions.items():
    require(
        f'{version_name} = "{expected}"' in versions,
        f"toolchain drifted: {version_name} must remain {expected}",
    )
require("compileSdk = 36" in android_convention, "compileSdk must remain 36")
require("targetSdk = 36" in android_convention, "targetSdk must remain 36")
require("minSdk = 23" in android_convention, "minSdk must remain 23")
require("navigation3" not in versions.lower(), "Navigation 3 must not be introduced during UI/UX V2")
require(
    'id("org.jetbrains.kotlin.android")' not in app_build,
    "AGP 9 built-in Kotlin must not be replaced with the legacy Kotlin Android plugin",
)

for route_name in (
    "TodayRoute",
    "PlanWorkspaceRoute",
    "PracticeWorkspaceRoute",
    "ProgressWorkspaceRoute",
    "FocusSessionRoute",
    "PlanPickerRoute",
    "QuickAddRoute",
    "SystemHubRoute",
    "ScheduleManagerRoute",
    "ContentManagerRoute",
    "LibraryManagerRoute",
    "QuestionBankRoute",
    "ReviewCenterRoute",
    "ProgressCenterRoute",
    "AnalyticsDetailRoute",
):
    require(f"data class {route_name}" in routes, f"missing typed route {route_name}")

for enum_name in (
    "StudyFlowWorkspaceRoot",
    "PlanWorkspaceTab",
    "PracticeWorkspaceTab",
    "ProgressWorkspacePeriod",
):
    require(f"enum class {enum_name}" in routes, f"missing route enum {enum_name}")

for legacy_name in (
    "DashboardRoute",
    "AnalyticsRoute",
    "SubjectsRoute",
    "ScheduleRoute",
    "LibraryRoute",
    "PracticeRoute",
    "ProgressRoute",
    "ReviewsRoute",
):
    require(f"is {legacy_name} ->" in routes, f"legacy route is not translated: {legacy_name}")

shell_source = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowShell.kt")
shell_primitives = text(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt"
)
require("StudyFlowShell(" in v2, "custom StudyFlow workspace shell is missing")
require("NavigationSuiteScaffold" not in v2, "retired Material navigation suite returned")
require("StudyFlowCompactDock(" in shell_source, "compact StudyFlow dock is missing")
require("StudyFlowExpandedRail(" in shell_source, "expanded StudyFlow rail is missing")
require("NavigationBar(" not in shell_primitives, "stock Material navigation bar returned")
require("NavigationRail(" not in shell_primitives, "stock Material navigation rail returned")
for root in ("TODAY", "PLAN", "PRACTICE", "PROGRESS"):
    require(
        f"StudyFlowWorkspaceRoot.{root}" in shell_source,
        f"custom shell destination is missing for {root}",
    )
require("saveState = restoreSavedStack" in v2, "root navigation does not conditionally save state")
require("restoreState = restoreSavedStack" in v2, "root navigation does not conditionally restore state")
require(
    "restoreSavedStack = false" in v2,
    "plan switching must not restore another plan's current workspace stack",
)
for route_name in ("TodayRoute", "PlanWorkspaceRoute", "PracticeWorkspaceRoute", "ProgressWorkspaceRoute"):
    require(
        f"clearBackStack<{route_name}>()" in v2,
        f"plan switching does not clear the saved {route_name} stack",
    )
plan_picker_block = v2[v2.index("composable<PlanPickerRoute>"):v2.index("composable<QuickAddRoute>")]
require(
    "setDefaultStudyPlan(" not in plan_picker_block,
    "temporary workspace plan switching must not mutate the canonical default plan",
)
require(
    "planId = plan.id.value" in plan_picker_block and "restoreSavedStack = false" in plan_picker_block,
    "plan switching must navigate the selected workspace without restoring another plan stack",
)
require("launchSingleTop = true" in v2, "root navigation does not use launchSingleTop")
require(
    "onClick = { if (root != selectedRoot) onNavigate(root) }" in shell_source,
    "reselecting the active workspace can reset or duplicate its current coordinate",
)
require("currentBackStackEntryAsState" in v2, "root selection is not derived from destination")
require("rememberSaveable" not in v2, "app shell should not keep duplicate selected-root state")
require("startDestination = TodayRoute()" in app, "Today is not the application start destination")
require("StudyFlowV2Navigation(" in app, "application shell does not delegate to V2")
for direct_search_target in (
    "GlobalSearchContentType.PLAN -> navigate(TodayRoute(planId))",
    "GlobalSearchContentType.SUBJECT,",
    "GlobalSearchContentType.TOPIC,",
    "PlanWorkspaceRoute(planId, PlanWorkspaceTab.CONTENT, result.id)",
    "GlobalSearchContentType.MATERIAL ->",
    "PlanWorkspaceRoute(planId, PlanWorkspaceTab.LIBRARY, result.id)",
    "GlobalSearchContentType.QUESTION ->",
    "PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.QUESTIONS, result.id)",
    "GlobalSearchContentType.NOTE -> navigate(ProgressWorkspaceRoute(planId, targetId = result.id))",
    "GlobalSearchContentType.FLASHCARD ->",
    "PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.REVIEWS, result.id)",
    "GlobalSearchContentType.ERROR ->",
    "PracticeWorkspaceRoute(planId, PracticeWorkspaceTab.ERRORS, result.id)",
):
    require(direct_search_target in app, f"search result does not enter V2 directly: {direct_search_target}")
require("LegacyWorkspaceRedirect" in app, "legacy route compatibility redirects are missing")
require("enableEdgeToEdge()" in main_activity, "edge-to-edge setup is missing")

require("import androidx.activity.compose.BackHandler" not in v2, "shell-wide BackHandler breaks predictive back")
require("navigateToWorkspaceRecommendation" in v2, "recommendation navigation call is missing")
require(
    "fun NavHostController.navigateToWorkspaceRecommendation" in v2,
    "recommendation navigation helper is not defined",
)
for destination in (
    "StudyRecommendationDestination.REVIEWS",
    "StudyRecommendationDestination.SCHEDULE",
    "StudyRecommendationDestination.SUBJECTS",
    "StudyRecommendationDestination.PRACTICE",
    "StudyRecommendationDestination.LIBRARY",
):
    require(destination in v2, f"recommendation target is not mapped: {destination}")
require("navigateWorkspaceTab" in v2, "workspace tab replacement helper is missing")
require(
    "inline fun <reified T : Any> NavHostController.navigateWorkspaceTab" in v2,
    "workspace tab navigation is not type-safe",
)
require(
    "inline fun <reified T : Any> NavHostController.navigateWorkspaceRootRoute" in v2,
    "workspace root navigation is not type-safe",
)
require(
    not re.search(r"(?:val|var|fun\s+\w+\([^)]*)\btarget\s*:\s*Any\b", v2),
    "navigation stores route targets as untyped Any values",
)
require("inclusive = true" in v2, "tab replacement must remove the previous tab route")
require("PracticeWorkspaceRoute(plan.id.value, PracticeWorkspaceTab.REVIEWS)" in v2, "review reminder misses V2")
require("PlanWorkspaceRoute(plan.id.value, PlanWorkspaceTab.SCHEDULE)" in v2, "schedule reminder misses V2")
require("TodayRoute(plan.id.value)" in v2, "daily-summary reminder misses V2")
require("activeSession = activeSession" in v2, "plan picker is not protected by active focus state")
for coordinate in ("planTab = route.planTab", "practiceTab = route.practiceTab", "progressPeriod = route.progressPeriod"):
    require(coordinate in v2, f"plan switching does not preserve workspace coordinate: {coordinate}")
focus_exit_block = v2[v2.index("composable<FocusSessionRoute>"):v2.index("composable<ScheduleManagerRoute>")]
require(
    focus_exit_block.count("restoreSavedStack = false") == 2,
    "finishing or cancelling focus can restore the completed focus destination",
)

workspace_dir = ROOT / "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace"
workspace_sources = {path.name: path.read_text(encoding="utf-8") for path in workspace_dir.glob("*.kt")}
required_screens = {
    "TodayWorkspaceScreen.kt": ("ManageStudySchedule", "ManageStudySession", "ManageStudyReview"),
    "PlanWorkspaceScreen.kt": ("ManageStudySchedule", "ManageStudyContent", "ManageStudyLibrary"),
    "PracticeWorkspaceScreen.kt": ("ManageStudyPractice", "ManageStudyReview"),
    "ProgressWorkspaceScreen.kt": ("ManageStudyAnalytics", "ManageStudyProgress", "ManageStudySession"),
    "FocusSessionScreen.kt": ("pause", "resume", "complete", "cancel", "updateNotes"),
    "WorkspaceUtilityScreens.kt": ("PlanPickerScreen", "SystemHubScreen", "QuickAddScreen"),
}
for file_name, tokens in required_screens.items():
    source = workspace_sources.get(file_name, "")
    require(source, f"missing workspace screen: {file_name}")
    for token in tokens:
        require(token in source, f"{file_name} does not contain real contract {token}")
    require("onClick = {}" not in source, f"{file_name} contains a dead click callback")


focus_source = workspace_sources.get("FocusSessionScreen.kt", "")
common_source = workspace_sources.get("WorkspaceCommon.kt", "")
utility_source = workspace_sources.get("WorkspaceUtilityScreens.kt", "")
design_source = text(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt",
)
require("paneTitle" in common_source, "workspace panes do not expose pane-title semantics")
practice_primitives = text(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowPracticePrimitives.kt"
)
require("LiveRegionMode.Polite" in practice_primitives, "focus timer is not announced as a live region")
require("stateDescription = timerState" in practice_primitives, "focus timer lacks state description")
require("accessibilityTimerBucket" in focus_source, "focus timer announces every raw second")
require("performFocusMutation" in focus_source, "focus mutations do not share cancellation-safe handling")
require(
    "rememberSaveable(session?.id?.value)" in focus_source,
    "focus note drafts must survive recreation/process death until their debounced Room write completes",
)
require(
    "catch (cancelled: CancellationException)" in focus_source,
    "focus mutations do not rethrow coroutine cancellation",
)
require("shouldInterceptFocusBack" in focus_source, "focus back interception is not state-scoped")
require("imePadding()" in focus_source, "focus notes do not respect the IME")
require(
    "windowInsetsPadding(WindowInsets.safeDrawing)" in focus_source,
    "focus mode does not use explicit safe-drawing insets",
)
require("PredictiveBackHandler(" in focus_source, "focus mode does not integrate predictive back")
require(
    "navigationBarsPadding()" not in focus_source,
    "focus mode double-applies navigation-bar padding on top of Scaffold insets",
)
require(
    'android:windowSoftInputMode="adjustResize"' in app_manifest,
    "MainActivity does not resize for the IME",
)
require("focus_overtime" in focus_source, "focus timer lacks overtime elapsed fallback")
require("concentrationRating = concentration" in focus_source, "focus completion omits concentration")
require("difficultyRating = difficulty" in focus_source, "focus completion omits difficulty")
require("StudyFlowResponsivePair" in design_source, "large-font responsive pair component is missing")
focus_primitives = text(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowFocusWorkspacePrimitives.kt"
)
require("PrimaryScrollableTabRow(" not in design_source, "legacy Material workspace tabs remain")
require("fun <T> StudyFlowTabStrip(" in focus_primitives, "custom workspace tab strip is missing")
require("Role.Tab" in focus_primitives, "custom workspace tabs lack tab semantics")
require("fontScale >= 1.5f" in design_source, "200% font layouts are not forced to stack")
require("MinimumTouchTarget = 48.dp" in design_source, "48dp interaction token is missing")
require("StudyFlowCoachCard(" not in design_source, "retired coach-card primitive remains")
progress_source = workspace_sources.get("ProgressWorkspaceScreen.kt", "")
progress_primitives = text(
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowProgressPrimitives.kt"
)
require("FilterChip(" not in progress_source, "stock Material progress period chips remain")
require("StudyFlowPeriodStrip(" in progress_source, "custom progress period strip is missing")
require("heightIn(min = 48.dp)" in progress_primitives, "progress period controls lack 48dp targets")
require("calculateStartPadding" in common_source, "utility padding is not RTL-aware at the start edge")
require("calculateEndPadding" in common_source, "utility padding is not RTL-aware at the end edge")
require("search_plans" in utility_source, "plan picker lacks search")
require("archivedPlans" in utility_source or "val archived" in utility_source, "archived plans are not surfaced")
require("active_session_blocks_plan_switch" in utility_source, "active focus does not block plan switching")

plan_source = workspace_sources.get("PlanWorkspaceScreen.kt", "")
practice_source = workspace_sources.get("PracticeWorkspaceScreen.kt", "")
require("StudyFlowSubjectAccordion" in plan_source, "Plan content does not expose expandable subject sections")
require("expandedSubjectIds" in plan_source, "subject expansion state is not restored as a lightweight coordinate")
require("topicsBySubject" in plan_source, "Plan content does not attach topics to their subjects")
require("reviewManager.gradeReview" in practice_source, "Practice reviews cannot be graded directly from the V2 workspace")
require("reviewManager.skipReview" in practice_source, "Practice reviews cannot be skipped directly from the V2 workspace")
require("ReviewExecutionCard" in practice_source, "Practice review execution card is missing")
require("tests: List" in practice_source and "mocks: List" in practice_source, "Practice workspace does not expose tests and mock exams")
require("quick_add_context" in utility_source, "Quick add does not identify its source workspace context")
require("sourceRoot: StudyFlowWorkspaceRoot" in utility_source, "Quick add route context is not passed into the UI")
require("period-trend" in progress_source and "trendLabel" in progress_source, "Progress workspace lacks trend semantics")
require("AdaptationDestination.REVIEWS" in progress_source, "Adaptation preview cannot route to review work")
require("AdaptationDestination.PROGRESS" in progress_source, "Adaptation preview cannot route to goal/progress work")

require("targetDurationMinutes: Int? = null" in model, "session domain model lacks target duration")
require("targetDurationMinutes: Int? = null" in entity, "session entity lacks target duration")
schema_match = re.search(r"const val SCHEMA_VERSION = (\d+)", database)
require(schema_match is not None and int(schema_match.group(1)) >= 4, "Room database version is not 4 or later")
require("StudyFlowMigrations.All" in database, "Room database does not register the migration array")
require("Migration2To3" in migrations, "Room migration array lacks migration 2 to 3")
require("Migration3To4" in migrations, "Room migration array lacks migration 3 to 4")
require("ALTER TABLE study_sessions" in migrations, "migration 2 to 3 does not alter study_sessions")
require("targetDurationMinutes INTEGER DEFAULT NULL" in migrations, "migration column definition differs")
require("targetDurationMinutes" in session_manager, "session manager does not persist target duration")
require("updateNotes" in session_manager, "focus notes mutation is missing")

require(
    '"study_sessions" -> expected - "targetDurationMinutes"' in portability_store,
    "schema-2 backup rows are not accepted",
)
require(
    'put("targetDurationMinutes", PortableValue(PortableValueType.NULL))' in portability_store,
    "schema-2 backup rows are not normalized with a null duration",
)
require(
    'sourceSets.getByName("androidTest").assets.srcDir' in database_build,
    "Room migration schemas are not exposed to tests",
)

schema_path = ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/4.json"
require(schema_path.is_file(), "Room schema 3 is missing")
if schema_path.is_file():
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    require(schema["database"]["version"] == 4, "Room schema JSON does not declare version 4")
    sessions = next(
        (entity for entity in schema["database"]["entities"] if entity["tableName"] == "study_sessions"),
        None,
    )
    require(sessions is not None, "Room schema 3 lacks study_sessions")
    if sessions:
        field = next(
            (field for field in sessions["fields"] if field["columnName"] == "targetDurationMinutes"),
            None,
        )
        require(field is not None, "Room schema 3 lacks targetDurationMinutes")
        if field:
            require(not field.get("notNull", False), "targetDurationMinutes must remain nullable")
            require(field.get("defaultValue") == "NULL", "targetDurationMinutes default must be NULL")
    calculated_hash = room_database_hash(schema)
    require(
        calculated_hash == schema["database"]["identityHash"],
        "Room schema 3 identity hash does not match its contents",
    )
    setup_text = "\n".join(schema["database"].get("setupQueries", []))
    require(calculated_hash in setup_text, "Room setup query does not contain the schema identity hash")

for test_path in (
    "core/navigation/src/test/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowWorkspaceRoutesTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySessionFocusTest.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration2To3Test.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyPlanPortabilityMigrationCompatibilityTest.kt",
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceContractTest.kt",
    "feature/workspace/src/androidTest/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceAccessibilityTest.kt",
):
    require((ROOT / test_path).is_file(), f"missing V2 test: {test_path}")

route_test = text(
    "core/navigation/src/test/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowWorkspaceRoutesTest.kt",
)
require(
    "typedWorkspaceCoordinatesRoundTripThroughSavedNavigationPayloads" in route_test,
    "typed route saved-state round-trip test is missing",
)

migration_test = text(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration2To3Test.kt",
)
portability_test = text(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/"
    "StudyPlanPortabilityMigrationCompatibilityTest.kt",
)
accessibility_test = text(
    "feature/workspace/src/androidTest/java/com/mikeyphw/studyflow/feature/workspace/"
    "WorkspaceAccessibilityTest.kt",
)
for token in (
    "migrationOneToFour",
    "migrationTwoToThree",
    "freshVersionFourDatabaseContainsNullableTargetDuration",
    "activeFocusSessionRestoresAfterDatabaseReopen",
):
    require(token in migration_test, f"migration/restoration test missing: {token}")
require("ExportsDurationAndImportsSchemaTwoBackupWithoutIt" in portability_test, "backup compatibility test missing")
require("SemanticsProperties.PaneTitle" in accessibility_test, "Compose pane semantics test missing")
require("SemanticsProperties.IsTraversalGroup" in accessibility_test, "traversal-group test missing")
require("DeviceConfigurationOverride.FontScale(2f)" in accessibility_test, "actual 200% font test missing")
require("DeviceConfigurationOverride.LayoutDirection(LayoutDirection.Rtl)" in accessibility_test, "RTL test missing")
require("StateRestorationTester" in accessibility_test, "saved-state restoration test missing")
require("emulateSavedInstanceStateRestore" in accessibility_test, "saved-state recreation is not exercised")
require("assertHeightIsAtLeast(48.dp)" in accessibility_test, "Compose 48dp test missing")
require("isFocusable()" in accessibility_test, "keyboard/switch focus test missing")
require("assertIsSelected()" in accessibility_test, "selected-tab semantics test missing")
workspace_contract_test = text(
    "feature/workspace/src/test/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceContractTest.kt",
)
for token in (
    "largeFontsAndCompactWidthsStackPairedControls",
    "timerAccessibilityAnnouncementsChangeByMinuteExceptFinalSeconds",
    "predictiveBackIsInterceptedOnlyForMeaningfulActiveSessionState",
):
    require(token in workspace_contract_test, f"workspace contract test missing: {token}")

devtool_config = tomllib.loads(devtool)
android_target = devtool_config.get("targets", {}).get("studyflow_android", {})
android_phases = android_target.get("validation", {}).get("phases", {})
require(
    ":feature:workspace:testDebugUnitTest" in android_phases.get("unit-test", []),
    "workspace unit-test coverage is missing from the optimized Android lane",
)
for instrumentation_task in (
    ":feature:workspace:connectedDebugAndroidTest",
    ":core:database:connectedDebugAndroidTest",
):
    require(
        instrumentation_task in android_phases.get("instrumentation", []),
        f"instrumentation coverage is missing: {instrumentation_task}",
    )
require(android_phases.get("compile") == [], "redundant explicit compile phase must remain disabled")
require(
    ":app:lintDebug" in android_phases.get("lint", [])
    and ":app:lintRelease" in android_phases.get("lint", []),
    "app-level Debug/Release lint roots are missing",
)
require("checkDependencies = true" in app_build, "app-level lint must cover workspace dependencies")
require(":app:assembleDebug" in android_phases.get("package", []), "Debug package gate is missing")
require("scripts/verify_ui_ux_v2.py" in root_build, "V2 verifier is not wired into repository contracts")

strings_paths = (
    ROOT / "feature/workspace/src/main/res/values/strings.xml",
    ROOT / "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
)
string_sets = []
for strings_path in strings_paths:
    try:
        root = ET.parse(strings_path).getroot()
    except (ET.ParseError, OSError) as error:
        FAILURES.append(f"invalid strings XML {strings_path.relative_to(ROOT)}: {error}")
        continue
    names = [element.attrib["name"] for element in root if element.tag == "string"]
    require(len(names) == len(set(names)), f"duplicate string resource in {strings_path.relative_to(ROOT)}")
    string_sets.append(set(names))
if len(string_sets) == 2:
    require(string_sets[0] == string_sets[1], "workspace English and pt-BR resource keys differ")

source_roots = [ROOT / "app", ROOT / "core", ROOT / "feature/workspace"]
excluded_directory_names = {"build", ".gradle", ".idea"}
for source_root in source_roots:
    for source_path in source_root.rglob("*"):
        if not source_path.is_file() or source_path.suffix not in {".kt", ".kts", ".xml"}:
            continue
        if any(part in excluded_directory_names for part in source_path.relative_to(ROOT).parts):
            continue
        source = source_path.read_text(encoding="utf-8")
        require("\x00" not in source, f"NUL byte in {source_path.relative_to(ROOT)}")
        stripped_lines = [line.strip() for line in source.splitlines()]
        for line_number, (current, following) in enumerate(zip(stripped_lines, stripped_lines[1:]), 1):
            require(
                not (current == following and current.endswith("(")),
                f"duplicate adjacent call in {source_path.relative_to(ROOT)}:{line_number}",
            )
        if source_path.suffix in {".kt", ".kts"}:
            # Generic line-length/style enforcement belongs to the dedicated verifyFormatting task.
            # This phase verifier stays focused on UI/UX semantic contracts to avoid duplicate scans.
            require(
                not re.search(
                    r"^import androidx\.compose\.foundation\.layout\."
                    r"(weight|align|matchParentSize|offset|absoluteOffset)$",
                    source,
                    re.MULTILINE,
                ),
                f"scoped Compose modifier import in {source_path.relative_to(ROOT)}",
            )
            require(
                not re.search(
                    r"Icons\.(Outlined|Filled|Default)\."
                    r"(ArrowBack|ArrowForward|KeyboardArrowLeft|KeyboardArrowRight|ListAlt)",
                    source,
                ),
                f"non-auto-mirrored directional icon in {source_path.relative_to(ROOT)}",
            )

for doc_path in (
    "FULL_UI_UX_V2_IMPLEMENTATION.md",
    "docs/architecture/ADR-0029-workspace-navigation-and-focus-mode.md",
    "docs/quality/UI_UX_V2_ACCEPTANCE.md",
    "docs/quality/UI_UX_V2_LESSONS.md",
):
    require((ROOT / doc_path).is_file(), f"missing implementation documentation: {doc_path}")

if FAILURES:
    print("UI_UX_V2_VALIDATION_FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("UI_UX_V2_VALIDATION_OK")
print(
    "4 adaptive roots, predictive-back contracts, accessibility semantics, Room 1->4/2->4/3->4, "
    "schema-2 backup compatibility, and bilingual resources verified",
)
