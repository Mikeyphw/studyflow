### ATT07 — Cross-Device Delivery Router

- Added shared Kotlin and Web attention delivery routers with single-owner routing, bounded mirrors, explicit-target routing, foreground-only TV truth, ledger-state suppression, and canonical move-to-device handoff requests.
- Added Android, Desktop, and Android TV router capability bridges so each surface reports real delivery capability without mutating SFLP protocol-v1 golden capability lists.
- Added Web Notification Center route-summary diagnostics, runtime/dist parity, tests, docs, and the focused `verifyAtt07CrossDeviceDeliveryRouter` gate.


### ATT06 — TV attention experience
- Added Android TV foreground attention runtime, D-pad banners, Web TV attention projection, capability truth, diagnostics, docs and focused validation.
## ATT04 — Desktop Native Attention + Tray Product

- Added the production Desktop reminder scheduler with startup/restart recovery and 30-second reconciliation while StudyFlow is running.
- Added durable Desktop attention preferences and shared ATT01 ledger lifecycle integration.
- Upgraded native Desktop notifications to capability-aware identified delivery with tray click/action routing and a live mini-dashboard.
- Added snooze/dismiss, Start next, Review due, pause/resume, manual reconcile, test notification, settings health evidence, tests, docs, and Devtool validation wiring.

# Changelog

## ATT03 — Android Widget Suite 2.0

- Upgraded the existing Android widget in place to five configurable modes: Today, Next, Reviews, Session, and Compact.
- Added per-widget plan/mode persistence, Android reconfiguration, ID-restore migration, plan-unavailable recovery, responsive compact rendering and launcher preview metadata.
- Added real Start-next and Pause/Resume widget mutations plus precise review/schedule/session deep links.
- Added a notification-permission-independent inexact freshness alarm covering session minute ticks, planned blocks, review boundaries, session targets and day rollover, including system time/boot/package recovery.
- Expanded widget Debug Workbench evidence and added unit/instrumentation/source-contract tests plus the focused `verifyAtt03AndroidWidgetSuite` gate.


## ATT02 — Android Attention System 2.0

- Added independent Android reminder categories and configurable timing with an 08:00 daily-summary default.
- Added study/review/session/summary channels, actionable notifications, session/exam reminders, exact-target deep links and break completion.
- Added versioned per-plan policy migration, richer scheduled-occurrence persistence, ATT01 ledger lifecycle integration and permission-safe recovery.
- Added app-resume and live domain reconciliation plus expanded Reliability Center / Debug Workbench evidence.
- Hardened the ATT01 Web notification/media runtime regression contract so the durable attention-ledger schema marker survives TypeScript runtime generation.


## REL-FINAL Devtool Android release-runner hotfix

- Added a dedicated `studyflow_release` Android Devtool target for the real signed release evidence campaign.
- Routed final release acceptance through Devtool instead of raw Gradle so Termux AAPT2/NDK handling matches normal Android builds.
- Disabled Gradle configuration cache for the custom release-evidence graph and excluded only the unrelated repository-wide `verifyFormatting` backlog from this lane.
- Made REL-FINAL execute fail immediately as `BLOCKED` when the automated Devtool gate fails.
- Preserved the already-applied TV Android-runner hotfix and registered both post-REL-FINAL regression verifiers without changing the historical 213-verifier audit boundary.

## TV Devtool Android runner hotfix

- Route `studyflow_tv` through Devtool's Android runner instead of the generic raw-Gradle runner.
- Pin the TV target to debug, Termux AAPT2, native-Termux preference, and NDK 29.
- Add a regression verifier that preserves TV-only build tasks and Android-runner routing.

# PARITY04 — Five-client full-mesh/product-parity acceptance seal

- Added a language-neutral five-client scenario corpus covering all ten direct client pairs.
- Added real loopback five-peer JVM SFLP acceptance for convergence, direct TV pairing, offline conflicts, and exactly-once completion.
- Reused the 40 WEB20 semantic vectors verbatim and added Desktop/Web acceptance consumers.
- Sealed TV capability rejection, presentation, route-loop prevention, Presence coexistence, and canonical portability checks.
- Marked PARITY04 complete; REL-FINAL evidence execution is now the sole remaining canonical roadmap track.

# PRESENCE01 — Peer/activity presence
- Hardened the browser-TV relay acceptance harness with load-tolerant LAN relay readiness retries and diagnostic capture.

- Added ephemeral online/away/offline/stale presence with bounded expiry and reconnect session supersession.
- Added simultaneous-instance, resumable activity hint, capability visibility, authenticated exchange, and loop-safe gossip semantics.
- Wired Android, Desktop, Web, Android TV, and Web TV without making presence durable or a sync prerequisite.
- Closed PRESENCE01 in the canonical roadmap; PARITY04 is now the next cross-platform acceptance batch.

# WEB20 — Shared semantic golden vectors

- Added 40 language-neutral semantic vectors consumed by Kotlin/core, TypeScript/Web, and Desktop.
- Covered readiness, scheduling/rescheduling, scoring, SRS, pause-aware study time, goal progress, Error Notebook state, canonical portable serialization, and deterministic SFLP conflict resolution.
- Added a production-backed Desktop semantic adapter, canonical JSON helper, cross-runtime verifier, and roadmap ledger closure.
- Made WEB20 the canonical semantic fixture set that PARITY04 must reuse; PRESENCE01 remains next.

# REL01 — Target release validation runner

- Added a post-SFLP target-machine release validation runner for the existing signed APK/AAB,
  connected Android, restore/restart, and manual device-evidence gates.
- The runner writes plan/summary artifacts with BLOCKED or PENDING status for missing evidence; it
  never synthesizes a passing release verdict.
- Added a static verifier, Gradle task, docs, and Devtool test-lane wiring without creating a Phase 65
  or extending the SFLP protocol past SFLP24.

# SFLP08 + SFLP09 — Android and Desktop generic peer integration

- Bound Android to the generic SFLP peer runtime with private persistent identity/trust/journal/queue/endpoints,
  recoverable app startup, NSD + LAN/manual discovery, QR pairing, authenticated reconciliation, and revoke/forget.
- Bound Desktop to the same JVM-safe SFLP runtime with atomic filesystem persistence, direct LAN/manual discovery,
  background reconnect for registered workspaces, and Settings UI for pairing, peer trust, sync, revoke, and forget.
- Added real TCP Android-kind ↔ Desktop-kind pairing/reconnect/bidirectional-convergence tests, revoked-peer rejection,
  platform persistence/source contracts, and language-neutral peer-RPC schema/golden vectors.
