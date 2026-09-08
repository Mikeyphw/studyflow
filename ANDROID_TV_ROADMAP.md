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
TVOV23  Android phone TV controller [next]
TVOV24  Real cast/state synchronization [planned]
TVOV25  Domain truth and persistence convergence [planned]
TVOV26  End-to-end physical-device final seal [planned]
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


## TVOV05 implementation note

TVOV05 turns the TV placeholder into a structured native navigation shell. The top-level TV
destinations are now represented by a small enum, rendered as a D-pad-aware navigation rail,
and mapped to the shared `:core:tvcontrol` surface model so paired-phone control and local TV
navigation do not drift. TVOV05 still avoids Compose Runtime, AndroidX TV UI, MediaRouter,
Cast SDK, phone feature-module fanout, `core:data`, and `core:database`.


## TVOV06 — Home surface

Status: implemented in `studyflow_tvov06_home_surface_warning_cleanup_v1.zip`.

Scope:
- default TV Home destination backed by a structured source model;
- remote-first Home cards for Today, Practice, Reviews, and Mock Exam;
- visible paired-phone entry point routed through the TV control protocol;
- TVOV05 unused-string lint warning cleanup;
- no Compose, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, or `core:database` dependency is introduced.

Validation:
- `verifyStudyFlowTvHomeSurface`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:assembleDebug`;
- `:app-tv:lintDebug`.

## TVOV07 — Today workspace

Status: implemented in `studyflow_tvov07_08_today_session_v1.zip`.

Scope:
- structured Today snapshot with exam label, date label, next study block, review queue, practice queue, weekly progress, and mock-exam scheduling context;
- TV-rendered Today cards for next block, due reviews, question practice, and schedule/mock-prep context;
- D-pad focus items for Today cards;
- paired-phone commands for every Today action through the shared `:core:tvcontrol` protocol;
- Today actions route to concrete TV destinations, including the TVOV08 Study Session surface.

Validation:
- `verifyStudyFlowTvTodayWorkspace`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.

## TVOV08 — Study session experience

Status: implemented in `studyflow_tvov07_08_today_session_v1.zip`.

Scope:
- structured Study Session destination opened from Today or paired-phone control;
- TV session state machine for ready, running, paused, and completed states;
- remote and paired-phone actions for start, pause, resume, next segment, complete, and return to Today;
- visible session plan segments for warm-up, focus block, checkpoint, and summary;
- Play/Pause remote-key handling while the Study Session surface is active;
- cast-state emission for the Study Session surface using the shared protocol rather than mirroring UI or sending database rows.

Validation:
- `verifyStudyFlowTvStudySessionExperience`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.



## TVOV09 — Question practice

TVOV09 implements a real Android TV question-practice surface. It contains a question set model,
answer choices, answer selection, explanation reveal, previous/next navigation, completion, D-pad
focus items, paired-phone command generation, and cast-state generation. The TV app renders the
surface in the native TV shell and keeps the dependency budget intact.

## TVOV10 — Mock Exam Studio

TVOV10 implements a real Android TV Mock Exam Studio. It contains timed sections, running/paused/
submitted/results states, section advancement, mark-for-review, submit, result review, D-pad focus
items, paired-phone command generation, and cast-state generation. It does not import phone feature
modules, Room repositories, Compose, AndroidX TV UI, Cast SDK, or MediaRouter.


## TVOV11 — Review and error notebook

Status: implemented in `studyflow_tvov11_12_review_progress_v1.zip`.

Scope:
- structured review queue with due items, confidence, reasons, active item, known markers, and error-note markers;
- state machine for ready, reviewing, explanation, error-notebook, and completed states;
- TV-rendered review cards and actions for start, next, known, error note, explanation, complete, and Today;
- D-pad focus items for cards and actions;
- paired-phone command generation and cast-state output through the shared `:core:tvcontrol` protocol;
- no Compose, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module dependency is introduced.

Validation:
- `verifyStudyFlowTvReviewNotebook`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.

## TVOV12 — Progress and analytics

Status: implemented in `studyflow_tvov11_12_review_progress_v1.zip`.

Scope:
- structured progress snapshot with weekly progress, accuracy, question volume, readiness, and weak-subject summaries;
- TV-rendered metric cards, weak-subject cards, and recommended next actions;
- D-pad focus items for metrics, subjects, and actions;
- paired-phone command generation through shared TV navigation/control protocol;
- cast-state output for the `PROGRESS` surface;
- no Compose, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module dependency is introduced.

Validation:
- `verifyStudyFlowTvProgressAnalytics`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.


## TVOV13 — Read-only schedule

Status: implemented in `studyflow_tvov13_14_schedule_library_v2.zip`.

Scope:
- structured read-only TV schedule model with today/week context;
- visible schedule items for review windows, study blocks, practice, mock-exam rehearsal, and recovery buffer;
- D-pad focus items for schedule cards and actions;
- paired-phone schedule navigation through the shared TV control protocol;
- cast-state output for the Schedule surface without mirroring UI or exposing database rows;
- no TV-side editing, import, deletion, rescheduling, SAF file picking, `core:data`, or `core:database` dependency.

Validation:
- `verifyStudyFlowTvReadOnlySchedule`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.

## TVOV14 — Limited library/reference material

Status: implemented in `studyflow_tvov13_14_schedule_library_v2.zip`.

Scope:
- structured limited Library/reference surface with curated, read-only reference cards;
- short TV-readable excerpts for legislation, summaries, error-notebook excerpts, and checklists;
- D-pad focus items for references and actions;
- paired-phone reference opening through the shared TV control protocol;
- cast-state output for the Library surface without raw file paths, SAF handles, repositories, or database rows;
- no TV-side library management, import/export, file deletion, note editing, `core:data`, or `core:database` dependency.

Validation:
- `verifyStudyFlowTvLibraryReference`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug`.


