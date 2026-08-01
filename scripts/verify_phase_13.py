#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AppShellReadiness.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/AppShellReadinessAuditorTest.kt",
    "docs/architecture/ADR-0018-settings-backup-shell-seal.md",
    "docs/quality/PHASE_13_ACCEPTANCE.md",
    "PHASE_13_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 13 file: {relative}")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
expected_versions = [
    'versionName = "0.13.0-phase13"',
    'versionName = "0.14.0-phase14"',
    'versionName = "0.15.0-phase15"',
    'versionName = "0.16.0-phase16"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 13 or later")
settings_build = (ROOT / "feature/settings/build.gradle.kts").read_text(encoding="utf-8")
if 'implementation(project(":core:domain"))' not in settings_build:
    failures.append("feature:settings does not depend on :core:domain for shell readiness")
settings_source = (ROOT / "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt").read_text(
    encoding="utf-8",
)
for marker in ("AppShellReadinessAuditor", "hasDefaultPlan", "onOpenDefaultPlanBackup", "backupEntryAvailable"):
    if marker not in settings_source:
        failures.append(f"settings screen missing shell marker: {marker}")
app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("hasDefaultPlan = preferences.defaultStudyPlanId != null", "onOpenDefaultPlanBackup", "PlanPortabilityRoute"):
    if marker not in app_source:
        failures.append(f"app navigation missing settings backup marker: {marker}")
if "core.database" in app_source:
    failures.append("app module leaks database implementation after Phase 13")
readiness = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/AppShellReadiness.kt").read_text(
    encoding="utf-8",
)
for marker in ("readyPercent", "backupEntryAvailable", "accessibilityBaselineReady"):
    if marker not in readiness:
        failures.append(f"readiness auditor missing marker: {marker}")

def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(node.tag, node.attrib["name"]) for node in root if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"}
if resource_keys(ROOT / "feature/settings/src/main/res/values/strings.xml") != resource_keys(
    ROOT / "feature/settings/src/main/res/values-pt-rBR/strings.xml"
):
    failures.append("feature:settings translation resources are not in parity")
for path in ROOT.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text(encoding="utf-8")
    for scoped in ("align", "alignBy", "alignByBaseline", "matchParentSize", "weight"):
        if f"import androidx.compose.foundation.layout.{scoped}" in source:
            failures.append(f"{path.relative_to(ROOT)} imports scoped modifier {scoped}")
    if "@Composable" in source:
        for pattern in (".getString(R.", ".resources.getString(R.", ".getQuantityString(R."):
            if pattern in source:
                failures.append(f"{path.relative_to(ROOT)} uses resource lookup pattern {pattern}")
if "verify_phase_13.py" not in (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8"):
    failures.append("validate_repo.sh does not run Phase 13 contract")
if failures:
    print("PHASE_13_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_13_CONTRACT: PASSED (settings, backup polish, app shell seal)")
