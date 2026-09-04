# CSOV05 Manual Folder Sync Acceptance

StudyFlow has a real, manual-only sync lane backed by a user-selected Android Storage Access Framework (SAF) folder.

Acceptance checks:
- A SAF tree picker configures the local-folder provider only after the selected folder can actually be enumerated.
- The persisted tree permission is used for real document I/O; checkpoint documents are created under the tree-root document URI, committed from a temporary document, and enumerated from the provider.
- `Write encrypted checkpoint` captures a consistent logical Room snapshot plus readable annex payloads and writes an encrypted `.sfbackup` through `StudyFlowSyncBundleCodec`.
- Upload metadata, checkpoint count, and the sync ledger are updated only after the encrypted document has been committed successfully.
- `Refresh folder checkpoints` enumerates the selected folder; it does not infer a remote count from a local upload timestamp.
- `Preview restore` opens the latest real `.sfbackup`, decrypts/authenticates it with a user-entered passphrase, and displays read-only checkpoint metadata without mutating local study data.
- `View sync history` is wired to the repository ledger rather than a no-op callback.
- The passphrase is never persisted by the settings UI and is cleared from the temporary `CharArray` after each operation.
- Automatic sync remains off for this provider. No StudyFlow account is required.

This phase intentionally provides restore **preview**, not restore application. Semantic archive-hardening and broader sync-ledger retention are separate follow-up scopes.
