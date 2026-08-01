# Phase 17: Annex File Storage and Portability

Phase 17 closes the metadata-only annex gap.

## Added

- `AnnexFileStorageRepository` and `ManageAnnexFileStorage` in `:core:domain`.
- `RoomAnnexFileStorageRepository` in `:core:data`.
- App-private annex file copies under `study-plans/<planId>/annexes/<annexId>/`.
- SHA-256 checks for stored annex files.
- Orphan annex cleanup contract.
- `.studyplan` annex payload entries under `annex-payloads/`.
- Library screen file picker for attaching annex files.
- Phase 17 verifier and acceptance documentation.

## Schema

No Room migration is required. The existing `annexes` table already contains storage mode,
storage reference, size, and checksum fields.