- Kept legacy TVOV phone/TV connectivity as a compatibility lane only; SFLP11 remains responsible for native-TV
  generalization, and Web/Web-TV remain SFLP10/SFLP12 work.

# SFLP06 + SFLP07 — multi-peer topology and offline reconnection

- Added a platform-neutral workspace topology with simultaneous peers, health tracking, deterministic direct/learned/
  relay route selection, source-preserving route traces, hop limits, bounded message dedupe, and loop-safe broadcast.
- Added a persistent idempotent per-peer outgoing queue with bounded exponential retry scheduling and frontier-based
  acknowledgements; pending operations are never discarded merely because retries keep failing.
- Added bidirectional reconnect coordination that catches up stale peers from journal truth even with an empty/lost
  queue and recovers safely when a remote batch committed but its response was lost.
- Added language-neutral topology/offline schema + golden vectors and focused JVM/source validation. Client-specific
  Android/Desktop/Web/TV bindings remain correctly deferred to SFLP08-SFLP12.

# SFLP04 + SFLP05 — device trust and transport abstraction

- Added persistent device-identity/trust ports with stable device IDs, remembered peers, revocation, descriptor
  refresh, six-digit and QR pairing, 120-second offers, ten-attempt lockout, random shared secrets, and one-time
  HMAC reconnect proofs.
- Added platform-neutral transport/discovery APIs, canonical endpoint parsing, 1 MiB length-prefixed SFLP frames,
  a real native LAN TCP implementation, adapter-backed WebSocket/relay transports, manual discovery, and the
  `_studyflow._tcp.` mDNS/NSD record contract.
- Added language-neutral trust/transport schema + golden fixtures and focused JVM/source validation. Platform
  storage/UI/network bindings remain the correctly-scoped SFLP06-SFLP12 integration work.

# SFLP02 + SFLP03 — canonical operation journal and reconciliation

- Added the append-only canonical domain operation journal with origin sequences, entity revisions, logical
  timestamps, deterministic replay, duplicate integrity checks, vector frontiers, checkpoints, and tombstones.
- Added transport-neutral reconciliation with typed frontier exchange, bounded missing-operation batches,
  exact batch continuation/integrity checks, retry-safe dedupe, interruption recovery, and conflict handlers.
- Added language-neutral sync schema/golden vectors plus focused JVM/source validation while keeping live
  device trust, discovery, transport, and client adapters explicitly deferred to SFLP04-SFLP12.

# SFLP01 — platform-neutral StudyFlow Link Protocol baseline

- Added pure JVM `:core:link` protocol identities, version negotiation, peer descriptors, capabilities,
  message envelopes, structured errors, and JSON codec behavior.
- Added language-neutral SFLP v1 JSON Schema plus request/response/event/error golden vectors for later
  Kotlin/TypeScript/Desktop/TV interoperability work.
- Added focused JVM and source-contract validation without claiming discovery, trust, transport, or sync.

# PARITY01-PARITY03 rebase refresh

- Rebased the cross-platform parity baseline onto the current post-TVOV26/Web-TV repository state.
- Reworded the historical Android RC1 exclusion so it cannot be mistaken for current product scope.
- Kept the canonical capability matrix, full-mesh SFLP target, and OV20/TVOV source seals intact.

# Cross-platform parity baseline — PARITY01-PARITY03

- Added the canonical Android/Web/Desktop/Android-TV/Web-TV capability matrix.
- Added the full parity + SFLP connectivity roadmap, including full-mesh peer connectivity as a release goal.
- Reconciled stale scope/release/README roadmap statements with shipped Web, Desktop preview, TV and optional-sync tracks.
- Restored OV20 source-quality validation after later TVOV build/link additions exceeded the existing 120-character Kotlin/KTS policy.

## SFLP12/SFLP13 - Web TV connectivity and activity handoff

- Added Web TV receiver offers, QR/code payloads, projection snapshot acceptance and remote input gating.
- Added typed activity handoff snapshots, offers, accepts, resume events, one-time consumption, expiry and capability checks.
- Added Android, Desktop, Android TV and Web/Web TV handoff bridge/controller coverage without making Android a hub.
- Added schema, golden vectors, tests and validation wiring for the SFLP12/SFLP13 promise seal.

## TVOV26 — end-to-end physical-device final seal

- Added the missing `core/tvlink/consumer-rules.pro` required by the shared Android-library convention, and added a final-seal audit for every convention-backed Android library module.
- Added fail-closed real phone + Leanback TV Release acceptance with exact installed artifact hashes, public-UI pairing/control/cast/persistence/restart/revoke checks, and machine-readable evidence.
- Added deterministic production TV-controller launch action and private explicit-cast-intent persistence so phone process restart can resume the same canonical cast.
- Advanced the TV package to version code 26 / `0.1.0-tv26`. Source/build completion never substitutes for the required physical evidence-backed final product seal.



## TVOV24 — Android TV real cast/state synchronization

## TVOV25 — Android TV domain truth and persistence convergence

- Added shared canonical TV domain-action request/result mapping and authenticated result acknowledgements.
- Added phone-owned canonical mutation execution through existing Study Session, Practice, Review, Schedule, and repository APIs with an action-id journal for retry idempotency.
- Added a bounded TV action outbox so local D-pad mutations are persisted by the paired phone before projected state changes.
- Removed synthetic TV answer/review/lifecycle/elapsed/mock-scoring mutations, including the fixed 70% mock result.
- Extended presentation projection with persisted practice answers/current question, source question ids for review evidence, and real elapsed/scoring state.
- Advanced the TV package to version code 25 / `0.1.0-tv25`; TVOV26 remains the final physical-device seal.

- Connected the phone-owned canonical TVOV21 presentation stream to the authenticated TVOV22 link with explicit Start cast / Stop cast / Resync controls.
- Added exact presentation-receipt acknowledgement, serialized latest-snapshot delivery, reconnect resync, pending STOP_CAST retry, and TV inbox clearing back to the waiting state.
- Advanced the TV package to version code 24 / `0.1.0-tv24`; TVOV25 remains the domain-truth/persistence convergence boundary.
## TVOV23 — Android phone TV controller

