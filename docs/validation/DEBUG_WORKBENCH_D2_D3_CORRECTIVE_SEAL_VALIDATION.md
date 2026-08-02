# Debug Workbench D2/D3 Corrective Seal Validation

## Local independent gates

- Exact D3 source-state hashes before overlay construction.
- All Phase 0 through Phase 29 verifiers.
- D1 r2, corrected D2, D3, and corrective-seal verifiers.
- Strict standalone Kotlin compilation with warnings treated as errors.
- 32 cumulative pure diagnostics tests.
- 13 app-level corrective behavior tests.
- Compose-shaped compilation of the changed workbench UI files.
- Android/AndroidX-shaped compilation of the registry and action boundaries.
- English and Brazilian Portuguese resource-key parity and XML parsing.
- Formatting policy: final newline, no tabs, no trailing whitespace, and Kotlin lines no longer than 120 characters.
- Clean-apply and byte-for-byte result-tree comparison.
- Inventory/payload equality, duplicate/path traversal/NUL/cache/build-output scans, and source/output hash verification.

## Required Devtool gates

Run the target-specific full validation chain after applying the overlay. D4 must not begin until compile, resources, tests, lint, package, diagnostics, and finalization complete successfully.
