#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/UserExperiencePolish.kt",
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/UserExperiencePolishAuditorTest.kt",
    "docs/architecture/ADR-0019-polish-accessibility-ux-consistency.md",
    "docs/quality/PHASE_14_ACCEPTANCE.md",
    "PHASE_14_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 14 file: {relative}")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
expected_versions = [
    'versionName = "0.14.0-phase14"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 14")
settings_source = (ROOT / "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt").read_text(
    encoding="utf-8",
)
for marker in ("UserExperiencePolishAuditor", "polishReport", "ux_polish_title"):
    if marker not in settings_source:
        failures.append(f"settings screen missing Phase 14 UX marker: {marker}")
polish_source = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/UserExperiencePolish.kt").read_text(
    encoding="utf-8",
)
for marker in ("ACCESSIBILITY", "LOCALIZATION", "EMPTY_STATES", "ACTION_FEEDBACK", "NAVIGATION"):
    if marker not in polish_source:
        failures.append(f"polish auditor missing area: {marker}")

def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {
        (node.tag, node.attrib["name"])
        for node in root
        if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"
    }
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
if "verify_phase_14.py" not in (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8"):
    failures.append("validate_repo.sh does not run Phase 14 contract")
if failures:
    print("PHASE_14_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_14_CONTRACT: PASSED (polish, accessibility, UX consistency)")
