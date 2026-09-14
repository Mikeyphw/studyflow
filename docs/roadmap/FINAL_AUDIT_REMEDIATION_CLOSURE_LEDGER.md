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
