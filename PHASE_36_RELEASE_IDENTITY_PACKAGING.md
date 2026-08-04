# Phase 36: Release Identity and Packaging

Phase 36 turns the source tree into a release-candidate shape without pretending the target host has
already built or signed the app.

## Delivered

- App identity moved to `versionCode = 37` and `versionName = "1.0.0-rc1"`.
- `scripts/phase_version.py` accepts release-candidate names so historical phase validators remain useful.
- Release packaging instructions live in `docs/release/RELEASE_PACKAGING.md`.
- Release notes for `1.0.0-rc1` live in `docs/release/RELEASE_NOTES_1.0.0_RC1.md`.
- `app/proguard-rules.pro` records the release-candidate minification decision.
- Source validators reject phase-style app version names for the release-candidate phase.

## Boundaries

- No keystore, password, alias, service account, or store credential is committed.
- Release shrinking remains disabled until a separate R8 validation pass exists.
- Source validation does not claim that an APK/AAB has been produced on the target host.
