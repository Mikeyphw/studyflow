# SFDT22 HF01 — hosted CI repair (source-pinned)

Base: `Mikeyphw/studyflow` PR #15 at `714cf25a0253225774fa0844c732bfd77c871b5e`.

## Actual failures

- SFDT22 `42428461332` and SFDT21 `42428461225` compile the shared Android instrumentation APK. `Sfdt22HostedStudyJourneyTest.kt` references Compose collection extension methods `onFirst` and `onLast` without imports. `:app:compileDebugAndroidTestKotlin` fails at lines 95, 107, 118, 133. Add both imports; keep all five tests and the real-UI gate.
- Native Devtool phone `42428461294` completed `:app:packageDebug` and wrote lint reports, then stalled after `:app:lintDebug`. The existing SFDT18 guard correctly refused after 10 minutes of no substantive output. The older main failure `42424780036` failed while packaging and may have a different cause.

## Mitigation and remaining uncertainty

The phone runner now writes `org.gradle.parallel=false`, `org.gradle.workers.max=2`, and `org.gradle.configuration-cache=false` to its **hosted Gradle user home** before the existing native Devtool job. This matches the proven SFDT21 emulator's Gradle execution envelope, not its task list. No Gradle tasks are skipped, no retry masks failures, no timeouts extended, no release signing altered, and the authoritative native Devtool workflow and downstream provenance gate stay enabled. This is a plausible mitigation for the silent Gradle stall and must be verified by a new hosted run; it is **not** proof of root cause or success.

The source contract adds negative tests for environment, symlinks, conflicting properties and no repo modification. Failed compilation/runtime requires another hotfix, never a fake PASS.

## Required evidence before merge

All current-head SFDT22 real emulator, SFDT21 regression, native Devtool phone + gate, source and basic StudyFlow CI must pass. Verify the same commit across all jobs. SFDT22 requires five tests, seven screenshots, app restart and actual state persistence; neither source-only verification nor APK assembly count as completion.
