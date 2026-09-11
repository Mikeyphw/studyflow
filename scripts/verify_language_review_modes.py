#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    full = ROOT / path
    if not full.exists():
        raise SystemExit(f"missing required file: {path}")
    return full.read_text()


def require(token: str, source: str, message: str) -> None:
    if token not in source:
        raise SystemExit(message)


model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageReviewPractice.kt")
ids = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/Identifiers.kt")
entities = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/LanguageLearningEntities.kt")
dao = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/LanguageLearningDao.kt")
database = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/LanguageReviewPracticeTest.kt")

for token in [
    "LanguageReviewPrompt",
    "LanguageReviewAttempt",
    "LanguageReviewOutcome",
    "buildLanguageReviewPrompt",
    "evaluateLanguageReviewAnswer",
    "toSpacedReviewDecision",
    "toLanguageReviewFlashcard",
    "ReviewMethod.SPACED_REPETITION",
    "LanguageReviewDirection.FILL_IN_THE_BLANK",
    "LanguageReviewDirection.CHOOSE_NATURAL_USAGE",
    "LanguageReviewDirection.SPOT_THE_MISTAKE",
    "LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE",
    "requiresManualReview = true",
]:
    require(token, model, f"language review mode model missing {token}")

require("LanguageReviewAttemptId", ids, "language review attempt identifier missing")
require("LanguageReviewAttemptEntity", entities, "language review attempt entity missing")
require("language_review_attempts", migrations, "language review attempts migration missing")
require("Migration8To9", migrations, "CMOV-08 database migration missing")
schema_match = __import__("re").search(r"SCHEMA_VERSION\s*=\s*(\d+)", database)
if schema_match is None or int(schema_match.group(1)) < 9:
    raise SystemExit("database schema version must be at least 9")
require("upsertLanguageReviewAttempt", dao, "language review attempt DAO upsert missing")
require("observeLanguageReviewAttempts", dao, "language review attempt DAO query missing")
require("sentence creation stays manual", test, "manual sentence review regression test missing")

print("LANGUAGE_REVIEW_MODES_VALIDATION_OK")
