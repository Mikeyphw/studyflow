# PC18 — Encrypted portable backup + full applicable functional parity seal

PC18 closes the final browser-applicable rows left by the post-PC11 promise audit. It does **not** claim that a browser is Android: platform-only distribution, Android Keystore/FileProvider behavior, native Termux arbitrary execution, and cloud/provider integrations remain explicit platform divergences. StudyFlow Local remains local-first, offline-capable, account-free and dark-only.

## Android-compatible encrypted portable envelope

The encrypted portable v2 export wraps the existing checksum-verified portable package without changing its inner schema. The envelope is compatible with the Android `StudyPlanEncryptedPackageCodec` contract:

- magic `STUDYFLOWENC`, envelope version 1;
- PBKDF2-HMAC-SHA256, 210,000 iterations, AES-256-GCM with a 128-bit tag;
- random 16-byte salt and 12-byte nonce for production exports;
- the complete serialized header is authenticated as AES-GCM AAD;
- passwords are never stored and must contain at least eight characters;
- wrong passwords or tampering fail authentication before a portable import preview can be created.

The regression suite includes the published deterministic Android vector for plaintext `checksum-verified-study-plan` with password `correct horse`, salt bytes 0–15 and nonce bytes 16–27. WebCrypto produces the exact same envelope bytes.

## Today temporal correctness

Today selection uses the current **local civil date**. Pending work scheduled for today wins. If none exists, only the earliest pending activity on a future local date may be presented as the fallback. Stale earlier-day missed work is not promoted as “next”, and unscheduled work is never mislabeled as future scheduled work.

## Exam-proximity scheduling

A **confirmed** exam date is now a first-class scheduler urgency signal: within 90, 30 and 7 days it increases cycle-candidate urgency. Expected dates do not trigger official exam-proximity prioritization. This preserves the same confirmed-versus-expected truth boundary already used by the cockpit and countdown.

## Mock Exam Studio

Generic Mock Studio selection now has explicit exact-board, source, required-tag, disputed-question and optional passing-score controls. No passing cutoff is inferred when none is configured. Question tags can enter through CSV/TSV/Markdown import and through manual question creation, so the tag filter is reachable from normal product workflows.

The dedicated Banco do Brasil reference mock reuses the same engine and preserves the reference contract: 70 objective questions, 300 minutes, eight reference weights totaling 100, derived item counts `[11, 4, 5, 3, 5, 10, 16, 16]`, exact Cesgranrio/Fundação Cesgranrio matching, local bank-availability gating, a separate essay reference minimum of 70/100, and **no invented objective cutoff, negative marking, official per-subject item count or incidence claim**.

## Parity statement

With PC12–PC18 applied, the browser edition closes the functional rows from the post-PC11 audit that have a meaningful browser equivalent. This is **full applicable functional parity**, not binary/platform parity. The remaining differences are deliberate platform boundaries rather than missing browser workflows.

## Validation

Focused PC18 validation checks the encrypted-envelope contract, TypeScript/runtime parity, PC18 regressions, affected temporal/mock/scheduler regressions, dark-only policy and committed release integrity. The Devtool artifact intentionally omits `validation.tasks` so it cannot expand into unrelated target-wide validation.
