#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    file = ROOT / path
    require(file.is_file(), f"missing required file: {path}")
    return file.read_text(encoding="utf-8") if file.is_file() else ""


def require_tokens(path: str, *tokens: str) -> None:
    value = text(path)
    for token in tokens:
        require(token in value, f"{path}: missing contract token {token!r}")


# Repository reconciliation.
require_tokens(
    "README.md",
    "CPOV13 public-exam integration seal implemented",
    "CPOV_PUBLIC_EXAM_COMPLETION_MATRIX.md",
)
require_tokens(
    "CHANGELOG.md",
    "CPOV13 public-exam full integration and release seal",
    "9->10->11->12->13",
)
require_tokens(
    "docs/roadmap/CPOV_PUBLIC_EXAM_COMPLETION_MATRIX.md",
    "CPOV00",
    "CPOV13",
    "source validators never claim signed-artifact or real-device evidence",
)
require_tokens(
    "docs/architecture/ADR-0035-public-exam-specialization.md",
    "Edital revisions",
    "Study cycle",
    "Explainable priority",
    "Banca blueprints",
    "Legislation",
)
require_tokens(
    "CPOV13_PUBLIC_EXAM_FULL_INTEGRATION_RELEASE_SEAL.md",
    "9 -> 10 -> 11 -> 12 -> 13",
    "No root access is required",
    "source/build validation does not manufacture signed or manual device evidence",
)

# Temporary CPOV patch scaffolding must not ship.
backup_globs = ["*.bak", "*.pre-*", "*.orig", "*.rej"]
for pattern in backup_globs:
    offenders = [
        item.relative_to(ROOT).as_posix()
        for item in ROOT.rglob(pattern)
        if ".git" not in item.parts and "build" not in item.parts
    ]
    require(not offenders, f"temporary patch artifacts remain for {pattern}: {offenders[:5]}")
require(not (ROOT / ".patch-backups").exists(), ".patch-backups must be removed by the final seal")

# Database and migration chain.
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
require("const val SCHEMA_VERSION = 13" in database, "CPOV final schema must remain 13")
chain = ["Migration9To10", "Migration10To11", "Migration11To12", "Migration12To13"]
for token in chain:
    require(token in migrations, f"missing migration {token}")
all_match = re.search(r"val All = arrayOf\(([\s\S]*?)\n\s*\)", migrations)
require(all_match is not None, "StudyFlowMigrations.All must remain explicit")
if all_match:
    body = all_match.group(1)
    positions = [body.find(token) for token in chain]
    require(all(pos >= 0 for pos in positions), "CPOV migrations must be present in StudyFlowMigrations.All")
    require(positions == sorted(positions), "CPOV migrations must stay ordered 9->10->11->12->13")

schema_dir = ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase"
required_tables = {
    "exam_notice_revisions",
    "study_cycles",
    "study_cycle_items",
    "legislation_documents",
    "legislation_provisions",
    "legislation_evidence_links",
}
for version in range(9, 14):
    path = schema_dir / f"{version}.json"
    require(path.is_file(), f"missing exported Room schema {version}.json")
    if not path.is_file():
        continue
    payload = json.loads(path.read_text(encoding="utf-8"))
    require(payload.get("database", {}).get("version") == version, f"schema {version} has wrong version")
    if version == 13:
        tables = {entity.get("tableName") for entity in payload["database"].get("entities", [])}
        require(required_tables <= tables, "schema 13 is missing persisted CPOV tables")
        questions = next(
            (entity for entity in payload["database"].get("entities", []) if entity.get("tableName") == "questions"),
            None,
        )
        require(questions is not None, "schema 13 is missing questions")
        if questions:
            columns = {field.get("columnName") for field in questions.get("fields", [])}
            provenance_columns = {
                "organization",
                "role",
                "examStageId",
                "authorship",
                "examNoticeRevisionId",
                "externalReference",
                "points",
            }
            require(
                provenance_columns <= columns,
                "schema 13 is missing CPOV06 question provenance columns",
            )

require_tokens(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration9To13CpovSealTest.kt",
    "representativePhase64FixtureMigratesThroughEveryCpovSchema",
    "freshSchemaCreatesTheSameCpovEndpoint",
    "StudyFlowMigrations.Migration9To10",
    "StudyFlowMigrations.Migration12To13",
)

# Portability and ownership.
duplicator = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt")
store = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt")
for table in sorted(required_tables | {"study_targets"}):
    require(f'"{table}"' in duplicator, f"portability/duplication inventory is missing {table}")
for column in ["organization", "role", "authorship", "examNoticeRevisionId", "externalReference", "points"]:
    require(f'"{column}"' in duplicator, f"question portability is missing {column}")
