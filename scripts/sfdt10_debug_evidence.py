#!/usr/bin/env python3
"""SFDT10-R1: independently inventory native Devtool Android debug/JVM outputs.

This binds local hosted source, native receipts and actual bytes. Remote artifact
producer authenticity, legacy equivalence, cutover and release stay unqualified.
Never executes Gradle or reads signing credentials.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEVTOOL_SHA = "0ebd22c17ac3058704012a1e03ee3265328b0659"
SHA40 = re.compile(r"[0-9a-f]{40}\Z")
LANES = {
    "phone-debug": {"module": "app", "apk": "app/build/outputs/apk/debug/app-debug.apk",
                    "receipt": "sfdt07/receipt.json", "junit": None},
    "tv-debug": {"module": "app-tv", "apk": "app-tv/build/outputs/apk/debug/app-tv-debug.apk",
                 "receipt": "sfdt07/receipt.json", "junit": "app-tv/build/test-results/testDebugUnitTest"},
    "phone-release-jvm": {"module": "app", "apk": None,
                          "receipt": "sfdt08/receipt.json", "junit": "app/build/test-results/testReleaseUnitTest"},
}


class EvidenceError(ValueError):
    pass


def need(ok: bool, message: str) -> None:
    if not ok:
        raise EvidenceError(message)


def regular(root: Path, rel: str, *, limit: int) -> Path:
    need(isinstance(rel, str) and re.fullmatch(r"[A-Za-z0-9_.\-/]+", rel) is not None,
         "Unsafe evidence member path")
    need(all(part not in ("", ".", "..") for part in rel.split("/")), "Traversal member path")
    need(root.is_dir() and not root.is_symlink(), "Unsafe evidence root")
    p = root
    for part in rel.split("/"):
        p = p / part
        need(not p.is_symlink(), "Symlinked evidence member")
    need(p.is_file() and stat.S_ISREG(p.stat().st_mode) and 0 < p.stat().st_size <= limit,
         "Missing/oversized evidence member: " + rel)
    need(p.resolve().is_relative_to(root.resolve()), "Evidence escaped root")
    return p


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parsed_junit(blob: bytes) -> tuple[int, int, int]:
    try:
        root = ET.fromstring(blob)
    except ET.ParseError as exc:
        raise EvidenceError("Malformed JUnit report") from exc
    suites = [root] if root.tag == "testsuite" else list(root) if root.tag == "testsuites" else []
    need(bool(suites) and all(s.tag == "testsuite" for s in suites), "Invalid JUnit suite")
    total = failures = skipped = 0
    for suite in suites:
        cases = list(suite.findall("testcase"))
        observed = {"tests": len(cases),
                    "failures": sum(bool(c.findall("failure")) for c in cases),
                    "errors": sum(bool(c.findall("error")) for c in cases),
                    "skipped": sum(bool(c.findall("skipped")) for c in cases)}
        for key, count in observed.items():
            text = suite.get(key, "0")
            need(text.isdecimal() and int(text) == count, "JUnit header/testcase mismatch: " + key)
        total += observed["tests"]
        failures += observed["failures"] + observed["errors"]
        skipped += observed["skipped"]
    return total, failures, skipped


def native_record(temp: Path, lane: str, source: str, run_id: int, attempt: int) -> dict:
    path = regular(temp, LANES[lane]["receipt"], limit=256 * 1024)
    obj = json.loads(path.read_text(encoding="utf-8"))
    expected_kind = ("studyflow.sfdt08.native-coverage" if lane == "phone-release-jvm"
                     else "studyflow.sfdt07.native-hosted-shadow")
    expected_status = "NATIVE_COVERAGE_PASSED" if lane == "phone-release-jvm" else "NATIVE_SHADOW_PASSED"
    need(obj.get("kind") == expected_kind and obj.get("status") == expected_status
         and obj.get("lane") == lane and obj.get("native_graph_executed") is True,
         "Native Devtool job did not pass")
    stamp = obj.get("source") or {}
    need(stamp.get("source_sha") == source and stamp.get("runtime_sha") == DEVTOOL_SHA
         and stamp.get("run_id") == run_id and stamp.get("attempt") == attempt
         and isinstance(stamp.get("source_tree"), str)
         and SHA40.fullmatch(stamp["source_tree"]) is not None,
         "Native Devtool source/runtime/run/attempt mismatch")
    for key in ("legacy_equivalent", "release_qualified", "device_qualified", "cutover_authorized"):
        need(obj.get(key) is False, "Native receipt improperly promotes: " + key)
    if lane == "phone-release-jvm":
        job_id, digest = obj.get("job_id"), obj.get("native_receipt_sha256")
        need(obj.get("provider") == "android-gradle" and obj.get("test_gate") == "PASS"
             and (obj.get("junit") or {}).get("local_verified") is True,
             "Missing native typed JVM test proof")
    else:
        node = obj.get("node") or {}
        job_id, digest = node.get("job_id"), node.get("native_receipt_sha256")
        need(node.get("provider") == "android-gradle" and node.get("remote_authenticated") is False,
             "Android debug provider/node mismatch")
    need(re.fullmatch(r"node-[0-9]{2}(?:-[a-z0-9-]+)?", str(job_id)) is not None,
         "Unreviewed native job identity")
    native = regular(temp, f"rm06-evidence/{job_id}/node-evidence.json", limit=128 * 1024)
    typed = json.loads(native.read_text(encoding="utf-8"))
    need(typed.get("receipt_sha256") == digest and typed.get("source_commit") == source
         and typed.get("run_id") == run_id and typed.get("attempt") == attempt
         and typed.get("release_qualified") is False
         and typed.get("authenticated_remote") is False,
         "Typed node receipt identity/trust mismatch")
    canonical = {k: v for k, v in typed.items() if k != "receipt_sha256"}
    need(sha(json.dumps(canonical, sort_keys=True, separators=(",", ":"),
                        ensure_ascii=True).encode("ascii")) == digest,
         "Typed node canonical receipt checksum mismatch")
    return {"path": LANES[lane]["receipt"], "sha256": sha(path.read_bytes()),
            "typed_path": f"rm06-evidence/{job_id}/node-evidence.json",
            "typed_sha256": sha(native.read_bytes()), "typed_receipt_sha256": digest,
            "graph_sha256": typed.get("graph_sha256"), "source_tree": stamp["source_tree"]}


def collect(root: Path, temp: Path, dest: Path, lane: str, *, source: str, run_id: int,
            attempt: int, apksigner: Path | None = None) -> dict:
    need(lane in LANES and SHA40.fullmatch(source) is not None and run_id > 0 and attempt > 0,
         "Invalid lane/source/run identity")
    need(not dest.exists() and not dest.is_symlink(), "Output evidence directory exists")
    need(dest.resolve().is_relative_to(temp.resolve()) and not dest.is_relative_to(root.resolve()),
         "Evidence must remain in runner temp")
    native = native_record(temp, lane, source, run_id, attempt)
    spec = LANES[lane]
    rows: list[dict] = []
    total_bytes = 0

    def stage(rel: str, role: str, *, cap: int) -> bytes:
        nonlocal total_bytes
        src = regular(root, rel, limit=cap)
        blob = src.read_bytes()
        total_bytes += len(blob)
        need(total_bytes <= 350 * 1024 * 1024 and len(rows) < 192,
             "Evidence exceeds bounded upload budget")
        dst = dest / "members" / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        need(not dst.exists(), "Duplicate output evidence")
        dst.write_bytes(blob)
        need(sha(dst.read_bytes()) == sha(blob), "Staged evidence bytes changed")
        rows.append({"role": role, "path": rel, "size": len(blob), "sha256": sha(blob)})
        return blob

    apk = spec["apk"]
    if apk:
        blob = stage(apk, "debug-apk", cap=250 * 1024 * 1024)
        need(blob[:2] == b"PK", "Debug APK does not look like an APK ZIP")
        need(apksigner is not None and apksigner.is_file() and not apksigner.is_symlink(),
             "Pinned Android apksigner is absent")
        proc = subprocess.run([str(apksigner), "verify", "--print-certs", str(root / apk)],
                              text=True, capture_output=True, timeout=45, check=False)
        need(proc.returncode == 0 and "Signer #1" in proc.stdout,
             "Debug APK signature verification failed")
        module = spec["module"]
        lint = root / module / "build/reports"
        need(lint.is_dir() and not lint.is_symlink(), "Missing lint report directory")
        files = sorted(x for x in lint.iterdir() if x.is_file() and not x.is_symlink()
                       and re.fullmatch(r"lint-results-[A-Za-z0-9_.-]+\.(xml|html|sarif)", x.name))
        need(any(x.suffix == ".xml" for x in files), "No lint XML evidence produced")
        need(len(files) <= 15, "Too many lint report members")
        for file in files:
            rel = f"{module}/build/reports/{file.name}"
            data = stage(rel, "lint-" + file.suffix[1:], cap=16 * 1024 * 1024)
            if file.suffix == ".xml":
                try: document = ET.fromstring(data)
                except ET.ParseError as exc: raise EvidenceError("Malformed lint XML") from exc
                need(document.tag == "issues", "Unrecognized lint XML root")
            if file.suffix == ".sarif":
                need(isinstance(json.loads(data), dict), "Malformed lint SARIF")
    aggregate = {"total": 0, "failed": 0, "skipped": 0, "gate": "NOT_RUN"}
    junit_dir = spec["junit"]
    if junit_dir:
        folder = root / junit_dir
        need(folder.is_dir() and not folder.is_symlink(), "Required JUnit output directory missing")
        tests = sorted(p for p in folder.glob("TEST-*.xml") if p.is_file() and not p.is_symlink())
        need(0 < len(tests) <= 160, "Required JUnit inventory missing/oversized")
        for p in tests:
            data = stage(junit_dir + "/" + p.name, "junit-xml", cap=5 * 1024 * 1024)
            n, f, s = parsed_junit(data)
            aggregate["total"] += n
            aggregate["failed"] += f
            aggregate["skipped"] += s
        need(aggregate["total"] > 0 and aggregate["failed"] == 0
             and aggregate["total"] > aggregate["skipped"],
             "No successful qualifying JVM tests")
        aggregate["gate"] = "PASS"
        if lane == "phone-release-jvm":
            # SFDT08 typed receipt must independently match the same JVM report path.
            typed = json.loads(regular(temp, native["typed_path"], limit=128*1024).read_text())
            need((typed.get("typed_evidence") or {}).get("tests", {}).get("total") == aggregate["total"],
                 "Typed test count differs from independently enumerated JVM reports")
    dest.mkdir(parents=True, exist_ok=True)
    outcome = {"schema_version": 1, "kind": "studyflow.sfdt10.native-debug-parity-evidence",
               "status": "LOCAL_NATIVE_BYTES_CAPTURED_UNQUALIFIED", "lane": lane,
               "source_sha": source, "devtool_sha": DEVTOOL_SHA,
               "github_run_id": run_id, "github_run_attempt": attempt,
               "native": native, "members": rows, "tests": aggregate,
               "lint_xml_required": bool(apk),
               "legacy_equivalent": False, "deep_equivalent": False,
               "remote_authenticated": False, "cutover_authorized": False,
               "release_qualified": False, "device_qualified": False}
    (dest / "manifest.json").write_text(json.dumps(outcome, indent=2, sort_keys=True) + "\n")
    return outcome


def source_check(root: Path = ROOT) -> None:
    required = [".devtool.toml", "scripts/sfdt07_native_lane.py",
                "scripts/sfdt08_native_coverage.py", "scripts/sfdt07_consolidated_contract.py",
                "scripts/sfdt08_contract.py", ".github/workflows/studyflow-ci.yml",
                ".github/workflows/studyflow-hosted-sign.yml",
                ".github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml"]
    for name in required:
        p = root / name
        need(p.is_file() and not p.is_symlink(), "Missing reviewed source: " + name)
    legacy = (root / ".github/workflows/studyflow-ci.yml").read_text()
    need(all(f"  {name}:\n" in legacy for name in ("source", "web", "desktop", "android", "deep-contracts")),
         "Authoritative legacy jobs were changed/removed")
    signer = (root / ".github/workflows/studyflow-hosted-sign.yml").read_text()
    need("environment: studyflow-signing" in signer and
         "secrets.STUDYFLOW_SIGNING_P12_BASE64" in signer, "Protected signer boundary missing")
    workflow = (root / required[-1]).read_text()
    need("  workflow_dispatch:\n" in workflow and "  contents: read\n" in workflow,
         "Manual-only read-only shadow missing")
    need(not any("  " + x + ":" in workflow for x in ("pull_request", "push", "schedule"))
         and all(x not in workflow for x in ("secrets.", "id-token: write", "continue-on-error:",
                                       "environment: studyflow-signing")),
         "Shadow gained unreviewed event or privileged authority")
    need(workflow.count(DEVTOOL_SHA) == 1, "Workflow runtime pin mismatched")
    for lane in LANES:
        need(workflow.count("lane: " + lane + "\n") == 1,
             "Native shadow matrix lane missing or duplicated: " + lane)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--source-check", action="store_true")
    mode.add_argument("--lane", choices=sorted(LANES))
    ap.add_argument("--out", type=Path)
    args = ap.parse_args(argv)
    try:
        if args.source_check:
            source_check()
            print("SFDT10_R1_SOURCE_ONLY_PASS: shadow-only, cutover BLOCKED")
        else:
            need(args.out is not None, "Missing output directory")
            env = os.environ
            need(all(env.get(k) == v for k, v in (("GITHUB_ACTIONS", "true"),
                                                  ("GITHUB_EVENT_NAME", "workflow_dispatch"),
                                                  ("RUNNER_OS", "Linux"), ("RUNNER_ARCH", "X64"))),
                 "Collector restricted to hosted manual GitHub Actions")
            source = env.get("GITHUB_SHA", "")
            need(SHA40.fullmatch(source) is not None and env.get("EXPECTED_SHA") == source,
                 "Requested SHA differs from checked-out source")
            git = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"],
                                 capture_output=True, text=True, check=True, timeout=12)
            need(git.stdout.strip() == source, "Checkout commit mismatch")
            temp = Path(env["RUNNER_TEMP"]).resolve()
            signer = Path(env["ANDROID_HOME"]) / "build-tools/36.0.0/apksigner"
            result = collect(ROOT, temp, args.out, args.lane, source=source,
                             run_id=int(env["GITHUB_RUN_ID"]), attempt=int(env["GITHUB_RUN_ATTEMPT"]),
                             apksigner=signer)
            print("SFDT10_R1_NATIVE_OUTPUT_CAPTURED:", result["lane"],
                  "members=", len(result["members"]), "cutover=BLOCKED")
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError,
            subprocess.TimeoutExpired) as exc:
        print("SFDT10_R1_REFUSED:", str(exc)[:350], file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
