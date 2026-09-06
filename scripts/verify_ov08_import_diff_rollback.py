#!/usr/bin/env python3
from pathlib import Path
import sys, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def read(rel): return (ROOT/rel).read_text()
models=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageModels.kt")
engine=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanImportDiffEngine.kt")
compat=read("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanImportCompatibility.kt")
store=read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt")
repo=read("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt")
screen=read("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt")
for marker in ("StudyPlanImportTableDiff", "tableDiffs: List<StudyPlanImportTableDiff>", "transactionalRollbackProtected"):
    if marker not in models: errors.append(f"models missing {marker}")
for marker in ("CREATE_AS_NEW", "REPLACE_EXISTING", "MERGE_COMPATIBLE", "addedRows", "removedRows", "changedRows", "ignoredRows"):
    if marker not in engine: errors.append(f"diff engine missing {marker}")
for table in ("study_targets", "exam_notice_revisions", "study_cycles", "study_plan_settings"):
    if f'"{table}"' not in compat: errors.append(f"merge compatibility missing {table}")
if "StudyPlanImportCompatibility.MergeSkippedTables" not in store:
    errors.append("import execution does not share the centralized merge skip contract")
for marker in ("StudyPlanImportDiffEngine.diff", "currentPayload", "databaseRowsChanged = 0", "transactionalRollbackProtected = true"):
    if marker not in repo: errors.append(f"dry-run repository missing {marker}")
for marker in ("import_diff_summary", "tableDiffs.sumOf", "import_transactional_rollback"):
    if marker not in screen: errors.append(f"portability UI missing {marker}")
for rel in ("feature/plans/src/main/res/values/strings.xml","feature/plans/src/main/res/values-pt-rBR/strings.xml"):
    ET.parse(ROOT/rel)
if errors:
    print("OV08 import diff/rollback FAILED", file=sys.stderr)
    print("\n".join(f"- {e}" for e in errors), file=sys.stderr)
    raise SystemExit(1)
print("OV08_IMPORT_DIFF_ROLLBACK: PASS")
