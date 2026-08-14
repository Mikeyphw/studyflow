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
  --task :app:bundleRelease \
  --commit "Apply StudyFlow release identity and acceptance seal" \
  "$HOME/Downloads/studyflow_release_identity_acceptance_phase36_37_overlay.zip"
```

For the final release gate, build **both** the Release APK (`:app:assembleRelease`) and Release AAB
(`:app:bundleRelease`) on the target host. Configure signing outside the repository before generating
distribution candidates.

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

## OV-13 automated final gate

OV-13 does not infer release identity from Gradle configuration. Supply signing material only through
`STUDYFLOW_RELEASE_STORE_FILE`, `STUDYFLOW_RELEASE_STORE_PASSWORD`, `STUDYFLOW_RELEASE_KEY_ALIAS`,
and `STUDYFLOW_RELEASE_KEY_PASSWORD`. The final overlay and `scripts/validate_repo.sh` run
`verifyFinalReleaseAutomatedGate`, which fails closed unless all of the following succeed in one
validation session:

- host source/compile/unit/resource/lint validation;
- signed Release APK and signed Release AAB assembly;
- APK identity/version/non-debuggable/signing verification with `apkanalyzer` and `apksigner`;
- AAB signature-integrity verification with JDK `jarsigner -verify` (without CA-chain strictness);
- connected data/database migration and feature instrumentation suites;
- the connected Release app suite; and
- an exact-signed-APK full journey with real `REPLACE_EXISTING` and `CREATE_AS_NEW` restores, followed
  by a forced process restart and durable re-read in a second instrumentation invocation.

Generated evidence stays below `build/reports/studyflow/`. The automated gate is intentionally not
the final product verdict because TalkBack/theme/upgrade and other explicit device observations still
require human/device attestation. `verifyFinalReleaseVerdict` fails closed until those observations are
recorded with non-empty evidence text for every required check.

## Evidence-session binding

Use `scripts/run_final_release_acceptance.sh` for the authoritative automated run. It creates one
`STUDYFLOW_RELEASE_VALIDATION_SESSION` shared by host, exact APK/AAB identity, connected-suite, and
restore/restart journey evidence. Device acceptance derives that same session from generated evidence.
`verifyFinalReleaseVerdict` rejects mixed sessions, missing gates, unsigned/unverified artifacts,
incomplete or evidence-free device checks, and APK/AAB bytes that differ from their recorded hashes.
