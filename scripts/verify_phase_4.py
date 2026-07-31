#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

required = [
    "core/portability/build.gradle.kts",
    "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageModels.kt",
    "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt",
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt",
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPlanPortabilityRepository.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt",
    "docs/architecture/ADR-0008-study-plan-portability.md",
    "docs/quality/PHASE_4_ACCEPTANCE.md",
    "PHASE_4_IMPLEMENTATION.md",
]
for relative in required:
    if not (ROOT / relative).is_file():
        failures.append(f"missing Phase 4 file: {relative}")

models = (ROOT / required[1]).read_text()
for marker in (
    "StudyPlanPackageSchemaVersion = 1",
    "StudyPlanExportMode",
    "PRIVATE_STRIPPED",
    "STRUCTURE_ONLY",
    "TEMPLATE",
    "StudyPlanImportStrategy",
    "CREATE_AS_NEW",
    "REPLACE_EXISTING",
    "MERGE_COMPATIBLE",
    "includesAnnexPayloads: Boolean = false",
):
    if marker not in models:
        failures.append(f"portable package model missing: {marker}")

codec = (ROOT / required[2]).read_text()
for marker in (
    "manifest.json", "payload.json", "checksums.json", "SHA-256",
    "validateEntryName", "readBounded", "Checksum verification failed",
    "AllowedEntries", "MaxTotalBytes",
):
    if marker not in codec:
        failures.append(f"archive codec missing safety contract: {marker}")

store = (ROOT / required[3]).read_text()
for marker in (
    "roomDatabase.withTransaction",
    "sanitizeAnnex",
    "remapRoot",
    "remapChild",
    "expectedTableNames",
    "clearPlanForReplacement",
    "updateRoot",
    "MergeSkippedTables",
    "plan_snapshots",
    "study_plan_settings",
):
    if marker not in store:
        failures.append(f"graph portability store missing: {marker}")

service = (ROOT / required[4]).read_text()
for marker in (
    "PRE_IMPORT",
    "writeSnapshotFile",
    "restoreSnapshot",
    "canonicalFile",
    "StudyPlanExportMode.FULL",
    "includeAnnexMetadata = true",
):
    if marker not in service:
        failures.append(f"Room portability repository missing: {marker}")

screen = (ROOT / required[6]).read_text()
for marker in (
    "ActivityResultContracts.CreateDocument",
    "ActivityResultContracts.OpenDocument",
    "StudyPlanExportMode.FULL",
    "StudyPlanExportMode.PRIVATE_STRIPPED",
    "StudyPlanExportMode.STRUCTURE_ONLY",
    "StudyPlanExportMode.TEMPLATE",
    "StudyPlanImportStrategy.CREATE_AS_NEW",
    "StudyPlanImportStrategy.REPLACE_EXISTING",
    "StudyPlanImportStrategy.MERGE_COMPATIBLE",
):
    if marker not in screen:
        failures.append(f"portability UI missing: {marker}")

settings = (ROOT / "settings.gradle.kts").read_text()
if '":core:portability"' not in settings:
    failures.append("settings.gradle.kts does not include :core:portability")

