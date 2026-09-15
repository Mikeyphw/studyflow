# Final audit remediation closure ledger

This ledger is updated by the 30-overlay final remediation campaign. “Implemented” means production/build behavior and regression contracts exist; runtime-generated evidence remains separately fail-closed where applicable.

| Finding | Overlay | Status | Implementation / evidence owner |
|---|---:|---|---|
| S0-01 | 1/30 | Implemented | root Gradle lazy ordering + Python verifiers; `verifyOv01RepositoryGradleDevtoolTruth` |
| S0-02 | 1/30 | Implemented | `verifyPhoneReleaseSigningPreflight`; `ov01_release_gate.py signing` |
| S0-03 | 1/30 | Implemented | dependency-only `verifyRepositoryContracts` with individually owned verifier tasks |
| S0-04 | 1/30 | Implemented | root `Exec` declared inputs/command/stamp policy; volatile gates always-run |
| S0-05 | 1/30 | Implemented | OV01 `devtool-overlay.json` becomes current root overlay metadata on apply |
| S0-06 | 1/30 | Implemented | runtime-state ignore rules + `ov01_finalize_target_state.sh` narrow cleanup |
| S0-07 | 1/30 | Implemented | `.devtool.toml` `@everything` includes `@quality` |
| S0-08 | 1/30 | Implemented | direct Gradle defaults aligned to standard Devtool resource policy |
| S0-09 | 1/30 | Implemented | generated-behavior `gradlew` / `gradlew.bat` launchers |
| S0-10 | 1/30 | Implemented | desugaring dependency/configuration owned only by Android convention |
| S0-11 | 1/30 | Implemented; materialization required on target | dependency locking + SHA-256 metadata refresh/finalizer + fail-closed release integrity gate |
| S0-12 | 1/30 | Implemented | global `android.sync.suppressAgpWarnings` removed |
| S1-01 | 2/30 | Implemented | canonical persisted active seconds + monotonic millisecond runtime clock; goal/public-exam analytics regression tests |
| S1-02 | 2/30 | Implemented | shared 5..720 session/activity target contract; long planned-activity start regression |
| S1-04 | 2/30 | Implemented | `questionSnapshotV1` immutable attempt payload; frozen grading and legacy start upgrade/fail-closed behavior |
| S1-05 | 2/30 | Implemented | explicit `StudyPlanId` Mock Exam Studio owner + mixed-plan rejection |
| S1-11 | 2/30 | Implemented | strict single non-empty identity record; no current-bank fallback; malformed/started legacy attempts fail closed |
| S1-12 | 2/30 | Implemented | `StudyDomainInvariants` shared by models/managers and relevant Android forms; material/duration contracts reconciled |
| S1-03 | 3/30 | Implemented | missed-activity rebalance uses canonical persisted schedule/rules, recurrence-aware policy placement, overlap reservation, and atomic mutation evidence |
| S1-06 | 3/30 | Implemented | generation rejects foreign-Plan rules/strategy and shares canonical rule/activity ownership and duration contracts |
| S1-07 | 3/30 | Implemented | strict ISO goal dates, mandatory custom end date, non-reversed ranges, and fail-closed goal evaluation |
| S1-08 | 3/30 | Implemented | DAY_OFF and AVAILABILITY use the same WEEKLY/INTERVAL/COUNT recurrence evaluator as schedulable rules |
| S1-09 | 3/30 | Implemented | topic projection rejects duplicate, orphan, cross-owner, self-parent, and cyclic graphs before recursive projection |
| S1-10 | 3/30 | Implemented | Desktop Study Session wiring injects `System.nanoTime` milliseconds as its monotonic runtime clock |

## Overlay 4 of 30 — OV04 Room graph ownership & revision integrity

