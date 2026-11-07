# SFDT18 — Live GitHub REST alignment + same-SHA hosted evidence coordinator

**State:** non-authoritative source-only patch and opt-in GitHub Actions dispatch coordinator. No change to legacy CI, required checks, signing, release, device testing, or branch protection. No Gradle runs in Termux.

## Verified failure and repair

On November 6, 2026, real GitHub REST workflow runs for StudyFlow returned a source-relative `path` (e.g. `.github/workflows/studyflow-ci.yml`). SFDT16-R2 incorrectly expected `Mikeyphw/studyflow/.github/workflows/...@refs/heads/main`, causing all real same-SHA checks to refuse. SFDT18 fixes this; `repository.full_name`, `workflow_id`, exact file path, `head_branch=main`, `head_sha`, event type, attempt, jobs and artifacts remain individually validated. Regression tests include the **real GitHub REST path shape** and refuse the fictional suffixed shape.

The workflow identities observed in actual successful GitHub runs are fixed as independent source policy: legacy `402184638`, SFDT06 `406396373`, SFDT07 `406444033`, SFDT08 fast/deep `406507103`. The verifier refuses any mismatched reviewed numeric ID even if a dispatch JSON is self-consistent. Rebaseline these only via a new source-reviewed PR if GitHub legitimately replaces a workflow.

## After the SFDT18 PR merges

Ensure the current main is available in the **existing** Termux checkout:

```zsh
function studyflow_sfdt18_sync() {
  cd "$HOME/Code/studyflow" || return
  git switch main || return
  git pull --ff-only origin main || return
}
studyflow_sfdt18_sync
```

Wait until the authoritative push-triggered `StudyFlow CI` on **the SFDT18 merge commit** completes successfully. Then run:

```zsh
function studyflow_sfdt18_dispatch() {
  cd "$HOME/Code/studyflow" || return
  python3 -B scripts/sfdt18_evidence_coordinator.py dispatch
}
studyflow_sfdt18_dispatch
```

This command requires only authenticated `gh` and Python. It checks the exact current `main` SHA, a **successful push** legacy CI run, the GitHub workflow catalog's independently reviewed IDs/paths/names/active states, and source SHA stability. It dispatches four distinct source-matched workflows: SFDT06, SFDT07, SFDT08-fast, and SFDT08-deep. It discovers each new run ID after its own dispatch, fails closed if attribution is ambiguous, and keeps a private local JSON state at `~/.local/state/studyflow/sfdt18/<sha>.json`. No secret/token is stored.

SFDT08 defaults to Devtool commit `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`, observed as Devtool `main` on November 6, 2026. It is deliberately pinned, *not* whatever the runtime's branch happens to contain. For a separately reviewed source commit, use `dispatch --devtool-sha <40-hex-sha>`. Existing SFDT06/SFDT07 workflows still use `df2d306045619bc096c3bd18a68c3fb85023e4dd`; therefore **this is not same-Devtool-runtime parity**.

When the four hosted shadow workflows have completed, inspect status:

```zsh
function studyflow_sfdt18_status() {
  cd "$HOME/Code/studyflow" || return
  python3 -B scripts/sfdt18_evidence_coordinator.py status
}
studyflow_sfdt18_status
```

Only when all five are successful, dispatch the SFDT17 byte verifier:

```zsh
function studyflow_sfdt18_finalize() {
  cd "$HOME/Code/studyflow" || return
  python3 -B scripts/sfdt18_evidence_coordinator.py finalize
}
studyflow_sfdt18_finalize
```

Finalization independently re-reads every run record; enforces real GitHub workflow IDs/paths, SHA/branch/event/repository/attempt and `completed/success`; builds SFDT16's compact five-lane identity JSON from pinned workflow policy; and dispatches the SFDT17 verifier. It never overrides its `REFUSED` outcome. On a GitHub failure/incomplete attempt, the script refuses rather than selecting a different SHA or declaring parity.

**Safe recovery:** If the dispatch is interrupted, its state is marked `DISPATCHING`, not `READY`. Inspect the Actions run URLs before any manual recovery or retry. Do not delete state and blindly rerun: that would duplicate costly Android jobs. If `finalize` is interrupted after submission, it is marked `EVIDENCE_DISPATCHING` and refuses re-dispatch until manually reconciled. This is intentional fail-closed behavior.

## Acceptance boundaries

