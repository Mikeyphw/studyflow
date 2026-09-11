# REL-FINAL Devtool Android release-runner hotfix

This hotfix rebases the release campaign onto the repository state where the standalone TV Android-runner hotfix is already installed. Devtool is now the single Android build entry point for both Android TV and the real REL-FINAL signed release campaign.

## Fixed

- Preserves `studyflow_tv` as `type = "android"` with Termux AAPT2, NDK 29, and its standalone regression guard.
- Adds `studyflow_release`, an Android/release target whose build lane runs `verifyRelFinalDevtoolReleaseRunner` and `verifyFinalReleaseAutomatedGate`.
- Delegates release Gradle execution to Devtool rather than `./gradlew` or raw `/usr/bin/gradle`.
- Disables configuration cache for the custom release-evidence graph.
- Excludes `verifyFormatting` from the release-evidence invocation because REL-FINAL v2 explicitly classified repository-wide historical formatting debt as outside the release-readiness promise; all other transitive release gates remain intact.
- Makes a failed automated release gate immediately `BLOCKED` and terminates the execute campaign.
- Registers both post-REL-FINAL verifier files separately from the immutable historical 213-verifier audit set.
- Finishes manual device acceptance through the Python verdict runner instead of raw Gradle.

## Invariants

Signing credentials remain external environment variables. The hotfix does not embed keys, upload artifacts, publish releases, synthesize device evidence, or weaken the signed artifact, connected-test, restore/restart, device-observation, or operator-checklist requirements.
