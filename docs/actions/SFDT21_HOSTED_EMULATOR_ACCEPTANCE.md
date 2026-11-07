# SFDT21 — Real Android instrumentation and API 36 emulator acceptance

## Scope and trust boundaries

This is an independent, required-to-pass *candidate* for StudyFlow's next
major quality gate. It builds **debug app and debug AndroidTest APKs** only
on ephemeral GitHub `ubuntu-24.04` runners with JDK 21, Android SDK 36,
NDK 29 and pinned Gradle 9.7.1, then uses Cinderbox-Companion's already
proven `ReactiveCircus/android-emulator-runner` pinned action with a Google
APIs x86_64 Pixel 6 API 36 emulator and hardware KVM.

The production Compose UI is unchanged; Kotlin instrumentation test bytes are additionally SHA-256 pinned in the source job. The workflow is new; it does not replace the existing native Devtool CI or
change release signing, release workflow, key management, Android permissions,
or physical-device policy. The app's historical `testBuildType = "release"`
remains the default. **Only** `GITHUB_ACTIONS=true` plus
`STUDYFLOW_SFDT21_HOSTED_EMULATOR=true`, with no personal/release signing,
selects `testBuildType = "debug"` to create an emulator-only instrumented
APK. This does not allow an unsigned release build.

## Real UI test coverage

Four AndroidJUnit4 tests start the **actual** `MainActivity` and app's
production Navigation Compose code, using `createAndroidComposeRule` without
calling `setContent()` or replacing UI with fixtures:

1. Tap Today → Plan → Practice → Progress → Today, check each selected root
   and capture four real Compose-rendered PNGs.
2. Select Practice, recreate the real Android activity, verify selected root
   restoration and navigate back to Today.
3. Repeatedly switch nonadjacent roots and check that each real touch action
   selects the destination without losing any of the four navigation targets.
4. Open the actual Global Search route from Today, verify the real search input
   is displayed, and return to Today through Android Back.

The tests reuse existing production `Role.Tab` semantics and localized
workspace labels; **no production UI, layout, font, theme, accessibility
preference or navigation behavior is changed.**

## Emulator-hosted acceptance and evidence

The job refuses a non-Linux/non-x86_64 host, missing KVM, wrong source SHA,
wrong API, a physical device (`ro.kernel.qemu != 1`), or an app already
installed on the emulator. It independently checks the debug APK and AndroidTest APK
signatures, signing certificate SHA-256 identities and complete file SHA-256, confirms the installed `base.apk` matches the built
app exactly, disables network, and executes the four named real UI tests
through the AndroidJUnitRunner. It records no raw instrumentation logs.

A separate verifier requires all four tests to be started and passed, the
successful instrumentation terminal markers, four genuine bounded PNGs
with consistent dimensions and unique SHA-256 hashes, the exact source/run
identity, and explicit **false** physical-device/release/publication flags.
Only the minimized JSON receipt and four synthetic-emulator PNGs are
uploaded; on failure the bounded receipt stays FAIL/NOT_RUN. No credentials,
user study database, private screenshots or restored accounts are used.

## Hosted qualification and follow-up

Review a fresh CI run at the candidate PR SHA. Do not merge because source
checks pass: both `SFDT21 instrumentation source and safety` and
`SFDT21 API36 real MainActivity UI instrumentation`, plus the aggregate
`SFDT21 hosted emulator UI gate`, must be green. Inspect the actual test
roster, PNG hashes and app/test APK identity. Existing Devtool, SFDT11,
SFDT20 provenance checks must remain green on that same commit.

After merging, independently confirm the push-triggered SFDT21 run at the
new `main` SHA before considering this status a required branch check. Only
then add `SFDT21 hosted emulator UI gate` to the repository's existing
branch-protection/ruleset **without erasing other required checks**. Never
claim this is physical hardware, TalkBack, release signing or production-
publishing acceptance. Additional device-specific flows remain future work.

## Rollback

Revert this consolidated SFDT21 change. This restores the prior release
instrumentation configuration and removes the isolated emulator workflow;
SFDT19 native providers and SFDT20 provenance remain untouched.

## HF01 — Hosted CI first-run fixes (2026-11-07)

PR #14's initial hosted run at SHA `e140483cb97703e0d65b345cfd41c5a87b112851`
identified two independent failures, both before emulator execution:

