#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def text(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        failures.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(ok: bool, message: str) -> None:
    if not ok:
        failures.append(message)


versions_text = text("gradle/libs.versions.toml")
build_logic = text("build-logic/convention/build.gradle.kts")
android_convention = text("build-logic/convention/src/main/kotlin/AndroidConvention.kt")
kotlin_convention = text("build-logic/convention/src/main/kotlin/KotlinLibraryConventionPlugin.kt")
desktop_build = text("desktopApp/build.gradle.kts")
wrapper = text("gradle/wrapper/gradle-wrapper.properties")
devtool_text = text(".devtool.toml")

for name, version in {
    "agp": "9.3.1",
    "kotlin": "2.4.10",
    "composeBom": "2026.06.01",
    "ksp": "2.3.11",
}.items():
    require(f'{name} = "{version}"' in versions_text, f"gradle/libs.versions.toml: {name} must be {version}")

for token in (
    "JavaLanguageVersion.of(21)",
    "com.android.tools.build:gradle:9.3.1",
    "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10",
    "com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11",
):
    require(token in build_logic, f"build-logic/convention/build.gradle.kts: missing {token}")

for token, minimum in {
    "compileSdk = 36": 2,
    "targetSdk = 36": 1,
    'buildToolsVersion = "36.0.0"': 2,
    "sourceCompatibility = JavaVersion.VERSION_21": 2,
    "targetCompatibility = JavaVersion.VERSION_21": 2,
}.items():
    require(android_convention.count(token) >= minimum, f"AndroidConvention.kt: expected >= {minimum} x {token}")

require("jvmToolchain(21)" in kotlin_convention, "Kotlin library convention must use JVM toolchain 21")
require("jvmToolchain(21)" in desktop_build, "desktopApp must use JVM toolchain 21")

m = re.search(r"gradle-([0-9]+\.[0-9]+(?:\.[0-9]+)?)-bin\.zip", wrapper)
wrapper_version = m.group(1) if m else None

def version_tuple(value: str) -> tuple[int, int, int] | None:
    match = re.fullmatch(r"([0-9]+)\.([0-9]+)(?:\.([0-9]+))?", value)
    if not match:
        return None
    return int(match.group(1)), int(match.group(2)), int(match.group(3) or 0)

require(wrapper_version is not None and version_tuple(wrapper_version) >= (9, 7, 0), "Gradle wrapper must be >= 9.7.0")
checksum = re.search(r"(?m)^distributionSha256Sum=([0-9a-f]{64})$", wrapper)
require(checksum is not None, "Gradle wrapper must pin a lowercase SHA-256 distribution checksum")
if wrapper_version == "9.7.1":
    require(
        checksum is not None and checksum.group(1) == "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a",
        "Gradle 9.7.1 wrapper checksum is missing or unexpected",
    )

try:
    devtool = tomllib.loads(devtool_text)
except Exception as exc:
    failures.append(f".devtool.toml is invalid TOML: {exc}")
    devtool = {}
versions = []
for target in devtool.get("targets", {}).values():
    value = target.get("gradle", {}).get("version") if isinstance(target, dict) else None
    if value:
        versions.append(str(value))
require(len(versions) >= 2, "Devtool must define Gradle versions for multiple Gradle-backed targets")
for value in versions:
    parsed = version_tuple(value)
    require(parsed is not None and parsed >= (9, 7, 0), f"Devtool Gradle target uses unsupported version {value}")
    if wrapper_version:
        require(value == wrapper_version, f"Devtool Gradle version {value} must match wrapper {wrapper_version}")
require('ndk_version = "29.0.14206865"' in devtool_text, "Devtool Android target must pin NDK 29.0.14206865")
require(
    'internal const val STUDYFLOW_NDK_VERSION = "29.0.14206865"' in android_convention,
    "AndroidConvention.kt must define the canonical NDK 29.0.14206865 version",
)
require(android_convention.count("ndkVersion = STUDYFLOW_NDK_VERSION") == 2, "Both Android conventions must use canonical NDK")
require("VERSION_17" not in android_convention, "Java 17 remnant in Android convention")
require("jvmToolchain(17)" not in kotlin_convention, "Java 17 remnant in Kotlin convention")
require("jvmToolchain(17)" not in desktop_build and "JavaLanguageVersion.of(17)" not in build_logic, "Java 17 remnant in desktop/build logic")

prerelease = re.compile(r"(?i)(?:^|[-.+_])(alpha|beta|rc|preview|canary|eap|snapshot|nightly|milestone|dev|m\d+)(?:[-.+_]?\d*)")
in_versions = False
for line in versions_text.splitlines():
    stripped = line.strip()
    if stripped == "[versions]":
        in_versions = True
        continue
    if in_versions and stripped.startswith("["):
        in_versions = False
    if in_versions:
        match = re.fullmatch(r'([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"', stripped)
        if match and prerelease.search(match.group(2)):
            failures.append(f"gradle/libs.versions.toml: prerelease {match.group(1)}={match.group(2)} is forbidden")

if failures:
    print("STABLE_TOOLCHAIN_BASELINE: FAIL")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)
print("STABLE_TOOLCHAIN_BASELINE: PASS")
