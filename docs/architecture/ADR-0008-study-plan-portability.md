# ADR-0008: Study Plan portability packages

## Decision

StudyFlow exports one Study Plan as a versioned `.studyplan` ZIP package containing:

- `manifest.json` with format identity, schema, privacy mode, row counts, and capabilities;
- `payload.json` with typed relational rows for the root and owned graph;
- `checksums.json` with SHA-256 digests for the manifest and payload.

The archive codec is a pure Kotlin module. Room graph extraction and insertion remain in
`:core:database`, while Android storage and private snapshots remain in `:core:data`.

## Safety

- ZIP entry names are allowlisted and traversal paths are rejected.
- Entry and package sizes are bounded before JSON decoding.
- Checksums are verified before preview or import.
- Table names, column names, and row counts must match the supported schema.
- Imports execute inside Room transactions.
- Replace and merge operations create a private pre-import snapshot.
- Imported plans are paused; templates are drafts.
- Physical annex bytes are not claimed as portable before Phase 9. Annex metadata is sanitized.

## Conflict strategies

- `CREATE_AS_NEW` remaps the complete graph to a new UUID.
- `REPLACE_EXISTING` snapshots and atomically replaces the selected plan.
- `MERGE_COMPATIBLE` keeps root/settings/one-to-one target strategy data and merges independently
  remapped compatible child content.

## Consequences

The package is independent from Room's physical schema and can be migrated by explicit package
schema versions. Snapshots use the exact same codec, avoiding a second restore format.