## Roadmap and working-rules carry-forward

The TVOV00-TVOV22 implementation chain, corrective roadmap, and the user's overlay preferences are preserved in
`TV_ANDROID_TV_FULL_ROADMAP_AND_WORKING_RULES.md`. Future TV overlays must remain structured
implementation units, never light placeholder patches, and must keep using the `studyflow_tv` target
with explicit Devtool overlay commit metadata.


## TVOV15 — Minimal TV settings

Status: implemented in `studyflow_tvov15_16_settings_control_continuity_v2.zip`.

TVOV15 replaces the static Settings placeholder with `StudyFlowTvSettingsSurface`: pairing status
and revocation, focus-visibility preference representation, a local-first privacy reminder, and a
TV-safe About/diagnostics view. Settings remains presentation-only with no account, storage, SAF,
repository, Room, or phone-feature dependencies.

## TVOV16 — Cross-device control/state continuity

Status: implemented in `studyflow_tvov15_16_settings_control_continuity_v2.zip`.

TVOV16 adds shared session/continuity schemas in `:core:tvcontrol` and a deterministic TV-side
`StudyFlowTvControlStateReducer`. Local remote and paired-phone commands converge on one TV state,
cover all ten TV destinations, expose a monotonic revision and active presentation identifiers, and
fail closed for duplicate, stale, expired, or revoked paired commands. Real transport remains deferred.


## TVOV17 — Accessibility and 10-foot UI seal

Status: implemented in `studyflow_tvov17_18_accessibility_size_release_optimization_v2.zip`.

Scope:
- centralized pure Kotlin 10-foot typography, layout, contrast, and focus policy;
- native semantic headings, polite live continuity status, and accessibility-important controls;
- focus-following `ScrollView` content so dense TV surfaces remain reachable on shorter viewports;
- actual rendering support for the TVOV15 high-visibility focus setting using a 5dp yellow focus ring;
- no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database` added.

Validation:
- `verifyStudyFlowTvAccessibilityTenFootUi`;
- `:app-tv:testDebugUnitTest`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:lintDebug` and `:app-tv:lintRelease`.

## TVOV18 — Size optimization, R8, and resource shrinking

Status: implemented in `studyflow_tvov17_18_accessibility_size_release_optimization_v2.zip`.

Scope:
- R8 minification and resource shrinking remain mandatory for Release and now use Android's optimized default ProGuard configuration;
- TV release budget is modeled independently in pure Kotlin at a 25 MiB target / 35 MiB hard ceiling;
- optimized Release validation requires `:app-tv:assembleRelease`, R8 `mapping.txt`, APK ZIP integrity, and a Release APK at or below 25 MiB;
- packaging omits dependency metadata and intentionally avoids broad keep rules so unused TV code/resources remain shrinkable;
- signing, launcher/package hardening, install/upgrade, and real-device acceptance remain TVOV19/TVOV20.

Validation:
- `verifyStudyFlowTvSizeOptimization`;
- `verifyStudyFlowTvOptimizedReleaseArtifact` (depends on `:app-tv:assembleRelease`);
- `:app-tv:testDebugUnitTest`;
- `:app-tv:lintRelease`.

## TVOV19 — Android TV manifest, packaging, and Devtool TV target hardening

Status: implemented in `studyflow_tvov19_20_manifest_packaging_real_device_release_seal_v2.zip`.

