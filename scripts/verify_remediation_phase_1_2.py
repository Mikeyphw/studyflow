#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"missing required file: {path}")
    return p.read_text(encoding="utf-8", errors="strict")

def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)

def fail(message: str) -> None:
    print(f"PHASE_1_2_REMEDIATION: FAILED — {message}", file=sys.stderr)
    raise SystemExit(1)

# Complete wrapper contract.
for wrapper_path in (
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
):
    require((ROOT / wrapper_path).is_file(), f"Gradle wrapper is incomplete: {wrapper_path}")
require((ROOT / "gradlew").stat().st_mode & 0o111 != 0, "gradlew is not executable")
wrapper_jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
try:
    with zipfile.ZipFile(wrapper_jar) as archive:
        require(
            "org/gradle/wrapper/GradleWrapperMain.class" in archive.namelist(),
            "Gradle wrapper JAR has no GradleWrapperMain bootstrap",
        )
except zipfile.BadZipFile:
    fail("Gradle wrapper JAR is not a valid JAR/ZIP")
wrapper_properties = text("gradle/wrapper/gradle-wrapper.properties")
version_match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip", wrapper_properties)
require(version_match is not None, "Gradle wrapper URL has no stable version")
version = tuple(int(part) for part in version_match.group(1).split(".")) if version_match else (0,)
version = version + (0,) * (3 - len(version))
require(version >= (9, 7, 0), "Gradle wrapper must use stable Gradle >= 9.7.0")
checksum_match = re.search(r"(?m)^distributionSha256Sum=([0-9a-f]{64})$", wrapper_properties)
require(checksum_match is not None, "Gradle wrapper distribution checksum is missing or malformed")
if version_match and version_match.group(1) == "9.7.1":
    require(
        checksum_match is not None and checksum_match.group(1) == "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a",
        "Gradle 9.7.1 wrapper checksum is unexpected",
    )


# Release validation cannot remain debug-only or silently skip Android.
build = text("build.gradle.kts")
validate = text("scripts/validate_repo.sh")
devtool = text(".devtool.toml")
for token in (":app:compileReleaseKotlin", ":app:lintRelease"):
    require(token in build, f"checkAll is missing release gate {token}")
require("verifyFinalReleaseAutomatedGate" in validate, "validate_repo.sh does not run the final automated Release gate")
require(
    "verifyFinalReleaseAutomatedGate" in build
    and "verifyFinalReleaseHostGate" in build
    and ":app:assembleRelease" in build,
    "final automated Release gate does not own the signed host gate and Release assembly",
)
require("ANDROID VALIDATION: SKIPPED" not in validate, "validate_repo.sh still permits a false-green Android skip")
require(":app:assembleRelease" in devtool, "Devtool does not package the Release variant")

# One process-owned data graph.
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
widget = text("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowWidgetUpdateCoordinator.kt")
receivers = text("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderReceivers.kt")
require("AppInitializationState" in app, "application startup has no explicit initialization state")
require("createStudyFlowDataLayer" not in widget, "widget still constructs a private StudyFlowDataLayer")
require("createStudyFlowDataLayer" not in receivers, "receiver still constructs a private StudyFlowDataLayer")
require("requireStudyFlowAppContainer" in widget, "widget does not reuse the process AppContainer")
require("requireStudyFlowAppContainer" in receivers, "receivers do not reuse the process AppContainer")
require(
    "reconciliationCompleted" in app and "if (!reconciliationCompleted)" in app,
    "startup still performs an unconditional duplicate widget refresh after reminder reconciliation",
)

# Loading and one-shot reminder launch.
root_app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
main_activity = text("app/src/main/java/com/mikeyphw/studyflow/MainActivity.kt")
require("initialValue = AppPreferences()" not in root_app, "root UI still treats default preferences as loaded state")
require("StudyFlowLoadingPane" in root_app, "root UI has no loading state")
reminder_launch = text("app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderLaunchActivity.kt")
require("StudyReminderLaunchStore.consume()" in main_activity, "private reminder launch is not consumed one-shot")
require("StudyReminderLaunchStore.publish(launch)" in reminder_launch, "reminder trampoline does not publish validated routing")
require("pending = null" in reminder_launch, "reminder launch store does not clear consumed routing")
require("private fun Intent.toStudyReminderLaunch()" in reminder_launch, "private reminder routing is not validated")
require("StudyReminderExtraPlanId" in reminder_launch and "StudyReminderExtraType" in reminder_launch,
        "private reminder trampoline does not validate plan/type payload")
