from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"Missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""

models = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/PublicExam.kt")
entities = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/PublicExamEntities.kt")
dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/PublicExamDao.kt")
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
revision = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamNoticeRevisionPlanner.kt")
cycle_planner = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCyclePlanner.kt")
public_api = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamRepository.kt")
room_public = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomPublicExamRepository.kt")
session_contract = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySessionRepository.kt")
session_room = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudySessionRepository.kt")
duplicator = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt")
portability = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt")
privacy = text("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPrivacyPolicy.kt")
content_ui = text("feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt")
schedule_ui = text("feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt")
today_ui = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt")
nav = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
data_layer = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt")

for token in (
    "data class ExamNoticeRevision(",
    "data class StudyCycle(",
    "data class StudyCycleItem(",
    "data class StudyCycleSessionBinding(",
):
    require(token in models, f"Public-exam model missing: {token}")

for token in ('tableName = "exam_notice_revisions"', 'tableName = "study_cycles"', 'tableName = "study_cycle_items"'):
    require(token in entities, f"Room entity missing: {token}")
require("SCHEMA_VERSION = 11" in database, "Room schema must advance to version 11")
require("Migration(9, 10)" in migrations and "Migration(10, 11)" in migrations, "Room 9->10->11 chain missing")
require("Migration9To10" in migrations and "Migration10To11" in migrations, "Public-exam migrations missing from source")

for token in (
    "baseSyllabusSha256",
    "A newer exam notice revision was accepted after this preview",
    "The syllabus changed after this exam notice preview",
    "archiveSubject",
    "archiveTopic",
):
    require(token in revision + room_public, f"Revision preview/apply contract missing: {token}")
require("deleteSubject" not in room_public and "deleteTopic" not in room_public, "Removed edital content must archive, not delete")
require("coverage(" in revision and "canonicalSyllabusSha256" in revision, "Canonical coverage/version binding missing")

for token in (
    "currentEligibleItem",
    "nextAfterCompletion",
    "nextAfterSkip",
):
    require(token in cycle_planner, f"Persistent cycle planner contract missing: {token}")
for token in (
    "cycleBinding: StudyCycleSessionBinding? = null",
    "cycle_session_binding:",
    "advanceBoundStudyCycle",
    "StudyCyclePlanner.nextAfterCompletion",
):
    require(token in session_contract + session_room, f"Atomic session/cycle binding missing: {token}")
require(
    "session.status == StudySessionStatus.COMPLETED" in session_room
    and "StudySessionStatus.CANCELLED" in session_room,
    "Completion/cancellation paths must remain explicit",
)
require("metadataDao.delete(cycleSessionBindingKey(session.id))" in session_room, "Terminal sessions must clear cycle binding")

for table in ("exam_notice_revisions", "study_cycles", "study_cycle_items"):
    require(f'"{table}"' in duplicator, f"Plan graph duplication missing {table}")
    require(f'"{table}"' in portability, f"Portability missing {table}")
require('"exam_notice_revisions"' in privacy, "Revision history must participate in history redaction")

for token in ("ManagePublicExam", "RoomPublicExamRepository", "publicExamRepository"):
    require(token in app + data_layer, f"Application composition missing: {token}")
for token in ("exam_notice_revision_preview_title", "exam_notice_history_title", "ExamNoticeRevisionPlanner.coverage"):
    require(token in content_ui, f"CPOV03 content UX missing: {token}")
for token in ("study_cycle_title", "replaceStudyCycleFromSyllabus", "skipCurrentStudyCycleItem", "moveStudyCycleItemToEnd"):
    require(token in schedule_ui, f"CPOV04 schedule UX missing: {token}")
for token in ("study_cycle_next", "startCurrentStudyCycleItem", "nextStudyCycleAction"):
    require(token in today_ui, f"CPOV04 Today UX missing: {token}")
require(nav.count("publicExamManager = container.managePublicExam") >= 3, "Navigation must inject public-exam manager")

for version, expected_tables in {
    10: {"exam_notice_revisions"},
    11: {"exam_notice_revisions", "study_cycles", "study_cycle_items"},
}.items():
    path = ROOT / f"core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/{version}.json"
    require(path.is_file(), f"Exported Room schema {version}.json is missing")
    if path.is_file():
        payload = json.loads(path.read_text(encoding="utf-8"))
        require(payload["database"]["version"] == version, f"Schema {version} has wrong version")
        tables = {entity["tableName"] for entity in payload["database"]["entities"]}
        require(expected_tables <= tables, f"Schema {version} is missing public-exam tables")

migration_test = text(
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/StudyFlowMigration9To11PublicExamTest.kt"
)
require("runMigrationsAndValidate" in migration_test, "9->11 migration fixture missing validation")
require("Migration9To10" in migration_test and "Migration10To11" in migration_test, "Migration fixture must exercise both hops")

for test_rel, tokens in {
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyCyclePlannerTest.kt": (
        "wraps deterministically", "archived or disabled items", "empty eligibility"
    ),
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ExamNoticeRevisionPlannerTest.kt": (
        "additive removal rename", "impossible calendar dates"
    ),
}.items():
    source = text(test_rel)
    for token in tokens:
        require(token in source, f"Regression test missing: {token}")

for module in ("subjects", "schedule", "workspace"):
    en_path = ROOT / f"feature/{module}/src/main/res/values/strings.xml"
    pt_path = ROOT / f"feature/{module}/src/main/res/values-pt-rBR/strings.xml"
    en = {n.attrib["name"] for n in ET.parse(en_path).getroot() if n.tag == "string"}
    pt = {n.attrib["name"] for n in ET.parse(pt_path).getroot() if n.tag == "string"}
    require(en == pt, f"{module} EN/pt-BR string parity mismatch")

if failures:
    print("CPOV03_04_PUBLIC_EXAM_PROGRESS: FAILED")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print("CPOV03_04_PUBLIC_EXAM_PROGRESS: PASSED")
