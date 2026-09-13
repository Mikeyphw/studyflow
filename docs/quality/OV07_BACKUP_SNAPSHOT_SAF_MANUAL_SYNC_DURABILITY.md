# OV07 — Backup, snapshot, SAF & manual-sync durability

Overlay 7 of 30 closes S3-09 through S3-15, DS2-05, DS2-10, DS2-11, DS2-12, DS2-41, and S12-08 from the final deduplicated audit.

## Contracts

- Repeated annex-bearing Study Plan restore is replacement-safe. Existing deterministic payload destinations are preserved as rollback backups, new bytes are checksum-verified, and the old bytes are removed only after the database commit succeeds.
- Desktop workspace backup/restore refuses symbolic links (symlinks), resolves the workspace boundary without following aliases, enforces the same 2 GiB encrypted limit as restore, and reserves headroom before opening the encrypted archive writer.
- Plan portability import grants are deliberately short-lived: the selected SAF read grant is released when the import is replaced, completed, abandoned, or the screen is disposed.
- Portable manifests require both unique annex archive paths and unique annex IDs.
- Snapshot deletion uses a reversible tombstone. The database row is restored if physical deletion fails, so private snapshot bytes never become silently untracked.
- Snapshot write and resolve paths share the same sanitized Study Plan segment and canonical-path checks.
- Manual-folder sync is no longer preview-only. The apply action authenticates and stages the checkpoint privately, validates the logical snapshot, stages annex bytes, replaces the Room graph transactionally, verifies the restored graph revision/integrity, and records `RESTORE_APPLIED` only after success.
- Manual-folder and Plan Portability secrets are retained as wipeable `CharArray` state, copied only for an operation, and zeroed on success, failure, cancellation/disposal, or navigation away.
- Restore preview decrypts authenticated plaintext to a bounded private temp file and streams only manifest/checkpoint metadata instead of materializing the decrypted archive in a `ByteArrayOutputStream`.
- Manual checkpoint upload captures the logical Room snapshot inside a transaction, then performs annex SAF/file hashing outside the transaction. A graph-revision retry rejects a mixed checkpoint if user data changes while annexes are inspected.
- Explicit annex and snapshot deletion fail closed around physical-byte deletion. Persisted linked-document grants are also verified released when their last reference is removed.

## Evidence

- `StudyFlowSyncBundleCodecTest.streamingPreviewAndStageAvoidWholeArchiveHeapMaterialization`
- `StudyPlanPackageCodecAnnexPayloadTest.writerRejectsDuplicateAnnexIdsEvenWhenEntryNamesDiffer`
- `ManualSyncDatabaseRestoreOv07Test.logicalSnapshotRestoreReplacesMutatedGraphAtomically`
- `ManualFolderSyncIntegrationContractTest.manual folder sync is backed by real SAF archive io`
- `StudyPlanPortabilityAtomicRestoreTest.repeatedAnnexBearingSnapshotRestoreAtomicallyReplacesExistingDestination`
- `scripts/verify_ov07_backup_snapshot_saf_manual_sync.py`
- `scripts/validate_ov07_backup_snapshot_saf_manual_sync.sh`

The validator compiles Android instrumentation tests on every run. When an authorized ADB/Termux-ADB device is available, it additionally executes the connected core-data and app instrumentation suites.
