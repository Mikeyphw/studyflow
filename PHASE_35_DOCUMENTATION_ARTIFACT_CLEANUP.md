# Phase 35: Documentation and Artifact Cleanup

Phase 35 cleans the repository story after the product-facing truthfulness and offline-first phases.
It does not claim release packaging. Instead it makes the current state readable and keeps artifact
payloads free from generated validation state.

## Delivered

- Rewrote README as the current offline-first product overview through Phase 35.
- Rewrote VALIDATION_REPORT with source-contract status and target-machine gates.
- Normalized CHANGELOG to one top-level heading and one current Phase 34-35 entry.
- Kept historical phase docs in place because old validators still use them as source contracts.
- Added artifact hygiene checks for Devtool state, build outputs, caches, NUL bytes, and reserved
  inventory targets.

## Acceptance

Docs must describe the app as offline-first, not release-packaged. Overlays must not ship stale
Devtool state or generated build/cache output. Historical phase documents may remain as source
contracts, but README and validation notes must be current.


## r3 reserved-state boundary

Devtool reserves `.devtool/` runtime metadata and rejects overlay inventory entries that target it, including delete entries. Phase 35 r3 therefore keeps overlay payloads and inventory free of generated Devtool state, documents the reserved boundary, and leaves cleanup of an existing local `.devtool/` workspace to an explicit command outside the overlay.

Suggested local cleanup after a successful apply, when old validation state is known to be stale:

```bash
rm -rf "$REPO/.devtool/reports" \
       "$REPO/.devtool/state/android-validation" \
       "$REPO/.devtool/test-history.json"
```

This command is intentionally not part of the overlay because `.devtool/` belongs to Devtool, not to the patch inventory.
