# SFLP20 + SFLP21 — Clipboard/share/import bridge and conflict-review UX

SFLP20 adds a peer-neutral clipboard, share-sheet, and import-preview bridge. Android, Desktop,
Web, Web TV, and Android TV can receive bounded import offers through SFLP. Write-capable peers can
accept `clipboard_apply` and `share_import`; TV-class peers can preview only and reject library import
or workspace write authority before applying anything.

SFLP21 adds conflict-review UX primitives shared across platforms. Conflict reviews name the entity,
local/remote revisions, affected fields, summaries, and target peer. Resolutions are one-time,
monotonic, and become normal SFLP domain operations for keep-local, take-remote, merge, or skip.

## Guardrails

- `share.import_offer` is target-gated, capability-gated, TTL-bound, and duplicate-safe.
- `share.import_ack` reports accepted, duplicate, expired, or rejected.
- Android TV and Web TV reject `clipboard_apply`, `share_import`, `library.import`, and `*.write` routes.
- `conflict.review_open` is target-gated and requires `conflict.review` capability.
- `conflict.resolve` is one-time, monotonic, and requires merge text when the decision is `merge`.
- `conflict.resolution_ack` records resolved, duplicate, or rejected results.

## Validation

Run `verifySflp20Sflp21ShareImportConflict` on the final validation pass. Intermediate application is
intended to use `--no-validate`.
