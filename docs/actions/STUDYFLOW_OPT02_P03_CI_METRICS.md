# StudyFlow OPT02 P03 — Reproducible CI efficiency measurement

## Acceptance evidence (15 November 2026)

Repository: `Mikeyphw/studyflow`; PR #16; branch `sfdt22-hotfix/hf30-hf04-pr-20261110`.

| Evidence | GitHub Actions run | Result |
| --- | ---: | --- |
| P01 baseline Targeted CI at `d9c9a2f28eaa4aad51266195596a8f5bb387a235` | 43735333671 | Android compiled app + instrumentation APKs in 582 s |
| HF03 Targeted CI at `66dd019dcb2d636f6e8ccd5a46ade08906e990e3` | 43751476848 | `OPT02_SHARED_APKS_HIT EXACT_SHA_SIGNED_APK_PAIR_VERIFIED`; Android Gradle compilation **skipped**; required gate success |
| Exact-SHA physical APK producer (build-only) | 43751473042 | successful push, matching signed artifact `sfdt22-physical-apks-43751473042-1`, 28,097,930 bytes |

GitHub API job timeline:

- P01 baseline Android job: 16:25:14–16:35:38 UTC, **624 seconds**. Gradle compile step: 16:25:51–16:35:33 UTC, **582 seconds**.
- HF03 Android job: 19:16:37–19:24:08 UTC, **451 seconds**. Reuse/wait step: 19:17:10–19:24:04 UTC, **414 seconds**. Gradle compile step: `skipped`. The existing producer itself still built once.
- Observed job wall-clock improvement: **173 seconds (27.7%)**. This is observational across different commits and runner conditions; **not a guaranteed causal improvement or runner-minute saving**. Waiting on a runner has cost.
- **One duplicate Android Gradle invocation was avoided** in this paired comparison. The physical build is not physical-device acceptance or release qualification.

## Reproduce safely

```zsh
cd ~/Code/studyflow
python3 -B scripts/ci/test_opt02_metrics.py
python3 -B scripts/ci/opt02_metrics.py \
  --baseline-run 43735333671 \
  --targeted-run 43751476848 \
  --producer-run 43751473042 \
  --output "$HOME/storage/downloads/StudyFlow_OPT02_P03_metrics.json"
```

This uses authenticated `gh api` GET calls to exact runs, job steps, a producer artifact listing, and the Targeted Android job log. The raw log is parsed locally, **never printed or saved** by this tool. Nothing is installed on Android, and no Gradle, GitHub write, re-run, merge or PR mutation occurs. Offline regression tests make no network requests.

### Fail-closed acceptance rules

All three runs must be successful and belong to this repository and reviewed branch. Targeted and producer runs must match the *same full 40-character source SHA*. Producer must be a push from the exact canonical workflow, with a unique unexpired artifact from the matching run attempt. Baseline and Targeted runs must both be from Targeted CI. Targeted required gate and Android job must pass; the reuse step must pass, Gradle step must be skipped, and signed APK output verification step must pass. The exact `HIT` marker must appear once, without `MISS` or `REFUSED`. The baseline must have a successful Gradle compilation. Malformed metadata, missing steps, output spoofing and nonmatching repositories refuse.

This reader does **not** claim to independently check APK bytes or signing certificates; that happened in the already qualified consumer run. The source metrics are not release or physical-device qualification.

## Further optimization candidates — OPT02 P04 / OPT03

1. **Producer-driven coordination**: avoid spending ~414 s polling on an expensive Android runner. The existing prebuild workflow can publish a completion/receipt, with an explicitly reviewed trigger and source pin, or the compilation may be moved behind a single same-workflow dependency. Must preserve PR safety and original gates. Do not introduce untrusted `workflow_run` write-token execution.
2. **Devtool phone and targeted APK work**: evaluate tasks and output provenance for same-SHA reuse. Reuse a compiled APK only if exact variant, build inputs, certificate and debug flags match; Devtool JVM/lint/test gates must remain independent.
3. **SFDT21**: GitHub pull-request workflow checks a *merge SHA*, not necessarily the reviewed branch head SHA. **Do not** share APKs from branch pushes across this identity boundary. Keep its real emulator gates.
4. **TV / desktop**: current Targeted jobs compile separate module targets. Do not substitute Android APKs. Investigate Gradle setup caching, workflow path filters and queue time separately (OPT03).
5. **Measurement**: add repeatable same-SHA comparisons and monitor real runner-minutes before declaring savings. Zero duplication is useful, but waiting 7 minutes may offset job runtime savings.

## Gates

This OPT02 P03 is **read-only instrumentation** plus pinned source contracts. It must not modify application source, physical APK producer, Android install logic, emulator behavior, release/signing, or dependency/security gates. All Gradle builds remain on GitHub Actions.
