#!/usr/bin/env python3
"""SFDT17: read-only, hosted five-lane artifact byte and coverage inventory.

NEVER qualifies CI cutover, signing, release, or devices. GitHub Actions only
performs network access; local unit tests exercise offline fixtures only.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import tempfile
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen
import xml.etree.ElementTree as ET
import zipfile

MAX_ARCHIVE = 95 * 1024 * 1024
MAX_EXPANDED = 350 * 1024 * 1024
MAX_MEMBERS = 10000
MAX_INSPECT_XML = 8 * 1024 * 1024
MAX_INSPECT_JSON = 3 * 1024 * 1024
SHA = re.compile(r"[a-f0-9]{40}\Z")
DIGEST = re.compile(r"sha256:[a-f0-9]{64}\Z")
REPO = "Mikeyphw/studyflow"
LANES = ("legacy", "source_shadow", "expanded_shadow", "native_fast", "native_deep")
# Independently reviewed from the StudyFlow main Git tree, not inferred from
# caller-supplied IDs or from the candidate run being checked.
CANONICAL_WORKFLOWS = {
    "legacy": (".github/workflows/studyflow-ci.yml", "StudyFlow CI"),
    "source_shadow": (".github/workflows/studyflow-devtool-shadow.yml",
                      "StudyFlow Devtool native source shadow"),
    "expanded_shadow": (".github/workflows/studyflow-devtool-native-expanded.yml",
                        "StudyFlow Devtool expanded native hosted shadow"),
    "native_fast": (".github/workflows/studyflow-devtool-native-coverage.yml",
                    "StudyFlow Devtool native coverage expansion"),
    "native_deep": (".github/workflows/studyflow-devtool-native-coverage.yml",
                    "StudyFlow Devtool native coverage expansion"),
}

# Stable GitHub workflow-definition IDs, independently inspected from actual
# StudyFlow GitHub Actions workflow-run REST payloads on 2026-11-06.
# Pin both identity AND source-controlled relative workflow path.
CANONICAL_WORKFLOW_IDS = {
    "legacy": 402184638,
    "source_shadow": 406396373,
    "expanded_shadow": 406444033,
    "native_fast": 406507103,
    "native_deep": 406507103,
}


def verify_workflow_policy(identities: dict, inventories: dict[str, dict]) -> None:
    require(isinstance(identities, dict) and set(identities) == set(LANES),
            "Missing reviewed workflow identities")
    for lane in LANES:
        path, name = CANONICAL_WORKFLOWS[lane]
        require(identities[lane].get("workflow_path") == path,
                f"{lane}: workflow path differs from source-controlled policy")
        require(identities[lane].get("workflow_id") == CANONICAL_WORKFLOW_IDS[lane],
                f"{lane}: workflow ID differs from independently pinned policy")
        run = inventories[lane].get("run")
        if run is not None:
            require(run.get("name") == name, f"{lane}: workflow name differs from source-controlled policy")
            require(run.get("workflow_id") == CANONICAL_WORKFLOW_IDS[lane],
                    f"{lane}: observed workflow ID differs from pinned policy")
            require(run.get("path") == path,
                    f"{lane}: observed workflow path differs from pinned policy")



class EvidenceRefused(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceRefused(message)


def artifact_contracts(run_ids: dict[str, int], attempts: dict[str, int]) -> dict[str, dict[str, str]]:
    """Frozen review of known StudyFlow upload names; unknown uploads refuse."""
    r, a = run_ids, attempts
    return {
        "legacy": {
            f"studyflow-app-ci-debug-{r['legacy']}": "phone_debug_apk",
            f"studyflow-app-tv-ci-debug-{r['legacy']}": "tv_debug_apk",
            f"studyflow-app-diagnostics-{r['legacy']}-{a['legacy']}": "phone_diagnostics",
            f"studyflow-app-tv-diagnostics-{r['legacy']}-{a['legacy']}": "tv_diagnostics",
        },
        "source_shadow": {
            f"studyflow-sfdt06-native-shadow-{r['source_shadow']}-{a['source_shadow']}": "source_receipt",
        },
        "expanded_shadow": {
            f"studyflow-sfdt07-phone-debug-{r['expanded_shadow']}-{a['expanded_shadow']}": "phone_debug_apk",
            f"studyflow-sfdt07-tv-debug-{r['expanded_shadow']}-{a['expanded_shadow']}": "tv_debug_apk",
            f"studyflow-sfdt07-quality-source-{r['expanded_shadow']}-{a['expanded_shadow']}": "quality_receipt",
            f"studyflow-sfdt07-web-pytest-{r['expanded_shadow']}-{a['expanded_shadow']}": "web_junit",
        },
        "native_fast": {
            f"studyflow-sfdt08-phone-release-jvm-{r['native_fast']}-{a['native_fast']}": "phone_junit",
            f"studyflow-sfdt08-web-full-{r['native_fast']}-{a['native_fast']}": "web_junit",
            f"studyflow-sfdt08-desktop-fast-{r['native_fast']}-{a['native_fast']}": "desktop_junit",
        },
        "native_deep": {
            f"studyflow-sfdt08-{role}-contracts-{r['native_deep']}-{a['native_deep']}": role + "_deep"
            for role in ("android", "tv", "desktop", "quality", "parity")
        },
    }


def verify_bundle_metadata(lane: str, items: list[dict], expected: dict[str, str],
                           run_id: int, sha: str, seen: set[int]) -> dict[str, dict]:
    require(isinstance(items, list), f"{lane}: invalid artifact list")
    names: dict[str, dict] = {}
    for artifact in items:
        require(isinstance(artifact, dict), f"{lane}: invalid artifact record")
        name = artifact.get("name")
        require(isinstance(name, str) and name in expected, f"{lane}: unexpected artifact {name!r}")
        require(name not in names, f"{lane}: duplicate artifact name")
        aid = artifact.get("id")
        require(type(aid) is int and aid > 0 and aid not in seen, f"{lane}: duplicate/invalid artifact ID")
        seen.add(aid)
        require(type(artifact.get("size_in_bytes")) is int and
                0 < artifact["size_in_bytes"] <= MAX_ARCHIVE,
                f"{lane}: invalid/unbounded archive size")
        require(artifact.get("expired") is False, f"{lane}: expired artifact")
        require(isinstance(artifact.get("digest"), str) and DIGEST.fullmatch(artifact["digest"]) is not None,
                f"{lane}: missing/malformed GitHub SHA-256 digest")
        producer = artifact.get("workflow_run")
        require(isinstance(producer, dict) and producer.get("id") == run_id
                and producer.get("head_sha") == sha,
                f"{lane}: artifact producer run/SHA not established")
        names[name] = artifact
    require(set(names) == set(expected),
            f"{lane}: required artifacts missing: {sorted(set(expected) - set(names))}; unexpected: {sorted(set(names) - set(expected))}")
    return names


def safe_member(name: str, is_dir: bool) -> str:
    require(bool(name) and "\\" not in name and "\x00" not in name, "Unsafe ZIP name")
    require(not name.startswith(("/", "~")) and not re.match(r"^[A-Za-z]:", name), "Absolute ZIP path")
    normalized = name[:-1] if is_dir and name.endswith("/") else name
    p = PurePosixPath(normalized)
    require(bool(normalized) and not any(piece in ("", ".", "..") for piece in normalized.split("/"))
            and not p.is_absolute(), "ZIP traversal or noncanonical path")
    return normalized.casefold()


def _strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def inspect_junit(data: bytes) -> dict | None:
    # Secure stdlib parser: no external entity resolution, bounded input bytes.
    require(len(data) <= MAX_INSPECT_XML, "Oversize XML inspection input")
    try:
        root = ET.fromstring(data)
    except ET.ParseError:
        return None
    if _strip_namespace(root.tag) not in ("testsuite", "testsuites"):
        return None
    cases = [e for e in root.iter() if _strip_namespace(e.tag) == "testcase"]
    if not cases:
        return None
    failed = sum(any(_strip_namespace(child.tag) == "failure" for child in case) for case in cases)
    errors = sum(any(_strip_namespace(child.tag) == "error" for child in case) for case in cases)
    skipped = sum(any(_strip_namespace(child.tag) == "skipped" for child in case) for case in cases)
    return {"executed": len(cases), "failed": failed, "errors": errors,
            "skipped": skipped, "passed": len(cases) - failed - errors - skipped}


def inspect_zip(path: Path, *, role: str) -> dict:
    require(path.is_file() and not path.is_symlink(), "Missing or linked artifact ZIP")
    require(path.stat().st_size <= MAX_ARCHIVE, "Artifact ZIP exceeds size cap")
    hashes = []
    apks = []
    junit = []
    receipts = []
    lint = []
    seen: set[str] = set()
    total = 0
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        require(bool(entries) and len(entries) <= MAX_MEMBERS, "Empty/oversize artifact ZIP inventory")
        for entry in entries:
            canon = safe_member(entry.filename, entry.is_dir())
            require(canon not in seen, "Duplicate/case-colliding ZIP entry")
            seen.add(canon)
            kind = (entry.external_attr >> 16) & 0o170000
            require(kind not in (stat.S_IFLNK, stat.S_IFCHR, stat.S_IFBLK, stat.S_IFIFO,
                                 stat.S_IFSOCK), "ZIP symlink/special entry")
            require((entry.flag_bits & 1) == 0, "Encrypted ZIP member")
            require(entry.file_size >= 0 and entry.file_size <= MAX_EXPANDED,
                    "Oversize ZIP member")
            if entry.is_dir():
                continue
            total += entry.file_size
            require(total <= MAX_EXPANDED, "Excessive expanded ZIP bytes")
            require(entry.compress_size > 0 or entry.file_size == 0, "Invalid ZIP compression")
            if entry.file_size > 1024 * 1024:
                require(entry.file_size <= 1000 * max(1, entry.compress_size), "Suspicious ZIP compression ratio")
            h = hashlib.sha256()
            captured = bytearray()
            suffix = entry.filename.lower()
            xml = suffix.endswith(".xml") and entry.file_size <= MAX_INSPECT_XML
            js = suffix.endswith(".json") and entry.file_size <= MAX_INSPECT_JSON
            with archive.open(entry) as stream:
                while block := stream.read(1024 * 1024):
                    h.update(block)
                    if xml or js:
                        captured.extend(block)
            member = {"path": entry.filename, "size": entry.file_size, "sha256": h.hexdigest()}
            hashes.append(member)
            if suffix.endswith(".apk"):
                apks.append(member)
            if xml:
                report = inspect_junit(bytes(captured))
                if report is not None:
                    junit.append({"path": entry.filename, **report})
                elif "lint" in suffix:
                    lint.append(member)
            if js and any(t in suffix for t in ("receipt", "evidence", "manifest", "summary", "report")):
                try:
                    parsed = json.loads(captured)
                    if isinstance(parsed, dict):
                        receipts.append({"path": entry.filename, "sha256": member["sha256"],
                                         "schema": parsed.get("schema"),
                                         "status": parsed.get("status")})
                except (ValueError, UnicodeDecodeError):
                    # Do not invent receipt validity; retain file hash instead.
                    pass
    if role.endswith("_apk"):
        require(len(apks) == 1, f"{role}: expected one packaged debug APK, got {len(apks)}")
    if role.endswith("junit") or role in ("android_deep", "tv_deep", "desktop_deep"):
        require(bool(junit), f"{role}: missing parseable JUnit XML")
    return {"role": role, "files": hashes, "apks": apks, "junit": junit,
            "lint_xml": lint, "receipts": receipts,
            "expanded_bytes": total, "members": len(hashes)}


class DenyRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward bearer authorization to a CDN host.


def _trusted_cdn(url: str) -> bool:
    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.hostname or parts.username or parts.password:
        return False
    hostname = parts.hostname.lower()
    return hostname == "github.com" or any(hostname.endswith(suffix) for suffix in
         (".actions.githubusercontent.com", ".githubusercontent.com", ".blob.core.windows.net"))


def download_archive(repo: str, artifact: dict, token: str, dest: Path) -> None:
    """Download actual artifact ZIP bytes, verify REST SHA-256 and byte count."""
    require(repo == REPO and bool(token), "Refusing unreviewed repository or missing GitHub token")
    aid = artifact["id"]
    url = f"https://api.github.com/repos/{repo}/actions/artifacts/{aid}/zip"
    request = Request(url, headers={"Authorization": f"Bearer {token}",
                       "Accept": "application/vnd.github+json",
                       "X-GitHub-Api-Version": "2022-11-28",
                       "User-Agent": "StudyFlow-SFDT17-readonly"})
    opener = build_opener(DenyRedirect)
    try:
        with opener.open(request, timeout=45) as response:
            raise EvidenceRefused("Artifact endpoint did not issue expected HTTP redirect")
    except HTTPError as exc:
        require(exc.code in (301, 302, 303, 307, 308),
                f"Artifact download API returned HTTP {exc.code}")
        target = exc.headers.get("Location")
    require(isinstance(target, str) and _trusted_cdn(target), "Unsafe artifact download redirect host")
    require(not dest.exists() and not dest.is_symlink(), "Refusing to overwrite artifact ZIP")
    digest = hashlib.sha256()
    size = 0
    with urlopen(Request(target, headers={"User-Agent": "StudyFlow-SFDT17-readonly"}), timeout=90) as src, dest.open("xb") as out:
        while chunk := src.read(1024 * 1024):
            size += len(chunk)
            require(size <= MAX_ARCHIVE and size <= artifact["size_in_bytes"], "ZIP download exceeded expected size")
            digest.update(chunk)
            out.write(chunk)
    require(size == artifact["size_in_bytes"], "Artifact ZIP byte count differs from GitHub")
    require("sha256:" + digest.hexdigest() == artifact["digest"], "Artifact ZIP SHA-256 differs from GitHub")


def assemble_report(expected_sha: str, run_ids: dict[str, int], identities: dict,
                    inventories: dict[str, dict], root: Path) -> dict:
    """Offline testable, assumes SFDT16's run/job verifier was executed first."""
    require(SHA.fullmatch(expected_sha) is not None, "Invalid exact StudyFlow SHA")
    require(set(run_ids) == set(LANES) and set(inventories) == set(LANES), "Incomplete five-lane inventory")
    verify_workflow_policy(identities, inventories)
    attempts = {lane: identities[lane]["attempt"] for lane in LANES}
    patterns = artifact_contracts(run_ids, attempts)
    seen: set[int] = set()
    result: dict[str, list] = {}
    total_junit = {"executed": 0, "failed": 0, "errors": 0, "skipped": 0, "passed": 0}
    for lane in LANES:
        metadata = verify_bundle_metadata(lane, inventories[lane]["artifacts"], patterns[lane],
                                          run_ids[lane], expected_sha, seen)
        role_rows = []
        for name, role in patterns[lane].items():
            art = metadata[name]
            archive = root / f"{art['id']}.zip"
            require(archive.is_file(), f"{lane}: missing downloaded ZIP {art['id']}")
            with archive.open("rb") as handle:
                sha = hashlib.file_digest(handle, "sha256").hexdigest()
            require("sha256:" + sha == art["digest"] and
                    archive.stat().st_size == art["size_in_bytes"],
                    f"{lane}: artifact archive bytes mismatch {art['id']}")
            parsed = inspect_zip(archive, role=role)
            for suite in parsed["junit"]:
                for key in total_junit:
                    total_junit[key] += suite[key]
            role_rows.append({"artifact_id": art["id"], "name": name,
                              "github_sha256": art["digest"], **parsed})
        result[lane] = role_rows
    return {
        "schema": "studyflow.sfdt17.hosted-equivalence.v1",
        "status": "FIVE_LANE_ARTIFACT_BYTES_VERIFIED_NOT_CUTOVER_APPROVED",
        "studyflow_sha": expected_sha,
        "devtool_execution_sha": "NOT_ATTESTED_BY_THIS_CHECK",
        "runs": {lane: {"id": run_ids[lane], "attempt": attempts[lane]} for lane in LANES},
        "lanes": result, "aggregate_junit_executions_not_unique_tests": total_junit,
        "artifact_archive_integrity": "PASS",
        "artifact_member_integrity": "PASS",
        "exact_artifact_attempt_provenance": "NOT_VERIFIED",
        "legacy_vs_native_apk_equivalence": "NOT_VERIFIED",
        "native_vs_legacy_task_equivalence": "NOT_VERIFIED",
        "web_desktop_quality_parity_equivalence": "NOT_VERIFIED",
        "push_pr_semantics": "NOT_VERIFIED",
        "cutover_authorized": False,
        "legacy_ci_retired": False,
        "release_qualified": False, "signing_qualified": False, "device_qualified": False,
    }


