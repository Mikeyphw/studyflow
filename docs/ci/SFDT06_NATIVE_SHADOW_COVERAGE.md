# SFDT-06 Phase A — Source-only native shadow and retained CI coverage

**Authority:** Current StudyFlow workflows are unchanged. This is an opt-in source-only, manually dispatched GitHub Actions experiment. It does not replace, skip, suppress, or grant release status to existing checks. Neither the overlay hook nor local validator executes Gradle.

## What this overlay does

- Adopts the *existing* four `studyflow_android` source-only target-local EXO jobs into Devtool's reviewed `python-shell` hosted provider, by attaching the exact Python script SHA-256, Python 3.12 and typed argument metadata. No new wrapper commands or local task behavior are introduced.
- Adds `.github/workflows/studyflow-devtool-shadow.yml`, **workflow_dispatch only**. It verifies the expected StudyFlow source SHA and pins a specific published Devtool runtime commit. The GitHub runner imports that exact tree via `PYTHONPATH` and provisions only version-pinned Python runtime dependencies in an external temporary venv (no checked-out Gradle execution). Its compiler is `devtool.actions_graph_export.compile_graph`, and its node executor is `devtool.actions_graph_export.run_node` (no second scheduler). The execution output includes typed Devtool node receipts, a Graph SHA-256 and a non-promoting attempt receipt.
- Writes a machine-readable source-only coverage inventory with the existing five deep-profile task lists and their digests. All legacy fast/deep lanes remain authoritative.
- Leaves signing, candidate packaging, GitHub workflow triggers, Gradle tasks, and source scripts unchanged. The only preflight-mutated tracked file is `.devtool.toml`.

## Coverage status (before real GitHub runs)

| Scope | Existing authoritative owner | Devtool shadow | Gate |
|---|---|---|---|
| Source checks | `studyflow-ci.yml#source` | `ci-source` four-node native EXO DAG | Hosted execution **pending** |
| Web | `studyflow-ci.yml#web` | Not adopted | Devtool RM-11 Node provider refuses StudyFlow's custom `npm` script grammar/package metadata |
| Desktop | `studyflow-ci.yml#desktop` and `desktop-contracts` | Not adopted | Requires native hosted Gradle/JVM equivalence |
| Phone | `studyflow-ci.yml#android[app]` and `android-contracts` | Not adopted | Requires existing release JVM tests and full Gradle task parity; no signing |
| TV | `studyflow-ci.yml#android[app-tv]` and `tv-contracts` | Not adopted | Requires full Gradle task and report parity |
| Quality | `quality-contracts` | Not adopted | Preserve full `build` task inventory |
| Parity | `parity-contracts` | Not adopted | Preserve `validation.phases.preflight` inventory |

**No cutover** is authorized on a source-only PASS. A real shadow run alone does not prove equivalence with the legacy source job; compare exact same-SHA run/attempt results before permitting a merge gate or migration. In particular, `test` evidence remains NOT_RUN without an independently parsed test report; no `release` gate can be promoted.

## Hosted execution commands (after applying and pushing StudyFlow to GitHub)

Run the full legacy source/fast/deep workflows using the existing manual dispatch; then run the additional shadow at the same StudyFlow commit. On Termux the shadow may only be *dispatched*, never run locally.

```zsh
cd "$HOME/Code/studyflow"
SHA="$(git rev-parse HEAD)"
gh workflow run studyflow-devtool-shadow.yml -R Mikeyphw/studyflow --ref main -f expected_sha="$SHA"
gh run list -R Mikeyphw/studyflow --workflow studyflow-devtool-shadow.yml --limit 5
```

If `main` has advanced, ensure the dispatched workflow source SHA matches the exact `--ref` branch head: the workflow intentionally rejects cross-source execution. The Devtool hosted runtime is the immutable published commit `df2d306045619bc096c3bd18a68c3fb85023e4dd`, independent of the **overlay's** source check (which is checksum-only, not Git-HEAD-pinned).

## Acceptance remaining

1. Native source DAG must execute on GitHub and upload all four exact-graph typed receipts; failures must retain FAILED/NOT_RUN evidence and fail the job.
2. Compare against the old `source` job for the exact same StudyFlow SHA, with independent check conclusions and any missing evidence explicitly recorded.
3. Extend supported provider/Gradle task parity, then add non-gating Web, desktop, phone, TV, quality and parity shadow lanes. StudyFlow's custom Web script adapter needs upstream Devtool work; do not downgrade Web coverage to satisfy a generic Node contract.
4. Real same-SHA full CI + five deep profiles is mandatory before retirement of any bridge or selective skip.
