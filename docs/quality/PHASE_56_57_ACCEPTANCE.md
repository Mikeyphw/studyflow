# Phase 56-57 acceptance checklist

- `PostV2IntegrationSealTest` verifies that every V2 integration area is required.
- `DeviceReadinessPolishSeal` verifies that release polish cannot skip warning or device gates.
- `scripts/verify_phase_56_57.py` checks navigation, practice, progress, reminder,
  widget, resource, and warning-boundary source contracts.
- Devtool must run at least `:core:domain:test`, `:feature:reminders:testDebugUnitTest`,
  `:feature:workspace:testDebugUnitTest`, `:verifyPhase56_57ReleaseReadiness`,
  `:app:assembleDebug`, `:app:lintDebug`, and `:app:lintRelease`.
- Real-device sign-off remains required for TalkBack, font scaling, widget taps,
  reminder permissions, edge-to-edge, predictive back, and upgrade behavior.

- R2 closes the release-mode wording gap by adding a release-facing source gate and release lint.