| Finding | Closure implementation | Behavioral evidence |
|---|---|---|
| S2-01 | Global active-session SQLite guard + global existing-data verification | `StudyFlowOv04IntegrityTest.singleActiveSessionIsGlobalAcrossPlans` |
| S2-02 | Complete modern graph revision table registry | `StudyFlowOv04IntegrityTest.modernTablesAdvanceGraphRevision` |
| S2-03 | Modern stable-owner/scoped-reference triggers | `StudyFlowOv04IntegrityTest.crossPlanModernReferencesAreRejectedAndDiagnosed` |
| S2-04 | `moveTopic` modern linked-table rebinding | `StudyTopicMoveModernOwnershipTest` |
| S2-08 | SQLite-scoped `questions.examStageId` and `examNoticeRevisionId` provenance | `StudyFlowOv04IntegrityTest.questionProvenanceAndCyclePointerAreDurable` |
| S2-09 | Durable `study_cycles.currentItemId` ownership + delete/move safety | `StudyFlowOv04IntegrityTest.questionProvenanceAndCyclePointerAreDurable` |
| S2-10 | Shared modern logical-integrity diagnostic engine | corruption probe in `StudyFlowOv04IntegrityTest.crossPlanModernReferencesAreRejectedAndDiagnosed` |
| S2-12 | Schema 14 explicit managed-trigger drop/recreate migration | `StudyFlowMigration13To14IntegrityTest` |

## Overlay 5 of 30 — OV05 Duplication, migrations & snapshot determinism

| Finding | Closure implementation | Behavioral evidence |
|---|---|---|
| S2-A1 | orphan byte accounting advances only on successful physical deletion | OV05 source verifier + cleanup implementation |
| S2-05 | duplicated Practice/Mock configuration remaps question identity, frozen snapshot IDs and answer IDs | `StudyPlanGraphDuplicationOv05Test` |
| S2-06 | language graph + Termux request/result tables join the duplication graph with parent-scoped predicates | `StudyPlanGraphDuplicatorTest`; `StudyPlanGraphDuplicationOv05Test` |
| S2-07 | 2→3 target-duration normalization + schema-15 trigger replacement align persistence with canonical 5..720 duration | `StudyFlowOv05MigrationCoverageTest.twoToFifteenAcceptsLegacyLongFocusDurationAndPreservesCanonicalTarget` |
| S2-11 | checked schema 7/8 snapshots and direct sequential 6→7→8→9 migration coverage | `StudyFlowOv05MigrationCoverageTest.sixToNineHasCheckedIntermediateSchemasAndPreservesTermuxAndLanguageRows` |
| S2-13 | logical snapshot excludes FTS/internal tables and orders every table by primary key | `StudyFlowDatabaseSnapshotWriterOv05Test` |

## Overlay 6 of 30 — OV06 Portable package semantics & restore modes

| Finding | Closure implementation | Behavioral evidence |
|---|---|---|
| S3-01 | FULL inventory now uses the complete portability graph, including parent-scoped language and Termux rows | `StudyPlanPortabilityOv06Test.fullReplaceCoversParentScopedLanguageAndTermuxRows` |
| S3-02 | REPLACE clears every portability table using each table's canonical ownership predicate in reverse graph order | `StudyPlanPortabilityOv06Test.fullReplaceCoversParentScopedLanguageAndTermuxRows`; OV06 verifier |
| S3-03 | schema-2 `POINT_IN_TIME_SNAPSHOT` purpose preserves exact identities/state/timestamps/settings/annex metadata and restores only to the source plan | `StudyPlanPortabilityOv06Test.exactSnapshotRestoresRootStateAndTimestamps`; repository restore path |
| S3-04 | portable annex rows without matching archive payload bytes are ignored before persistence | OV06 projector/verifier; snapshot purpose remains exact |
| S3-05 | STRUCTURE/TEMPLATE are history-redacted across legislation, cycle cursor, programming workspace, Termux runtime/history and language review state | `StudyPlanOv06SemanticsTest.structureAndTemplateStripProgressAndDeviceState` |
| S3-06 | MERGE retains only existing singleton parents and rewrites imported child references to the retained canonical IDs | `StudyPlanPortabilityOv06Test.mergeRebindsChildrenToRetainedSingletonParent` |
| S3-07 | dry-run consumes `projectPayloadForImport` and `diffProjected`, the same ID/reference projection used by production import | `StudyPlanOv06SemanticsTest.projectedDiffConsumesProductionRemappedIds`; repository dry-run path |
| S3-08 | package schema advances to v2; writers are v2-only; readers accept explicit v1 migration semantics while v2 remains inventory-strict and future versions fail closed | `StudyPlanPortabilityOv06Test.legacyV1MayOmitModernTablesButV2IsStrict`; codec unit/source contracts |

