# StudyFlow OPT02 P02 — Cross-workflow producer coordination

## Evidence from P01

PR #16 at `d9c9a2f28eaa4aad51266195596a8f5bb387a235` succeeded in both Targeted CI and SFDT22 physical build-only GitHub Actions. However, the Targeted Android job executed its `Compile Android debug and AndroidTest APKs` step as well as its reuse probe, so P01 did **not** eliminate the duplicate compilation for that run. This is consistent with the independent producer still being in progress.

## P02 scope and safety

The existing Targeted CI consumer now waits up to 300 seconds (configurable 0–360) for the already-scheduled physical APK producer on the **same** PR branch and **same** 40-character commit. It checks registration and progress in bounded 20-second increments and prints each poll. A missing, failed, cancelled or superseded producer causes a **MISS** and the unchanged hosted Gradle build. Bad API responses, wrong repository identity, malformed manifest, wrong run attempt, unexpected files, mismatched hashes or signer certificates remain fatal errors. No automatic reattempts of a completed failed build.

The implementation never creates a new workflow run, rewrites an APK, installs on Android or weakens the test suite. The SFDT22 physical builder, its manifests and the independently passing physical ARM64 acceptance remain unchanged. SFDT21's PR-merge SHA differs from the PR head SHA; no cross-SHA reuse is attempted there. Release qualification remains explicitly false.

## Expected GitHub result

In the Targeted Android job, `OPT02_SHARED_APKS_INFO PRODUCER_PENDING` may appear while the producer runs. A verified success produces `OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED`; the Gradle compile step must then be **skipped**. If no eligible producer arrives within 300 seconds, `OPT02_SHARED_APKS_MISS` is printed and the original Gradle compile step must **succeed**. A corrupt candidate fails the entire job and required gate.

Source-only synthetic tests validate timeout, late success, completed failure, wrong-repo refusal, superseded source, signer and digest tampering, and full artifact output invariants. Actual CI cache hit depends on GitHub workflow timing. No physical retest is necessary for a CI-only helper change; retain the prior physical evidence with its original source SHA, which is not transferable to the new commit.

## Termux

Run the consolidated source-pinned ZIP with `--self-test`, `--inspect`, `--dry-run`, then `--watch` to stream the exact GitHub PR checks. Never compile Gradle locally or force-push/auto-merge. Inspect the Targeted Android step outcomes to confirm whether reuse was a hit.
