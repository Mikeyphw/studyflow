#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"OV04_NAVIGATION_PROGRESS: FAIL: {message}")


components = read("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowWorkspaceComponents.kt")
shell = read("core/designsystem/src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowShellPrimitives.kt")
progress = read("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/ProgressWorkspaceScreen.kt")

require(".widthIn(max = 360.dp)" in components, "plan selector is not width-capped")
require("stateDescription = planSupportingText" in components, "compact plan selector lost supporting context")
require("softWrap = false" in shell and "overflow = TextOverflow.Ellipsis" in shell, "compact dock labels may wrap")
require(".heightIn(min = 48.dp)" in shell, "compact dock touch target is not protected")

for token in ("SUMMARY", "PERFORMANCE", "GOALS", "TOOLS"):
    require(token in progress, f"Progress segment {token} is missing")
require("periods = ProgressHubSegment.entries" in progress, "Progress segment selector is not wired")
require("if (selectedSegment.usesPeriod)" in progress, "period strip is not segment-aware")
require(
    "this == ProgressHubSegment.SUMMARY || this == ProgressHubSegment.PERFORMANCE" in progress,
    "Goals/Tools can still expose irrelevant period controls",
)
for token in (
    "selectedSegment == ProgressHubSegment.SUMMARY",
    "selectedSegment == ProgressHubSegment.PERFORMANCE",
    "selectedSegment == ProgressHubSegment.GOALS",
    "selectedSegment == ProgressHubSegment.TOOLS",
):
    require(token in progress, f"segment content gate missing: {token}")

expected = {
    "feature/workspace/src/main/res/values/strings.xml": {
        "progress_segment_summary": "Summary",
        "progress_segment_performance": "Performance",
        "progress_segment_goals": "Goals",
        "progress_segment_tools": "Tools",
    },
    "feature/workspace/src/main/res/values-pt-rBR/strings.xml": {
        "progress_segment_summary": "Resumo",
        "progress_segment_performance": "Desempenho",
        "progress_segment_goals": "Metas",
        "progress_segment_tools": "Ferramentas",
    },
    "feature/practice/src/main/res/values/strings.xml": {"practice_title": "Practice"},
    "feature/practice/src/main/res/values-pt-rBR/strings.xml": {"practice_title": "Prática"},
    "feature/progress/src/main/res/values/strings.xml": {"progress_title": "Progress"},
    "feature/progress/src/main/res/values-pt-rBR/strings.xml": {"progress_title": "Progresso"},
    "feature/dashboard/src/main/res/values/strings.xml": {"practice": "Practice", "progress_center": "Progress"},
    "feature/dashboard/src/main/res/values-pt-rBR/strings.xml": {"practice": "Prática", "progress_center": "Progresso"},
}
for rel, values in expected.items():
    tree = ET.parse(ROOT / rel)
    actual = {node.attrib.get("name"): node.text or "" for node in tree.getroot().findall("string")}
    for name, value in values.items():
        require(actual.get(name) == value, f"{rel}:{name} expected {value!r}, got {actual.get(name)!r}")

print("OV04_NAVIGATION_PROGRESS: PASS")
