# StudyFlow targeted CI — AuroraStore-style (nonqualifying)

**Status: introduced on HF27 staging only; no merge or release qualification.**

This additional workflow borrows the *principles* of `Mikeyphw/AuroraStore-Standalone`'s targeted CI, not its Android API level or Aurora-specific release gates. It uses the exact PR head or pushed SHA, the exact two-commit change range, deterministic path classification, relevant source/negative contracts, and selective hosted builds. It avoids local Gradle and never auto-merges.

## What runs

`studyflow-targeted.yml` triggers on PRs, pushes to `main` and `sfdt22-hotfix/**`, and manual exact-SHA dispatch. The always-required `scope` and `source` jobs run checks at the exact pinned head. The conditional lanes compile the Android debug **and instrumentation APKs** (API 36), TV debug, desktop classes/tests, and web test/lint/build. None execute on-device Android tests; all are **nonqualifying**.

`required` is a true final aggregate: selected failed, skipped or cancelled lanes fail the workflow. CI-sensitive or unknown code changes run the complete historical SFDT22 source-contract set, never silent skips. Known domain/data/database/content/session/UI files run the relevant source-contract groups. Docs-only changes run the immutable baseline policy/security checks. An absent mandated test is a failure, not a synthetic pass.

**Existing authoritative gates remain unchanged**: `studyflow-sfdt22-hosted-journeys.yml` still runs on PRs/main and requires its **source + emulator** jobs, all five actual UI tests, seven screenshots, a cold process restart, and evidence verified for exact SHA. `studyflow-sfdt26-hotfix-push.yml` still runs the independent three-journey nonqualifying staging diagnosis. `studyflow-ci.yml`, Devtool, SFDT21, and release controls remain unchanged.

There is intentionally **no Aurora-like full-gate cadence/router yet**: StudyFlow's active strict PR gate must not be demoted or replaced by a periodic gate without a separately reviewed acceptance policy and confirmed baseline. This is a fast signal in addition to the full gates, never an excuse to bypass them. No secrets, publishing, release keys, actions writes or branch mutation are used.

## HF27 incorporation

HF04's existing-plan `@Update` safeguard, personalized-settings transaction verification, strict Room regressions, and unchanged full five-journey qualification are included in the **same consolidated source-pinned overlay**. The targeted CI scripts and tests are source-pinned by SHA-256 in the full hosted workflow. The native Devtool kit will create one staging commit and watch its focused hosted run. The targeted CI run starts on the same pushed staging SHA; its result must also be checked before promotion.

## Review and qualification

A targeted green result is *not* proof that Android UI journeys work. For HF27, require a green focused hosted diagnostic and an independently green targeted CI on the same SHA, followed by separate full PR/Devtool qualification as designed. Never auto-promote or merge PR #15.
