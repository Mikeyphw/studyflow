# Validation Report

Date: 2026-07-30
Artifact: StudyFlow Android Phase 2 r6 overlay

## Completed checks

| Check | Result |
|---|---|
| Phase 0 product-contract validator | Passed |
| Phase 1 foundation validator | Passed |
| Phase 2 domain/database validator | Passed, 22 domain roots |
| English and pt-BR string-key parity | Passed |
| Android locale configuration contract | Passed |
| Source formatting contract | Passed |
| Core model Kotlin compilation | Passed |
| Room/data compatibility syntax probe | Passed |
| AGP 9 plugin topology contract | Passed |
| JVM-to-Android dependency boundary | Passed |
| `:core:testing` runtime variant contract | Passed |
| Existing `.devtool.toml` preservation | Passed |
| Overlay inventory and source hashes | Passed |

## AGP and Kotlin topology

- `android.builtInKotlin=true`
- AGP new DSL remains enabled
- Android conventions do not apply `org.jetbrains.kotlin.android`
- KSP 2.3.10 is applied only through `studyflow.android.library.room`
- Compose and Serialization are supplied through the included convention build

This topology follows the AGP 9 built-in Kotlin migration path and avoids both failure modes seen in
earlier revisions: split Kotlin/KSP classloaders and the invalid external-Kotlin/new-DSL hybrid.

## Destination validation

Run the complete gate with:

```bash
devtool --copy --auto-hud --hud-mode desktop-window --yes \
  -r /path/to/studyflow \
  --target studyflow_android \
  apply-overlay /path/to/studyflow_android_phase2_domain_room_database_overlay_r6.zip \
  --validate
```
