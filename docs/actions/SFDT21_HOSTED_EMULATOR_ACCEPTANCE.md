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
