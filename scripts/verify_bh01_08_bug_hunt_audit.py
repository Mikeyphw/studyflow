#!/usr/bin/env python3
"""Final source-contract seal for StudyFlow bug-hunt findings BH01 through BH08."""

from __future__ import annotations

from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

CHECKS = (
    ("BH01", "scripts/verify_csov05_manual_folder_sync.py"),
    ("BH02", "scripts/verify_bh02_sync_bundle_hardening.py"),
    ("BH02-CSOV02", "scripts/verify_csov02_sync_bundle.py"),
    ("BH03", "scripts/verify_bh03_installation_device_identity.py"),
    ("BH04", "scripts/verify_bh04_reminder_delivered_history.py"),
    ("BH05", "scripts/verify_bh05_durable_sync_ledger_history.py"),
    ("BH05-CSOV06", "scripts/verify_csov06_sync_ledger.py"),
    ("BH06", "scripts/verify_bh06_webdav_concurrent_index.py"),
    ("BH06-CSOV10", "scripts/verify_csov10_real_providers.py"),
    ("BH06-CSOV12", "scripts/verify_csov12_provider_setup_security.py"),
    ("BH07", "scripts/verify_bh07_desktop_schedule_repository_parity.py"),
    ("BH08", "scripts/verify_bh08_late_reminder_reconciliation.py"),
    ("BH08-PHASE29", "scripts/verify_phase_29.py"),
    ("BH08-PHASE54_55", "scripts/verify_phase_54_55.py"),
)


def fail(message: str) -> None:
    print(f"BH01_08_BUG_HUNT_AUDIT: FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    env = dict(os.environ)
    env.setdefault("TERM", "dumb")
    for label, relative in CHECKS:
        script = ROOT / relative
        if not script.is_file():
            fail(f"{label} verifier is missing: {relative}")
        result = subprocess.run(
            [sys.executable, str(script)],
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        output = result.stdout.strip()
        if output:
            print(output)
        if result.returncode != 0:
            fail(f"{label} verifier failed with exit {result.returncode}")

    bh08_doc = ROOT / "docs/quality/BH08_LATE_REMINDER_RECONCILIATION_ACCEPTANCE.md"
    bh08_text = bh08_doc.read_text(encoding="utf-8")
    if ":app:testReleaseUnitTest" not in bh08_text:
        fail("BH08 acceptance must name the repository's release unit-test task")
    if ":app:testDebugUnitTest" in bh08_text:
        fail("BH08 acceptance still names the unavailable debug unit-test task")

    print("BH01_08_BUG_HUNT_AUDIT: PASS")


if __name__ == "__main__":
    main()
