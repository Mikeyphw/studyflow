# Debug Workbench D1 r2: Recorder and Privacy Foundation

D1 r2 establishes and seals the backend for the StudyFlow Debug Workbench. It supersedes the original D1 overlay,
which was rejected before landing after an adversarial promise audit found privacy, rotation, and failure-export gaps.
It deliberately adds no new screen.

## Runtime contract

- `:core:diagnostics` is a pure Kotlin module with no Android, Room, Compose, or networking dependency.
- `DiagnosticEvent` records a stable area, severity, machine action, outcome, duration, correlation fingerprint,
  bounded details, and a sanitized exception category/fingerprint without an exception message.
- Generic detail values such as reason, result, source, status, and task are fingerprinted. Only narrowly typed
  booleans, integers, build types, versions, locales, and timezones can remain readable.
- Caller-created exception summaries are sanitized again when the event is serialized, so forged model values cannot
  bypass the privacy boundary.
- `NoOpDiagnosticRecorder` is the Release-build default and creates no files.
- Debug builds create `RollingJsonlDiagnosticRecorder` under app-private `files/diagnostics/` storage.
- Recording is asynchronous through a bounded queue. A full queue increments a dropped-event counter instead of
  blocking the application thread.
- The active JSONL file is limited to 2 MiB. Rotation filenames contain a monotonic sequence so multiple rotations in
  the same millisecond cannot overwrite one another, and five rotated sessions are retained.

## Initial real instrumentation

D1 records only lifecycle and startup facts with stable action names:

1. application container creation;
2. notification-channel bootstrap;
3. data-layer initialization;
4. reminder reconciliation;
5. Today-widget refresh.

The wrappers preserve exceptions and coroutine cancellation. They do not alter success or failure behavior.

## Privacy contract

The redactor removes, fingerprints, or tightly validates:

- Authorization and cookie values;
- passwords, passphrases, tokens, signatures, sessions, secrets, and keys;
- plan names, titles, labels, notes, questions, answers, bodies, and free-form content;
- generic status/reason/result/source/task values;
- plan, subject, topic, review, attempt, exam, reminder, annex, and generic identifiers;
- absolute paths, file locations, and raw URLs or content/file URIs;
- email addresses, JWT-like values, UUIDs, exception messages, and forged exception fields.

Support bundles use a fixed application-generated entry allowlist and contain no database, DataStore,
SharedPreferences, annex, plan-package, Room WAL/SHM, raw path, or raw URI payloads.

## Support-bundle skeleton

D1 can build a local ZIP with only:

- `events/current.jsonl` when a readable timeline exists;
- `environment.json` with redacted caller-supplied metadata;
- `manifest.json` with session, queue, flush, and writer-health evidence;
- `redaction-report.txt`;
- `checksums.sha256` covering every preceding entry.

If the writer has failed, export can still produce a manifest through the caller thread. The manifest sets
`flushCompleted=false` and `timelineMayBeIncomplete=true`, and includes the sanitized writer-failure fingerprint.
There is no share UI and no automatic upload.

## Validation boundary

D1 r2 adds 18 behavior tests covering redaction, forged event models, session expiry, disabled-mode zero side effects,
concurrent recording, deterministic queue overflow, same-millisecond rotation collisions, writer-failure export,
fixed support-bundle entries, and checksum verification. The embedded validator independently compiles and exercises
those adversarial paths instead of relying only on markers.
