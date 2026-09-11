#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/roadmap/REL_FINAL_PROMISE_AUDIT.json"
DEFAULT_REPORT = ROOT / "build/reports/studyflow/rel-final-roadmap-audit.json"
EXTERNAL = {"verify_final_release_artifact.py", "verify_final_release_verdict.py"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def run(script: str, timeout: int) -> dict[str, Any]:
    env = os.environ.copy()
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    process = subprocess.Popen(
        [sys.executable, str(ROOT / "scripts" / script)],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        start_new_session=True,
    )
    try:
        stdout, stderr = process.communicate(timeout=timeout)
        code = process.returncode
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        stdout, stderr = process.communicate()
        code = 124
        stderr = (stderr or "") + f"\nverifier timed out after {timeout}s"
    output = ((stdout or "") + (stderr or "")).strip()
    return {"script": script, "returncode": code, "output": output[-12000:]}


def prove_external_fail_closed(script: str, timeout: int) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="studyflow-rel-final-") as tmp:
        tmp_path = Path(tmp)
        missing = tmp_path / "missing.json"
        command = [sys.executable, str(ROOT / "scripts" / script)]
        if script == "verify_final_release_artifact.py":
            command += ["--host", str(missing), "--report", str(tmp_path / "artifact.json")]
        else:
            command += [
                "--host", str(missing), "--artifact", str(missing), "--connected", str(missing),
                "--journey", str(missing), "--device", str(missing), "--report", str(tmp_path / "verdict.json"),
            ]
        completed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=timeout)
        output = (completed.stdout + completed.stderr).strip()
        blocked = completed.returncode != 0 and ("BLOCKED" in output or "missing" in output.lower())
        return {
            "script": script,
            "returncode": completed.returncode,
            "classification": "EXPECTED_EXTERNAL_EVIDENCE_GATE" if blocked else "UNEXPECTED_RESULT",
            "output": output[-12000:],
        }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run every legacy StudyFlow roadmap/product verifier for REL-FINAL.")
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()

    ledger = json.loads(LEDGER.read_text(encoding="utf-8"))
    scripts = list(ledger["verifier_inventory"]["legacy_scripts"])
    static = [name for name in scripts if name not in EXTERNAL]
    results: list[dict[str, Any]] = []
    failures: list[str] = []

    workers = max(1, min(args.workers, 4))
    aggregate: list[str] = []
    parallel: list[str] = []
    for script in static:
        source = (ROOT / "scripts" / script).read_text(encoding="utf-8", errors="ignore")
        (aggregate if "subprocess." in source else parallel).append(script)

    completed_count = 0
    with ThreadPoolExecutor(max_workers=workers) as executor:
        future_to_script = {executor.submit(run, script, args.timeout_seconds): script for script in parallel}
        for future in as_completed(future_to_script):
            script = future_to_script[future]
            completed_count += 1
            try:
                result = future.result()
            except Exception as exc:
                result = {"script": script, "returncode": 99, "output": f"audit runner exception: {exc}"}
            results.append(result)
            state = "PASS" if result["returncode"] == 0 else "FAIL"
            print(f"[{completed_count:03d}/{len(static):03d}] {state} {script}", flush=True)
            if result["returncode"] != 0:
                failures.append(script)
                print(result.get("output", "")[-4000:], flush=True)

    for script in aggregate:
        completed_count += 1
        result = run(script, args.timeout_seconds)
        results.append(result)
        state = "PASS" if result["returncode"] == 0 else "FAIL"
        print(f"[{completed_count:03d}/{len(static):03d}] {state} {script}", flush=True)
        if result["returncode"] != 0:
            failures.append(script)
            print(result.get("output", "")[-4000:], flush=True)
    results.sort(key=lambda item: item["script"])

    external_results = [prove_external_fail_closed(name, args.timeout_seconds) for name in sorted(EXTERNAL)]
    bad_external = [item["script"] for item in external_results if item["classification"] != "EXPECTED_EXTERNAL_EVIDENCE_GATE"]
    status = "PASS" if not failures and not bad_external else "FAIL"
    report = {
        "schemaVersion": 1,
        "kind": "studyflow.rel_final.roadmap_audit_result",
        "status": status,
        "generatedAtUtc": utc_now(),
        "legacyVerifierCount": len(scripts),
        "staticVerifierCount": len(static),
        "staticPassCount": sum(item["returncode"] == 0 for item in results),
        "staticFailures": failures,
        "externalEvidenceGates": external_results,
        "productImplementationComplete": status == "PASS",
        "releaseEvidenceComplete": False,
        "releaseReady": False,
        "notes": [
            "External release-artifact and final-verdict gates are proven fail-closed against isolated missing evidence.",
            "This audit does not synthesize signing, connected-device, restart/restore, or operator evidence.",
        ],
        "results": results,
    }
    out = Path(args.report).expanduser().resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if status != "PASS":
        print("REL_FINAL_EXHAUSTIVE_ROADMAP_AUDIT: FAIL")
        for name in failures + bad_external:
            print(f"- {name}")
        return 1
    print(f"REL_FINAL_EXHAUSTIVE_ROADMAP_AUDIT: PASS ({len(static)}/{len(static)} static; 2/2 external fail-closed)")
    print(f"Report: {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
