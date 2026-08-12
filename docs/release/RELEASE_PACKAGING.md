# Release Packaging: StudyFlow 1.0.0-rc1

## Identity

- Application ID: `com.mikeyphw.studyflow`
- Version code: `37`
- Version name: `1.0.0-rc1`
- Scope: offline-first Android release candidate

## Build tasks

Use Devtool with explicit target selection for the source tree on the Android build host. A focused
release-candidate pass should include at least:

```bash
devtool --yes --copy -r "$HOME/Code/studyflow" --target studyflow_android apply overlay \
  --validate \
  --task :app:compileDebugKotlin \
  --task :app:compileReleaseKotlin \
  --task :app:lintDebug \
  --task :app:lintRelease \
  --task :app:assembleDebug \
  --task :app:assembleRelease \
  --commit "Apply StudyFlow release identity and acceptance seal" \
  "$HOME/Downloads/studyflow_release_identity_acceptance_phase36_37_overlay.zip"
```

For a signed distribution candidate, run the matching release assemble or bundle task on the target
host after configuring signing outside the repository.

## Signing boundary

Do not commit or include any of these in source or overlays:

- keystore files
- keystore passwords
- key aliases or passwords
- Play Console service-account credentials
- upload certificates
- generated signed artifacts

Use local Gradle properties, CI secrets, or Devtool host configuration for signing material.

## Minification decision

`release { isMinifyEnabled = false }` remains intentional for `1.0.0-rc1`. This keeps diagnostic
stack traces readable while the final acceptance pass is still being gathered. Re-enable R8 only with
a dedicated shrinking validation pass, explicit keep rules, and a fresh install/upgrade smoke.

## Package outputs

Do not commit APK, AAB, mapping, merged resources, lint reports, or generated build output. Store
those artifacts in the target host's release output directory and reference them from the completed
`FINAL_ACCEPTANCE_REPORT.md`.
