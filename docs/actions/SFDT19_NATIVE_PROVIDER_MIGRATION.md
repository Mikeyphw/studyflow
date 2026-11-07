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

## HF03 — Materialize offline Desktop classpath artifacts

The HF02 hosted run `42390639526` passed source, Phone, TV, native Web,
SFDT11 and the independent Node consumer, but native Desktop RM12 failed
when `:desktopApp:test` attempted to compile against uncached Compose Desktop,
Kotlin and AndroidX JARs in `--offline` mode. Gradle's previous
`:desktopApp:dependencies` reports resolved component metadata, not all
artifact bytes. The configuration-cache serialization error was a symptom
of those unresolved offline artifacts, not a reason to disable Devtool tests.

HF03 replaces that metadata-only step with a checksum-pinned, reviewed Groovy
init script that registers a **temporary hosted dependency materialization
task** on `:desktopApp`. It force-resolves actual external module artifacts
for compile/runtime and test compile/runtime configurations using Gradle
`artifactView`/`ModuleComponentIdentifier`, checks nonempty readable files,
and refuses missing or unbounded results. It excludes project-owned artifacts,
so it never builds the Desktop app or produces tests during preparation.
The prewarm invocation opts out of the configuration cache solely for this
ad hoc task, leaving the native RM12 `gradle --offline ... :desktopApp:test`
command and its separate provider, source, test and JUnit evidence unchanged.

The prewarm script is SHA-256 pinned inside the primary workflow, and its
review is enforced by a source-only regression. No `.devtool.toml`, SFDT11
immutable policies, Web/Phone/TV providers, signing, release, device or
rollback jobs are modified. Real hosted acceptance remains mandatory;
a passing source test cannot establish the offline JVM build succeeded.

## HF04 — Prewarm Kotlin Build Tools API artifacts for offline Desktop JVM

The HF03 PR SHA `04811cc9680a532e9e8f6f441b5c4d616f95ac72` ran
`StudyFlow Devtool CI` as GitHub Actions run `42393798945`. Its hosted
prewarm succeeded with **31, 44, 35 and 45 external files** across the four
Desktop compile/runtime/test classpaths, but the native RM12 JVM test still
failed. The exact missing offline inputs were
`org.jetbrains.kotlin:kotlin-build-tools-compat:2.4.10` and
`org.jetbrains.kotlin:kotlin-build-tools-impl:2.4.10` in
`:desktopApp:kotlinBuildToolsApiClasspath`, a Kotlin compiler-internal
configuration not covered by HF03. The configuration-cache serialization
failure was secondary to those missing artifacts.

HF04 extends the checksum-pinned, GitHub-only prewarm task to require the
actual Kotlin Build Tools API JAR bytes and fail if they are not available.
It also resolves known Kotlin compiler and compiler-plugin configurations
if they are present and resolvable. The task still only downloads external
module artifacts, never executes Desktop compilation or tests, and never
accepts metadata alone. `ci_toolchain=jvm` and the Devtool-owned literal
`gradle --offline --no-daemon --console=plain :desktopApp:test` are unchanged.
The previous Python bridge remains an unused rollback option. The workflow
and source regression pin the updated prewarm script by exact SHA-256.
There are no `.devtool.toml`, SFDT11 policy, Android, Web, signing or
release changes. Real hosted Desktop JUnit and aggregate gate evidence
remain necessary; source checks cannot substitute for GitHub acceptance.

## HF05 — Materialize the complete reviewed Desktop JVM closure

HF04 reached the real `:desktopApp:test` offline JVM task on commit
`444cbf7576e6b5199e3700e5278724a95d3de969`, but hosted run
`42395335373` failed because the Kotlin serialization plugin artifact
`org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable:2.4.10`
was not cached for `:core:portability:kotlinCompilerPluginClasspathMain`.
The configuration-cache serialization diagnostic was secondary to that
missing module-owned compiler-plugin classpath artifact.

HF05 expands the checksum-pinned GitHub-hosted prewarm task to the exact
reviewed JVM closure: `:desktopApp`, `:core:model`, `:core:domain`,
`:core:link`, and `:core:portability`. For each of those modules it requires
external bytes for compile/test runtime classpaths and any declared Kotlin
compiler/build-tools/plugin classpaths; on both serialization modules it also
requires the specific Kotlin serialization compiler plugin JAR. All other
projects are excluded and project-owned components are never compiled by
prewarm. Kotlin tests and fresh JUnit remain solely owned by Devtool RM12's
unchanged `gradle --offline --no-daemon --console=plain :desktopApp:test`.

No `.devtool.toml`, SFDT11, native Web, Phone/TV, signing, release, credentials,
local Gradle or Python rollback changes. Source-only checks and bundle tests
cannot qualify the build; a new hosted PR SHA must pass the aggregate gate.

## HF06 — Gradle 9 project-scoped cache preparation

HF05 reached the source-bound hosted prewarm task on PR #12 at
`d4c5870a9aa469853bd6b046fca4abee01ffa7a6`, but run `42397522465`
failed before the JVM test with Gradle's `Resolution of the configuration
':core:model:compileClasspath' was attempted without an exclusive lock`.
HF05's aggregate task attempted to resolve sibling-project configurations
from a `:desktopApp` task, which Gradle 9 refuses as unsafe.

HF06 registers an identically named, read-only external-artifact prewarm task
**inside each of the five exact reviewed JVM modules**, then makes the
`:desktopApp` entry task depend on the four `:core` tasks. Every module now
resolves its *own* configurations while Gradle holds that project's task
lock. The Kotlin 2.4.10 Build Tools and serialization compiler plugin JAR
checks are retained, as is byte-based artifact resolution. Project-owned
outputs are not compiled or tested during preparation. `:desktopApp:test`
remains exclusively owned by Devtool's unchanged offline JVM provider.

The prewarm SHA-256 is re-pinned in the workflow and all existing source
regressions, and HF06 adds an explicit no-cross-project test. No change to
`.devtool.toml`, SFDT11, Devtool SHA, native Web/Android, signing, publishing,
secrets, local Gradle or the Python rollback bridge. Hosted CI remains the
only acceptance gate; this package's offline tests do not prove the Gradle
build succeeds on GitHub.