Scope:
- stable TV package identity `com.mikeyphw.studyflow.tv`, current version code 22 / `0.1.0-tv22`;
- required Leanback + optional touchscreen launcher contract, Leanback-only exported activity, TV banner/icon resources, productivity app category, and cleartext framework traffic disabled;
- cloud backup and device-transfer exclusions for all TV-local storage domains;
- conditional production signing using the complete `STUDYFLOW_RELEASE_*` credential set and fail-fast rejection of partial signing configuration;
- `studyflow_tv` Devtool target isolation from phone `:app`, feature modules, `core:data`, `core:database`, and repository-wide formatting;
- built Debug/Release APK package inspection.

Validation:
- `verifyStudyFlowTvManifestPackaging`;
- `verifyStudyFlowTvTargetIsolation`;
- `verifyStudyFlowTvPackagedArtifacts`;
- `:app-tv:assembleDebug` and `:app-tv:assembleRelease`.

## TVOV20 — Real-device acceptance and release seal

Status: implemented in `studyflow_tvov19_20_manifest_packaging_real_device_release_seal_v2.zip`.

Scope:
- pure Kotlin final acceptance policy distinguishing clean no-device skip from real-device pass/fail;
- final Release gate requiring optimized Release output, R8 mapping, package/Leanback identity, unit tests, Debug/Release lint, and the 25 MiB target;
- optional connected-TV acceptance: Leanback feature check, install/upgrade, activity launch, D-pad smoke input, process-survival check, and installed package/version verification;
- no connected device or unusable ADB is a clean skip and never counts as physical-device acceptance;
- production signing is verified when signing credentials are present;
- TVOV20 originally sealed packaging while wire transport was still deferred; TVOV22 now supplies the explicit local pairing/discovery/transport layer without changing TVOV20's package/device acceptance scope.

Validation:
- `verifyStudyFlowTvFinalAcceptanceContracts`;
- `verifyStudyFlowTvRealDeviceAcceptance`;
- `verifyStudyFlowTvFinalReleaseSeal`;
- cumulative TVOV00-TVOV20 verifiers and the existing optimized Release gate.

## TVOV21 — Typed presentation snapshots and canonical data projection

Status: implemented in `studyflow_tvov21_typed_presentation_projection_v2.zip`.

Scope:
- serializable typed presentation snapshots in `:core:tvcontrol` for Today, session, questions, mock, review, progress, schedule, and library;
- production phone/data-owner projection from the real StudyFlow repositories and canonical domain models;
- TV-side presentation adapter/inbox with duplicate/stale snapshot rejection and canonical source-ID retention;
- the TV Activity starts with a truthful waiting state and no longer uses historical demo fixture defaults as runtime data;
- `app-tv` remains isolated from Room, `core:data`, `core:database`, phone feature modules, Cast SDK, MediaRouter, and networking;
- Home `PAIR_PHONE` protocol routing is corrected to the Settings pairing surface;
- TVOV21 intentionally left wire pairing/discovery/transport for TVOV22; the following overlay now supplies that boundary.

Validation:
- `verifyStudyFlowTvPresentationProjection`;
- `:core:tvcontrol:test`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:testDebugUnitTest`;
- cumulative TVOV00-TVOV21 source verifiers and existing package/release gates.

## TVOV22 — Local pairing, discovery, and transport

Status: implemented in `studyflow_tvov22_local_pairing_discovery_transport_v1.zip`.

Scope:
- shared Android `:core:tvlink` module using framework NSD discovery and a bounded local TCP transport, with no Cast SDK or MediaRouter;
- explicit 120-second six-digit TV pairing code plus random nonce and a ten-attempt cap; discovery never advertises the pairing code;
- random 24-hour session id/secret credentials persisted in app-private non-backed-up storage and revocable from either device;
- authenticated command, typed presentation, continuity, revoke, and ping operations over strict length-prefixed JSON frames;
- TV receiver host active only while the TV Activity is started; remote commands use the TVOV16 reducer and presentations use the TVOV21 inbox;
- production phone discovery/client objects are installed in the app container, while the user-facing controller remains TVOV23 work;
- TV package version code 22 / `0.1.0-tv22`.

Validation:
- `verifyStudyFlowTvLocalPairingTransport`;
- `:core:tvcontrol:test`;
- `:core:tvlink:compileDebugKotlin`;
- `:core:tvlink:testDebugUnitTest`;
- `:core:tvlink:lintDebug`;
- `:app-tv:compileDebugKotlin`;
- `:app-tv:testDebugUnitTest`;
- cumulative TVOV00-TVOV22 source verifiers and existing package/release gates.

