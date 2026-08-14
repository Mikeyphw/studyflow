#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required file: {relative}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        FAILURES.append(f"NUL byte found in text source: {relative}")
        return ""
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError as exc:
        FAILURES.append(f"non-UTF-8 text source: {relative}: {exc}")
        return ""


def require(relative: str, *needles: str) -> str:
    text = read(relative)
    for needle in needles:
        if needle not in text:
            FAILURES.append(f"{relative}: missing {needle!r}")
    return text


settings = require("settings.gradle.kts", '":core:diagnostics"')
module_build = require(
    "core/diagnostics/build.gradle.kts",
    'id("studyflow.kotlin.library")',
    "testImplementation(libs.junit4)",
)
if re.search(r"\b(android|room|compose)\b", module_build, re.IGNORECASE):
    FAILURES.append(":core:diagnostics must remain a pure Kotlin module")

require("app/build.gradle.kts", 'implementation(project(":core:diagnostics"))')
require(
    ".devtool.toml",
    '":core:diagnostics:compileKotlin"',
    '":core:diagnostics:compileTestKotlin"',
    '":core:diagnostics:test"',
)
require(
    "build.gradle.kts",
    "python3 scripts/verify_debug_workbench_d1_r2.py",
    '":core:diagnostics:test"',
)

models = require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticModels.kt",
    "data class DiagnosticEvent",
    "interface DiagnosticRecorder",
    "interface DiagnosticStore",
    "object NoOpDiagnosticRecorder",
    "sanitizeExceptionSummary",
)
redactor = require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticRedactor.kt",
    "genericDetailKeys" if False else "standardDetailKeys",
    "fingerprintValue",
    "sanitizeExceptionSummary",
    "safeExceptionType",
    "safeBoolean",
    "safeInteger",
    "safeVersion",
    "safeLocale",
    "safeTimezone",
)
recorder = require(
    "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/RollingJsonlDiagnosticRecorder.kt",
    "ArrayBlockingQueue",
    "rotationSequence",
    "nextRotationFile",
    "flushCompleted",
    "timelineMayBeIncomplete",
    "writerFailureFingerprint",
    "createForTesting",
    "events/current.jsonl",
    "environment.json",
    "manifest.json",
    "redaction-report.txt",
    "checksums.sha256",
)

for forbidden in [
    "HttpURLConnection",
    "URLConnection",
    "OkHttp",
    "Socket(",
    "android.permission.INTERNET",
    "complete = true",
]:
    if forbidden in models + redactor + recorder:
        FAILURES.append(f"diagnostics implementation contains forbidden {forbidden!r}")

application = require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    "interface AppContainer : DiagnosticRecorderProvider",
    "override val diagnosticRecorder: DiagnosticStore",
    "application-container-create",
    "notification-channel-bootstrap",
    "data-layer-initialize",
    "startup-reminder-reconciliation",
    "startup-widget-refresh",
)
helper = require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDiagnostics.kt",
    "if (!BuildConfig.DEBUG) return NoOpDiagnosticRecorder",
    "RollingJsonlDiagnosticRecorder(",
    "DiagnosticSessionPolicy.debug(",
    "DiagnosticOutcome.CANCELLED",
    "throw cancelled",
    "throw failure",
)
if "recordSuspendStep" not in application or "recordStep" not in application:
    FAILURES.append("application startup paths are not wired through the real recorder helpers")
if "filesDir" not in helper or '"diagnostics"' not in helper:
    FAILURES.append("debug recorder is not rooted in app-private files storage")