## Overlay 7 of 30 — OV07 Backup, snapshot, SAF & manual-sync durability

| Finding | Closure implementation | Behavioral evidence |
|---|---|---|
| S3-09 | deterministic annex restore destinations preserve/rollback existing bytes and commit replacement only with the database transaction | `StudyPlanPortabilityAtomicRestoreTest.repeatedAnnexBearingSnapshotRestoreAtomicallyReplacesExistingDestination` + OV07 verifier |
| S3-10 | Desktop backup/restore rejects symlinks throughout workspace/source traversal and resolves real external boundaries | Desktop portability source contracts + OV07 verifier |
| S3-11 | Desktop backup pre-budgets payload and bounds encrypted output to the same 2 GiB accepted by restore | `BoundedBackupOutputStream`; OV07 verifier |
| S3-12 | Plan Portability SAF import grants are released on replace/success/back/dispose | `PlanPortabilityScreen.releaseImportGrant`; OV07 verifier |
| S3-13 | package manifests reject duplicate annex IDs as well as duplicate entry names | `StudyPlanPackageCodecAnnexPayloadTest.writerRejectsDuplicateAnnexIdsEvenWhenEntryNamesDiffer` |
| S3-14 | snapshot deletion tombstones the file and restores the row/file if physical deletion fails | `RoomStudyPlanPortabilityRepository.deleteSnapshot`; OV07 verifier |
| S3-15 | snapshot write/resolve paths use the same sanitized plan segment and canonical boundary checks | `RoomStudyPlanPortabilityRepository.writeSnapshotFile`; OV07 verifier |
| DS2-05 | manual-folder sync has a production apply path with private staging, transactional logical DB restore, annex rebinding and revision/integrity verification | `ManualSyncDatabaseRestoreOv07Test`; `ManualFolderSyncIntegrationContractTest` |
| DS2-10 | manual-sync passphrase is wipeable `CharArray` state and cleared in operation `finally` | `SyncFoundationScreen`; OV07 verifier |
| DS2-11 | manual restore preview decrypts to private bounded storage and streams metadata instead of materializing plaintext archive in heap | `StudyFlowSyncBundleCodecTest.streamingPreviewAndStageAvoidWholeArchiveHeapMaterialization` |
| DS2-12 | annex SAF/file inspection and hashing are outside Room transaction; graph-revision retry rejects mixed captures | `LocalStudyCloudSyncRepository.captureStableManualCheckpoint`; OV07 verifier |
| DS2-41 | Plan Portability export/import password state and pending launcher password are wipeable arrays and cleared across failure/cancel/dispose | `PlanPortabilityScreen`; OV07 verifier |
| S12-08 | explicit snapshot/imported-annex deletion is reversible until physical deletion succeeds; final linked-document grant release is verified | Room portability/annex repositories + OV07 verifier |

