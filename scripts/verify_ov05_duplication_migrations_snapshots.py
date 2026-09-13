#!/usr/bin/env python3
from pathlib import Path
import json, sys
root=Path(__file__).resolve().parents[1]
checks=[]
def need(path,*tokens):
    text=(root/path).read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f"OV05 missing {token!r} in {path}")

dup='core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt'
need(dup, 'termux_command_requests', 'termux_command_results', 'language_deck_profiles', 'language_review_attempts',
     'remapDuplicatedPracticeConfigurations', 'questionSnapshotV1=', 'sourcePlanPredicate')

text=(root/dup).read_text()
if 'TableSpecs.filterNot { it.tableName in DuplicationOnlyTableNames } + AnnexSpec' not in text:
    raise SystemExit('OV05 must keep parent-scoped duplication rows out of legacy portability until OV06')
for test_path in [
    'core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicationOv05Test.kt',
    'core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowOv05MigrationCoverageTest.kt',
    'core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotWriterOv05Test.kt',
    'core/data/src/test/kotlin/com/mikeyphw/studyflow/core/data/AnnexOrphanCleanupAccountingTest.kt',
]:
    if not (root/test_path).is_file(): raise SystemExit(f'missing OV05 behavior test {test_path}')

need('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt',
     'WHEN activity.plannedDurationMinutes < 5 THEN 5', 'WHEN activity.plannedDurationMinutes > 720 THEN 720',
     'Migration14To15')
need('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowIntegrity.kt',
     'NEW.targetDurationMinutes BETWEEN 5 AND 720')
need('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt', 'SCHEMA_VERSION = 15')
need('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDatabaseSnapshotWriter.kt',
     'SnapshotExcludedPrefixes', 'studyflow_search_fts', 'PRAGMA table_info', 'ORDER BY $orderBy')
need('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomAnnexFileStorageRepository.kt',
     'deleteOrphanCandidate(file)', 'if (delete(file))', 'removedBytes = candidateBytes', 'removedBytes = 0L')
for version in (7,8,15):
    p=root/f'core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/{version}.json'
    d=json.loads(p.read_text())
    if d['database']['version'] != version: raise SystemExit(f'bad schema version {version}')
print('OV05 source verifier: PASS')
