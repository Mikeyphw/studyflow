#!/usr/bin/env python3
"""SFDT18: fail-closed StudyFlow five-lane GitHub Actions dispatch coordinator.

Requires an already authenticated local `gh` CLI. Never executes Gradle, signs,
changes CI authority, uploads code, or grants parity. API inputs are verified
against independently reviewed workflow IDs and paths before submission.

Typical workflow after this source is merged into StudyFlow main:
    python3 -B scripts/sfdt18_evidence_coordinator.py dispatch
    python3 -B scripts/sfdt18_evidence_coordinator.py status
    python3 -B scripts/sfdt18_evidence_coordinator.py finalize
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from typing import Any, Callable

REPO = "Mikeyphw/studyflow"
SOURCE_SHA_RE = re.compile(r"[a-f0-9]{40}\Z")
DEFAULT_DEVTOOL_SHA = "bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c"
SCHEMA = "studyflow.sfdt18.hosted-dispatch.v1"
LANES = ("legacy", "source_shadow", "expanded_shadow", "native_fast", "native_deep")
POLICY: dict[str, tuple[int, str, str]] = {
    "legacy": (402184638, "studyflow-ci.yml", "StudyFlow CI"),
    "source_shadow": (406396373, "studyflow-devtool-shadow.yml", "StudyFlow Devtool native source shadow"),
    "expanded_shadow": (406444033, "studyflow-devtool-native-expanded.yml", "StudyFlow Devtool expanded native hosted shadow"),
    "native_fast": (406507103, "studyflow-devtool-native-coverage.yml", "StudyFlow Devtool native coverage expansion"),
    "native_deep": (406507103, "studyflow-devtool-native-coverage.yml", "StudyFlow Devtool native coverage expansion"),
}
EVIDENCE_WORKFLOW = ("studyflow-sfdt17-hosted-equivalence.yml",
                     "StudyFlow SFDT17 hosted archive and coverage evidence")


class Refused(RuntimeError):
    """Fail closed, preserving authoritative StudyFlow CI and existing state."""


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Refused(message)


def reviewed_path(lane: str) -> str:
    return ".github/workflows/" + POLICY[lane][1]


def validate_workflow_catalog(workflows: list[dict]) -> int:
    require(isinstance(workflows, list), "GitHub workflow catalog is missing")
    by_id = {v.get("id"): v for v in workflows if isinstance(v, dict)}
    for lane, (workflow_id, filename, name) in POLICY.items():
        row = by_id.get(workflow_id)
        require(isinstance(row, dict) and row.get("path") == reviewed_path(lane)
                and row.get("name") == name and row.get("state") == "active",
                f"{lane}: reviewed workflow ID/path/name/active-state changed")
    candidates = [v for v in workflows if isinstance(v, dict)
                  and v.get("path") == ".github/workflows/" + EVIDENCE_WORKFLOW[0]
                  and v.get("name") == EVIDENCE_WORKFLOW[1] and v.get("state") == "active"]
    require(len(candidates) == 1 and type(candidates[0].get("id")) is int
            and candidates[0]["id"] > 0,
            "SFDT17 evidence workflow not uniquely available on main")
    return candidates[0]["id"]


def choose_legacy_run(runs: list[dict], sha: str) -> int:
    """Pick an actual successful push-triggered authoritative CI run at SHA."""
    require(SOURCE_SHA_RE.fullmatch(sha) is not None, "Invalid source SHA")
    candidates = [r for r in runs if isinstance(r, dict)
                  and type(r.get("id")) is int
                  and r.get("workflow_id") == POLICY["legacy"][0]
                  and r.get("head_sha") == sha
                  and r.get("head_branch") == "main"
                  and r.get("path") == reviewed_path("legacy")
                  and r.get("event") == "push"
                  and r.get("status") == "completed"
                  and r.get("conclusion") == "success"]
    require(bool(candidates), "No successful main push StudyFlow CI at exact source SHA")
    return max(r["id"] for r in candidates)


def fresh_dispatch_ids(runs: list[dict], before_id: int, sha: str,
                       workflow_id: int) -> list[int]:
    require(type(before_id) is int and before_id >= 0, "Invalid dispatch cursor")
    require(SOURCE_SHA_RE.fullmatch(sha) is not None, "Invalid dispatch SHA")
    return sorted({r["id"] for r in runs if isinstance(r, dict)
                   and type(r.get("id")) is int and r["id"] > before_id
                   and r.get("workflow_id") == workflow_id
                   and r.get("head_sha") == sha
                   and r.get("head_branch") == "main"
                   and r.get("event") == "workflow_dispatch"})


def require_unique_dispatch(runs: list[dict], before_id: int, sha: str, workflow_id: int) -> int | None:
    ids = fresh_dispatch_ids(runs, before_id, sha, workflow_id)
    require(len(ids) <= 1, "Ambiguous concurrent workflow dispatches; cannot safely attribute a run")
    return ids[0] if ids else None


def check_run(run: dict, *, lane: str, sha: str, run_id: int) -> int:
    require(lane in POLICY, "Unreviewed StudyFlow lane")
    require(SOURCE_SHA_RE.fullmatch(sha) is not None, "Invalid source SHA")
    wid, _, name = POLICY[lane]
    require(isinstance(run, dict) and run.get("id") == run_id,
            f"{lane}: wrong GitHub Actions run ID")
    require(run.get("workflow_id") == wid and run.get("path") == reviewed_path(lane)
            and run.get("name") == name, f"{lane}: wrong workflow definition identity")
    require(run.get("head_sha") == sha and run.get("head_branch") == "main",
            f"{lane}: wrong source commit/branch")
    require(run.get("event") == ("push" if lane == "legacy" else "workflow_dispatch"),
            f"{lane}: wrong GitHub event")
    require(run.get("repository", {}).get("full_name") == REPO,
            f"{lane}: wrong GitHub repository")
    attempt = run.get("run_attempt")
    require(type(attempt) is int and attempt > 0, f"{lane}: invalid run attempt")
    return attempt


def checked_inputs(sha: str, run_ids: dict[str, int],
                   runs: dict[str, dict]) -> tuple[dict, dict[str, str]]:
    require(SOURCE_SHA_RE.fullmatch(sha) is not None, "Invalid main commit")
    require(set(run_ids) == set(LANES) and set(runs) == set(LANES),
            "Exactly five completed runs must be recorded")
    require(all(type(v) is int and v > 0 for v in run_ids.values())
            and len(set(run_ids.values())) == len(LANES), "Invalid/duplicate run IDs")
    identities = {}
    for lane in LANES:
        attempt = check_run(runs[lane], lane=lane, sha=sha, run_id=run_ids[lane])
        require(runs[lane].get("status") == "completed"
                and runs[lane].get("conclusion") == "success",
                f"{lane}: hosted workflow not completed successfully")
        identities[lane] = {"workflow_id": POLICY[lane][0],
                            "workflow_path": reviewed_path(lane), "attempt": attempt}
    return identities, {
        "expected_sha": sha,
        "expected_identities_json": json.dumps(identities, sort_keys=True, separators=(",", ":")),
        **{lane + "_run": str(run_ids[lane]) for lane in LANES},
    }


def gh(*args: str) -> str:
    cmd = ["gh", *args]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, check=False,
                              timeout=90, encoding="utf-8")
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise Refused(f"GitHub CLI execution failed: {exc}") from None
    require(proc.returncode == 0,
            f"gh {args[0] if args else '<missing>'} failed: {proc.stderr.strip()[:1200]}")
    return proc.stdout


def gh_json(endpoint: str) -> dict:
    require(endpoint.startswith("repos/" + REPO + "/"),
            "GitHub API restricted to reviewed StudyFlow repository")
    try:
        response = json.loads(gh("api", endpoint))
    except (ValueError, UnicodeDecodeError) as exc:
        raise Refused(f"GitHub API did not return JSON: {exc}") from None
    require(isinstance(response, dict), "Unexpected GitHub REST payload")
    return response


def main_sha() -> str:
    result = gh_json("repos/" + REPO + "/commits/main")
    sha = result.get("sha")
    require(isinstance(sha, str) and SOURCE_SHA_RE.fullmatch(sha) is not None,
            "Main source commit is missing")
    return sha


def workflow_catalog() -> tuple[int, list[dict]]:
    result = gh_json("repos/" + REPO + "/actions/workflows?per_page=100")
    workflows = result.get("workflows")
    require(isinstance(workflows, list) and result.get("total_count") == len(workflows),
            "Workflow catalog incomplete; refusing to guess identity")
    return validate_workflow_catalog(workflows), workflows


def _run_list(workflow_id: int) -> list[dict]:
    result = gh_json("repos/" + REPO +
                     f"/actions/workflows/{workflow_id}/runs?branch=main&event=workflow_dispatch&per_page=100")
    rows = result.get("workflow_runs")
    require(isinstance(rows, list), "Cannot list hosted workflow runs")
    return rows


def _latest_run_id(workflow_id: int) -> int:
    rows = _run_list(workflow_id)
    return max((r.get("id", 0) for r in rows if type(r.get("id")) is int), default=0)


def _wait_new_run(workflow_id: int, previous_id: int, sha: str,
                  *, sleeper: Callable[[float], None] = time.sleep,
                  rounds: int = 45) -> int:
    for _ in range(rounds):
        found = require_unique_dispatch(_run_list(workflow_id), previous_id, sha, workflow_id)
        if found is not None:
            return found
        require(main_sha() == sha, "Main advanced during dispatch; refusing continued qualification")
        sleeper(2.0)
    raise Refused("Timed out locating the exact newly dispatched run; inspect Actions manually")


def _state_file(state_dir: Path, sha: str) -> Path:
    require(SOURCE_SHA_RE.fullmatch(sha) is not None, "Invalid state SHA")
    return state_dir / f"{sha}.json"


def write_state(path: Path, state: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    descriptor, temporary = tempfile.mkstemp(prefix=".sfdt18-", suffix=".json", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as out:
            json.dump(state, out, indent=2, sort_keys=True)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def read_state(path: Path, sha: str) -> dict:
    require(path.is_file() and not path.is_symlink(),
            f"No dispatch state exists for this main SHA: {path}")
    try:
        state = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise Refused(f"Invalid stored dispatch state: {exc}") from None
    require(isinstance(state, dict) and state.get("schema") == SCHEMA
            and state.get("repo") == REPO and state.get("sha") == sha
            and isinstance(state.get("runs"), dict), "Wrong/corrupt dispatch state")
    return state


def dispatch(*, state_dir: Path, devtool_sha: str) -> None:
    require(SOURCE_SHA_RE.fullmatch(devtool_sha) is not None,
            "Devtool commit must be explicit lowercase 40-hex")
    sha = main_sha()
    evidence_id, _ = workflow_catalog()
    require(evidence_id > 0, "SFDT17 verifier workflow is not active")
    state_path = _state_file(state_dir, sha)
    require(not state_path.exists() and not state_path.is_symlink(),
            f"State already exists: {state_path}. Use 'status' or 'finalize'; do not duplicate runs")
    result = gh_json("repos/" + REPO + f"/actions/runs?head_sha={sha}&event=push&per_page=100")
    legacy_id = choose_legacy_run(result.get("workflow_runs", []), sha)
    state: dict[str, Any] = {"schema": SCHEMA, "repo": REPO, "sha": sha,
                             "devtool_sha": devtool_sha, "phase": "DISPATCHING",
                             "runs": {"legacy": legacy_id}}
    write_state(state_path, state)
    print(f"StudyFlow main: {sha}\nVerified green push CI: {legacy_id}\nDevtool SFDT08 runtime: {devtool_sha}", flush=True)
    for lane in LANES[1:]:
        require(main_sha() == sha, "Main changed between lane dispatches")
        wid, filename, _ = POLICY[lane]
        before = _latest_run_id(wid)
        params = ["workflow", "run", filename, "-R", REPO, "--ref", "main",
                  "-f", "expected_sha=" + sha]
        if lane in ("native_fast", "native_deep"):
            params += ["-f", "devtool_sha=" + devtool_sha,
                       "-f", "scope=" + ("fast" if lane == "native_fast" else "deep")]
        print("Dispatching", lane, "...", flush=True)
        gh(*params)
        run_id = _wait_new_run(wid, before, sha)
        state["runs"][lane] = run_id
        write_state(state_path, state)
        print(f"  {lane}: https://github.com/{REPO}/actions/runs/{run_id}", flush=True)
    state["phase"] = "READY"
    write_state(state_path, state)
    print("\nDispatch complete. This is NOT CI parity or cutover approval.", flush=True)
    print("When all workflows pass: python3 -B scripts/sfdt18_evidence_coordinator.py finalize", flush=True)
    print("State:", state_path, flush=True)


def _live_states(state: dict) -> dict[str, dict]:
    sha = state["sha"]
    runs = state["runs"]
    require(all(lane in runs and type(runs[lane]) is int and runs[lane] > 0 for lane in LANES),
            "Incomplete dispatch state (possible interrupted dispatch); inspect manually")
    return {lane: gh_json("repos/" + REPO + "/actions/runs/" + str(runs[lane])) for lane in LANES}


def status(*, state_dir: Path) -> None:
    sha = main_sha()
    state_path = _state_file(state_dir, sha)
    state = read_state(state_path, sha)
    print(f"StudyFlow source: {sha}\nDispatch phase: {state['phase']}")
    for lane in LANES:
        run_id = state["runs"].get(lane)
        if type(run_id) is not int:
            print(f"{lane:16} not yet captured")
            continue
        run = gh_json("repos/" + REPO + "/actions/runs/" + str(run_id))
        check_run(run, lane=lane, sha=sha, run_id=run_id)
        print(f"{lane:16} {str(run.get('status')):12} {str(run.get('conclusion')):12} "
              f"https://github.com/{REPO}/actions/runs/{run_id}")
    if state.get("evidence_run"):
        print("SFDT17 evidence:", f"https://github.com/{REPO}/actions/runs/{state['evidence_run']}")
    print("State:", state_path)


def finalize(*, state_dir: Path) -> None:
    sha = main_sha()
    state_path = _state_file(state_dir, sha)
    state = read_state(state_path, sha)
    if state.get("evidence_run"):
        print(f"Evidence already dispatched: https://github.com/{REPO}/actions/runs/{state['evidence_run']}")
        return
    require(state.get("phase") == "READY", "Dispatch was interrupted or already finalized; do not duplicate")
    evidence_id, _ = workflow_catalog()
    run_ids = state["runs"]
    live = _live_states(state)
    identities, inputs = checked_inputs(sha, run_ids, live)
    before = _latest_run_id(evidence_id)
    params = ["workflow", "run", EVIDENCE_WORKFLOW[0], "-R", REPO,
              "--ref", "main"]
    for key in ("expected_sha", "expected_identities_json",
                "legacy_run", "source_shadow_run", "expanded_shadow_run",
                "native_fast_run", "native_deep_run"):
        params += ["-f", key + "=" + inputs[key]]
    # Refuse duplicate dispatch if the CLI call fails or the run is ambiguous.
    state["phase"] = "EVIDENCE_DISPATCHING"
    state["manifest"] = identities
    write_state(state_path, state)
    gh(*params)
    evidence_run = _wait_new_run(evidence_id, before, sha)
    state["phase"] = "EVIDENCE_DISPATCHED"
    state["evidence_run"] = evidence_run
    write_state(state_path, state)
    print("SFDT17 hosted verifier dispatched. A PASS is only ZIP integrity, not cutover.")
    print(f"https://github.com/{REPO}/actions/runs/{evidence_run}")
    print("State:", state_path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("dispatch", "status", "finalize"))
    parser.add_argument("--devtool-sha", default=DEFAULT_DEVTOOL_SHA)
    parser.add_argument("--state-dir", type=Path,
                        default=Path.home() / ".local/state/studyflow/sfdt18")
    args = parser.parse_args(argv)
    try:
        if args.operation == "dispatch":
            dispatch(state_dir=args.state_dir, devtool_sha=args.devtool_sha)
        elif args.operation == "status":
            status(state_dir=args.state_dir)
        else:
            finalize(state_dir=args.state_dir)
        return 0
    except (Refused, OSError, TypeError, ValueError) as exc:
        print("SFDT18_REFUSED:", exc, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
