# StudyFlow Android TV full roadmap and working rules

This document is carried inside the Android TV overlay chain so future work does not depend on chat
history. It records the full roadmap, the Devtool target rules, and the implementation preferences
that every following TV overlay must preserve.

## Full Android TV roadmap

```text
TVOV00  Architecture boundary, size budget, Devtool target, and control/cast contract [implemented]
TVOV01  Minimal app-tv application module [implemented]
TVOV02  TV dependency budget and lint-clean app module [implemented]
TVOV03  Phone-to-TV pairing, protocol, and cast foundation [implemented]
TVOV04  D-pad and focus primitives [implemented]
TVOV05  TV navigation shell [implemented]
TVOV06  TV Home / dashboard [implemented]
TVOV07  Today workspace for TV [implemented]
TVOV08  Study session experience [implemented]
TVOV09  Question practice [implemented]
TVOV10  Mock Exam Studio [implemented]
TVOV11  Review and error notebook [implemented]
TVOV12  Progress and analytics [implemented]
TVOV13  Read-only schedule [implemented]
TVOV14  Limited library/reference material [implemented]
TVOV15  Minimal TV settings [implemented]
TVOV16  Cross-device control/state continuity [implemented]
TVOV17  Accessibility and 10-foot UI seal [implemented]
TVOV18  Size optimization, R8, and resource shrinking [implemented]
TVOV19  Android TV manifest, packaging, and Devtool TV target hardening [implemented]
TVOV20  Real-device acceptance and release seal [implemented]
TVOV21  Typed presentation snapshots and canonical data projection [implemented]
TVOV22  Local pairing, discovery, and transport [implemented]
TVOV23  Android phone TV controller [implemented]
TVOV24  Real cast/state synchronization [implemented]
TVOV25  Domain truth and persistence convergence [next]
TVOV26  End-to-end physical-device final seal [planned]
```

## Instructions and preferences

- No light placeholder overlays. Every TV overlay must be a structured implementation unit with
  product source, TV shell integration, paired-phone/control integration where relevant, cast-state
  integration where relevant, unit tests, verifier scripts, Gradle task wiring, Devtool target wiring,
  documentation, and manifest/source guards.
- When two roadmap overlays are requested as one, keep one overlay artifact and one commit, but keep
  the two roadmap items independently provable with separate model classes, tests, docs, and verifier
  tasks.
- Always target `studyflow_tv` in overlay metadata and apply commands after TVOV00. Do not fall back
  to the normal Android phone target for TV work.
- Devtool commands should use `devtool --copy apply overlay ...`; artifact metadata must include
  `apply.commit.enabled=true`, `strategy=single`, and the intended commit message.
- Fix real compile, lint, and verifier failures in the next overlay when they are introduced by the
  previous TV overlay. Environmental ADB probe warnings are not product-code failures.
- Do not claim a roadmap item is implemented unless the overlay has source code and validation that
  prove it. Documentation alone is not enough.
- Keep the TV app a separate small APK in the same repository, not a fork. Shared schemas can live in
  `:core:tvcontrol`; phone/tablet remains in `:app`; the TV receiver stays in `:app-tv`.
- Preserve the TV dependency boundary unless a later explicit size overlay changes it: no Compose
  Runtime in `:app-tv`, no AndroidX TV UI, no Cast SDK, no MediaRouter, no `core:data`, no
  `core:database`, and no phone feature-module fanout.
- The Android mobile app must eventually control the TV app end-to-end after explicit, revocable
  pairing. Control commands should be StudyFlow intents/presentation state, not raw database rows or
  arbitrary UI-tree mirroring.
- Read-only Schedule and limited Library/reference material must stay presentation surfaces on TV:
  no schedule mutation, file import/export, SAF picker, repository access, or Room access from
  `:app-tv`.
- Termux Devtool validation is authoritative for Android compile, test, assemble, and lint. Local
  container checks can prove source contracts and Kotlin syntax but cannot replace the user's Gradle
  run on the Android build host.

## Current chain status

As of TVOV24, the structured implementation chain covers TVOV00 through TVOV24. TVOV21 replaced runtime demo fixtures with canonical typed presentation snapshots; TVOV22 added explicit local discovery/pairing/authenticated transport; TVOV23 exposed that link through a real phone controller; and TVOV24 now streams the canonical phone-owned presentation feed to the TV with exact receipt acknowledgement, reconnect resync, explicit STOP_CAST, and pending-stop recovery. The original stopping gate is still intentionally open: TVOV25 is next and must converge TV/remote interactions onto the real phone-owned StudyFlow use cases and persistence before TVOV26 can perform the true two-device final seal.
