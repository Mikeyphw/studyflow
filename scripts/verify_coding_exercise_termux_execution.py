#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"Missing {path}")
        return ""
    return target.read_text()

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt")
termux = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/TermuxProgrammingRuntime.kt")
entities = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/ProgrammingCourseEntities.kt")
dao = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/ProgrammingCourseDao.kt")
db = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
panel = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseWorkspacePanel.kt")
strings = read("feature/workspace/src/main/res/values/strings.xml")
pt = read("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecutionTest.kt")

def require(token: str, haystack: str, message: str) -> None:
    if token not in haystack:
        failures.append(message)

for token in [
    "TermuxExerciseExecutionPlan",
    "toTermuxExecutionPlan",
    "exerciseFileBundle",
    "applyTermuxResult",
    "CREATE_WORKSPACE",
    "WRITE_EXERCISE_FILES",
    "RUN_COMMAND",
    "READ_RESULT",
    "CodingExerciseStatus.RAN_SUCCESSFULLY",
    "CodingExerciseStatus.FAILED_TESTS",
]:
    require(token, model, f"Coding exercise execution model missing {token}")

require("isAllowedTermuxCommandTemplate", termux, "Termux execution must still use allow-listed command templates")
require("TermuxCommandRequestEntity", entities, "Termux command request entity missing")
require("TermuxCommandResultEntity", entities, "Termux command result entity missing")
require("termux_command_requests", migrations, "Migration must create termux_command_requests")
require("termux_command_results", migrations, "Migration must create termux_command_results")
require("Migration6To7", migrations, "Database migration 6->7 missing")
require("const val SCHEMA_VERSION =", db, "Database schema version must be visible")
require("upsertTermuxCommandRequest", dao, "DAO must persist Termux command requests")
require("upsertTermuxCommandResult", dao, "DAO must persist Termux command results")
require("programming_termux_execution_title", panel, "Programming panel must surface Termux execution")
require("programming_termux_execution_message", strings, "English Termux execution string missing")
require("programming_termux_execution_message", pt, "Portuguese Termux execution string missing")
require("unsafe command template is rejected", test, "Unsafe command regression test missing")
require("automatically", test, "Manual completion/result contract test missing")

for path in [
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecution.kt",
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/CodingExerciseTermuxExecutionTest.kt",
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgrammingCourseWorkspacePanel.kt",
]:
    for idx, line in enumerate(read(path).splitlines(), 1):
        if len(line) > 120:
            failures.append(f"{path}:{idx} exceeds 120 characters")

if failures:
    raise SystemExit("CMOV-06 verification failed:\n" + "\n".join(failures))
print("CMOV_06_CODING_EXERCISE_TERMUX_EXECUTION_OK")
