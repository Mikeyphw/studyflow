#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

ROOT=Path(__file__).resolve().parents[1]

def text(path): return (ROOT/path).read_text(encoding='utf-8')
def require(cond,msg):
    if not cond:
        print(f'OV04 VERIFY FAIL: {msg}', file=sys.stderr); raise SystemExit(1)

integrity=text('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowIntegrity.kt')
migrations=text('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt')
database=text('core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt')
move=text('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt')
diag=text('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyDatabaseDiagnostics.kt')
cycle=text('core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomPublicExamRepository.kt')

modern=[
 'exam_notice_revisions','study_cycles','study_cycle_items','programming_course_profiles','code_snippets',
 'coding_exercises','mini_projects','termux_runtime_preferences','termux_command_requests','termux_command_results',
 'language_deck_profiles','language_expressions','idiom_cards','vocabulary_cards','language_review_attempts',
]
for table in modern:
    require(f'"{table}"' in integrity, f'modern table missing from integrity registry: {table}')
require('active.studyPlanId = NEW.studyPlanId' not in integrity, 'active-session guard is still per-plan')
require("only one active Study Session is allowed globally" in integrity, 'global active-session guard missing')
for token in ['ScopedReference("questions", "examStageId", "exam_stages")',
              'ScopedReference("questions", "examNoticeRevisionId", "exam_notice_revisions")']:
    require(token in integrity, f'question provenance guard missing: {token}')
for table in ['code_snippets','coding_exercises','mini_projects','study_cycle_items']:
    require(f'"{table}"' in move[move.index('LINKED_TOPIC_TABLES'):], f'moveTopic does not rebind {table}')
require('logicalIntegrityViolationCount(sqlite)' in diag, 'database diagnostics do not use canonical integrity engine')
require('Migration13To14' in migrations and 'StudyFlowIntegrity.reinstall(db)' in migrations,
        'explicit trigger replacement migration missing')
require('SCHEMA_VERSION = 14' in database, 'database schema version is not 14')
require('currentItemId = null' in cycle and 'setCurrentStudyCycleItem(' in cycle,
        'cycle replacement does not assign current pointer after item creation')
for test in [
 'core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowOv04IntegrityTest.kt',
 'core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration13To14IntegrityTest.kt',
 'core/data/src/androidTest/kotlin/com/mikeyphw/studyflow/core/data/StudyTopicMoveModernOwnershipTest.kt',
]: require((ROOT/test).is_file(), f'missing behavioral test {test}')
schema=json.loads(text('core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/14.json'))
require(schema['database']['version']==14, 'schema 14 export missing/wrong version')
print('OV04 VERIFY PASS: Room graph ownership, revision, provenance, cycle pointer, diagnostics and trigger migration contracts present.')