- Added a Settings-accessible production Android phone controller backed by the existing TVOV22 NSD/TCP client.
- Added explicit receiver selection, pairing-window check, six-digit pairing, persisted-session resume, continuity refresh, remote revoke, and local forget.
- Added shared command controls for all TV destinations plus Study Session, Questions, Mock Exam, Review, and Back.
- Kept `feature:settings` free of TV transport dependencies and kept live typed-presentation synchronization deferred to TVOV24.
- Advanced the TV package to version code 23 / `0.1.0-tv23` and added TVOV23 verifier/build-contract/docs coverage.

## TVOV22 — local pairing, discovery, and transport

- Added shared `:core:tvlink` Android NSD discovery plus a bounded local TCP request/response transport.
- Added explicit 120-second six-digit/nonce pairing with ten-attempt cap and random 24-hour revocable session credentials.
- Wired authenticated remote commands into the TVOV16 reducer and typed presentations into the TVOV21 inbox.
- Added production phone discovery/client ownership for the TVOV23 controller without adding phone UI yet.
- Added local-network permissions, persistent non-backed-up session storage, TV status/code rendering, tests, verifier/Devtool wiring, and TV package version 22 / `0.1.0-tv22`.

## TVOV19/TVOV20 - Android TV package hardening and release acceptance

## TVOV21 — typed TV presentation projection

- Added serializable typed TV presentation snapshots for eight StudyFlow surfaces.
- Added a production phone/data-owner projector and repository-backed presentation source.
- Replaced TV Activity runtime demo fixtures with an explicit waiting state and typed snapshot adapter/inbox.
- Preserved canonical source IDs across TV display slots and reject duplicate/stale presentation snapshots.
- Corrected the Home pair-phone command to route to Settings pairing.
- Bumped the TV package to version code 21 / `0.1.0-tv21`; transport remains TVOV22 work.


- Hardened the Leanback manifest/package identity, TV banner, backup/data-transfer exclusions, and cleartext policy.
- Added conditional TV Release signing using the complete `STUDYFLOW_RELEASE_*` contract with partial-configuration rejection.
- Hardened the `studyflow_tv` Devtool target so phone `:app`, feature modules, data/database modules, and repository-wide formatting are excluded.
- Added packaged APK inspection plus TVOV20 optimized Release, no-device-clean-skip, connected Leanback install/upgrade, launch, D-pad, process, and installed-version acceptance gates.
- Kept TVOV16's wire/network transport deferral explicit rather than claiming an unimplemented physical phone-to-TV transport.

## TVOV17/TVOV18 - Android TV accessibility and optimized Release

- Added the TVOV17 pure Kotlin accessibility/10-foot policy, semantic headings/live status, scrollable focus-following content, and real high-visibility focus rendering.
- Kept 64dp focus targets and centralized title/body/control/status text metrics with automated contrast checks.
- Added the TVOV18 optimized Release path using R8, resource shrinking, Android's optimized default ProGuard rules, and no broad keep-all rule.
- Added a Release artifact gate that requires `mapping.txt`, validates APK ZIP integrity, and enforces the 25 MiB release target while preserving the 35 MiB architectural ceiling.
- Added TVOV17/18 tests, verifiers, Gradle tasks, Devtool phase wiring, documentation, and cumulative roadmap updates.


## TVOV16 - Cross-device control/state continuity

- Added shared control-session and continuity-state schema in `:core:tvcontrol`.
- Added one deterministic reducer for local TV remote and paired-phone commands across all TV surfaces.
- Added monotonic revisions, active-item/playback continuity, duplicate/stale rejection, and expiry/revocation behavior.
- Kept network/Cast transport deferred and preserved the TV dependency boundary.

## TVOV15 - Minimal TV settings

- Replaced the Settings placeholder with a native TV-safe Settings surface.
- Added pairing status/manage/revoke, focus-visibility preference representation, local-first privacy, and safe About/diagnostics.
- Added shared revoke-pairing intent modeling, unit tests, verifier wiring, and documentation.
## TVOV13/TVOV14 - Android TV Schedule and Library Reference

- Added the Android TV read-only Schedule surface with today/week schedule context, review windows, study blocks, practice entries, mock-exam rehearsal, D-pad focus, paired-phone navigation commands, cast-state output, unit tests, and verifier wiring.
- Added the Android TV limited Library/reference surface with curated reference cards, short excerpts, source labels, related learning navigation, D-pad focus, paired-phone commands, Library cast-state output, unit tests, and verifier wiring.
- Added `StudyFlowTvSurface.LIBRARY` as a castable presentation surface and mapped `StudyFlowTvDestination.LIBRARY` into the native TV navigation shell.
- Preserved the TV APK dependency boundary: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, SAF file management, or phone feature-module fanout in `:app-tv`.

# TVOV07/TVOV08 Today workspace and Study session

## TVOV07 - Android TV Today workspace

- Added a structured Today workspace model with next block, review queue, practice queue, weekly progress, and schedule/mock-prep context.
- Rendered Today as a first-class TV surface with D-pad focus cards and paired-phone command routing.
- Added `verifyStudyFlowTvTodayWorkspace` and unit tests for routing, focus, snapshot, and command contracts.

## TVOV08 - Android TV Study session experience

- Added a dedicated Study Session destination opened from Today and paired-phone control.
- Added a TV session state machine for ready, running, paused, and completed states.
- Added start, pause, resume, next, complete, and return-to-Today actions for TV remote and paired-phone control.
- Added Study Session cast-state emission through the shared TV protocol.
- Added `verifyStudyFlowTvStudySessionExperience` and unit tests for state transitions, focus, command, and cast contracts.

# TVOV00 Android TV architecture boundary

- Added the Android TV roadmap for a separate, size-constrained TV APK that reuses StudyFlow core logic.
- Added a placeholder `studyflow_tv` Devtool target with source-contract-only validation until `:app-tv` exists.
- Added TV size-budget contracts: 25 MiB target and 35 MiB hard ceiling for the eventual release APK.
- Added the paired phone-to-TV control/cast contract before any TV module implementation starts.
- Added source verifiers for TV architecture, target wiring, dependency boundaries, control/cast boundaries, and size-budget drift.

# OV20 final integrated public-exam validation seal

### OV20 v5 navigation contract hotfix

- Replaced `PracticeWorkspaceRoute.openMockStudio: Boolean` with the small serializable `PracticeWorkspaceEntry` enum so workspace route payloads remain limited to identifiers and small enums.
- Preserved Mock Exam Studio deep-link behavior by deriving the screen-level open flag from `PracticeWorkspaceEntry.MOCK_EXAM_STUDIO`.
- Extended navigation serialization and OV13/OV20 source contracts so Boolean route state cannot regress.


