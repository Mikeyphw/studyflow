#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
paths = {
    "ports": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopPlatformPorts.kt",
    "native": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopNativePlatform.kt",
    "links": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopDeepLinkRouter.kt",
    "bootstrap": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppBootstrap.kt",
    "main": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/Main.kt",
    "state": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAppState.kt",
    "library": ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLibraryScreen.kt",
    "test": ROOT / "desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopNativePlatformDeepLinkTest.kt",
    "remaining": ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json",
    "root_gradle": ROOT / "build.gradle.kts",
    "desktop_gradle": ROOT / "desktopApp/build.gradle.kts",
    "devtool": ROOT / ".devtool.toml",
}
for key, path in paths.items():
    assert path.is_file(), f"missing {key}: {path}"
text = {key: path.read_text() for key, path in paths.items()}
for token in ("DesktopFilesystemPort", "openPath", "openUrl", "NoOpDesktopFilesystemPort"):
    assert token in text["ports"], token
for token in ("JFileChooser", "Desktop.getDesktop()", "SystemTray", "GraphicsEnvironment.isHeadless()"):
    assert token in text["native"], token
for token in ('"studyflow"', '"route"', '"plan"', '"library"'):
    assert token in text["links"], token
assert "NativeDesktopPlatformPorts" in text["bootstrap"]
assert "fun main(args: Array<String>)" in text["main"] and "studyflow://" in text["main"]
for token in ("chooseLibraryImportFile", "openSelectedMaterialExternally", "choosePortabilityExportFile", "handleDeepLink"):
    assert token in text["state"], token
for token in ("Choose file", "Open in browser", "Open with system app"):
    assert token in text["library"], token
for token in ("RecordingDesktopPlatformPorts", "openedPaths", "openedUrls", "studyflow://route/library"):
    assert token in text["test"], token
remaining = json.loads(text["remaining"])
desktop = next(row for row in remaining["remaining_tracks"] if row["id"] == "DOV08-DOV24")
assert int(desktop["completed_through"].removeprefix("DOV")) >= 23
for key in ("root_gradle", "desktop_gradle"):
    assert "verifyDesktopDov23NativeDesktopIntegration" in text[key]
assert '"verifyDesktopDov23NativeDesktopIntegration"' in text["devtool"]
print("DESKTOP_DOV23_NATIVE_DESKTOP_INTEGRATION: PASS")
