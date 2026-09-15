#!/usr/bin/env python3
from __future__ import annotations
import subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
run = subprocess.run(["node", "scripts/assert_runtime_dist_fresh.mjs"], cwd=ROOT / "web", text=True, capture_output=True)
if run.returncode != 0:
    print(run.stdout, end="")
    print(run.stderr, end="")
    raise SystemExit(run.returncode)
print(run.stdout, end="")
