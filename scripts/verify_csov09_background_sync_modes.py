#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

checks = {
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySync.kt": [
        "enum class StudySyncBackgroundMode",
        "OFF",
        "MANUAL_ONLY",
        "UPLOAD_AFTER_CHANGES",
        "CHECK_DAILY",
        "FULL_AUTOMATIC",
        "StudySyncBackgroundConstraints",
        "batteryNotLow",
        "StudySyncBackgroundSyncPlanner",
        "effectiveBackgroundMode",
    ],
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyCloudSync.kt": [
        "setBackgroundSyncMode(mode: StudySyncBackgroundMode)",
    ],
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/LocalStudyCloudSyncRepository.kt": [
        "BackgroundSyncModeKey",
        "BatteryNotLowKey",
        "decodeBackgroundSyncMode",
        "StudySyncBackgroundMode.MANUAL_ONLY",
    ],
    "feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SyncFoundationScreen.kt": [
        "BackgroundSyncModeCard",
        "StudySyncBackgroundSyncPlanner.plan",
        "sync_background_mode_upload_after_changes",
        "sync_background_mode_check_daily",
        "sync_background_mode_full_automatic",
    ],
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudySyncBackgroundSyncPlannerTest.kt": [
        "fresh install has effective background sync off",
        "manual only is the default configured mode",
        "automatic modes require provider",
    ],
}

failures = []
for relative, markers in checks.items():
    text = (ROOT / relative).read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{relative}: missing {marker}")

if failures:
    raise SystemExit("CSOV09 background sync mode violations:\n" + "\n".join(failures))

print("CSOV09_BACKGROUND_SYNC_MODES: PASS")
