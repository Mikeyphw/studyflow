#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


def resource_names(path: str) -> dict[str, str]:
    source = text(path)
    root = ET.fromstring(source)
    names: dict[str, str] = {}
    duplicates: list[str] = []
    for child in root:
        if child.tag not in {"string", "plurals"} or "name" not in child.attrib:
            continue
        name = child.attrib["name"]
        if name in names:
            duplicates.append(name)
        names[name] = child.tag
    require(not duplicates, f"duplicate string/plurals resource names in {path}: {sorted(set(duplicates))}")
    return names


def section_after(source: str, marker: str, until: str | None = None) -> str:
    if marker not in source:
        return ""
    body = source.split(marker, 1)[1]
    if until is not None and until in body:
        body = body.split(until, 1)[0]
    return body


build = text("build.gradle.kts")
devtool = text(".devtool.toml")
versions = text("gradle/libs.versions.toml")
manifest = text("app/src/main/AndroidManifest.xml")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
v2_nav = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
routes = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
widget = text("app/src/main/res/layout/widget_today.xml")
backup_rules = text("app/src/main/res/xml/backup_rules.xml")
data_extraction = text("app/src/main/res/xml/data_extraction_rules.xml")
provider_paths = text("app/src/main/res/xml/file_provider_paths.xml")
privacy = text("feature/privacy/src/main/java/com/mikeyphw/studyflow/feature/privacy/PrivacyCenterScreen.kt")
plan_portability = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt")
progress_screen = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
plan_screen = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
practice_screen = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
today_screen = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
onboarding_screen = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt")
report_exporter = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowExamReportExporter.kt")
cleanup_domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentCleanup.kt")
report_domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamReport.kt")
onboarding_domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt")
weekly_coach = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCoachWeeklyReview.kt")
readiness = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngine.kt")
adaptive_reschedule = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AdaptiveReschedulePlanner.kt")
reminder_reliability = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReminderReliabilityCenter.kt")
sync_domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt") + "\n" + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/CloudlessBackupQueue.kt")
portability = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability").glob("*.kt")) + "\n" + text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt") + "\n" + text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt")
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")

# Stack invariants: this seal is an acceptance gate, not a migration gate.
for token in (
    'agp = "9.3.0"',
    'kotlin = "2.4.10"',
    'composeBom = "2026.06.00"',
    'navigation = "2.9.8"',
    'room = "2.8.0"',
    'ksp = "2.3.10"',
):
    require(token in versions, f"stack version changed or missing: {token}")
require("androidx-navigation-compose" in versions, "Navigation Compose dependency alias is missing")
require("Navigation 3" not in versions + build, "Navigation 3 migration is forbidden for this seal")

# Release identity and startup shell.
for token in (
    "applicationId = \"com.mikeyphw.studyflow\"",
    "versionCode = 37",
    "versionName = \"1.0.0-rc1\"",
):
    require(token in text("app/build.gradle.kts"), f"release identity missing: {token}")
require(":app:assembleRelease" in build + devtool, "Release APK assembly gate is missing")
require(":app:bundleRelease" in build + devtool, "Release AAB bundle gate is missing")
require("android.intent.action.MAIN" in manifest, "launcher MAIN intent is missing")
require("android.intent.category.LAUNCHER" in manifest, "launcher category is missing")
require("startDestination = TodayRoute()" in app, "V2 Today route must remain the release startup destination")
require("StudyFlowV2Navigation(" in app, "app shell must route through V2 navigation")

# Verify the entire known contract chain is wired into Gradle and Devtool defaults.
required_scripts = [
    *(f"scripts/verify_phase_{phase}.py" for phase in range(0, 38)),
    "scripts/verify_debug_workbench_d1_r2.py",
    "scripts/verify_debug_workbench_d2.py",
    "scripts/verify_debug_workbench_d3.py",
    "scripts/verify_debug_workbench_d2_d3_corrective_seal.py",
    "scripts/verify_debug_workbench_d4.py",
    "scripts/verify_debug_workbench_d5.py",
    "scripts/verify_debug_workbench_d6.py",
    "scripts/verify_debug_workbench_d7.py",
    "scripts/verify_debug_workbench_d8.py",
    "scripts/verify_ui_ux_v2.py",
    "scripts/verify_phase_48_49.py",
    "scripts/verify_phase_50_51.py",
    "scripts/verify_phase_52_53.py",
    "scripts/verify_phase_54_55.py",
    "scripts/verify_phase_56_57.py",
    "scripts/verify_phase_58_59.py",
    "scripts/verify_phase_60_61.py",
    "scripts/verify_phase_62.py",
    "scripts/verify_full_app_final_user_readiness.py",
    "scripts/verify_final_release_verdict_contract.py",
]
for path in required_scripts:
    require((ROOT / path).is_file(), f"missing validator: {path}")
    script_name = Path(path).name
    require(script_name in build or "for phase in {0..37}" in build, f"repository contract task does not run {script_name}")
