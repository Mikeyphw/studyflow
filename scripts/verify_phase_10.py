#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
required = [
    "feature/library/build.gradle.kts",
    "feature/library/consumer-rules.pro",
    "feature/library/src/main/AndroidManifest.xml",
    "feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt",
    "feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/LibraryUiModels.kt",
    "feature/library/src/test/java/com/mikeyphw/studyflow/feature/library/LibraryUiModelsTest.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyLibraryRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyLibrary.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/LibraryProgressCalculator.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyLibraryRepository.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyLibraryEntityMapper.kt",
    "docs/architecture/ADR-0015-library-study-resources.md",
    "docs/quality/PHASE_10_ACCEPTANCE.md",
    "PHASE_10_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 10 file: {relative}")
settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
if '":feature:library"' not in settings:
    failures.append("settings.gradle.kts does not include :feature:library")
app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
expected_versions = [
    'versionName = "0.10.0-phase10"',
    'versionName = "0.11.0-phase11"',
    'versionName = "0.12.0-phase12"',
    'versionName = "0.13.0-phase13"',
    'versionName = "0.14.0-phase14"',
    'versionName = "0.15.0-phase15"',
    'versionName = "0.16.0-phase16"',
    'versionName = "0.17.0-phase17"',
    'versionName = "0.18.0-phase18"',
    'versionName = "0.19.0-phase19"',
    'versionName = "0.20.0-phase20"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 10 or later")
if 'implementation(project(":feature:library"))' not in app_build:
    failures.append(":app does not depend on :feature:library")
root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
if ':feature:library:testDebugUnitTest' not in root_build:
    failures.append("checkAll does not include feature:library tests")
app_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text(encoding="utf-8")
for marker in ("LibraryRoute", "StudyLibraryScreen", "manageStudyLibrary", "onOpenLibrary"):
    if marker not in app_source:
        failures.append(f"app navigation missing library marker: {marker}")
application_source = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt").read_text(encoding="utf-8")
for marker in ("StudyLibraryRepository", "ManageStudyLibrary", "studyLibraryRepository"):
    if marker not in application_source:
        failures.append(f"application container missing library marker: {marker}")
if "core.database" in application_source:
    failures.append("app module leaks database implementation after Phase 10")
data_layer = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt").read_text(encoding="utf-8")
for marker in ("val studyLibraryRepository: StudyLibraryRepository", "RoomStudyLibraryRepository"):
    if marker not in data_layer:
        failures.append(f"data layer does not expose library repository correctly: {marker}")
library_dao = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyLibraryDao.kt").read_text(encoding="utf-8")
for marker in ("upsertMaterial", "findMaterial", "deleteMaterial", "upsertVideo", "findVideo", "deleteVideo", "findAnnex"):
    if marker not in library_dao:
        failures.append(f"library DAO missing operation: {marker}")
mapper = (ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/mapper/StudyLibraryEntityMapper.kt").read_text(encoding="utf-8")
for marker in ("StudyMaterialEntity.asExternalModel", "StudyMaterial.asEntity", "VideoResourceEntity.asExternalModel", "Annex.asEntity", "Base64"):
    if marker not in mapper:
        failures.append(f"library mapper missing marker: {marker}")
repo = (ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyLibraryRepository.kt").read_text(encoding="utf-8")
for marker in ("database.withTransaction", "requireSubject", "requireTopic", "deleteMaterial"):
    if marker not in repo:
        failures.append(f"Room library repository missing: {marker}")
manager = (ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyLibrary.kt").read_text(encoding="utf-8")
for marker in ("createMaterial", "toggleFavorite", "updateMaterialProgress", "createVideo", "createLinkedAnnex"):
    if marker not in manager:
        failures.append(f"library manager missing operation: {marker}")

def resource_keys(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(node.tag, node.attrib["name"]) for node in root if "name" in node.attrib and node.attrib.get("translatable", "true") != "false"}
if resource_keys(ROOT / "feature/library/src/main/res/values/strings.xml") != resource_keys(ROOT / "feature/library/src/main/res/values-pt-rBR/strings.xml"):
    failures.append("feature:library translation resources are not in parity")
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
if "verify_phase_10.py" not in (ROOT / "scripts/validate_repo.sh").read_text(encoding="utf-8"):
    failures.append("validate_repo.sh does not run Phase 10 contract")
if failures:
    print("PHASE_10_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_10_CONTRACT: PASSED (library, resources, annex metadata, warning seals)")
