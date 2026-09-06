#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        raise SystemExit(f"OV02 missing required file: {path}")
    return p.read_text(encoding="utf-8")

model = read("core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudyReadiness.kt")
engine = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngine.kt")
coach = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCoachWeeklyReview.kt")
report = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamReport.kt")
adaptive = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AdaptiveReschedulePlanner.kt")
ui = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")
tests = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ExamReadinessEngineTest.kt")
en = read("feature/workspace/src/main/res/values/strings.xml")
pt = read("feature/workspace/src/main/res/values-pt-rBR/strings.xml")

required = {
    "nullable readiness score": "val scorePercent: Int?" in model,
    "nullable readiness band": "val band: ExamReadinessBand?" in model,
    "confidence model": "enum class ReadinessConfidence" in model,
    "nullable adaptive readiness": "val readinessScorePercent: Int?" in model,
    "no 45 practice default": "answered <= 0 -> null" in engine,
    "no 50 mock default": "if (submitted.isEmpty()) return null" in engine,
    "no 70 pre-edital default": "val timeFeasibility = daysUntilExam?.let" in engine,
    "minimum evidence gate": "val hasMinimumEvidence = hasMinimumEvidence(" in engine,
    "confidence calculation": "confidenceFor(" in engine,
    "schedule evidence gate": "observedScheduleEvidenceCount" in engine,
    "coach nullable mock": "readiness?.mockExamHealthPercent?.takeIf" in coach,
    "report nullable signal": "val scorePercent: Int?" in report,
    "adaptive unknown summary": "Readiness needs more evidence" in adaptive,
    "UI insufficient evidence": "R.string.readiness_insufficient_evidence_message" in ui,
    "UI no-data rendering": "R.string.no_data" in ui,
    "UI confidence": "readinessConfidenceLabel" in ui,
    "English no-data": '<string name="no_data">No data</string>' in en,
    "Portuguese no-data": '<string name="no_data">Sem dados</string>' in pt,
    "fresh import regression": "freshImportedPlanDoesNotInventReadinessFromMissingEvidence" in tests,
    "small sample regression": "eightQuestionsRemainInsufficientEvidence" in tests,
    "confidence regression": "broadEvidenceCanReachHighConfidence" in tests,
}
failed = [name for name, ok in required.items() if not ok]
for forbidden, why in [
    ("answered <= 0 -> 45", "practice must not fabricate 45%"),
    ("if (submitted.isEmpty()) return 50", "mock health must not fabricate 50%"),
    ("if (daysUntilExam == null) return 70", "time feasibility must not fabricate 70%"),
]:
    if forbidden in engine:
        failed.append(why)

if failed:
    print("OV02_EVIDENCE_READINESS: FAIL")
    for item in failed:
        print(f"- {item}")
    sys.exit(1)

print("OV02_EVIDENCE_READINESS: PASS")
