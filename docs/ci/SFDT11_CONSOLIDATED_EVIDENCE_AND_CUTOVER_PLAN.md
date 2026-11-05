# SFDT11 — Consolidated StudyFlow × Devtool hosted acceptance wave

**Delivery:** one *additive, source-pinned Devtool overlay* for `Mikeyphw/studyflow`.
**Baseline:** StudyFlow `main` `4a592aa413a8581eb9ba5276d904dceabb6b5a3e`, Devtool native runtime pin `0ebd22c17ac3058704012a1e03ee3265328b0659`.
**Decision at packaging:** candidate only. SFDT10 R1 and R2 were hosted-successful on earlier matching source `f3fc21ce...`, but an SFDT11 wave **must be rerun on the merged SFDT11 commit**. No source, release, signing, branch protection, or native provider cutover is authorized merely by applying/merging this ZIP.

## What the consolidated overlay adds

| Component | Scope | Guarantees / limitations |
| --- | --- | --- |
| `.github/workflows/studyflow-devtool-sfdt11-source-gate.yml` | Automatically checks PRs and pushes to `main` | Source-only, pins current authoritative CI, signing and Devtool-native workflow Git blobs; refuses scope/privilege drift; tests 35 local contracts. **Not a required check** until branch protection is intentionally reconfigured in a separate transaction. |
| `.github/workflows/studyflow-devtool-sfdt11-rollup.yml` | Manual, read-only, GitHub-hosted | Consumes *eight distinct same-SHA successful runs*, reads original GitHub artifacts and exact profile/typed receipts, invokes existing SFDT07/SFDT08 member comparators, binds SFDT10-R2 Android byte/member report. Does not run Gradle/npm or touch signing. |
| `scripts/sfdt11_wave.py` | User-triggered GH CLI coordinator | `start`, `status`, `compare`, `rollup`, `drive` commands, anchored to actual remote `main` SHA. Dispatches only six reviewed existing workflows plus SFDT10-R2 and the rollup, in the correct order; refuses incomplete results or a changing source. |
| `scripts/sfdt11_contract.py` | Policy and provider truth | Freezes 15 independently verified source Git blob IDs. Explicitly refuses claiming native Node/JVM Web/Desktop providers for SHA-pinned Python bridge jobs. Maintains existing unsigned Android Release JVM task semantics. |
| `scripts/sfdt11_rollup.py` | Eight-run verifier and report | Verifies run SHA, main branch, event, job identities, full matrix counts, archive metadata digests, four real source typed node receipts, five legacy deep task/Gradle receipts, SFDT07 expanded member bytes, SFDT08 fast/deep native JUnit/typed member bytes, and the R2 Android member report. **Transfer ZIP archive digest independently verified remains false.** |
| `scripts/test_sfdt11_*.py` | Standalone regressions | Source policy, positive/negative eight-run evidence, wrong event/branch/sha, missing/mismatched native receipts, deep-task tampering, fail-close API pagination, dangerous or duplicate dispatch. Runs without Gradle/network. |

No changes are made to `.devtool.toml`, `studyflow-ci.yml`, existing native workflows, signing, device installer, release workflow, existing required checks, build targets, Kotlin, Web runtime code, or any GitHub repository setting.

## Acceptance matrix — exact same commit, eight workflows

All runs listed below must have `head_branch=main`, source SHA identical to the SFDT11 merged main commit, terminal successful result and exact runner/attempt identity.

| Key | Existing workflow | Required evidence |
| --- | --- | --- |
| `legacy` | `studyflow-ci.yml` automatic push | Source, Web, Desktop, phone Android, TV Android five active jobs; four APK/diagnostics artifacts |
| `legacy_deep` | `studyflow-ci.yml` manual `lane=deep` | Source + five canonical deep-profile jobs; **real task/execution receipts**; no fabricated native-Gradle claim |
| `source` | `studyflow-devtool-shadow.yml` | Four real Devtool source EXO nodes, individual byte-bound typed receipts |
| `expanded` | `studyflow-devtool-native-expanded.yml` | Four lanes: phone debug, TV debug, Web pytest and quality-source; SFDT07 same-SHA member comparator |
| `fast` | `studyflow-devtool-native-coverage.yml` `scope=fast` | Three fast Devtool DAG lanes and independent JUnit comparison |
| `deep` | `studyflow-devtool-native-coverage.yml` `scope=deep` | Five deep Devtool DAG lanes and independent JUnit member comparison |
| `android` | `studyflow-devtool-sfdt10-debug-shadow.yml` | Native phone/TV debug APK+lint and Release JVM members |
| `compare` | `studyflow-devtool-sfdt10-r2-compare.yml` | Independent same-source legacy↔R1 Android APK, lint and JVM member parity report |

