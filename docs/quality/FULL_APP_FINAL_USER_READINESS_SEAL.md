# Full App Final-User Readiness Quality Gate

The final-user readiness gate is intentionally broader than the Debug Workbench validators. It covers app-wide release blockers, localized UI readiness, minSdk runtime compatibility, destructive-action safeguards, support-bundle integrity, and saved-state crash prevention.

The gate is source-contract based inside the overlay and must be followed by Devtool Android validation for AGP, Compose, lint, packaging, and device runtime proof.
