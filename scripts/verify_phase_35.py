#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing file: {rel}")
        return ""
    data = path.read_bytes()
    if b"\x00" in data:
        errors.append(f"NUL byte in source: {rel}")
    return data.decode("utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


if not phase_version_at_least(ROOT, 35):
    errors.append("app version must be Phase 35 or later")

readme = read("README.md")
require(readme.count("# StudyFlow Android") == 1, "README must have one StudyFlow heading")
for required in [
    "implemented through **Phase 35**",
    "offline-first",
    "not an online synchronization service",
    "target-machine gates",
    "Developer diagnostics",
]:
    require(required in readme, f"README missing current product marker: {required}")
for stale in [
    "Roadmap Phases 0 through 29",
    "Future phases attach",
    "until Phase 9",
    "future release phases",
    "app-shell readiness section",
    "release-candidate polish",
]:
    require(stale not in readme, f"README still has stale product copy: {stale}")

changelog = read("CHANGELOG.md")
require(changelog.count("# Changelog") == 1, "CHANGELOG must have one top-level heading")
require(changelog.count("## Phase 34-35 interaction honesty and documentation cleanup") == 1,
        "CHANGELOG must contain exactly one Phase 34-35 entry")
require(changelog.count("## Phase 30-31 product truthfulness and dashboard action completion") == 1,
        "CHANGELOG must preserve exactly one Phase 30-31 entry")
require("Earlier delivery history" in changelog, "CHANGELOG must preserve history pointer")

validation = read("VALIDATION_REPORT.md")
for required in [
    "implemented through **Phase 35**",
    "scripts/verify_phase_35.py",
    "Target-machine gates",
    "offline-first",
    "Artifact hygiene contract",
]:
    require(required in validation, f"VALIDATION_REPORT missing marker: {required}")
for stale in ["Phase 17", "Phase 22/23 overlay", "Phase 29 r2", "Generated for the Phase"]:
    require(stale not in validation, f"VALIDATION_REPORT still has stale marker: {stale}")

for required in [
    "Devtool reserves `.devtool/` runtime metadata",
    "cleaned outside the overlay",
]:
    require(required in validation or required in readme, f"reserved Devtool-state boundary missing: {required}")

for rel in [
    "PHASE_34_INTERACTION_HONESTY.md",
    "PHASE_35_DOCUMENTATION_ARTIFACT_CLEANUP.md",
]:
    require((ROOT / rel).is_file(), f"missing phase documentation: {rel}")

for rel in [
    "feature/privacy/src/main/res/values/strings.xml",
    "feature/privacy/src/main/res/values-pt-rBR/strings.xml",
    "feature/settings/src/main/res/values/strings.xml",
    "feature/settings/src/main/res/values-pt-rBR/strings.xml",
]:
    text = read(rel)
    values = " ".join(node.text or "" for node in ET.fromstring(text) if "name" in node.attrib)
    for stale in ["cloud sync", "sincronização em nuvem", "Legacy rules", "Regras legadas"]:
        require(stale not in values, f"{rel} still has stale user-facing value: {stale}")

release = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseReadinessSeal.kt")
require("ReleaseReadinessPhase(35" in release and "Documentation and artifact cleanup" in release,
        "Release readiness seal must include Phase 35")
factory = read("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/ReleaseValidationSealFactory.kt")
require("CurrentPhase = 35" in factory and "Phase 0 through 35 validators pass." in factory,
        "Release validation factory must advance through Phase 35")
validate_repo = read("scripts/validate_repo.sh")
require("python3 scripts/verify_phase_35.py" in validate_repo,
        "validate_repo must run Phase 35 verifier")
build = read("build.gradle.kts")
require("through Phase 35" in build and "for phase in {0..35}; do" in build,
        "Gradle repository contract task must cover Phase 35")



require("Devtool reserves `.devtool/` runtime metadata" in readme and "cleaned outside the overlay" in validation,
        "Phase 35 must document reserved Devtool workspace-state cleanup outside overlay inventory")

if errors:
    print("PHASE_35_CONTRACT: FAILED")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PHASE_35_CONTRACT: PASSED (documentation and artifact cleanup)")
