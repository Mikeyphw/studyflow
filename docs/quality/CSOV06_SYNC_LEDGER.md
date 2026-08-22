# CSOV06 Sync Ledger Acceptance

StudyFlow records optional sync state as durable device and checkpoint ledger facts.

Required guarantees:

- A local device has a stable id, display name, first-seen time, and last-seen time.
- Checkpoints record the creating device, database revision, local change token, change count, upload state, remote location, restore-preview state, and conflict count.
- Manual upload and remote-check actions append ledger events.
- Restore previews are recorded before any future restore path can apply remote data.
- Cloud remains optional: a ledger can exist without a required StudyFlow account.