A successful SFDT18 coordination run does *not* establish equivalent task graphs, Devtool host provenance, Gradle/Android APK equivalence, independent artifact attempt identity, GitHub required-check semantics, device tests, releases, or signing. Even a green SFDT17 byte verifier retains `cutover_authorized=false` and `legacy_ci_retired=false`. Reconcile those remaining blockers in a separate, source-pinned SFDT19 phase before any authoritative CI migration.

All SFDT18 tests run offline via Python stdlib:

```zsh
python3 -B tests/test_sfdt16_cutover_probe.py
python3 -B tests/test_sfdt17_hosted_equivalence.py
python3 -B tests/test_sfdt18_evidence_coordinator.py
```

## SFDT18-HF01 — guarded SFDT07 phone and same-SHA recovery

The original SFDT07 run `42270845902` at StudyFlow SHA
`7d4319533f631cad6f83810348d80fd28919c5d8` finished **cancelled**.
The `native-phone-debug` log reached `:app:lintDebug` at 18:36:46 UTC,
then emitted Devtool heartbeat text without substantive Gradle output for
~56 minutes until the 65-minute GitHub job timeout. Its 422-byte artifact
was a failure-only receipt, **not** an APK. The specific underlying Gradle
hang/daemon failure is not established.

HF01 adds a GitHub-hosted watchdog around the *existing native Devtool node*.
It does not implement a second Gradle executor or change its task list,
JDK/SDK/NDK, signing, releases, device or CI authority. The phone guard
records process/JVM thread diagnostics to an unqualified SFDT07 artifact,
then fails with code 124 on 10 minutes of non-heartbeat silence or 42 minutes
of total runtime. A failed guard is never a PASS and cannot be adopted.
`native-tv-debug`, `native-web-pytest`, and `native-quality-source` remain
unchanged. The hosted watchdog's presence does **not** prove the original
Gradle stall is fixed; it makes the failure bounded and diagnosable.

### Source SHA invariant — crucial

Merging HF01 creates a *new* StudyFlow `main` SHA. The four green runs from
`7d431953...` **cannot** be combined with a fresh HF01 SFDT07 run on the new
commit; SFDT16 and SFDT17 correctly reject such an attempt. After merging,
sync the existing StudyFlow checkout, ensure new `main` push CI succeeds,
then run `dispatch` to gather a new exact-SHA five-lane cohort. No local Gradle.

### Recover one cancelled/failed SFDT07 lane, without restarting the other four

Only for an existing `READY` state on **the exact current main SHA**, with
legacy, SFDT06, SFDT08 fast and SFDT08 deep already green and SFDT07 failed:

```zsh
cd ~/Code/studyflow
python3 -B scripts/sfdt18_evidence_coordinator.py recover
python3 -B scripts/sfdt18_evidence_coordinator.py status
```

`recover` checks GitHub's original failed run, four successful runs, canonical
workflow identity, exact source SHA and state; writes `RECOVERY_DISPATCHING`
*before* dispatch so an interrupted run cannot be duplicated; and records
`RECOVERY_PENDING` with a new candidate run ID without overwriting the failed
run. It does not silently re-run on a changed source SHA.

When the candidate completes successfully:

```zsh
python3 -B scripts/sfdt18_evidence_coordinator.py adopt
python3 -B scripts/sfdt18_evidence_coordinator.py finalize
```

`adopt` verifies the exact current SHA and the previous five runs, a completed
successful SFDT07 rerun, the exact four named matrix jobs and GitHub's
attempt-scoped successful job inventory, plus complete producer-bound artifact
metadata. Phone/TV debug artifact archives below 1 MiB are refused, including
the observed 422-byte failure receipt. The old run ID is preserved in recovery
history, all other run IDs remain unchanged, and only the SFDT07 ID is replaced.
Only then may SFDT17's byte verifier run. Actual artifact ZIP contents,
Devtool runtime consistency and semantic parity remain separately unverified.

A failed or interrupted candidate stays `RECOVERY_PENDING` /
`RECOVERY_DISPATCHING`, respectively. Do not delete state and replay blindly;
inspect the exact GitHub run before a source-pinned recovery revision.

Run the source-only checks on Termux:

```zsh
python3 -B tests/test_sfdt18_evidence_coordinator.py
python3 -B tests/test_sfdt18_phone_debug_guard.py
```
