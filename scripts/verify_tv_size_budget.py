#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []
TARGET_MIB = 25
HARD_CEILING_MIB = 35


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def section(text: str, header: str) -> str:
    marker = f"[{header}]"
    start = text.find(marker)
    require(start >= 0, f"missing TOML section: {header}")
    if start < 0:
        return ""
    end_match = re.search(r"\n\[[^\n]+\]", text[start + 1 :])
    end = len(text) if end_match is None else start + 1 + end_match.start()
    return text[start:end]


roadmap = read("ANDROID_TV_ROADMAP.md")
quality = read("docs/quality/TVOV00_ARCHITECTURE_CONTRACTS.md")
devtool = read(".devtool.toml")
build = read("build.gradle.kts")
settings = read("settings.gradle.kts")

require(f"TV_RELEASE_APK_TARGET_MIB={TARGET_MIB}" in roadmap, "TV target MiB budget is missing")
require(f"TV_RELEASE_APK_HARD_CEILING_MIB={HARD_CEILING_MIB}" in roadmap, "TV hard ceiling MiB budget is missing")
require("25 MiB target" in quality, "TV quality doc must state the target budget")
require("35 MiB hard ceiling" in quality, "TV quality doc must state the hard ceiling")
require(TARGET_MIB < HARD_CEILING_MIB, "TV target budget must be lower than hard ceiling")

for task_name in ("verifyStudyFlowTvArchitecture", "verifyStudyFlowTvSizeBudget"):
    require(task_name in section(devtool, "targets.studyflow_tv.tasks"), f"studyflow_tv tasks missing {task_name}")

source_only_target = section(devtool, "targets.studyflow_tv.validation.phases")
for forbidden in (":app-tv:", ":app:assemble", ":app:bundle", ":app:lint", ":app:test"):
    require(forbidden not in source_only_target, f"studyflow_tv placeholder target must not run {forbidden}")

require('"verifyStudyFlowTvSizeBudget"' in build, "checkAll/Gradle wiring must retain TV size verifier")
require('":app-tv"' not in settings, "TVOV00 must not create app-tv before size contracts exist")

artifact_dirs = [ROOT / "app" / "build" / "outputs", ROOT / "app-tv" / "build" / "outputs"]
for artifact_dir in artifact_dirs:
    if not artifact_dir.exists():
        continue
    for apk in artifact_dir.rglob("*.apk"):
        if "tv" not in apk.name.lower() and "app-tv" not in str(apk).lower():
            continue
        size_mib = apk.stat().st_size / (1024 * 1024)
        require(size_mib <= HARD_CEILING_MIB, f"{apk.relative_to(ROOT)} is {size_mib:.1f} MiB, above TV ceiling")

if FAILURES:
    print("TV_SIZE_BUDGET: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    sys.exit(1)
print("TV_SIZE_BUDGET: PASS")
