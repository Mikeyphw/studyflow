# CSOV13 sync runbook

CSOV13 adds a source-level runbook planner for manual and background sync execution.

Acceptance criteria:

- Manual upload builds an encrypted bundle before provider upload.
- Manual remote check lists checkpoints and records a ledger event.
- Restore preview downloads and verifies a bundle, then performs a dry-run restore only.
- Conflicts require review before any restore can proceed.
- Background runs require a matching enabled background mode.
- Disabled sync or unconfigured providers block all remote execution.
