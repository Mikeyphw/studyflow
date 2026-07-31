# Phase 4 Implementation

Phase 4 makes the complete Study Plan workspace portable without coupling exports to the physical
Room database file.

## Delivered

- Versioned `.studyplan` ZIP archive with manifest, typed relational payload, and SHA-256 checksums.
- Safe bounded archive reader with allowlisted entries and path traversal rejection.
- Full, private-history-stripped, structure-only, and reusable-template exports.
- Import preview before any mutation and exact table-inventory validation by export mode.
- Create-as-new, replace-existing, and compatible-merge import strategies.
- Deterministic root/child identifier and foreign-key remapping.
- Transactional Room graph import across all Phase 2 plan-owned tables.
- Automatic pre-import snapshots for replace and merge, with existing snapshot history preserved.
- Manual snapshot creation, restore, deletion, and app-private storage.
- Storage Access Framework UI for opening and creating packages.
- Plan-list, plan action, dashboard, and navigation integration.
- Explicit annex metadata sanitization and `includesAnnexPayloads=false` until Phase 9.
- Phase 3 warning seal: AutoMirrored icons, `mutableIntStateOf`, modifier ordering, and Portuguese
  plural `many` quantities.

## Package schema

```text
manifest.json
payload.json
checksums.json
```

The manifest uses schema version 1 and the stable format id
`com.mikeyphw.studyflow.studyplan`.

## Deferred by design

- Imported-copy and linked-document annex bytes remain Phase 9.
- Encrypted package exports remain Phase 21.
- Public/shared template distribution remains Phase 26.

## Phase 4 r3 BuildConfig generation seal

AGP 8 and newer disable `BuildConfig` generation by default. Because the application uses `BuildConfig.VERSION_NAME` in portable-package manifests, `:app` explicitly enables `buildFeatures.buildConfig`. The Phase 4 validator checks this relationship and verifies that the Kotlin package matches the Android namespace.

