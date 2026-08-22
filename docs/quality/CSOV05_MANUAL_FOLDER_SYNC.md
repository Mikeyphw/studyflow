# CSOV05 Manual Folder Sync Acceptance

StudyFlow now has a manual sync lane for bring-your-own folder providers.

Acceptance checks:
- A SAF folder picker configures the local-folder provider through explicit user action.
- Automatic sync remains off after folder configuration.
- Manual upload and remote-check timestamps are tracked locally.
- Preview restore is present but blocked until a remote checkpoint is known.
- No StudyFlow account is required for the manual folder sync lane.
