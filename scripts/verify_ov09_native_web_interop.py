#!/usr/bin/env python3
"""Standalone OV09 native/Web interop source-contract verifier."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
result = subprocess.run(
    [sys.executable, str(ROOT / "scripts" / "verify_ov09_sflp_native_web_interop.py")],
    cwd=ROOT,
)
if result.returncode:
    raise SystemExit(result.returncode)
print("OV09_NATIVE_WEB_INTEROP: PASS")
