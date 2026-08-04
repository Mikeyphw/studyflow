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


if not phase_version_at_least(ROOT, 32):
    errors.append("app version must be Phase 32 or later")

settings = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/SettingsScreen.kt")
app = read("app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt")
workbench = read("feature/settings/src/main/java/com/mikeyphw/studyflow/feature/settings/DebugWorkbenchScreen.kt")
strings_en = read("feature/settings/src/main/res/values/strings.xml")
strings_pt = read("feature/settings/src/main/res/values-pt-rBR/strings.xml")

require("diagnosticsAvailable: Boolean" in settings and "if (diagnosticsAvailable)" in settings,
        "Settings must keep developer diagnostics gated")
require("diagnosticsAvailable = BuildConfig.DEBUG" in app,
        "App host must expose diagnostics only for debug builds")
for forbidden in [
    "DebugProbePlaceholder",
    "DebugLabPlaceholder",
    "debug_probes_placeholder",
    "debug_labs_placeholder_title",
    "debug_labs_placeholder_message",
    "arrive in Debug Workbench",
    "à medida que forem instalados",
    "as they are installed",
]:
    require(forbidden not in workbench + strings_en + strings_pt,
            f"developer diagnostics still carries placeholder/future copy: {forbidden}")
for required in [
    "DebugProbeEntryPanel",
    "DebugLabEmptyPanel",
    "debug_probes_entry_message",
    "debug_labs_empty_message",
]:
    require(required in workbench + strings_en + strings_pt,
            f"developer diagnostics missing concrete fallback marker: {required}")
require("Developer diagnostics" in strings_en, "English diagnostics title must be developer/support scoped")
require("Diagnósticos de desenvolvedor" in strings_pt, "Portuguese diagnostics title must be developer/support scoped")
require("support-only diagnostics" in strings_en, "English settings copy must scope diagnostics away from normal settings")
require("diagnósticos de suporte" in strings_pt, "Portuguese settings copy must scope diagnostics away from normal settings")

for rel in [
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"invalid XML in {rel}: {exc}")

seal = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
require("ReleaseReadinessPhase(32" in seal and "Developer diagnostics de-scaffolding" in seal,
        "Release readiness seal must include Phase 32")
factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require(any(f"CurrentPhase = {phase}" in factory for phase in range(32, 100)),
        "Release validation factory must advance beyond Phase 31")
validate_repo = read("scripts/validate_repo.sh")
require("python3 scripts/verify_phase_32.py" in validate_repo,
        "validate_repo must run Phase 32 verifier")

if errors:
    print("PHASE_32_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_32_CONTRACT: PASSED (developer diagnostics de-scaffolded and gated)")
