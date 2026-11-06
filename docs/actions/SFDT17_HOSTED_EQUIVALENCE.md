# SFDT17-R1 — StudyFlow × Devtool hosted byte-evidence qualification

**Status:** non-authoritative, hosted-read-only evidence. **No CI cutover is authorized.**

## What this actually delivers

The following canonical workflow paths are pinned in the SFDT17 code independently of dispatch input: `studyflow-ci.yml`, `studyflow-devtool-shadow.yml`, `studyflow-devtool-native-expanded.yml`, and `studyflow-devtool-native-coverage.yml` under `.github/workflows/`. An externally supplied valid-but-unrelated workflow identity **refuses** even if the jobs and artifact names look right.

The current `main` baseline for this overlay is StudyFlow `3deef0bc6697e3adf91a0a1dd69448ae140d3f40`, after SFDT16-R2 merged as PR #6. The examined Devtool `main` baseline is `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`; SFDT17 is an evidence consumer and **does not claim execution against that Devtool SHA** or modify Devtool sources.

SFDT16-R2 confirms **metadata** run identity/attempts, required job count and nonexpired artifact metadata. SFDT17 adds a hosted, read-only byte-level evidence step: the same five lanes and same exact StudyFlow source SHA are checked with the existing SFDT16 verifier, GitHub ZIPs are downloaded from GitHub Actions, their actual SHA-256 and size are reconciled to authenticated GitHub artifact API metadata, and each member is streamed and checked against its ZIP CRC. Paths, case collisions, dangerous UNIX link types, encryption, compression ratio, expansion and file counts are bounded. APK, JUnit, lint XML and JSON receipt inventories are reported with SHA-256 member hashes and test counts. No local Termux Gradle executes.

### Required five hosted lanes on the *same* StudyFlow `main` SHA

| Lane | Required artifact classes | Legacy/native status |
|---|---|---|
| `legacy` | phone/TV debug APK, phone/TV lint/JVM diagnostic ZIP | Authoritative remains active |
| `source_shadow` | SFDT06 native shadow receipt ZIP | Read-only source evidence |
| `expanded_shadow` | SFDT07 phone/TV debug, quality source, Web pytest ZIPs | Native debug comparison candidate |
| `native_fast` | SFDT08 phone Release JVM, Web, desktop ZIPs | Shadow |
| `native_deep` | SFDT08 Android, TV, desktop, quality, parity ZIPs | Shadow |

This is a **reviewed fixed artifact-name policy**, established from prior GitHub hosted uploads. Unexpected, missing, duplicated, expired or malformed artifacts are refused rather than being silently ignored. Uploaded artifacts belonging to another attempt may appear on the run-level API; this limitation is explicitly retained as `exact_artifact_attempt_provenance=NOT_VERIFIED` pending authenticated attempt-scoped receipt verification.

### Runbook

1. Merge the source-only SFDT17 PR after its tests pass. Do not disable the old CI.
2. On that exact merged `main` SHA, obtain real hosted runs for legacy, source shadow, expanded SFDT07 shadow, SFDT08 fast, and SFDT08 deep. If a historic workflow cannot dispatch, do not fabricate data, do not substitute another SHA, and do not declare GO; restoring the shadow lane becomes a separate source-pinned change.
   The existing shadow workflows are dispatchable using `expected_sha`. SFDT08 additionally requires `devtool_sha` and `scope=fast` or `scope=deep`; they must be separate runs. The current SFDT06/SFDT07 workflows still embed an older pinned Devtool runtime (`df2d306045619bc096c3bd18a68c3fb85023e4dd`), so **same-StudyFlow-SHA is not same-Devtool-SHA**; no provider parity can be asserted until this difference is explicitly requalified.
3. Independently inspect each workflow's numeric ID and `.github/workflows/...yml` path, its exact attempt, five distinct run IDs, and trigger. Prepare the compact `expected_identities_json` described by SFDT16-R2; it is *human reviewed input*, not a cryptographically authenticated identity anchor.
4. Dispatch `StudyFlow SFDT17 hosted archive and coverage evidence` on **main**, inputting `expected_sha`, `expected_identities_json`, and all five run IDs. This reads the GitHub API and artifacts without executing builds, changing branches, signing or publishing.
5. Retrieve the resulting JSON artifact. `FIVE_LANE_ARTIFACT_BYTES_VERIFIED_NOT_CUTOVER_APPROVED` certifies the source-bound ZIP byte and classification checks only, **not semantic parity**. Any missing/ambiguous evidence yields `REFUSED` and retains old CI.

### Outstanding gates before ANY authoritative CI cutover

- Same-source equivalent canonical commands/tasks and first-class Devtool provider ownership for Android/TV debug, desktop Gradle, Web/npm, deep/quality/parity, with real hosted worker receipts.
- Independent JUnit, Gradle and npm parity by suite, package/version/APK classification and lint **content**, not only binary hashes or matching numbers of tests. Device tests and release signing remain completely separate.
- Verify artifact **attempt-level producer** identity using actual signed or reliably source-bound receipt contents; metadata and naming alone are insufficient.
- Representative PR/push triggers, required check names, permissions, cancellation behavior, failure propagation, and branch protection with rollback tested.
- A separate maintainer-authorized cutover PR once all blockers are resolved. Never change protected signing environment, private keys, Gradle host policy, or release/device flags here.

The aggregate JUnit total counts **executions**, including intentional repetitions, not unique test cases. Quality/parity lacking JUnit are not falsely reported as tested. No ZIP archive is treated as an authenticated code-signing or device qualification artifact.

### Local no-Gradle validation

`python3 -B tests/test_sfdt17_hosted_equivalence.py`

The installer is source-pinned and refuses dirty work, unexpected remote refs, file collisions, or unseen changes. It stages only four new files. It does not invoke Gradle locally or run a live artifact download on the device.

## SFDT18 hardening (live API verified)

The SFDT16 workflow-run path checker now uses the exact repository-relative
`path` returned by GitHub REST, rather than a fictional `@refs/heads/main`
path. Source branch and commit remain separately pinned. SFDT17 additionally
requires the canonical numeric GitHub workflow IDs independently observed for
StudyFlow on 2026-11-06: legacy `402184638`, SFDT06 `406396373`,
SFDT07 `406444033`, and SFDT08 (both fast and deep) `406507103`.

The non-authoritative SFDT18 coordinator can dispatch all four shadow runs
with an already successful push-triggered legacy CI run at one new merge SHA;
it records the run IDs and, when those runs succeed, dispatches this same
SFDT17 verifier with the independently pinned identities and exact attempts.
No runtime/API credentials are ever stored in the coordinator's state file.
