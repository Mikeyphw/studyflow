# CSOV02 Sync Bundle Acceptance

CSOV02 adds the portable sync checkpoint artifact that future providers will move.

Acceptance criteria:

- Sync bundles declare `com.mikeyphw.studyflow.sync` and schema version 1.
- The canonical archive entries are `manifest.json`, `checkpoint.json`, `database.snapshot`, `attachments/`, and `integrity.sha256`.
- The manifest records checkpoint id, device id/name, creation time, graph revision, change count, database size, and SHA-256 database hash.
- Integrity is checked for every archive entry before restore code can use it.
- Remote-ready bundles must be written through the existing AES-GCM authenticated envelope and must not expose the raw database snapshot bytes.
- The file extension for provider transfer is `.sfbackup`.
