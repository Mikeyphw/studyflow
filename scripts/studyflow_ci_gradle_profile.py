#!/usr/bin/env python3
"""GitHub-only execution of reviewable Gradle tasks from StudyFlow Devtool target config.

Local --plan/--check-source are strictly read-only and never invoke Gradle.
This is an Actions bridge, not an EXO receipt or release qualification.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
PROFILES = {
    "android-contracts": ("studyflow_android", "test"),
    "tv-contracts": ("studyflow_tv", "test"),
    "desktop-contracts": ("studyflow_desktop", "test"),
    "quality-contracts": ("studyflow_quality", "build"),
    "parity-contracts": ("studyflow_parity", "validation.phases.preflight"),
}
TASK = re.compile(r"(?:(?::[A-Za-z][A-Za-z0-9_-]*)+:)?[A-Za-z][A-Za-z0-9_-]*\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")


def profile_plan(profile: str) -> dict:
    config_path = ROOT / ".devtool.toml"
    if config_path.is_symlink():
        raise ValueError("Refusing symlinked Devtool config")
    source = config_path.read_bytes()
    config = tomllib.loads(source.decode("utf-8"))
    target, operation = PROFILES[profile]
    selected = config["targets"][target]
    if selected.get("root") != ".":
        raise ValueError(f"Unsafe target root: {target}")
    expected_runner = "android" if profile in {"android-contracts", "tv-contracts"} else "gradle"
    if selected.get("runner") != expected_runner:
        raise ValueError(f"Unexpected Devtool runner: {target}")
    if operation == "validation.phases.preflight":
        if profile != "parity-contracts":
            raise ValueError("Only parity CI may select the source-only native preflight")
        tasks = selected["validation"]["phases"]["preflight"]
    else:
        tasks = selected["tasks"][operation]
    if not isinstance(tasks, list) or not tasks or any(not isinstance(t, str) or not TASK.fullmatch(t) for t in tasks):
        raise ValueError(f"Invalid Gradle task list: {target}#{operation}")
    tasks = list(dict.fromkeys(tasks))
    if profile == "parity-contracts":
        if "verifyRelFinalRoadmapPromiseAudit" not in tasks or "verifyOv30R2AllTargetProperValidationSeals" not in tasks:
            raise ValueError("Unsigned parity plan lost canonical Devtool preflight coverage")
        if "verifyRelFinalTerminalSeal" in tasks or any(t.startswith((":app:assembleRelease", ":app:bundleRelease", ":app:packageRelease", ":app:signRelease")) for t in tasks):
            raise ValueError("Unsigned parity plan includes release-qualified packaging or terminal seal")
    if profile == "android-contracts":
        # The phone module sets testBuildType="release". Release JVM *tests*
        # are not signed APK/AAB builds. The real signed-output gate stays in
        # app/build.gradle.kts, where packageReleaseResources is exempted.
        phone_gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        if not re.search(r'testBuildType\s*=\s*"release"', phone_gradle):
            raise ValueError("Phone JVM task selection disagrees with testBuildType")
        if ":app:testReleaseUnitTest" not in tasks or ":app:testDebugUnitTest" in tasks:
            raise ValueError("Unsigned Android profile must use the configured Release JVM test task")
        for task in tasks:
            if not task.startswith(":app:"):
                continue
            name = task.rsplit(":", 1)[-1]
            if (name.startswith(("assembleRelease", "bundleRelease", "packageRelease",
                                 "signRelease", "publishRelease"))
                    or (name.startswith("test") and "Release" in name
                        and name != "testReleaseUnitTest")):
                raise ValueError("Unsigned Android profile requested a Release packaging task")
    return {"schema": "studyflow.devtool.github-gradle-plan.v1", "profile": profile,
            "target": target, "operation": operation, "tasks": tasks,
            "config_sha256": hashlib.sha256(source).hexdigest(),
            "execution_authority": "GitHub Actions only", "qualified_release": False}



# The Android deep Gradle plan depends on the shared Web pytest task. A clean
# GitHub setup-python 3.12 runner does not include pytest: Pillow alone is not
# sufficient. Keep every pytest runtime dependency pinned and installed before
# Gradle in only the hosted android-contracts profile.
DEEP_WEB_PYTEST_PINS = (
    "pytest==9.0.2",
    "iniconfig==2.3.0",
    "packaging==25.0",
    "pluggy==1.6.0",
    "Pygments==2.20.0",
)


def validate_deep_web_pytest_hosted_prereqs(ci: str, requirements: str) -> None:
    """Fail closed on test omissions, unpinned wheels, wrong job or late setup."""
    actual = tuple(line.strip() for line in requirements.splitlines()
                   if line.strip() and not line.lstrip().startswith("#"))
    if actual != DEEP_WEB_PYTEST_PINS:
        raise ValueError("Deep Web pytest runtime pins differ from reviewed exact dependency set")
    marker = "      - name: Install pinned pytest for deep Android Web regression\n"
    if ci.count(marker) != 1 or "  deep-contracts:\n" not in ci:
        raise ValueError("Missing or duplicated deep Android pytest prerequisite step")
    deep = ci.split("  deep-contracts:\n", 1)[1]
    if marker not in deep:
        raise ValueError("Pytest setup must be under deep-contracts")
    begin = deep.index(marker)
    end = deep.find("\n      - ", begin + len(marker))
    step = deep[begin:] if end < 0 else deep[begin:end]
    required = (
        "        if: ${{ matrix.profile == 'android-contracts' }}\n",
        "        shell: bash\n",
        "          set -euo pipefail\n",
        "          python3 -m pip install --disable-pip-version-check --no-deps "
        "--only-binary=:all: -r web/requirements-deep-pytest.txt\n",
        "          python3 -m pytest --version",
    )
    if any(part not in step for part in required):
        raise ValueError("Deep Android pytest prerequisites missing pin, safe install or profile scope")
    if "continue-on-error:" in step or "|| true" in step:
        raise ValueError("Deep Android pytest dependency install must fail closed")
    build_marker = "      - name: Run canonical StudyFlow Devtool Gradle target profile\n"
    if build_marker not in deep or begin >= deep.index(build_marker):
        raise ValueError("Pytest must be installed before deep native Gradle tasks")
    setup_marker = "      - uses: actions/setup-python@v5\n"
    if setup_marker not in deep or begin <= deep.index(setup_marker):
        raise ValueError("Pytest install must follow pinned setup-python")
    # All Web tests remain enabled; specifically preserve function-style pytest
    # regression tests, which unittest discovery would silently omit.
    gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    if 'commandLine("python3", "-m", "pytest", "tests", "-q")' not in gradle:
        raise ValueError("Full Web pytest regression task removed from hosted Gradle graph")


def source_check() -> None:
    for profile in PROFILES:
        profile_plan(profile)
    android = (ROOT / ".github/workflows/devtool-android-build.yml").read_text()
    ci = (ROOT / ".github/workflows/studyflow-ci.yml").read_text()
    for fragment in ("expected_sha:", 'required: true', 'test "$EXPECTED_SHA" = "$GITHUB_SHA"', "actions/upload-artifact@v4"):
        if fragment not in android:
            raise ValueError(f"Android workflow lacks source/receipt control: {fragment}")
    for fragment in ("expected_sha:", "deep-contracts:", "studyflow_ci_gradle_profile.py", "if: always()", "if-no-files-found: warn", "--init-receipt"):
        if fragment not in ci:
            raise ValueError(f"CI workflow lacks Devtool profile control: {fragment}")
    requirements = (ROOT / "web/requirements-test.txt").read_text(encoding="utf-8")
    if [line.strip() for line in requirements.splitlines() if line.strip() and not line.lstrip().startswith("#")] != ["Pillow==12.0.0"]:
        raise ValueError("Web Pillow test dependency must be pinned and reviewable")
    for fragment in ("actions/setup-python@v5", "python-version: '3.12'",
                     "-r requirements-test.txt", "-r web/requirements-test.txt"):
        if fragment not in ci:
            raise ValueError(f"Hosted CI lacks pinned Python test prerequisite: {fragment}")
    validate_deep_web_pytest_hosted_prereqs(
        ci, (ROOT / "web/requirements-deep-pytest.txt").read_text(encoding="utf-8")
    )
    for marker in ("matrix.profile == 'android-contracts' || matrix.profile == 'parity-contracts'",
                   "npm ci", "npm run build"):
        if marker not in ci:
            raise ValueError(f"Hosted CI missing native pinned TypeScript environment: {marker}")
    # No local launcher/wrapper alias is allowed to run Gradle as a CI shortcut.
    config = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    commands = config.get("wrapper", {}).get("commands", {})
    if set(commands) != {"ci-preflight", "ci-source"}:
        raise ValueError(f"Unexpected local Devtool wrapper commands: {sorted(commands)}")
    if config.get("wrapper", {}).get("default_command") != "ci-source":
        raise ValueError("Local Devtool wrapper default must be source-only")
    for command in commands.values():
        if command.get("target") != "studyflow_android" or command.get("workflow") not in {"ci-preflight", "ci-source"}:
            raise ValueError("Wrapper command must use a source-only workflow")
    for path in ("settings.gradle.kts", "build-logic/settings.gradle.kts"):
        settings = (ROOT / path).read_text(encoding="utf-8")
        for marker in ('System.getenv("GITHUB_ACTIONS") != "true"', 'System.getenv("CI") != "true"',
                       'System.getenv("RUNNER_OS") != "Linux"', 'STUDYFLOW_GITHUB_ONLY_GRADLE'):
            if marker not in settings:
                raise ValueError(f"Missing local Gradle settings guard in {path}: {marker}")
    print("STUDYFLOW_GITHUB_ONLY_GRADLE_SOURCE_CONTRACT: PASS (no Gradle execution)")


def git_head() -> str:
    proc = subprocess.run(["git", "-c", f"safe.directory={ROOT}", "-C", str(ROOT), "rev-parse", "HEAD"],
                          capture_output=True, text=True, check=True, timeout=10)
    sha = proc.stdout.strip().lower()
    if not SHA.fullmatch(sha):
        raise ValueError("Invalid Git source SHA")
    return sha


def init_receipt(profile: str, receipt_file: Path) -> None:
    """Record a source-bound, non-build initial receipt *before* runner setup.

    Remains available when setup-java, Android SDK, or setup-gradle fails.
    Does not execute Gradle and does not claim a completed result.
    """
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("CI") != "true":
        raise ValueError("Hosted receipt initialization requires GitHub Actions")
    expected_sha = os.environ.get("GITHUB_SHA", "").lower()
    if not SHA.fullmatch(expected_sha) or git_head() != expected_sha:
        raise ValueError("GitHub Actions checkout/source SHA mismatch")
    if receipt_file.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError("Receipt path must be outside checked-out sources")
    receipt_file.parent.mkdir(parents=True, exist_ok=True)
    receipt = {**profile_plan(profile), "git_sha": expected_sha,
               "github_run_id": os.environ.get("GITHUB_RUN_ID", ""),
               "github_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
               "created_at_utc": datetime.now(timezone.utc).isoformat(),
               "status": "blocked_before_gradle", "failure_stage": "hosted_prerequisites",
               "exit_code": None, "build_executed": False, "native_exo_gradle_execution": False}
    receipt_file.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")


def run(profile: str, receipt_file: Path) -> int:
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("CI") != "true":
        raise ValueError("Gradle execution forbidden outside GitHub Actions")
    if os.environ.get("RUNNER_OS") != "Linux" or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
        raise ValueError("StudyFlow deep Gradle profiles require GitHub Actions manual Linux dispatch")
    if not re.fullmatch(r"[0-9]+", os.environ.get("GITHUB_RUN_ID", "")) or not re.fullmatch(r"[1-9][0-9]*", os.environ.get("GITHUB_RUN_ATTEMPT", "")):
        raise ValueError("StudyFlow deep Gradle profiles require a valid hosted run identity")
    expected_sha = os.environ.get("GITHUB_SHA", "").lower()
    if not SHA.fullmatch(expected_sha) or git_head() != expected_sha:
        raise ValueError("GitHub Actions checkout/source SHA mismatch")
    plan = profile_plan(profile)
    gradlew = ROOT / "gradlew"
    if not gradlew.is_file() or gradlew.is_symlink():
        raise ValueError("Missing regular Gradle wrapper")
    if receipt_file.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError("Receipt path must be outside checked-out sources")
    receipt_file.parent.mkdir(parents=True, exist_ok=True)
    receipt = {**plan, "git_sha": expected_sha, "github_run_id": os.environ.get("GITHUB_RUN_ID", ""),
               "github_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
               "started_at_utc": datetime.now(timezone.utc).isoformat(),
               "status": "started", "exit_code": None, "build_executed": False}
    receipt_file.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
    command = [str(gradlew), "--no-daemon", "--max-workers=2", *plan["tasks"]]
    # Evidence describes the exact hosted command, not a native Devtool EXO Gradle receipt.
    receipt["gradle_argv"] = ["./gradlew", *command[1:]]
    receipt["native_exo_gradle_execution"] = False
    receipt_file.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
    print(f"StudyFlow Devtool profile: {profile} ({len(plan['tasks'])} distinct tasks)", flush=True)
    exit_code = 1
    try:
        receipt["build_executed"] = True
        receipt_file.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
        exit_code = subprocess.run(command, cwd=ROOT, check=False).returncode
        return exit_code
    finally:
        receipt.update(exit_code=exit_code, status="passed" if exit_code == 0 else "failed",
                       finished_at_utc=datetime.now(timezone.utc).isoformat())
        receipt_file.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    flags = parser.add_mutually_exclusive_group(required=True)
    flags.add_argument("--check-source", action="store_true")
    flags.add_argument("--plan", choices=sorted(PROFILES))
    flags.add_argument("--init-receipt", choices=sorted(PROFILES))
    flags.add_argument("--run", choices=sorted(PROFILES))
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    try:
        if args.check_source:
            source_check()
        elif args.plan:
            print(json.dumps(profile_plan(args.plan), indent=2))
        elif args.init_receipt:
            receipt = args.receipt or Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "studyflow-devtool-profile.json"
            init_receipt(args.init_receipt, receipt)
        else:
            receipt = args.receipt or Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "studyflow-devtool-profile.json"
            return run(args.run, receipt)
    except (KeyError, ValueError, OSError, subprocess.SubprocessError, tomllib.TOMLDecodeError) as exc:
        print(f"STUDYFLOW_DEVTOOL_CI_PROFILE: FAIL: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
