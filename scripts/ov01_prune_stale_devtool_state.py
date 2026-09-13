#!/usr/bin/env python3
"""Remove only stale Devtool runtime registry/history files that must never be repository truth."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
paths = (
    ROOT / ".devtool/artifacts/registry.json",
    ROOT / ".devtool/artifacts/registry.lock",
    ROOT / ".devtool/test-history.json",
)
for path in paths:
    if path.exists():
        path.unlink()
        print(f"removed {path.relative_to(ROOT)}")
    else:
        print(f"already absent {path.relative_to(ROOT)}")
print("Devtool will recreate runtime registry/history as needed.")