`compare` runs on the same merged `main` SHA. It refers to the `legacy` and `android` runs as explicit inputs. The final rollup cannot pass if a run is missing, skipped unexpectedly, has a duplicate identity, or was made from another commit. Other historical successful SFDT07/SFDT08/SFDT10 runs **cannot** be substituted when they were built from older commits.

## Apply and create PR (Termux/Zsh safe)

Download the consolidated ZIP into `/storage/emulated/0/Download`, then use an isolated subshell; do **not** source a script with `set -e` inside your interactive Zsh.

```zsh
(
  cd "$HOME/Code/studyflow" || exit 1
  git fetch origin main || exit 1
  test "$(git rev-parse origin/main)" = 4a592aa413a8581eb9ba5276d904dceabb6b5a3e || exit 1
  git switch -c sfdt11-r1-consolidated-evidence origin/main || exit 1
  devtool apply-overlay \
    /storage/emulated/0/Download/STUDYFLOW_SFDT11_R1_CONSOLIDATED_EVIDENCE.overlay.zip || exit 1
  git status --short
  git log -1 --oneline
)
```

```zsh
(
  cd "$HOME/Code/studyflow" || exit 1
  git push -u origin sfdt11-r1-consolidated-evidence &&
  gh pr create -R Mikeyphw/studyflow --base main \
    --head sfdt11-r1-consolidated-evidence --fill
)
```

Check that the legacy StudyFlow CI and the new SFDT11 source-only check pass, then merge this additive PR. **Do not dispatch the wave before merge**: new GH workflows may 404 until their YAML is on the default branch, and any run from the previous commit is invalid for the new eight-run report.

## One-command hosted wave after merge

This invokes a *synchronous coordinator on your Termux terminal*, not ChatGPT background work. It starts six hosted workflows (including costly full deep profiles), waits for their verified successful status, dispatches R2 comparison, then dispatches the SFDT11 eight-run rollup and waits for that completion. If `main` advances, a run fails, or the bounded wait expires, it fails closed. It does **not** run Gradle on the phone.

```zsh
(
  cd "$HOME/Code/studyflow" || exit 1
  python3 -B scripts/sfdt11_wave.py drive
)
```

The coordinator is resumable in stages; it never persists a daemon. If Termux suspends or you prefer separate commands, use:

```zsh
(
  cd "$HOME/Code/studyflow" || exit 1
  python3 -B scripts/sfdt11_wave.py start
  python3 -B scripts/sfdt11_wave.py status
)
```

When all seven prereq runs (`legacy` through `android`) are successful:

```zsh
python3 -B "$HOME/Code/studyflow/scripts/sfdt11_wave.py" compare
```

When the comparator completes successfully:

```zsh
python3 -B "$HOME/Code/studyflow/scripts/sfdt11_wave.py" rollup
```

The `status` command works any time. The coordinator intentionally refuses failed or missing runs instead of accepting a previous run by number or dispatching with an ambiguous source SHA.

## Result and remaining cutover blockers

A successful final report uses `EIGHT_RUN_BOUND_NATIVE_AND_LEGACY_EVIDENCE_VERIFIED_UNQUALIFIED`. It contains the eight exact run IDs, attempts, required GitHub artifact metadata digests, the five legacy deep profiles, four source nodes, four expanded lanes, three fast lanes, five deep native lanes, three Android R2 lanes, and explicit `NO_GO_CI_CUTOVER` decision.

**Still blocked, even if the rollup succeeds:**

- **First-class Devtool Node/npm provider** for the actual StudyFlow Web pipeline. Currently `sfdt08-web-full` is a SHA-pinned `python-shell` bridge; Devtool RM-11's reviewed root/workspace and script allowlist does not admit this pipeline as native Node DAG execution.
- **First-class desktop Gradle/JVM provider**. The existing `sfdt08-desktop-fast` task is still a SHA-pinned `python-shell` bridge, and the RM-12 JVM provider has a separate strict Gradle project/task contract. A provider extension requires its own Devtool source changes, tests and hosted verification; do not relabel the old bridge.
- A separate, fully qualified source-driven **push/PR required-check equivalence and failure-propagation** rehearsal. A new optional source-only check is not a branch protection transaction.
- Independent **GitHub artifact ZIP transfer digest** verification; current report preserves GitHub archive metadata digests and verifies extracted member bytes/typed checksums but explicitly reports transfer ZIP bytes not independently verified.
- **Signing environment configuration readback and policy** plus real **phone and Android TV device acceptance**. These must remain isolated from normal unsigned source CI.
- An operator-approved, reversible required-check/rollback cutover transaction, only after the preceding blockers have independent PASS evidence.

This is a consolidation/acceptance overlay, **not a claim that the final native provider migration is finished**. Existing `studyflow-ci.yml` remains authoritative throughout.