today_workspace = text(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt"
)
plan_workspace = text(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt"
)
require(
    "initialValue = StudySchedule(" not in today_workspace and
    "initialValue = emptyList()" not in today_workspace,
    "Today workspace still treats unloaded schedule/session data as a real empty state",
)
require(
    "initialValue = StudySchedule(" not in plan_workspace and
    "initialValue = emptyList()" not in plan_workspace,
    "Plan workspace still treats unloaded plan data as a real empty state",
)
require("workspaceLoadGate" in today_workspace, "Today workspace has no loading/failure/retry gate")
require("workspaceLoadGate" in plan_workspace, "Plan workspace has no loading/failure/retry gate")

# Schema v4 + migration correctness + integrity policy.
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
integrity = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowIntegrity.kt")
schema_match = re.search(r"SCHEMA_VERSION = (\d+)", database)
require(schema_match is not None and int(schema_match.group(1)) >= 4, "database schema was not advanced to v4")
require("Migration3To4" in migrations, "v3→v4 integrity migration is missing")
require("activity.plannedDurationMinutes" in migrations, "v2→v3 target-duration backfill is missing")
for token in (
    "stable_plan_owner",
    "single_active",
    'ScopedReference("topics", "subjectId", "subjects")',
    'ScopedReference("exam_stages", "studyTargetId", "study_targets")',
    'ScopedReference("flashcards", "subjectId", "subjects")',
    'table = "study_targets"',
    'table = "exam_stages"',
    "installPlanSettingsIdentityTriggers",
    "installTopicHierarchyUpdateGuard",
    "guard_topics_parent_subject_change",
    "installValueGuards",
):
    require(token in integrity, f"database integrity policy is missing {token}")
require((ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/4.json").is_file(),
        "Room schema v4 export is missing")

# Every v4 plan-owned table and every non-plan Room FK must be represented by the integrity policy.
schema4 = json.loads(
    text("core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/4.json")
)["database"]["entities"]
plan_owned_section = integrity[
    integrity.index("private val planOwnedTables"):integrity.index("private val scopedReferences")
]
integrity_compact = re.sub(r"\s+", "", integrity)
missing_plan_guards = []
missing_scoped_guards = []
for entity in schema4:
    table = entity["tableName"]
    field_names = {field["fieldPath"] for field in entity.get("fields", [])}
    if "studyPlanId" in field_names and table != "study_plan_settings" and f'"{table}"' not in plan_owned_section:
        missing_plan_guards.append(table)
    create_sql = entity["createSql"].replace("${TABLE_NAME}", table)
    for child_column, target_table, target_column in re.findall(
        r"FOREIGN KEY\(`([^`]+)`\) REFERENCES `([^`]+)`\(`([^`]+)`\)",
        create_sql,
    ):
        if child_column == "studyPlanId" and target_table == "study_plans":
            continue
        positional = f'ScopedReference("{table}","{child_column}","{target_table}")'
        named = (
            f'table="{table}",column="{child_column}",targetTable="{target_table}"'
        )
        if positional not in integrity_compact and named not in integrity_compact:
            missing_scoped_guards.append(f"{table}.{child_column}->{target_table}.{target_column}")
require(not missing_plan_guards, f"v4 plan-owned tables lack stable-owner guards: {missing_plan_guards}")
require(not missing_scoped_guards, f"v4 foreign-key relationships lack plan-scope guards: {missing_scoped_guards}")
for test_path in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowLoadStateFlowTest.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration3To4IntegrityTest.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowPlanningAtomicityTest.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/mapper/MapperSafetyTest.kt",
    "core/database/src/test/kotlin/com/mikeyphw/studyflow/core/database/mapper/StudyStructureEntityMapperTest.kt",
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/CoreModelInvariantTest.kt",
):
    require((ROOT / test_path).is_file(), f"missing Phase 1/2 regression test: {test_path}")

planning_atomicity_test = text(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowPlanningAtomicityTest.kt"
)
for token in (
    "staleSessionStatusCannotOverwriteNewerTransition",
    "noteUpdateCannotReopenOrMutateTerminalSession",
    "stalePlannedActivitySnapshotCannotOverwriteNewerState",
):
    require(token in planning_atomicity_test, f"missing Room atomicity regression: {token}")

