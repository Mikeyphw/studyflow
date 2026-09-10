#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
paths = {
    "localization": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLocalization.kt",
    "app": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
    "state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "layout": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLayout.kt",
    "settings": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopLocalizationPersistenceTest.kt",
    "doc": ROOT / "DESKTOP_DOV21_EN_PTBR_LOCALIZATION.md",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for key, path in paths.items():
    assert path.is_file(), f"missing {key}: {path}"
text = {key: path.read_text() for key, path in paths.items()}
for token in (
    "AppLanguage.SYSTEM_DEFAULT",
    "AppLanguage.PORTUGUESE_BRAZIL",
    '"Settings" to "Configurações"',
    '"Library" to "Biblioteca"',
    '"Concurso Público" to "Concurso Público"',
    "staticCompositionLocalOf",
):
    assert token in text["localization"], token
assert "CompositionLocalProvider(LocalDesktopTextCatalog provides textCatalog)" in text["app"]
assert "desktopText(appState.selectedRoute.title)" in text["app"]
assert "preferencesRevision" in text["state"] and "currentLanguage()" in text["state"]
for key in ("layout", "settings"):
    assert "desktopText(" in text[key], f"{key} bypasses localization"
# Every static key routed through desktopText must have a pt-BR catalog entry.
source_files = list((ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop").glob("*.kt"))
used = set()
for path in source_files:
    used.update(re.findall(r'desktopText\("((?:[^"\\]|\\.)*)"\)', path.read_text()))
mapped = set(re.findall(r'^\s*"((?:[^"\\]|\\.)*)"\s+to\s+', text["localization"], re.MULTILINE))
missing = sorted(used - mapped)
assert not missing, f"untranslated static Desktop keys: {missing}"
for token in (
    "englishPortugueseAndSystemLocaleResolveDeterministically",
    "selectedLanguagePersistsAcrossWorkspaceRestart",
    '"Configurações"',
):
    assert token in text["test"], token
assert "English / pt-BR UI | FULL | FULL | FULL" in text["matrix"]
remaining = json.loads(text["remaining"])
desktop = next(row for row in remaining["remaining_tracks"] if row["id"] == "DOV08-DOV24")
assert desktop["completed_through"] == "DOV21"
for key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov21EnPtBrLocalization" in text[key]
assert '"verifyDesktopDov21EnPtBrLocalization"' in text["devtool"]
assert "androidx.compose.material3" not in "\n".join(path.read_text() for path in source_files)
print("DESKTOP_DOV21_EN_PTBR_LOCALIZATION: PASS")
