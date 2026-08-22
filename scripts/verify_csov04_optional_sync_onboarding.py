#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures = []

screen = (ROOT / "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt").read_text()
strings = (ROOT / "feature/settings/src/main/res/values/strings.xml").read_text()
bundle = (ROOT / "core/portability/src/main/kotlin/com/mikeyphw/studyflow/core/portability/StudyFlowSyncBundle.kt").read_text()

for marker in [
    "OptionalSyncOnboardingCard",
    "onKeepOffline",
    "onChooseSyncFolder",
    "onLearnMore",
    "SyncMutation.KEEP_OFFLINE",
]:
    if marker not in screen:
        failures.append(f"SyncFoundationScreen.kt missing {marker}")

for marker in [
    "Optional sync onboarding",
    "Keep offline",
    "Choose sync folder",
    "Learn more",
    "StudyFlow works fully offline",
]:
    if marker not in strings:
        failures.append(f"settings strings missing {marker}")

duplicate_read = "val bytes = readBounded(zip, MaxEntryBytes)\n                    val bytes = readBounded"
if duplicate_read in bundle:
    failures.append("StudyFlowSyncBundle.kt still has duplicate readBounded val bytes declaration")

if failures:
    raise SystemExit("CSOV04 onboarding violations:\n" + "\n".join(failures))
print("CSOV04_OPTIONAL_SYNC_ONBOARDING: PASS")