for task in (
    "verifyPhase56_57ReleaseReadiness",
    "verifyPhase58_59WarningHygiene",
    "verifyPhase60_61ContentReportSeal",
    "verifyPhase62OnboardingSeal",
):
    require(task in build, f"Gradle acceptance task missing: {task}")
require("verifyFinalSourceAcceptance" in build, "truthful final source acceptance task is missing")
require("verifyFinalSourceAcceptance" in devtool, "Devtool test defaults must include truthful source acceptance")
require("verifyFinalAppAcceptanceSeal" in build, "legacy final-app acceptance alias must remain available")

# Typed route graph, legacy redirect and no-loop route hygiene.
for enum_name in (
    "StudyFlowWorkspaceRoot",
    "PlanWorkspaceTab",
    "PracticeWorkspaceTab",
    "ProgressWorkspacePeriod",
):
    require(f"@Keep\n@Serializable\nenum class {enum_name}" in routes, f"{enum_name} must stay Keep+Serializable")
for route_name in (
    "TodayRoute",
    "PlanWorkspaceRoute",
    "PracticeWorkspaceRoute",
    "ProgressWorkspaceRoute",
    "FocusSessionRoute",
    "PlanPickerRoute",
    "QuickAddRoute",
    "SystemHubRoute",
    "OnboardingRoute",
):
    require(route_name in routes, f"typed route missing: {route_name}")
    require(f"composable<{route_name}>" in app + v2_nav, f"route is not composed: {route_name}")
for legacy in (
    "DashboardRoute(route.planId)",
    "AnalyticsRoute(route.planId)",
    "ScheduleRoute(route.planId)",
    "LibraryRoute(route.planId)",
    "PracticeRoute(route.planId)",
    "ProgressRoute(route.planId)",
    "ReviewsRoute(route.planId)",
    "SubjectsRoute(route.planId)",
):
    legacy_name = legacy.split("(", 1)[0]
    require(f"composable<{legacy_name}>" in app, f"legacy route not registered: {legacy_name}")
require(app.count("LegacyWorkspaceRedirect(") >= 7, "legacy routes must redirect into V2 workspaces")
require("popUpTo(destinationId) { inclusive = true }" in v2_nav, "legacy redirect must remove the compatibility destination")

# V2 workspace roots have real actions and destination-specific handlers.
for root, screen, token in (
    ("TODAY", today_screen, "onOpenFocus"),
    ("PLAN", plan_screen, "onOpenSchedule"),
    ("PRACTICE", practice_screen, "onOpenReviews"),
    ("PROGRESS", progress_screen, "onOpenAnalytics"),
):
    require(f"StudyFlowWorkspaceRoot.{root}" in routes + v2_nav, f"workspace root missing: {root}")
    require(token in screen + v2_nav, f"{root} workspace action missing: {token}")
    require("onSearch" in screen + v2_nav, f"{root} workspace search entry missing")
    require("onSystem" in screen + v2_nav, f"{root} workspace system entry missing")
for forbidden in ("onClick = {}", "onClick = { }", "Button(onClick = {})", "AssistChip(onClick = {})"):
    for source_path in ROOT.rglob("*.kt"):
        if "/src/test/" in str(source_path) or "/src/androidTest/" in str(source_path):
            continue
        require(forbidden not in source_path.read_text(encoding="utf-8"), f"decorative empty action remains: {source_path}")

# Search and recommendations reach the V2 surfaces.
for token in (
    "onOpenResult = { result ->",
    "navController.navigateToResult(result)",
    "onOpenRecommendation = { recommendation ->",
    "navController.navigateToRecommendation(recommendation)",
    "StudyRecommendationDestination.REVIEWS",
    "StudyRecommendationDestination.SCHEDULE",
    "StudyRecommendationDestination.SUBJECTS",
    "StudyRecommendationDestination.PRACTICE",
    "StudyRecommendationDestination.LIBRARY",
):
    require(token in app + v2_nav + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyDiscovery.kt"),
            f"search/recommendation integration missing: {token}")