app = (ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt").read_text()
for marker in ("PlanPortabilityRoute", "PlanPortabilityScreen", "manageStudyPlanPortability"):
    if marker not in app:
        failures.append(f"app navigation missing Phase 4 marker: {marker}")

# Seal the exact warnings reported by the landed Phase 3 validation.
for path in ROOT.rglob("*.kt"):
    if any(part in {"build", ".gradle", ".idea"} for part in path.parts):
        continue
    source = path.read_text()
    relative = path.relative_to(ROOT).as_posix()
    for forbidden in (
        "Icons.Outlined.ArrowBack",
        "Icons.Outlined.ListAlt",
        "import androidx.compose.material.icons.outlined.ArrowBack",
        "import androidx.compose.material.icons.outlined.ListAlt",
    ):
        if forbidden in source:
            failures.append(f"{relative}: deprecated directional icon usage: {forbidden}")
if "mutableStateOf(0)" in (ROOT / "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/CreatePlanScreen.kt").read_text():
    failures.append("CreatePlanScreen still boxes integer wizard state")

components = (ROOT / "core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowComponents.kt").read_text()
empty_state = re.search(r"fun StudyFlowEmptyState\((.*?)\n\)", components, re.S)
if not empty_state or empty_state.group(1).find("modifier: Modifier") > empty_state.group(1).find("actionLabel"):
    failures.append("StudyFlowEmptyState modifier is not the first optional parameter")

for path in (
    ROOT / "feature/plans/src/main/res/values-pt-rBR/strings.xml",
    ROOT / "feature/dashboard/src/main/res/values-pt-rBR/strings.xml",
):
    root = ET.parse(path).getroot()
    for plurals in root.findall("plurals"):
        quantities = {item.attrib.get("quantity") for item in plurals.findall("item")}
        if "many" not in quantities:
            failures.append(f"{path.relative_to(ROOT)}:{plurals.attrib['name']} missing quantity many")

app_build = (ROOT / "app/build.gradle.kts").read_text()
version_match = re.search(r'versionName = "0\.(\d+)\.0-phase(\d+)"', app_build)
if not version_match or int(version_match.group(1)) < 4 or int(version_match.group(2)) < 4:
    failures.append("app version is older than the Phase 4 baseline")

# AGP 8+ disables BuildConfig generation unless the consuming module opts in.
# Any BuildConfig reference must therefore be paired with a module-local build feature.
application_path = ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt"
application_text = application_path.read_text()
if "BuildConfig." in application_text:
    if not re.search(r"buildFeatures\s*\{[^}]*buildConfig\s*=\s*true", app_build, re.S):
        failures.append(
            "app references BuildConfig without android.buildFeatures.buildConfig = true"
        )
    namespace_match = re.search(r'namespace\s*=\s*"([^"]+)"', app_build)
    package_match = re.search(r'^package\s+([\w.]+)', application_text, re.M)
    if not namespace_match or not package_match or namespace_match.group(1) != package_match.group(1):
        failures.append(
            "BuildConfig consumer package must match the app namespace or use an explicit import"
        )

root_build = (ROOT / "build.gradle.kts").read_text()
if '":core:portability:test"' not in root_build:
    failures.append("checkAll does not include the portability codec tests")

if "lowercase(Locale.ROOT)" not in screen:
    failures.append("portable export filenames are not locale-stable")

# Module-boundary seal: Room is an implementation detail of :core:data.
data_layer_path = ROOT / "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt"
if not data_layer_path.is_file():
    failures.append("missing StudyFlowDataLayer module boundary")
else:
    data_layer = data_layer_path.read_text()
    for marker in (
        "fun createStudyFlowDataLayer(",
        "val studyPlanRepository: StudyPlanRepository",
        "val studyPlanPortabilityRepository: StudyPlanPortabilityRepository",
        "StudyFlowDatabase.create(context)",
    ):
        if marker not in data_layer:
            failures.append(f"StudyFlowDataLayer missing boundary marker: {marker}")

application_source = (
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt"
).read_text()
if "com.mikeyphw.studyflow.core.database" in application_source or "StudyFlowDatabase" in application_source:
    failures.append("app module leaks the Room database implementation across the data boundary")
if "createStudyFlowDataLayer" not in application_source:
    failures.append("app module does not construct repositories through StudyFlowDataLayer")

data_build = (ROOT / "core/data/build.gradle.kts").read_text()
if 'implementation(project(":core:database"))' not in data_build:
    failures.append(":core:data must keep :core:database as an implementation dependency")
app_build_text = (ROOT / "app/build.gradle.kts").read_text()
if 'project(":core:database")' in app_build_text:
    failures.append(":app must not depend directly on :core:database")
for relative in (
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanRepository.kt",
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyPlanPortabilityRepository.kt",
):
    source = (ROOT / relative).read_text()
    if not re.search(r"internal class RoomStudyPlan(?:Portability)?Repository\(", source):
        failures.append(f"{relative}: Room implementation must be internal to :core:data")

if failures:
    print("PHASE_4_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)

print("PHASE_4_CONTRACT: PASSED (portable package + snapshots + warning seal)")
