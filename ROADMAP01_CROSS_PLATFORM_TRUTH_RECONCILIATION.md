# ROADMAP01 — Cross-platform truth reconciliation

ROADMAP01 reconciles the post-REL13 cross-platform roadmap without changing runtime behavior.

## Decisions

- SFLP01-SFLP24 are the completed, terminal StudyFlow Link Protocol series.
- Shipped SFLP18 is notification/scheduling sync; shipped SFLP19 is media/material streaming handoff.
- The older draft's `SFLP18 Presence` promise was never satisfied by transport peer-health or `lastSeen` bookkeeping. It remains planned as `PRESENCE01`.
- The older draft's `SFLP19 Full-mesh interoperability seal` is now canonically owned by `PARITY04`.
- There is no SFLP25 and no Android product Phase 65 implied by the remaining cross-platform track.
- The remaining order is DOV08-DOV24 → TVOV27-TVOV31 → WEB20 → PRESENCE01 → PARITY04 → REL-FINAL evidence execution.

## Machine-readable contract

`docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json` records the ordered remaining tracks, prerequisite edges, terminal milestones, and historical promise remapping. `scripts/verify_roadmap01_cross_platform_truth_reconciliation.py` checks that the human roadmap, capability matrix, final roadmap index, and JSON contract stay aligned.

## Non-goals

ROADMAP01 does not implement Desktop parity, TVOV27+, WEB20 semantics, Presence, PARITY04 behavior, or release evidence. It only makes those promises explicit and machine-checkable so later seals cannot silently erase or renumber them.