redactor_tests = require(
    "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticRedactorTest.kt",
    "sensitiveKeysAreRedactedAcrossCaseAndCommonShapes",
    "privateContentUrisPathsEmailsAndIdentifiersDoNotLeak",
    "genericDetailKeysFingerprintFreeFormStudyContent",
    "narrowlyTypedMachineDetailsRemainUseful",
    "forgedExceptionSummariesCannotBypassSanitization",
    "eventJsonContainsOnlySanitizedValues",
)
recorder_tests = require(
    "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/"
    "RollingJsonlDiagnosticRecorderTest.kt",
    "queueOverflowIsCountedWithoutBlockingCallers",
    "rotationRetainsConfiguredHistoryDespiteSameMillisecondClock",
    "writerFailureCanStillExportAHealthManifest",
    "concurrentWritersProduceCompleteSanitizedJsonLines",
    "supportBundleUsesFixedAllowlistRedactionAndValidChecksums",
)
require(
    "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DiagnosticSessionPolicyTest.kt",
    "disabledPolicyNeverRecords",
    "debugPolicyRemainsEnabled",
    "timeLimitedPolicyStopsAtExpiry",
)

if redactor_tests.count("@Test") != 8:
    FAILURES.append(f"expected 8 redactor behavior tests, found {redactor_tests.count('@Test')}")
if recorder_tests.count("@Test") != 10:
    FAILURES.append(f"expected 10 recorder behavior tests, found {recorder_tests.count('@Test')}")

require(
    "docs/quality/DEBUG_WORKBENCH_D1_RECORDER_PRIVACY_FOUNDATION.md",
    "supersedes the original D1 overlay",
    "Generic detail values",
    "same millisecond",
    "flushCompleted=false",
    "18 behavior tests",
)
require(
    "DEBUG_WORKBENCH_D1_IMPLEMENTATION.md",
    "D1 r2 delivers",
    "supersedes the original unsealed D1 artifact",
    "D2 may add",
)
require(
    "docs/validation/DEBUG_WORKBENCH_D1_VALIDATION.md",
    "all 18 D1 r2 behavior tests",
    "writer-storage failure",
    "D2 must not begin",
    "does not claim Android Gradle configuration",
)

format_paths = list((ROOT / "core/diagnostics").rglob("*.kt")) + [
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApplication.kt",
    ROOT / "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDiagnostics.kt",
]
for path in format_paths:
    text = path.read_text(encoding="utf-8")
    if text and not text.endswith("\n"):
        FAILURES.append(f"{path.relative_to(ROOT)}: missing final newline")
    for line_number, line in enumerate(text.splitlines(), 1):
        if "\t" in line:
            FAILURES.append(f"{path.relative_to(ROOT)}:{line_number}: tab character")
        if line != line.rstrip():
            FAILURES.append(f"{path.relative_to(ROOT)}:{line_number}: trailing whitespace")
        if len(line) > 120:
            FAILURES.append(
                f"{path.relative_to(ROOT)}:{line_number}: {len(line)} characters exceeds 120",
            )

for path in (ROOT / "core/diagnostics").rglob("*"):
    relative = path.relative_to(ROOT)
    if any(part in {"build", ".gradle"} for part in relative.parts):
        continue
    if path.is_dir():
        continue
    if path.suffix in {".pyc", ".pyo"} or "__pycache__" in path.parts:
        FAILURES.append(f"forbidden generated diagnostics payload: {relative}")
    if b"\x00" in path.read_bytes():
        FAILURES.append(f"NUL byte in diagnostics payload: {relative}")

entry_names = set(re.findall(r'entries\["([^"]+)"\]', recorder))
expected_static_entries = {
    "environment.json",
    "manifest.json",
    "redaction-report.txt",
    "checksums.sha256",
}
if entry_names != expected_static_entries:
    FAILURES.append(f"support bundle static allowlist mismatch: {sorted(entry_names)}")
if 'val name = "events/current.jsonl"' not in recorder:
    FAILURES.append("support bundle no longer emits the current sanitized event segment")
if 'val name = "events/retained-${index.toString().padStart(3, \'0\')}.jsonl"' not in recorder:
    FAILURES.append("support bundle no longer emits bounded retained event segments")

if FAILURES:
    print("DEBUG_WORKBENCH_D1_R2: FAILED")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)

print("DEBUG_WORKBENCH_D1_R2: PASSED")
