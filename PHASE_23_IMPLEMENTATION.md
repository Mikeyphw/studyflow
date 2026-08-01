# Phase 23: Privacy, Encryption, and Backup Hardening

Delivered:
- optional password-encrypted `.studyplan` packages using a versioned AES-256-GCM envelope and
  PBKDF2-HMAC-SHA256 key derivation;
- streaming encryption and authenticated streaming restore through an app-private temporary file, authenticated
  headers, bounded decoding, password-memory cleanup, and a deterministic external compatibility vector;
- safe wrong-password and tamper failure while preserving unencrypted package compatibility;
- metadata privacy that uses a fresh export scope, pseudonymizes all portable identifiers and relationships,
  genericizes package metadata, reduces timestamp precision, uses a generic export filename, and keeps annex payload manifests on pseudonymous IDs;
- private-note redaction and history redaction that also clears embedded progress/completion traces;
- package invariants that reject privacy-declared archives still containing selected sensitive data;
- checksum-backed integrity reports and zero-write restore dry-runs;
- explicit recovery explanations and a truthful local data retention policy screen;
- Android 11 and Android 12+ backup-rule coverage without storing export passwords or encryption keys;
- bilingual UI, deterministic tests, durable phase-version contracts, and Phase 23 validation coverage.

Portable password backups are intentionally not Android-Keystore-bound because they must remain restorable on another
device. The password-derived archive key is never persisted. The cryptographic boundary and Tink evaluation are
recorded in `docs/architecture/ADR-0027-portability-privacy-encryption.md`.

This phase does not claim or implement cloud sync.
