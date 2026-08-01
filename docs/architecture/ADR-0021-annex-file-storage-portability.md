# ADR-0021: Annex File Storage and Portability

## Decision

StudyFlow stores imported annex bytes in app-private storage and references them from the existing
`annexes.storageReference` column. `.studyplan` packages can optionally include those bytes in
`annex-payloads/` entries with SHA-256 checksums.

## Rationale

The MVP already modeled annex metadata. Reusing the existing table avoids a schema migration while
closing the largest resource gap: physical annex files now survive app restart, snapshots, and full
package exports.

## Safety

- Package entry names are deterministic and path-traversal checked.
- Payload checksums are verified independently of metadata checksums.
- Missing local annex files are skipped from export rather than corrupting the package.
- Merge imports reject annex payload restoration until stable remap IDs are available.
