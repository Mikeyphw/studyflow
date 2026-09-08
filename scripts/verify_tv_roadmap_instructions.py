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


roadmap = read("ANDROID_TV_ROADMAP.md")
rules = read("TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md")
readme = read("README.md")
quality = read("docs/quality/TV_ROADMAP_INSTRUCTIONS_CONTRACTS.md")
root_gradle = read("build.gradle.kts")
devtool = read(".devtool.toml")
validate = read(".devtool-artifact/validate.sh") if (ROOT / ".devtool-artifact/validate.sh").is_file() else ""

combined = "\n".join([roadmap, rules, readme, quality])

for index in range(0, 21):
    token = f"TVOV{index:02d}"
    require(token in rules, f"full roadmap missing {token} in working-rules document")
    require(token in roadmap, f"full roadmap missing {token} in ANDROID_TV_ROADMAP.md")

for index in range(0, 19):
    require(f"TVOV{index:02d}" in rules and "[implemented]" in rules.split(f"TVOV{index:02d}", 1)[1].split("\n", 1)[0], f"TVOV{index:02d} not marked implemented in working-rules roadmap")

required_phrases = [
    "No light placeholder overlays",
    "structured implementation unit",
    "studyflow_tv",
    "devtool --copy apply overlay",
    "apply.commit.enabled=true",
    "Termux Devtool validation is authoritative",
    "Compose Runtime",
    "AndroidX TV UI",
    "Cast SDK",
    "MediaRouter",
    "core:data",
    "core:database",
    "phone feature-module fanout",
    "revocable pairing",
    "raw database rows",
    "no schedule mutation",
    "SAF picker",
    "Room access",
]
for phrase in required_phrases:
    require(phrase in combined, f"missing roadmap/instruction phrase: {phrase}")

require("TVOV15  Minimal TV settings [implemented]" in rules, "TVOV15 settings roadmap item must be implemented")
require("TVOV16  Cross-device control/state continuity [implemented]" in rules, "TVOV16 continuity roadmap item must be implemented")
require("TVOV17  Accessibility and 10-foot UI seal [implemented]" in rules, "TVOV17 accessibility roadmap item must be implemented")
require("TVOV18  Size optimization, R8, and resource shrinking [implemented]" in rules, "TVOV18 size optimization roadmap item must be implemented")
require("TVOV19" in rules and "[next]" in rules.split("TVOV19", 1)[1].split("\n", 1)[0], "TVOV19 must be the next TV roadmap item")
require('tasks.register<Exec>("verifyStudyFlowTvRoadmapInstructions")' in root_gradle, "missing Gradle roadmap/instructions verifier task")
require("verifyStudyFlowTvRoadmapInstructions" in devtool, "studyflow_tv target missing roadmap/instructions verifier")
if validate:
    require("verify_tv_roadmap_instructions.py" in validate, "artifact validator missing roadmap/instructions script")

if FAILURES:
    print("TV_ROADMAP_INSTRUCTIONS: FAIL")
    for failure in FAILURES:
        print(f" - {failure}")
    sys.exit(1)

print("TV_ROADMAP_INSTRUCTIONS: PASS")