# DAO/repository semantics and mutation primitives.
planning_dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPlanningDao.kt")
retention_dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyRetentionDao.kt")
practice_dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyPracticeDao.kt")
session_repo = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudySessionRepository.kt")
require("deleteReplaceablePlannedActivities" in planning_dao, "schedule replacement still uses destructive unlocked deletion")
require("status IN ('PLANNED', 'MISSED')" in planning_dao, "schedule replacement can still delete historical activity states")
require("findActiveStudySessions" in planning_dao and "LIMIT 1" not in planning_dao[planning_dao.find("findActiveStudySessions")-220:planning_dao.find("findActiveStudySessions")+80],
        "active-session DAO still hides duplicate rows")
for token in ("compareAndSetPlannedActivity", "compareAndSetStudySessionStatus", "updateActiveStudySessionNotes"):
    require(token in planning_dao, f"missing atomic planning mutation primitive: {token}")
require("incrementErrorRecurrence" in retention_dao, "missing atomic error recurrence primitive")
require("compareAndSetReviewState" in retention_dao, "missing review compare-and-set primitive")
require("submitStudyTestIfOpen" in practice_dao, "missing terminal practice-submit primitive")
require("active.size <= 1" in session_repo, "session repository still masks multiple active sessions")
require("requireValidSessionTransition" in session_repo, "session repository lacks terminal transition validation")
require("requireMaterial" in session_repo and "requireVideo" in session_repo, "session resource ownership is incomplete")

# Mapper corruption policy and complete structure mapping.
mapper_dir = ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper"
mapper_text = "\n".join(p.read_text() for p in mapper_dir.glob("*.kt"))
require("enumValueOf<" not in mapper_text, "raw enumValueOf remains in database mappers")
require("mapNotNull" not in mapper_text or "runCatching" not in mapper_text,
        "mapper list decoder can still silently drop corrupt elements")
require("StudyFlowDatabaseCorruptionException" in mapper_text, "mapper corruption exception is missing")
structure_mapper = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyStructureEntityMapper.kt")
for token in ("StudyTargetEntity.asExternalModel", "StudyTarget.asEntity", "ExamStage.asEntity"):
    require(token in structure_mapper, f"structure mapper is still asymmetric: {token}")
structure_mapper_test = text(
    "core/database/src/test/kotlin/com/mikeyphw/studyflow/core/database/mapper/StudyStructureEntityMapperTest.kt"
)
for token in (
    "studyTargetRoundTripsThroughEntity",
    "examStageRoundTripsThroughEntity",
):
    require(token in structure_mapper_test, f"missing structure mapping round-trip regression: {token}")

# Discovery joins must retain Study Plan scope.
discovery = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyDiscoveryDao.kt")
discovery_lines = discovery.splitlines()
unsafe_join_lines = []
for index, line in enumerate(discovery_lines):
    is_scoped_join = (
        "JOIN subjects" in line or "JOIN topics" in line or "JOIN questions" in line or
        "JOIN study_materials" in line or "JOIN error_entries" in line or "JOIN flashcards" in line
    )
    continuation = discovery_lines[index + 1] if index + 1 < len(discovery_lines) else ""
    if is_scoped_join and "studyPlanId" not in line and "studyPlanId" not in continuation:
        unsafe_join_lines.append(line.strip())
require(not unsafe_join_lines, f"cross-plan discovery join(s) remain: {unsafe_join_lines[:3]}")

# Core model invariants.
identifiers = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/Identifiers.kt")
practice = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPractice.kt")
plan = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPlan.kt")
require("require(value.isNotBlank())" in identifiers, "identifier wrappers still accept blanks")
require("requireValidIsoDate" in plan, "Study Plan exam date is not validated")
require("correct answer must be one of the options" in practice.lower(), "MCQ invariant is missing")


# Strengthened post-review contract checks.
load_model = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyFlowLoadState.kt")
load_flow = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowLoadStateFlow.kt")
for token in ("Loading", "Ready", "Missing", "Failed"):
    require(token in load_model, f"explicit load-state contract is missing {token}")
