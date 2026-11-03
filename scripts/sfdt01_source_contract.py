#!/usr/bin/env python3
"""SFDT-01: source-only Devtool/Actions inventory. Does not launch Gradle or write files."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
TARGETS = (
    "studyflow_android", "studyflow_desktop", "studyflow_web", "studyflow_tv",
    "studyflow_tv_release", "studyflow_release", "studyflow_quality", "studyflow_parity",
)
WORKFLOWS = (
    ".github/workflows/studyflow-ci.yml",
    ".github/workflows/devtool-android-build.yml",
    ".github/workflows/studyflow-hosted-release.yml",
    ".github/workflows/studyflow-hosted-sign.yml",
)
EXACT_COMMANDS = {
    "ci-actions-contract": ["python3", "scripts/devtool_actions_contract.py"],
    "ci-source-contract": ["python3", "scripts/verify_studyflow_actions.py", "--source-only"],
    "ci-sfdt-source-audit": ["python3", "scripts/sfdt01_source_contract.py", "--check"],
    "ci-github-gradle-policy": ["python3", "scripts/studyflow_ci_gradle_profile.py", "--check-source"],
}
STAGES = (
    ("actions-contract", "ci-actions-contract", ()),
    ("source-contract", "ci-source-contract", ("actions-contract",)),
    ("sfdt-source-audit", "ci-sfdt-source-audit", ("source-contract",)),
    ("github-gradle-policy", "ci-github-gradle-policy", ("sfdt-source-audit",)),
)


class SourceContractError(ValueError):
    pass


def require(test: bool, message: str) -> None:
    if not test:
        raise SourceContractError(message)


def audit(root: Path) -> dict:
    """Report read-only graph/config identities, refusing unsupported sources."""
    config_path = root / ".devtool.toml"
    require(config_path.is_file() and not config_path.is_symlink(), "Devtool config missing/symlinked")
    config = tomllib.loads(config_path.read_text(encoding="utf-8"))
    targets = config.get("targets", {})
    require(set(targets) == set(TARGETS), "StudyFlow's eight target identities drifted")
    for name in TARGETS:
        target = targets[name]
        require(isinstance(target, dict), f"Target configuration invalid: {name}")
        if name != "studyflow_web":
            require(target.get("root") == ".", f"Gradle execution root drifted: {name}")
        require(isinstance(target.get("inputs"), list) and bool(target["inputs"]), f"Missing target inputs: {name}")
    jobs = targets["studyflow_android"].get("jobs", {})
    for alias in ("ci-source", "ci-preflight"):
        workflow = targets["studyflow_android"].get("workflows", {}).get(alias)
        require(isinstance(workflow, list) and len(workflow) == len(STAGES), f"Unexpected {alias} DAG length")
        for step, (step_id, job_name, dependencies) in zip(workflow, STAGES):
            require(isinstance(step, dict) and step.get("id") == step_id
                    and step.get("ref") == f"job:{job_name}"
                    and step.get("depends_on") == list(dependencies), f"Unsafe {alias} step {step_id}")
            job = jobs.get(job_name)
            require(isinstance(job, dict), f"Missing local EXO job: {job_name}")
            command = job.get("command")
            require(command == EXACT_COMMANDS[job_name],
                    f"Unexpected source-only job argv: {job_name}")
            inputs = job.get("inputs")
            require(isinstance(inputs, list) and ".devtool.toml" in inputs
                    and command[1] in inputs and ".github/workflows/*.yml" in inputs,
                    f"Source-only job input ownership changed: {job_name}")
            require(not job.get("outputs") and not job.get("output_scopes"),
                    f"Source-only job unexpectedly writes outputs: {job_name}")
            metadata = job.get("metadata")
            require(isinstance(metadata, dict) and metadata.get("read_only") is True,
                    f"Local EXO job not read-only: {job_name}")
            relative_script = Path(command[1])
            require(not relative_script.is_absolute() and ".." not in relative_script.parts,
                    f"Unsafe source-only script path: {command[1]}")
            script = root / relative_script
            require(script.is_file() and not script.is_symlink()
                    and script.resolve().is_relative_to(root.resolve()),
                    f"Missing/unsafe source-only script: {command[1]}")
    wrapper = config.get("wrapper", {})
    require(wrapper.get("schema") == 2 and wrapper.get("default_command") == "ci-source", "Wrapper default/schema drift")
    commands = wrapper.get("commands", {})
    require(set(commands) == {"ci-source", "ci-preflight"}, "Local wrapper command allow-list drift")
    for alias in commands:
        require(commands[alias].get("target") == "studyflow_android" and commands[alias].get("workflow") == alias,
                f"Local wrapper command changed execution identity: {alias}")
    fingerprints = {}
    for relative in WORKFLOWS:
        path = root / relative
        require(path.is_file() and not path.is_symlink(), f"Workflow missing/symlinked: {relative}")
        contents = path.read_bytes()
        require(bool(contents.strip()), f"Empty workflow: {relative}")
        fingerprints[relative] = hashlib.sha256(contents).hexdigest()
    ci = (root / WORKFLOWS[0]).read_text(encoding="utf-8")
    require("workflow_dispatch:" in ci and "expected_sha:" in ci and "deep-contracts:" in ci,
            "Hosted CI source/deep gate drifted")
    require('test "$EXPECTED_SHA" = "$GITHUB_SHA"' in ci,
            "Mandatory manually dispatched SHA check removed")
    for invocation in ("python3 scripts/sfdt01_source_contract.py --check",
                       "python3 scripts/test_sfdt01_source_contract.py",
                       "python3 scripts/test_studyflow_github_signing.py",
                       "python3 scripts/test_studyflow_ci_gradle_profile.py"):
        require(invocation in ci, f"Hosted source verification missing: {invocation}")
    sign = (root / WORKFLOWS[3]).read_text(encoding="utf-8")
    require("environment: studyflow-signing" in sign and "secrets.STUDYFLOW_SIGNING_P12_BASE64" in sign,
            "Protected signer boundary drifted")
    return {
        "schema": "studyflow.sfdt01.source_contract.v1",
        "status": "SOURCE_ONLY_PASS",
        "hosted_run": "NOT_RUN",
        "qualified_release": False,
        "gradle_executed": False,
        "target_count": len(TARGETS),
        "source_workflows": sorted(("ci-source", "ci-preflight")),
        "source_exo_steps": [step[0] for step in STAGES],
        "exact_argv_enforced": True,
        "workflow_sha256": fingerprints,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="StudyFlow source-only Devtool graph/Actions audit")
    parser.add_argument("--check", action="store_true", help="verify source contracts and print verdict")
    parser.add_argument("--json", action="store_true", help="emit machine-readable source-only receipt")
    args = parser.parse_args()
    try:
        report = audit(ROOT)
    except (SourceContractError, KeyError, OSError, ValueError, TypeError) as exc:
        print(f"SFDT01_SOURCE_REFUSED: {exc}", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report, sort_keys=True, indent=2))
    else:
        print("SFDT01_SOURCE_ONLY_PASS: 8 targets, 4 source EXO stages, 4 hosted workflow identity hashes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
