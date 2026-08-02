# Phase 28 Backup/Restore Drill Seal

Status: implemented as a pure, non-destructive backup drill seal.

The Phase 28 seal exercises the privacy and portability promises that are most expensive to discover late:

- encrypted export drill;
- wrong-password drill;
- tamper-detection drill;
- metadata-private annex drill;
- restore dry-run drill;
- retention cleanup drill;
- human-readable recovery report.

Promise audit:

- The encrypted export drill creates an authenticated encrypted `.studyplan` package and decrypts it back to the same manifest.
- The wrong-password drill expects decryption to fail before a restore preview can be trusted.
- The tamper-detection drill flips ciphertext and requires authenticated decryption failure.
- The metadata-private annex drill requires private annex IDs and ZIP paths that do not expose raw database IDs.
- The restore dry-run drill asserts `databaseRowsChanged == 0`.
- The retention cleanup drill keeps automatic deletion disabled and tells users exported files are managed outside StudyFlow.
- The recovery report gives human-readable restore, failure, and no-write guidance.

This is still a source-level drill. Device storage picker behavior, real user-selected files, and Android document providers remain target-device checks.


## r2 correction

The restore dry-run drill is no longer a self-declared `StudyPlanRestoreDryRun` object. The seal now calls an injected `dryRunImport` path and blocks release readiness unless that path reports `databaseRowsChanged == 0`. The verifier rejects generated Python cache files, literal NUL bytes in Kotlin source, and production source that hand-manufactures a zero-write dry-run result.
