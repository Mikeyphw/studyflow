# SFDT22 HF29 — Stable production subject FAB acceptance

Evidence: SFDT22 full hosted run 42866697226 against HF28 source 9476e99580ac7e069c0766b92ff04967bc986afa reported `phase=subjects`, `failure_step=subject_open_dialog`, `failure_reason_code=ambiguous_compose_node`; source and the five Room/SFLP regressions passed. One of five UI journeys completed, and the full gate remained red.

Root cause at the contract boundary: `onNodeWithContentDescription` did not uniquely identify the subject creation control. The test now targets one production `FloatingActionButton` by a stable `testTag`, requires exactly one actionable, visible and enabled FAB, performs an actual touch, and independently requires exactly one editable subject-name field before continuing. No fake repository, seeded row, direct navigation, skip, synthetic success, or timeout extension is used.

The source marker allowlist now covers `subject_fab_ready` and `subject_editor_ready`, in addition to the existing `subject_open_dialog`. An Android instrumentation failure in any stage remains red, with sanitized diagnostic classification. The focused workflow runs this added contract, and the full workflow checks SHA-256 of all changed and new files. Five UI journeys, seven PNG screenshots, all Room and SFLP regressions, a genuine process restart/new PID, and independent source/emulator gates remain mandatory.

This overlay is **not** proof of Android runtime success. Full hosted qualification must pass at the new reviewed SHA before release or merge.
