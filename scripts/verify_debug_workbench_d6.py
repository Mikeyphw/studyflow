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

def require(cond, msg):
    if not cond:
        errors.append(msg)

models = read("core/diagnostics/src/main/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabs.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowStudyEngineLab.kt")
ui = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/StudyEngineLabPanel.kt")
panel = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugLabsPanel.kt")
main = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
view_model = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowDebugWorkbenchViewModel.kt")
tests = read("app/src/test/java/com/mikeyphw/studyflow/StudyFlowStudyEngineLabTest.kt")
core_tests = read("core/diagnostics/src/test/kotlin/com/mikeyphw/studyflow/core/diagnostics/DebugWorkbenchLabsTest.kt")

require("data class StudyEngineLabReport" in models, "StudyEngineLabReport model missing")
require("enum class StudyEngineDiagnosticKind" in models, "StudyEngineDiagnosticKind missing")
for kind in ["SCHEDULE_EXPANSION", "REVIEW_QUEUE", "PRACTICE_DECK", "MOCK_EXAM", "RECOMMENDATIONS", "SEARCH_QUERY", "PROGRESS_SNAPSHOT", "ANALYTICS_SNAPSHOT", "SYNC_FOUNDATION"]:
    require(kind in models and kind in ui, f"{kind} not modelled and rendered")
require("diagnostics.distinctBy(StudyEngineDiagnostic::kind).size == diagnostics.size" in models, "duplicate diagnostic kinds not rejected")
require("percent?.let { require(it in 0..100) }" in models, "percentage bounds not enforced")
require("status != DebugLabStatus.RUNNING" in models, "completed diagnostics may remain running")

for call in [
    "StudySchedulePlanner(",
    "generateActivities(",
    "observeQueue(planId).first()",
    "PracticeAnalytics.summarize",
    "buildDeck(",
    "recommendations(planId",
    "manageStudyDiscovery.search(searchQuery)",
    "observeSnapshot(planId).first()",
    "inspectLocalChanges()",
]:
    require(call in app, f"Production study-engine call missing: {call}")

for forbidden in ["targetLabel", ".title", ".subtitle", "localChangeToken", "deviceName", "blockedReason"]:
    require(forbidden not in app, f"D6 app lab exposes forbidden raw value or token: {forbidden}")
require("defaultPlanId()" in app and "MISSING_DEFAULT_PLAN" in app, "missing-plan boundary absent")
require("catch (cancelled: CancellationException)" in app, "cancellation not preserved")
require("catch (fatal: Error)" in app and "throw fatal" in app, "fatal JVM errors not rethrown")
require("catch (_: Exception)" in app, "recoverable exceptions not handled explicitly")
require("DiagnosticOutcome.STARTED" in app and "DiagnosticOutcome.SUCCEEDED" in app and "DiagnosticOutcome.BLOCKED" in app, "diagnostic events incomplete")

require("STUDY_ENGINE" in panel and "debug_lab_page_study_engine" in panel, "DebugLabsPanel lacks Study Engine page")
require("StudyFlowStudyEngineLab(" in view_model and "studyEngineReport" in main,
        "Study Engine lab is not ViewModel-owned and surfaced by StudyFlowApp")
require("StudyEngineLabPanel" in panel, "DebugLabsPanel does not render StudyEngineLabPanel")
require("studyEngineRunning" in view_model and "onInspectStudyEngine" in main,
        "Study Engine action is not guarded in the ViewModel and surfaced by the app")
require(app.count("configurationJson = questionIdsConfig") == 2,
        "Study Engine canary may use configurationJson only for its two synthetic execution inputs")

for phrase in ["without default plan", "forwards exact default plan", "recoverable inspection error", "cancellation is never", "fatal errors are never"]:
    require(phrase in tests, f"App test missing: {phrase}")
for phrase in ["study engine report counts statuses", "duplicate diagnostic kinds", "unbounded percentages"]:
    require(phrase in core_tests, f"Core D6 test missing: {phrase}")

for rel in ["feature/settings/src/main/res/values/strings.xml", "feature/settings/src/main/res/values-pt-rBR/strings.xml"]:
    try:
        root = ET.parse(ROOT / rel).getroot()
        names = {child.attrib.get("name") for child in root if child.tag == "string"}
        for name in ["debug_lab_page_study_engine", "debug_study_engine_lab_title", "debug_study_engine_kind_sync"]:
            require(name in names, f"{rel} missing {name}")
    except Exception as exc:
        errors.append(f"XML parse failed for {rel}: {exc}")

require((ROOT / "DEBUG_WORKBENCH_D6_IMPLEMENTATION.md").exists(), "D6 implementation doc missing")
require((ROOT / "docs/quality/DEBUG_WORKBENCH_D6_STUDY_ENGINE_LAB.md").exists(), "D6 quality doc missing")

if errors:
    print("D6 verifier failed:")
    for e in errors:
        print("-", e)
    sys.exit(1)
print("D6 verifier passed")
