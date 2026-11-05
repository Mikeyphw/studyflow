# SFDT10-R1 — Native Android Debug/Diagnostics Evidence Shadow

**Scope:** additive, manual-only StudyFlow × Devtool CI candidate. Existing
`studyflow-ci.yml` remains authoritative. No release, signing, device
instrumentation, branch protection change, or CI cutover is authorized.

## Source identity

- StudyFlow audited base commit: `a7f2d9cfc744ce41b13041b692e34879f0f9b967`
- Native Devtool runtime pin: `0ebd22c17ac3058704012a1e03ee3265328b0659`
- New workflow: `.github/workflows/studyflow-devtool-sfdt10-debug-shadow.yml`
- Executor: existing first-class Devtool `android-gradle` native RM-06 DAG runner;
  **no new Gradle shell bridge** and no build outside GitHub Actions.

## Three independent hosted lanes

| Lane | Reviewed job | Native tasks | Required artifacts |
| --- | --- | --- | --- |
| phone-debug | `sfdt07-phone-debug` | `:app:assembleDebug :app:lintDebug` | debug APK, lint XML, optional HTML/SARIF |
| tv-debug | `sfdt07-tv-debug` | `:app-tv:assembleDebug :app-tv:testDebugUnitTest :app-tv:lintDebug` | debug APK, lint XML, TV JUnit, optional HTML/SARIF |
| phone-release-jvm | `sfdt08-phone-release-jvm` | `:app:testReleaseUnitTest` | phone Release JVM JUnit, native typed JUnit proof |

The separate phone Release JVM job is intentional: Devtool's reviewed
Android provider permits `:app:testReleaseUnitTest` only as a dedicated
unsigned host-side task. Mixing it into the debug APK job would bypass
its explicitly admitted task contract.

## Trust and evidence semantics

The collector rejects missing lint XML, malformed JUnit XML, absent passing
JVM tests, failed tests, unknown or mismatched source/run/attempt, altered
native typed receipts, missing APKs, invalid APK signatures, symlinks, path
traversal, oversized evidence, and stale evidence destinations. It preserves
SHA-256 for every staged member and both native and typed receipt bytes.
Each output manifest deliberately records `legacy_equivalent=false`,
`deep_equivalent=false`, `remote_authenticated=false`,
`cutover_authorized=false`, `release_qualified=false`, and
`device_qualified=false`.

APK SHA-256 may legitimately differ between independent builds. Artifact
parity means *reviewed source identity, task ownership, package identity,
report coverage and hashed member inventories*, not automatically bit-for-bit
APK reproducibility. To prove digest equality, a separate reproducible-build
qualification would be necessary. Local receipt checksums alone do not prove
GitHub upload ownership; verify live run/job/artifact metadata and transfer
SHA-256 independently before a parity decision.

`phone-debug` has a truthful `test=NOT_RUN` evidence state, because its native
job builds debug and lint only. `phone-release-jvm` supplies the separate
legacy phone JVM test contract. The TV job has native tasks for its test lane,
with local independently parsed JUnit members.

## Acceptance sequence (do not switch CI)

1. Apply the pinned additive overlay to a fresh `sfdt10-r1-debug-parity` branch.
2. Push the branch, open a PR and merge the additive source-only change.
   GitHub workflow_dispatch cannot reliably dispatch a new workflow until its
   YAML is present on the default branch; a branch-only workflow may 404.
3. Record the resulting **main HEAD SHA**. Dispatch both the authoritative
   legacy workflow (`lane=all`) and the SFDT10 native shadow using that exact
   same SHA and `--ref main`.
4. Require all five legacy active jobs and all three SFDT10 native jobs to pass.
5. Download original GitHub artifacts from both runs and compare exact source,
   artifact role, JUnit count, lint presence, signer identities and SHA-256 member
   inventories. Verify run IDs, attempts, names and GitHub artifact digests.
6. Keep source-shadow, expanded native, Web, desktop, deep, policy and device
   gaps separately blocked. Do not claim a four-way comparator from 2 green runs.

## Explicit outstanding SFDT10 work

- Same-source four-way comparison, including **source-shadow and SFDT07
  expanded** on the new commit, plus SFDT08 fast/deep with appropriate pinning.
- Native first-class Node/npm and desktop Gradle task DAGs, including test,
  lint, build/release:check and typed report ownership; current bridge tests
  cannot be misrepresented as first-class evidence.
- Native quality/parity and deep task graph equivalence, push/PR/required-check
  compatibility, failure propagation and branch protection transaction plan.
- Signing environment policy readback and real phone/Leanback TV acceptance,
  both distinct from normal source-only CI migration.
