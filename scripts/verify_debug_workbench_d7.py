from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel):
    p = ROOT / rel
    if not p.exists():
        errors.append(f"Missing {rel}")
        return ""
    return p.read_text(encoding="utf-8")

def require(condition, message):
    if not condition:
        errors.append(message)

seal = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchSeal.kt")
actions = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActions.kt")
suite = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowRuntimeSelfCheckSuite.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
view_model = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchViewModel.kt")
ui = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
core_tests = read("core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchSealTest.kt")
action_tests = read("app/src/test/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchActionsTest.kt")
suite_tests = read("app/src/test/java/com/mikeyphw/studyflow/StudyFlowRuntimeSelfCheckSuiteTest.kt")

for token in [
    "data class RuntimeSelfCheckRun",
    "data class RuntimeSelfCheckHistory",
    "object SupportBundleSealPolicy",
    "SupportBundleSealMessage.ENTRY_TRAVERSAL",
    "SupportBundleSealMessage.ENTRY_NOT_ALLOWED",
    "DiagnosticRedactor::fingerprint",
]:
    require(token in seal, f"D7 seal model missing {token}")

for token in [
    "DebugProbeCatalog.safeSuiteIds",
    "safe probes only",
    "status in setOf(DebugProbeStatus.NOT_RUN, DebugProbeStatus.RUNNING)",
    "fun inspect(",
]:
    require(token in seal, f"D7 invariant missing {token}")

for entry in [
    "environment.json",
    "manifest.json",
    "redaction-report.txt",
    "checksums.sha256",
]:
    require(entry in seal, f"Support bundle allowlist missing {entry}")
require(r'Regex("events/(?:current|retained-[0-9]{3})\\.jsonl")' in seal,
        "Support bundle allowlist is missing bounded current/retained event entries")

for forbidden in ["studyflow.db", "shared_prefs", "datastore", "annex", "questions", "answers"]:
    require(forbidden not in seal.lower(), f"Support seal allowlist includes forbidden hint {forbidden}")

require("class StudyFlowRuntimeSelfCheckSuite" in suite, "Runtime self-check app suite missing")
require("probeIds.all(DebugProbeCatalog.safeSuiteIds::contains)" in suite, "Suite does not restrict to safe probes")
require("catch (cancelled: CancellationException)" in suite, "Suite does not preserve cancellation")
require("catch (fatal: Error)" in suite and "throw fatal" in suite, "Suite does not rethrow fatal errors")
require("DiagnosticOutcome.STARTED" in suite and "DiagnosticOutcome.SUCCEEDED" in suite, "Suite events incomplete")
require("RuntimeSelfCheckHistory" in view_model and "selfCheckHistory.record(run)" in view_model,
        "ViewModel does not retain self-check history")
require("StudyFlowRuntimeSelfCheckSuite(" in view_model, "ViewModel does not use D7 suite")
require("supportBundleSealReport" in app and "debugActions.supportBundleSealSnapshot()" in view_model,
        "Support-bundle seal state is not retained by the ViewModel and surfaced by the app")
require("BUNDLE_SEAL_FAILED" in ui, "UI result missing bundle seal failure")
require("SupportBundleSealStatus.label" in ui, "UI does not render seal status")
require("debug_self_check_history_title" in ui, "UI does not render self-check history")
require("PreparedSupportBundle" in actions, "Actions do not carry prepared bundle seal")
require("lastSealReport.set(prepared.sealReport)" in actions, "Actions do not retain last seal")
require("BUNDLE_SEAL_FAILED" in actions, "Actions do not block failed seal")
require("ZipFile(bundle)" in actions, "Actions do not inspect generated ZIP")

for phrase in [
    "rejects visible side-effect probes",
    "support bundle seal rejects traversal",
    "support bundle seal rejects database payloads",
]:
    require(phrase in core_tests, f"Core D7 test missing {phrase}")
for phrase in [
    "failed support bundle seal prevents share launch",
    "bundle cancellation is never",
]:
    require(phrase in action_tests, f"Action test missing {phrase}")
for phrase in [
    "safe suite runs read only catalog",
    "suite rejects side effect probes",
    "suite cancellation is recorded",
    "suite fatal errors are never",
]:
    require(phrase in suite_tests, f"Suite test missing {phrase}")

for rel in ["feature/settings/src/main/res/values/strings.xml", "feature/settings/src/main/res/values-pt-rBR/strings.xml"]:
    try:
        names = {child.attrib.get("name") for child in ET.parse(ROOT / rel).getroot() if child.tag == "string"}
        for name in [
            "debug_self_check_history_title",
            "debug_support_bundle_seal_title",
            "debug_action_bundle_seal_failed",
            "debug_support_bundle_message_traversal",
        ]:
            require(name in names, f"{rel} missing {name}")
    except Exception as exc:
        errors.append(f"XML parse failed for {rel}: {exc}")

require((ROOT / "DEBUG_WORKBENCH_D7_IMPLEMENTATION.md").exists(), "D7 implementation doc missing")
require((ROOT / "docs/quality/DEBUG_WORKBENCH_D7_RUNTIME_SELF_CHECK_SEAL.md").exists(), "D7 quality doc missing")

if errors:
    print("D7 verifier failed:")
    for error in errors:
        print("-", error)
    sys.exit(1)
print("D7 verifier passed")
