# Phase 23 Acceptance

- An encrypted package is not a readable ZIP and cannot be decoded without its password.
- Wrong passwords and modified ciphertext fail authentication; temporary plaintext is discarded and never imported.
- Large encrypted packages are restored through a bounded app-private streaming path instead of a whole-file byte array.
- Existing unencrypted exports remain readable.
- Integrity inspection verifies package checksums and reports privacy boundaries.
- Restore dry-run returns `databaseRowsChanged = 0` and does not invoke graph import.
- Metadata privacy uses a fresh per-export scope and pseudonymizes root, primary, and foreign identifiers consistently.
- Metadata-private annex payload manifests and ZIP entry paths use only pseudonymous annex IDs.
- Identical source identifiers cannot be linked across separate metadata-private exports.
- Note and history redaction remove their selected tables and declare those omissions in the manifest.
- Password-derived portable keys are not persisted or device-bound, preserving cross-device restore behavior.
- The privacy center states the real local retention and recovery behavior without claiming cloud sync.
