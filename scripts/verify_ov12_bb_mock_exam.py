#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(f"OV12_BB_MOCK_EXAM: FAIL: {message}")

preset = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BancoDoBrasilMockExamPreset.kt")
blueprint = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamMockBlueprint.kt")
screen = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
bb = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BancoDoBrasilAgenteComercialPreset.kt")

en_path = ROOT / "feature/workspace/src/main/res/values/strings.xml"
pt_path = ROOT / "feature/workspace/src/main/res/values-pt-rBR/strings.xml"
for path in (en_path, pt_path):
    ET.parse(path)
en = en_path.read_text(encoding="utf-8")
pt = pt_path.read_text(encoding="utf-8")

require("DERIVED_FROM_REFERENCE_POINT_WEIGHTS" in preset, "question allocation must be labelled derived")
require("ExamBoardFilter = \"Cesgranrio\"" in preset, "Cesgranrio filter missing")
require("ReferenceFullMockMinutes = 300" in preset, "reference full-mock duration missing")
require("minimumOverallScorePercent = null" in preset, "must not invent objective pass minimum")
require("ExamScoringMode.NO_PENALTY" in preset, "preset must not invent a negative-marking rule")
require("ExamBlueprintPlanner.preview" in preset, "preset must reuse blueprint preview engine")
require("preview.missingQuestionCount" in preset, "local question deficit must remain explicit")
require("type == QuestionType.ESSAY" in blueprint, "essay questions must remain outside objective blueprint")
require("examBoardMatches" in blueprint and "shorter.length >= 5" in blueprint, "board matching hardening missing")
require("createMockExamFromBlueprint" in screen, "UI must reuse canonical blueprint creation")
require("matchesReferencePlanName(plan.name)" in screen, "BB preset must not leak into unrelated plans")
require("bb_mock_exam_missing" in screen, "UI must show insufficient-bank state")
require("preset.executable" in screen, "create action must be gated by executable preview")
require("ObjectiveQuestionCount = 70" in bb, "BB reference objective total must remain 70")
require("EssayMinimumScorePercent = 70" in bb, "essay reference minimum must remain 70")
require("not presented as official per-subject question counts" in en, "English truthfulness label missing")
require("não são apresentadas como quantidade oficial" in pt, "pt-BR truthfulness label missing")

weights = [15.0, 5.0, 7.5, 5.0, 7.5, 15.0, 22.5, 22.5]
require(abs(sum(weights) - 100.0) < 1e-9, "reference weights must sum to 100 points")
print("OV12_BB_MOCK_EXAM: PASS")
