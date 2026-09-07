#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text()

def require(cond, msg):
    if not cond:
        raise SystemExit(f"OV20_LINT_WARNING_CLEANUP: FAIL: {msg}")

settings_pt = text("feature/settings/src/main/res/values-pt-rBR/strings.xml")
require("descriptografa, autentica e inspeciona" not in settings_pt, "lint false-positive wording returned")
require("valida a autenticidade" in settings_pt, "pt-BR authenticity wording missing")

for rel, plural_name, require_many in [
    ("feature/library/src/main/res/values/strings.xml", "reference_stale_policy", False),
    ("feature/library/src/main/res/values-pt-rBR/strings.xml", "reference_stale_policy", True),
    ("feature/sessions/src/main/res/values/strings.xml", "writing_weekly_goal", False),
    ("feature/sessions/src/main/res/values-pt-rBR/strings.xml", "writing_weekly_goal", True),
]:
    root = ET.parse(ROOT / rel).getroot()
    plurals = [p for p in root.findall("plurals") if p.attrib.get("name") == plural_name]
    require(len(plurals) == 1, f"{plural_name} must be exactly one plurals resource in {rel}")
    quantities = {i.attrib.get("quantity") for i in plurals[0].findall("item")}
    require({"one", "other"}.issubset(quantities), f"{plural_name} missing one/other in {rel}")
    if require_many:
        require("many" in quantities, f"{plural_name} missing pt-BR many in {rel}")

library = text("feature/library/src/main/java/com/mikeyphw/studyflow/feature/library/StudyLibraryScreen.kt")
require("R.plurals.reference_stale_policy" in library, "library call site must use plural resource")
require("R.string.reference_stale_policy" not in library, "old library string call returned")
require(
    "StudyReferenceFreshnessPolicy.PossiblyStaleAfterDays.toPluralQuantity()" in library,
    "Long stale-day quantity must be converted to Int for pluralStringResource",
)
require(
    "private fun Long.toPluralQuantity(): Int" in library,
    "bounded Long-to-plural quantity helper missing",
)

sessions = text("feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/WritingWorkspaceSection.kt")
require("import androidx.compose.ui.res.pluralStringResource" in sessions, "sessions pluralStringResource import missing")
require("R.plurals.writing_weekly_goal" in sessions, "sessions weekly goal must use plural resource")
require("R.string.writing_weekly_goal" not in sessions, "old sessions string call returned")

plans_en = text("feature/plans/src/main/res/values/strings.xml")
plans_pt = text("feature/plans/src/main/res/values-pt-rBR/strings.xml")
require("Import diff — to add: %1$d;" in plans_en, "count-neutral English import diff wording missing")
require("Diferenças da importação — a adicionar: %1$d;" in plans_pt, "count-neutral pt-BR import diff wording missing")

unused = [
    "readiness_message_with_confidence",
    "dashboard_questions_total",
    "dashboard_accuracy_windows",
    "dashboard_speed",
    "dashboard_mock_cutoff",
    "dashboard_days_to_exam",
    "edital_matrix_row",
]
for rel in [
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
]:
    body = text(rel)
    for name in unused:
        require(f'name="{name}"' not in body, f"unused resource {name} returned in {rel}")

print("OV20_LINT_WARNING_CLEANUP: PASS")
