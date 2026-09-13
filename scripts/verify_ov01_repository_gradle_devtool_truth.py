#!/usr/bin/env python3
"""Behavioral/source contract for OV01 repository, Gradle and Devtool truth remediation."""
from __future__ import annotations

import json
import re
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing file: {rel}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


build = text("build.gradle.kts")
app_build = text("app/build.gradle.kts")
core_data = text("core/data/build.gradle.kts")
android_convention = text("build-logic/convention/src/main/kotlin/AndroidConvention.kt")
android_app_convention = text("build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt")
android_library_convention = text("build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt")
build_logic = text("build-logic/convention/build.gradle.kts")
properties = text("gradle.properties")
devtool_text = text(".devtool.toml")
gitignore = text(".gitignore")
wrapper = text("gradlew")
wrapper_bat = text("gradlew.bat")
release_gate = text("scripts/ov01_release_gate.py")
prune = text("scripts/ov01_prune_stale_devtool_state.py")
refresh = text("scripts/refresh_gradle_dependency_integrity.sh")
finalizer = text("scripts/ov01_finalize_target_state.sh")
integrity = text("scripts/verify_gradle_dependency_integrity.py")

# S0-01
require("doLast {" not in build, "S0-01: root build still contains doLast execution closures")
require("gradle.projectsEvaluated {" not in build, "S0-01: root build still uses projectsEvaluated")
require(
    'tasks.matching { it.name == "testDebugUnitTest" }.configureEach' in build,
    "S0-01: lazy cross-project task ordering is missing",
)
require("org.gradle.configuration-cache=true" in properties, "configuration cache unexpectedly disabled")

# S0-02
require(
    'tasks.register<Exec>("verifyPhoneReleaseSigningPreflight")' in app_build,
    "S0-02: phone signing preflight task is missing",
)
require(
    "dependsOn(verifyPhoneReleaseSigningPreflight)" in app_build,
    "S0-02: Release packaging does not depend on signing preflight",
)
require(
    "mustRunAfter(verifyPhoneReleaseSigningPreflight)" in app_build,
    "S0-02: Release graph is not ordered behind signing preflight",
)
require(
    '":app:verifyPhoneReleaseSigningPreflight"' in build,
    "S0-02: host release gate does not own phone signing preflight",
)
require(
    '"signing"' in release_gate and "SIGNING_VARS" in release_gate,
    "S0-02: signing preflight implementation is missing",
)

# S0-03
repo_match = re.search(r'tasks\.register\("verifyRepositoryContracts"\) \{(?P<body>.*?)\n\}', build, re.S)
require(repo_match is not None, "S0-03: dependency-only verifyRepositoryContracts task missing")
if repo_match:
    body = repo_match.group("body")
    require(
        "commandLine(" not in body and "bash" not in body,
        "S0-03: aggregate still hides verification inside an Exec/shell command",
    )
    for owner in (
        "verifyAndroidFocusShellFinalSeal",
        "verifyPhase56_57ReleaseReadiness",
        "verifyPhase58_59WarningHygiene",
        "verifyPhase60_61ContentReportSeal",
        "verifyPhase62OnboardingSeal",
        "verifyPhase63_64ExamRunbookSprintSeal",
        "verifyPostPhase64FinalValidationRefresh",
        "verifyFinalSourceAcceptance",
        "verifyRemediationOv13",
    ):
        require(owner in body, f"S0-03: aggregate lost explicit owner {owner}")
for script in (
    "verify_android_focus_shell_r2.py",
    "verify_final_app_acceptance.py",
    "verify_phase_60_61.py",
    "verify_phase_62.py",
    "verify_phase_63_64.py",
    "verify_post_phase_64_final_validation.py",
    "verify_remediation_ov_13.py",
):
    require(
        build.count(f"scripts/{script}") == 1,
        f"S0-03: {script} must have exactly one Gradle Exec owner",
    )

# S0-04
require("tasks.withType<Exec>().configureEach" in build, "S0-04: root Exec I/O policy missing")
for token in (
    "inputs.files(repositoryVerificationInputs)",
    "withPathSensitivity(PathSensitivity.RELATIVE)",
    'inputs.property("verificationCommand"',
    "outputs.file(stampFile)",
    "outputs.upToDateWhen { false }",
    'outputs.cacheIf("source-only repository verifier")',
    "scripts/run_gradle_verifier.py",
):
    require(token in build, f"S0-04: missing Exec boundary token {token}")

# S0-05 and S0-06
manifest_path = ROOT / "devtool-overlay.json"
require(manifest_path.is_file(), "S0-05: root devtool-overlay.json missing")
if manifest_path.is_file():
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        require(
            manifest.get("name") == "studyflow_ov01_repository_gradle_devtool_truth_baseline_v1",
            "S0-05: root devtool-overlay.json is not the OV01 manifest",
        )
        inventory_paths = [entry.get("path", "") for entry in manifest.get("inventory", {}).get("entries", [])]
        require(
            not any(path.startswith(".devtool/") for path in inventory_paths),
            "S0-06: overlay illegally inventories Devtool runtime state",
        )
    except Exception as exc:
        FAILURES.append(f"S0-05: invalid devtool-overlay.json: {exc}")
