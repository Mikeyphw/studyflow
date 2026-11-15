# SFDT22 HF30 HF08 — AndroidX instrumentation runner bootstrap

## Verified failing run

GitHub Actions SFDT22 full API36 run 43106844247, commit
`d31179efd5e9895872b821c1942402460d7852a2`:

- Source/trust checks passed.
- `:core:data:assembleDebugAndroidTest` passed.
- `:core:data:connectedDebugAndroidTest` failed before any test assertions.
- Android runtime threw `ClassNotFoundException` for
  `androidx.test.runner.AndroidJUnitRunner` while instantiating
  `com.mikeyphw.studyflow.core.data.test/androidx.test.runner.AndroidJUnitRunner`.
- HF07 correctly recorded `room_instrumentation_failed` and `ui=NOT_RUN`.
- The launcher correctly refused to report a green CI run.

## Correction

`core:data` declared the AndroidX JUnit extension but not the AndroidX
instrumentation runner. Register the stable official runtime dependency
`androidx.test:runner:1.7.0` in the version catalog and explicitly add
`androidTestImplementation(libs.androidx.test.runner)` to core:data.

An offline source contract checks version pins, runtime dependency, Room
execution, five real UI journeys, seven screenshots and independence of the
source/emulator success gate. Full workflow SHA-256 pins prevent unreviewed
changes to the version catalog, core:data Gradle file or new contract.

Source: https://developer.android.com/jetpack/androidx/releases/test

## Qualification

This is a test APK bootstrap fix, not a claim that the five Room methods or five
real production UI journeys passed. The existing HF07 and OPT01 implementation
and all release/physical-device restrictions remain unchanged. Rerun the
full hosted GitHub API36 workflow. Do not force-push, merge, release or skip
any mandatory gate. If Room tests fail after the runner loads, inspect their
real JUnit method names and evidence instead of bypassing them.
