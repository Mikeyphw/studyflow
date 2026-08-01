#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "feature/analytics/build.gradle.kts",
    "feature/analytics/consumer-rules.pro",
    "feature/analytics/src/main/AndroidManifest.xml",
    "feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/StudyAnalyticsScreen.kt",
    "feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/AnalyticsUiModels.kt",
    "feature/analytics/src/test/java/com/mikeyphw/studyflow/feature/analytics/AnalyticsUiModelsTest.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyAnalytics.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyAnalytics.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyAnalyticsRepository.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyAnalyticsRepository.kt",
    "docs/architecture/ADR-0017-analytics-insights.md",
    "docs/quality/PHASE_12_ACCEPTANCE.md",
    "PHASE_12_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 12 file: {relative}")
settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:analytics"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:analytics")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
expected_versions = [
    'versionName = "0.12.0-phase12"',
    'versionName = "0.13.0-phase13"',
    'versionName = "0.14.0-phase14"',
    'versionName = "0.15.0-phase15"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 12 or later")
if 'implementation(project(":feature:analytics"))' not in app_build:
    failures.append(":app does not depend on :feature:analytics")
root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:analytics:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:analytics tests")
app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("AnalyticsRoute", "StudyAnalyticsScreen", "manageStudyAnalytics", "onOpenAnalytics"):
    if marker not in app_source:
        failures.append(f"app navigation missing analytics marker: {marker}")
application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyAnalyticsRepository", "ManageStudyAnalytics", "studyAnalyticsRepository"):
    if marker not in application_source:
        failures.append(f"application container missing analytics marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 12")
data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(encoding="utf-8")
for marker in ("val studyAnalyticsRepository: StudyAnalyticsRepository", "RoomStudyAnalyticsRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose analytics repository correctly: {marker}")
analytics_repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyAnalyticsRepository.kt").read_text(encoding="utf-8")
for marker in ("observeStudySessions", "observePlannedActivities", "observeReviewItems", "WeakStudyArea"):
    if marker not in analytics_repo:
        failures.append(f"analytics repository missing aggregate marker: {marker}")

def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(node.tag, node.attrib["name"]) for node in root if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"}
if resource_keys(ROOT / "feature/analytics/src/main/res/values/strings.xml") != resource_keys(
    ROOT / "feature/analytics/src/main/res/values-pt-rBR/strings.xml"
):
    failures.append("feature:analytics translation resources are not in parity")
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
if "verify_phase_12.py" not in (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8"):
    failures.append("validate_repo.sh does not run Phase 12 contract")
if failures:
    print("PHASE_12_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_12_CONTRACT: PASSED (analytics, insights, warning seals)")
