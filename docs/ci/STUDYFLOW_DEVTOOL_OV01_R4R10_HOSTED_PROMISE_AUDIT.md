# StudyFlow OV01-R4R10 — Source-bound promise audit

## Source and observed evidence

- Exact target commit: `4476538dbd937b0cec437b81b951a710f8a40fa3` (R4R9; main).
- Standard CI [41165343613](https://github.com/Mikeyphw/studyflow/actions/runs/41165343613): **5/5 PASS**, including phone Android Kotlin/JVM tests, `:app:lintDebug` and debug assembly.
- Deep CI [41165344473](https://github.com/Mikeyphw/studyflow/actions/runs/41165344473): **3/5 PASS**; android-contracts and desktop-contracts both fail at `verifySflp08Sflp09AndroidDesktopIntegration`.
- The deep verifier's first-30-errors display omitted 16 additional overlong lines: **46 total** in eight authoritative Kotlin candidate files. The verifier-owned inventory, *not* a guess from the Gradle stack trace, determined scope.

## Explicit promises and delivery audit

| Promise | Implementation/evidence | State |
|---|---|---|
| Fix every existing SFLP08/09 formatting violation | Exhaustive 120-column scan across all existing `formatting_candidates()` entries, including the 16 errors previously hidden by truncation | LOCAL PASS; hosted pending |
| Preserve SFLP peer protocol and queue behavior | 44 of the 46 old long lines were reflowed with exact lexical-token preservation; the remaining two split long Kotlin message literals into concatenations preserving full text. Pairing, authorization, state-store synchronization and retry controls unchanged | LOCAL SOURCE PASS; hosted Kotlin pending |
| Do not weaken deep contract | Full SFLP08/09 verifier still runs predecessor checks and its own inventory, and now reports **all** violating paths and count; no column limit reduction, skip or exclusion | LOCAL PASS |
| Move failures before Gradle | `scripts/sflp08_09_formatting_preflight.py` imports the same SFLP verifier's candidate inventory and runs in `source` job, before native Gradle jobs | LOCAL PASS; hosted pending |
| Fail closed on regression | R4R10 contract script injects overlong source and deletes owner fixture; both must be rejected | LOCAL PASS; hosted pending |
| Preserve native SFLP chain | SFLP08/09, 10/11, 12/13, 14/15, 16/17, 18/19, 20/21, 22/23, 24 all passed as standalone source verifiers | LOCAL PASS |
| Keep earlier hotfixes | R4R9 previous source regressions passed; transaction hook runs R4R9–R4R10 and SFLP24; earlier R4R6–R4R8 suites are verified independently, without local Gradle | LOCAL PASS; hosted pending |
| Track cumulative improvements | Ledger entries SF-DT-52..54 added to existing ledger | LOCAL PASS |
| Validate exact source and transactional application | ZIP per-path SHA-256 plus source SHA-256, tied to real GitHub blob hashes, disposable Devtool apply and deliberate mutated preimage rejection | Confirm actual transaction output separately |

## Qualification limits

This package does **not** run Gradle locally, change signing, request permissions beyond existing CI, bypass any phase gate, promise a release binary, or claim physical-device testing. The repository-wide historical Kotlin source inventory contains legacy lines exceeding 120 characters outside the SFLP08/09 owned candidate set. That global formatting audit is not a passing gate and is not changed by this focused deep-CI remediation. Hosted deep 5/5 and release/device acceptance must be evidenced separately.
