#!/usr/bin/env python3
from __future__ import annotations

import re
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


def find_section(text: str, header: str) -> str:
    marker = f"[{header}]"
    start = text.find(marker)
    require(start >= 0, f"missing TOML section: {header}")
    if start < 0:
        return ""
    next_match = re.search(r"\n\[[^\n]+\]", text[start + 1 :])
    end = len(text) if next_match is None else start + 1 + next_match.start()
    return text[start:end]


devtool = read(".devtool.toml")
build = read("build.gradle.kts")
settings = read("settings.gradle.kts")
roadmap = read("ANDROID_TV_ROADMAP.md")
baseline = read("TVOV00_ANDROID_TV_ARCHITECTURE_BASELINE.md")
quality = read("docs/quality/TVOV00_ARCHITECTURE_CONTRACTS.md")

require("[targets.studyflow_tv]" in devtool, "studyflow_tv Devtool target is missing")
require('type = "gradle"' in find_section(devtool, "targets.studyflow_tv"), "studyflow_tv must be Gradle-backed")
require('root = "."' in find_section(devtool, "targets.studyflow_tv"), "studyflow_tv must validate from repo root")
require("[targets.studyflow_tv.gradle]" in devtool, "studyflow_tv Gradle settings are missing")
require("version = \"9.7.1\"" in find_section(devtool, "targets.studyflow_tv.gradle"), "studyflow_tv must use Gradle 9.7.1")
require("[targets.studyflow_tv.tasks]" in devtool, "studyflow_tv task set is missing")
require("[targets.studyflow_tv.validation.phases]" in devtool, "studyflow_tv validation phases are missing")

for task_name in (
    "verifyStudyFlowTvArchitecture",
    "verifyStudyFlowTvSizeBudget",
    "verifyStudyFlowTvControlCast",
):
    require(task_name in find_section(devtool, "targets.studyflow_tv.tasks"), f"studyflow_tv tasks missing {task_name}")
    require(task_name in find_section(devtool, "targets.studyflow_tv.validation.phases"), f"studyflow_tv phases missing {task_name}")
    require(f'tasks.register<Exec>("{task_name}")' in build, f"missing Gradle task registration: {task_name}")

studyflow_tv_text = devtool[devtool.find("[targets.studyflow_tv]") :]
next_android_profile = studyflow_tv_text.find("[targets.studyflow_android.validation.phase_profiles.preflight]")
if next_android_profile >= 0:
    studyflow_tv_text = studyflow_tv_text[:next_android_profile]
require(":app-tv" not in studyflow_tv_text, "studyflow_tv must not reference :app-tv before TVOV01 creates it")
require(":app:" not in studyflow_tv_text, "studyflow_tv source-only target must not run phone app tasks")
require("include(\n" in settings and '":app-tv"' not in settings, "TVOV00 must not create :app-tv yet")

for token in (
    "small TV-only APK",
    "TV_RELEASE_APK_TARGET_MIB=25",
    "TV_RELEASE_APK_HARD_CEILING_MIB=35",
    "TVOV00  Architecture boundary",
    "TVOV20  Real-device acceptance",
    "TVOV03  Phone-to-TV pairing",
    "control/cast contract",
    "paired-phone control/cast flows",
):
    require(token in roadmap, f"Android TV roadmap missing token: {token}")

for token in (
    "source-contract Gradle target",
    "verifyStudyFlowTvArchitecture",
    "verifyStudyFlowTvSizeBudget",
    "verifyStudyFlowTvControlCast",
    "Phone-to-TV control/cast baseline",
    "It does not create",
    "`:app-tv` yet",
):
    require(token in baseline, f"TVOV00 baseline missing token: {token}")

for token in (
    "source-contract-only",
    "25 MiB target",
    "35 MiB hard ceiling",
    "Shared modules do not depend on Android TV UI libraries",
    "paired controller",
    "Cast mode sends StudyFlow intents",
):
    require(token in quality, f"TVOV00 quality contract missing token: {token}")

for forbidden in ("androidx.tv", "tv-material", "androidx.compose.foundation.tv"):
    for rel in ("app", "core", "feature", "desktopApp"):
        base = ROOT / rel
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.is_file() and "build" not in path.parts and path.suffix in {".kt", ".kts", ".toml", ".xml"}:
                require(forbidden not in path.read_text(encoding="utf-8"), f"TV dependency leaked into {path.relative_to(ROOT)}")

if FAILURES:
    print("TVOV00_ARCHITECTURE: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TVOV00_ARCHITECTURE: PASS")
