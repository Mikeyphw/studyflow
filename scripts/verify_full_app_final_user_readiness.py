#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


# Validation hygiene: stale generated lint reports must be deleted before contract execution so
# Devtool does not report warnings from a previous build when fail-fast stops before lint runs.
build_gradle = read("build.gradle.kts")
validate_repo = read("scripts/validate_repo.sh")
require("*/build/reports/lint*" in build_gradle and "-type f -delete" in build_gradle,
        "verifyRepositoryContracts must delete stale lint report files before validation")
require("*/build/reports/lint*" in validate_repo and "-type f -delete" in validate_repo,
        "validate_repo.sh must delete stale lint report files before validation")

# Release-blocker validators and minSdk runtime floor.
practice = read("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt")
require("pluralStringResource(\n                                    R.plurals.question_count_value," in practice,
        "Practice question-count plurals must be wrapped below the Phase 15 line limit")
phase29 = read("scripts/verify_phase_29.py")
require("plural_names" in phase29 and "settings plural resource parity failed" in phase29,
        "Phase 29 validator must handle Settings plurals through resource parity")
planner = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySchedulePlanner.kt")
require("java.time" not in planner and "Calendar" in planner and "TimeZone" in planner,
        "StudySchedulePlanner must avoid java.time for API 23-25 runtime safety")
engine = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt")
require("java.util.Base64" not in engine and "Base64UrlAlphabet" in engine,
        "PracticeExecutionEngine must avoid java.util.Base64 for API 23-25 runtime safety")


# Compile-floor regression from focused validation: suspend tests must call suspend APIs from runBlocking
# while preserving JUnit block-body Unit methods.
annex_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ManageAnnexFileStorageTest.kt")
require("import kotlinx.coroutines.runBlocking" in annex_test,
        "ManageAnnexFileStorageTest must import runBlocking for suspend importCopy")
require("fun rejectsBlankName() {" in annex_test and "runBlocking {\n                manager.importCopy(" in annex_test,
        "ManageAnnexFileStorageTest rejectsBlankName must be a block-body Unit test that wraps suspend importCopy")
require("fun rejectsBlankName() =" not in annex_test,
        "ManageAnnexFileStorageTest rejectsBlankName must not use an expression body")


# Core domain behavior regressions found by full focused validation.
backup_drill = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BackupRestoreDrillSeal.kt")
require("applicationVersion = StudyPlanPrivacyPolicy.PrivateApplicationVersion" in backup_drill,
        "BackupRestoreDrillSeal private fixture must redact application metadata")
require('"examDateIso" to nullValue()' in backup_drill,
        "BackupRestoreDrillSeal private fixture must redact root exam date")
for column in ["status", "progressPercent", "studiedMinutesThisWeek", "questionsAnswered", "averageAccuracyPercent", "trashedAtEpochMillis"]:
    require(column in backup_drill,
            f"BackupRestoreDrillSeal private fixture must include sanitized history column {column}")
require("exportedAtEpochMillis = 0L" in backup_drill or "exportedAtEpochMillis = MillisPerDay" in backup_drill,
        "BackupRestoreDrillSeal private fixture export time must be day-granular")
runtime_smoke = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/RuntimeSmoke.kt")
require("probe.status == RuntimeSmokeProbeStatus.PASS" in runtime_smoke,
        "Runtime smoke reports must require every probe to pass before release-ready")
release_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSealTest.kt")
require("assertEquals(41, report.totalPhaseCount)" in release_test and
        "assertEquals(41, report.completedPhaseCount)" in release_test,
        "ReleaseReadinessSealTest must match the current 33-phase plus D1-D8 delivery report")
require("phase.number == 30" in release_test and "phase.number == 31" in release_test and "phase.number == 32" in release_test and "phase.number == 33" in release_test,
        "ReleaseReadinessSealTest must cover product truthfulness, dashboard action completion, diagnostics de-scaffolding, and offline checkpoint completion")
timer_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySessionTimerTest.kt")
require("assertEquals(5, StudySessionTimer.pausedSeconds(paused, nowEpochMillis = 66_000))" in timer_test,
        "StudySessionTimerTest must assert pause elapsed seconds, not active plus paused seconds")
remediator = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WrongAnswerRemediation.kt")
require("item.copy(status = ReviewStatus.DUE)" in remediator,
        "WrongAnswerRemediator must surface overdue scheduled linked reviews as due in notebook entries")

# Final-user diagnostics and responsive UI.
settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
require("diagnosticsAvailable: Boolean" in settings and "if (diagnosticsAvailable)" in settings,
        "Debug Workbench card must be gated instead of always visible to release users")
