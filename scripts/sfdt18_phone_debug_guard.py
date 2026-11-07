#!/usr/bin/env python3
"""SFDT18-HF01: bounded, diagnostic-only wrapper for hosted SFDT07 phone debug.

The actual Gradle task graph remains owned by Devtool run_node. This process
only forwards its output, detects prolonged *substantive* silence, records
bounded host/JVM diagnostics, and fails closed. No build retry or promotion.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import time

QUIET_SECONDS = 10 * 60
WALL_SECONDS = 42 * 60
MAX_BUFFER = 128 * 1024
HEARTBEAT = re.compile(r"(?:^|\s)Devtool heartbeat:.*\bgradlew\b", re.I)


def is_progress(line: str) -> bool:
    """Devtool's periodic heartbeat is not actual output from Gradle."""
    return bool(line.strip()) and HEARTBEAT.search(line) is None


def stop_reason(*, elapsed: float, quiet_for: float, quiet_limit: float,
                wall_limit: float) -> str | None:
    if elapsed >= wall_limit:
        return "wall_timeout"
    if quiet_for >= quiet_limit:
        return "gradle_output_stalled"
    return None


def _write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def _diagnostics(destination: Path, pid: int) -> list[str]:
    """Best-effort bounded diagnostics, never sends a signal to external PIDs."""
    failures: list[str] = []
    psfile = destination / "hf01-processes.txt"
    with psfile.open("w", encoding="utf-8") as out:
        try:
            subprocess.run(["ps", "-eo", "pid,ppid,etime,stat,comm"],
                           stdout=out, stderr=subprocess.STDOUT, timeout=6,
                           check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            failures.append("ps:" + type(exc).__name__)
    # Only inspect JVMs in our process group, not unrelated hosted builds.
    dumpfile = destination / "hf01-jvm-threads.txt"
    with dumpfile.open("w", encoding="utf-8") as out:
        try:
            pgid = os.getpgid(pid)
        except (ProcessLookupError, PermissionError):
            pgid = None
        seen = 0
        for proc in Path("/proc").iterdir():
            if not proc.name.isdecimal() or pgid is None:
                continue
            child_pid = int(proc.name)
            try:
                if child_pid == pid or os.getpgid(child_pid) != pgid:
                    continue
                comm = (proc / "comm").read_text(encoding="utf-8").strip()
                if comm != "java":
                    continue
                seen += 1
                if seen > 3:
                    break
                out.write(f"--- java PID {child_pid}; timeout 10s ---\n")
                out.flush()
                subprocess.run(["jcmd", str(child_pid), "Thread.print", "-l"],
                               stdout=out, stderr=subprocess.STDOUT,
                               timeout=10, check=False)
            except (ProcessLookupError, PermissionError, OSError, subprocess.TimeoutExpired) as exc:
                failures.append("java-dump:" + type(exc).__name__)
    return failures


def _terminate_group(proc: subprocess.Popen, *, grace: float = 5.0) -> None:
    try:
        os.killpg(proc.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        proc.wait(timeout=grace)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.wait(timeout=5)


def monitor(command: list[str], destination: Path, *, quiet_limit: float = QUIET_SECONDS,
            wall_limit: float = WALL_SECONDS, diagnostics: bool = True) -> int:
    """Runs the existing native lane, not Gradle; returns nonzero for any anomaly."""
    if not (command and quiet_limit > 0 and wall_limit > quiet_limit):
        raise ValueError("Invalid bounded hosted guard configuration")
    destination.mkdir(parents=True, exist_ok=True)
    started = last_progress = time.monotonic()
    count = 0
    recent = bytearray()
    reason = None
    proc = subprocess.Popen(command, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, bufsize=0, start_new_session=True)
    try:
        with selectors.DefaultSelector() as selector:
            assert proc.stdout is not None
            selector.register(proc.stdout, selectors.EVENT_READ)
            while True:
                ready = selector.select(timeout=0.25)
                for key, _ in ready:
                    data = os.read(key.fd, 65536)
                    if not data:
                        selector.unregister(key.fileobj)
                        continue
                    os.write(sys.stdout.fileno(), data)
                    recent.extend(data)
                    if len(recent) > MAX_BUFFER:
                        # Incomplete line overflow is not valid progress evidence.
                        del recent[:len(recent) - MAX_BUFFER]
                    while b"\n" in recent:
                        raw, _, tail = recent.partition(b"\n")
                        recent = bytearray(tail)
                        if is_progress(raw.decode("utf-8", "replace")):
                            last_progress = time.monotonic()
                            count += 1
                now = time.monotonic()
                # Exit only after the merged output pipe has closed, too.
                if proc.poll() is not None and not selector.get_map():
                    break
                reason = stop_reason(elapsed=now-started,
                                     quiet_for=now-last_progress,
                                     quiet_limit=quiet_limit, wall_limit=wall_limit)
                if reason:
                    print("SFDT18_HF01_REFUSED:", reason, flush=True)
                    warnings = _diagnostics(destination, proc.pid) if diagnostics else []
                    _write_json(destination / "hf01-guard.json", {
                        "status": "REFUSED", "reason": reason,
                        "substantive_lines": count,
                        "diagnostics_warnings": warnings,
                        "source_sha": os.environ.get("GITHUB_SHA", ""),
                        "run_id": os.environ.get("GITHUB_RUN_ID", ""),
                        "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
                        "cutover_authorized": False})
                    _terminate_group(proc)
                    return 124
    finally:
        if proc.poll() is None:
            _terminate_group(proc)
        if proc.stdout is not None:
            proc.stdout.close()
    rc = proc.returncode
    _write_json(destination / "hf01-guard.json", {
        "status": "NATIVE_EXIT_ZERO" if rc == 0 else "NATIVE_EXIT_NONZERO",
        "exit_code": rc, "substantive_lines": count,
        "source_sha": os.environ.get("GITHUB_SHA", ""),
        "run_id": os.environ.get("GITHUB_RUN_ID", ""),
        "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
        "cutover_authorized": False})
    return rc if rc != 0 else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    env = os.environ
    if (env.get("GITHUB_ACTIONS") != "true" or
            env.get("GITHUB_EVENT_NAME") != "workflow_dispatch" or
            env.get("RUNNER_OS") != "Linux" or env.get("SFDT_LANE") != "phone-debug" or
            env.get("GITHUB_REPOSITORY") != "Mikeyphw/studyflow"):
        print("SFDT18_HF01_REFUSED: unreviewed hosted identity", file=sys.stderr)
        return 2
    workspace = Path(env.get("GITHUB_WORKSPACE", "/missing")).resolve()
    runner_temp = Path(env.get("RUNNER_TEMP", "/missing")).resolve()
    if (not runner_temp.is_dir() or runner_temp == workspace or
            runner_temp.is_relative_to(workspace) or args.out.is_symlink() or
            args.out.resolve().is_relative_to(workspace) or
            args.out.resolve().parent != runner_temp / "sfdt07"):
        print("SFDT18_HF01_REFUSED: unsafe evidence output", file=sys.stderr)
        return 2
    command = [sys.executable, "-u", "-B", "scripts/sfdt07_native_lane.py",
               "--lane", "phone-debug", "--execute", "--out", str(args.out)]
    return monitor(command, runner_temp / "sfdt07")


if __name__ == "__main__":
    raise SystemExit(main())
