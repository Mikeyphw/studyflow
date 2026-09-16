#!/usr/bin/env python3
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors = []


def read(path):
    file_path = ROOT / path
    if not file_path.is_file():
        errors.append(f"missing file: {path}")
        return ""
    return file_path.read_text(encoding="utf-8")


def require(path, *tokens):
    text = read(path)
    for token in tokens:
        if token not in text:
            errors.append(f"{path}: missing {token!r}")


require(
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/PublicExamLegislation.kt",
    "data class LegislationDocument(",
    "data class LegislationProvision(",
    "data class LegislationEvidenceLink(",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamLegislation.kt",
    "object LegislationImportParser",
    "STRUCTURED_TEXT",
    "MARKDOWN",
    "CSV",
    "No legislation provisions were recognized",
)
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamDashboard.kt",
    "data class PublicExamDashboardMetrics(",
    "accuracy7d",
    "accuracy30d",
    "accuracy90d",
    "estimatedCutoffGapPercent",
    "data class PublicExamEditalMatrixRow(",
    "incidencePercent: Int?",
    "object PublicExamDashboardBuilder",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowMigrations.kt",
    "Migration12To13",
    "legislation_documents",
    "legislation_provisions",
    "legislation_evidence_links",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt",
    "SCHEMA_VERSION = 15",
    "LegislationDocumentEntity::class",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphDuplicator.kt",
    "legislation_documents",
    "legislation_provisions",
    "legislation_evidence_links",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyPlanGraphPortabilityStore.kt",
    'rows("legislation_evidence_links")',
    "Legislation evidence link must reference exactly one evidence target",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PublicExamLegislationPanel.kt",
    "ActivityResultContracts.OpenDocument",
    "LegislationImportFormat.CSV",
    "legislation_local_only_notice",
)
require(
    "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
    "PublicExamDashboardBuilder.build",
    "PublicExamDashboardSummary",
    "PublicExamLegislationPanel",
)

schema = ROOT / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/13.json"
try:
    data = json.loads(schema.read_text(encoding="utf-8"))
    if data["database"]["version"] != 13:
        errors.append("schema 13 version mismatch")
except Exception as exc:
    errors.append(f"schema 13 parse failed: {exc}")

resource_files = (
    "feature/workspace/src/main/res/values/strings.xml",
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml",
)
for rel in resource_files:
    try:
        ET.parse(ROOT / rel)
    except Exception as exc:
        errors.append(f"{rel}: XML parse failed: {exc}")

def resource_names(rel):
    return {
        node.attrib["name"]
        for node in ET.parse(ROOT / rel).getroot()
        if "name" in node.attrib
    }


en = resource_names(resource_files[0])
pt = resource_names(resource_files[1])
keys = {
    key
    for key in en | pt
    if key.startswith(
        (
            "legislation_",
            "dashboard_",
            "edital_",
            "public_exam_dashboard",
            "weighted_mastery",
            "importance_mastery",
        )
    )
}
if keys - en:
    errors.append("missing EN resources: " + ",".join(sorted(keys - en)))
if keys - pt:
    errors.append("missing pt-BR resources: " + ",".join(sorted(keys - pt)))

entities = read(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowDatabase.kt"
)
if re.search(r"@Entity[^\n]+dashboard|DashboardEntity::class", entities, re.I):
    errors.append("CPOV10 must remain derived-only")

if errors:
    print("CPOV09_10 verification FAILED", file=sys.stderr)
    for error in errors:
        print("- " + error, file=sys.stderr)
    raise SystemExit(1)
print("CPOV09_10 verification PASS")