def report_refusal(sha: str, reason: str) -> dict:
    return {"schema": "studyflow.sfdt17.hosted-equivalence.v1", "status": "REFUSED",
            "studyflow_sha": sha, "reason": reason, "cutover_authorized": False,
            "legacy_ci_retired": False, "release_qualified": False,
            "signing_qualified": False, "device_qualified": False}


def main(argv: list[str] | None = None) -> int:
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--repo", default=REPO)
    cli.add_argument("--expected-sha", required=True)
    cli.add_argument("--identities-json", required=True)
    for lane in LANES:
        cli.add_argument("--" + lane.replace("_", "-"), required=True, type=int)
    cli.add_argument("--output", type=Path, required=True)
    args = cli.parse_args(argv)
    run_ids = {lane: getattr(args, lane) for lane in LANES}
    try:
        require(args.repo == REPO, "Unreviewed repository")
        require(SHA.fullmatch(args.expected_sha) is not None, "Invalid exact SHA")
        identities = json.loads(args.identities_json)
        from scripts.sfdt16_cutover_probe import GitHubReadOnly, gather, assess, validate_identities
        validate_identities(identities, args.repo)
        require(all(type(i) is int and i > 0 for i in run_ids.values()) and
                len(set(run_ids.values())) == len(LANES), "Invalid/reused run IDs")
        token = os.environ.get("GITHUB_TOKEN", "")
        client = GitHubReadOnly(args.repo, token)
        inventories = {lane: gather(client, run_ids[lane], identities[lane]["attempt"]) for lane in LANES}
        # Establish source, repository, workflow ID/ref, attempt-scoped jobs, and job counts.
        result_16 = assess(args.expected_sha, run_ids, inventories, identities, args.repo)
        verify_workflow_policy(identities, inventories)
        require(result_16.get("status") == "SAME_SHA_SHADOW_INVENTORY_PASS", "SFDT16 did not pass")
        patterns = artifact_contracts(run_ids,
            {lane: identities[lane]["attempt"] for lane in LANES})
        seen: set[int] = set()
        with tempfile.TemporaryDirectory(prefix="sfdt17-") as temp:
            destination = Path(temp)
            for lane in LANES:
                metadata = verify_bundle_metadata(lane, inventories[lane]["artifacts"],
                                                  patterns[lane], run_ids[lane], args.expected_sha, seen)
                for artifact in metadata.values():
                    download_archive(args.repo, artifact, token, destination / f"{artifact['id']}.zip")
            report = assemble_report(args.expected_sha, run_ids, identities, inventories, destination)
    except (EvidenceRefused, HTTPError, URLError, OSError, KeyError, ValueError,
            TypeError, zipfile.BadZipFile, RuntimeError) as err:
        report = report_refusal(args.expected_sha, str(err))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
        print("SFDT17_REFUSED:", err, file=sys.stderr)
        return 2
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    print("SFDT17_FIVE_LANE_ZIP_BYTES_PASS_NO_CUTOVER_AUTHORITY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
