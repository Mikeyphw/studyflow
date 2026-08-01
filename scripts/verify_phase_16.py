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
    'versionName = "0.16.0-phase16"',
    'versionName = "0.17.0-phase17"',
    'versionName = "0.18.0-phase18"',
    'versionName = "0.19.0-phase19"',
]
if not any(marker in app_build for marker in expected_versions):
    failures.append("app version is not Phase 16 or later")

roadmap = text("docs/roadmap/IMPLEMENTATION_ROADMAP.md")
required_roadmap_markers = [
    "Delivered 15-phase MVP baseline",
    "Phase 15: Final integration seal and regression suite.",
    "Phase 16: Roadmap and validation truth cleanup.",
    "Phase 17: Annex file storage and portability.",
]
for marker in required_roadmap_markers:
    if marker not in roadmap:
        failures.append(f"roadmap missing marker: {marker}")

post_mvp = text("docs/roadmap/POST_MVP_GAP_CLOSURE_ROADMAP.md")
for marker in ("Phase 17: Annex file storage and portability", "Phase 25: Gap closure seal"):
    if marker not in post_mvp:
        failures.append(f"post-MVP roadmap missing marker: {marker}")

validation_report = text("VALIDATION_REPORT.md")
stale_markers = [
    "sort_content",
    "clear_exam_stage",
    "difficulty_value",
    "minutes_value",
    "invalid_number",
    "required_name",
]
for marker in stale_markers:
    if marker in validation_report:
        failures.append(f"validation report still contains stale warning marker: {marker}")

if "python3 scripts/verify_phase_16.py" not in text("scripts/validate_repo.sh"):
    failures.append("validate_repo.sh does not run Phase 16 verifier")

if failures:
    print("PHASE_16_CONTRACT: FAILED")
    print("\n".join(f"- {failure}" for failure in failures))
    sys.exit(1)
print("PHASE_16_CONTRACT: PASSED (roadmap and validation truth cleanup)")
