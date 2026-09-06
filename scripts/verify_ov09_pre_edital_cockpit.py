#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(rel: str) -> str:
    p = ROOT / rel
    if not p.is_file():
        raise SystemExit(f"OV09 missing required file: {rel}")
    return p.read_text(encoding="utf-8")

def require(cond: bool, message: str) -> None:
    if not cond:
        raise SystemExit(f"OV09 contract failed: {message}")

core = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPreEdital.kt")
workspace = text("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt")
en_path = ROOT / "feature/workspace/src/main/res/values/strings.xml"
pt_path = ROOT / "feature/workspace/src/main/res/values-pt-rBR/strings.xml"
ET.parse(en_path)
ET.parse(pt_path)
en = en_path.read_text(encoding="utf-8")
pt = pt_path.read_text(encoding="utf-8")

for token in (
    "PublicExamDateSource",
    "CONFIRMED_TARGET",
    "EXPECTED_TARGET",
    "LEGACY_PLAN",
    "PublicExamPreEditalBuilder",
    "NO_CONFIRMED_EXAM_DATE",
    "NO_REFERENCE_NOTICE",
    "REFERENCE_PUBLICATION_DATE_UNKNOWN",
    "STALE_REFERENCE_NOTICE",
):
    require(token in core, f"domain pre-edital contract missing {token}")
require("val officialCountdownDays: Int? get() = null" in core, "pre-edital cockpit must not fabricate countdown days")
require("observeExamNoticeRevisions(plan.id)" in workspace, "workspace must observe canonical revision history")
require("PublicExamPreEditalBuilder.build(" in workspace, "workspace must derive cockpit state in domain")
require("onReviewPreEditalAssumptions = onManageContent" in workspace, "review action must reuse content/revision flow")
for key in (
    "pre_edital_cockpit_title",
    "pre_edital_cockpit_status",
    "pre_edital_review_assumptions",
):
    require(f'name="{key}"' in en, f"English resource missing {key}")
    require(f'name="{key}"' in pt, f"pt-BR resource missing {key}")

print("OV09 pre-edital cockpit source contract: PASS")
