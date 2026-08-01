# Phase 17 Acceptance

- App-private annex copies can be created from an input stream.
- Stored annex files include byte size, MIME type, display name, storage reference, and SHA-256.
- Deleting a stored annex removes both metadata and the private file when present.
- Orphan cleanup deletes unreferenced private annex files.
- `.studyplan` packages support optional annex payload entries under `annex-payloads/`.
- Annex payload ZIP entries are checksum verified and path traversal safe.
- Metadata-only exports remain supported.
- No Room migration is introduced.
