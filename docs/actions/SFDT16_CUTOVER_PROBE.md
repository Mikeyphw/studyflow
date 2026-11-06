# SFDT16 — StudyFlow × Devtool staged CI cutover: source-alignment probe

**Status:** Safe to land as non-authoritative source-only tooling. **Not** approval to replace StudyFlow CI.

## Purpose

PR #5 merged as StudyFlow `main` SHA `3e4947fb303e87e992833a3adf8f052fee408fe2`. The source-only ownership graph, SFDT13 JVM shadow, SFDT14 independent RM21 and SFDT15 Android metadata no-op are established. They do **not** prove full native parity for phone, TV, desktop, Web, quality and deep checks, or push/PR semantics.

This milestone adds a read-only, five-lane GitHub Actions comparator. It gathers real GitHub run identities, conclusions, job inventories and artifact metadata. It **refuses** any different StudyFlow SHA, missing lane, duplicate run, incomplete/failed job, unverified run attempt, shadow job skip, or required missing/expired artifact.

It is deliberately *not* a byte-level artifact comparator: a successful SFDT16 report always records `cutover_authorized: false`, `artifact_byte_parity: NOT_VERIFIED`, and `provider_task_parity: NOT_VERIFIED`. Digest fields are metadata only unless ZIP contents have been independently retrieved and inspected.

## Operational sequence

1. Merge this source-only probe after its PR source tests pass; no authoritative CI is changed.
2. Run legacy StudyFlow CI, source shadow, expanded native shadow, native fast, and native deep at the **same** exact `main` SHA. Preserve each exact run ID and attempt. Never substitute old SFDT07 or SFDT08 runs from different commits.
3. Run the `StudyFlow SFDT16 source-bound cutover probe` workflow manually on `main` with `expected_sha` and all five exact run IDs. This workflow only reads GitHub Actions run/job/artifact metadata; it never executes Gradle or changes releases.
4. Independently compare actual native and legacy task lists, APK SHA-256/classifications, JUnit and lint outputs, Web/desktop tests, quality/parity evidence, source/build toolchain, failed-run propagation, and push/PR/required-check behavior. The SFDT16 probe alone **cannot** close those gates.
5. Only when those separate lanes are demonstrably equivalent may later cutover PRs replace selected jobs. Preserve a full-run fallback until affected-selection accuracy and cancellation/permissions are proven.

## Security and invariants

- Termux executes Python source tests only; Android and desktop Gradle remain GitHub-hosted.
- Legacy StudyFlow PR/push CI stays authoritative. No workflow cancellation policy, branch protection, required check, environment permission, credential, or signing-key changes.
- Candidate, source, signing, release, and physical-device acceptance stay separate and explicitly nonpromoted.
- Release, signing, and device flags are always false for this report.
- A status `SAME_SHA_SHADOW_INVENTORY_PASS` means **inventory consistency, not coverage parity or cutover readiness**.
