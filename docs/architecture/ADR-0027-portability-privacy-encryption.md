# ADR-0027: Authenticated portable backup encryption

## Status

Accepted for Phase 23.

## Context

A `.studyplan` backup must remain portable between devices and installations. Android Keystore keys are intentionally
non-exportable and device-bound, so binding the archive key to Keystore would make a password-protected backup
unrestorable on another device. The export password must also never be persisted.

The project already has a checksum-verified ZIP package with bounded entries and exact inventory validation. Phase 23
needs optional confidentiality and metadata privacy without replacing that inner compatibility format.

## Decision

Keep the existing checksum-verified ZIP as the inner `.studyplan` format. Optional password protection wraps the
entire package in a versioned AES-256-GCM envelope. A per-export salt and nonce are random; the 256-bit archive key is
derived with PBKDF2-HMAC-SHA256 and is never stored. The serialized envelope header is authenticated as associated
data.

The portable archive key is intentionally **not** Android-Keystore-bound. Keystore remains appropriate for future
non-portable, device-local secrets, but not for a user-carried password backup.

The implementation uses platform JCA primitives in the pure JVM portability module. Tink was evaluated, but adding an
AEAD wrapper would not remove the password-KDF contract and would introduce a new dependency without improving this
archive's cross-platform key derivation boundary. A future format version may adopt Tink only with an explicitly
compatible password-derived key interface and migration story.

Encrypted restore is streamed into a private cache temporary file. The temporary file is consumed only after GCM
authentication succeeds, is never exposed outside app-private storage, and is deleted on every exit path. This avoids
holding a valid large backup entirely in memory while preserving the rule that unauthenticated plaintext is never
imported.

Metadata-private exports use a fresh random export scope. All portable primary and foreign identifiers are
pseudonymized consistently within that export and differently across exports, preventing stable cross-export linking.

Restore inspection and dry-run decode and validate packages without invoking graph import, snapshot creation, file
copy, or any database mutation. Privacy redaction changes the exported table inventory and is declared in manifest
metadata so import validation remains exact.

## Consequences

- Password loss is unrecoverable; the application cannot derive or retrieve the password later.
- Wrong passwords, modified headers, and modified ciphertext fail authentication before import.
- Unencrypted packages remain compatible with the pre-Phase-23 reader path.
- Password archives remain transferable between StudyFlow installations.
- Device-local key storage and cloud sync are outside this phase.
