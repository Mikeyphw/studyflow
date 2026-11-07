# SFDT19 — Native Devtool JVM Desktop + Node Web CI candidate

## Intent and exact source

Source-pinned consolidated change based on `Mikeyphw/studyflow` main merge
`3821ce939044f8e98d0ca39fe6ea327bba0d8a3f`, with the existing
immutable Devtool runtime at `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`.
This PR must be accepted by **real hosted checks** before merging. Source-only
checks, mocked receipts or a previously green PR do not qualify the new path.

## Native ownership

- **Phone/TV:** unchanged first-class `android-gradle` native jobs.
- **Desktop:** new `studyflow_desktop` `sfdt19-native-desktop-test` target-owned,
  first-class RM12 `jvm` provider (`gradle --offline --no-daemon
  --console=plain :desktopApp:test`), reviewed `:desktopApp` JVM-only closure,
  JDK 21, Gradle 9.7.1, verified `dist/jvm-tests.xml`, JUnit source directory.
- **Web:** new repository-root `studyflow_android`-owned `sfdt19-native-web-test` first-class
  RM11/RM17 `node` provider (`npm run test`, Node 22, npm 10.9.4,
  `reviewed-composite`). The source-reviewed npm test plan retains the original
  unit tests, then executes lint, build and release checks, and finally real
  pytest cases. It writes `web/dist/test-results.xml` as the provider-owned
  report and a distinct fresh JUnit copy under
  `web/build/test-results/native-node` for byte-attested generic CI evidence.
- Existing `ci-sfdt08-desktop-fast` / `ci-sfdt08-web-full` Python bridge jobs
  remain configured, but no longer execute in the primary CI matrix. They are
  rollback paths only. The legacy `studyflow-ci.yml` remains manual-dispatch.

The host prewarms reviewed Desktop dependency configurations via GitHub-hosted
Gradle dependency-report tasks to support Devtool's **offline** JVM test
provider; this does not compile/test the application. The native `run_node`
remains the sole Desktop test owner and must independently produce valid
fresh JUnit bytes. **Offline dependency resolution may still require further
upstream work for this large Android/JVM mixed build.** A hosted failure must
not be reclassified as success or silently fall back to Python.

## Gates and safety

- Immutable source pins and SFDT11 `.devtool.toml` Git blob are updated together.
- Native provider type, workflow, project/module, exact outputs, source/runtime
  SHA, local receipt identity and non-release trust remain fail-closed.
- The normal `Devtool CI gate` still requires source + phone + TV + Desktop +
  Web success, real Android debug APK bytes, Android lint, and fresh JUnit.
- Workflow permissions stay `contents: read`; no signing, release publishing,
  device instrumentation, secrets, branch protection changes or local Gradle.

## Required hosted acceptance (not yet performed)

1. Confirm `StudyFlow Devtool CI` passes at the **new PR SHA**, with actual
   `jvm` and `node` typed provider receipts, fresh native JUnit and phone/TV APKs.
2. Confirm legacy source CI and immutable SFDT11 policy pass at the same SHA.
3. Inspect the actual result counts, artifact SHA-256 and rollback configuration.
4. Merge **only after** all three pass. Confirm fresh `main` push CI and that
   required status checks still enforce `Devtool CI gate`.

If native provider admission refuses the mixed JVM closure, or the offline
Gradle cache lacks required dependencies, leave the PR unmerged. Fix the actual
Devtool/provider or consumer source contract and rerun hosted acceptance. Do
not bypass admission, switch to an unreviewed shell command, substitute a fake
JUnit report, or weaken source/signing checks.

## Rollback

Revert SFDT19's consolidated commit and preserve the already merged PR #11
primary cutover. The previous SFDT08 bridges remain in `.devtool.toml`; no
secrets or publication settings are changed. Restore their NODES references in
the primary orchestrator only if an explicit rollback PR is required.

## HF01 — Native Node graph root repair

PR #12's first hosted Web lane failed at graph admission:
`NEXT-03 subdirectory root requires reviewed Android Gradle project_dir`.
The `studyflow_web` target has root `web`, which the Devtool exporter treats
as a nested job root and refuses for native Node. HF01 moves only the native
Node CI job and DAG workflow to the existing repository-root `studyflow_android`
target (root `.`), while retaining `ci_toolchain=node`, reviewed nested Node
project directory `web`, and all source/test/JUnit/rollback contracts. This
is a host-layout correction, **not** a downgrade to Android or Python providers.
A second observed hosted failure, `RM-06 cannot prove generated-input
ownership through globs`, rejected the Desktop native job before JVM tests
ran. The same invariant would have rejected Web after its target-root fix.
HF01 changes **both** new native jobs to literal input paths. Devtool's
provider-specific source reviewers still independently source-hash the actual
tracked JVM and Web dependency/script closure; no generated artifact is
accepted from an unowned broad glob.
All prior hosted evidence refers to the earlier PR SHA and cannot qualify HF01.