## OV08 — SFLP product-state bridge & full-mesh mutation projection

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S4-01 | `RoomStudyFlowSflpProductStateBridge` diffs canonical Room rows and `StudyFlowPeerRuntime.flushProductState` journals normal DB mutations. | `SflpProductStateBridgeOv08Test` performs an ordinary SQLite/Room graph write and requires a product journal mutation. |
| S4-02 | `projectProductState` replays resolved SFLP row operations transactionally into Room and refreshes the baseline to prevent echo. | `SflpProductStateBridgeOv08Test` projects a remote operation and verifies no new local mutation is emitted. |
| S4-09 | Android app starts SFLP and runs a 15-second reconciliation coroutine; native TV runs its own 15-second scheduled reconciler. | OV08 verifier checks both production driver call paths; core/link tests cover membership semantics used by the drivers. |
| DS2-01 | `StudyFlowWorkspaceMembershipManager` is independent from trust; Android/TV/Desktop persist grants and sync RPC rejects non-members. | `StudyFlowWorkspaceMembershipOv08Test` proves access does not leak between workspaces and scoped revocation works. |
| DS2-56 | TV persistent actions are journaled as `tv.domain_action_requested` in `plan-*` workspaces and Android writable peers drain them through `StudyFlowTvDomainActionExecutor`. | OV08 source/compile validator requires TV publication, active-workspace reconciliation and Android canonical execution wiring. |

## OV13 — Android peer lifecycle & LAN discovery recovery

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S5-01 | `StudyFlowApplication` no longer starts generic SFLP sockets unconditionally; `AndroidStudyFlowLinkPeer` requires persisted user enablement before `start()` can bind TCP/WebSocket or advertise. | `StudyFlowOv13AndroidPeerLifecycleContractTest.applicationNoLongerStartsGenericSflpSocketsBeforeUserEnablement`; OV13 source verifier. |
| S5-02 | Peer lifecycle readiness and enablement now live in `AndroidStudyFlowLinkPeer` persisted lifecycle state, not only in the `Application` process. | `AndroidStudyFlowPeerLifecycleState`; `StudyFlowPeerLifecyclePolicy`; OV13 tests. |
| S5-05 | Android NSD failures preserve desired state and schedule recovery instead of leaving discovery disabled for process lifetime. | `AndroidStudyFlowNsdDiscovery.scheduleRestartAfterFailure`; OV13 source verifier. |
| S5-06 | Generic Android and phone→TV discovery now serialize and retry service resolution through queues instead of relying on one callback-only one-shot resolve. | `pendingResolveQueue` in both discovery adapters; OV13 source verifier. |
| S5-07 | LAN endpoint collection and NSD records preserve IPv6 link-local scope identifiers in endpoint hosts. | `StudyFlowOv13PeerLifecycleLanRecoveryTest.externallyReachableEndpointFilterRejectsLoopbackWildcardAndPreservesIpv6ScopeIds`. |
| S5-08 | Android pairing QR hints are generated only from externally reachable TCP/WebSocket endpoints and no longer fall back to `127.0.0.1`. | `AndroidStudyFlowLinkPeer.pairingOffer`; OV13 app contract test. |
| S5-10 | Generic SFLP socket/listener startup is permission-gated with `NEARBY_WIFI_DEVICES` before local LAN transports are opened. | `AndroidStudyFlowLocalNetworkPermission`; OV13 app contract test. |
| S5-11 | Failed SAF sync-folder configuration releases the just-taken persistable tree URI grant. | `StudyFlowApp` grant cleanup branch; OV13 app contract test. |
| DS2-07 | Closing the Android link no longer permanently detaches NSD from the discovery registry; restart re-adds the source and recreates the LAN beacon source. | `discoveryRegistry.add(nsdDiscovery)` in `start()`; OV13 verifier. |
| DS2-39 | Peer startup failures remain retryable through explicit lifecycle/backoff state after the app reaches Ready. | `StudyFlowPeerLifecycleRecovery`; `AndroidStudyFlowNsdDiscovery.lastFailureReason`; OV13 core tests. |

