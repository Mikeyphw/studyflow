#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSealTest.kt",
    "docs/architecture/ADR-0020-final-integration-seal.md",
    "docs/quality/PHASE_15_ACCEPTANCE.md",
    "docs/quality/PHASE_15_RELEASE_READINESS_REPORT.md",
    "PHASE_15_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 15 file: {relative}")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if not phase_version_at_least(ROOT, 15):
    failures.append("app version is not Phase 15 or later")
settings_source = (ROOT / "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt").read_text(
    encoding="utf-8",
)
for marker in ("ReleaseReadinessSeal", "releaseReport", "final_seal_title"):
    if marker in settings_source:
        failures.append(f"settings screen exposes internal Phase 15 seal marker after product truthfulness: {marker}")
seal_source = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt").read_text(
    encoding="utf-8",
)
for phase in range(1, 16):
    if f"ReleaseReadinessPhase({phase}," not in seal_source:
        failures.append(f"release seal missing phase {phase}")
if "UserExperiencePolishAuditor.evaluate()" not in seal_source:
    failures.append("release seal does not include Phase 14 polish auditor")
if "AppShellReadiness" not in seal_source:
    failures.append("release seal does not include app-shell readiness")
# Android library consumer rules must exist whenever a module declares them.
for build_file in list((ROOT / "feature").glob("*/build.gradle.kts")) + list((ROOT / "core").glob("*/build.gradle.kts")):
    text = build_file.read_text(encoding="utf-8")
    if "consumerProguardFiles" in text:
        rules = build_file.parent / "consumer-rules.pro"
        if not rules.is_file():
            failures.append(f"missing consumer-rules.pro for {build_file.parent.relative_to(ROOT)}")
# Resource parity across every values-pt-rBR directory with a matching default values file.
def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }
for pt_file in ROOT.glob("**/src/main/res/values-pt-rBR/strings.xml"):
    default_file = pt_file.parent.parent / "values" / "strings.xml"
    if default_file.is_file() and resource_keys(default_file) != resource_keys(pt_file):
        failures.append(f"translation resources are not in parity for {pt_file.parent.parent.parent.relative_to(ROOT)}")
# Architecture and Compose hygiene.
app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
if "core.database" in app_source or "core.database" in application_source:
    failures.append("app module leaks database implementation package")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if 'implementation(project(":core:database"))' in app_build:
    failures.append("app depends directly on :core:database")
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
# Formatting has one canonical owner. Phase 15 verifies that the gate remains
# registered and isolated to the explicit quality target instead of re-scanning
# the repository in Python and duplicating Gradle work on every preflight.
root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
devtool = (ROOT / ".devtool.toml").read_text(encoding="utf-8")
if 'tasks.register("verifyFormatting")' not in root_build:
    failures.append("root build is missing the canonical verifyFormatting task")
if '[targets.studyflow_quality]' not in devtool or 'lint = ["verifyFormatting"]' not in devtool:
    failures.append("studyflow_quality must own the canonical formatting gate")

# validate_repo must contain every phase verifier.
validate = (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8")
for phase in range(0, 16):
    if f"verify_phase_{phase}.py" not in validate:
        failures.append(f"validate_repo.sh does not run Phase {phase} contract")
if not phase_version_at_least(ROOT, 15):
    failures.append("app version is not Phase 15 or later")
if failures:
    print("PHASE_15_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_15_CONTRACT: PASSED (final integration seal and regression suite)")
