#!/usr/bin/env python3
"""SFDT16-R2: fail-closed GitHub CI run and attempt identity inventory.

No Gradle, signing, publication, or cutover decision is performed.
Workflow identities are independently pinned in an explicit dispatch manifest.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SHA_RE = re.compile(r"[a-f0-9]{40}\Z")
REPO_RE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
DIGEST_RE = re.compile(r"sha256:[a-f0-9]{64}\Z")
WORKFLOW_RE = re.compile(r"\.github/workflows/[A-Za-z0-9_.-]+\.ya?ml\Z")
LANES = ("legacy", "source_shadow", "expanded_shadow", "native_fast", "native_deep")
ARTIFACT_LANES = {"legacy", "native_fast", "native_deep"}
MIN_SUCCESS_JOBS = {"legacy": 5, "source_shadow": 1, "expanded_shadow": 4,
                    "native_fast": 3, "native_deep": 5}


class CutoverRefused(ValueError):
    pass


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise CutoverRefused(reason)


def validate_identities(identities: dict, repository: str) -> dict:
    """Require independent reviewed workflow path/id and exact attempt per lane.

    Fast and deep intentionally share the SFDT08 workflow definition, but use
    different run IDs and full separate job matrices. Other lanes are distinct.
    """
    require(isinstance(identities, dict) and set(identities) == set(LANES),
            "Identity manifest must define exactly five lanes")
    for lane, data in identities.items():
        require(isinstance(data, dict) and set(data) == {"workflow_id", "workflow_path", "attempt"},
                f"{lane}: identity manifest keys must be workflow_id, workflow_path, attempt")
        require(type(data["workflow_id"]) is int and data["workflow_id"] > 0,
                f"{lane}: invalid reviewed workflow ID")
        require(type(data["attempt"]) is int and data["attempt"] > 0,
                f"{lane}: invalid reviewed attempt")
        require(isinstance(data["workflow_path"], str)
                and WORKFLOW_RE.fullmatch(data["workflow_path"]) is not None,
                f"{lane}: workflow path must be a checked-in YAML file")
    require(identities["legacy"]["workflow_path"] == ".github/workflows/studyflow-ci.yml",
            "Authoritative legacy workflow path changed")
    for lane in ("source_shadow", "expanded_shadow", "native_fast", "native_deep"):
        require(identities[lane]["workflow_path"] != identities["legacy"]["workflow_path"],
                f"{lane}: cannot reuse authoritative legacy workflow")
    # These two modes run as distinct dispatches of the same workflow file.
    for field in ("workflow_id", "workflow_path"):
        require(identities["native_fast"][field] == identities["native_deep"][field],
                f"SFDT08 fast/deep must use the same {field}")
    unique = [(identities[lane]["workflow_id"], identities[lane]["workflow_path"])
              for lane in ("legacy", "source_shadow", "expanded_shadow", "native_fast")]
    require(len(set(unique)) == len(unique),
            "Legacy, source shadow, expanded shadow and SFDT08 require distinct workflows")
    return identities


class GitHubReadOnly:
    def __init__(self, repository: str, token: str):
        require(REPO_RE.fullmatch(repository) is not None, "Invalid GitHub repository")
        require(bool(token), "Missing read-only GitHub token")
        self.repository, self.token = repository, token

    def get(self, path: str) -> dict:
        require(re.fullmatch(r"/actions/runs/[1-9][0-9]*(?:/attempts/[1-9][0-9]*/jobs|/artifacts)?(?:\?per_page=100&page=[1-9][0-9]*)?", path) is not None,
                "Unexpected GitHub API path")
        req = Request(
            "https://api.github.com/repos/" + self.repository + path,
            headers={"Authorization": "Bearer " + self.token,
                     "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28",
                     "User-Agent": "StudyFlow-SFDT16-R2-readonly"}, method="GET")
        with urlopen(req, timeout=30) as response:
            return json.load(response)

    def items(self, path: str, key: str) -> list[dict]:
        result: list[dict] = []
        for page in range(1, 101):
            body = self.get(f"{path}?per_page=100&page={page}")
            batch = body.get(key)
            require(isinstance(batch, list), f"Missing {key} page {page}")
            result.extend(batch)
            require(len(result) <= 10000, "Unreasonably large API inventory")
            if len(batch) < 100:
                total = body.get("total_count")
                if total is not None:
                    require(len(result) == total, f"Incomplete {key} pagination")
                return result
        raise CutoverRefused(f"Excessive {key} pagination")


def gather(api: GitHubReadOnly, run_id: int, attempt: int) -> dict:
    root = f"/actions/runs/{run_id}"
    return {"run": api.get(root),
            "jobs": api.items(root + f"/attempts/{attempt}/jobs", "jobs"),
            "artifacts": api.items(root + "/artifacts", "artifacts")}


def assess(expected_sha: str, run_ids: dict[str, int], inventories: dict[str, dict],
           identities: dict, repository: str = "Mikeyphw/studyflow") -> dict:
    require(REPO_RE.fullmatch(repository) is not None, "Invalid repository")
    require(SHA_RE.fullmatch(expected_sha) is not None, "Expected SHA must be exact lowercase 40-hex")
    require(set(run_ids) == set(LANES), "All five independently identified CI lanes are required")
    require(all(type(n) is int and n > 0 for n in run_ids.values()), "Invalid GitHub run ID")
    require(len(set(run_ids.values())) == len(LANES), "Reused run ID across independent lanes")
    require(set(inventories) == set(LANES), "Missing run inventory")
    validate_identities(identities, repository)

    output: list[dict] = []
    all_artifact_ids: set[int] = set()
    for lane in LANES:
        observed = inventories[lane]
        require(isinstance(observed, dict) and set(observed) >= {"run", "jobs", "artifacts"},
                f"{lane}: incomplete inventory")
        run, jobs, artifacts = observed["run"], observed["jobs"], observed["artifacts"]
        identity = identities[lane]
        require(run.get("id") == run_ids[lane], f"{lane}: run ID mismatch")
        require(run.get("head_sha") == expected_sha, f"{lane}: source SHA mismatch")
        require(run.get("head_branch") == "main", f"{lane}: run is not on main")
        require(run.get("event") in ({"workflow_dispatch", "push"} if lane == "legacy" else {"workflow_dispatch"}),
                f"{lane}: unexpected trigger event")
        require(run.get("repository", {}).get("full_name") == repository,
                f"{lane}: source repository mismatch")
        require(type(run.get("workflow_id")) is int and run["workflow_id"] == identity["workflow_id"],
                f"{lane}: workflow ID mismatch")
        # GitHub's run.path includes owner/repo and exact definition ref.
        expected_path = f'{repository}/{identity["workflow_path"]}@refs/heads/main'
        require(run.get("path") == expected_path,
                f"{lane}: workflow path/ref mismatch: {run.get('path')!r}")
        require(isinstance(run.get("name"), str) and bool(run["name"].strip()),
                f"{lane}: missing workflow name")
        require(run.get("status") == "completed" and run.get("conclusion") == "success",
                f"{lane}: workflow incomplete or not successful")
        require(type(run.get("run_attempt")) is int and run["run_attempt"] == identity["attempt"],
                f"{lane}: run attempt does not match independently pinned attempt")
        require(isinstance(jobs, list) and len(jobs) >= MIN_SUCCESS_JOBS[lane],
                f"{lane}: too few jobs for expected lane")
        require(all(isinstance(job, dict) and job.get("status") == "completed" for job in jobs),
                f"{lane}: unfinished job")
        require(all(job.get("conclusion") in ("success", "skipped") for job in jobs),
                f"{lane}: failed/cancelled/neutral job")
        require(sum(job.get("conclusion") == "success" for job in jobs) >= MIN_SUCCESS_JOBS[lane],
                f"{lane}: missing successful required jobs")
        for job in jobs:
            if "run_id" in job:
                require(job["run_id"] == run_ids[lane], f"{lane}: job run ID mismatch")
            if "head_sha" in job:
                require(job["head_sha"] == expected_sha, f"{lane}: job SHA mismatch")
        if lane != "legacy":
            require(not any(job.get("conclusion") == "skipped" for job in jobs),
                    f"{lane}: skipped shadow job; full coverage not established")
        require(isinstance(artifacts, list), f"{lane}: missing artifact inventory")
        if lane in ARTIFACT_LANES:
            require(bool(artifacts), f"{lane}: required artifact inventory empty")
        verified_artifacts: list[dict] = []
        for artifact in artifacts:
            require(isinstance(artifact, dict), f"{lane}: malformed artifact")
            aid, name, size, digest = (artifact.get(k) for k in
                                        ("id", "name", "size_in_bytes", "digest"))
            require(type(aid) is int and aid > 0 and aid not in all_artifact_ids,
                    f"{lane}: invalid/duplicate artifact ID")
            all_artifact_ids.add(aid)
            require(isinstance(name, str) and bool(name.strip()), f"{lane}: artifact name missing")
            require(type(size) is int and size > 0, f"{lane}: artifact size missing or empty")
            require(isinstance(digest, str) and DIGEST_RE.fullmatch(digest) is not None,
                    f"{lane}: invalid/missing SHA-256 digest metadata")
            require(artifact.get("expired") is False, f"{lane}: missing/expired artifact")
            artifact_run = artifact.get("workflow_run")
            if artifact_run is not None:
                require(isinstance(artifact_run, dict) and artifact_run.get("id") == run_ids[lane],
                        f"{lane}: artifact producer run mismatch")
                if "head_sha" in artifact_run:
                    require(artifact_run["head_sha"] == expected_sha,
                            f"{lane}: artifact producer SHA mismatch")
            verified_artifacts.append({"id": aid, "name": name, "size_in_bytes": size,
                                       "digest": digest, "expired": False,
                                       "attempt_bytes_verified": False})
        output.append({
            "lane": lane, "workflow": run["name"], "workflow_id": identity["workflow_id"],
            "workflow_path": identity["workflow_path"], "run_id": run_ids[lane],
            "run_attempt": identity["attempt"], "event": run["event"],
            "head_sha": expected_sha,
            "successful_jobs": [j.get("name") for j in jobs if j["conclusion"] == "success"],
            "skipped_jobs": [j.get("name") for j in jobs if j["conclusion"] == "skipped"],
            "artifacts": verified_artifacts})
    return {
        "schema": "studyflow.sfdt16.cutover-probe.v2",
        "status": "SAME_SHA_SHADOW_INVENTORY_PASS",
        "candidate_sha": expected_sha, "lanes": output,
        "cutover_authorized": False, "required_checks_replaced": False,
        "legacy_ci_retired": False, "artifact_byte_parity": "NOT_VERIFIED",
        "artifact_attempt_provenance": "NOT_VERIFIED",
        "provider_task_parity": "NOT_VERIFIED", "push_pr_semantics": "NOT_VERIFIED",
        "release_qualified": False, "signing_qualified": False, "device_qualified": False}


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="Mikeyphw/studyflow")
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--identities-json", required=True, help="Reviewed five-lane workflow ID, source path, and attempt map")
    for lane in LANES:
        parser.add_argument("--" + lane.replace("_", "-"), type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    run_ids = {lane: getattr(args, lane) for lane in LANES}
    try:
        require(REPO_RE.fullmatch(args.repo) is not None, "Invalid repository")
        require(SHA_RE.fullmatch(args.expected_sha) is not None, "Invalid expected SHA")
        require(len(set(run_ids.values())) == len(LANES) and all(n > 0 for n in run_ids.values()),
                "Run IDs must be positive and unique")
        identities = json.loads(args.identities_json)
        validate_identities(identities, args.repo)
        api = GitHubReadOnly(args.repo, os.environ.get("GITHUB_TOKEN", ""))
        inventories = {lane: gather(api, run_ids[lane], identities[lane]["attempt"]) for lane in LANES}
        report = assess(args.expected_sha, run_ids, inventories, identities, args.repo)
    except (CutoverRefused, HTTPError, URLError, OSError, KeyError, ValueError, TypeError,
            json.JSONDecodeError) as exc:
        report = {"schema": "studyflow.sfdt16.cutover-probe.v2", "status": "REFUSED",
                  "reason": str(exc), "candidate_sha": args.expected_sha,
                  "cutover_authorized": False, "legacy_ci_retired": False}
        write_json(args.output, report)
        print("SFDT16_CUTOVER_PROBE_REFUSED:", exc, file=sys.stderr)
        return 2
    write_json(args.output, report)
    print("SFDT16_R2_SAME_SHA_SHADOW_INVENTORY_PASS_NO_CUTOVER_AUTHORITY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