## OV14 — Android receiver, deep-link, widget & device-target durability

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S5-03 | `StudyFlowTvSessionCredential` now carries selected receiver service/host/port binding; phone client and controller reject authenticated sends to mismatched endpoints before emitting HMAC-authenticated LAN traffic. | `StudyFlowOv14ReceiverBindingJournalKeysTest.sessionCredentialBindsToTheSelectedReceiverEndpoint`; OV14 source verifier. |
| S5-04 | Reminder launch destination state is written onto the MainActivity Intent via `putReminderLaunchExtras`; MainActivity consumes actual intent extras before falling back to the in-memory handoff. | OV14 source verifier checks trampoline, parser and MainActivity intent consumption. |
| S5-09 | Reminder and widget receivers journal accepted asynchronous mutation work to persistent SharedPreferences before launching and settle each handle on completion/failure. | `StudyFlowReceiverMutationJournal`; OV14 source verifier. |
| DS2-06 | Widget `START_NEXT` filters active sessions to the configured widget plan before opening a session; unrelated active sessions no longer override the selected plan. | `StudyFlowWidgetActionPolicyOv14Test.startNextOnlyTreatsConfiguredPlanSessionAsActive`. |
| DS2-28 | Domain-action journal/SFLP keys use full SHA-256 digests from `StudyFlowTvDomainActionJournalKeys` instead of filtered/truncated action IDs. | `StudyFlowOv14ReceiverBindingJournalKeysTest.domainActionJournalKeysUseFullSha256InsteadOfSanitizedTruncation`. |

## OV15 — Android product-flow and UI/domain honesty

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S6-01 | `PracticeRunnerCard` now renders free-text entry for short-answer/essay questions and never falls back to `correctAnswer` as a single option. | `Ov15PracticeHonestyContractsTest.optionlessQuestionsDoNotExposeCorrectAnswerAsOnlyButton`; OV15 verifier. |
| S6-02 | `QuestionEditorState.validation()` requires MC correct answers to match visible options; the dialog remains open while the asynchronous save is in-flight and only closes on success. | `Ov15PracticeHonestyContractsTest.manualEditorValidatesMultipleChoiceCorrectAnswerAgainstOptions`; OV15 verifier. |
| S6-03 | Manual question saves and import previews run behind explicit validation/bounds and recoverable-operation surfaces before domain mutation. | `PracticeHonestyContracts`; `ImportHonestyContracts`; OV15 verifier. |
| S6-04 | Plan list opening is guarded by `canOpenPlanFromList`, so archived/trashed plans from All Plans cannot resolve into a workspace. | `Ov15PlanHonestyContractsTest.archivedPlansDoNotExposeOpenAction`. |
| S6-05 | `PlanWorkspaceScreen` keeps required schedule/syllabus gating but applies safe fallbacks to unrelated optional stream failures. | `Ov15WorkspaceHonestyContractsTest.optionalStreamFailuresDoNotBlockWholeWorkspace`. |
| S6-06 | Focus-session predictive back is no longer intercepted while sessions are loading, and the loading empty state has a working Back action. | `Ov15WorkspaceHonestyContractsTest.focusBackIsNotTrappedWhileLoading`. |
| S6-07 | A failed focus-session stream now displays `session_load_failed` instead of the missing-session copy. | OV15 verifier checks failed-state branch in `FocusSessionScreen`. |
| S6-08 | Archived plan cards no longer show the Open button and their card click is a no-op. | `canOpenPlanFromList`; OV15 plan test. |
| S6-09 | Plan cards show a truthful no-scheduled-activity message rather than fabricating a next activity. | `truthfulNextActivityAvailable`; OV15 plan test. |
| S6-10 | Exam countdowns use local calendar dates via `daysUntilExamLocalCalendar`. | `Ov15PlanHonestyContractsTest.examCountdownUsesLocalCalendarDayInsteadOfUtcFlooring`. |
| S6-11 | Manual import text/source name are `rememberSaveable`, so drafts survive Activity recreation. | OV15 verifier checks dialog state storage. |
| S6-12 | Pasted import text is bounded by `boundManualImportDraft`; PDF size/text extraction is bounded by `Ov15MaxPdfImportBytes` and `MaxTextBytes`. | `Ov15ImportHonestyContractsTest.manualImportDraftIsBoundedBeforePreviewAndSaveableRecreationState`; OV15 verifier. |
| S6-13 | `QuestionEditorDialog` iterates `QuestionType.entries`, exposing MC, true/false, short-answer, and essay editing. | `Ov15PracticeHonestyContractsTest.manualEditorRepresentsEverySupportedQuestionType`. |
## OV16 — Attention scheduler, quiet-hours & durable wakeups

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S7-01 | Fired snooze/break reminders are evaluated against ATT10 and re-added with the quiet-window end before delivery consumes them. | `StudyReminderWakeupContracts.rearmFiredQuietCandidate`; `StudyReminderWakeupContractsOv16Test.att10QuietHoursRearmSnoozeInsteadOfConsumingIt`. |
| S7-02 | Android reconciliation maps every planner candidate through `applyAuthoritativeQuietHours`, so ATT10 is authoritative even when legacy planner times differ. | `StudyReminderDeliveryCoordinator.reconcilePlanInternal`; OV16 verifier. |
| S7-03 | `MIRROR_IF_UNHANDLED` computes a mirror boundary and keeps the occurrence scheduled in `DELIVERY_PENDING` until escalation time. | `StudyReminderWakeupContracts.mirrorEscalationEpochMillis`; `StudyReminderWakeupContractsOv16Test.mirrorIfUnhandledProducesSecondWakeupBoundary`. |
| S7-04 | The reminder planner emits `due-future:*` candidates from the first upcoming review when no reviews are currently due. | `StudyReminderPlannerOv16Test.futureDueReviewsReceiveDurableWakeupCandidate`. |
| S7-05 | Android topology advertises notification/compact surfaces only when real surfaces exist; failed notification posts retry instead of marking delivered. | `AndroidStudyFlowAttentionRouterBridge.localCapability`; `permissionDeniedRetryCandidate`; OV16 verifier. |
| S7-06 | Session-ending, session-completed and break-completed candidates use exact alarm scheduling when exact alarms are allowed. | `StudyReminderWakeupContracts.requiresExactWakeup`; `scheduleExactOrShortestWindow`; manifest exact-alarm permission. |
| S7-07 | Widget snapshot projection keeps sub-minute upcoming activity boundaries instead of dropping them through a one-minute filter. | `StudyFlowWidgetSnapshotProjectorAtt03Test.keepsSubMinutePlannedBoundaryForExactFreshnessScheduler`. |
| S7-08 | Widget freshness alarms use exact near-term scheduling for active-session/minute-boundary refreshes with bounded fallback. | `StudyFlowWidgetFreshnessScheduler.schedule`; OV16 verifier. |
## OV17 — Attention delivery, widget resilience & remote policy

