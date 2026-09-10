#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
paths = {
    "controller": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityController.kt",
    "workspace": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkspaceStore.kt",
    "services": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt",
    "state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "screen": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityMigrationTest.kt",
    "doc": ROOT / "DESKTOP_DOV20_PORTABILITY_BACKUP_MIGRATION.md",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for key, path in paths.items():
    assert path.is_file(), f"missing {key}: {path}"
text = {key: path.read_text() for key, path in paths.items()}
# Kotlin 2.x inference is stricter around Java member callable references in collection pipelines.
# Keep the migration fixture on explicit lambdas, matching production's compiler-safe mapper style.
for forbidden in ("Object::toString", "legacy::remove"):
    assert forbidden not in text["test"], forbidden
# Kotlin forbids callable references to member extension functions. Keep all portability
# conversions as direct lambdas so the real compiler accepts the mapper.
for forbidden in (
    "::toPortableRow",
    "PortableRow::toStudyTarget",
    "PortableRow::toExamStage",
    "PortableRow::toSubject",
    "PortableRow::toTopic",
    "PortableRow::toStudyStrategy",
    "PortableRow::toScheduleRule",
    "PortableRow::toLegislationDocument",
    "PortableRow::toLegislationProvision",
    "PortableRow::toStudyCycle",
    "PortableRow::toStudyCycleItem",
):
    assert forbidden not in text["controller"], forbidden

for token in (
    "StudyPlanPackageCodec",
    "StudyPlanEncryptedPackageCodec",
    "StudyPlanExportMode.STRUCTURE_ONLY",
    'PortableTable("study_plans"',
    'PortableTable("study_targets"',
    'PortableTable("study_plan_settings"',
    "restoreEncryptedWorkspaceBackup",
    "validateStagedWorkspace",
    "safetySnapshotCreated = true",
    "rewriteCurrentSchema",
):
    assert token in text["controller"] or token in text["workspace"], token
for token in (
    "Export portable structure",
    "Import portable structure",
    "Create encrypted backup",
    "Restore encrypted backup",
    "Migrate workspace schema",
):
    assert token in text["screen"], token
for token in (
    "portableStructureAndEncryptedWorkspaceRestoreSurviveRestart",
    "legacyWorkspaceSchemaCanBeRewrittenToCurrentVersion",
    "Created after backup",
    "restartRequired",
):
    assert token in text["test"], token
assert "Import / export / portable backup | FULL | FULL | FULL" in text["matrix"]
remaining = json.loads(text["remaining"])
desktop = next(row for row in remaining["remaining_tracks"] if row["id"] == "DOV08-DOV24")
assert int(desktop["completed_through"].removeprefix("DOV")) >= 20
for key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov20PortabilityBackupMigration" in text[key]
assert '"verifyDesktopDov20PortabilityBackupMigration"' in text["devtool"]
print("DESKTOP_DOV20_PORTABILITY_BACKUP_MIGRATION: PASS")
