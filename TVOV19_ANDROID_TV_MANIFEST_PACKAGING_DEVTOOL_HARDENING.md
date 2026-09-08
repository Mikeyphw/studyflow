# TVOV19 — Android TV manifest, packaging, and Devtool TV target hardening

TVOV19 hardens the separate `:app-tv` APK as an Android TV package rather than adding new study behavior.

Implemented package identity and launcher requirements:

- application/namespace identity remains `com.mikeyphw.studyflow.tv`;
- version moves to code `20`, name `0.1.0-tv20` for install/upgrade acceptance;
- Leanback remains required while touchscreen remains explicitly optional;
- the exported receiver shell is `MAIN` + `LEANBACK_LAUNCHER` only, with no phone `LAUNCHER` category;
- the TV application has a productivity category, fixed TV banner geometry, launcher icon, no cleartext framework traffic, and no cloud/device-transfer backup surface;
- Release signing reuses the phone app's `STUDYFLOW_RELEASE_*` environment contract when all credentials are present and rejects partially configured signing.

Devtool target isolation is now an executable contract. `studyflow_tv` may run only `verifyStudyFlowTv*`, `:app-tv:*`, and `:core:tvcontrol:*` validation work; repository-wide formatting, phone `:app`, phone feature modules, data/database modules, and unrelated targets are not part of the TV target.

Validation is split between `verifyStudyFlowTvManifestPackaging`, `verifyStudyFlowTvTargetIsolation`, and `verifyStudyFlowTvPackagedArtifacts` so source/package identity, target isolation, and built APK evidence remain independently visible.