Closed: S7-09, S7-10, S7-11, S7-12, S7-13, S7-14, DS2-40. Evidence: per-widget failure isolation, self-healing invalidation observers, recipient ATT10 remote delivery enforcement, timezone refresh, expired snooze purge, SHA-256 notification IDs, and canonical remote tap routing.


## OV18 — Native TV durable canonical state & action journal

Closed: `S8-01`, `S8-02`, `S8-03`, `S8-07`, `S8-18`, `S8-19`, `DS2-27`, `DS2-44`.

Evidence: persistent TV domain-action outbox, durable local command sequencer, explicit outbox backpressure, removal of wall-clock stale rejection, persisted TV UI cursors, synchronous phone↔TV credential commits, and apply-time `verifyOv18NativeTvDurableStateActionJournal` validation.

## OV19 — Native TV practice, mock, review & schedule truth

Closed: `S8-04`, `S8-05`, `S8-06`, `S8-11`, `S8-12`, `S8-13`, `S8-14`, `S8-15`, `DS2-45`.

Evidence: full 20-question TV practice reachability, synthetic/non-persistable action fencing, live session/mock elapsed projection, real mock sections, canonical pause/resume persistence, actionable mock selection, distinct review-grade intent, formatted schedule labels, canonical source-ID commands, and apply-time `verifyOv19NativeTvPracticeMockReviewScheduleTruth` validation.

## OV20 — Native TV materials, focus, layout & projection resilience

