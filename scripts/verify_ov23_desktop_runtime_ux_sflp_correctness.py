#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required OV23 file: {rel}")
    return path.read_text(encoding="utf-8")

checks = []
def check(name: str, condition: bool, detail: str) -> None:
    if not condition:
        raise AssertionError(f"{name}: {detail}")
    checks.append(name)

policy = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkUxPolicy.kt")
link = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowLinkService.kt")
state = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt")
native = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopNativePlatform.kt")
screen = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt")
models = read("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopWorkflowUiModels.kt")
test = read("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopOv23RuntimeUxSflpCorrectnessTest.kt")
build = read("build.gradle.kts")
hook = read(".devtool-artifact/validation/validate.sh") if (ROOT / ".devtool-artifact/validation/validate.sh").exists() else ""
changelog = read("CHANGELOG.md")
docs = read("REMEDIATION_OV_23_IMPLEMENTATION.md")

for token in [
    "object DesktopStudyFlowLinkUxPolicy",
    "shareableEndpoints",
    "validateManualEndpoint",
    "isLoopbackOrWildcardHost",
    "Sync partially complete",
    "No shareable LAN endpoint",
]:
    check(f"OV23-policy-{token}", token in policy, f"policy missing {token}")

check("OV23-no-loopback-pairing-fallback", 'sflp+tcp://127.0.0.1:$port' not in link and "require(hints.isNotEmpty())" in link, "Desktop pairing still advertises loopback fallback")
check("OV23-pairing-hints-filtered", "DesktopStudyFlowLinkUxPolicy.shareableEndpoints(webSocketEndpoints + tcpEndpoints)" in link, "pairing hints are not filtered")
check("OV23-manual-endpoint-validated", "DesktopStudyFlowLinkUxPolicy.validateManualEndpoint(StudyFlowManualEndpointParser.parse(value))" in link, "manual endpoint validation missing")
check("OV23-qr-endpoints-validated", "validateManualEndpoint(StudyFlowManualEndpointParser.parse(hint))" in link, "QR endpoints are not normalized through policy")
check("OV23-snapshot-health", "healthMessage" in link and "capabilityMessage" in link and "reachable = runtime.bestEndpoint" in link, "SFLP Settings snapshot lacks health/reachability")
check("OV23-sync-summary", "DesktopStudyFlowLinkUxPolicy.syncSummary(service.syncAll(workspace))" in state, "sync action still claims success by result count")
check("OV23-readable-pairing-expiry", "DesktopStudyFlowLinkUxPolicy.formatPairingExpiry" in state, "pairing expiry still exposes raw epoch millis")
check("OV23-tray-handler-isolated", "StudyFlow action failed" in native and "runCatching { callback(action, notificationId) }" in native, "tray action handler exceptions are not isolated")
check("OV23-local-time-labels", "Instant.ofEpochMilli(epochMillis)" in models and "DateTimeFormatter.ofPattern(\"HH:mm\")" in models and "epochMillis / MinuteMillis" not in models, "Desktop reminder time labels still derive from raw epoch minutes")
check("OV23-settings-health-copy", "Health: ${linkState.healthMessage}" in screen and "No shareable endpoint is being advertised" in screen, "Settings UI does not show link health and endpoint truth")
check("OV23-restore-copy-fixed", "does not require restart" in screen, "Settings still claims restore requires restart")
check("OV23-unit-test", "desktop pairing hints never advertise loopback-only endpoints" in test and "sync summary stays retry-aware" in test, "OV23 unit test coverage missing")
check("OV23-gradle-task", "verifyOv23DesktopRuntimeUxSflpCorrectness" in build, "Gradle task missing")
check("OV23-validation-hook", "verifyOv23DesktopRuntimeUxSflpCorrectness" in hook and "--stop" not in hook and "gradle --stop" not in hook, "validation hook missing or stops daemons")
check("OV23-docs", "Overlay 23 of 30" in docs and "Desktop runtime, UX, tray & SFLP correctness" in docs, "implementation doc missing overlay title")
check("OV23-changelog", "OV23" in changelog and "Desktop runtime" in changelog, "changelog missing OV23 entry")

print(f"OV23 Desktop runtime/UX/tray/SFLP correctness source evidence: PASS ({len(checks)} checks)")