- Added the OV01–OV20 completion matrix and fail-closed integrated source validator.
- Re-ran OV/BH/CPOV and selected historical release contracts rather than treating new source checks as sufficient evidence.
- Reconciled the Phase-63 date contract with the canonical `PublicExamDateResolver` introduced by OV18.
- Hardened manual-activity enum restore and reconciled the existing confirmed-delete helper with the final-user-readiness contract.
- Preserved the release boundary: no usable device means no claim of connected/manual device acceptance.
- Cleared the remaining workspace plural-candidate warnings with real EN/pt-BR plural resources and observable Compose call sites.
- Split ordinary Devtool validation from credentialed Release artifact generation: normal validation keeps Release compile/resources/tests/lint but packages only Debug, while the existing fail-closed final Release gate remains responsible for signed APK/AAB outputs.
- Added signing-aware cleanup of stale Release output directories before ordinary validation so a rolled-back `app-release-unsigned.apk` cannot poison the next artifact scan.

# OV19 public-exam promise audit and cleanup

- Closed the remaining literal screenshot/backlog gaps: scheduled manual activities, subject-owned weekly rules, all strategy types, schedule search/past readability, and a signal-first concurso dashboard.
- Removed the unnecessary `StudyTarget` safe-call warning in the pre-edital resolver path.
- Preserved existing engines, ownership boundaries, bilingual resources, and unknown-data truthfulness.

# CPOV roadmap reconciliation hotfix

- Reconciled the applied CPOV00-CPOV13 tree against every literal concurso-publico roadmap promise.
- Fixed the PublicExamRepository/ManagePublicExam legislation facade compile defect.
- Completed cycle/recent-practice priority evidence, attempt-outcome filters, truthful error classification, configurable banca blueprints, weak Lei Seca filtering/evidence links, the Importance x Mastery dashboard matrix, and onboarding/template/goal coverage.
- Added `verifyCpovRoadmapReconciliation` to Android and desktop preflight while retaining Room schema 13 and the full CPOV13 release gate.

# CPOV13 public-exam full integration and release seal

- Closed the CPOV00-CPOV13 concurso-publico roadmap at Room schema 13.
- Added fresh-schema and representative Phase-64 9->10->11->12->13 migration acceptance.
- Added full-package portability coverage for edital revisions, cycles, question provenance and legislation.
- Added a connected public-exam CPOV journey and kept process-restart proof in the authoritative full journey.
- Expanded Devtool final validation to Release resources, Debug/Release lint and conditional connected suites.
- Added the CPOV completion matrix and architecture decision for the reused Study Plan graph.
- Removed temporary CPOV patch backups and compatibility artifacts from the repository.
- Preserved the fail-closed boundary: source/build validation does not fabricate signed/manual device evidence.

# CSOV15 optional instrumentation validation

- Added an optional connected-device validation lane for CSOV sync work.
- `recordOptionalInstrumentationValidation` uses adb, prefers `ANDROID_SERIAL`, tries `localhost:5555`, and records SKIPPED when no device is connected unless explicitly required.
- Root is probed when available with `su -c id`; optional `adb root` probing is behind `STUDYFLOW_ADB_TRY_ROOT=true`.
- Added on-device Compose instrumentation coverage for optional sync/offline/manual-folder actions.

# Phase 63-64 Exam Runbook and Study Sprint

- Added a local, read-only exam runbook derived from readiness, review backlog, weak areas, schedule and mock evidence.
- Added an explicit no-exam-date state instead of fabricating countdown guidance.
- Added a preview-first seven-day sprint that only targets existing unlocked planned activities.
- Reused the version-token/CAS schedule transaction for explicit sprint Apply; preview never writes.
- Added English/pt-BR workspace resources, domain regression tests and the Phase 63/64 source seal.
- Preserved Phase 62 onboarding isolation while allowing the deferred runbook/sprint direction to begin after Phase 62.
- Re-audit correction: fail closed on nested cross-plan schedule evidence, preserve final-review priority under the runbook cap, guard duplicate sprint Apply, recover ordinary apply failures, validate the sprint mutation envelope, re-read the current seven-day candidate set, and reject stale previews before the existing CAS transaction.

# Remediation Phase 1-2 — Build/runtime and database foundation

- Restored a complete pinned Gradle wrapper and made Android validation fail closed instead of silently skipping build gates.
- Added Debug and Release compile/lint/package gates to repository and Devtool validation.
- Consolidated receivers/widgets onto the process-owned AppContainer/Room graph and added explicit startup loading/failure state.
- Consumed reminder launch extras one-shot so Activity recreation cannot replay notification navigation.
- Advanced Room to schema v4 with plan-scoped ownership triggers, single-active-session enforcement, value guards, and v2→v3 Focus-duration backfill.
- Replaced destructive unlocked schedule cleanup with replaceable-state cleanup and added atomic/CAS mutation primitives.
- Hardened Study Session ownership/state transitions and changed Focus note persistence to a field-specific active-session update.
- Added fail-closed mapper enum/list decoding, stable list serialization, complete StudyTarget/ExamStage mappings, and core model invariants.
- Added migration/integrity/mapper/model regression tests and a dedicated Phase 1-2 remediation source contract.

# Final Full-App Acceptance Seal r2

- Strengthened the final acceptance verifier after audit closure.
- Added explicit guards for adaptive-reschedule preview/apply wiring, report/weekly-coach inputs, reminder blocked/degraded/action-needed/ready states, exact-alarm availability, battery optimization, notification permission inputs, and duplicate string/plurals resources.
- Kept runtime app code and Android stack unchanged; this is a validator/doc-only closure over the final seal.

# Final Full-App Acceptance Seal

- Added a final source acceptance gate for the completed UI/UX V2 through Phase 62 roadmap.
- Wired `verifyFinalAppAcceptanceSeal` into Gradle and Devtool default verification so future runs keep the full product chain sealed.
- Added a final acceptance matrix documenting source coverage versus Devtool/real-device target gates.
- Preserved the Android, Gradle, Kotlin, KSP, Compose, Room, and Navigation 2.9.8 stack.

# Phase 62 Onboarding and Sample Plan Wizard

- Added a local, preview-first onboarding wizard for users who do not want to start from a blank workspace.
- Added four structured sample templates: public exam, university semester, certification bootcamp, and free-study habit.
- Wired the Plan list to “Start with sample” and added a bilingual preview screen for subjects, topics and weekly blocks.
- Seeded localized sample subjects, topics, plan settings and recurring schedule rules only after the user taps Create sample.
- Added the Phase 62 source verifier and Gradle gate while preserving the local-first boundary.

