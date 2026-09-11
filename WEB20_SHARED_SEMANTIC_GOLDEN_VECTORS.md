# WEB20 — Shared Semantic Golden Vectors

WEB20 freezes language-neutral cross-platform semantics in `protocol/semantic/v1/golden-vectors.json`. The 40 vectors cover readiness thresholds, schedule generation/rebalancing, question and mock scoring, SRS progression, pause-aware study-time accounting, goal progress, Error Notebook state, canonical portable JSON, and deterministic SFLP conflict winners.

The Kotlin core test executes every vector against production domain/link/portability engines. Web executes the same JSON through committed TypeScript runtime. Desktop independently consumes all 40 vectors through a Desktop-facing adapter backed by the same production contracts. Positive, negative, threshold/boundary, and deterministic conflict-tie cases are represented.

`PARITY04` must reuse this fixture rather than defining a competing semantic suite. `PRESENCE01` is now complete; `PARITY04` is the next roadmap batch and must reuse these vectors.
