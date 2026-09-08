# StudyFlow Android TV roadmap

StudyFlow TV is a separate, small APK target that reuses the existing StudyFlow core instead of
forking the product. The TV work is intentionally additive: phone/tablet behavior remains owned by
`:app`, while the future `:app-tv` module will own only the Android TV launcher, manifest, TV shell,
TV navigation, TV-specific presentation wiring, and the receiving side of the phone-to-TV control
surface.

## Product decision

Use a small TV-only APK with shared business logic.

- Keep one source repository.
- Keep one Room/domain/navigation model set.
- Keep phone/tablet in `:app`.
- Add `:app-tv` only when TVOV01 creates the minimal application module.
- Keep TV UI dependencies out of shared modules unless a later verifier explicitly allows them.
- Do not duplicate repositories, sync engines, study-plan models, or portability formats.
- The Android mobile app must be able to control the TV app end-to-end after pairing.
- The Android mobile app must be able to cast a study session, question set, review, mock exam,
  progress screen, or schedule view to the TV app.

## Phone-to-TV control and cast boundary

TV support is not just a second launcher. It is a companion-display target controlled by either the
TV remote or the Android mobile app.

Contracts for the future implementation:

- Pairing is explicit and user-approved, using a local code/QR/deep-link handoff.
- A paired phone can navigate TV destinations, start/pause/resume sessions, select answers, reveal
  explanations, finish activities, launch mocks, open progress, and return home.
- Cast mode sends StudyFlow intents and presentation state, not raw database rows or arbitrary UI
  trees.
- TV-side commands route through the same domain/use-case layer as local TV remote actions.
- The phone controller must not force TV-only dependencies into the phone APK unless explicitly
  measured and accepted by a later size-budget overlay.
- Control sessions expire, can be revoked from either device, and never bypass local-first data
  ownership.

## Size budget

These budgets are contract values for the eventual TV release artifact:

```text
TV_RELEASE_APK_TARGET_MIB=25
TV_RELEASE_APK_HARD_CEILING_MIB=35
```

The debug APK is informational only. Release APK size is the enforced product budget once `:app-tv`
exists. Until then, `studyflow_tv` is a source-contract Devtool target that prevents accidental TV
bloat and phone-only dependency coupling before the module is introduced.

## Overlay sequence

```text
TVOV00  Architecture boundary, size budget, Devtool target, and control/cast contract
TVOV01  Minimal app-tv application module [implemented]
TVOV02  TV dependency budget and lint-clean app module [implemented]
TVOV03  Phone-to-TV pairing, protocol, and cast foundation [implemented]
TVOV04  D-pad and focus primitives [implemented]
TVOV05  TV navigation shell
TVOV06  TV Home / dashboard
TVOV07  Today workspace for TV
TVOV08  Study session experience
TVOV09  Question practice
TVOV10  Mock Exam Studio
TVOV11  Review and error notebook
TVOV12  Progress and analytics
TVOV13  Read-only schedule
TVOV14  Limited library/reference material
TVOV15  Minimal TV settings
TVOV16  Cross-device control/state continuity
TVOV17  Accessibility and 10-foot UI seal
TVOV18  Size optimization, R8, and resource shrinking
TVOV19  Android TV manifest, packaging, and Devtool TV target hardening
TVOV20  Real-device acceptance and release seal
```

## Stopping gate

Do not split business logic. The TV app is allowed to be a separate APK only because size matters;
it is not allowed to become a separate StudyFlow implementation.

Do not ship TV as a passive viewer. The final TV track must support both D-pad use and complete
paired-phone control/cast flows.


## TVOV01 implementation note

TVOV01 creates the first real `:app-tv` Android application module as a minimal TV receiver shell.
It intentionally uses the Android framework view toolkit instead of Compose TV, AndroidX TV UI,
MediaRouter, or Cast SDK dependencies. This keeps the first TV APK measurable and small while the
roadmap still requires complete paired-phone control and cast support in later overlays.

The `studyflow_tv` Devtool target now validates the real module with `:app-tv:compileDebugKotlin`,
`:app-tv:testDebugUnitTest`, `:app-tv:assembleDebug`, and `:app-tv:lintDebug` in addition to the TV
architecture, size-budget, and control/cast source contracts.


## TVOV02 implementation note

TVOV02 keeps the newly created `:app-tv` module small and lint-clean before any TV UI stack is added. No Cast SDK before TVOV04. No AndroidX TV UI before TVOV03. No phone feature-module fanout, and no fixed-orientation manifest assumptions. The mobile-control and cast contract remains architectural until pairing/control protocol overlays implement it.


## TVOV03 implementation note

TVOV03 adds the shared phone-to-TV control protocol in `:core:tvcontrol`. The mobile app depends on `:core:tvcontrol`, and `:app-tv` depends on the same module, so later pairing/control work cannot drift into two incompatible schemas. The protocol covers revocable pairing, controllable TV surfaces, castable presentation surfaces, remote commands, and cast state. The transport remains deferred: no Cast SDK, MediaRouter, network discovery, or raw screen mirroring is added in TVOV03.


## TVOV04 implementation note

TVOV04 adds pure Kotlin/native Android D-pad and focus primitives to `:app-tv` without introducing Compose Runtime, AndroidX TV UI, MediaRouter, Cast SDK, phone feature-module fanout, `core:data`, or `core:database`. It provides deterministic initial focus, non-wrapping directional movement, Android remote key-code mapping, visible focus drawable state, and a tiny focusable action row in the placeholder TV shell. No Compose Runtime in TVOV04; future TV UI overlays can build on these primitives only after their size impact is measured.
