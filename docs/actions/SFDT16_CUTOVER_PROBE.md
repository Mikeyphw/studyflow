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

## SFDT16-R2 source-identity and artifact-metadata correction

The R2 probe now **requires** a separately reviewed `expected_identities_json` workflow-dispatch input, in addition to the five run IDs. Do not derive the expected identity from the same selected run *inside* the verifier; doing so would allow an unrelated successful workflow to impersonate a lane. Build the JSON after inspecting the canonical workflow definitions and the proposed runs in GitHub. Each of the five keys (`legacy`, `source_shadow`, `expanded_shadow`, `native_fast`, `native_deep`) has exactly three required fields: positive integer `workflow_id`, source-relative `workflow_path` (`.github/workflows/…yml`), and positive integer `attempt`. Example *shape only* (IDs below are fictitious and MUST NOT be dispatched as real expectations):

```json
{
  "legacy": {"workflow_id": 101, "workflow_path": ".github/workflows/studyflow-ci.yml", "attempt": 1},
  "source_shadow": {"workflow_id": 102, "workflow_path": ".github/workflows/source-shadow.yml", "attempt": 1},
  "expanded_shadow": {"workflow_id": 103, "workflow_path": ".github/workflows/expanded-shadow.yml", "attempt": 1},
  "native_fast": {"workflow_id": 104, "workflow_path": ".github/workflows/native.yml", "attempt": 1},
  "native_deep": {"workflow_id": 104, "workflow_path": ".github/workflows/native.yml", "attempt": 1}
}
```

Check each mapping in the GitHub Actions **run JSON** (`workflow_id`, `path`, `run_attempt`) and **workflow definition** (`.github/workflows/…`). SFDT08 fast/deep must share the same workflow definition but have distinct run IDs and their full distinct job matrices. This inventory validates each run's workflow ID, exact repository-relative workflow path (with the main branch and SHA checked separately), repository, main branch, event type, expected attempt and job counts. It requests the **jobs endpoint scoped to the explicit attempt**, not the unqualified latest-jobs endpoint. A wrong attempt, workflow identity, source SHA, incomplete lane, skipped required job or malformed artifact metadata refuses.

Artifact IDs are unique across lanes and all enumerated artifact metadata must have a nonempty name, positive size, nonexpired status, valid `sha256:<64 lowercase hex>` digest, and matching producer run if supplied. **Important:** GitHub's run-level artifacts endpoint may include uploads from other attempts. R2 deliberately marks `artifact_attempt_provenance=NOT_VERIFIED` and `artifact_byte_parity=NOT_VERIFIED`; it does *not* claim that ZIP bytes or attempt-specific producer identity have been verified. Those are SFDT17 tasks. The manifest is an external human-reviewed assertion, not an authenticated independent source of truth.

For main-only manual dispatch, supply `expected_sha`, `expected_identities_json` (compact JSON without newlines), and all five actual run IDs. If a run lacks required artifacts or has failed/incomplete jobs, the expected result is refusal; do not swap in an older SHA to make it green. Local source-only check: `python3 -B tests/test_sfdt16_cutover_probe.py`.

**Cutover policy is unchanged:** authoritative CI stays enabled. No Gradle, signing, release, or hardware qualification is performed by SFDT16. A `SAME_SHA_SHADOW_INVENTORY_PASS` report cannot authorize CI replacement.

## SFDT18 live GitHub API path correction (2026-11-06)

Actual GitHub REST `GET /repos/{owner}/{repo}/actions/runs/{id}` returns `path`
as `.github/workflows/studyflow-ci.yml` (and equivalent relative paths for
SFDT06, SFDT07, SFDT08). It does **not** return a
`owner/repo/.github/workflows/...@refs/heads/main` suffix. SFDT16 now compares
the *exact relative path* with the reviewed manifest. Repository, event,
`head_branch == main`, `head_sha`, workflow ID and exact attempt remain
independent strict requirements. A wrong branch is rejected based on
`head_branch`, never inferred from a nonexistent suffix.
