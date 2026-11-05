#!/usr/bin/env python3
"""SFDT10-R2: fail-closed, same-commit hosted legacy/native Android evidence comparison.

Uses GitHub's authenticated metadata and actual downloaded member bytes. Neither
hosted success nor this comparison constitutes signed-release/device/CI cutover.
No Gradle invocation and no signing secrets or write operations.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
REPO = "Mikeyphw/studyflow"
LEGACY_WORKFLOW = ".github/workflows/studyflow-ci.yml"
SHADOW_WORKFLOW = ".github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml"
LANES = {
    "phone-debug": {"module": "app", "package": "com.mikeyphw.studyflow",
                    "apk": "app-debug.apk", "tests": False},
    "tv-debug": {"module": "app-tv", "package": "com.mikeyphw.studyflow.tv",
                 "apk": "app-tv-debug.apk", "tests": True},
    "phone-release-jvm": {"module": "app", "package": None,
                          "apk": None, "tests": True},
}


class ComparisonError(ValueError):
    pass


def need(ok: bool, reason: str) -> None:
    if not ok:
        raise ComparisonError(reason)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def command(argv: list[str], *, timeout: int = 90) -> str:
    result = subprocess.run(argv, text=True, capture_output=True, check=False,
                            timeout=timeout,
                            env={**os.environ, "GH_PROMPT_DISABLED": "1", "GIT_TERMINAL_PROMPT": "0"})
    need(result.returncode == 0, "Command refused: " + " ".join(argv[:3]) + ": " + result.stderr[:180])
    return result.stdout


def api(path: str) -> dict:
    need(path.startswith("repos/Mikeyphw/studyflow/") and
         re.fullmatch(r"[A-Za-z0-9/_?&=.-]+", path) is not None,
         "Unreviewed GitHub API resource")
    obj = json.loads(command(["gh", "api", "-X", "GET", path], timeout=45))
    need(isinstance(obj, dict), "Unexpected GitHub API response")
    return obj


def run_check(row: dict, *, number: int, workflow: str, source: str) -> int:
    need(row.get("id") == number and row.get("head_sha") == source and
         row.get("head_branch") == "main" and row.get("repository", {}).get("full_name") == REPO,
         "GitHub run source/repository mismatch")
    need(row.get("path") == workflow and row.get("status") == "completed" and
         row.get("conclusion") == "success", "Workflow did not complete successfully: " + workflow)
    event = row.get("event")
    need((event in ("push", "workflow_dispatch") if workflow == LEGACY_WORKFLOW
          else event == "workflow_dispatch"), "Unexpected workflow event")
    attempt = row.get("run_attempt")
    need(type(attempt) is int and attempt > 0, "Invalid GitHub run attempt")
    return attempt


def jobs_check(obj: dict, expected: set[str]) -> None:
    rows = obj.get("jobs")
    need(isinstance(rows, list) and obj.get("total_count", len(rows)) == len(rows)
         and len(rows) <= 30, "Incomplete GitHub jobs metadata")
    got = {r.get("name"): r.get("conclusion") for r in rows if r.get("conclusion") != "skipped"}
    need(set(got) == expected, "Missing/unexpected active workflow jobs: " + str(sorted(got)))
    need(all(v == "success" for v in got.values()), "Failed active workflow job")


def artifact_index(obj: dict) -> dict[str, dict]:
    items = obj.get("artifacts")
    need(isinstance(items, list) and obj.get("total_count") == len(items)
         and len(items) <= 50, "Incomplete artifact metadata index")
    names: dict[str, dict] = {}
    for x in items:
        name = x.get("name")
        need(isinstance(name, str) and name and name not in names, "Duplicate/invalid artifact name")
        names[name] = x
    return names


def artifact_meta(items: dict, name: str, run: int) -> dict:
    need(name in items, "Missing GitHub artifact: " + name)
    row = items[name]
    need(row.get("name") == name and row.get("expired") is False
         and 0 < row.get("size_in_bytes", 0) <= 130_000_000
         and (row.get("workflow_run") or {}).get("id", run) == run,
         "Unusable GitHub artifact: " + name)
    digest = row.get("digest")
    if digest is not None:
        need(re.fullmatch(r"sha256:[0-9a-f]{64}", digest) is not None,
             "Invalid GitHub artifact archive digest")
    return row


def download(run: int, name: str, dest: Path) -> Path:
    need(not dest.exists() and not dest.is_symlink(), "Existing artifact destination")
    dest.mkdir(parents=True)
    command(["gh", "run", "download", str(run), "-R", REPO, "-n", name,
             "-D", str(dest)], timeout=220)
    members = list(dest.rglob("*"))
    need(0 < len(members) <= 1200, "Empty/oversized extracted artifact")
    total = 0
    for path in members:
        need(not path.is_symlink() and path.resolve().is_relative_to(dest.resolve()),
             "Symlink/traversal in extracted artifact")
        mode = path.stat().st_mode
        need(stat.S_ISDIR(mode) or stat.S_ISREG(mode), "Unexpected artifact member type")
        if path.is_file():
            total += path.stat().st_size
            need(total <= 350_000_000, "Artifact members exceed bounded size")
    return dest


def exact_file(base: Path, name: str) -> Path:
    matches = [p for p in base.rglob(name) if p.is_file() and not p.is_symlink()]
    need(len(matches) == 1, "Missing/ambiguous member: " + name)
    return matches[0]


def xml_junit(blob: bytes) -> Counter:
    need(len(blob) <= 6_000_000, "Oversized JUnit")
    try:
        root = ET.fromstring(blob)
    except ET.ParseError as exc:
        raise ComparisonError("Malformed JUnit XML") from exc
    suites = [root] if root.tag == "testsuite" else list(root) if root.tag == "testsuites" else []
    need(suites and all(x.tag == "testsuite" for x in suites), "Unexpected JUnit suite shape")
    cases = Counter()
    for suite in suites:
        observed = {k: 0 for k in ("tests", "failures", "errors", "skipped")}
        for case in suite.findall("testcase"):
            outcome = "FAIL" if case.find("failure") is not None else (
                "ERROR" if case.find("error") is not None else (
                    "SKIP" if case.find("skipped") is not None else "PASS"))
            observed["tests"] += 1
            if outcome != "PASS":
                observed[{"FAIL": "failures", "ERROR": "errors", "SKIP": "skipped"}[outcome]] += 1
            key = (case.get("classname"), case.get("name"), outcome)
            need(all(isinstance(x, str) and x for x in key[:2]), "Missing JUnit testcase identity")
            cases[key] += 1
        for k, count in observed.items():
            declared = suite.get(k, "0")
            need(declared.isdecimal() and int(declared) == count,
                 "JUnit testcase count disagrees with suite header")
    need(sum(cases.values()) > 0 and all(k[2] != "FAIL" and k[2] != "ERROR" for k in cases),
         "Missing/passing JUnit cases or reported failures")
    return cases


def junit_inventory(root: Path) -> Counter:
    matches = sorted(p for p in root.rglob("TEST-*.xml") if p.is_file() and not p.is_symlink())
    need(0 < len(matches) <= 180, "No/bounded JUnit report inventory")
    result: Counter = Counter()
    for p in matches:
        result.update(xml_junit(p.read_bytes()))
    return result


def lint_inventory(root: Path) -> Counter:
    reports = sorted(p for p in root.rglob("lint-results-*.xml") if p.is_file() and not p.is_symlink())
    need(len(reports) == 1, "Expected exactly one lint XML report")
    try:
        xml = ET.fromstring(reports[0].read_bytes())
    except ET.ParseError as exc:
        raise ComparisonError("Malformed lint XML") from exc
    need(xml.tag == "issues", "Unexpected lint XML root")
    result: Counter = Counter()
    for issue in xml.findall("issue"):
        key = (issue.get("id"), issue.get("severity"))
        need(all(isinstance(x, str) and x for x in key), "Unidentified lint issue")
        result[key] += 1
    return result


def apk_identity(path: Path, package: str, *, aapt: Path, signer: Path) -> dict:
    need(path.is_file() and path.stat().st_size > 1024
         and zipfile.is_zipfile(path), "Invalid signed APK file")
    with zipfile.ZipFile(path) as z:
        need("AndroidManifest.xml" in z.namelist() and
             len(z.namelist()) <= 60000 and z.testzip() is None,
             "APK ZIP/manifest integrity refused")
    badge = command([str(aapt), "dump", "badging", str(path)], timeout=45)
    match = re.search(r"(?m)^package: name='([^']+)' versionCode='([^']+)'", badge)
    need(match is not None and match.group(1) == package,
         "APK package identity differs from expected package")
    signed = command([str(signer), "verify", "--print-certs", str(path)], timeout=60)
    fingerprints = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", signed)
    need(len(fingerprints) >= 1, "APK lacks independently verified signer certificate")
    return {"package": package, "version_code": match.group(2),
            "apk_sha256": sha(path.read_bytes()), "signers": sorted(x.lower() for x in fingerprints),
            "signature_valid": True}


def stage_manifest(shadow_folder: Path, lane: str, source: str, run: int, attempt: int) -> dict:
    path = shadow_folder / "sfdt10/manifest.json"
    need(path.is_file() and not path.is_symlink() and path.stat().st_size <= 150_000,
         "Missing native captured evidence manifest")
    row = json.loads(path.read_text(encoding="utf-8"))
    need(row.get("kind") == "studyflow.sfdt10.native-debug-parity-evidence" and
         row.get("status") == "LOCAL_NATIVE_BYTES_CAPTURED_UNQUALIFIED" and
         row.get("lane") == lane and row.get("source_sha") == source and
         row.get("github_run_id") == run and row.get("github_run_attempt") == attempt,
         "Native manifest source/run/attempt mismatch")
    for key in ("legacy_equivalent", "deep_equivalent", "remote_authenticated", "cutover_authorized",
                "release_qualified", "device_qualified"):
        need(row.get(key) is False, "Native manifest claims forbidden authority")
    member_list = row.get("members")
    need(isinstance(member_list, list) and 1 <= len(member_list) <= 192,
         "Missing/excess native evidence members")
    seen = set()
    for member in member_list:
        rel = member.get("path")
        need(isinstance(rel, str) and re.fullmatch(r"[A-Za-z0-9_.\-/]+", rel) is not None
             and all(x not in (".", "..", "") for x in rel.split("/")) and rel not in seen,
             "Unsafe/duplicate member path")
        seen.add(rel)
        full = shadow_folder / "sfdt10/members" / rel
        need(full.is_file() and not full.is_symlink() and
             full.resolve().is_relative_to(shadow_folder.resolve()),
             "Missing/unsafe staged member")
        need(full.stat().st_size == member.get("size") and
             sha(full.read_bytes()) == member.get("sha256"), "Native member digest mismatch")
    native = row.get("native") or {}
    for prefix in ("path", "typed_path"):
        rel = native.get(prefix)
        need(isinstance(rel, str) and re.fullmatch(r"[A-Za-z0-9_.\-/]+", rel) is not None
             and all(x not in (".", "..", "") for x in rel.split("/")), "Unsafe native receipt path")
        f = shadow_folder / rel
        need(f.is_file() and not f.is_symlink() and
             f.resolve().is_relative_to(shadow_folder.resolve()) and
             sha(f.read_bytes()) == native.get("sha256" if prefix == "path" else "typed_sha256"),
             "Native receipt bytes mismatch")
    receipt = json.loads((shadow_folder / native["path"]).read_text())
    typed = json.loads((shadow_folder / native["typed_path"]).read_text())
    expect = {"phone-release-jvm": ("studyflow.sfdt08.native-coverage", "NATIVE_COVERAGE_PASSED"),
              "phone-debug": ("studyflow.sfdt07.native-hosted-shadow", "NATIVE_SHADOW_PASSED"),
              "tv-debug": ("studyflow.sfdt07.native-hosted-shadow", "NATIVE_SHADOW_PASSED")}[lane]
    need(receipt.get("kind") == expect[0] and receipt.get("status") == expect[1] and
         receipt.get("source", {}).get("source_sha") == source and
         typed.get("source_commit") == source and typed.get("run_id") == run and
         typed.get("attempt") == attempt and
         native.get("typed_receipt_sha256") == typed.get("receipt_sha256"),
         "Native typed/source receipt mismatch")
    canonical = {k: v for k, v in typed.items() if k != "receipt_sha256"}
    need(sha(json.dumps(canonical, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()) ==
         typed["receipt_sha256"], "Native receipt canonical digest mismatch")
    return row


def compare_downloaded(legacy: dict[str, Path], shadow: dict[str, Path],
                       *, source: str, run: int, attempt: int, aapt: Path, signer: Path) -> dict:
    results = []
    for lane, spec in LANES.items():
        module = spec["module"]
        manifest = stage_manifest(shadow[lane], lane, source, run, attempt)
        member_root = shadow[lane] / "sfdt10/members"
        row = {"lane": lane, "apk": None, "lint": None, "junit_cases": 0}
        if spec["apk"]:
            local_apk = exact_file(legacy["apk-" + module], spec["apk"])
            native_apk = exact_file(member_root, spec["apk"])
            left = apk_identity(local_apk, spec["package"], aapt=aapt, signer=signer)
            right = apk_identity(native_apk, spec["package"], aapt=aapt, signer=signer)
            need(left["version_code"] == right["version_code"],
                 "APK versionCode differs despite same source")
            row["apk"] = {"package": spec["package"], "version_code": left["version_code"],
                          "legacy_sha256": left["apk_sha256"], "native_sha256": right["apk_sha256"],
                          "signatures_valid": True,
                          "certificate_fingerprint_match": left["signers"] == right["signers"],
                          "digest_equality_not_required": True}
            before = lint_inventory(legacy["diag-" + module])
            after = lint_inventory(member_root)
            need(before == after, "Same-source lint issues differ for " + module)
            row["lint"] = {"matching_issues": True, "issue_count": sum(before.values())}
        if spec["tests"]:
            old = junit_inventory(legacy["diag-" + module])
            new = junit_inventory(member_root)
            need(old == new, "Same-source JUnit testcase identifiers/outcomes differ: " + lane)
            published = manifest.get("tests") or {}
            need(published.get("total") == sum(new.values()) and published.get("failed") == 0
                 and published.get("skipped") == sum(v for k, v in new.items() if k[2] == "SKIP")
                 and published.get("gate") == "PASS", "Native JUnit summary differs from actual members")
            row["junit_cases"] = sum(new.values())
        else:
            need((manifest.get("tests") or {}).get("gate") == "NOT_RUN", "Invented phone debug test gate")
        results.append(row)
    return {"schema_version": 1, "kind": "studyflow.sfdt10.r2.android-same-source-evidence",
            "source_sha": source, "status": "ANDROID_MEMBER_CONTRACT_MATCHED_UNQUALIFIED",
            "lanes": results, "legacy_equivalent": False, "cutover_authorized": False,
            "release_qualified": False, "device_qualified": False,
            "github_archive_bytes_sha256_verified": False,
            "note": "GitHub metadata and extracted members checked; ZIP archive transfer digest not independently checked; no CI cutover authority."}


def source_check(root: Path) -> None:
    for p in (".github/workflows/studyflow-ci.yml", SHADOW_WORKFLOW,
              "scripts/sfdt10_debug_evidence.py",
              ".github/workflows/studyflow-devtool-sfdt10-r2-compare.yml"):
        need((root/p).is_file(), "Missing reviewed R2 source: " + p)
    text = (root/".github/workflows/studyflow-devtool-sfdt10-r2-compare.yml").read_text()
    need("  workflow_dispatch:" in text and "  actions: read" in text and
         "  contents: read" in text and "GH_TOKEN: ${{ github.token }}" in text and
         not any("  " + x + ":" in text for x in ("push", "pull_request", "schedule")),
         "Hosted evidence comparison must remain manual/read-only")
    need(not any(x in text for x in ("id-token: write", "secrets.", "continue-on-error:",
                                 "environment: studyflow-signing", "gh release", "npm publish", "git push")),
         "Comparison must not acquire signer, token write or release authority")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source-check", action="store_true")
    p.add_argument("--repo", default=REPO)
    p.add_argument("--source", default="")
    p.add_argument("--legacy-run", type=int, default=0)
    p.add_argument("--shadow-run", type=int, default=0)
    p.add_argument("--output", type=Path)
    p.add_argument("--aapt", type=Path)
    p.add_argument("--apksigner", type=Path)
    args = p.parse_args()
    try:
        if args.source_check:
            source_check(Path(__file__).resolve().parents[1])
            print("SFDT10_R2_SOURCE_PASS: GH read-only/manual, no Gradle")
            return 0
        need(args.repo == REPO and SHA.fullmatch(args.source) is not None and
             args.legacy_run > 0 and args.shadow_run > 0 and
             args.legacy_run != args.shadow_run, "Invalid exact comparison identities")
        need(args.output is not None and not args.output.exists(), "Output exists/missing")
        need(args.aapt is not None and args.aapt.is_file() and
             args.apksigner is not None and args.apksigner.is_file(), "Pinned APK tools required")
        need(os.environ.get("GITHUB_ACTIONS") == "true" and
             os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and
             os.environ.get("RUNNER_OS") == "Linux" and
             os.environ.get("GH_TOKEN"), "Read-only hosted GH_TOKEN/manual runner required")
        legacy = api(f"repos/Mikeyphw/studyflow/actions/runs/{args.legacy_run}")
        shadow = api(f"repos/Mikeyphw/studyflow/actions/runs/{args.shadow_run}")
        old_attempt = run_check(legacy, number=args.legacy_run,
                                workflow=LEGACY_WORKFLOW, source=args.source)
        new_attempt = run_check(shadow, number=args.shadow_run,
                                workflow=SHADOW_WORKFLOW, source=args.source)
        jobs_check(api(f"repos/Mikeyphw/studyflow/actions/runs/{args.legacy_run}/jobs?per_page=100"),
                   {"Actions integration source contract", "web", "desktop",
                    "android (app, :app:testReleaseUnitTest)",
                    "android (app-tv, :app-tv:testDebugUnitTest)"})
        jobs_check(api(f"repos/Mikeyphw/studyflow/actions/runs/{args.shadow_run}/jobs?per_page=100"),
                   {"Source and signing isolation contract", "native-phone-debug",
                    "native-tv-debug", "native-phone-release-jvm"})
        old_art = artifact_index(api(f"repos/Mikeyphw/studyflow/actions/runs/{args.legacy_run}/artifacts?per_page=100"))
        new_art = artifact_index(api(f"repos/Mikeyphw/studyflow/actions/runs/{args.shadow_run}/artifacts?per_page=100"))
        old_names = {
            "apk-app": f"studyflow-app-ci-debug-{args.legacy_run}",
            "apk-app-tv": f"studyflow-app-tv-ci-debug-{args.legacy_run}",
            "diag-app": f"studyflow-app-diagnostics-{args.legacy_run}-{old_attempt}",
            "diag-app-tv": f"studyflow-app-tv-diagnostics-{args.legacy_run}-{old_attempt}",
        }
        new_names = {lane: f"studyflow-sfdt10-{lane}-{args.shadow_run}-{new_attempt}" for lane in LANES}
        with tempfile.TemporaryDirectory(prefix="sfdt10-r2-") as tmp:
            temp = Path(tmp)
            before = {key: download(args.legacy_run, name, temp/("legacy-"+key))
                      for key, name in old_names.items() if artifact_meta(old_art, name, args.legacy_run)}
            after = {key: download(args.shadow_run, name, temp/("shadow-"+key))
                     for key, name in new_names.items() if artifact_meta(new_art, name, args.shadow_run)}
            report = compare_downloaded(before, after, source=args.source, run=args.shadow_run,
                                        attempt=new_attempt, aapt=args.aapt, signer=args.apksigner)
            report.update(legacy_run=args.legacy_run, shadow_run=args.shadow_run,
                          legacy_attempt=old_attempt, shadow_attempt=new_attempt,
                          archive_metadata={"legacy": {k: old_art[n].get("digest") for k,n in old_names.items()},
                                            "shadow": {k: new_art[n].get("digest") for k,n in new_names.items()}})
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, sort_keys=True, indent=2)+"\n")
        print("SFDT10_R2_ANDROID_MEMBER_PARITY_PASS_UNQUALIFIED:", args.source[:12],
              "3 lanes; cutover=BLOCKED")
    except (ComparisonError, OSError, ValueError, TypeError, KeyError,
            subprocess.TimeoutExpired, json.JSONDecodeError) as exc:
        print("SFDT10_R2_REFUSED:", str(exc)[:320], file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
