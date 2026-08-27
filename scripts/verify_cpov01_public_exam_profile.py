from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


dao = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyStructureDao.kt")
repo = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyContentRepository.kt")
domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyContent.kt")
contract = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyContentRepository.kt")
settings = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanSettingsScreen.kt")
profile = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PublicExamProfileSection.kt")
onboarding = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
entities = text("core/database/src/main/java/com/mikeyphw/studyflow/core/database/entity/StudyFlowEntities.kt")

require("Index(value = [\"studyPlanId\"], unique = true)" in entities, "StudyTarget must remain one-per-plan")
require("fun observeTarget(planId: String): Flow<StudyTargetEntity?>" in dao, "StudyTarget needs Room Flow observation")
require("findTargetById" in dao and "findExamStageById" in dao, "Ownership lookup DAO contracts missing")
require("fun observeStudyTarget(planId: StudyPlanId): Flow<StudyTarget?>" in contract, "Repository target Flow missing")
require("override fun observeStudyTarget" in repo, "Room repository target Flow missing")
require("Study target id already belongs to another Study Plan" in repo, "Target ownership guard missing")
require("Exam stage id already belongs to another Study Plan target" in repo, "Stage ownership guard missing")
require("fun observeStudyTarget" in domain and "saveStudyTarget" in domain and "saveExamStage" in domain, "Domain APIs missing")
require("shouldShowPublicExamProfile(plan.type)" in settings, "Plan Settings must gate contest UX by StudyType")
require("type == StudyType.PUBLIC_EXAMINATION" in profile, "Contest profile visibility policy missing")
for token in (
    "organization", "role", "examBoard", "noticeUrl", "confirmedExamDateIso", "expectedExamDateIso",
    "openings", "estimatedCutoffPercent", "ExamStageType", "minimumScorePercent",
):
    require(token in profile, f"Profile shell missing field contract: {token}")
require("No public-exam target" not in profile, "User-facing English should be resource backed")
require("onboarding_public_exam_profile_entry" in onboarding, "Onboarding public-exam entry hint missing")
require("completed.template == OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK" in app, "Onboarding routing guard missing")
require("navController.navigate(PlanSettingsRoute(completed.planId.value))" in app, "Public-exam onboarding must open Plan Settings")
require("collectAsStateWithLifecycle(initialValue = null)" in app, "Target must restore from repository Flow")
require("observeSyllabus(planId)" in app, "Exam stages must restore from repository Flow")
require("PublicExam" not in entities, "CPOV01 must not introduce a duplicate PublicExam database entity")

prefixes = ("public_exam_", "onboarding_public_exam_profile_entry")
resource_names: dict[str, set[str]] = {}
for locale, rel in {
    "en": "feature/plans/src/main/res/values/strings.xml",
    "pt-BR": "feature/plans/src/main/res/values-pt-rBR/strings.xml",
}.items():
    root = ET.parse(ROOT / rel).getroot()
    resource_names[locale] = {
        node.attrib["name"] for node in root if node.tag == "string" and node.attrib.get("name", "").startswith(prefixes)
    }
require(resource_names["en"] == resource_names["pt-BR"], "CPOV01 EN/pt-BR resource parity mismatch")
require(len(resource_names["en"]) >= 30, "CPOV01 resource surface is unexpectedly incomplete")

if failures:
    print("CPOV01_PUBLIC_EXAM_PROFILE: FAILED")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)

print(
    "CPOV01_PUBLIC_EXAM_PROFILE: PASSED "
    f"({len(resource_names['en'])} bilingual strings, repository-backed target + stages, ownership guards green)"
)
