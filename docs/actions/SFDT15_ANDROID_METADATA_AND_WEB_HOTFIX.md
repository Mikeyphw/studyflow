# SFDT15 — Retain RM21 JVM qualification, repair Web clock drift and Android metadata

This change is **not** release, signing, or device qualification. SFDT14 already
qualified `:core:model` as an independent RM21 JVM consumer on hosted GitHub
Actions run 42222198309. The RM23 Devtool SHA remains pinned unchanged.

## Web correction

`applyContentCleanupPreview` still rejects stale revisions and forged changed
summaries and **never applies anything**. It now reconstructs the canonical
snapshot at the preview's own recorded `generatedAt` instant, instead of an
unrelated wall clock. Its review-only denial remains mandatory. Source TS,
compiled runtime and distributed runtime are byte-identical where appropriate;
the offline TypeScript parity seal is updated for the exact new source bytes.

## Android verification metadata

The hosted JVM-only metadata collection did not resolve the Android AAPT2 POM
`com.android.tools.build:aapt2:9.3.1-15703166`.

The new read-only workflow executes the existing full Android phone + TV Gradle
build graph with `--write-verification-metadata sha256`, only on GitHub Actions.
A Python verifier compares every existing `(group, name, version, artifact) ->
SHA256` identity in the previously committed metadata with the hosted
candidate. Any changed/removed digest fails closed. The workflow also demands
that the precise missing AAPT2 POM acquired a SHA256. No `verify off`,
`continue-on-error`, synthetic SHA, release permissions or local Gradle tasks.

After the new workflow has **completed with success**, import its artifact with
the matching source SHA and checksums, using
`IMPORT_SFDT15_ANDROID_METADATA.zsh <run-id>`. This is a separate repository
commit so all existing JVM trust evidence remains attributable to its source.
The new commit reruns existing Android, Web and independent JVM workflows.

The source metadata importer validates the exact hosted run, source tree,
original metadata SHA, complete artifact checksum manifest, and additive
changes before committing and pushing the candidate. The hosted generator
never writes to GitHub itself.