Closed: `S8-08`, `S8-09`, `S8-10`, `S8-16`, `S8-17`, `S8-20`.

Evidence: TV-transferable material descriptors, phone-local URI rejection, renderer-backed PDF page counts, captured media playback position before play/pause/seek, dynamic live-View focus wiring, scroll-safe horizontal material/schedule/library rows, failure-isolated projection feeds, and apply-time `verifyOv20NativeTvMaterialsFocusProjectionResilience` validation.

## OV21 — Unified TV control, pairing, relay, cast & attention plane

Overlay 21 of 30.

Closed: `S9-01`, `S9-02`, `S9-03`, `S9-04`, `S9-05`, `S9-06`, `S9-07`, `S9-08`, `S9-09`.

Evidence: signed TV-plane envelopes on authenticated wire requests, receiver/session/operation/body-hash fail-closed admission, cast-scope and STOP_CAST binding, domain-action result binding, TV attention plane evidence, browser relay plane validation without bearer/controller tokens, and apply-time `verifyOv21UnifiedTvControlPlane` validation.

## OV25 — Web attention, push, Web-TV timing & route safety

Overlay 25 of 30.

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S10-05 | Browser PushSubscription remains `push-local-only` until same-origin relay registration succeeds; only acknowledged relay subscriptions can advertise `web_push` or designated-notifier ownership. | OV25 source verifier; normal/relay status contract. |
| S10-06 | Browser-TV timed mocks persist and restore versioned frozen-attempt checkpoints for answers, flags, cursor and timer/pause state; timed start is blocked without proven durable browser storage. | `ov25_attention_tv_safety_runner.mjs`; OV25 source verifier. |
| S10-07 | LAN `/api/tv/pair` failures are bounded independently by socket source IP and six-digit code, with 429/Retry-After lockout. | IP-sweep and rotated-IP/code behavioral regressions. |
| S10-08 | Service-worker attention drain is read/post/explicit-ack/per-id-delete instead of getAll/clear. | OV25 source verifier; acknowledgement wiring contract. |
| DS2-34 | Notification delivery validates sequence, self-target, route target/workspace, required capability, TV policy, source, occurrence target and schedule binding before sequence admission. | wrong-workspace then corrected-same-sequence behavioral regression. |
| DS2-35 | Web attention action and central transition paths reject terminal occurrence mutation. | terminal snooze/no-resurrection behavioral regression. |
| DS2-37 | Service-worker push/click navigation normalizes to an internal hash allowlist; malformed/absolute routes fall back to `#today`. | OV25 source verifier. |
| DS2-47 | Normal Web keeps an occurrence pending after an unavailable/failed system notification and records delivery only after the foreground React surface renders; push is recorded only after `showNotification()` succeeds. | invisible→foreground→ack behavioral regression; OV25 source verifier. |

## OV24 — Web persistence, bootstrap, portable import & secret hygiene

Overlay 24 of 30.

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S10-01 | IndexedDB save/clear returns a pending commit handle and StudyStore remains non-durable until transaction completion. | `ov24_persistence_runner.mjs`; OV24 verifier. |
| S10-02 | Full-state IndexedDB replacement uses same-transaction revision CAS and rejects stale tabs. | stale-tab CAS regression in `ov24_persistence_runner.mjs`. |
| S10-03 | IndexedDB open/recovery failures raise a visible startup error instead of silently substituting localStorage/sample state. | bootstrap-failure regression + source verifier. |
| S10-04 | BFCache page restoration restarts the Web attention runtime/bridges while preserved pages keep persistence open. | OV24 source verifier. |
| S10-09 | Plan/portable/encrypted file size gates run before full `text()`/`arrayBuffer()` materialization. | OV24 source verifier. |
| S10-10 | One-second focus ticking is scoped to live focus child surfaces instead of the root React App. | OV24 source verifier. |
| S10-11 | Material deletion pairs metadata mutation with local blob deletion and restores bytes after failed durable commit. | OV24 source verifier. |
| S10-12 | Common Web action runner is Promise-aware and waits for StudyStore durability before success copy. | OV24 source verifier. |
| S10-13 | Failed service-worker registration clears its memoized promise and is retryable. | service-worker retry regression. |
| S10-14 | localStorage compatibility mode advertises no attachment repository and hides local file upload. | OV24 source verifier. |
| DS2-04 | Portable export/replace always normalizes live focus state to idle and replace refuses to overwrite an active local focus session. | portable-focus regression. |
| DS2-36 | StudyStore `awaitPersistence()` observes the IndexedDB commit promise; save/clear success is never reported at enqueue time. | durability regression. |
| DS2-46 | SFLP localStorage access is capability-safe inside caught boot and denied localStorage returns null instead of pre-boot crashing. | denied-localStorage regression. |
| DS2-48 | Backup passwords use uncontrolled ephemeral refs and are cleared in `finally` after success/failure. | OV24 source verifier. |