# Phase 60-61 Smart Content Cleanup and Exam Report Export

- Added a structured, preview-only content-health engine for duplicates, orphans, stale resources/plans, tag variants, broken review links, and schedule/content mismatches.
- Added localized Plan and Progress content-health surfaces with no automatic mutation.
- Added user-initiated local HTML and native PDF exam-report export through FileProvider.
- Added structured report coverage for readiness, weekly coaching, evidence, recovery, practice tests, mock exams, adaptive reschedule, content health, and next safe actions.
- Converted the remaining primary resource plural candidates to `<plurals>`, removed truly unused compatibility resources, and removed the targeted resource warning suppressions.
- Added the Phase 60-61 source verifier and Gradle verification gate.

# Phase 56-57 r2 promise-closure audit

- Added `verifyPhase56_57ReleaseReadiness` as a release-facing source gate.
- Added `:app:lintRelease` to the overlay validation task list.
- Updated the Phase 56/57 verifier, acceptance docs, README, and validation report so release-mode wording no longer rests on debug-only validation.

# Phase 56-57 Post-V2 Integration and Real-Device Polish

- Added a Post-V2 Integration Seal covering the full UI/UX V2 workspace, focus, readiness, reschedule, practice, mock exam, widget, reminder, timeline, backup, search, and localization chain.
- Added a Device Readiness Polish Seal for compact portrait behavior, large fonts, TalkBack-facing labels, predictive back, edge-to-edge, widget taps, reminder permission states, upgrade/backup gates, and warning boundaries.
- Added repository verification for the known post-V2 traps: route loops, missing workspace wiring, android:tint, bad resource escapes, deprecated TrendingUp icon usage, duplicate strings, and forbidden Kotlin extension references.

# Changelog

## ATT03 — Android Widget Suite 2.0

- Upgraded the existing Android widget in place to five configurable modes: Today, Next, Reviews, Session, and Compact.
- Added per-widget plan/mode persistence, Android reconfiguration, ID-restore migration, plan-unavailable recovery, responsive compact rendering and launcher preview metadata.
- Added real Start-next and Pause/Resume widget mutations plus precise review/schedule/session deep links.
- Added a notification-permission-independent inexact freshness alarm covering session minute ticks, planned blocks, review boundaries, session targets and day rollover, including system time/boot/package recovery.
- Expanded widget Debug Workbench evidence and added unit/instrumentation/source-contract tests plus the focused `verifyAtt03AndroidWidgetSuite` gate.




## CSOV03

- Added the optional sync provider abstraction with provider id, display name, account requirement, configuration state, checkpoint listing, upload, download, and delete operations.
- Added encrypted `.sfbackup` upload/download DTOs so providers transfer encrypted bytes plus manifest metadata instead of raw database handles.
- Added a disabled default provider, provider registry, domain tests, and CSOV03 source-contract verification.

## CSOV02

- Added the portable StudyFlow sync checkpoint bundle format (`.sfbackup`) with manifest, checkpoint, database snapshot, attachments, and SHA-256 integrity entries.
- Reused the existing authenticated AES-GCM encrypted package envelope for remote-ready sync bundles so raw database bytes are not the provider-transfer default.
- Added codec unit coverage plus a CSOV02 source-contract verifier and Gradle verification task.

## CSOV00-CSOV01

- Migrated user-facing product copy from local-checkpoint-only sync language to local-first optional cloud sync.
- Added `StudyFlowSyncSettings` with sync disabled, provider none, automatic sync disabled, Wi-Fi-only enabled, charging-only disabled, and remote-backup encryption enabled by default.
- Updated Settings and Sync Foundation to expose provider-none and automatic-sync-off state without making cloud obligatory.

## Phase 58-59 r2 audit closure

- Scoped lint hygiene update: resource warning suppressions are now attached only to known compatibility strings instead of the entire resource file.

## Phase 54-55 reminder reliability and evidence timeline

- Added a Reminder Reliability Center that separates blocked reminder delivery, degraded timing, backlog pressure, quiet hours, and safe fallback behavior.
- Added a V2 Progress Evidence Timeline from sessions, planned blocks, reviews, questions, practice tests, mock exams, completed goals, and pinned notes.
- Kept the timeline localized in the UI and bounded/newest-first in the domain.
- Added Phase 54/55 tests, documentation, and verifier wiring.

## Phase 52-53 r4 Mock Exam Studio compile hotfix

- Replaced the `Question::isExamEligible` extension reference in `MockExamStudioPlanner` with an explicit lambda so Kotlin/JVM compilation accepts the planner.
- Hardened the Phase 52/53 verifier so the prohibited extension-function reference cannot return.


## Phase 52-53 - Mock Exam Studio and widget actions

- Added a preview-first Mock Exam Studio for timed simulated exams.
- Added localized Practice workspace controls for mock-exam creation.
- Upgraded the Today widget with plan-aware primary and review actions.
- Phase 52/53 r2: tightened Mock Exam Studio filtering, disputed-question handling, dynamic preview, and warning display.

## Phase 50-51 r3 Kotlin compile hotfix

- Fixed `PracticeWorkspaceScreen.reviewTitle` to snapshot cross-module nullable review IDs into local values before smart-casting.
- Added a source-contract gate that rejects direct smart-casts of public `ReviewItem` properties from another module.
- Preserved the complete Phase 50-51 r2 weakness-drill and question-import implementation.

## Phase 50-51 r2 audit hotfix

- Fixed the question importer to use valid escaped Kotlin regexes and no literal tab characters in source.
- Fixed TSV/Anki-style rows without options to import as short-answer questions.
- Fixed weakness-drill scoring so recurrence weight cannot pull unrelated questions into a generated drill.
- Fixed import rejected-count math to count rejected drafts rather than individual issue rows.
- Added focused tests and verifier checks for the audit gaps.

## Phase 50-51 weakness drills and question import

- Added a weakness-drill generator that builds targeted practice from repeated errors, overdue question reviews, matching topics, and matching subjects.
- Added a question import pipeline for CSV, TSV/Anki-style rows, and Markdown blocks with validation and duplicate detection.
- Wired weakness-drill creation and text question import into the V2 Practice workspace.
- Fixed remaining AutoMirrored `TrendingUp` deprecation warnings in the V2 Progress workspace.
- Added domain tests, bilingual workspace resources, documentation, and a Phase 50-51 verifier.

