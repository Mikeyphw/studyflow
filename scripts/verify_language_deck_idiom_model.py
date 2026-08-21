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

ids = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/Identifiers.kt")
model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageLearning.kt")
entities = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/LanguageLearningEntities.kt")
dao = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/LanguageLearningDao.kt")
db = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
migrations = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
test = read("core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/LanguageLearningTest.kt")

def require(token: str, haystack: str, message: str) -> None:
    if token not in haystack:
        failures.append(message)

for token in ["LanguageDeckId", "LanguageExpressionId", "IdiomCardId", "VocabularyCardId"]:
    require(token, ids, f"Identifier missing {token}")

for token in [
    "LanguageDeckProfile",
    "LanguageExpression",
    "IdiomCard",
    "VocabularyCard",
    "UsageExample",
    "LanguageReviewDirection.EXPRESSION_TO_MEANING",
    "LanguageReviewDirection.MEANING_TO_EXPRESSION",
    "LanguageReviewDirection.FILL_IN_THE_BLANK",
    "LanguageReviewDirection.CHOOSE_NATURAL_USAGE",
    "LanguageReviewDirection.SPOT_THE_MISTAKE",
    "LanguageReviewDirection.CREATE_YOUR_OWN_SENTENCE",
    "literalTranslation",
    "translationEquivalent",
    "commonMistake",
    "pronunciationNote",
]:
    require(token, model, f"Language model missing {token}")

for token in [
    "LanguageDeckProfileEntity",
    "LanguageExpressionEntity",
    "IdiomCardEntity",
    "VocabularyCardEntity",
    "language_deck_profiles",
    "language_expressions",
    "idiom_cards",
    "vocabulary_cards",
]:
    require(token, entities + migrations, f"Database language contract missing {token}")

require("LanguageLearningDao", dao, "Language learning DAO missing")
require("abstract fun languageLearningDao", db, "Database must expose languageLearningDao")
require("const val SCHEMA_VERSION = 8", db, "Database schema version must advance to 8")
require("Migration7To8", migrations, "Database migration 7->8 missing")
require("idiom deck captures", test, "Idiom model test missing")
require("CREATE_YOUR_OWN_SENTENCE", test, "Sentence creation review test missing")

for path in [
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/LanguageLearning.kt",
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/LanguageLearningTest.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/LanguageLearningEntities.kt",
]:
    for idx, line in enumerate(read(path).splitlines(), 1):
        if len(line) > 120:
            failures.append(f"{path}:{idx} exceeds 120 characters")

if failures:
    raise SystemExit("CMOV-07 verification failed:\n" + "\n".join(failures))
print("CMOV_07_LANGUAGE_DECK_IDIOM_MODEL_OK")
