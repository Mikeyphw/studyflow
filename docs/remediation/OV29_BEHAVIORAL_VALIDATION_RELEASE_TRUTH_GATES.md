# OV29 — Behavioral validation, debug labs & release-truth gates

Overlay 29 of 30 closes S12-04, S12-05, S12-10, S13-01, S13-02, S13-03, S13-04, S13-05, S13-06, S13-07, S13-08, S13-09, S13-10, DS2-13, and DS2-30.

The validation layer now depends on executable validator exit code evidence instead of checked-in status assertions. The CMOV final seal runs every registered CMOV validator and then verifies production flow wiring. Debug Workbench database and backup labs now fail closed on current-schema corruption and missing modern data coverage. Web release integrity compares compiled runtime hashes to dist hashes so stale dist assets cannot certify a release. REL-FINAL wiring includes the OV29 exhaustive verifier inventory before the terminal OV30 physical-evidence pass.

OV29 remains an intermediate overlay: use `--no-validate` for apply, then run `scripts/validate_ov29_behavioral_validation_release_truth_gates.sh` for the focused gate.