## Phase 48-49 readiness and adaptive reschedule

- Added an explainable exam readiness score combining coverage, review health, practice, mock exams, consistency, schedule adherence, and time feasibility.
- Added a preview-first adaptive reschedule wizard that updates only executable unlocked planned activities.
- Surfaced readiness signals and safe reschedule previews inside the V2 Progress workspace.
- Added domain tests, bilingual resources, documentation, and a Phase 48-49 repository verifier.

## Phases 38-47 complete UI/UX V2 workspace migration

- Replaced the feature-catalog shell with adaptive Today, Plan, Practice, and Progress roots.
- Added saved root back stacks, typed compact routes, legacy-route translation, and distinct full-editor routes.
- Added repository-backed command-center, plan, practice, progress, plan-picker, system-hub, and contextual quick-add screens.
- Added expandable subject/topic sections, direct V2 review grading and skipping, test/mock visibility, progress trend semantics, and actionable adaptation previews.
- Added persistent focus mode with Room schema 3, migration 1 to 3 / 2 to 3 coverage, notes, pause/resume, completion, cancellation, and restoration.
- Hardened the V2 source contract after audit so it rejects navigation compile defects, marker-only implementation, shell-wide back interception, schema-2 backup regressions, large-font clipping, weak pane semantics, and cross-plan stack leakage.
- Added bilingual workspace resources, reusable design-system components, edge-to-edge setup, tests, source validators, and Devtool gates.
- Preserved the existing release-candidate identity and real-device evidence boundary.

## Phase 36-37 release identity and real-device acceptance seal

- Moved the application identity to `versionCode = 37` and `versionName = "1.0.0-rc1"`.
- Updated phase-version validation so release-candidate versioning remains compatible with historical source-contract validators.
- Documented the release-candidate package path, signing boundary, minification decision, and external-secret policy.
- Added `FINAL_ACCEPTANCE_REPORT.md` as a pending target-execution template instead of fake device evidence.
- Added a real-device acceptance checklist covering fresh install, upgrade install, sessions, practice, reminders, widgets, backup, restore dry-run, locale, theme, background/foreground, kill/reopen, date/time, and accessibility checks.
- Advanced release readiness, validation seals, source scripts, and repository contract loops through Phase 37 while keeping target-machine gates blocked until real evidence exists.

## Phase 34-35 interaction honesty and documentation cleanup

- r3: Corrected the overlay contract so it no longer targets Devtool-reserved `.devtool/` runtime metadata. Generated Devtool workspace state must be cleaned with an explicit local command when needed, not through overlay inventory.

- Added a shared `StudyFlowStatusPill` display component for passive status labels.
- Replaced passive `AssistChip(onClick = {})` controls across Library, Reviews, Runtime Smoke,
  Progress, Sessions, Reminders, Analytics, Schedule, Plans, Subjects, and Practice screens.
- Removed remaining online-sync/security negation copy from the user-facing Privacy surface.
- Reworded developer diagnostic compatibility labels away from legacy scaffold language.
- Advanced source validation, app version, release readiness, and repository contract loops through
  Phase 35.
- Rewrote README, validation report, and changelog as current product docs with one top-level heading.

## Phase 32-33 diagnostics de-scaffolding and offline checkpoint completion

- Renamed the debug entry to Developer diagnostics and kept it gated to debug builds.
- Replaced fallback probe/lab placeholder copy with concrete developer-support fallback states.
- Completed the former sync foundation as an offline-first local checkpoint surface that keeps the
  user story on this device.
- Reworked the local checkpoint model around checkpoint readiness and pending local changes instead
  of online transfer selection.
- Advanced release readiness and source validation through Phase 33.

## Phase 30-31 product truthfulness and dashboard action completion

- Removed normal-user Settings cards that exposed app-shell readiness, UX polish, release-candidate
  polish, and stale release seals.
- Renamed local-only sync surfaces to local backup checkpoints and removed premature online-sync
  promises.
- Advanced the release-readiness scope through Phase 31 plus Debug Workbench D1-D8 while keeping
  target-machine release gates evidence-based.
- Wired the Dashboard primary session action to the existing Study Sessions route and converted the
  default-plan chip into a passive badge.
- Added Phase 30 and Phase 31 verifiers plus source-level contract tests for Settings and Dashboard
  truthfulness.

## Earlier delivery history

Detailed phase and Debug Workbench implementation notes remain in the root `PHASE_*`,
`DEBUG_WORKBENCH_*`, and `docs/quality/*` files because historical validators still treat those files
as source contracts. They are retained as implementation history, not as current user-facing product
copy.

## R4 Devtool test-contract hotfix

- Fixed `ExamReadinessEngine.timeFeasibility` so an empty pending schedule is treated as "no explicit backlog" rather than fabricating uncovered work from coverage alone.
- This keeps a well-covered, high-retention plan with strong mock/practice evidence in `ON_TRACK`, matching the domain test and the promised readiness semantics.
- The pessimistic branch still applies when there are real pending planned minutes, and same-day exams still use coverage directly.

## Phase 48-49 r5 workspace resource hotfix

- Added the missing localized `open_plan` workspace string referenced by the Progress readiness surface.
- Hardened the Phase 48-49 verifier so `ProgressWorkspaceScreen` `R.string.*` references must exist in workspace resources.


## Phase 54/55 r3 resource and lint hotfix

- Fixed reminder reliability strings that failed Android resource flattening.
- Added validation guards for risky reminder string escapes and widget `android:tint` lint regressions.

## Phase 58-59 - Cloudless queue, weekly coach, warning hygiene

- Added a local-first backup queue planner and Sync Foundation surface for local checkpoint/export work.
- Added a Study Coach weekly review generated from readiness, reschedule preview, analytics, and evidence.
- Cleaned the Phase 57 warning backlog: typed-route `@Keep`, current Material3 tabs, private-resource rename,
  primitive time state, obsolete SDK branch removal, and explicit resource-lint suppression boundaries.


### Phase 62 r3 compile hotfix

- Fixed onboarding schedule preview Kotlin helper reuse so `:feature:plans:compileDebugKotlin` does not hit overload ambiguity with the existing plan settings time formatter.
- Strengthened the Phase 62 validator to reject future duplicate `formatMinuteOfDay` helper declarations in the onboarding screen.
- CSOV04: Added optional sync onboarding with explicit Keep offline and Choose sync folder paths.
- CSOV05: Added manual folder sync controls, SAF folder selection, and local manual sync state.

