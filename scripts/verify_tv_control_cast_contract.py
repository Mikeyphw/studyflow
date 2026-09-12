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


def toml_section(text: str, header: str) -> str:
    marker = f"[{header}]"
    start = text.find(marker)
    require(start >= 0, f"missing TOML section: {header}")
    if start < 0:
        return ""
    match = re.search(r"\n\[[^\n]+\]", text[start + 1 :])
    end = len(text) if match is None else start + 1 + match.start()
    return text[start:end]

roadmap = read("ANDROID_TV_ROADMAP.md")
baseline = read("TVOV00_ANDROID_TV_ARCHITECTURE_BASELINE.md")
quality = read("docs/quality/TVOV00_ARCHITECTURE_CONTRACTS.md")
devtool = read(".devtool.toml")
build = read("build.gradle.kts")
readme = read("README.md")
changelog = read("CHANGELOG.md")

for token in (
    "Android mobile app must be able to control the TV app end-to-end",
    "cast a study session, question set, review, mock exam",
    "Pairing is explicit and user-approved",
    "Cast mode sends StudyFlow intents and presentation state",
    "Control sessions expire",
    "paired-phone control/cast flows",
):
    require(token in roadmap, f"roadmap missing phone-to-TV contract token: {token}")

for token in (
    "Phone-to-TV control/cast baseline",
    "phone-driven TV navigation across every TV destination",
    "revocable, expiring control sessions",
    "raw database writes",
):
    require(token in baseline, f"baseline missing phone-to-TV contract token: {token}")

for token in (
    "paired controller for every TV destination",
    "Cast mode sends StudyFlow intents",
    "Pairing must be explicit, revocable, and session-scoped",
):
    require(token in quality, f"quality contract missing phone-to-TV token: {token}")

for token in (
    "control every TV destination",
    "cast study sessions",
    "revocable pairing",
):
    require(token in readme, f"README missing phone-to-TV token: {token}")

require("paired phone-to-TV control/cast contract" in changelog, "CHANGELOG missing control/cast entry")
require('tasks.register<Exec>("verifyStudyFlowTvControlCast")' in build, "missing Gradle control/cast verifier task")
require(
    "verifyStudyFlowTvAllContracts" in toml_section(devtool, "targets.studyflow_tv.tasks"),
    "studyflow_tv tasks missing aggregate TV contract gate",
)
tv_aggregate = build.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1]
tv_aggregate = tv_aggregate.split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
require("verifyStudyFlowTvControlCast" in tv_aggregate, "aggregate TV gate missing control/cast verifier")
require(
    "verifyStudyFlowTvAllContracts" in toml_section(devtool, "targets.studyflow_tv.validation.phases"),
    "studyflow_tv phases must run the aggregate TV contract gate",
)

for forbidden in (
    "com.google.android.gms.cast",
    "androidx.mediarouter",
    "androidx.tv",
    "tv-material",
    "androidx.compose.foundation.tv",
):
    for rel in ("app", "core", "feature", "desktopApp"):
        base = ROOT / rel
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if path.is_file() and "build" not in path.parts and path.suffix in {".gradle", ".kts", ".kt", ".xml"}:
                require(forbidden not in path.read_text(encoding="utf-8"), f"TVOV00 added forbidden dependency {forbidden} in {path.relative_to(ROOT)}")

if FAILURES:
    print("TV_CONTROL_CAST_CONTRACT: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TV_CONTROL_CAST_CONTRACT: PASS")
