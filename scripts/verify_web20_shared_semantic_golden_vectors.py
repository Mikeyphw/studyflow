#!/usr/bin/env python3
from pathlib import Path
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

def need(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

fixture = ROOT / "protocol/semantic/v1/golden-vectors.json"
need(fixture.is_file(), "WEB20 fixture missing")
if fixture.is_file():
    data = json.loads(fixture.read_text())
    vectors = data.get("vectors", [])
    need(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    need(data.get("milestone") == "WEB20", "milestone must be WEB20")
    need(len(vectors) == 40, f"expected 40 vectors, got {len(vectors)}")
    expected = {
        "readiness_band", "schedule_generation", "schedule_rebalance", "question_score", "mock_score",
        "srs", "study_time", "goal_progress", "error_state", "portable_serialization", "sflp_conflict",
    }
    kinds = {vector.get("kind") for vector in vectors}
    need(kinds == expected, f"WEB20 kinds differ: {kinds ^ expected}")
    need(len({vector.get("id") for vector in vectors}) == len(vectors), "vector ids must be unique")
    monday = [v for v in vectors if v.get("id") == "schedule-generate-weekly"]
    need(bool(monday) and monday[0]["input"].get("dayOfWeek") == 1, "StudyFlow Monday must be encoded as weekday 1")

checks = {
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/Web20SharedSemanticGoldenVectorsTest.kt": [
        "ExamReadinessEngine.bandFor", "StudySchedulePlanner", "PracticeExecutionEngine",
        "SpacedRepetitionScheduler", "StudyGoalProgressEngine.evaluate", "GoalProgressCalculator",
        "WrongAnswerRemediator", "CanonicalPortableJson", "StudyFlowDeterministicWinnerConflictHandler",
        "assertEquals(40, vectors.size)",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWeb20SemanticAdapter.kt": [
        "ExamReadinessEngine", "StudySchedulePlanner", "PracticeExecutionEngine", "SpacedRepetitionScheduler",
        "StudyGoalProgressEngine", "GoalProgressCalculator", "WrongAnswerRemediator", "CanonicalPortableJson",
        "StudyFlowDeterministicWinnerConflictHandler",
    ],
    "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopWeb20SharedSemanticGoldenVectorsTest.kt": [
        "assertEquals(40, vectors.size)", "schedule_generation", "schedule_rebalance", "portable_serialization",
        "sflp_conflict",
    ],
    "web/src/semantic_golden.ts": [
        "readinessBandForScore", "stableStringify", "semanticPracticeScore", "scheduleSpacedRepetition", "semanticConflict",
    ],
    "web/src/review_semantics.ts": [
        "scheduleSpacedRepetition", "schedulePublicExamReview", "MIN_REVIEW_EASE_FACTOR",
    ],
    "web/src/state.ts": [
        "schedulePublicExamReview", "scheduleSpacedRepetition", "nextEaseFactor",
    ],
}
for relative, tokens in checks.items():
    path = ROOT / relative
    need(path.is_file(), f"{relative} missing")
    text = path.read_text() if path.is_file() else ""
    for token in tokens:
        need(token in text, f"{relative} missing {token}")

roadmap = (ROOT / "docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md").read_text()
need("PARITY04 must reuse these vectors" in roadmap, "roadmap must require PARITY04 to reuse WEB20 vectors")
remaining = json.loads((ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json").read_text())
tracks = remaining.get("remaining_tracks", [])
web20 = next((item for item in tracks if item.get("id") == "WEB20"), None)
tv = next((item for item in tracks if item.get("id") == "TVOV27-TVOV31"), None)
need(web20 is not None and web20.get("status") == "complete", "remaining-work WEB20 must be complete")
need(tv is not None and tv.get("status") == "complete", "remaining-work TVOV27-TVOV31 must be complete")

if not errors:
    process = subprocess.run(
        ["node", str(ROOT / "web/tests/web20_golden_runner.mjs")],
        cwd=ROOT / "web",
        text=True,
        capture_output=True,
    )
    if process.returncode != 0:
        errors.append("Web golden runner failed: " + (process.stderr or process.stdout).strip())
    elif "PASS (40)" not in process.stdout:
        errors.append("Web golden runner did not report 40 passing vectors")

if errors:
    print("WEB20_SHARED_SEMANTIC_GOLDEN_VECTORS: FAIL")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("WEB20_SHARED_SEMANTIC_GOLDEN_VECTORS: PASS (40 vectors)")
