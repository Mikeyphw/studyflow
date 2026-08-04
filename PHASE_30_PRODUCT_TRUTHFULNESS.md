# Phase 30 Product Truthfulness

Phase 30 removes release-checklist scaffolding from normal user settings and makes local-only capabilities honest.

## Delivered

- Normal `SettingsScreen` now shows user settings only: language, appearance, backup, reminders, privacy, local backup checkpoint, and Debug Workbench when diagnostics are available.
- App shell readiness, UX polish, release-candidate polish, and release seal cards are no longer rendered in normal Settings.
- System language and theme defaults are treated as valid user choices by `AppShellReadinessAuditor`, so a fresh user is not shown as half-ready for choosing system defaults.
- Sync copy now describes a local backup checkpoint. It no longer promises or hints at cloud provider, account, upload, or server merge behavior.
- Stale user-facing roadmap copy was removed from Settings and Dashboard resources in English and Brazilian Portuguese.
- `ReleaseReadinessSeal` now represents Phase 1 through Phase 31 plus Debug Workbench D1 through D8 instead of freezing at 25 items.
- `ReleaseValidationSealFactory` advances to Phase 31 source-contract evidence while keeping target-machine gates blocked until real build/device evidence exists.

## Non-goals

- No real network sync provider was added.
- No release signing or minification policy was changed.
- Debug Workbench remains available only when the app host marks diagnostics available.

## Verification

Run:

```bash
python3 scripts/verify_phase_30.py
```

## r2 cleanup

- Removed retired Settings resource IDs instead of only hiding the old release/readiness cards.
- Added verifier coverage that rejects resurrected app-shell readiness, UX polish, release-candidate, final-seal, foundation-contract, accessibility-baseline, and raw sync-token labels from Settings resources.
- Added changelog duplication protection so the Phase 30-31 entry appears once.
