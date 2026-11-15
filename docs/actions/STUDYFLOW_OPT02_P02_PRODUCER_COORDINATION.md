# StudyFlow OPT02 P02 — Cross-workflow producer coordination

## Evidence from P01

PR #16 at `d9c9a2f28eaa4aad51266195596a8f5bb387a235` succeeded in both Targeted CI and SFDT22 physical build-only GitHub Actions. However, the Targeted Android job executed its `Compile Android debug and AndroidTest APKs` step as well as its reuse probe, so P01 did **not** eliminate the duplicate compilation for that run. This is consistent with the independent producer still being in progress.

## P02 scope and safety

The existing Targeted CI consumer now waits up to 720 seconds (configurable 0–360) for the already-scheduled physical APK producer on the **same** PR branch and **same** 40-character commit. It checks registration and progress in bounded 20-second increments and prints each poll. A missing, failed, cancelled or superseded producer causes a **MISS** and the unchanged hosted Gradle build. Bad API responses, wrong repository identity, malformed manifest, wrong run attempt, unexpected files, mismatched hashes or signer certificates remain fatal errors. No automatic reattempts of a completed failed build.

The implementation never creates a new workflow run, rewrites an APK, installs on Android or weakens the test suite. The SFDT22 physical builder, its manifests and the independently passing physical ARM64 acceptance remain unchanged. SFDT21's PR-merge SHA differs from the PR head SHA; no cross-SHA reuse is attempted there. Release qualification remains explicitly false.

## Expected GitHub result

In the Targeted Android job, `OPT02_SHARED_APKS_INFO PRODUCER_PENDING` may appear while the producer runs. A verified success produces `OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED`; the Gradle compile step must then be **skipped**. If no eligible producer arrives within 720 seconds, `OPT02_SHARED_APKS_MISS` is printed and the original Gradle compile step must **succeed**. A corrupt candidate fails the entire job and required gate.

Source-only synthetic tests validate timeout, late success, completed failure, wrong-repo refusal, superseded source, signer and digest tampering, and full artifact output invariants. Actual CI cache hit depends on GitHub workflow timing. No physical retest is necessary for a CI-only helper change; retain the prior physical evidence with its original source SHA, which is not transferable to the new commit.

## Termux

Run the consolidated source-pinned ZIP with `--self-test`, `--inspect`, `--dry-run`, then `--watch` to stream the exact GitHub PR checks. Never compile Gradle locally or force-push/auto-merge. Inspect the Targeted Android step outcomes to confirm whether reuse was a hit.

## HF01 — P02 timeout follow-up (15 November 2026)

On SHA `76b4cc7ac221aae10cc6a4f4b7318a1f4fe41a72`, the Targeted CI
consumer run `43739590217` polled at 17:11:19 UTC and timed out at 17:16:20,
while the canonical producer run `43739585864` completed successfully at
17:17:52. Consequently the consumer ran its own Gradle build and **OPT02 had
not yet demonstrated a shared-APK HIT**, despite all required checks passing.

HF01 increases the bounded consumer wait from 300 to **720 seconds** (maximum
accepted 900 seconds), without changing provenance or signing verification.
A producer that never appears receives at most 60 seconds of discovery wait.
A completed failure/cancellation or superseded SHA returns to the existing
GitHub Gradle fallback early. An active exact-source producer is awaited up
to 720 seconds; timeout still triggers the safe Gradle fallback. A present
corrupt artifact is always fatal rather than an alternate compilation path.

Acceptance for this optimization must inspect a **new** Targeted Android job
log and observe `OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED`
with the `Compile Android debug and AndroidTest APKs` step **skipped**. All
GitHub required checks must pass. A successful workflow alone is insufficient.
If needed, run the Targeted CI workflow_dispatch with its reviewed exact SHA
after the corresponding physical APK producer has finished; this tests cache
reuse deterministically without any local Gradle or Android device changes.
Do not infer that a prior physical PASS qualifies any new source SHA.

Next: OPT02 P03, quantify reuse rate, elapsed minutes and duplicate compilation
using GitHub run logs. Then OPT03 cross-lane caching and workflow optimization.

## HF02 — Diagnosable artifact transfer and bounded retry (15 November 2026)

On PR #16 SHA `708cbe29031a4499914b001cf4113a3a820fe9e4`, the
canonical physical APK producer run **43745630803** succeeded at 18:23:33 UTC
and published the uniquely named artifact. The Targeted consumer run
**43745636320** had waited for the correct producer, but at 18:23:46 it ended
with `OPT02_SHARED_APKS_REFUSED COMMAND_FAILURE` (exit 2). The previous helper
suppressed subprocess stderr, so the old logs **cannot determine** whether the
failure was transient GitHub artifact availability, a transfer problem, missing
permissions, or a signer invocation. Do not claim the cause is established.

HF02 replaces this generic diagnostic with allowlisted command-stage and
error-category markers, never raw output or tokens. After a successful exact-
SHA producer and unique artifact metadata, the consumer retries **only** the
GitHub artifact transfer: at most six attempts, each in an isolated disposable
staging directory with partial files removed and bounded delays (5, 10, 15,
20, 20 seconds). 401/403 and other permanent access errors refuse immediately.
404 propagation, 5xx and recognized transport failures are retried. Unknown
nonzero transfer failures also get bounded retries, then fail with an explicit
category. A successful download still must pass unmodified artifact identity,
SHA-256, same signer and manifest provenance checks; **no invalid APK can
trigger a Gradle fallback or a HIT**.

The 720-second wait for the producer remains unchanged. If the producer is
absent, failed or superseded, the historical Gradle fallback remains valid.
This HF02 patch only affects CI helper/tests/handoff/source manifest, not the
physical APK producer, SFDT21, Android app/test source or release gates.

**Acceptance**: a new Targeted CI log must contain
`OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED`, the independent
Android compile step must be **skipped**, and the required result gate must
pass. If still failing, use the category in `OPT02_SHARED_APKS_REFUSED` or
`OPT02_SHARED_APKS_INFO DOWNLOAD_RETRY` to diagnose the exact stage. Do not
re-run physical device tests merely for this CI-only fix. All Gradle on GitHub.

Next: OPT02 P03 measurement, then OPT03 workflow optimization.