## OV28 — Language review correctness & Desktop specialized parity

Status: closed by `studyflow_ov28_language_review_desktop_specialized_parity_v1`.

Findings closed:

- S11-14 — Free-form language review scoring rejects gibberish and ranks valid target usage higher.
- S11-15 — Spot-the-mistake rejects repeated mistakes and literal-only answers.
- S11-16 — Natural-usage multiple choice no longer fixes the correct answer in the first position.
- S11-19 — Desktop persists the full specialized graph, including snippets, mini-projects, runtime preferences, idiom cards, and vocabulary cards.
- S11-20 — Desktop programming execution honors exercise command templates and expected output.

Validation entrypoint: `scripts/validate_ov28_language_review_desktop_specialized_parity.sh`.


## OV29 — Behavioral validation, debug labs & release-truth gates

Overlay 29 of 30.

| Finding | Production closure | Behavioral evidence |
|---|---|---|
| S12-04 | Database Health Lab reports current-schema corruption with explicit fail-closed flags and cannot mark corrupted state as verified. | `DebugWorkbenchLabsOv29BehaviorTest`; OV29 verifier. |
| S12-05 | Backup/Restore Lab requires modern programming, language, Termux/review and annex/material table-family coverage before release readiness. | `BackupRestoreDrillSealOv29BehaviorTest`; OV29 verifier. |
| S12-10 | Debug Workbench navigation and route registration are `BuildConfig.DEBUG` gated. | OV29 source verifier. |
| S13-01 | Quality, Android and parity gates include all registered CMOV validators. | exhaustive Gradle/Devtool inventory. |
| S13-02 | CMOV final integration executes every CMOV validator and checks production flow wiring. | executable CMOV aggregate verifier. |
| S13-03 | Duplication inventory is guarded by exhaustive verifier inventory rather than stale token-only claims. | OV29 verifier. |
| S13-04 | Migration validation includes the 6→9 blind-spot contract before terminal seal. | OV29 verifier. |
| S13-05 | Five-client parity gate runs the Web production runner and semantic vectors. | PARITY04 runner + OV29 verifier. |
| S13-06 | REL-FINAL includes OV29 behavioral and exhaustive-inventory gates. | REL-FINAL source contract. |
| S13-07 | Exhaustive verifier inventory is regenerated from current Gradle/Devtool graph. | `verify_ov29_exhaustive_verifier_inventory.py`. |
| S13-08 | Roadmap truth is tied to validator exit codes, not checked-in status text alone. | OV29 remediation doc + verifier. |
| S13-09 | Web release integrity compares runtime/dist SHA-256 hashes. | `assert_runtime_dist_fresh.mjs`. |
| S13-10 | Debug Workbench tests exercise production lab semantics for database and backup behavior. | new behavior tests. |
| DS2-13 | Android SFLP/manual-folder gates remain behavior-backed in the optimized graph. | OV29 verifier. |
| DS2-30 | Web validation includes behavioral runners plus stale-dist detection. | OV24/OV25/Web freshness gates. |
