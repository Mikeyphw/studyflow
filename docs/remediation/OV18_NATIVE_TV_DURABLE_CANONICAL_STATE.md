# OV18 — Native TV durable canonical state & action journal

Overlay 18 of 30 closes the native Android TV durability findings by moving volatile TV-only state behind durable, explicit contracts without making the TV a StudyFlow data owner.

## Closed findings

- `S8-01` — Canonical TV mutations no longer live only in an in-memory Activity outbox. `StudyFlowTvDomainActionOutbox` accepts a persistence adapter and the Android TV Activity wires it to `AndroidStudyFlowTvDomainActionOutboxPersistence`.
- `S8-02` — Local TV command IDs no longer reset when the Activity is recreated. `StudyFlowTvDurableCommandSequencer` commits the next sequence before exposing each `tv-local-*` ID.
- `S8-03` — A full outbox now returns explicit `domain-action-outbox-full` backpressure instead of silently removing the oldest pending action.
- `S8-07` — Command and presentation freshness no longer depends on unsynchronized phone/TV wall clocks. Duplicate command IDs and snapshot IDs remain replay barriers.
- `S8-18` — Destination, search, and material-viewer cursors are stored in `StudyFlowTvDurableUiStateStore` so presentation state survives Activity recreation.
- `S8-19` — Native TV transport declares the SFLP trust boundary as canonical and commits local phone↔TV session credentials synchronously before any paired success is published.
- `DS2-27` — The domain-action outbox uses explicit backpressure and preserves all pending canonical mutations until acknowledged.
- `DS2-44` — Phone and TV session save/revoke paths use synchronous SharedPreferences `commit()` and pairing reports failure if credential commit fails.

## Validation

`verifyOv18NativeTvDurableStateActionJournal` runs during artifact apply. Its shell hook does not invoke `gradle --stop` and the verifier checks the durable outbox, command sequencer, wall-clock stale removal, UI-state store, synchronous credential commits, tests, and documentation closure evidence.