require("connectionStatus.name" not in settings and "syncConnectionStatusLabel" in settings,
        "Settings sync card must use localized connection status")
require("AppShellReadinessAuditor" not in settings and "ReleaseReadinessSeal" not in settings,
        "Normal Settings must not show app-shell readiness or release seal scaffolding")
require("ReleaseCandidatePolishAuditor" not in settings and "UserExperiencePolishAuditor" not in settings,
        "Normal Settings must not show polish checklist scaffolding")
for marker in ["app_shell_readiness_title", "release_candidate_polish_title", "final_seal_title", "ux_polish_title"]:
    require(marker not in settings, f"Normal Settings still references internal release card {marker}")
dashboard = read("feature/dashboard/src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt")
require("Button(onClick = onStartSession)" in dashboard and "onStartSession = onOpenSessions" in dashboard,
        "Dashboard primary session action must route to the sessions flow")
require("Button(onClick = {})" not in dashboard,
        "Dashboard must not keep a no-op primary button")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
require("diagnosticsAvailable = BuildConfig.DEBUG" in app,
        "App must hide debug workbench entry when the recorder is disabled in release builds")
workbench = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
labs = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugLabsPanel.kt")
require("FlowRow" in workbench, "Debug Workbench section chips must wrap")
require("FlowRow" in labs, "Debug lab chips must wrap")

# Sync final-user labels and token hiding.
sync = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt")
require("connectionStatus.name" not in sync and "conflictPolicy.name" not in sync,
        "Sync screen must not expose raw enum names")
require("currentSummary.localChangeToken" not in sync and "currentSummary.changeCount" in sync,
        "Sync screen must hide local change tokens")
sync_model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt")
require("val changeCount: Int = planCount" in sync_model,
        "StudySyncChangeSummary must expose a stable aggregate changeCount")
for rel in ["feature/settings/src/main/res/values/strings.xml", "feature/settings/src/main/res/values-pt-rBR/strings.xml"]:
    text = read(rel)
    for key in [
        "sync_connection_local_only", "sync_connection_local_ready", "sync_connection_checkpoint_prepared",
        "sync_connection_checkpoint_error", "sync_conflict_last_write_wins", "sync_conflict_manual_review",
        "sync_conflict_local_only", "sync_change_count",
    ]:
        require(f'name="{key}"' in text, f"missing localized sync label {key} in {rel}")
require("sync_change_token" not in sync, "Sync screen must not reference the raw token label")

# Destructive final-user actions require confirmation in the audited feature surfaces.
for rel in [
    "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
    "feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt",
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt",
]:
    text = read(rel)
    require("AlertDialog" in text and ("ConfirmingDelete" in text or "ConfirmingSnapshotDelete" in text),
            f"{rel} must confirm destructive deletes")
for module in ["progress", "library", "practice", "schedule", "plans"]:
    for values in ["values", "values-pt-rBR"]:
        rel = f"feature/{module}/src/main/res/{values}/strings.xml"
        if (ROOT / rel).is_file():
            text = read(rel)
            require('name="confirm_delete_title"' in text, f"{rel} missing confirm_delete_title")
            require('name="confirm_delete_message"' in text, f"{rel} missing confirm_delete_message")

# Safe saved enum decoding.
for rel in [
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentDialogs.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt",
]:
    text = read(rel)
    require("enumValueOrDefault" in text, f"{rel} must use safe enum restoration")


# User-facing flows must not convert fatal JVM errors into routine UI failures.
for rel in [
    "feature/search/src/main/java/com/mikeyphw/studyflow/feature/search/StudySearchScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt",
]:
    text = read(rel)
    require("catch (_: Throwable)" not in text and "catch (error: Throwable)" not in text,
            f"{rel} must preserve fatal errors and only catch recoverable exceptions")

# Support-bundle seal accuracy.
seal = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchSeal.kt")
actions = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt")
require("CHECKSUM_MISMATCH" in seal and "actualChecksums" in seal,
        "Support bundle seal must verify checksum contents")
require("actualPayloadChecksums" in actions and "lastSealReport.set" in actions and "BUNDLE_MISSING" in actions,
        "Support bundle actions must verify ZIP payload digests and clear stale pass state")

# XML parse and resource parity for touched modules.
for path in ROOT.glob("feature/*/src/main/res/values*/strings.xml"):
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"invalid XML {path.relative_to(ROOT)}: {exc}")

if errors:
    print("FULL_APP_FINAL_USER_READINESS: FAILED")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("FULL_APP_FINAL_USER_READINESS: PASSED")
