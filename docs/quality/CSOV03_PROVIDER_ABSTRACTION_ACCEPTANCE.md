# CSOV03 Provider Abstraction Acceptance

CSOV03 introduces the provider API that later SAF-folder, WebDAV, Google Drive, Dropbox, and OneDrive implementations will use.

Acceptance criteria:

- `StudyFlowSyncProvider` exposes provider id, display name, account requirement, configuration state, checkpoint listing, upload, download, and delete operations.
- Provider upload/download traffic uses encrypted `.sfbackup` bytes plus manifest/checkpoint metadata, not raw database handles.
- `DisabledStudyFlowSyncProvider` represents the default no-provider state and reports unconfigured without requiring an account.
- Upload and download operations fail closed when the selected provider is not configured.
- `StudyFlowSyncProviderRegistry` resolves `null`/none to the disabled provider and rejects unknown ids.
