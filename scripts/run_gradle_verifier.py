#!/usr/bin/env python3
"""Run a Gradle-owned verifier command and materialize its declared success stamp."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys
import time


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stamp", required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        parser.error("missing verifier command after --")

    completed = subprocess.run(command, check=False)
    if completed.returncode != 0:
        return completed.returncode

    stamp = Path(args.stamp)
    stamp.parent.mkdir(parents=True, exist_ok=True)
    stamp.write_text(
        "status=PASS\n"
        f"command={command!r}\n"
        f"pid={os.getpid()}\n"
        f"completed_epoch_millis={int(time.time() * 1000)}\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
