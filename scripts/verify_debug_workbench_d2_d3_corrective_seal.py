#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, needle: str) -> None:
    if needle not in read(path):
        raise SystemExit(f"{path}: missing {needle!r}")


def forbid(path: str, needle: str) -> None:
    if needle in read(path):
        raise SystemExit(f"{path}: forbidden {needle!r}")


actions = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt"
app = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt"
registry = "app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistry.kt"
registry_test = "app/src/test/java/com/mikeyphw/studyflow/StudyFlowDebugProbeRegistryTest.kt"
actions_test = "app/src/test/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActionsTest.kt"
model = "core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugProbes.kt"
policy_test = (
    "core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/"
    "DebugProbePlatformPolicyTest.kt"
)
app_build = "app/build.gradle.kts"
manifest = "app/src/main/AndroidManifest.xml"

for needle in (
    "class StudyFlowDebugWorkbenchActions(",
    "suspend fun loadTimeline",
    "suspend fun copyTimeline",
    "suspend fun clearHistory",
    "suspend fun exportAndShare",
    "withContext(ioContext)",
    "catch (cancelled: CancellationException)",
    "BUNDLE_SHARE_FAILED",
):
    require(actions, needle)
require(app, "StudyFlowDebugWorkbenchActions.android(")
forbid(app, "copySanitizedTimeline()")
forbid(app, 'File(context.cacheDir, "support-bundles")')

for needle in (
    "fun interface StudyFlowDebugProbeOperations",
    "executionContext: CoroutineContext = Dispatchers.IO",
    "DiagnosticOutcome.STARTED",
    "withTimeout(descriptor.timeoutMillis)",
    "withContext(executionContext)",
    "runInterruptible",
    "catch (cancelled: CancellationException)",
    "throw cancelled",
    "catch (failure: Exception)",
    "hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)",
    "getReceiverInfo",
    "META_DATA_APPWIDGET_PROVIDER",
    "BuildConfig.BACKUP_LEGACY_RULE_LINKED",
    "BuildConfig.BACKUP_MODERN_RULE_LINKED",
):
    require(registry, needle)
forbid(registry, "catch (failure: Throwable)")
forbid(registry, "catch (_: Throwable)")

started = read(registry).index("DiagnosticOutcome.STARTED")
precondition = read(registry).index("descriptor.requiresDefaultPlan")
if started >= precondition:
    raise SystemExit("probe STARTED event must be recorded before default-plan preconditions")

for needle in (
    "data class WidgetHealthSnapshot",
    "providerRegistered",
    "providerEnabled",
    "providerMetadataPresent",
    "WIDGET_PROVIDER_MISSING",
    "WIDGET_PROVIDER_DISABLED",
    "data class BackupConfigurationSnapshot",
    "legacyRuleLinked",
    "modernRuleLinked",
    "BACKUP_RULES_INVALID",
):
    require(model, needle)

for needle in (
    'buildConfigField("boolean", "BACKUP_LEGACY_RULE_LINKED"',
    'buildConfigField("boolean", "BACKUP_MODERN_RULE_LINKED"',
    'manifestLinksXml("fullBackupContent", "backup_rules")',
    'manifestLinksXml("dataExtractionRules", "data_extraction_rules")',
):
    require(app_build, needle)
for needle in (
    'android:fullBackupContent="@xml/backup_rules"',
    'android:dataExtractionRules="@xml/data_extraction_rules"',
):
    require(manifest, needle)

for needle in (
    "blocked probe records start and terminal events without executing operation",
    "cooperative operation timeout returns explicit failure and terminal event",
    "interruptible blocking operation obeys probe timeout",
    "external cancellation is recorded and propagated",
    "recoverable exception becomes failed probe",
    "fatal errors are never converted into probe failures",
):
    require(registry_test, needle)
for needle in (
    "timeline copy and clear execute on injected io context",
    "bundle preparation runs on io and launcher stays on caller",
    "bundle preparation failure becomes unavailable result",
    "share launcher failure becomes explicit result",
    "unexpected recoverable share failure becomes explicit result",
    "bundle cancellation is never converted into ordinary failure",
):
    require(actions_test, needle)
for needle in (
    "widget policy verifies device provider metadata enabled state and instances",
    "backup policy fails when either manifest link or resource is missing",
):
    require(policy_test, needle)

base = ROOT / "feature/settings/src/main/res/values/strings.xml"
pt = ROOT / "feature/settings/src/main/res/values-pt-rBR/strings.xml"
for path in (base, pt):
    ET.parse(path)

def keys(path: Path) -> set[str]:
    return {node.attrib["name"] for node in ET.parse(path).getroot() if "name" in node.attrib}

if keys(base) != keys(pt):
    missing_pt = sorted(keys(base) - keys(pt))
    missing_base = sorted(keys(pt) - keys(base))
    raise SystemExit(f"resource parity mismatch: missing_pt={missing_pt}, missing_base={missing_base}")

print("DEBUG_WORKBENCH_D2_D3_CORRECTIVE_SEAL_VERIFY: PASSED")
