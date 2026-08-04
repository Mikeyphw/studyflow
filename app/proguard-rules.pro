# Phase 36 release-candidate packaging decision.
# Shrinking remains disabled in app/build.gradle.kts for the 1.0.0-rc1 internal release candidate
# so target-device debugging, stack traces, and diagnostic support bundles stay readable.
# Re-enable minification only after a separate shrinking/R8 verification pass with explicit keep rules.
