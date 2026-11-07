# SFDT20 — Native CI post-merge qualification, provenance and rollback

## Scope

Source basis: `Mikeyphw/studyflow` `main` at merge commit
`89ea81891a50200b3d51c96b0f5b1dd5e9357631` (PR #12).
Immutable native Devtool runtime:
`bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`.

PR #12's final PR-head commit `be42f0d72aa42cfcd193a99df44fadee9a762a82`
passed native CI on run `42398848962` (source, phone, TV, Desktop, Web,
aggregate gate), SFDT11, legacy source CI, and independent Node acceptance.
Those are **pull-request results**, not proof that the merge-commit `main`
**push** and required-check branch protection are healthy. Never substitute
one for the other.

## Changes

- The native CI source job runs `scripts/test_sfdt20_provenance.py` before
  starting native jobs. Its negative tests cover forged receipts, JUnit
  failures, tampering, source/run mismatches, unsafe files and nonnative
  provider substitution.
- Each successful native lane now requires `sfdt20_provenance.py` to verify
  its exact source/runtime/run identity, typed node inventory, fresh JUnit
  bytes, APK or native language outputs and SHA-256 digests. The new receipt
  `RUNNER_TEMP/studyflow-devtool-primary/sfdt20-provenance.json` is included
  in the existing upload-artifact receipt bundle. The script itself is
  SHA-256 source-pinned in the job before execution.
- The aggregate `Devtool CI gate` already requires the source and entire
  native matrix. Adding mandatory provenance validation to the native job
  makes a missing or mismatched artifact fail that gate.
- `sfdt20_main_audit.py` audits the **current** `main` commit via `gh api`:
  it requires a push-triggered successful Devtool CI with six distinct green
  jobs, **plus** successful SFDT11 and legacy StudyFlow source-check push
  workflows at the same SHA, and inspects effective required checks from
  classic branch protection and rulesets. A 404/403 on protection inspection is **not** evidence that
  protections exist. This audit is read-only; it doesn't quietly change
  repository administration.

## What remains unchanged

Phone and TV use native `android-gradle`; Desktop uses native RM12 `jvm`
with `gradle --offline --no-daemon --console=plain :desktopApp:test`; Web
uses the first-class `node` provider. The SFDT19 HF06 prewarm resolves
actual external artifacts with **per-project tasks** for exactly five
approved JVM modules, including Kotlin 2.4.10 build-tools and serialization
compiler plugin JARs. SFDT20 adds a source test for these invariants but does
not rewrite or broaden the prewarm. This protects the **now-green** build.

No `.devtool.toml`, Gradle property, SFDT11 immutable policy, signing,
release, secret, Android emulator, device or publishing changes are made.
The original `ci-sfdt08-desktop-fast` and `ci-sfdt08-web-full` Python bridge
jobs and manual-dispatch `studyflow-ci.yml` remain available for rollback,
without running in the primary matrix.

## Acceptance / required status protection

After SFDT20's PR is green, merge it normally. Then on Termux (gh
already authenticated), in a clean checkout of the **newest** `main`:

```zsh
cd "$HOME/Code/studyflow"
git switch main
git pull --ff-only origin main
SHA="$(git rev-parse HEAD)"
python3 -B scripts/sfdt20_main_audit.py --sha "$SHA" \
  --out "$HOME/sfdt20-main-audit-${SHA:0:12}.json"
```

The read-only audit refuses to claim a pass unless a completed, successful
**push-triggered** `StudyFlow Devtool CI` at that exact SHA includes all six
required jobs and the SFDT11 and legacy StudyFlow source workflows are also
green at the same SHA. The audit independently checks whether branch protection
or rulesets require `Devtool CI gate`, `SFDT11 fail-closed source, CI and signing
boundaries`, and `Actions integration source contract`. It returns exit code
`3` if the CI is qualified but required-check enforcement cannot be confirmed. If protection
is unverified/missing, configure a branch rule or ruleset in GitHub settings
requiring all three status contexts (without overwriting other protections).
Do **not** blind-replace existing rules; inspect current contexts and reviewer
requirements before modifying them. Re-run the audit after settings changes.

An audit run immediately after a merge may refuse because the hosted push
workflow has not completed. It must not convert a pending run to PASS.

## Evidence and review

Expected per-lane artifacts are the existing:

- `studyflow-primary-<lane>-receipt-<run>-<attempt>`, now containing
  `studyflow-devtool-primary/sfdt20-provenance.json`.
- `studyflow-primary-<lane>-reports-<run>-<attempt>`, preserving JUnit
  and lint diagnostic bytes.
- `studyflow-primary-{phone,tv}-debug-<run>-<attempt>`, whose native APK bytes
  must still pass the separate `apksigner verify --print-certs` step.

The provenance JSON intentionally records only file bytes and source/job
identities; it does not claim the signature verification, release signing,
physical-device instrumentation or publishing were qualified.

## Rollback

Revert only the SFDT20 commit (new provenance step, source tests, scripts and
this document). The already-merged SFDT19 first-class native provider jobs
and their standalone SFDT08 Python rollback configurations remain intact.
Do not bypass failed provenance validation, forge test reports, disable
required checks, or fall back silently to Python bridges.
