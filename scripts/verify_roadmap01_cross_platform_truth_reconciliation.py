#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROADMAP = ROOT / "docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md"
MATRIX = ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md"
INDEX = ROOT / "docs/roadmap/FINAL_ROADMAP_INDEX.md"
REMAINING = ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json"
IMPLEMENTATION = ROOT / "ROADMAP01_CROSS_PLATFORM_TRUTH_RECONCILIATION.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ROADMAP01 verification failed: {message}")


def require_contains(text: str, needle: str, where: str) -> None:
    require(needle in text, f"{where} is missing required text: {needle!r}")


def require_absent(text: str, needle: str, where: str) -> None:
    require(needle not in text, f"{where} still contains stale text: {needle!r}")


def main() -> None:
    roadmap = ROADMAP.read_text(encoding="utf-8")
    matrix = MATRIX.read_text(encoding="utf-8")
    index = INDEX.read_text(encoding="utf-8")
    implementation = IMPLEMENTATION.read_text(encoding="utf-8")
    contract = json.loads(REMAINING.read_text(encoding="utf-8"))

    require(contract.get("schema_version") == 1, "remaining-work schema_version must be 1")
    require(contract.get("contract") == "studyflow.cross_platform.remaining_work", "unexpected contract id")

    baseline = contract.get("baseline", {})
    require(baseline.get("android_product_phase") == "Phase 64", "Android product baseline must remain Phase 64")
    require(baseline.get("sflp_terminal") == "SFLP24", "SFLP24 must remain terminal")
    require(baseline.get("sflp_status") == "complete", "SFLP series must be marked complete")
    require(baseline.get("no_phase_65") is True, "contract must forbid an implied Phase 65")
    require(baseline.get("no_sflp_25") is True, "contract must forbid an implied SFLP25")

    reconciliation = contract.get("historical_reconciliation", {})
    require(reconciliation.get("old_sflp18_presence") == "PRESENCE01", "old SFLP18 Presence must map to PRESENCE01")
    require(reconciliation.get("old_sflp19_full_mesh_seal") == "PARITY04", "old SFLP19 full-mesh seal must map to PARITY04")
    require(reconciliation.get("shipped_sflp18") == "notification_and_scheduling_sync", "shipped SFLP18 identity drift")
    require(reconciliation.get("shipped_sflp19") == "media_and_material_streaming_handoff", "shipped SFLP19 identity drift")

    tracks = contract.get("remaining_tracks")
    require(isinstance(tracks, list), "remaining_tracks must be a list")
    expected_ids = ["DOV08-DOV24", "TVOV27-TVOV31", "WEB20", "PRESENCE01", "PARITY04", "REL-FINAL"]
    require([item.get("id") for item in tracks] == expected_ids, "remaining-track order drifted")
    require([item.get("order") for item in tracks] == list(range(1, 7)), "remaining-track order numbers must be contiguous")

    parity = next(item for item in tracks if item.get("id") == "PARITY04")
    require(
        parity.get("prerequisites") == ["DOV24", "TVOV31", "WEB20", "PRESENCE01"],
        "PARITY04 prerequisites drifted",
    )
    rel_final = next(item for item in tracks if item.get("id") == "REL-FINAL")
    require(rel_final.get("status") == "pending_execution", "REL-FINAL must remain execution-pending")
    require(rel_final.get("prerequisites") == ["PARITY04", "REL01-REL08"], "REL-FINAL prerequisites drifted")

    require_contains(roadmap, "## StudyFlow Link Protocol — SFLP01-SFLP24 — COMPLETE", "connectivity roadmap")
    require_contains(roadmap, "SFLP18 Notification and scheduling sync — COMPLETE", "connectivity roadmap")
    require_contains(roadmap, "SFLP19 Media/material streaming handoff — COMPLETE", "connectivity roadmap")
    require_contains(roadmap, "## Presence — PRESENCE01", "connectivity roadmap")
    require(
        "PARITY04 is the canonical successor" in roadmap
        or "**PARITY04 is implemented.** It is the canonical successor" in roadmap,
        "connectivity roadmap must preserve or complete the PARITY04 successor transition",
    )
    require_contains(roadmap, "CROSS_PLATFORM_REMAINING_WORK.json", "connectivity roadmap")
    require_absent(roadmap, "**SFLP18 Presence**", "connectivity roadmap")
    require_absent(roadmap, "**SFLP19 Full-mesh interoperability seal**", "connectivity roadmap")
    require_absent(roadmap, "## StudyFlow Link Protocol — SFLP01-SFLP19", "connectivity roadmap")

    for stale in (
        "Web/TV bindings remain SFLP10-SFLP12",
        "no universal SFLP peer binding yet; SFLP10 remains",
        "until SFLP11 generalizes TV",
        "until SFLP12",
        "When SFLP19 is complete",
    ):
        require_absent(matrix, stale, "capability matrix")
    require_contains(matrix, "StudyFlow has completed the SFLP01-SFLP24 protocol series", "capability matrix")
    require(
        "PARITY04 is still required" in matrix or "PARITY04 is **SUPPORTED / SEALED**" in matrix,
        "capability matrix must describe PARITY04 as planned or sealed",
    )
    require_contains(matrix, "### Presence truth", "capability matrix")
    require_contains(matrix, "PRESENCE01", "capability matrix")

    require_contains(index, "## Current post-SFLP cross-platform completion track", "final roadmap index")
    for item in expected_ids:
        require_contains(index, item, "final roadmap index")
    require_contains(index, "There is no SFLP25", "final roadmap index")
    require_contains(index, "no Phase 65", "final roadmap index")

    require_contains(implementation, "ROADMAP01", "implementation note")
    require_contains(implementation, "DOV08-DOV24 → TVOV27-TVOV31 → WEB20 → PRESENCE01 → PARITY04 → REL-FINAL", "implementation note")

    print("ROADMAP01_CROSS_PLATFORM_TRUTH_RECONCILIATION: PASS")


if __name__ == "__main__":
    main()
