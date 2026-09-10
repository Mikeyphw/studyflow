#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
paths = {
    "appearance": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppearance.kt",
    "app": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/StudyFlowDesktopApp.kt",
    "state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "layout": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLayout.kt",
    "settings": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAnalyticsSearchSettingsScreens.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppearancePersistenceTest.kt",
    "matrix": ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for key, path in paths.items():
    assert path.is_file(), f"missing {key}: {path}"
text = {key: path.read_text() for key, path in paths.items()}
for token in ("ThemeMode.SYSTEM_DEFAULT", "ThemeMode.LIGHT", "ThemeMode.DARK", "systemDarkTheme"):
    assert token in text["appearance"], token
for token in ("isSystemInDarkTheme()", "resolveDesktopDarkTheme", "darkColors()", "lightColors()", "MaterialTheme(colors = colors)"):
    assert token in text["app"], token
assert "currentThemeMode()" in text["state"] and "preferencesRevision" in text["state"]
assert "desktopThemeModeLabel(state.themeMode)" in text["settings"]
for forbidden in ("Color(0xFFF7F8FA)", "Color.DarkGray", "Color(0xFFD0D5DD)", "Color(0xFFE9EEF6)"):
    assert forbidden not in text["layout"], forbidden
assert "MaterialTheme.colors" in text["layout"]
for token in ("systemLightAndDarkResolveAgainstSystemState", "selectedThemePersistsAcrossWorkspaceRestart"):
    assert token in text["test"], token
assert "System / Light / Dark appearance | FULL | LIMITED | FULL" in text["matrix"]
remaining = json.loads(text["remaining"])
desktop = next(row for row in remaining["remaining_tracks"] if row["id"] == "DOV08-DOV24")
assert desktop["completed_through"] == "DOV22"
for key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov22SystemLightDarkAppearance" in text[key]
assert '"verifyDesktopDov22SystemLightDarkAppearance"' in text["devtool"]
print("DESKTOP_DOV22_SYSTEM_LIGHT_DARK_APPEARANCE: PASS")