# Phase 48-55 evidence: readiness, reschedule, drills/imports, mocks, widget, reminders and timeline.
for token in (
    "ReadinessSignalKind.COVERAGE",
    "ReadinessSignalKind.RETENTION",
    "ReadinessSignalKind.PRACTICE",
    "ReadinessSignalKind.MOCK_EXAM",
    "ReadinessSignalKind.SCHEDULE",
    "ReadinessSignalKind.TIME_LEFT",
):
    require(token in readiness, f"readiness signal missing: {token}")
for token in (
    "class ManageAdaptiveReschedule",
    "fun observePreview",
    "suspend fun applySafePreview",
    "AdaptiveReschedulePlanner.preview",
    "scheduleRepository.applyAdaptivePreview(plan.id, approvedPreview)",
    "AdaptiveRescheduleChangeType.REDISTRIBUTE_MISSED",
    "AdaptiveRescheduleChangeType.PROTECT_REVIEWS",
    "AdaptiveRescheduleChangeType.REDUCE_OVERLOAD",
    "AdaptiveRescheduleChangeType.ADD_THEORY_BEFORE_QUESTIONS",
    "AdaptiveRescheduleChangeType.KEEP_STEADY",
    "reschedulePreview = reschedulePreview",
    "apply_safe_reschedule",
):
    require(token in adaptive_reschedule + progress_screen + weekly_coach + report_domain,
            f"adaptive reschedule acceptance coverage missing: {token}")
for token in (
    "StudyEvidenceTimelineBuilder.build",
    "StudyEvidenceTimelineInput(",
    "tests = tests",
    "mockExams = mockExams",
    "goals = progress?.let { snapshot -> snapshot.activeGoals + snapshot.completedGoals }.orEmpty()",
    "notes = progress?.notes.orEmpty()",
):
    require(token in progress_screen, f"evidence timeline coverage missing: {token}")
for token in (
    "WeaknessDrillGenerator",
    "QuestionImportPipeline",
    "onCreateWeaknessDrill",
    "onImportQuestions",
):
    require(token in practice_screen + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WeaknessDrillGenerator.kt") + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/QuestionImportPipeline.kt"),
            f"drill/import flow missing: {token}")
for token in (
    "MockExamStudioPlanner",
    "onCreateMockExam",
    "observeMockExams",
    "startMockExam",
    "finishMockExam",
):
    require(token in practice_screen + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/MockExamStudioPlanner.kt") + text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt"),
            f"mock exam flow missing: {token}")
for token in (
    "StudyFlowTodayWidgetProvider",
    "StudyFlowWidgetUpdateCoordinator",
    "PracticeWorkspaceRoute",
    "PlanWorkspaceRoute",
):
    require(token in app + v2_nav + "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "app/src/main/java/com/mikeyphw/studyflow").rglob("*Widget*.kt")),
            f"widget route/action coverage missing: {token}")
for token in (
    "ReminderNotificationLabReport",
    "onInspectReminders",
    "onReconcileReminders",
    "ReminderNotificationType.DUE_REVIEWS",
    "ReminderNotificationType.MISSED_ACTIVITY",
):
    require(token in app + v2_nav, f"reminder diagnostics/tap path missing: {token}")
for token in (
    "ReminderReliabilityCenter",
    "ReminderReliabilityStatus.BLOCKED",
    "ReminderReliabilityStatus.DEGRADED",
    "ReminderReliabilityStatus.ACTION_NEEDED",
    "ReminderReliabilityStatus.READY",
    "quietHoursActive",
    "ReminderReliabilityFallback.QUIET_HOURS_DELAY",
    "batteryOptimizationIgnored",
    "notificationPermissionGranted",
):
    require(token in reminder_reliability + app + v2_nav,
            f"reminder degraded-state acceptance coverage missing: {token}")

# Phase 58-62 offline arc: cloudless queue, weekly coach, cleanup, report export and onboarding.
for token in (
    "StudySyncChangeSummary",
    "CloudlessBackupQueuePlan",
    "prepareLocalFoundation",
    "inspectLocalChanges",
    "clearLocalFoundation",
):
    require(token in sync_domain + app, f"cloudless sync/local checkpoint surface missing: {token}")
