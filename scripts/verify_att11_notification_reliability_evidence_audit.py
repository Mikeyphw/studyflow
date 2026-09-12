from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionReliabilityEvidence.kt": [
        "StudyFlowAttentionReliabilityAnalyzer", "StudyFlowAttentionReliabilityExport", "StudyFlowAttentionRouteAudit",
        "truthfulBackgroundDelivery", "auditedRouteCount", "averageDeliveryDelayMillis",
    ],
    "core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttention.kt": [
        "routeAudit", "recordRouteDecision", "StudyFlowAttentionRouteAudit.from(route)",
    ],
    "core/link/src/test/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttentionReliabilityEvidenceTest.kt": [
        "analyticsSeparateDeferredMissedSnoozedDismissedAndDoneEvidence", "routeAuditIsPersistedOnCanonicalLedgerEntryAndExported",
        "platformDiagnosticsStayTruthfulAboutWebPushAndTvForegroundOnlyDelivery",
    ],
    "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt": [
        "attentionReliabilityEvidence", "StudyFlowAttentionReliabilityAnalyzer", "recordRouteDecision(route)",
    ],
    "feature/reminders/src/main/java/com/mikeyphw/studyflow/feature/reminders/StudyRemindersScreen.kt": [
        "ATT11", "att11_delivery_audit_title", "onCopyAttentionAuditJson", "onCopyAttentionAuditCsv",
    ],
    "app/src/test/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionReliabilityEvidenceContractTest.kt": [
        "recordRouteDecision(route)", "StudyFlowAttentionReliabilityExport.toJson", "ATT11",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt": [
        "reliabilityEvidence", "StudyFlowAttentionReliabilityAnalyzer", "recordRouteDecision(route)",
    ],
    "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt": [
        "ATT11 delivery reliability and audit", "Copy audit JSON", "Copy audit CSV",
    ],
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionDeliveryRouterBridge.kt": [
        "reliabilityProjection", "StudyFlowAttentionReliabilityAnalyzer",
    ],
    "app-tv/src/main/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurface.kt": [
        "att11ReliabilityAuditReadOnly", "ATT11 delivery audit is read-only",
    ],
    "app-tv/src/test/java/com/mikeyphw/studyflow/tv/attention/StudyFlowTvAttentionReliabilityEvidenceTest.kt": [
        "FOREGROUND_ONLY", "truthfulBackgroundDelivery",
    ],
    "web/src/attention_reliability.ts": [
        "analyzeWebAttentionReliability", "routeAuditFromDeliveryRoute", "exportWebAttentionReliabilityJson",
        "actual Web Push", "truthfulBackgroundDelivery",
    ],
    "web/src/sflp_link.ts": ["routeAudit?:", "candidateCount", "suppressedDeviceIds"],
    "web/src/attention.ts": [
        "reliability: StudyFlowAttentionReliabilityEvidenceSnapshot", "recordRouteDecision(route)",
        "exportReliabilityJson", "pushSubscription ? [\"web_push\" as const] : []",
    ],
    "web/src/react/App.tsx": [
        "ATT11 delivery reliability and audit", "actual Web Push subscription", "exportReliabilityJson", "exportReliabilityCsv",
    ],
    "web/src/tv_attention.ts": ["reliabilityReadOnly: true", "att11-web-runtime", "studyflowTvAttentionReliability"],
    "web/service-worker.js": ["./runtime/attention_reliability.js"],
    "web/tests/test_att11_attention_reliability.py": ["ATT11 Web reliability evidence tests passed", "actual Web Push subscription"],
    "docs/product/NOTIFICATION_RELIABILITY_EVIDENCE_AUDIT.md": ["ATT01 ledger", "actual Web Push subscription", "JSON/CSV"],
    "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md": ["ATT11 reliability evidence + cross-device delivery audit", "ATT11 JSON/CSV audit export"],
    "ATT11_NOTIFICATION_RELIABILITY_EVIDENCE_AUDIT.md": ["verifyAtt11NotificationReliabilityEvidenceAudit", "canonical ledger"],
    "README.md": ["ATT11 — Notification Reliability Evidence / Delivery Audit", "actual Web Push subscription"],
    "CHANGELOG.md": ["ATT11 — Notification Reliability Evidence / Delivery Audit", "verifyAtt11NotificationReliabilityEvidenceAudit"],
    "build.gradle.kts": ["verifyAtt11NotificationReliabilityEvidenceAudit", "verifyAtt11WebReliabilityTests"],
    ".devtool.toml": ["verifyAtt11NotificationReliabilityEvidenceAudit"],
}

def require(path: str, *needles: str) -> str:
    candidate = ROOT / path
    if not candidate.exists():
        raise AssertionError(f"Missing required ATT11 file: {path}")
    text = candidate.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise AssertionError(f"{path} missing ATT11 contract text: {needle}")
    return text

for path, needles in REQUIRED.items():
    require(path, *needles)

# ATT11 must remain a projection of ATT01, not a schema-v2 or parallel-store migration.
attention = require("core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowAttention.kt")
if "private const val AttentionLedgerSchemaVersion = 1" not in attention:
    raise AssertionError("ATT11 must preserve the ATT01 ledger schema-version contract")
if "routeAudit: StudyFlowAttentionRouteAudit? = null" not in attention:
    raise AssertionError("ATT11 route audit must be optional for migration-safe legacy ledger entries")

# Web source, tracked runtime, packaged runtime, and release manifest must agree.
for relative in (
    "attention_reliability.js", "attention.js", "attention_router.js", "attention_focus_policy.js",
    "sflp_link.js", "tv_attention.js", "react/App.js",
):
    runtime = require(f"web/runtime/{relative}")
    dist_runtime = require(f"web/dist/runtime/{relative}")
    if runtime != dist_runtime:
        raise AssertionError(f"ATT11 runtime/dist parity failed for {relative}")

require("web/dist/service-worker.js", "./runtime/attention_reliability.js")
manifest = require("web/dist/release-manifest.json", "runtime/attention_reliability.js")
if "runtime/attention_reliability.js" not in manifest:
    raise AssertionError("ATT11 reliability runtime missing from Web release manifest")

result = subprocess.run(
    [
        "python3", "-m", "unittest",
        "tests/test_att11_attention_reliability.py",
        "tests/test_att10_focus_policy.py",
        "tests/test_att07_attention_router.py",
        "tests/test_att06_web_tv_attention.py",
        "tests/test_att05_web_attention_runtime.py",
        "tests/test_att01_attention_runtime.py",
    ],
    cwd=ROOT / "web",
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    check=False,
)
if result.returncode != 0:
    raise AssertionError(result.stdout)

print("ATT11 notification reliability evidence / delivery audit verification passed")
