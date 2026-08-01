#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
failures = []

def text(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        failures.append(f"missing {path}")
        return ""
    return target.read_text(encoding="utf-8")

app_build = text("app/build.gradle.kts")
expected_versions = [
    'versionName = "0.18.0-phase18"',
    'versionName = "0.19.0-phase19"',
    'versionName = "0.20.0-phase20"',
    'versionName = "0.21.0-phase21"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 18")

manifest = text("app/src/main/AndroidManifest.xml")
for marker in (
    "<queries>",
    "android.intent.action.VIEW",
    "androidx.core.content.FileProvider",
    "@xml/file_provider_paths",
):
    if marker not in manifest:
        failures.append(f"manifest missing viewer marker: {marker}")

paths = text("app/src/main/res/xml/file_provider_paths.xml")
if "study-plans/" not in paths or "<files-path" not in paths:
    failures.append("file provider paths do not expose study plan annex copies")

planner = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyResourceLaunchPlanner.kt")
for marker in (
    "class StudyResourceLaunchPlanner",
    "StudyResourceLaunchPlan.AppPrivateAnnex",
    "ManageStudyResourceViewer",
    "markVideoOpened",
):
    if marker not in planner:
        failures.append(f"resource launch planner missing marker: {marker}")

launcher = text("feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyResourceLauncher.kt")
for marker in (
    "FileProvider.getUriForFile",
    "Intent.ACTION_VIEW",
    "FLAG_GRANT_READ_URI_PERMISSION",
):
    if marker not in launcher:
        failures.append(f"resource launcher missing marker: {marker}")

library = text("feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt")
for marker in (
    "resourceViewer: ManageStudyResourceViewer",
    "open_resource",
    "open_video",
    "open_annex",
    "launchStudyResource",
):
    if marker not in library:
        failures.append(f"library screen missing Phase 18 marker: {marker}")

app_container = text("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt")
if "ManageStudyResourceViewer" not in app_container:
    failures.append("app container does not expose ManageStudyResourceViewer")

if "python3 scripts/verify_phase_18.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 18 verifier")

if failures:
    print("PHASE_18_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_18_CONTRACT: PASSED (material viewer and media playback)")
