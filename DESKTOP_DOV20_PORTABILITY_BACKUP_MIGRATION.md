# Desktop DOV20 — portability, encrypted backup and migration

DOV20 closes Desktop data portability without importing Android storage APIs.

- Shared `.studyplan` structure export/import uses `core:portability` and the canonical structure table inventory.
- Full Desktop workspace backup streams the complete workspace into an AES-GCM encrypted archive via `StudyPlanEncryptedPackageCodec`.
- Restore authenticates and extracts into staging, validates the workspace, creates a safety copy, and rolls back on replacement failure.
- Supported legacy `workspace-v1.properties` schemas are rewritten to the current schema explicitly.
- Restored workspaces require application restart so no stale in-memory repository state continues after replacement.