for token in (
    "StudyCoachWeeklyReviewItem",
    "StudyCoachWeeklyReviewBuilder",
    "generatedAtEpochMillis",
):
    require(token in weekly_coach + progress_screen, f"weekly coach integration missing: {token}")
for token in (
    "StudyContentCleanupIssueKind",
    "DUPLICATE_QUESTION",
    "ORPHAN_TOPIC",
    "STALE_RESOURCE",
    "STALE_PLAN",
    "INCONSISTENT_TAG",
    "STALE_REVIEW",
    "BROKEN_SCHEDULE_ALIGNMENT",
    "StudyContentCleanupEngine.inspect",
):
    require(token in cleanup_domain + progress_screen + plan_screen, f"cleanup contract missing: {token}")
for forbidden in ("delete", "remove", "purge", "upsert", "update", "archive"):
    cleanup_lower = cleanup_domain.lower()
    require(f"fun {forbidden}" not in cleanup_lower, f"cleanup domain must not expose mutating {forbidden} API")
for token in (
    "StudyExamReportSnapshot",
    "StudyExamReportBuilder.build",
    "freeformNotesIncluded: Boolean = false",
    "StudyFlowExamReportExporter",
    "REPORT_DIRECTORY = \"exam-reports\"",
    "context.cacheDir",
    "FileProvider.getUriForFile",
    "Intent.ACTION_SEND",
    "StudyExamReportFormat.HTML",
    "StudyExamReportFormat.PDF",
):
    require(token in report_domain + report_exporter + progress_screen + v2_nav, f"local report export contract missing: {token}")
for forbidden in ("Retrofit", "OkHttp", "Firebase", "http://", "https://", "openConnection", "URLConnection"):
    require(forbidden not in report_domain + report_exporter + cleanup_domain + onboarding_domain,
            f"offline-only domain/export boundary violated by {forbidden}")
for token in (
    "OnboardingSamplePlanTemplate",
    "PUBLIC_EXAM_12_WEEK",
    "UNIVERSITY_SEMESTER",
    "CERTIFICATION_BOOTCAMP",
    "FREE_STUDY_HABIT",
    "previewFirst: Boolean",
    "userInitiatedOnly: Boolean",
    "OnboardingSamplePlanWizardScreen",
    "onCreateSample",
    "container.samplePlanProvisioner.startProvisioning(",
    "SamplePlanProvisioningState.Completed",
):
    require(token in onboarding_domain + onboarding_screen + app, f"onboarding contract missing: {token}")
provisioner = text("app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioner.kt")
for token in (
    "StudyOnboardingSamplePlanWizard.buildScheduleRules(",
    "repository.provision(request)",
    "journal.begin(",
    "journal.updateCommittedPlan(",
    "preferencesRepository.setDefaultStudyPlanIfAbsent(",
    "suspend fun recoverPendingCompletion()",
):
    require(token in provisioner, f"journaled sample-plan provisioner contract missing: {token}")
onboarding_route_body = section_after(app, "composable<OnboardingRoute>", "composable<EditPlanRoute>")
require("LaunchedEffect(Unit)" not in onboarding_route_body, "onboarding route must not auto-create sample plans")

# Backup/export/import coverage and zero-write dry-run semantics.
for token in (
    "StudyPlanExportOptions",
    "StudyPlanExportMode.FULL",
    "inspectBackup",
    "dryRunImport",
    "StudyPlanImportStrategy",
    "redactHistory",
    "metadataPrivacyMode",
):
    require(token in portability + app + plan_portability, f"backup/export/import coverage missing: {token}")
for token in (
    "subjects",
    "topics",
    "study_materials",
    "video_resources",
    "annexes",
    "planned_activities",
    "review_items",
    "flashcards",
    "questions",
    "study_tests",
    "mock_exams",
    "study_sessions",
):
    require(token in portability, f"portability payload must include {token}")
portability_lab = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDatabasePortabilityLab.kt")
for token in (
    "runProductionPortabilityDrills(",
    "container.manageStudyPlanPortability.exportPlan",
    "container.manageStudyPlanPortability.dryRunImport(",
    "StudyPlanImportStrategy.CREATE_AS_NEW",
    "dryRun.databaseRowsChanged == 0",
    "PortabilityDrillKind.RESTORE_DRY_RUN",
    "container.manageStudyPlan.deletePermanently(disposableId)",
    "observePlan(disposableId).first() == null",
):
    require(token in portability_lab, f"production portability drill evidence missing: {token}")

