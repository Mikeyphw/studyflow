#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


if not phase_version_at_least(ROOT, 30):
    errors.append("app version must be Phase 30 or later")

settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
for forbidden in [
    "AppShellReadinessAuditor",
    "ReleaseReadinessSeal",
    "ReleaseCandidatePolishAuditor",
    "ReleaseCandidatePolishEvidence",
    "UserExperiencePolishAuditor",
    "app_shell_readiness_title",
    "release_candidate_polish_title",
    "final_seal_title",
    "ux_polish_title",
    "releaseReport",
    "releaseCandidateReport",
    "polishReport",
]:
    require(forbidden not in settings, f"normal Settings still exposes release scaffold: {forbidden}")
require("if (diagnosticsAvailable)" in settings, "Debug Workbench entry must remain diagnostics-gated")
for required in [
    "language_title",
    "appearance_title",
    "backup_title",
    "reminders_title",
    "privacy_title",
    "sync_title",
    "debug_workbench_title",
]:
    require(required in settings, f"Settings missing user-facing section: {required}")

retired_setting_resource_keys = [
    "foundation_contract_title",
    "foundation_contract_message",
    "app_shell_readiness_title",
    "app_shell_readiness_value",
    "app_shell_readiness_message",
    "release_candidate_polish_title",
    "release_candidate_polish_value",
    "release_candidate_polish_message",
    "accessibility_title",
    "accessibility_message",
    "ux_polish_title",
    "ux_polish_value",
    "ux_polish_message",
    "final_seal_title",
    "final_seal_value",
    "final_seal_message",
    "sync_change_token",
]
for rel in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    for key in retired_setting_resource_keys:
        require(f'name="{key}"' not in text, f"{rel} still carries retired Settings resource key: {key}")

changelog = read("CHANGELOG.md")
require(
    changelog.count("## Phase 30-31 product truthfulness and dashboard action completion") == 1,
    "CHANGELOG must contain exactly one Phase 30-31 entry",
)

shell = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AppShellReadiness.kt")
require("languageConfigured = true" in shell, "system-default language must count as a valid user choice")
require("appearanceConfigured = true" in shell, "system-default theme must count as a valid user choice")
require("backupEntryAvailable = input.hasDefaultPlan" in shell, "backup readiness must still depend on a default plan")

seal = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
for marker in [
    "ReleaseReadinessPhase(30",
    "ReleaseReadinessPhase(31",
    "ReleaseReadinessPhase(101",
    "ReleaseReadinessPhase(108",
    "Product truthfulness and user-facing settings cleanup",
    "Dashboard action completion and session routing",
]:
    require(marker in seal, f"release readiness scope missing marker: {marker}")

factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
match = re.search(r"private const val CurrentPhase = (\d+)", factory)
require(match is not None and int(match.group(1)) >= 31, "release validation factory must advance to Phase 31")
match = re.search(r"Phase 0 through (\d+) validators pass\.", factory)
require(match is not None and int(match.group(1)) >= 31, "release validation evidence must cover Phase 31")

bad_user_copy = [
    "MVP",
    "release polish",
    "release-candidate",
    "RC ready",
    "cloud-sync",
    "Debug Workbench D3",
    "arrive in Debug Workbench",
    "future phase",
    "later phases",
    "in this phase",
    "nesta fase",
    "fases de planejamento",
]
for rel in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
    "feature/dashboard/src/main/res/values/strings.xml",
    "feature/dashboard/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    for snippet in bad_user_copy:
        require(snippet not in text, f"{rel} still contains stale user-facing copy: {snippet}")
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"invalid XML in {rel}: {exc}")

def keys(rel: str) -> set[tuple[str, str]]:
    root = ET.fromstring(read(rel))
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }

require(
    keys("feature/settings/src/main/res/values/strings.xml") ==
    keys("feature/settings/src/main/res/values-pt-rBR/strings.xml"),
    "settings EN/PT-BR resource keys must remain in parity",
)
require(
    keys("feature/dashboard/src/main/res/values/strings.xml") ==
    keys("feature/dashboard/src/main/res/values-pt-rBR/strings.xml"),
    "dashboard EN/PT-BR resource keys must remain in parity",
)

settings_test = read("feature/settings/src/test/java/com/mikeyphw/studyflow/feature/settings/SettingsContractTest.kt")
require("normal settings hide release validation scaffolding" in settings_test, "settings contract test missing")
app_shell_test = read("core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/AppShellReadinessAuditorTest.kt")
require("systemLanguageAndThemeDefaultsAreValidUserChoices" in app_shell_test, "app shell default-choice test missing")

if errors:
    print("PHASE_30_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_30_CONTRACT: PASSED (product truthfulness and normal-settings cleanup)")
