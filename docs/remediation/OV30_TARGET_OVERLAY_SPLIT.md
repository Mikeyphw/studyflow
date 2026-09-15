# OV30 — one overlay per Devtool target

Overlay 30 of 30 is delivered as a target-specific split instead of one mixed artifact.

The final aggregate seal is intentionally placed in the `studyflow_parity` target after the Android, Desktop, Web, TV, Release, TV Release, and Quality target overlays have landed. It verifies that each Devtool target owns a dedicated OV30 verifier and that release/device readiness remains evidence-based and fail-closed.
