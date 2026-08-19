#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8") if candidate.is_file() else ""


def resource_names(path: str) -> dict[str, str]:
    source = text(path)
    root = ET.fromstring(source)
    return {
        child.attrib["name"]: child.tag
        for child in root
        if child.tag in {"string", "plurals"} and "name" in child.attrib
    }


domain = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt")
test = text("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizardTest.kt")
screen = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt")
plan_list = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt")
plan_settings = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanSettingsScreen.kt")
app = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
routes = text("core/navigation/src/main/kotlin/com/mikeyphw/studyflow/core/navigation/StudyFlowRoutes.kt")
build = text("build.gradle.kts")
versions = text("gradle/libs.versions.toml")

for token in (
    "enum class OnboardingSamplePlanTemplate",
    "PUBLIC_EXAM_12_WEEK",
    "UNIVERSITY_SEMESTER",
    "CERTIFICATION_BOOTCAMP",
    "FREE_STUDY_HABIT",
    "OnboardingSamplePlanBlueprint",
    "localOnly: Boolean",
    "previewFirst: Boolean",
    "userInitiatedOnly: Boolean",
    "require(input.localOnly)",
    "require(input.previewFirst)",
    "require(input.userInitiatedOnly)",
    "fun choices(): List<OnboardingSamplePlanBlueprint>",
    "fun build(input: OnboardingSamplePlanInput)",
    "fun buildSettings(",
    "fun buildScheduleRules(",
):
    require(token in domain, f"Phase 62 domain contract missing: {token}")

for forbidden in (
    "Retrofit",
    "OkHttp",
    "http://",
    "https://",
    "openConnection",
    "URLConnection",
    "StudyCloudSync",
    "repository.",
    "suspend fun",
):
    require(forbidden not in domain, f"Phase 62 domain must stay local and pure; found {forbidden}")

for token in (
    "data object OnboardingRoute",
    "@Serializable",
):
    require(token in routes, f"Onboarding route missing: {token}")

for token in (
    "OnboardingSamplePlanWizardScreen",
    "StudyOnboardingSamplePlanWizard.choices",
    "onboarding_local_only_note",
    "OnboardingSamplePlanTemplate.titleRes",
    "OnboardingSampleSubjectKind.titleRes",
    "OnboardingSampleTopicKind.titleRes",
    "OnboardingWorkloadPreview",
    "OnboardingSchedulePreview",
    "dayOfWeekRes",
    "formatMinuteOfDay",
    "choice.scheduleRules.forEach",
    "onCreateSample(selectedChoice.template)",
):
    require(token in screen, f"Onboarding screen integration missing: {token}")

for token in (
    "onStartSamplePlan",
    "onboarding_start_sample",
    "Icons.Outlined.School",
):
    require(token in plan_list, f"Plan list onboarding entry missing: {token}")

for token in (
    "composable<OnboardingRoute>",
    "OnboardingSamplePlanWizardScreen",
    "StudyOnboardingSamplePlanWizard.build",
    "container.samplePlanProvisioner.state.collectAsStateWithLifecycle()",
    "container.samplePlanProvisioner.startProvisioning(",
    "reminderDeliveryCoordinator.reconcilePlan(completed.planId)",
    "container.samplePlanProvisioner.consumeCompletion(completed.planId)",
):
    require(token in app, f"App-level onboarding provisioning path missing: {token}")

provisioner = text("app/src/main/java/com/mikeyphw/studyflow/SamplePlanProvisioner.kt")
for token in (
    "journal.begin(",
    "StudyOnboardingSamplePlanWizard.buildScheduleRules(",
    "repository.provision(request)",
    "journal.updateCommittedPlan(",
    "preferencesRepository.setDefaultStudyPlanIfAbsent(",
    "suspend fun recoverPendingCompletion()",
):
    require(token in provisioner, f"SamplePlanProvisioner missing durable/atomic behavior: {token}")

onboarding_route_body = app.split("composable<OnboardingRoute>", 1)[-1].split(
    "composable<EditPlanRoute>",
    1,
)[0]
require(
    "LaunchedEffect(Unit)" not in onboarding_route_body,
    "Onboarding route must not auto-create samples on launch",
)

for token in (
    "@Test",
    "choices expose every template",
    "rejects non-local automatic or destructive setup",
    "settings and schedule rules are derived",
):
    require(token in test, f"Onboarding domain tests missing: {token}")
require(test.count("@Test") >= 4, "Onboarding wizard test must contain at least four behavioral tests")