## CSOV06 - Sync device identity and ledger

- Added durable optional-sync device identity models.
- Added checkpoint ledger records for creator device, revision, upload state, restore-preview state, and conflict count.
- Added repository ledger inspection and restore-preview recording hooks.

## CSOV07 - Conservative merge engine

- Added stable-id sync entity versions and merge decisions.
- Added conservative merge engine with local-only, remote-only, one-sided change, both-changed, and delete-vs-change decisions.
- Added tests and source contract for conflict-first sync behavior.

## CSOV08 - Conflict review UI

- Added sync conflict review item models and presenter.
- Added a sync UI card for keep-local, use-remote, and keep-both decisions.
- Added source verifier `verifyCsov08ConflictReview`.

## CSOV09 - Background sync modes

- Added background sync mode and constraint models.
- Added a planner that keeps fresh installs off and makes manual-only the configured default.
- Added sync UI summary copy and `verifyCsov09BackgroundSyncModes`.

## CSOV10 — Real provider lane

- Added optional WebDAV / Nextcloud provider lane for encrypted `.sfbackup` upload, list, download, and delete through a user-supplied HTTPS endpoint.
- Added Google Drive as an optional provider shell without default configuration or StudyFlow account requirement.
- Added source verifier `verifyCsov10RealProviders`.

## CSOV11 — Final optional cloud sync acceptance seal

- Added final sync acceptance seal covering local-first defaults, optional provider catalog, encrypted remote uploads, conflict review, and background sync gating.
- Added source verifier `verifyCsov11FinalSyncSeal`.


## CSOV12 provider setup security

- Added optional provider setup validation for SAF and WebDAV.
- Added secret-handle based diagnostics that avoid raw cloud credentials.


## CSOV13 sync runbook

- Added sync execution planner for manual upload, remote check, restore preview, and background modes.
- Restore preview stays dry-run-only and conflict-gated.

## CSOV14 - Optional sync delivery audit hotfix

- Added a cumulative CSOV00-CSOV13 optional sync delivery verifier.
- Registered the missing CSOV00/CSOV01 Gradle verifier task.
- Wired the optional sync verifier lane into `checkAll`.


## TVOV01 - Minimal Android TV application module

- Added the `:app-tv` Compose compiler guard so the native placeholder module does not require Compose Runtime.

- Added `:app-tv` as the first small, separate Android TV application module.
- Upgraded the `studyflow_tv` Devtool target to compile, unit-test, package, and lint the TV module.
- Added a Leanback launcher manifest with touchscreen marked optional and a separate TV package id.
- Kept TVOV01 dependency-budgeted: no phone feature modules, Cast SDK, MediaRouter, AndroidX TV UI, or Compose runtime dependencies are pulled into the TV APK yet.
- Retained the TV size budget and paired phone-to-TV control/cast contract for later overlays.


## TVOV02 - TV dependency budget and lint cleanup

- Added `verifyStudyFlowTvDependencyBudget` to keep the TV app dependency boundary explicit.
- Removed the initial `app-tv` manifest lint warnings by declaring data extraction/backup rules and dropping fixed-orientation/resizeable assumptions.
- Replaced the large vector TV banner with a compact layer-list resource.
- Kept Cast, MediaRouter, AndroidX TV UI, Compose Runtime, and phone feature dependencies out of `:app-tv`.


## TVOV03 - Pairing, control, and cast protocol foundation

- Added `:core:tvcontrol` as the shared pure-Kotlin phone-to-TV protocol module.
- Added revocable pairing token, pairing-state, control-command, TV-surface, control-action, and cast-state contracts.
- Wired both the Android mobile app and `:app-tv` to the shared protocol module.
- Added serialization round-trip tests for commands and cast state.
- Added `verifyStudyFlowTvPairingControlCastProtocol` to the `studyflow_tv` target.
- Kept transport deferred: no Cast SDK, MediaRouter, AndroidX TV UI, Compose Runtime, or phone feature-module fanout in `:app-tv`.


## TVOV04 - D-pad and focus primitives

- Added pure Kotlin TV remote command, key mapping, focus role, focus item, and focus policy contracts.
- Added deterministic initial focus, disabled-item skipping, non-wrapping directional movement, and select/back/play-pause focus retention.
- Added native View focus helper with 64dp minimum target and visible focus drawable state.
- Added a structured focusable action row to the placeholder `:app-tv` shell.
- Added JVM tests for D-pad focus policy and Android remote key-code mapping.
- Added `verifyStudyFlowTvFocusPrimitives` to the `studyflow_tv` target.
- Kept the TV app dependency-budgeted: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database`.


## TVOV05 - Android TV navigation shell

- Added a structured TV destination enum and navigation shell contract.
- Replaced the placeholder-centered TV screen with a native D-pad-aware navigation rail and content panel.
- Mapped TV destinations to shared `:core:tvcontrol` surfaces so paired-phone commands and local TV navigation share one model.
- Added Back-to-Home handling for non-Home TV destinations.
- Added JVM tests and `verifyStudyFlowTvNavigationShell` to the `studyflow_tv` target.
- Kept the TV app dependency-budgeted: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database`.


## TVOV06 — Android TV Home surface

- Added a structured Home surface for the separate Android TV app.
- Added TV Home cards for Today, Practice, Reviews, and Mock Exam.
- Routed Home primary actions through the shared navigation and TV control protocol.
- Removed unused TVOV05 TV string resources reported by lint.
- Added `verifyStudyFlowTvHomeSurface` and focused app-tv unit coverage.


## TVOV09/TVOV10 - Android TV Question Practice and Mock Exam Studio

- Added TV Question Practice with answer selection, explanation reveal, progress, focus items,
  paired-phone commands, cast state, tests, and verifier.
- Added TV Mock Exam Studio with timed sections, start/pause/resume, mark-for-review, submit,
  result review, focus items, paired-phone commands, cast state, tests, and verifier.
- Fixed the TV Today status string that triggered the lint plural-candidate warning.
- Preserved the TV dependency boundary: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter,
  phone feature-module fanout, `core:data`, or `core:database` in `:app-tv`.


## TVOV11/TVOV12 - Android TV Review Notebook and Progress Analytics

