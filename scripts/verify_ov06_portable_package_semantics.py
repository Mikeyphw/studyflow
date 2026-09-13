#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def text(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    data = text(path)
    for token in tokens:
        if token not in data:
            errors.append(f"{path}: missing contract token {token!r}")

models = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageModels.kt"
codec = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt"
dup = "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt"
store = "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt"
privacy = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPrivacyPolicy.kt"
compat = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanImportCompatibility.kt"
diff = "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanImportDiffEngine.kt"
repo = "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt"
desktop = "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPortabilityController.kt"
android_test = "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyPlanPortabilityOv06Test.kt"
unit_test = "core/portability/src/test/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanOv06SemanticsTest.kt"
doc = "docs/quality/OV06_PORTABLE_PACKAGE_SEMANTICS_RESTORE_MODES.md"
ledger = "docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md"

require(models,
        "StudyPlanPackageSchemaVersion = 2",
        "StudyPlanPackageMinimumReadableSchemaVersion = 1",
        "enum class StudyPlanPackagePurpose",
        "POINT_IN_TIME_SNAPSHOT",
        "val packagePurpose: StudyPlanPackagePurpose = StudyPlanPackagePurpose.PORTABLE")
require(codec,
        "payload.manifest.schemaVersion != StudyPlanPackageSchemaVersion",
        "manifest.schemaVersion !in StudyPlanPackageMinimumReadableSchemaVersion..StudyPlanPackageSchemaVersion",
        "manifest.schemaVersion != StudyPlanPackageSchemaVersion",
        "Point-in-time snapshots require schema 2 FULL unredacted graph metadata")
require(dup,
        "val PortabilityTableSpecs: List<TableSpec> = TableSpecs + AnnexSpec",
        "sourcePlanPredicate",
        "termux_command_results",
        "language_review_attempts")
require(store,
        "payload.manifest.schemaVersion == 1",
        "projectPayloadForImport",
        "mergeSingletonReferenceOverrides",
        "payload.manifest.annexPayloads",
        "StudyPlanPackagePurpose.POINT_IN_TIME_SNAPSHOT",
        "spec.sourcePlanPredicate",
        "PortabilityTableSpecs.asReversed()")
require(privacy,
        '"language_review_attempts"',
        '"termux_command_requests"',
        '"termux_command_results"',
        "historyRedacted(options)",
        '"legislation_provisions"',
        '"study_cycles"',
        '"workspaceRelativePath" to text("course")',
        '"workspaceRoot" to text("~/studyflow/courses")')
require(compat,
        "MergeSingletonTables",
        '"study_targets"',
        '"study_cycles"',
        '"programming_course_profiles"',
        '"termux_runtime_preferences"',
        '"language_deck_profiles"')
require(diff, "fun diffProjected(", "ignoredRowsByTable")
require(repo,
        "graphStore.projectPayloadForImport",
        "StudyPlanImportDiffEngine.diffProjected",
        "override suspend fun restoreSnapshot",
        "StudyPlanPackagePurpose.POINT_IN_TIME_SNAPSHOT",
        "preserveSnapshotMetadata",
        "replacedFinalFiles")
require(desktop, "StudyPlanPrivacyPolicy.sanitizeRow(table.name, row, options)")
require(android_test,
        "fullReplaceCoversParentScopedLanguageAndTermuxRows",
        "mergeRebindsChildrenToRetainedSingletonParent",
        "exactSnapshotRestoresRootStateAndTimestamps",
        "legacyV1MayOmitModernTablesButV2IsStrict")
require(unit_test,
        "schemaAndPurposeContractsAreExplicit",
        "structureAndTemplateStripProgressAndDeviceState",
        "projectedDiffConsumesProductionRemappedIds")
require(doc, "Overlay 6 of 30", "POINT_IN_TIME_SNAPSHOT", "Dry-run")
ledger_data = text(ledger)
for finding in [f"S3-0{i}" for i in range(1, 9)]:
    if finding not in ledger_data:
        errors.append(f"closure ledger missing {finding}")

if errors:
    print("OV06 portable-package verifier: FAIL", file=sys.stderr)
    for err in errors:
        print(f"- {err}", file=sys.stderr)
    raise SystemExit(1)
print("OV06 portable-package verifier: PASS")