plans_en = resource_names("feature/plans/src/main/res/values/strings.xml")
plans_pt = resource_names("feature/plans/src/main/res/values-pt-rBR/strings.xml")
require(plans_en == plans_pt, "feature/plans English and pt-BR resource names/types must match")

required_strings = (
    "onboarding_start_sample",
    "onboarding_title",
    "onboarding_heading",
    "onboarding_message",
    "onboarding_create_sample",
    "onboarding_preview_title",
    "onboarding_local_only_note",
    "onboarding_template_public_exam_title",
    "onboarding_template_university_title",
    "onboarding_template_certification_title",
    "onboarding_template_free_study_title",
    "onboarding_subject_core_theory",
    "onboarding_topic_question_bank",
    "onboarding_topic_reflection_log",
    "onboarding_workload_title",
    "onboarding_workload_subjects_topics",
    "onboarding_workload_minutes_week",
    "onboarding_schedule_title",
    "onboarding_schedule_block",
    "onboarding_schedule_flexible",
    "onboarding_day_monday",
    "onboarding_day_sunday",
)
for name in required_strings:
    require(plans_en.get(name) == "string", f"missing plan string resource: {name}")
    require(f"R.string.{name}" in screen or f"R.string.{name}" in plan_list,
            f"plan resource is not consumed by UI: {name}")

for name in (
    "onboarding_subjects",
    "onboarding_topics",
    "onboarding_schedule_blocks",
):
    require(plans_en.get(name) == "plurals", f"missing plan plural resource: {name}")
    require(f"R.plurals.{name}" in screen, f"plan plural is not consumed by UI: {name}")

require("choice.weeklyScheduleMinutes" in screen, "preview must show concrete weekly workload minutes")
require("choice.preferredSessionMinutes" in screen, "preview must show preferred session workload")
require("rule.durationMinutes" in screen, "preview must show each schedule block duration")
require("rule.startMinuteOfDay" in screen, "preview must show each schedule block start time")
require("rule.subjectKind" in screen, "preview must show each schedule block subject/flexible kind")
require("R.string.onboarding_schedule_block" in screen, "preview must render concrete schedule block rows")
require(
    "private fun formatMinuteOfDay" not in screen,
    "Onboarding screen must not redeclare formatMinuteOfDay and collide with PlanSettingsScreen helper",
)
require(
    "formatMinuteOfDay(rule.startMinuteOfDay)" in screen,
    "Onboarding schedule preview must reuse the existing non-redeclared plan time formatter",
)
require(
    "formatSampleMinuteOfDay" not in screen,
    "Onboarding screen should reuse the existing plan time formatter instead of adding another helper",
)
require(
    plan_settings.count("fun formatMinuteOfDay(") == 1,
    "Plan settings should keep the single shared formatMinuteOfDay helper",
)

for kind, name in re.findall(r"R\.(string|plurals)\.([A-Za-z0-9_]+)", screen + "\n" + plan_list):
    if name.startswith("onboarding_"):
        require(name in plans_en, f"plans resources missing {kind}.{name}")
        if name in plans_en:
            require(plans_en[name] == kind, f"plans resource kind mismatch for {name}: expected {kind}")

for token in (
    "verifyPhase62OnboardingSeal",
    "scripts/verify_phase_62.py",
    "verifyPhase60_61ContentReportSeal",
):
    require(token in build, f"Gradle verification wiring missing: {token}")

require("navigation = \"2.9.8\"" in versions, "Navigation version must remain 2.9.8")
require("2026.06.01" in versions, "Compose BOM must remain unchanged")
for onboarding_path in (
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
):
    require(
        "StudyExamRunbook" not in text(onboarding_path),
        f"Future runbook behavior must not leak into Phase 62 onboarding: {onboarding_path}",
    )

for source_path in (
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizard.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyOnboardingSamplePlanWizardTest.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/OnboardingSamplePlanWizardScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
):
    source = text(source_path)
    for line_number, line in enumerate(source.splitlines(), 1):
        require("\t" not in line, f"{source_path}:{line_number} contains a tab")
        require(line.rstrip() == line, f"{source_path}:{line_number} has trailing whitespace")
        require(len(line) <= 120, f"{source_path}:{line_number} has {len(line)} characters")

if FAILURES:
    print("Phase 62 onboarding verification failed:")
    for failure in FAILURES:
        print(f" - {failure}")
    sys.exit(1)

print("Phase 62 onboarding and sample plan wizard seal passed")
