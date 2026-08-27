#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIN_GRADLE = (9, 7, 0)
CURRENT_GRADLE = "9.7.1"
CURRENT_GRADLE_SHA256 = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
NDK_VERSION = "29.0.14206865"


def fail(message: str) -> None:
    print(f"CPOV00_TOOLCHAIN: FAILED — {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def parse_stable_gradle(version: str) -> tuple[int, int, int] | None:
    match = re.fullmatch(r"(\d+)\.(\d+)(?:\.(\d+))?", version)
    if match is None:
        return None
    return tuple(int(part or 0) for part in match.groups(default="0"))


def is_supported_gradle(version: str) -> bool:
    parsed = parse_stable_gradle(version)
    return parsed is not None and parsed >= MIN_GRADLE


# Comparator regression contract: stable minimum accepted, prereleases rejected.
CASES = {
    "9.6.1": False,
    "9.7.0": True,
    "9.7.1": True,
    "9.8": True,
    "10.0.0": True,
    "9.8-milestone-1": False,
    "9.8-rc-1": False,
    "10.0-nightly": False,
}
for candidate, expected in CASES.items():
    require(
        is_supported_gradle(candidate) is expected,
        f"Gradle comparator regression for {candidate}: expected {expected}",
    )

wrapper_path = ROOT / "gradle/wrapper/gradle-wrapper.properties"
wrapper = wrapper_path.read_text(encoding="utf-8")
version_match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip", wrapper)
require(version_match is not None, "wrapper URL must name a stable binary Gradle distribution")
wrapper_version = version_match.group(1) if version_match else ""
require(is_supported_gradle(wrapper_version), "wrapper Gradle must be stable >= 9.7.0")
checksum_match = re.search(r"(?m)^distributionSha256Sum=([0-9a-f]{64})$", wrapper)
require(checksum_match is not None, "wrapper must pin a lowercase 64-character SHA-256")
wrapper_checksum = checksum_match.group(1) if checksum_match else ""
if wrapper_version == CURRENT_GRADLE:
    require(
        wrapper_checksum == CURRENT_GRADLE_SHA256,
        f"Gradle {CURRENT_GRADLE} checksum must match the official pinned value",
    )

with (ROOT / ".devtool.toml").open("rb") as handle:
    devtool = tomllib.load(handle)
targets = devtool.get("targets", {})
for target_name in ("studyflow_android", "studyflow_desktop"):
    target = targets.get(target_name, {})
    gradle_version = target.get("gradle", {}).get("version")
    require(isinstance(gradle_version, str), f"{target_name} must declare a Gradle version")
    require(is_supported_gradle(gradle_version), f"{target_name} must use stable Gradle >= 9.7.0")
    require(
        gradle_version == wrapper_version,
        f"{target_name} Gradle must match wrapper version {wrapper_version}",
    )

android_target = targets.get("studyflow_android", {}).get("android", {})
require(
    android_target.get("ndk_version") == NDK_VERSION,
    f"studyflow_android must pin NDK {NDK_VERSION}",
)

android_convention_path = ROOT / "build-logic/convention/src/main/kotlin/AndroidConvention.kt"
android_convention = android_convention_path.read_text(encoding="utf-8")
require(
    android_convention.count(f'internal const val STUDYFLOW_NDK_VERSION = "{NDK_VERSION}"') == 1,
    "Android convention must define exactly one canonical NDK version constant",
)
require(
    android_convention.count("ndkVersion = STUDYFLOW_NDK_VERSION") == 2,
    "application and library conventions must both consume the canonical NDK version",
)
require(
    android_convention.count(NDK_VERSION) == 1,
    "NDK version literal must occur only in the canonical shared convention constant",
)

# No active validator may retain the old exact-9.7.0-only contract.
active_contracts = [
    ROOT / "build.gradle.kts",
    ROOT / "scripts/verify_phase_1.py",
    ROOT / "scripts/verify_remediation_phase_1_2.py",
]
for contract in active_contracts:
    contract_text = contract.read_text(encoding="utf-8")
    require(
        "gradle-9.7.0-bin.zip" not in contract_text,
        f"{contract.relative_to(ROOT)} still pins only Gradle 9.7.0",
    )

model_build = (ROOT / "core/model/build.gradle.kts").read_text(encoding="utf-8")
require(
    'testImplementation(kotlin("test-junit"))' in model_build,
    "core:model must provide kotlin-test-junit because its tests import kotlin.test.*",
)

print(
    "CPOV00_TOOLCHAIN: PASSED "
    f"(Gradle {wrapper_version}, NDK {NDK_VERSION}, 2 Devtool targets, comparator + core:model test-classpath contracts green)"
)
