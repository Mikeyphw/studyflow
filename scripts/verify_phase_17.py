#!/usr/bin/env python3
from pathlib import Path
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures = []

def text(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"missing {path}")
        return ""
    return target.read_text(encoding="utf-8")

app_build = text("app/build.gradle.kts")
if not phase_version_at_least(ROOT, 17):
    failures.append("app version is not Phase 17 or later")

models = text("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageModels.kt")
for marker in (
    "includeAnnexPayloads: Boolean = false",
    "data class PortableAnnexPayload",
    "data class StudyPlanPackageArchive",
):
    if marker not in models:
        failures.append(f"package models missing marker: {marker}")

codec = text("core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyPlanPackageCodec.kt")
for marker in (
    "fun writeArchive",
    "fun readArchive",
    "annex-payloads/",
    "Annex payloads require annex metadata.",
):
    if marker not in codec:
        failures.append(f"package codec missing marker: {marker}")

storage = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomAnnexFileStorageRepository.kt")
for marker in (
    "class RoomAnnexFileStorageRepository",
    "study-plans/${sanitizeSegment(planId)}/annexes",
    'MessageDigest.getInstance("SHA-256")',
    "cleanupOrphanedAnnexFiles",
):
    if marker not in storage:
        failures.append(f"annex storage repository missing marker: {marker}")

data_layer = text("core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/StudyFlowDataLayer.kt")
if "annexFileStorageRepository" not in data_layer:
    failures.append("data layer does not expose annex file storage repository")

app_container = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
if "ManageAnnexFileStorage" not in app_container:
    failures.append("app container does not expose ManageAnnexFileStorage")

library_screen = text("feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt")
for marker in ("ActivityResultContracts.OpenDocument", "annexFiles.importCopy", "deleteStoredAnnex"):
    if marker not in library_screen:
        failures.append(f"library screen missing marker: {marker}")

plans = text("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanPortabilityScreen.kt")
if "export(StudyPlanExportMode.FULL, true, true)" not in plans:
    failures.append("portability screen does not expose full export with annex payloads")

if "python3 scripts/verify_phase_17.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 17 verifier")

if failures:
    print("PHASE_17_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_17_CONTRACT: PASSED (annex file storage and portability)")
