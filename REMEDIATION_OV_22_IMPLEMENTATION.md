# Remediation OV22 — Desktop persistence, atomicity, restore & file safety

Overlay 22 of 30 closes the Desktop persistence wave after OV21 sealed the unified TV control plane.

## Baseline

- Roadmap position: Overlay 22 of 30.
- Previous overlay: OV21 — Unified TV control, pairing, relay, cast & attention plane.
- User-applied state: cumulative StudyFlow remediation through OV21.

## Findings closed

- `S9-01` — Desktop event-thread blocking from normal UI actions.
- `S9-02` — Subject/topic deletion left references in schedule, session, practice and review stores.
- `S9-03` — Linked Desktop session creation was non-transactional across files.
- `S9-04` — Practice/mock submission could commit attempts before wrong-answer evidence committed.
- `S9-05` — Backup restore replaced disk while running repositories retained stale in-memory graphs.
- `S9-06` — Desktop encrypted backup was not staged as a point-in-time snapshot.
- `S9-07` — Restore validated only core workspace state and skipped auxiliary feature stores.
- `S9-08` — Older Desktop workspaces could fail because `contentMode` became mandatory without tolerant migration.
- `S9-09` — Desktop lacked a single-instance workspace lock to prevent multi-process lost updates.
- `S12-06` — Desktop workspace/trust files lacked explicit restrictive permissions.
- `S12-09` — Desktop encrypted-backup password remained in immutable Compose `String` state.
- `DS2-38` — Desktop linked session/activity transitions were not atomic across persistence sections.

## Implementation

### Nonblocking Desktop actions

`DesktopAppState` now delegates normal user-triggered mutations to `DesktopUiActionRunner`, which uses a `SupervisorJob` on `Dispatchers.Default`. The Desktop search state also uses synchronous repository snapshots instead of `runBlocking` from the service-level state builder, so common UI state derivation no longer blocks the Compose event thread.

### Referential cleanup

Subject/topic permanent deletion now calculates the full removed topic set, including descendants, then scrubs schedule rules/activities, sessions, practice questions, review items and error notebook references. This prevents orphan references from surviving after content deletion.

### Linked-session atomicity

Desktop linked session create/transition operations snapshot the previous session graph and linked planned activity before mutating either side. If any schedule/session/cycle write fails, the session list and linked activity are restored and the failure is surfaced as a structured atomicity error.

### Practice/mock evidence ordering

Practice and mock submission now delivers wrong-answer evidence before the final attempt row is committed. A failed evidence sink prevents the submitted attempt from being persisted.

### Restore and backup correctness

Encrypted backup creation runs through a point-in-time staging snapshot before encryption. Restore now validates the core workspace plus auxiliary Desktop stores, replaces the tree with a safety rollback, and reloads all running repositories from disk immediately after success or rollback. The returned restore result no longer requires a restart to avoid stale memory/disk divergence.

### Migration, locking and file secrecy

Workspace loading uses tolerant `contentModeOrDefault` migration for legacy rows. `DesktopWorkspacePersistence` owns an exclusive `.studyflow-workspace.lock` using `FileChannel.tryLock()`. Workspace and Desktop SFLP/trust persistence writes apply owner-only POSIX permissions where supported.

### Backup secret lifecycle

The backup password is now held as a mutable `CharArray`, displayed only as a bullet mask, copied only for operation execution, and zeroed after backup/restore completion.

## Validation

The overlay adds `verifyOv22DesktopPersistenceAtomicRestoreFileSafety`. The artifact validation hook runs that Gradle task during Devtool apply and does not stop Gradle daemons.

The source validator checks the nonblocking UI runner, no `runBlocking` in `DesktopAppState`/Desktop service state paths, referential cleanup hooks, linked-session rollback, wrong-answer evidence ordering, restore memory reload, point-in-time backup staging, auxiliary restore validation, content-mode migration, single-instance locking, owner-only file permissions, password zeroing, and embedded validation hook behavior.
