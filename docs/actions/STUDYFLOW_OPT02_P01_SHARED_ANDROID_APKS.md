# StudyFlow OPT02 P01 — Source-pinned shared Android APKs

## Scope and expected outcomes

The already-successful **SFDT22 physical APK build-only** workflow is the canonical producer for its exact PR-branch commit. Its `app-debug.apk`, `app-debug-androidTest.apk` and `SFDT22_PHYSICAL_APKS.json` are immutable build artifacts, not physical or release qualification.

Targeted CI checks out the PR head SHA, so it can consume the identical source build if the canonical producer has already succeeded. This is an opportunistic performance improvement: **a missing producer compiles normally in GitHub Actions**. An invalid artifact fails instead of masquerading as a safe cache miss.

SFDT21 uses a GitHub PR merge SHA rather than the PR head SHA; its Android emulator and independent build are intentionally unchanged in P01. SFDT22 hosted emulator remains paused. OPT02 P02 can expand reuse only after exact-source and provenance conditions are met for those additional workflows.

## Trust rules

1. The consumer must be on the exact checked-out commit, run on Linux GitHub Actions in `Mikeyphw/studyflow` with `actions: read` and no write permissions.
2. Candidate build run must be a successful `push` of the expected SHA on `sfdt22-hotfix/hf30-hf04-pr-20261110`, in the same repository and canonical workflow.
3. The selected run ID and attempt must match the uniquely named, unexpired artifact.
4. Manifest must match source SHA, run ID and attempt, and must explicitly deny emulator, physical, release and publication qualification.
5. Both APK files must have exact SHA-256 hashes, valid independent `apksigner` certificate digests, and the same signer. No extra files or symlinks.
6. A miss leads to the historical hosted Gradle compilation. A corrupt manifest, invalid certificate or ambiguous artifact **refuses** rather than silently rebuilding.
7. Exact APK paths, debug-only scope, no release packaging, source contracts, and mandatory Targeted CI gate remain intact.

## Observability

Targeted Android job prints either `OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED` or `OPT02_SHARED_APKS_MISS ...`, followed by the unchanged compile route when missed. A refusal causes the job and required gate to fail. The test leaves user/device data untouched and performs no local Gradle compilation.

## Termux handoff

Apply the source-pinned OPT02 P01 launcher, monitor PR #16 checks and the new physical build-only artifact with `gh`. **No device installation or physical requalification is required for this CI-only optimization.** Preserve the existing successful physical `SFDT22_PHYSICAL_EVIDENCE.json` and its exact source SHA: physical PASS does not automatically extend to OPT02's new commit.
