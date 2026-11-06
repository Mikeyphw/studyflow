#!/usr/bin/env python3
"""SFDT16: independent, read-only source-bound CI shadow comparison.

This is an evidence inventory, never authorization to remove authoritative CI.
No Gradle, signing, packaging, deployment, or mutation occurs here.
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
LANES = ("legacy", "source_shadow", "expanded_shadow", "native_fast", "native_deep")
ARTIFACT_LANES = {"legacy", "native_fast", "native_deep"}


class CutoverRefused(ValueError):
    pass


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise CutoverRefused(reason)


class GitHubReadOnly:
    def __init__(self, repository: str, token: str):
        require(REPO_RE.fullmatch(repository) is not None, "Invalid GitHub repository")
        require(bool(token), "Missing read-only GitHub token")
        self.repository = repository
        self.token = token

    def get(self, path: str) -> dict:
        require(path.startswith("/actions/runs/") and ".." not in path,
                "Unexpected GitHub API path")
        req = Request(
            "https://api.github.com/repos/" + self.repository + path,
            headers={
                "Authorization": "Bearer " + self.token,
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "StudyFlow-SFDT16-readonly",
            },
            method="GET",
        )
        with urlopen(req, timeout=30) as response:
            return json.load(response)

    def items(self, path: str, key: str) -> list[dict]:
        items: list[dict] = []
        for page in range(1, 101):
            delimiter = "&" if "?" in path else "?"
            body = self.get(f"{path}{delimiter}per_page=100&page={page}")
            batch = body.get(key)
            require(isinstance(batch, list), f"Missing {key} page {page}")
            items.extend(batch)
            require(len(items) <= 10000, "Unreasonably large API inventory")
            if len(batch) < 100:
                total = body.get("total_count")
                if total is not None:
                    require(len(items) == total, f"Incomplete {key} pagination")
                return items
        raise CutoverRefused(f"Excessive {key} pagination")


def gather(api: GitHubReadOnly, run_id: int) -> dict:
    root = f"/actions/runs/{run_id}"
    return {
        "run": api.get(root),
        "jobs": api.items(root + "/jobs", "jobs"),
        "artifacts": api.items(root + "/artifacts", "artifacts"),
    }


def assess(expected_sha: str, run_ids: dict[str, int], inventories: dict[str, dict]) -> dict:
    require(SHA_RE.fullmatch(expected_sha) is not None, "Expected SHA must be exact lowercase 40-hex")
    require(set(run_ids) == set(LANES), "All five independently identified CI lanes are required")
    require(all(type(n) is int and n > 0 for n in run_ids.values()), "Invalid GitHub run ID")
    require(len(set(run_ids.values())) == len(LANES), "Reused run ID across independent lanes")
    require(set(inventories) == set(LANES), "Missing run inventory")
    output: list[dict] = []
    for lane in LANES:
        observed = inventories[lane]
        run, jobs, artifacts = observed["run"], observed["jobs"], observed["artifacts"]
        require(run.get("id") == run_ids[lane], f"{lane}: run ID mismatch")
        require(run.get("head_sha") == expected_sha, f"{lane}: source SHA mismatch")
        require(run.get("status") == "completed" and run.get("conclusion") == "success",
                f"{lane}: workflow incomplete or not successful")
        require(type(run.get("run_attempt")) is int and run["run_attempt"] > 0,
                f"{lane}: missing exact run attempt")
        require(isinstance(jobs, list) and jobs, f"{lane}: job inventory empty")
        require(all(job.get("status") == "completed" for job in jobs),
                f"{lane}: unfinished job")
        require(all(job.get("conclusion") in ("success", "skipped") for job in jobs),
                f"{lane}: failed/cancelled/neutral job")
        require(any(job.get("conclusion") == "success" for job in jobs),
                f"{lane}: zero successful jobs")
        if lane != "legacy":
            require(not any(job.get("conclusion") == "skipped" for job in jobs),
                    f"{lane}: skipped shadow job; full coverage not established")
        require(isinstance(artifacts, list), f"{lane}: missing artifact inventory")
        if lane in ARTIFACT_LANES:
            require(artifacts and all(a.get("expired") is False for a in artifacts),
                    f"{lane}: missing or expired artifacts")
        output.append({
            "lane": lane,
            "workflow": run.get("name"),
            "run_id": run_ids[lane],
            "run_attempt": run["run_attempt"],
            "event": run.get("event"),
            "head_sha": run["head_sha"],
            "successful_jobs": [j.get("name") for j in jobs if j.get("conclusion") == "success"],
            "skipped_jobs": [j.get("name") for j in jobs if j.get("conclusion") == "skipped"],
            "artifacts": [{"id": a.get("id"), "name": a.get("name"),
                           "size_in_bytes": a.get("size_in_bytes"),
                           "digest": a.get("digest"), "expired": a.get("expired")}
                          for a in artifacts],
        })
    return {
        "schema": "studyflow.sfdt16.cutover-probe.v1",
        "status": "SAME_SHA_SHADOW_INVENTORY_PASS",
        "candidate_sha": expected_sha,
        "lanes": output,
        "cutover_authorized": False,
        "required_checks_replaced": False,
        "legacy_ci_retired": False,
        "artifact_byte_parity": "NOT_VERIFIED",
        "provider_task_parity": "NOT_VERIFIED",
        "push_pr_semantics": "NOT_VERIFIED",
        "release_qualified": False,
        "signing_qualified": False,
        "device_qualified": False,
    }


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="Mikeyphw/studyflow")
    parser.add_argument("--expected-sha", required=True)
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
        api = GitHubReadOnly(args.repo, os.environ.get("GITHUB_TOKEN", ""))
        inventories = {lane: gather(api, run_ids[lane]) for lane in LANES}
        report = assess(args.expected_sha, run_ids, inventories)
    except (CutoverRefused, HTTPError, URLError, OSError, KeyError, ValueError, TypeError) as exc:
        report = {
            "schema": "studyflow.sfdt16.cutover-probe.v1",
            "status": "REFUSED",
            "reason": str(exc),
            "candidate_sha": args.expected_sha,
            "cutover_authorized": False,
            "legacy_ci_retired": False,
        }
        write_json(args.output, report)
        print("SFDT16_CUTOVER_PROBE_REFUSED:", exc, file=sys.stderr)
        return 2
    write_json(args.output, report)
    print("SFDT16_SAME_SHA_SHADOW_INVENTORY_PASS_NO_CUTOVER_AUTHORITY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