# Database, upgrade, backup rules and local file-sharing boundaries.
require("SCHEMA_VERSION = 4" in database, "Room schema version must include the remediation integrity migration")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
for migration in ("Migration1To2", "Migration2To3", "Migration3To4"):
    require(migration in migrations, f"database upgrade migration missing: {migration}")
require("All = arrayOf(Migration1To2, Migration2To3, Migration3To4)" in migrations, "database migration chain is incomplete")
require("@xml/backup_rules" in manifest, "manifest backup rules reference missing")
require("@xml/data_extraction_rules" in manifest, "manifest data extraction rules reference missing")
require('android:allowBackup="false"' in manifest, "platform backup must remain disabled")
for token in (
    '<exclude domain="root" path="." />',
    '<exclude domain="file" path="." />',
    '<exclude domain="database" path="." />',
    '<exclude domain="sharedpref" path="." />',
    '<exclude domain="external" path="." />',
):
    require(token in backup_rules and token in data_extraction, f"backup/data-extraction exclusion missing: {token}")
require("cache-path" in provider_paths and "exam-reports" in provider_paths, "FileProvider must expose cached exam reports only")
require("android:tint" not in widget, "widget must not use android:tint")
require("android:text" in widget and "48dp" in widget, "widget must expose tappable text actions")

# Accessibility/layout/resource warning sentries.
for source_path in (
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/WorkspaceCommon.kt",
    "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt",
):
    source = text(source_path)
    require("semantics" in source or "contentDescription" in source,
            f"accessibility semantics/content descriptions missing from {source_path}")
require("keyboardOptions" in onboarding_screen + text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanSettingsScreen.kt") + text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt"),
        "interactive plan forms must keep keyboard options")
require("enableEdgeToEdge()" in text("app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt"),
        "edge-to-edge setup is missing")
for resource_path in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-pt-rBR/strings.xml",
    "feature/plans/src/main/res/values/strings.xml",
    "feature/plans/src/main/res/values-pt-rBR/strings.xml",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
):
    first_lines = "\n".join(text(resource_path).splitlines()[:3])
    require("tools:ignore=\"PluralsCandidate,UnusedResources\"" not in first_lines,
            f"file-scope warning suppression remains in {resource_path}")
    require("tools:ignore=\"UnusedResources\"" not in first_lines,
            f"file-scope warning suppression remains in {resource_path}")
for prefix in ("app/src/main/res", "feature/plans/src/main/res", "feature/settings/src/main/res", "feature/workspace/src/main/res"):
    en_path = f"{prefix}/values/strings.xml"
    pt_path = f"{prefix}/values-pt-rBR/strings.xml"
    if (ROOT / en_path).is_file() and (ROOT / pt_path).is_file():
        require(resource_names(en_path) == resource_names(pt_path), f"English/pt-BR resource parity failed for {prefix}")
for xml in ROOT.rglob("*.xml"):
    if "/build/" in str(xml):
        continue
    ET.parse(xml)

# Acceptance documentation should exist and be honest about target-machine boundaries.
for path in (
    "FINAL_FULL_APP_ACCEPTANCE_SEAL.md",
    "docs/validation/STUDYFLOW_FINAL_ACCEPTANCE_MATRIX.md",
):
    require((ROOT / path).is_file(), f"missing final acceptance documentation: {path}")
    doc = text(path)
    require("PENDING TARGET EXECUTION" in doc, f"{path} must not pretend real-device evidence was executed")
    require("Devtool" in doc and "lint" in doc.lower(), f"{path} must call out Devtool/lint target gates")

# Source hygiene for files touched by the final seal.
for source_path in (
    "build.gradle.kts",
    ".devtool.toml",
    "scripts/verify_final_app_acceptance.py",
):
    source = text(source_path)
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path}:{line_number} contains a tab")
        require(line.rstrip() == line, f"{source_path}:{line_number} has trailing whitespace")
        if source_path.endswith(".kts"):
            require(len(line) <= 120, f"{source_path}:{line_number} has {len(line)} characters")

if FAILURES:
    print("Final StudyFlow source acceptance contract failed:")
    for failure in FAILURES:
        print(f" - {failure}")
    sys.exit(1)

print("Final StudyFlow source acceptance contract passed")
