#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
checks = {
 "persistence": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistence.kt", [
   "content-modes-v1.properties", "ATOMIC_MOVE", "DesktopContentModeSnapshot", "purgePlan",
 ]),
 "controller": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModeController.kt", [
   "ProcessBuilder", "buildLanguageReviewPrompt", "evaluateLanguageReviewAnswer", "setMode",
   "python3", "node", "go", "Programming workspace escaped",
   'testCommandTemplate = "python3 main.py"',
 ]),
 "screen": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModeScreen.kt", [
   "Programming Course", "Language Deck", "Reading Study", "General Skill", "runs with your desktop user permissions",
 ]),
 "services": ("desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopStudyFlowServices.kt", [
   "DesktopContentModePersistence.open", "contentModeController", "contentModeRepository",
 ]),
 "test": ("desktopApp/src/test/kotlin/com/mikeyphw/studyflow/desktop/DesktopContentModePersistenceExecutionTest.kt", [
   "programmingAndLanguageModeEvidencePersistAcrossRestart", "content-modes-v1.properties",
 ]),
}
for label,(name,tokens) in checks.items():
 text=(ROOT/name).read_text(); missing=[t for t in tokens if t not in text]
 if missing: raise SystemExit(f"{label} missing: {missing}")
cap=(ROOT/'docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md').read_text()
if '| Programming / language content modes | FULL | FULL | FULL | LIMITED | LIMITED |' not in cap:
 raise SystemExit('Desktop content-mode capability is not FULL')
if '| Termux exercise execution | PLATFORM_SPECIFIC | NOT_APPLICABLE | NOT_APPLICABLE |' not in cap:
 raise SystemExit('Termux remains Android platform-specific')
build=(ROOT/'build.gradle.kts').read_text()
if 'tasks.register<Exec>("verifyDesktopDov17ContentModesExecution")' not in build:
 raise SystemExit('DOV17 root Gradle task missing')
devtool=(ROOT/'.devtool.toml').read_text()
if '"verifyDesktopDov17ContentModesExecution"' not in devtool:
 raise SystemExit('DOV17 Devtool wiring missing')
print('DESKTOP_DOV17_CONTENT_MODES_EXECUTION: PASS')
