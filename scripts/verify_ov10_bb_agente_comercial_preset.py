#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"OV10 missing required file: {rel}")
    return path.read_text(encoding="utf-8")

def require(cond: bool, message: str) -> None:
    if not cond:
        raise SystemExit(f"OV10 contract failed: {message}")

preset = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BancoDoBrasilAgenteComercialPreset.kt")
wizard = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt")
request = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SamplePlanProvisioning.kt")
repo = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomSamplePlanProvisioningRepository.kt")
provisioner = text("app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioner.kt")
ui = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt")
for path in (
    ROOT / "feature/plans/src/main/res/values/strings.xml",
    ROOT / "feature/plans/src/main/res/values-pt-rBR/strings.xml",
):
    ET.parse(path)

en = text("feature/plans/src/main/res/values/strings.xml")
pt = text("feature/plans/src/main/res/values-pt-rBR/strings.xml")

require("BB_AGENTE_COMERCIAL_PRE_EDITAL" in wizard, "native BB onboarding template missing")
for subject in (
    "BB_PORTUGUESE", "BB_ENGLISH", "BB_MATHEMATICS", "BB_FINANCIAL_MARKET_CURRENT_AFFAIRS",
    "BB_FINANCIAL_MATHEMATICS", "BB_BANKING_KNOWLEDGE", "BB_INFORMATION_TECHNOLOGY",
    "BB_SALES_NEGOTIATION",
):
    require(subject in preset and subject in wizard, f"BB subject missing: {subject}")
weights = [15.0, 5.0, 7.5, 5.0, 7.5, 15.0, 22.5, 22.5]
for weight in weights:
    require(str(weight) in wizard, f"reference objective weight missing: {weight}")
require(abs(sum(weights) - 100.0) < 0.0001, "reference weights must total 100")
require("ObjectiveQuestionCount = 70" in preset, "70-question reference metadata missing")
require("ObjectiveMaximumPoints = 100.0" in preset, "100-point reference metadata missing")
require("EssayMinimumScorePercent = 70" in preset, "essay threshold metadata missing")
require('examDateIso = ""' in wizard, "BB preset must not invent an exam date")
require("topics = emptyList()" in wizard, "BB preset must not fabricate topic syllabus/incidence")
require("estimatedMinutes = 0" in wizard, "BB subject workload estimate must remain unknown")
require("PublicExamCyclePreference.FIXED_WEEKLY" in wizard, "BB reference schedule must use fixed weekly strategy")
require("listOf(1, 7, 30)" in wizard, "1/7/30 review policy missing")
require("weeklyAvailabilityHours = 19" in wizard, "19-hour weekly goal missing")
require("minimumScorePercent = 70" in wizard, "essay 70/100 stage threshold missing")
require("val examStages: List<ExamStage>" in request, "atomic provisioning request must carry exam stages")
require("upsertExamStages(request.examStages" in repo, "Room provisioning must persist exam stages")
require(repo.index("upsertExamStages") < repo.index("upsertSubjects"), "exam stages must be persisted before stage-linked subjects")
require("examStageId = subject.examStageKind?.let(stageIds::get)" in provisioner, "objective subjects must retain stage linkage")
require("examStageNames = localizedExamStageNames(resources)" in text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"), "localized stage names must reach provisioning")
require("onboarding_template_bb_title" in ui, "BB template UI mapping missing")
for key in (
    "onboarding_template_bb_title", "onboarding_template_bb_description", "onboarding_stage_objective",
    "onboarding_stage_essay", "onboarding_subject_bb_portuguese", "onboarding_subject_bb_sales_negotiation",
):
    require(f'name="{key}"' in en, f"English resource missing {key}")
    require(f'name="{key}"' in pt, f"pt-BR resource missing {key}")

print("OV10 BB Agente Comercial preset source contract: PASS")