- Added TV Review/Error Notebook with due review cards, confidence/reason metadata, known/error-note state transitions, D-pad focus, paired-phone commands, cast state, tests, and verifier.
- Added TV Progress/Analytics with weekly progress, accuracy, question-volume, readiness, weak-subject summaries, recommended next actions, D-pad focus, paired-phone commands, cast state, tests, and verifier.
- Fixed the TV Today status string plural-candidate warning by removing `%d` followed by plural words.
- Preserved the TV dependency boundary: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, phone feature-module fanout, `core:data`, or `core:database` in `:app-tv`.


### SFLP10/SFLP11 — Web + Android TV generic peers

- Added Web/Web-TV SFLP peer persistence, manual WebSocket endpoints, offline-cache integration, frontiers, queue acknowledgement, QR pairing import, revoke and forget.
- Added Android TV generic SFLP peer persistence and capability profile over `:core:link` while preserving the TVOV22 phone/TV compatibility adapter.
- Added contracts and validators for Web runtime parity, Android TV peer generalization, and predecessor SFLP01-SFLP09 continuity.

## SFLP14/SFLP15 - Live session continuity and universal presentation projection

- Added SFLP14 live session snapshots, events, fencing, monotonic replay, duplicate suppression, stale epoch rejection, pause/resume/cursor continuity and exactly-once completion.
- Added SFLP15 presentation projection offers, monotonic frames, sink acknowledgements, control commands, TV write-capability rejection and universal source/sink semantics.
- Added Android, Desktop, Android TV and Web bridge/controller coverage plus protocol schema and golden vectors.


## SFLP16 + SFLP17 Control/Input Delegation

- Added leased generic control protocol for peer-to-peer StudyFlow control.
- Added target-gated input delegation for keyboard, text, D-pad, select/back and touch input.
- Added Android, Desktop, Android TV and Web bridges plus schema, golden vectors and verifier wiring.

## SFLP18-19 — Notification/media sync

- Added SFLP18 peer-neutral notification delivery, delivery acknowledgement, and schedule reminder sync.
- Added SFLP19 read-only material streaming handoff with chunk acknowledgements and TV write guards.
- Added Android, Desktop, Android TV, Web, schema, golden vector, and verifier coverage for notification/media sync.
- Repaired SFLP16/SFLP17 test constructors to match the canonical peer descriptor API before final validation.

## SFLP20-21 — Clipboard/share/import and conflict review

- Added SFLP20 clipboard/share/import offers, acknowledgements, capability guards, duplicate suppression, and TV preview-only enforcement.
- Added SFLP21 conflict-review open/resolve/ack primitives with one-time monotonic resolution and canonical domain operations.
- Added Android, Desktop, Android TV, Web, schema, golden vector, and verifier coverage.
- Repaired Web runtime exports so appended SFLP runtime classes remain CommonJS-compatible for final validation.

## SFLP22-23 — Account backup handoff and release validation consolidation

- Added SFLP22 account handoff offers, acknowledgements, target/capability/TTL guards, and duplicate suppression.
- Added backup bundle manifests, restore plans, restore acknowledgements, and TV/Web-TV write-restore rejection.
- Added SFLP23 final release validation plan/result models and a consolidated SFLP verifier chain.
- Added Android, Desktop, Android TV, Web, protocol schema, golden vector, and verifier coverage.

## SFLP24 — Final release validation seal

- Added the SFLP24 final release seal model, protocol schema, golden vector, and core tests.
- Added `verifySflp24FinalReleaseSeal` and restored the complete SFLP01-SFLP23 chain into `checkAll`.
- Updated Devtool test-lane defaults so the final run names the SFLP verifier chain once and in order.

# REL02 - release evidence exporter and triage bundle

- Added the REL02 post-SFLP release evidence exporter.
- Added redacted summary JSON, markdown report, triage JSON, and zipped evidence bundle generation.
- Preserved fail-closed release semantics so missing, pending, failed or blocked evidence cannot become release-ready.

# REL03/REL04 artifact identity and rollout manifest

- Added REL03 signed artifact identity collection for Release APK/AAB checksums, structure checks, signature-tool evidence and install smoke evidence.
- Added REL04 release channel manifest and rollout readiness generation for internal, beta and production channels.
- Preserved fail-closed release semantics: `PENDING_TARGET_EXECUTION` and `BLOCKED` never synthesize `releaseReady=true`.

# REL05/REL06

- Added REL05 release notes exporter for markdown, JSON, and store-draft copy generation.
- Added REL06 rollback package manifest generation with operator command template and evidence bundle.
- Wired `verifyRel05Rel06ReleaseNotesRollback` after REL03/REL04 without claiming synthetic release readiness or rollback readiness.

# REL07/REL08

- Added REL07 release checklist gate for REL01-REL06 evidence and operator checklist aggregation.
- Added REL08 final release-ops seal with final evidence bundle export.
- Wired `verifyRel07Rel08FinalReleaseOpsSeal` after REL05/REL06 for the final release-operation track gate.

## REL-FINAL — roadmap promise audit and evidence campaign

- Audited every pre-existing roadmap/product verifier and folded current-truth, localization, historical-provenance, stale-verifier, and source-hygiene gaps into the terminal release batch.
- Added a machine-readable 213-verifier promise ledger and exhaustive 211-static-verifier audit runner.
- Added a safe REL01-REL08 campaign orchestrator that remains pending until real signed artifact, connected-device, restart/restore, device-observation, and operator-checklist evidence closes both final readiness gates.

### ATT05 Web/PWA Notification & Attention Runtime

StudyFlow Web now has a production Web/PWA attention runtime with foreground scheduling, service-worker push/click/close/drain handling, browser notification permission flow, in-app Notification Center, optional Web Push subscription foundation, app-badge progressive enhancement, SFLP delivery ingestion, and durable ATT01 ledger convergence. The UI reports closed-browser delivery truthfully as foreground-only unless Push is subscribed.

## ATT08 — Compact Companion Attention

- Added phone-owned wearable/compact companion attention projections across Android, Desktop, TV and Web.
- Added shared compact companion routing, privacy, suppression and action models on top of ATT07.
- Added Web compact companion runtime/dist parity, tests, docs and `verifyAtt08CompactCompanionAttention`.

## ATT09 — Calendar / Schedule Export Attention Integration

- Added shared calendar attention export contracts with ICS rendering, `VALARM` reminder lead times, privacy modes, and ATT01-compatible attention occurrence projection.
- Added Android, Desktop and Android TV bridges that keep calendar export user-initiated and preserve ATT06/ATT07 delivery truth.
- Added Web/PWA calendar export runtime, Progress → Tools export/copy UI, runtime/dist parity, service-worker cache coverage and Web regression tests.
