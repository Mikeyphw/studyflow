#!/usr/bin/env python3
"""Validate StudyFlow's Gradle dependency provenance/locking contract."""
from __future__ import annotations

import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


parser = argparse.ArgumentParser()
parser.add_argument("--require-materialized", action="store_true")
args = parser.parse_args()

root_build = text("build.gradle.kts")
build_logic = text("build-logic/convention/build.gradle.kts")
wrapper = text("gradle/wrapper/gradle-wrapper.properties")
refresh = text("scripts/refresh_gradle_dependency_integrity.sh")

require(
    "dependencyLocking" in root_build and "lockAllConfigurations()" in root_build,
    "main build must lock all resolvable configurations",
)
require(
    "dependencyLocking" in build_logic and "lockAllConfigurations()" in build_logic,
    "included build-logic must lock all resolvable configurations",
)
require(
    re.search(r"(?m)^distributionSha256Sum=[0-9a-f]{64}$", wrapper) is not None,
    "wrapper distribution SHA-256 must be pinned",
)
require("--write-locks" in refresh, "integrity refresh must materialize dependency locks")
require(
    "--write-verification-metadata sha256" in refresh,
    "integrity refresh must materialize SHA-256 verification metadata",
)
require(
    "verify_gradle_dependency_integrity.py --require-materialized" in refresh,
    "integrity refresh must fail closed after generation",
)

# Forbid common dynamic selectors in build declarations. A dependency-integrity seal is not
# meaningful while versions can float independently from the reviewed source/lock state.
dynamic_patterns = (
    re.compile(
        r'''["'][^"'\n:\s]+:[^"'\n:\s]+:[^"'\n]*(?:\+|SNAPSHOT|latest\.(?:release|integration)|nightly)[^"'\n]*["']''',
        re.I,
    ),
)
for path in list(ROOT.rglob("*.gradle.kts")) + [ROOT / "gradle/libs.versions.toml"]:
    if ".gradle" in path.parts or "build" in path.parts:
        continue
    source = path.read_text(encoding="utf-8")
    for pattern in dynamic_patterns:
        if pattern.search(source):
            FAILURES.append(f"dynamic dependency selector forbidden: {path.relative_to(ROOT)}")
            break

if args.require_materialized:
    metadata_files = (
        ROOT / "gradle/verification-metadata.xml",
        ROOT / "build-logic/gradle/verification-metadata.xml",
    )
    for path in metadata_files:
        require(path.is_file(), f"missing generated verification metadata: {path.relative_to(ROOT)}")
        if path.is_file():
            try:
                tree = ET.parse(path)
                xml = path.read_text(encoding="utf-8")
                require(
                    tree.getroot().tag.endswith("verification-metadata"),
                    f"invalid verification metadata root: {path.relative_to(ROOT)}",
                )
                require(
                    "<sha256" in xml,
                    f"verification metadata has no SHA-256 checksums: {path.relative_to(ROOT)}",
                )
                require(
                    "<component" in xml,
                    f"verification metadata has no resolved components: {path.relative_to(ROOT)}",
                )
            except ET.ParseError as exc:
                FAILURES.append(f"invalid XML {path.relative_to(ROOT)}: {exc}")

    lockfiles = sorted(ROOT.rglob("gradle.lockfile"))
    require(bool(lockfiles), "no generated Gradle lockfiles were found")
    require(
        any("build-logic" in path.parts for path in lockfiles),
        "included build-logic dependency lockfile is missing",
    )
    require(
        any("build-logic" not in path.parts for path in lockfiles),
        "main build dependency lockfiles are missing",
    )
    for path in lockfiles:
        require(
            bool(path.read_text(encoding="utf-8").strip()),
            f"empty dependency lockfile: {path.relative_to(ROOT)}",
        )

if FAILURES:
    print("GRADLE_DEPENDENCY_INTEGRITY: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)
print("GRADLE_DEPENDENCY_INTEGRITY: PASS")
