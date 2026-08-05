#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


if not phase_version_at_least(ROOT, 31):
    errors.append("app version must be Phase 31 or later")

dashboard = read("feature/dashboard/src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt")
for marker in [
    "onStartSession: () -> Unit",
    "Button(onClick = onStartSession)",
    "onStartSession = onOpenSessions",
    "DefaultPlanBadge",
]:
    require(marker in dashboard, f"dashboard missing session-action marker: {marker}")
for forbidden in [
    "Button(onClick = {})",
    "AssistChip(\n                                onClick = {}",
]:
    require(forbidden not in dashboard, f"dashboard still has fake interactive control: {forbidden}")

app = "\n".join(
    read(path)
    for path in (
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
        "app/src/main/java/com/mikeyphw/studyflow/StudyFlowV2Navigation.kt",
    )
)
require(
    "onOpenSessions = {" in app or "onSessions = {" in app or "onOpenFocus = {" in app,
    "active workspace host must expose a real study-session action",
)
require(
    "navController.navigate(SessionsRoute(" in app or "navController.navigate(session.focusRoute())" in app,
    "study-session action must navigate to sessions or focused execution",
)
require(
    "composable<DashboardRoute>" in app and "LegacyWorkspaceRedirect" in app,
    "legacy dashboard route must remain translated into the V2 workspace shell",
)

for rel in [
    "feature/dashboard/src/main/res/values/strings.xml",
    "feature/dashboard/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    for snippet in ["adaptive recommendation engine", "fases de planejamento", "will activate", "será ativado"]:
        require(snippet not in text, f"{rel} still contains dashboard roadmap copy: {snippet}")
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"invalid XML in {rel}: {exc}")

require("Open study sessions" in read("feature/dashboard/src/main/res/values/strings.xml"), "English session CTA copy missing")
require("Abrir sessões de estudo" in read("feature/dashboard/src/main/res/values-pt-rBR/strings.xml"), "Portuguese session CTA copy missing")

dashboard_test = read("feature/dashboard/src/test/java/com/mikeyphw/studyflow/feature/dashboard/DashboardContractTest.kt")
require("primary session action is wired to dashboard callback" in dashboard_test, "dashboard contract test missing")

build = read("build.gradle.kts")
validate_repo = read("scripts/validate_repo.sh")
require("through Phase" in build and "Phase 37" in build, "repository contract description must cover Phase 37 or later")
require("for phase in {0..37}; do" in build, "repository contract loop must cover Phase 37 or later")
require("python3 scripts/verify_phase_30.py" in validate_repo, "validate_repo must run Phase 30")
require("python3 scripts/verify_phase_31.py" in validate_repo, "validate_repo must run Phase 31")

if errors:
    print("PHASE_31_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_31_CONTRACT: PASSED (dashboard primary action routes to study sessions)")
