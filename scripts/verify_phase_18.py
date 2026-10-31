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
if not phase_version_at_least(ROOT, 18):
    failures.append("app version is not Phase 18 or later")

manifest = text("app/src/main/AndroidManifest.xml")
for marker in (
    "androidx.core.content.FileProvider",
    "@xml/file_provider_paths",
):
    if marker not in manifest:
        failures.append(f"manifest missing viewer marker: {marker}")
# Termux visibility is a later, explicitly scoped integration; the viewer must
# still use implicit ACTION_VIEW, not an unrestricted or viewer-specific query.
import xml.etree.ElementTree as ET
try:
    manifest_root = ET.fromstring(manifest)
    query_roots = manifest_root.findall("queries")
    android = "{http://schemas.android.com/apk/res/android}name"
    allowed = (len(query_roots) == 1 and len(query_roots[0]) == 2
               and [child.tag for child in query_roots[0]] == ["package", "intent"]
               and query_roots[0][0].get(android) == "com.termux"
               and len(query_roots[0][1]) == 1
               and query_roots[0][1][0].tag == "action"
               and query_roots[0][1][0].get(android) == "com.termux.RUN_COMMAND")
    if not allowed:
        failures.append("viewer package visibility must be scoped only to Termux RUN_COMMAND")
except ET.ParseError:
    failures.append("invalid AndroidManifest XML")

paths = text("app/src/main/res/xml/file_provider_paths.xml")
if "shareable-annexes/" not in paths or "<files-path" not in paths:
    failures.append("file provider paths do not expose the narrow shareable annex root")

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
