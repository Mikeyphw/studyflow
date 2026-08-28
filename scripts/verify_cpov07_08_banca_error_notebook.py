#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"missing file: {path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing contract token {token!r}")

require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamMockBlueprint.kt",
    "data class ExamBlueprint(",
    "data class ExamBlueprintSection(",
    "data class ExamScoringPolicy(",
    "RIGHT_MINUS_WRONG",
    "WEIGHTED_CUSTOM",
    "missingQuestionCount",
    "object ExamBlueprintCodec",
    "examBlueprintV1=",
    "object ExamBlueprintScorer",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyPractice.kt",
    "fun previewExamBlueprint(",
    "suspend fun createMockExamFromBlueprint(",
    "ExamBlueprintCodec.encodeConfiguration(preview)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PracticeExecutionEngine.kt",
    "ExamBlueprintCodec.decode(mock.configurationJson)",
    "ExamBlueprintScorer.score(state, frozen)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamReviewPolicy.kt",
    "listOf(1, 7, 30)",
    "object PublicExamReviewPolicyCodec",
    "initialDueAt",
    "successor",
    "PublicExamRemediationAction",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyReview.kt",
    "suspend fun reclassifyErrorEntry(",
    "reviewPolicyFor(planId).successor",
    "PublicExamReviewPolicyCodec.decode",
)
require(
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPracticeRepository.kt",
    "PublicExamReviewPolicyCodec.decode",
    "policy.initialDueAt(now, entry.recurrenceCount)",
    "findPendingReviewForError",
)
require(
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
    "preview_banca_blueprint",
    "banca_blueprint_insufficient_bank",
    "createMockExamFromBlueprint",
)
require(
    "feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt",
    "ReclassifyError",
    "ErrorCategory.entries",
    "PublicExamErrorRemediation.actionFor",
    "error_category_count",
)
require(
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanSettingsScreen.kt",
    "PublicExamReviewPolicyCodec.decode",
    "PublicExamReviewPolicyCodec.encode",
    "reviewPolicyValid",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamMockBlueprintTest.kt",
    "right minus wrong and blanks use frozen scoring policy",
    "preview reports exact section shortage before creation",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamReviewPolicyTest.kt",
    "default policy is one seven thirty days",
    "repeated errors increase initial review urgency",
)

# These two overlays intentionally do not bump Room; CPOV06's schema 12 must remain the baseline.
db = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
match = re.search(r"SCHEMA_VERSION\s*=\s*(\d+)", db)
if not match or int(match.group(1)) < 12:
    errors.append("StudyFlowDatabase must remain at least schema 12 after CPOV07/CPOV08")
migrations = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt")
if "Migration11To12" not in migrations:
    errors.append("CPOV06 migration 11->12 must remain registered")

# EN / pt-BR additions must stay in parity for all touched feature modules.
for module in ("feature/practice", "feature/plans", "feature/reviews"):
    en_path = ROOT / module / "src/main/res/values/strings.xml"
    pt_path = ROOT / module / "src/main/res/values-pt-rBR/strings.xml"
    try:
        en = {node.attrib["name"] for node in ET.parse(en_path).getroot() if "name" in node.attrib}
        pt = {node.attrib["name"] for node in ET.parse(pt_path).getroot() if "name" in node.attrib}
    except (ET.ParseError, FileNotFoundError) as exc:
        errors.append(f"{module}: resource XML parse failed: {exc}")
        continue
    prefixes = ("banca_", "preview_banca", "public_exam_review_", "error_category_", "remediation_", "classify_error_")
    cpov = {name for name in en | pt if name.startswith(prefixes)}
    missing_en = sorted(cpov - en)
    missing_pt = sorted(cpov - pt)
    if missing_en:
        errors.append(f"{module}: missing EN resources: {', '.join(missing_en)}")
    if missing_pt:
        errors.append(f"{module}: missing pt-BR resources: {', '.join(missing_pt)}")

if errors:
    print("CPOV07_08 verification FAILED", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("CPOV07_08 verification PASS")
