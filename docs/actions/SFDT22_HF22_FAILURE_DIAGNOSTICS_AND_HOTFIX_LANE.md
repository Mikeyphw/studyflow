# StudyFlow SFDT22 HF22 — source-of-truth diagnostics + focused hotfix validation

## Scope and why HF16–HF21 were not enough

The hosted API36 emulator runner at `291b9805d069209b71924899071694791dcf75ba` compiled, passed its source contracts and completed 2 of 5 end-to-end UI journeys, but Quick Session emitted `session_start_plan_settings_missing`. The evidence proves an absent stored settings row *at the moment of lookup*, not when that row was lost or which plan was displayed. HF21's one-time seed/repair did not establish that boundary. This patch does not guess at a production-data mutation or silently supply defaults.

## New non-invasive read-only lifecycle checkpoints

The real `MainActivity` Compose instrumentation queries the production SQLite database in `OPEN_READONLY` mode at five exact boundaries. It does **not** introduce a test fixture, bypass UI navigation, modify DB rows or change session semantics.

1. `after_create`: stored settings after the real Create Plan wizard saved the new plan.
2. `after_subject`: settings after the real Subject save.
3. `after_topic`: settings after the real Topic save.
4. `session_route`: verify the Sessions screen visibly belongs to the exact plan created in step 1.
5. `before_session_start`: settings before the real Quick Session start action.

All checkpoints are recorded as allowlisted `point|state` ASCII in app-private storage; the host parses strict order and exports **only** enum names. Plan names, plan IDs, SQL rows, raw instrumentation logs, screenshot-on-error and stack traces are excluded. A failed checkpoint hard-fails the emulator and preserves its failure classification. `scripts/sfdt22_failure_triage.py` prints a bounded summary, first observed failure and last known good checkpoint; no arbitrary receipt keys are echoed. The existing `SFDT22_EVIDENCE.json` remains non-qualifying on failure.

If settings are present after creation but missing later, the first changed boundary isolates the loss window. If the session route points at the wrong plan, the fixed route check fails before Quick Session. If settings are missing immediately after creation, investigate the wizard/repository transaction rather than repairing a later session. A successful route check does not by itself prove database integrity; the settings read is an independent check.

## Opt-in focused GitHub Actions lane (not an acceptance gate)

HF04 leaves the legacy `.github/workflows/studyflow-ci.yml` **byte-for-byte unchanged**. Instead, the new `studyflow-sfdt22-hotfix-session.yml` is an independent opt-in workflow. A PR `labeled` event with label `sfdt22-hotfix-session` runs the first three real journeys on an API36 Android emulator and uploads sanitized, non-qualifying diagnostics. No web/desktop/TV/deep jobs run as part of this focused workflow. It does not perform practice, cold restart or seven-screenshot qualification and cannot pass the full hosted gate.

From Termux/Zsh after HF22 is committed and pushed, to invoke this **without another push**:

```zsh
cd "$HOME/Code/studyflow"
gh label create sfdt22-hotfix-session -R Mikeyphw/studyflow --color 7A5CFA --description 'Opt-in nonqualifying SFDT22 session diagnostic' --force
gh pr edit 15 -R Mikeyphw/studyflow --add-label sfdt22-hotfix-session
```

To rerun, remove the label then add it again with `gh pr edit 15 --remove-label sfdt22-hotfix-session` followed by `--add-label`. The full SFDT21/SFDT22 workflows run on PR `opened`/`synchronize`/`reopened`, not label events. A future `workflow_dispatch` option also requires a reviewed exact SHA, but GitHub typically only exposes it once the new workflow file exists on the default branch; do not merge just to enable dispatch. Treat label-triggered runs as an additional diagnostic only.

For failed actions, use GitHub's **Re-run failed jobs** to avoid unnecessarily rerunning already successful independent jobs. Do not mistake a rerun of a previously cached artifact for acceptance of a new source SHA.

## CI cost changes

Both existing emulator workflows originally had `cache-disabled: true`. HF22 replaces it with `cache-read-only: ${{ github.event_name != 'push' }}`. PRs read but never write Gradle cache; main pushes can write, and the manual focused diagnostic lane always uses read-only caching. This can help if a compatible cache already exists; it cannot skip an APK rebuild when source changes and does not eliminate Android emulator startup cost.

Other jobs remain unchanged, including the full five-journey, forced-new-PID, seven-distinct-screenshot SFDT22 gate. No test is silently skipped, made non-blocking, or treated as qualifying when only a diagnostic subset passed. There is no Gradle execution in the installer or on Termux.

## Required acceptance sequence

1. Source tests and exact native Devtool overlay verify on clean source SHA/tree (installer fails closed on any mismatch).
2. GitHub CI + Devtool/immutable source + SFDT21 must remain green.
3. HF22 focused diagnostic must explicitly return `DIAGNOSTIC_PASS_NOT_RELEASE_QUALIFIED` on its three real journeys, if dispatch is supported.
4. Full SFDT22 must return `PASS_HOSTED_REAL_STUDY_JOURNEYS`, five tests + seven unique screenshot hashes + force-stop and new PID + all five checkpoints.
5. Only when the full result is green can this milestone proceed to roadmap activities. Production bug repair must be based on the first failing checkpoint, not on default settings injection.

### Local receipt interpretation

```zsh
python3 -B scripts/sfdt22_failure_triage.py /path/to/unzipped/SFDT22_EVIDENCE.json
```

The tool does not disclose anything beyond a fixed allowlist of reason/stage categories and the ordered state checkpoints. It deliberately rejects corrupt/oversized receipts.

### Important remaining limitations

A PR push **still** triggers the full required hosted SFDT22 gate and the independent SFDT21 workflow. The focused lane avoids redundant manual full reruns; it does not bypass mandatory protection or stop automatic runs. The source-pinned HF22 installer does not apply if the GitHub branch has advanced. API36 emulator acceptance cannot be claimed until Actions has executed; local tests here cover source/contracts only. This is a focused observability/CI hardening milestone, not evidence that every app feature is fully hardened.

### SFDT01 source-negative regression safeguard (HF22 HF02)

The original StudyFlow CI SHA guard remains exactly one occurrence of
`test "$EXPECTED_SHA" = "$GITHUB_SHA"`, because `scripts/test_sfdt01_source_contract.py`
intentionally mutates it and must reject the tampered workflow. The new
focused hotfix job uses the equivalent strict `[[ "$EXPECTED_SHA" == "$GITHUB_SHA" ]]`
syntax. HF22's generator and source tests reject a duplicate historical guard.

## HF04 — cutover source contract and independent opt-in diagnostics

HF03 failed the Devtool cutover regression because it modified the exact legacy
`web`, `desktop`, `android`, `deep-contracts` dispatch-only conditions. HF04
leaves `.github/workflows/studyflow-ci.yml` entirely untouched. No CI contract
was relaxed. The separate non-qualifying diagnostic workflow responds only
to the `sfdt22-hotfix-session` PR label `labeled` event or explicitly reviewed
manual SHA dispatch (once available on the default branch). This event does
not trigger the original SFDT21/SFDT22 workflows, whose ordinary PR triggers
are `opened`, `reopened`, and `synchronize`. The full suite still runs for every
PR push and can never be replaced by this diagnostic.

The Kotlin UI test also avoids the previously prohibited domain-container
access and blocking coroutine calls. It uses `SQLiteDatabase.OPEN_READONLY`
and a parameter-bound SQL aggregate to count matching plan rows and stored
settings. Only ordinal checkpoint enums leave the app. `studyflow.db` and
`study_plans`/`study_plan_settings` are grounded in StudyFlow's Room schema.
