# REL05/REL06 release notes and rollback package

This overlay adds the final operator-facing release copy and rollback planning layer after REL03/REL04 artifact identity and rollout evidence.

## REL05 release notes exporter

Marker: `REL05_RELEASE_NOTES_EXPORTER`.

The exporter reads `CHANGELOG.md` and writes:

- `release-notes.json`
- `release-notes.md`
- `release-notes-play-store-draft.txt`

It separates public notes from internal validation notes, redacts credential-looking values, and keeps `releaseReady` false. Release readiness is still owned by target execution, artifact identity, install smoke and rollout gates.

## REL06 rollback package manifest

Marker: `REL06_ROLLBACK_PACKAGE_MANIFEST`.

The rollback builder consumes REL01 through REL05 evidence and writes:

- `release-rollback-manifest.json`
- `rollback-package-manifest.md`
- `rollback-command-template.sh`
- `studyflow-rollback-package-bundle.zip`

It emits a reversible operator plan only. It does not install, downgrade, upload, publish, delete, or assert `rollbackReady=true`.

## Validation

`verifyRel05Rel06ReleaseNotesRollback` depends on `verifyRel03Rel04ArtifactInstallRollout` and then runs `scripts/verify_rel05_06_release_notes_rollback.py`.
