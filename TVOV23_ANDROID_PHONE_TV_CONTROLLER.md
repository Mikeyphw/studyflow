# TVOV23 — Android phone TV controller

TVOV23 consumes the production TVOV22 local link from the normal Android `:app` and makes paired-TV control reachable to users without weakening the small `:app-tv` dependency boundary.

## Delivered

- Settings now exposes a **TV controller** entry without importing TV-link types into `feature:settings`.
- The app-owned controller screen starts/stops foreground NSD discovery and releases its discovery listener when closed and shows discovered StudyFlow TV receivers.
- Users explicitly select a receiver, verify that its pairing window is active, and enter the six-digit code shown on the TV.
- The phone resumes a persisted unexpired TVOV22 credential after process restart and automatically reselects the matching rediscovered TV when possible.
- The controller can refresh continuity state and open all ten TV destinations: Home, Today, Study Session, Questions, Review, Mock Exam, Progress, Schedule, Library, and TV Settings.
- Study Session controls expose start/pause/resume/previous/next/complete.
- Question controls expose previous/next, answer A-D, explanation, and mark-for-review.
- Mock controls expose start/pause/resume/previous/next/submit/review-results.
- Review controls expose previous/next/complete, and a general TV Back command is available.
- Pairing can be revoked remotely or forgotten locally. Authorization failures clear stale local credentials.
- The controller uses `AppContainer.tvReceiverDiscovery` and `tvLinkClient`; it does not introduce Cast SDK, MediaRouter, a second socket stack, or background discovery.

## Deliberate boundary

TVOV23 is a real remote controller, not the final cast pipeline. It does not continuously push the TVOV21 typed presentation snapshot. Live presentation/state synchronization remains TVOV24 so one later overlay can own resync, STOP_CAST, and presentation revision semantics coherently.

The `studyflow_tv` Devtool target remains TV-APK isolated and does not add direct `:app:*` Gradle tasks to its normal build/test/lint lists. TVOV23 is proven there by source contracts; the final two-device seal will explicitly validate both Android targets.
