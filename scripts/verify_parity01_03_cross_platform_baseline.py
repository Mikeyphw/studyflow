#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


implementation = read("PARITY01_03_CROSS_PLATFORM_BASELINE.md")
matrix = read("docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md")
roadmap = read("docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md")
scope = read("docs/product/SCOPE_BOUNDARIES.md")
release = read("docs/release/RELEASE_NOTES_1.0.0_RC1.md")
readme = read("README.md")
index = read("docs/roadmap/FINAL_ROADMAP_INDEX.md")
changelog = read("CHANGELOG.md")

require("SFLP" in implementation and "PARITY03" in implementation, "baseline implementation note is incomplete")

for client in ("Android", "Web", "Desktop", "Android TV", "Web TV"):
    require(client in matrix, f"capability matrix missing client: {client}")
for status in ("FULL", "LIMITED", "READ_ONLY", "HANDOFF", "PLATFORM_SPECIFIC", "NOT_APPLICABLE", "MISSING"):
    require(status in matrix, f"capability matrix missing status vocabulary: {status}")
require("Current connectivity truth" in matrix, "matrix must distinguish current connectivity from target mesh")
require("Target connectivity matrix" in matrix, "matrix missing full-mesh target")
require("Android TV | FULL | FULL | FULL" in matrix, "target matrix does not expose non-phone TV peers")

for phase in ("SFLP01", "SFLP07", "SFLP12", "SFLP19", "DOV08", "DOV24", "TVOV27", "TVOV31", "PARITY04"):
    require(phase in roadmap, f"cross-platform roadmap missing milestone: {phase}")
require("Android becomes a generic SFLP peer rather than the TV hub" in roadmap, "roadmap still privileges Android as hub")
require("pair Web TV or Android TV directly with Desktop/Web" in roadmap, "roadmap missing non-phone TV connectivity acceptance")

require("desktop or web companion." not in scope.lower(), "current scope still claims desktop/web companion is absent")
require("provider-backed upload, download, and merge flows are staged" not in readme, "README still says implemented sync I/O is staged")
require("it does not compile `:app-tv` until TVOV01" not in readme, "README still describes pre-TVOV01 target state")
require("current repository also contains later Web, Desktop, Android TV" in release, "RC1 notes lack historical/current-state disambiguation")
require("- Desktop or web companion." not in release, "RC1 notes retain the stale bare Desktop/Web exclusion bullet")
require("No Desktop or Web companion shipped as part of this historical Android RC1 milestone." in release, "RC1 notes do not preserve the historical boundary explicitly")
require("historical roadmap sequencing" in index, "roadmap index does not distinguish historical sequencing from current truth")
require("Cross-platform parity baseline" in changelog, "changelog missing PARITY01-PARITY03 baseline entry")

# PARITY03: preserve and pass the existing OV20 seal; do not weaken it.
# Re-run the later TVOV source seals touched only by formatting cleanup so the repair cannot
# accidentally satisfy formatting while breaking a roadmap contract.
for verifier in (
    "scripts/verify_ov20_final_integration.py",
    "scripts/verify_tvov21_presentation_projection.py",
    "scripts/verify_tvov22_local_pairing_transport.py",
    "scripts/verify_tvov23_phone_controller.py",
    "scripts/verify_tvov24_cast_state_synchronization.py",
    "scripts/verify_tvov25_domain_persistence_convergence.py",
    "scripts/verify_tvov26_end_to_end_physical_seal.py",
):
    result = subprocess.run(
        [sys.executable, str(ROOT / verifier)],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        FAILURES.append(f"{verifier} remains red:\n" + (result.stdout + result.stderr).strip())

if FAILURES:
    print("PARITY01_03_CROSS_PLATFORM_BASELINE: FAIL")
    for failure in FAILURES:
        print(f"- {failure}")
    raise SystemExit(1)

print("PARITY01_03_CROSS_PLATFORM_BASELINE: PASS")
