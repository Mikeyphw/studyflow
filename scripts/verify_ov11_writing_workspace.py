#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(f"OV11_WRITING_WORKSPACE: FAIL: {message}")

workspace = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/WritingWorkspace.kt")
manager = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudySession.kt")
screen = text("feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/StudySessionScreen.kt")
section = text("feature/sessions/src/main/java/com/mikeyphw/studyflow/feature/sessions/WritingWorkspaceSection.kt")
preset = text("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BancoDoBrasilAgenteComercialPreset.kt")

en_path = ROOT / "feature/sessions/src/main/res/values/strings.xml"
pt_path = ROOT / "feature/sessions/src/main/res/values-pt-rBR/strings.xml"
for path in (en_path, pt_path):
    ET.parse(path)

en = en_path.read_text(encoding="utf-8")
pt = pt_path.read_text(encoding="utf-8")

for token in (
    "data class WritingRubric",
    "themeAndTask: Int",
    "textType: Int",
    "cohesion: Int",
    "argumentation: Int",
    "standardLanguage: Int",
    "studyflowWritingV1=",
    "WritingWorkspaceAnalytics",
):
    require(token in workspace, f"domain contract missing {token}")
require("score ->\n            require(score in 0..20)" in workspace, "rubric dimensions must stay 0..20")
require("saveWritingDraft" in manager, "manager must save durable writing drafts")
require("completeWriting" in manager, "manager must expose dedicated writing completion")
require("require(checklist.complete)" in manager, "completion must require checklist")
require("session.type != StudyActivityType.WRITING" in screen, "generic finish must not bypass writing workflow")
for token in (
    "writing_editor",
    "WritingRubricField",
    "WritingChecklistRow",
    "writing_history_title",
    "writing_weekly_goal",
):
    require(token in section, f"writing UI missing {token}")
require("WeeklyWritingSessionGoal = 1" in preset, "BB weekly writing goal must remain one session")
require("Writing workspace" in en, "English writing resources missing")
require("Workspace de Redação" in pt and "Norma-padrão" in pt, "pt-BR writing resources missing")
print("OV11_WRITING_WORKSPACE: PASS")
