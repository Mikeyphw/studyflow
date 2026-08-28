#!/usr/bin/env python3
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def text(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


model = text("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyPractice.kt")
priority = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPriorityPlanner.kt")
filters = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamQuestionFilters.kt")
imports = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/QuestionImportPipeline.kt")
migrations = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
database = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
portability = text(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt"
)
duplicator = text(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt"
)
practice_ui = text("feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt")
workspace_ui = text(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt"
)

require(
    "enum class QuestionAuthorship" in model
    and "examNoticeRevisionId" in model
    and "points: Double?" in model,
    "Question provenance model incomplete",
)
for token in (
    "LOW_MASTERY",
    "OVERDUE_REVIEW",
    "STARVATION_RELIEF",
    "lockedByActiveSession",
    "studyPlanId == planId",
):
    require(token in priority, f"priority contract missing {token}")
for token in (
    "examBoard",
    "organization",
    "role",
    "subjectId",
    "topicId",
    "yearFrom",
    "authorship",
    "difficulty",
    "favorite",
    "disputed",
    "QuestionAttemptOutcome",
):
    require(token in filters, f"question filter contract missing {token}")
for token in (
    "organization",
    "examStageId",
    "examNoticeRevisionId",
    "externalReference",
    "points",
    "QuestionAuthorship",
):
    require(token in imports, f"import metadata missing {token}")

require(
    "Migration11To12" in migrations
    and "ALTER TABLE questions ADD COLUMN authorship" in migrations,
    "Room 11->12 migration missing",
)
schema_match = __import__("re").search(r"SCHEMA_VERSION\s*=\s*(\d+)", database)
require(
    schema_match is not None and int(schema_match.group(1)) >= 12,
    "Room schema version must remain at least 12",
)
require(
    '"questions" -> expected - setOf(' in portability
    and 'put("authorship", text("UNKNOWN"))' in portability,
    "legacy question portability normalization missing",
)
require(
    'requireReference("questions", row, "examStageId", "exam_stages")' in portability
    and 'requireReference("questions", row, "examNoticeRevisionId", "exam_notice_revisions")'
    in portability,
    "question provenance portability ownership missing",
)
require(
    '"examStageId", "examNoticeRevisionId"' in duplicator,
    "question provenance IDs are not remapped during duplication",
)

schema_path = ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/12.json"
require(schema_path.is_file(), "schema 12 missing")
if schema_path.is_file():
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    require(schema["database"]["version"] == 12, "schema 12 version mismatch")
    question_entity = next(
        (entity for entity in schema["database"]["entities"] if entity["tableName"] == "questions"),
        None,
    )
    require(question_entity is not None, "questions schema missing")
    if question_entity is not None:
        columns = {field["columnName"] for field in question_entity["fields"]}
        require(
            {
                "organization",
                "role",
                "examStageId",
                "authorship",
                "examNoticeRevisionId",
                "externalReference",
                "points",
            }
            <= columns,
            "schema 12 provenance columns missing",
        )
        indexes = {index["name"] for index in question_entity.get("indices", [])}
        require(
            "index_questions_studyPlanId_examBoard_year" in indexes
            and "index_questions_studyPlanId_subjectId_topicId" in indexes,
            "schema 12 question indexes missing",
        )

require(
    "PublicExamQuestionFilter(" in practice_ui and "public_exam_filters" in practice_ui,
    "Practice banca-aware filter UI missing",
)
require(
    "PublicExamPriorityPlanner.rank" in workspace_ui and "public_exam_priority_title" in workspace_ui,
    "Explainable priority preview UI missing",
)

for relative in (
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPriorityPlannerTest.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamQuestionFiltersTest.kt",
    "core/database/src/androidTest/java/com/mikeyphw/studyflow/core/database/"
    "StudyFlowMigration11To12QuestionProvenanceTest.kt",
):
    require((ROOT / relative).is_file(), f"missing test {relative}")

for prefix, en_relative, pt_relative in (
    (
        "practice",
        "feature/practice/src/main/res/values/strings.xml",
        "feature/practice/src/main/res/values-pt-rBR/strings.xml",
    ),
    (
        "workspace",
        "feature/workspace/src/main/res/values/strings.xml",
        "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
    ),
):
    en = text(en_relative)
    pt = text(pt_relative)
    for token in (
        ("public_exam_filters",) if prefix == "practice" else ("public_exam_priority_title",)
    ):
        require(f'name="{token}"' in en, f"{prefix} EN resource missing {token}")
        require(f'name="{token}"' in pt, f"{prefix} pt-BR resource missing {token}")

if FAILURES:
    print("CPOV05_06_PRIORITY_BANCA_QUESTIONS: FAILED")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("CPOV05_06_PRIORITY_BANCA_QUESTIONS: PASSED")
