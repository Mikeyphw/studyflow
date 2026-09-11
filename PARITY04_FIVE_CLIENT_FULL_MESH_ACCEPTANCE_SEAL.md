# PARITY04 — Five-client full-mesh/product-parity acceptance seal

PARITY04 closes the historical full-mesh interoperability promise after DOV24, TVOV31, WEB20, and PRESENCE01.
It adds no Phase 65 and no SFLP25. SFLP24 remains the terminal protocol milestone.

## Acceptance contract

The seal exercises Android, Desktop, Web, Android TV, and Web TV as five simultaneous SFLP peers. It proves all
10 direct pair combinations, three writable-client concurrent edits, convergence on both TV peers, exactly-once
completion, direct Desktop/Web-to-TV pairing without Android as coordinator, activity continuation, presentation to
both TV implementations, capability-based rejection, bounded loop-free routing, ephemeral Presence coexistence,
portable/canonical serialization interoperability, and WEB20 semantic equivalence.

`protocol/parity/v1/five-client-scenarios.json` is the language-neutral scenario corpus. The JVM full-mesh integration
suite executes real loopback SFLP servers and authenticated reconciliation for the five production client capability
profiles. Web executes the same corpus against its production handoff/presentation/presence/semantic/portable
runtimes. Desktop consumes the same corpus and the WEB20 fixture rather than maintaining an independent parity
model.

## Exactly-once and offline conflict rules

Operation IDs remain the durable dedupe boundary. Repeated reconciliation may increase transport attempts but must
not create a second journal copy. Offline writers may create the same entity revision independently; convergence is
resolved by the existing deterministic conflict handler, not by arrival order. Activity handoff completion is likewise
one-time consumed.

## TV authority

Android TV and Web TV remain presentation/execution peers. PARITY04 does not grant repository ownership, authoring,
writing, import, or restore authority to TV clients. Unsupported operations are rejected through the negotiated
capability contract or TV write-safety policy rather than silently ignored.

## Semantic and portability reuse

PARITY04 reuses `protocol/semantic/v1/golden-vectors.json` verbatim. The WEB20 40-vector suite remains the sole
cross-language semantic truth for readiness, scheduling, scoring, SRS, study-time accounting, goals, Error Notebook,
canonical portable JSON, and SFLP conflict resolution. Portable acceptance also checks canonical JSON behavior and
existing Desktop/Web backup/import gates; it does not invent a second package format.

## Completion

After this seal passes, cross-platform product implementation is complete. The only remaining canonical roadmap item
is **REL-FINAL evidence execution**, which runs the existing REL01–REL08 release machinery and records signed
artifact/device/restart/restore/interoperability evidence.