for token in [
    'rows("exam_notice_revisions")',
    'rows("study_cycle_items")',
    'rows("legislation_evidence_links")',
    '"questionId", "questions"',
]:
    require(token in store, f"portability ownership validation is missing {token}")
require_tokens(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyPlanCpovPortabilitySealTest.kt",
    "fullPackageExportsAndRestoresEveryPersistedCpovConceptWithOwnershipRemapped",
    "exam_notice_revisions",
    "study_cycles",
    "legislation_evidence_links",
    "OFFICIAL",
)

# Connected CPOV journey plus existing kill/restart authority.
require_tokens(
    "app/src/androidTest/java/com/mikeyphw/studyflow/PublicExamCpovConnectedJourneyTest.kt",
    "PUBLIC_EXAMINATION",
    "prepareExamNoticeRevision",
    "replaceStudyCycleFromSyllabus",
    "recordWrongAnswer",
    "createMockExamFromBlueprint",
    "PublicExamDashboardBuilder.build",
    "StudySprintPlanner.preview",
    "StudyPlanImportStrategy.CREATE_AS_NEW",
)
require_tokens(
    "app/src/androidTest/java/com/mikeyphw/studyflow/StudyFlowFullJourneyAcceptanceTest.kt",
    "restoredJourneySurvivesProcessRestartAndCleansUp",
    "processInstanceToken",
)

# Full validation wiring.
build = text("build.gradle.kts")
require_tokens(
    "build.gradle.kts",
    'tasks.register<Exec>("verifyCpov13PublicExamReleaseSeal")',
    '"verifyCpov13PublicExamReleaseSeal"',
    '"verifyFinalSourceAcceptance"',
    '"verifyRepositoryContracts"',
)
devtool = text(".devtool.toml")
import tomllib
devtool_config = tomllib.loads(devtool)
android_target = devtool_config["targets"]["studyflow_android"]
android_tasks = android_target["tasks"]
android_phases = android_target["validation"]["phases"]
require(android_target.get("runner") == "android", "Devtool Android target must use the canonical runner")
require(android_target.get("android", {}).get("instrumentation", {}).get("policy") == "when-device-present", "Devtool instrumentation policy drifted")
require(android_target.get("validation", {}).get("split_gradle") is False, "Devtool Android validation must coalesce Gradle")
for phase in ("dependencies", "compile", "resources"):
    require(android_phases.get(phase) == [], f"redundant Devtool phase must stay disabled: {phase}")
require(":app:assembleDebug" in android_tasks.get("build", []), "ordinary Devtool validation must assemble Debug")
require(":desktopApp:test" not in android_tasks.get("build", []), "ordinary Android build must not run Desktop tests")
require(":core:database:connectedDebugAndroidTest" in android_phases.get("instrumentation", []), "database instrumentation gate missing")
require(":feature:workspace:connectedDebugAndroidTest" in android_phases.get("instrumentation", []), "workspace instrumentation gate missing")
require(":app:connectedReleaseAndroidTest" in android_phases.get("instrumentation", []), "release instrumentation gate missing")
require(android_tasks.get("lint") == [":app:lintDebug", ":app:lintRelease"], "Android lint must be app-level Debug/Release")
quality_target = devtool_config["targets"].get("studyflow_quality", {})
require(quality_target.get("tasks", {}).get("lint") == ["verifyFormatting"], "repository-wide formatting must stay available through studyflow_quality")
require("checkDependencies = true" in text("app/build.gradle.kts"), "app lint must include dependency modules")
require(
    ":app:assembleRelease" not in android_tasks.get("build", []),
    "ordinary Devtool validation must keep signed Release packaging outside its artifact scan",
)
for token in (
    'tasks.register("verifyFinalReleaseHostGate")',
    '":app:assembleRelease"',
    '":app:bundleRelease"',
):
    require(token in build, f"credentialed final Release gate is missing {token}")

# Existing signed/manual final verdict must stay fail closed rather than being weakened by CPOV13.
require_tokens(
    "scripts/verify_final_release_verdict.py",
    'errors.append("signed artifact identity is not verified")',
    'errors.append("connected Android test evidence is not PASS")',
    'errors.append("device acceptance evidence is not PASS")',
    '"releaseReady": not errors',
)
require_tokens(
    "scripts/run_final_release_acceptance.sh",
    "exactly one adb device required",
    "The product is still NOT sealed",
    "run_target_release_validation.py --mode verdict",
)

if FAILURES:
    print("CPOV13_PUBLIC_EXAM_RELEASE_SEAL: FAILED", file=sys.stderr)
    for failure in FAILURES:
        print(f"- {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    "CPOV13_PUBLIC_EXAM_RELEASE_SEAL: PASSED "
    "(schema 9->13, portability, connected journey, full Debug/Release gates, fail-closed verdict)"
)
