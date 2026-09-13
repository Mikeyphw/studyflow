#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROOTS = ("app", "app-tv", "core", "feature", "desktopApp", "build-logic")
EXCLUDED = {"build", ".gradle", ".idea"}
failures: list[str] = []

for root_name in ROOTS:
    root = ROOT / root_name
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".kts", ".xml"}:
            continue
        if any(part in EXCLUDED for part in path.parts):
            continue
        data = path.read_bytes()
        rel = path.relative_to(ROOT)
        if not data.endswith(b"\n"):
            failures.append(f"{rel}: missing final newline")
        try:
            source = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            failures.append(f"{rel}: invalid UTF-8: {exc}")
            continue
        for number, line in enumerate(source.splitlines(), 1):
            if "\t" in line:
                failures.append(f"{rel}:{number}: tab character")
            if line != line.rstrip():
                failures.append(f"{rel}:{number}: trailing whitespace")
            if path.suffix in {".kt", ".kts"} and len(line) > 120:
                failures.append(f"{rel}:{number}: {len(line)} characters")

if failures:
    print("FORMATTING: FAIL")
    for failure in failures:
        print(f"- {failure}")
    raise SystemExit(1)
print("FORMATTING: PASS")