require("CancellationException" in load_flow, "load-state Flow adapter does not preserve coroutine cancellation")
require("asStudyFlowOptionalLoadState" in root_app, "plan destinations do not distinguish missing from loading/failure")
require("LoadedPlanDestination" in root_app, "legacy plan destinations still lack the explicit plan load gate")
plan_load_block = root_app[root_app.index("private fun LoadedPlanDestination"):root_app.index("private fun LoadedPlanSettingsDestination")]
require(
    "initialValue = StudyFlowLoadState.Loading" in plan_load_block
    and "asStudyFlowOptionalLoadState" in plan_load_block,
    "StudyFlowApp still conflates an unloaded plan Flow with a missing plan",
)
for workspace_path in (
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
):
    workspace = text(workspace_path)
    require("asStudyFlowLoadState" in workspace, f"{workspace_path} does not use explicit load-state Flows")
    require("workspaceLoadGate" in workspace, f"{workspace_path} has no loading/failure/retry gate")
    require("initialValue = emptyList()" not in workspace, f"{workspace_path} still uses fake empty loading state")

app_container_start = app.index("interface AppContainer")
app_container_end = app.index("class DefaultAppContainer", app_container_start)
app_container_contract = app[app_container_start:app_container_end]
for forbidden in (
    "studyPlanRepository",
    "studyScheduleRepository",
    "studySessionRepository",
    "studyReviewRepository",
    "studyPracticeRepository",
    "studyProgressRepository",
    "studyContentRepository",
    "studyLibraryRepository",
    "studyAnalyticsRepository",
    "studyDiscoveryRepository",
):
    require(forbidden not in app_container_contract,
            f"AppContainer still exposes mutable repository surface: {forbidden}")
app_source = "\n".join(
    p.read_text(encoding="utf-8")
    for p in (ROOT / "app/src/main/java").rglob("*.kt")
)
require("container.studyPlanRepository" not in app_source, "app code still bypasses ManageStudyPlan")
require("container.studyScheduleRepository" not in app_source, "app code still bypasses ManageStudySchedule")
require("container.studySessionRepository" not in app_source, "app code still bypasses ManageStudySession")
require("container.studyReviewRepository" not in app_source, "app code still bypasses ManageStudyReview")
require("container.studyPracticeRepository" not in app_source, "app code still bypasses ManageStudyPractice")

require("verifyExistingData" in integrity, "v4 migration does not preflight existing logical corruption")
require("verifyValueGuards" in integrity, "v4 migration does not preflight existing value/range corruption")
require("cleanup_exam_stage_subject_topic_refs" in integrity,
        "ExamStage deletion can leave dangling Subject/Topic logical references")
require("StudyFlowIntegrity.verifyExistingData(db)" in migrations,
        "v3→v4 migration installs guards without rejecting existing corruption first")
for token in ("compareAndSetPlannedActivityStatus", "completeStudySessionIfStatus"):
    require(token in planning_dao, f"session transition foundation is missing DAO primitive {token}")
for token in ("createSession", "transitionSession"):
    require(token in session_repo, f"session repository does not expose atomic lifecycle operation {token}")
transition_body = session_repo[session_repo.index("override suspend fun transitionSession"): ]
require("upsertStudySession(" not in transition_body,
        "existing StudySession transitions still use stale full-row UPSERT")
require("updateActiveStudySessionNotes" in session_repo,
        "session note writes are not field-specific active-only mutations")

mapper_safety = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/MapperSafety.kt")
require("decodeModel" in mapper_safety, "model-constructor failures are not normalized as database corruption")
mapper_wrapped_count = mapper_text.count("= decodeModel(")
require(mapper_wrapped_count >= 21,
        f"not every entity mapper is protected by decodeModel (found {mapper_wrapped_count}, expected at least 21)")
mapper_test = text("core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/mapper/MapperSafetyTest.kt")
require("invalidPersistedModelFailsWithStructuredCorruption" in mapper_test,
        "structured corrupt-model mapper regression test is missing")
migration_test = text("core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration3To4IntegrityTest.kt")
require("migrationRejectsPreexistingDuplicateActiveSessions" in migration_test,
        "v3→v4 duplicate-active-session rejection regression is missing")
require("migrationRejectsPreexistingInvalidPersistedValues" in migration_test,
        "v3→v4 invalid-value rejection regression is missing")
require("SELECT examStageId FROM subjects" in migration_test and "SELECT examStageId FROM topics" in migration_test,
        "ExamStage delete-cleanup regression does not verify Subject and Topic references")

print("PHASE_1_2_REMEDIATION: PASSED")
