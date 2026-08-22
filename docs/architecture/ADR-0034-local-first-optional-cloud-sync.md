# ADR-0034: Local-first optional cloud sync

## Decision

StudyFlow is no longer limited to device-only sync language. The product contract is local-first with optional cloud sync:

- fresh installs work fully offline;
- no StudyFlow account or cloud provider is required;
- sync starts off with provider none;
- automatic sync starts off;
- remote-backup encryption starts on;
- local checkpoints remain available without provider setup.

## Rationale

Users may want backup and multi-device continuity, but StudyFlow should not turn cloud setup into an onboarding gate. Optional sync lets users opt into a provider later while preserving the current offline study workflow.

## Consequences

- User-facing copy must say optional cloud sync rather than promising that sync can never use a provider.
- The domain model owns off-by-default sync settings before provider upload/download is implemented.
- Provider selection, encrypted bundle upload, remote checkpoint discovery, merge, and conflicts remain future CSOV overlays.