1. Legacy `StudyFlow CI` run `42411453587` rejected the source-only
   `testBuildType` contract because the conditional assignment obscured the
   literal `testBuildType = "release"` default. HF01 restores that exact
   release-default assignment first, then overrides to `debug` **only** when
   `GITHUB_ACTIONS=true`, `STUDYFLOW_SFDT21_HOSTED_EMULATOR=true`, and both
   personal and release signing inputs are absent. Neither the regular JVM
   release test profile nor release signing or the SFDT19/20 pipeline changes.
2. `SFDT21` emulator run `42411453613` failed at
   `:app:compileDebugAndroidTestKotlin`: `androidx.compose.ui.test.onNode`
   and `onAllNodes` are rule members, not valid standalone imports, and the
   test needs `android.graphics.Bitmap` for PNG compression. HF01 corrects
   those imports while preserving the production `MainActivity` UI journey.

The native Devtool CI run `42411453588` also failed its Phone Release JVM
provider on `SFDT08 phone Release JVM testBuildType is not reviewed`. Review of
the exact pinned Devtool runtime (`devtool/actions_android_provider.py` at
`bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`) confirms that this provider
also matches the same literal `testBuildType = "release"` declaration. Thus the
one fail-closed Gradle source fix addresses the existing native Phone provider
**and** the legacy StudyFlow source gate, while leaving `:app:testReleaseUnitTest`
and debug APK semantics as they were.

The standalone SFDT21 source job now runs **both** established legacy
source contracts and the SFDT21 negative tests before any emulator build. The
workflow's Kotlin test source SHA-256 is updated together with the test file;
this hotfix does not relax checks, downgrade tools, or silently skip any
UI tests. The emulator and screenshot acceptance remained **unqualified**
until a fresh hosted run at the new exact PR SHA passes.


## HF02: hosted failure-stage diagnostics and early APK admission

In PR #14 HF01's second run (42413136975), all Java/Kotlin debug and
instrumentation APKs compiled; legacy StudyFlow CI, SFDT11 and all Devtool
lanes passed. The API36 KVM emulator booted, but before starting instrumentation
`sfdt21_emulator_verify.py --run` produced `tests=0` and a fail receipt with only
`failure_class=Refused`. Neither the sanitized receipt nor the hosted logs
establish which exact pre-instrumentation guard failed. Do **not** attribute
this to the UI tests, ADB, signing or APK path without new evidence.

HF02 adds a mandatory non-mutating hosted APK/pre-signing preflight before
starting the emulator, retaining the exact source/run identity; no APK/test
output is uploaded by this step. In the actual hosted emulator, HF02 writes
an enumerated `failure_phase` in the fail-closed receipt, covering host, emulator,
APK, signing, install, installed hash, instrumentation and screenshot phases.
Raw ADB output, stack traces, environment, private paths and secrets are never
retained. A non-PASS outcome still fails the emulator job and gate.

A new hosted attempt is necessary to identify the phase, then determine whether
any real production change is required. HF02 does not claim that the unknown
pre-instrumentation issue has been repaired or that the UI tests ran.

## HF03: API36 fresh package admission correction

The HF02 hosted attempt at PR #14 SHA `a0124d06ddc542435c1dd455c040f80a7a9ca0ab`
(`StudyFlow SFDT21 Real Android UI Emulator Acceptance` run `42419924673`)
passed the independent pre-AVD APK/signature check, compiled both Android
APKs and booted the Google APIs x86_64 API36 emulator. It then failed
`SFDT21_EMULATOR_REFUSED: tests=0 phase=fresh_install` before any real UI
instrumentation. This localizes the problem to the freshness probe, not Kotlin
compilation, emulator boot, or a Compose test assertion.

Previously the Python subprocess wrapper required a zero exit code from
`adb shell pm path com.mikeyphw.studyflow`. Android may return a nonzero exit
for an *absent* package, even though absence is the expected fresh state.
HF03 uses `adb shell pm list packages com.mikeyphw.studyflow`, which returns a
zero status and an empty listing for an absent app, and rejects **any** returned
StudyFlow-prefix package, including the `.test` instrumentation APK. ADB
failure or unexpected nonempty output remains a hard rejection. The independent
source and evidence checks, SHA pin, hosted-only gates, and screenshot
requirements are preserved. No release/signing/device/production UI change.

HF03 tests verify the absent-package success path, rejection of app/test APKs,
ADB errors, and that the runner progresses beyond `fresh_install` to the next
stage. A fresh GitHub Actions run must still pass all four UI tests and PNG
provenance before merging; this hotfix is not a hosted pass claim.
