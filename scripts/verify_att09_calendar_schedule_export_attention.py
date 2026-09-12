from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCalendarAttention.kt": [
        "StudyFlowCalendarAttentionExporter", "BEGIN:VCALENDAR", "StudyFlowAttentionOccurrence", "PrivacyMode",
    ],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowCalendarAttentionTest.kt": [
        "exportCreatesIcsAndAttentionOccurrences", "privacyModesHideSensitiveCalendarCopy",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowCalendarAttentionBridge.kt": [
        "userInitiatedExportOnly", "buildExport",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopCalendarAttentionExportBridge.kt": [
        "suggestedFilename", "StudyFlowCalendarAttentionExporter",
    ],
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvCalendarAttentionBridge.kt": [
        "exportOnTvSupported = false", "phone or desktop",
    ],
    "web/src/calendar_attention.ts": [
        "buildStudyFlowCalendarExport", "serializeStudyFlowCalendarIcs", "downloadStudyFlowCalendarIcs", "ATT09",
    ],
    "web/src/react/App.tsx": [
        "CalendarExportTools", "Calendar export", "Export .ics", "Copy ICS",
    ],
    "web/service-worker.js": ["./runtime/calendar_attention.js"],
    "docs/product/CALENDAR_ATTENTION_EXPORT.md": ["Privacy modes", "Non-goals"],
    "ATT09_CALENDAR_SCHEDULE_EXPORT_ATTENTION.md": ["verifyAtt09CalendarScheduleExportAttention", "user-initiated"],
    "build.gradle.kts": ["verifyAtt09CalendarScheduleExportAttention", "verifyAtt09WebCalendarAttentionTests"],
    ".devtool.toml": ["verifyAtt09CalendarScheduleExportAttention"],
}

def require(path: str, *needles: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"Missing required file: {path}")
    text = p.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"{path} missing required text: {needle}")
    return text

for path, needles in REQUIRED.items():
    require(path, *needles)

runtime = require("web/runtime/calendar_attention.js", "buildStudyFlowCalendarExport", "serializeStudyFlowCalendarIcs")
dist_runtime = require("web/dist/runtime/calendar_attention.js", "buildStudyFlowCalendarExport", "serializeStudyFlowCalendarIcs")
if runtime != dist_runtime:
    raise AssertionError("ATT09 runtime/dist calendar_attention.js parity failed")

manifest = require("web/dist/release-manifest.json", "runtime/calendar_attention.js")
if "service-worker.js" not in manifest:
    raise AssertionError("release manifest does not include the rebuilt service worker")

result = subprocess.run(
    ["python3", "-m", "unittest", "tests/test_att09_calendar_attention.py"],
    cwd=ROOT / "web",
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
)
if result.returncode != 0:
    raise AssertionError(result.stdout + result.stderr)

print("ATT09 calendar schedule export attention verification passed")
