# Offline-first and Portability Contract

## Offline guarantees

- Opening and switching Study Plans requires no network.
- Structured plan data is locally authoritative.
- Locally copied annexes remain accessible offline.
- Linked documents clearly report unavailable providers or revoked permissions.
- Direct and web media may require connectivity, but their metadata and progress remain local.
- Background work must be retryable and must never silently discard local mutations.

## Export guarantees

The final `.studyplan` package will contain a versioned manifest, stable English field names,
structured JSON, checksums, and optional annex payloads. Imports are staged and validated before
committing database or file changes.

## Conflict guarantees

Importing never silently overwrites an existing plan. The user chooses to create a new plan, replace
an existing plan, or merge supported structures after seeing a preview.

## Failure guarantees

A failed import or export leaves the original Study Plan unchanged. Temporary files are cleaned by a
recovery-safe maintenance job.