for ignored in (
    "**/.devtool/artifacts/registry.json",
    "**/.devtool/artifacts/registry.lock",
    "**/.devtool/test-history.json",
):
    require(ignored in gitignore, f"S0-06: missing ignore rule {ignored}")
for expected in (
    ".devtool/artifacts/registry.json",
    ".devtool/artifacts/registry.lock",
    ".devtool/test-history.json",
):
    require(expected in prune, f"S0-06: cleanup does not own {expected}")
require(
    "rmtree" not in prune and "glob(" not in prune,
    "S0-06: runtime cleanup is broader than the known stale files",
)
require(
    "ov01_prune_stale_devtool_state.py" in finalizer and "git add -u" in finalizer,
    "S0-06: target-state finalizer does not record stale tracked runtime-state cleanup",
)

# S0-07 and S0-08
try:
    devtool = tomllib.loads(devtool_text)
except Exception as exc:
    FAILURES.append(f"invalid .devtool.toml: {exc}")
    devtool = {}
groups = devtool.get("workspace", {}).get("groups", {})
require(
    groups.get("everything") == ["@clients", "@release", "@quality"],
    "S0-07: @everything must include @quality",
)
require(
    "-Xmx2048m" in properties and "MaxMetaspaceSize=768m" in properties,
    "S0-08: direct Gradle memory policy differs from Devtool",
)
require(
    "org.gradle.parallel=true" in properties and "org.gradle.workers.max=4" in properties,
    "S0-08: direct Gradle worker/parallel policy differs from Devtool",
)
for target_name in ("studyflow_android", "studyflow_tv", "studyflow_tv_release"):
    target = devtool.get("targets", {}).get(target_name, {})
    gradle = target.get("gradle", {}) if isinstance(target, dict) else {}
    validation = target.get("validation", {}) if isinstance(target, dict) else {}
    require(
        gradle.get("max_workers") == 4 and gradle.get("parallel") is True,
        f"S0-08: {target_name} Gradle resource policy drifted",
    )
    require(
        validation.get("gradle_heap_mb") == 2048 and validation.get("gradle_metaspace_mb") == 768,
        f"S0-08: {target_name} validation memory policy drifted",
    )

# S0-09
require(
    "gradlew start up script for POSIX generated by Gradle" in wrapper,
    "S0-09: POSIX wrapper is not a generated Gradle launcher",
)
require(
    len(wrapper.splitlines()) >= 100 and "cygpath" in wrapper and "ulimit" in wrapper and "xargs" in wrapper,
    "S0-09: POSIX wrapper is still a minimal custom launcher",
)
require(
    "gradlew startup script for Windows" in wrapper_bat and len(wrapper_bat.splitlines()) >= 50,
    "S0-09: Windows wrapper is still a minimal custom launcher",
)

# S0-10
require(
    "coreLibraryDesugaring(" not in core_data and "isCoreLibraryDesugaringEnabled" not in core_data,
    "S0-10: core:data still duplicates desugaring ownership",
)
require(
    "coreLibraryDesugaring" in android_app_convention and "coreLibraryDesugaring" in android_library_convention,
    "S0-10: Android convention plugins no longer own coreLibraryDesugaring",
)

# S0-11
require(
    "dependencyLocking" in build and "lockAllConfigurations()" in build,
    "S0-11: main Gradle dependency locking is absent",
)
require(
    "dependencyLocking" in build_logic and "lockAllConfigurations()" in build_logic,
    "S0-11: included build dependency locking is absent",
)
require(
    "--write-locks" in refresh and "--write-verification-metadata sha256" in refresh,
    "S0-11: canonical lock/checksum generation path is absent",
)
require(
    "--require-materialized" in release_gate and "verify_gradle_dependency_integrity.py" in release_gate,
    "S0-11: Release host gate does not require materialized integrity state",
)
require(
    "<sha256" in integrity and "gradle.lockfile" in integrity,
    "S0-11: materialized integrity verifier does not inspect checksums/locks",
)
require(
    "refresh_gradle_dependency_integrity.sh" in finalizer and "git commit --amend --no-edit" in finalizer,
    "S0-11: target-state finalizer does not materialize and commit generated integrity state",
)

# S0-12
require(
    "android.sync.suppressAgpWarnings" not in properties,
    "S0-12: broad AGP warning suppression is still enabled",
)

if FAILURES:
    print("OV01_REPOSITORY_GRADLE_DEVTOOL_TRUTH: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)
print("OV01_REPOSITORY_GRADLE_DEVTOOL_TRUTH: PASS")
