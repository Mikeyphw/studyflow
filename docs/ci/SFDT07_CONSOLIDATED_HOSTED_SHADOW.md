# SFDT-07 — Consolidated native hosted shadow (Wave 3)

**Status:** Source overlay is ready for GitHub acceptance. No live hosted pass or cutover is claimed by its installation.

## Goals

Extends the accepted four-node native source shadow and StudyFlow legacy CI, using the same pinned native `devtool.actions_graph_export.compile_graph` and `run_node` for four additional independent GitHub-only shadow lanes. No second Gradle engine, no local Gradle, no signing, no release/device qualification, and no automatic source/CI cutover.

| Native shadow lane | Actual native job | Verified subset | Intentionally not equivalent to |
| --- | --- | --- | --- |
| `phone-debug` | Android Gradle semantic `:app:assembleDebug`, `:app:lintDebug` | Debug artifact bytes + lint command success | Legacy Release JVM tests, release APK, signer/device |
| `tv-debug` | Android Gradle semantic `:app-tv:assembleDebug`, `:app-tv:testDebugUnitTest`, `:app-tv:lintDebug` | Debug artifact + explicit nonempty Gradle JUnit XML | Full TV/quality/release/device acceptance |
| `web-pytest` | SHA-pinned Python provider invoking the Web pytest suite from `web/` | Hosted real pytest JUnit XML, zero failures | Entire `npm test`, lint, bundle, release checks |
| `quality-source` | SHA-pinned Python provider running four existing source checks | Source-only quality regressions | Gradle quality + parity task inventories |

Devtool's present Node adapter requires a top-level, reviewed package manager and enumerated script grammar. StudyFlow's `web/package.json` uses custom compound npm scripts. No attempt is made to falsely classify those as a fully native Node job. Likewise, its desktop Gradle and root-level Gradle quality/parity tasks remain on the existing CI path.

The compiled graph and run-stage evidence are owned by the **installed pinned Devtool runtime**, not by shell commands in the workflow. The workflow only provisions the host, acquires the immutable Devtool runtime, invokes the one-node reviewed native graph, and uploads bounded evidence.

## Source and security constraints

- All four lanes run only from `workflow_dispatch` with the exact checked-out StudyFlow SHA. `source_sha`, Devtool runtime SHA, native graph SHA, job ID, GitHub run ID and attempt are bound in the per-lane JSON receipt.
- GitHub Actions permissions are `contents: read`; checkout has no persisted credentials; no signing environment or user secrets are available.
- Static Android profile: JDK 21, SDK 36, build-tools 36.0.0, NDK 29.0.14206865, Gradle 9.7.1 checked through Devtool's SFDT-05 provider. `gradle-wrapper.jar` SHA-256 is pinned independently.
- APK output paths are byte-exact, matching existing hosted legacy CI outputs. Android native graph inputs are **literal**, not generated-input globs, to satisfy Devtool's RM-06 output ownership proof.
- The Web and quality Python provider scripts are SHA-256 pinned in `.devtool.toml`. These commands never execute Gradle locally or inside the Devtool artifact validation hook.
- Android build jobs explicitly refuse release/signing/device, only produce debug bytes, and cannot claim remote authenticity. If a hosted task fails, `FAILED_INCOMPLETE` (or `FAILED_OR_NOT_RUN`) is the only permitted receipt.
- The manual workflow retains the prior `studyflow-devtool-shadow.yml` source-shadow lane and does not remove existing `studyflow-ci.yml` fast/deep jobs.

## How to run on GitHub

After the overlay is installed and its automatic commit pushed to GitHub, dispatch all four lanes against **that exact commit**:

```zsh
cd "$HOME/Code/studyflow"
SHA="$(git rev-parse HEAD)"
git push origin main
gh workflow run studyflow-devtool-shadow.yml -R Mikeyphw/studyflow --ref main -f expected_sha="$SHA"
gh workflow run studyflow-devtool-native-expanded.yml -R Mikeyphw/studyflow --ref main -f expected_sha="$SHA"
gh run list -R Mikeyphw/studyflow --limit 10
```

Once the legacy CI (push), original source shadow and new expanded shadow have completed, run the **read-only** three-run comparison using the actual IDs:

```zsh
python3 scripts/sfdt07_compare_runs.py \
  --legacy-run LEGACY_RUN_ID \
  --source-shadow-run SOURCE_SHADOW_RUN_ID \
  --expanded-run EXPANDED_SHADOW_RUN_ID --json
```

The comparator checks all three runs and all four expanded jobs against the same consumer commit. **By default it downloads the four expanded shadow artifacts using read-only `gh run download`, then independently checks** the per-lane receipt, source/tree/run/attempt/graph/job/provider identity, canonical Devtool typed-evidence digest, exact debug APK byte SHA-256, and Web/TV JUnit testcase members and SHA-256. TV test reports are staged into the artifact expressly for this cross-run verification. A quality-source receipt must contain the four expected source verifiers. An absent or tampered member fails closed. The artifact transfer digest alone is never interpreted as authenticity or provenance.

For a fast, low-bandwidth comparison without downloading members, pass `--metadata-only`; its result explicitly says `METADATA_ONLY_UNQUALIFIED`, so it is **not** a member verification or a native-equivalence gate. Both modes keep `cutover_authorized=false`, and neither claims signed APKs, trusted remote producers, device verification, or complete Web/desktop/deep equivalence.

## Acceptance and next steps

1. Source overlay acceptance: native artifact application/rollback and source-only test suite passes. **No Gradle on Termux.**
2. Hosted acceptance: inspect all four GitHub matrix jobs, the downloaded typed evidence and `receipt.json` members with the default three-run comparator. A failed job remains a failure; do not mark it equivalent to legacy.
3. Run comparison at an identical consumer SHA alongside the existing successful source shadow and full legacy fast CI. Explicitly record skipped deep profiles.
4. Remaining provider expansion: Devtool-reviewed Web composite Node + nested-root support, desktop and root-level Gradle/quality/parity support, Release JVM unit test variant review, and authenticated artifact/JUnit imports. **No CI cutover yet.**
5. Signing provenance, release admission and physical-device acceptance remain SFDT-07/08 separate acceptance gates. Neither these shadow jobs nor any success badge can authorize them.

## Local source-only verification

```zsh
python3 -B -S scripts/sfdt07_consolidated_contract.py --check
python3 -B -S scripts/test_sfdt07_consolidated_contract.py
```

The scripts above require only Python 3.11+ standard library; they never execute Gradle or access remote APIs.
