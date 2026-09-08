#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


settings = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurface.kt")
activity = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt")
strings = read("app-tv/src/main/res/values/strings.xml")
build_contract = read("app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt")
test = read("app-tv/src/test/java/com/mikeyphw/studyflow/tv/settings/StudyFlowTvSettingsSurfaceTest.kt")
build = read("build.gradle.kts")
devtool = read(".devtool.toml")
roadmap = read("ANDROID_TV_ROADMAP.md")
notes = read("TVOV15_ANDROID_TV_MINIMAL_SETTINGS.md")
quality = read("docs/quality/TVOV15_MINIMAL_TV_SETTINGS_CONTRACTS.md")
readme = read("README.md")
changelog = read("CHANGELOG.md")
app_build = read("app-tv/build.gradle.kts")

for token in (
    'object StudyFlowTvSettingsSurface',
    'const val overlay = "TVOV15"',
    'settingsArePresentationOnly = true',
    'accountManagementDisabledOnTv = true',
    'importExportDisabledOnTv = true',
    'databaseAndRepositoryAccessDisabledOnTv = true',
    'pairingStatusAndRevocationVisible = true',
    'focusAccessibilityPreferenceRepresented = true',
    'localFirstPrivacyReminderVisible = true',
    'tvSafeAboutVisible = true',
    'StudyFlowTvSettingsCardId.PAIRING',
    'StudyFlowTvSettingsCardId.FOCUS_VISIBILITY',
    'StudyFlowTvSettingsCardId.PRIVACY',
    'StudyFlowTvSettingsCardId.ABOUT',
    'StudyFlowTvSettingsAction.REVOKE_PAIRING',
    'StudyFlowTvControlAction.REVOKE_PAIRING',
):
    require(token in settings, f"TVOV15 settings model missing token: {token}")

for forbidden in ("Room", "Dao", "ContentResolver", "DocumentFile", "ACTION_OPEN_DOCUMENT", "androidx.compose", "MediaRouter", "CastContext"):
    require(forbidden not in settings, f"TVOV15 settings must stay presentation-only; found {forbidden}")

for token in (
    'StudyFlowTvDestination.SETTINGS -> renderSettingsDestination()',
    'StudyFlowTvSettingsSurface.cards(state)',
    'StudyFlowTvSettingsSurface.availableActions(state)',
    'StudyFlowTvSettingsSurface.commandForAction',
    'tv_settings_section_title',
):
    require(token in activity + strings, f"TVOV15 shell rendering missing token: {token}")

for token in (
    'object StudyFlowTvSettingsBuildContract',
    'minimalTvSettingsIntroduced = true',
    'settingsArePresentationOnly = true',
    'pairingRevocationVisible = true',
):
    require(token in build_contract, f"TVOV15 build contract missing token: {token}")
for token in (
    'minimalSettingsStayTvSafeAndPresentationOnly',
    'pairedSettingsExposeRevocationAndFocusPreference',
    'revokePairingMapsToSharedIntentAndFocusItemsStayDeterministic',
):
    require(token in test, f"TVOV15 unit test missing token: {token}")

for forbidden in ('project(":core:data")', 'project(":core:database")', 'project(":feature:', 'androidx.compose', 'androidx.tv', 'androidx.mediarouter', 'com.google.android.gms.cast'):
    require(forbidden not in app_build, f"TVOV15 must preserve app-tv dependency boundary; found {forbidden}")

require('tasks.register<Exec>("verifyStudyFlowTvSettings")' in build, "missing Gradle TVOV15 verifier task")
require("verifyStudyFlowTvSettings" in devtool, "studyflow_tv target must run TVOV15 verifier")
for token in (
    "TVOV15  Minimal TV settings [implemented]",
    "TVOV15 — Minimal TV settings",
):
    require(token in roadmap, f"roadmap missing TVOV15 token: {token}")
require("TVOV15" in notes and "presentation-only" in notes, "TVOV15 implementation note is incomplete")
require("TVOV15" in quality and "no account management" in quality, "TVOV15 quality contract is incomplete")
require("TVOV15 adds a native TV-safe Settings surface" in readme, "README missing TVOV15 summary")
require("TVOV15 - Minimal TV settings" in changelog, "CHANGELOG missing TVOV15 entry")

if FAILURES:
    print("TVOV15_SETTINGS: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV15_SETTINGS: PASS")
