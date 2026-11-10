# SFDT22 / HF27 — DIAG-01 diagnostic staging

This is a **diagnostic-only** change to the HF04 + Targeted CI baseline, not the discarded HF05 patch.
No persistence repair, default regeneration, replaced UI journey, local Gradle build, PR merge or release approval is included.

## Recorded facts versus inferences

A debuggable app writes an allowlisted, monotonically sequenced, max-64-event/max-16-KiB ASCII trace
atomically in its private files directory. The hosted runner reads it using `run-as`, rejects unexpected
fields/values/order/encoding/size, and reserializes JSON into the existing failure receipt. Raw files,
plan IDs, names, settings, hashes, SQL, exceptions and instrumentation logs are never uploaded.
Release builds install no writer. A trace failure is `capture_unavailable`, never a positive result.

Each real UI journey is a distinct Android instrumentation invocation. The host captures
DIAG-01 events immediately after the subject/topic journey, **before** invoking
Quick Session, because an instrumentation-process restart can replace the
private debug trace. Failure during the topic journey captures immediately in
the exception path. This prevents a later process from erasing the causal window.

The `production_room` observer records settings presence **inside** the active Room transaction.
`TOPIC_TRANSACTION_COMMIT` is emitted only after `database.withTransaction` returns successfully.
`TOPIC_TRANSACTION_ROLLBACK` means the transaction scope threw, not independent proof about WAL visibility.
`independent_pair` labels the existing second Room / read-only SQLite combination, not the production handle.

`TOPIC_UI_CLICK`, `TOPIC_COROUTINE_START`, `TOPIC_REPOSITORY_RETURN`, and
`TOPIC_UI_FEEDBACK_REQUESTED` are distinct. Feedback requested is *not* proof that the snackbar became visible.
`writer_unresolved` remains a valid (failed) diagnosis until actual writer provenance is established.

## Limitations and next proof

The trace instruments the main plan, subject, topic and progress operations, not every possible external
writer. Events cannot alone establish absence of an uninstrumented concurrent worker. A first-missing event
with no preceding traced destructive write is `writer_unresolved` and **must not** trigger an automatic fix.
If two observer handles disagree, classify snapshot mismatch instead of claiming a DELETE.
DIAG-01 must compile and run on hosted Android API 36 before acceptance can be asserted.

Follow-up: inspect the exact-SHA focused three-journey receipt; if the first missing event is attributable
to a specific writer after a confirmed commit, add only the minimal settings-preserving fix and expand real
file-backed regressions. Full gate continues to require five production UI tests, seven unique screenshots,
a force-stop/new PID, and all existing source/instrumentation checks on a reviewed PR SHA.
