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
    "parity-contracts": ("studyflow_parity", "build"),
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
    tasks = selected["tasks"][operation]
    if not isinstance(tasks, list) or not tasks or any(not isinstance(t, str) or not TASK.fullmatch(t) for t in tasks):
        raise ValueError(f"Invalid Gradle task list: {target}#{operation}")
    tasks = list(dict.fromkeys(tasks))
    return {"schema": "studyflow.devtool.github-gradle-plan.v1", "profile": profile,
            "target": target, "operation": operation, "tasks": tasks,
            "config_sha256": hashlib.sha256(source).hexdigest(),
            "execution_authority": "GitHub Actions only", "qualified_release": False}


def source_check() -> None:
    for profile in PROFILES:
        profile_plan(profile)
    android = (ROOT / ".github/workflows/devtool-android-build.yml").read_text()
    ci = (ROOT / ".github/workflows/studyflow-ci.yml").read_text()
    for fragment in ("expected_sha:", 'required: true', 'test "$EXPECTED_SHA" = "$GITHUB_SHA"', "actions/upload-artifact@v4"):
        if fragment not in android:
            raise ValueError(f"Android workflow lacks source/receipt control: {fragment}")
    for fragment in ("expected_sha:", "deep-contracts:", "studyflow_ci_gradle_profile.py", "if: always()", "if-no-files-found: error"):
        if fragment not in ci:
            raise ValueError(f"CI workflow lacks Devtool profile control: {fragment}")
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
    flags.add_argument("--run", choices=sorted(PROFILES))
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    try:
        if args.check_source:
            source_check()
        elif args.plan:
            print(json.dumps(profile_plan(args.plan), indent=2))
        else:
            receipt = args.receipt or Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "studyflow-devtool-profile.json"
            return run(args.run, receipt)
    except (KeyError, ValueError, OSError, subprocess.SubprocessError, tomllib.TOMLDecodeError) as exc:
        print(f"STUDYFLOW_DEVTOOL_CI_PROFILE: FAIL: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
