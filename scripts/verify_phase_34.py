#!/usr/bin/env python3
from pathlib import Path
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


if not phase_version_at_least(ROOT, 34):
    errors.append("app version must be Phase 34 or later")

component = read("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowComponents.kt")
for marker in [
    "fun StudyFlowStatusPill(",
    "text: String",
    "leadingIcon: (@Composable () -> Unit)? = null",
    "Surface(",
    "RoundedCornerShape(percent = 50)",
]:
    require(marker in component, f"StudyFlowStatusPill missing marker: {marker}")

production_roots = [ROOT / "app/src/main", ROOT / "core", ROOT / "feature"]
for source_root in production_roots:
    if not source_root.exists():
        continue
    for path in source_root.rglob("*.kt"):
        rel = path.relative_to(ROOT).as_posix()
        if "/src/test/" in rel or "/src/androidTest/" in rel:
            continue
        text = path.read_text(encoding="utf-8")
        for forbidden in ["onClick = {}", "onClick = { }", "Button(onClick = {})", "TextButton(onClick = {})"]:
            require(forbidden not in text, f"production source still has empty click handler {forbidden}: {rel}")

status_pill_screens = [
    "feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt",
    "feature/reviews/src/main/java/com/mikeyphw/studyflow/feature/reviews/StudyReviewScreen.kt",
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/RuntimeSmokeWorkbenchScreen.kt",
    "feature/progress/src/main/java/com/mikeyphw/studyflow/feature/progress/StudyProgressScreen.kt",
    "feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt",
    "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt",
    "feature/analytics/src/main/java/com/mikeyphw/studyflow/feature/analytics/StudyAnalyticsScreen.kt",
    "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
    "feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/PlanListScreen.kt",
    "feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt",
    "feature/practice/src/main/java/com/mikeyphw/studyflow/feature/practice/PracticeScreen.kt",
]
for rel in status_pill_screens:
    text = read(rel)
    require("StudyFlowStatusPill" in text, f"{rel} must use passive status pills")
    require("import androidx.compose.material3.AssistChip" not in text, f"{rel} must not import AssistChip for passive display")

search = read("feature/search/src/main/java/com/mikeyphw/studyflow/feature/search/StudySearchScreen.kt")
require("AssistChip(onClick = { runSearch(recent) }" in search,
        "Search may keep AssistChip only for real recent-search actions")

release = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
require("ReleaseReadinessPhase(34" in release and "Interaction honesty" in release,
        "Release readiness seal must include Phase 34")
factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require(any(f"CurrentPhase = {phase}" in factory for phase in range(34, 100)),
        "Release validation factory must cover Phase 34 or later")
validate_repo = read("scripts/validate_repo.sh")
require("python3 scripts/verify_phase_34.py" in validate_repo,
        "validate_repo must run Phase 34 verifier")

if errors:
    print("PHASE_34_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_34_CONTRACT: PASSED (interaction honesty and passive status UI)")
