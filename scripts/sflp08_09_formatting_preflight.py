#!/usr/bin/env python3
"""Fast source-only formatting gate for the precise SFLP08/09 owned set.

Intentionally does not sweep the full historic Kotlin tree: unrelated source debt
has its own ownership/qualification gate. Uses the authoritative native verifier's
candidate inventory, not a divergent subset. No Gradle or network needed.
"""
from __future__ import annotations

from verify_sflp08_09_android_desktop_integration import validate_line_lengths


def main() -> None:
    validate_line_lengths()
    print("SFLP08_09_EARLY_FORMATTING: PASS")


if __name__ == "__main__":
    main()
