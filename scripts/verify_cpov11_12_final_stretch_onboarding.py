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
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamFinalStretch.kt",
    "BEYOND_30_DAYS",
    "DAYS_30",
    "DAYS_15",
    "DAYS_7",
    "DAYS_2",
    "EXAM_EVE",
    "newContentPercent",
    "legislationPercent",
    "values.sum() == 100",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudySprintPlanner.kt",
    "finalStretchStrategy: PublicExamFinalStretchStrategy?",
    "PublicExamFinalStretchPlanner.plan(readiness.daysUntilExam)",
    "preview.horizonDays == HorizonDays",
    "Study sprint cannot mutate a locked activity.",
    "Study sprint cannot mutate an activity outside the approved seven-day candidate set.",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyExamRunbook.kt",
    "finalStretchStrategy: PublicExamFinalStretchStrategy?",
    "PublicExamFinalStretchPlanner.plan(daysUntilExam)",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt",
    "final-stretch-allocation",
    "final_stretch_allocation",
    "allocation.legislationPercent",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamOnboarding.kt",
    "data class PublicExamOnboardingInput(",
    "PublicExamCyclePreference",
    "PublicExamGoalPreset",
    "PublicExamOnboardingTemplates",
    "samples/import/public_exam_edital.txt",
    "samples/import/public_exam_questions.csv",
    "samples/import/public_exam_legislation.md",
    "fun buildTarget(",
    "fun buildStrategy(",
    "fun buildGoals(",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt",
    "val publicExam: PublicExamOnboardingBlueprint? = null",
    "PublicExamReviewPolicyCodec.encode",
    "publicExamPlan(input.publicExam)",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/SamplePlanProvisioning.kt",
    "val strategy: StudyStrategy? = null",
    "val target: StudyTarget? = null",
    "val goals: List<StudyGoal> = emptyList()",
)
require(
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomSamplePlanProvisioningRepository.kt",
    "request.target?.let",
    "request.strategy?.let",
    "upsertStudyGoals",
    "database.withTransaction",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioner.kt",
    "PublicExamOnboardingBuilder.buildStrategy",
    "PublicExamOnboardingBuilder.buildTarget",
    "PublicExamOnboardingBuilder.buildGoals",
    "examDateIso = blueprint.publicExam?.examDateIso",
)
require(
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt",
    "PublicExamOnboardingFields",
    "onboarding_exam_organization",
    "onboarding_cycle_preference",
    "onboarding_review_policy",
    "onboarding_goal_presets",
    "OnboardingSamplePlanInput(",
)

for rel in (
    "samples/import/public_exam_edital.txt",
    "samples/import/public_exam_questions.csv",
    "samples/import/public_exam_legislation.md",
):
    if not (ROOT / rel).is_file():
        errors.append(f"missing local import template: {rel}")

# CPOV11/CPOV12 are compatible with the current schema; require monotonic
# evolution rather than pinning validation to the historical schema number.
db = read("core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt")
match = re.search(r"SCHEMA_VERSION\s*=\s*(\d+)", db)
if not match or int(match.group(1)) < 13:
    errors.append("CPOV11/CPOV12 require Room schema at least 13")

for module, prefixes in (
    (
        "feature/plans",
        ("onboarding_exam_", "onboarding_cycle_", "onboarding_review_", "onboarding_goal_", "onboarding_import_"),
    ),
    ("feature/workspace", ("final_stretch_",)),
):
    en_path = ROOT / module / "src/main/res/values/strings.xml"
    pt_path = ROOT / module / "src/main/res/values-pt-rBR/strings.xml"
    try:
        en_root = ET.parse(en_path).getroot()
        pt_root = ET.parse(pt_path).getroot()
        en = {node.attrib["name"] for node in en_root if "name" in node.attrib}
        pt = {node.attrib["name"] for node in pt_root if "name" in node.attrib}
    except Exception as exc:
        errors.append(f"{module}: resource XML parse failed: {exc}")
        continue
    touched = {name for name in en | pt if name.startswith(prefixes)}
    if touched - en:
        errors.append(f"{module}: missing EN resources: {', '.join(sorted(touched - en))}")
    if touched - pt:
        errors.append(f"{module}: missing pt-BR resources: {', '.join(sorted(touched - pt))}")

if errors:
    print("CPOV11_12 verification FAILED", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("CPOV11_12 verification PASS")
