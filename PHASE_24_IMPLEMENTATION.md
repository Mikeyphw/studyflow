# Phase 24: Optional Cloud-Sync Foundation

Delivered:
- provider-neutral local sync state and change-summary models;
- repository/domain boundary for preparing a local sync checkpoint without network access;
- DataStore-backed local sync preferences with explicit provider, identity, and conflict-policy gates;
- Settings entry and Sync Foundation screen that expose the local-only status truthfully;
- local change-token inspection over the Room Study Plan graph without schema migration;
- tests and verifier coverage that prevent accidental cloud-provider, account, token, or network claims.

This phase intentionally does not select a real cloud provider, perform network I/O, create accounts, or merge
remote data. It prepares the local contract that a later provider integration must satisfy.
