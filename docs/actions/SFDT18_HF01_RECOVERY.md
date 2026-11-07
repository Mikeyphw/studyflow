# SFDT18-HF01 — hosted SFDT07 phone-debug stall containment

**Scope:** non-authoritative GitHub-only diagnostics and exact-SHA one-lane
recovery. Source-pinned to StudyFlow main
`7d4319533f631cad6f83810348d80fd28919c5d8` as the implementation base.
No Devtool source changes; the SFDT07 workflow continues to run its existing
reviewed Devtool source `df2d306045619bc096c3bd18a68c3fb85023e4dd`.

## Evidence and diagnosis

- [SFDT07 run 42270845902](https://github.com/Mikeyphw/studyflow/actions/runs/42270845902)
  was cancelled; three of four native shadow jobs succeeded.
- `native-phone-debug` executed `./gradlew --no-daemon :app:assembleDebug :app:lintDebug`
  as a Devtool graph node. Its last substantive Gradle output was the
  `:app:lintDebug` task at 18:36:46 UTC, after the HTML and SARIF lint reports.
- From that moment, the runner reported no further Gradle child output for
  ~56 minutes. The GitHub job was cancelled at 19:32:32 UTC, consistent with
  its configured 65-minute job timeout.
- Its 422-byte ZIP is a failure receipt, not an Android debug APK.
- An Android lint failure, deadlock cause, external cancellation reason or
  resource exhaustion cannot be asserted from this log alone. The guard
  provides evidence on a future reproduction; it does not claim to cure the hang.

## Guard policy

The phone lane uses `scripts/sfdt18_phone_debug_guard.py` to invoke **only**
the existing `sfdt07_native_lane.py` and Devtool native `run_node`; the guard
itself never calls Gradle. It forwards stdout/stderr, ignores Devtool heartbeat
text for progress assessment, captures a bounded `ps` process listing and
up to three in-process-group Java thread dumps via `jcmd` on timeout, and
terminates the isolated native process group if no *substantive* output occurs
for 10 minutes or total runtime reaches 42 minutes. GitHub's 65-minute job cap
is unchanged and becomes a last-resort boundary. Both guard timeouts are
reported as failures with exit 124. `hf01-guard.json` includes the source/run
identity and no cutover authority. Diagnostics and failure receipt are uploaded
by the existing nonrelease SFDT07 artifact step; no secrets are collected.

The remaining SFDT07 matrix lanes and legacy StudyFlow CI are unchanged.

## Hosted validation and recovery

The existing SFDT18 PR source-test workflow now also runs
`tests/test_sfdt18_phone_debug_guard.py`; it invokes only dummy Python
processes, never local Gradle. `tests/test_sfdt18_evidence_coordinator.py`
checks same-SHA recovery, candidate validation, and failure refusal.

After merging, obtain a new five-lane same-merge-SHA cohort using
`python3 -B scripts/sfdt18_evidence_coordinator.py dispatch`. If SFDT07
fails while the other four are green at that same SHA, use `recover`, then
`adopt` **only after** the new candidate succeeds. See
[`SFDT18_HOSTED_COORDINATOR.md`](SFDT18_HOSTED_COORDINATOR.md).

**The old four successful runs cannot be reused across an HF01 merge**, because
the StudyFlow SHA changes. Do not edit state JSON to make the SHA match.

A green SFDT17 ZIP verification is not task parity, release qualification,
platform signing, device acceptance or CI cutover authority. All those gates
remain explicitly NO-GO.
