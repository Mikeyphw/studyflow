# Overlay 26 of 30 — Specialized content production data & UI

## Scope

OV26 closes the S11 specialized-content findings by replacing synthetic programming/language facades with canonical Android production data paths.

## Closed findings

- S11-01 — Programming and language plans now have production Room-backed repositories, entity mappers, data-layer wiring, and app container managers.
- S11-05 — The Programming Course workspace panel now reads persisted programming snapshots instead of schedule and generic question metrics.
- S11-06 — Language Deck plans now have a concrete workspace surface fed by persisted deck/expression/review evidence.
- S11-07 — Coding exercises and mini projects now make run/test command templates explicit mandatory invariants.
- S11-08 — Default Termux policies now provide safe fallback templates for every supported programming language bucket instead of crashing on empty template sets.
- S11-17 — Analytics now derives specialized progress/accuracy from programming exercises/projects and language review attempts when specialized evidence exists.
- S11-18 — Existing specialized preview parsers now have mode-gated apply workflows that persist programming or language rows instead of relabeling generic syllabus imports.

## Validation

Run:

```bash
bash scripts/validate_ov26_specialized_content_data_ui.sh
```

The validator is Gradle-independent for Termux/Devtool use and verifies the source contracts plus model syntax via `kotlinc` when available.
