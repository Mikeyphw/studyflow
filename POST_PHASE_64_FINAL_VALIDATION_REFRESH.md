# Post-Phase-64 Final Validation Refresh

## Purpose

Phase 63 and Phase 64 were intentionally delivered as an intermediate feature overlay after the
original OV-13 release-seal work. This refresh is not a Phase 65 feature. It updates the final
validation chain so the current Phase 64 repository, rather than the older Phase 62 baseline, is the
artifact that earns target-machine evidence.

## Automated Release journey extension

`StudyFlowFullJourneyAcceptanceTest` now gives the acceptance plan an exam date, creates a real future
unlocked planned activity, derives `ExamReadinessScore` from production repositories, builds the
Phase 63 exam runbook, previews and applies the Phase 64 sprint through `ManageStudySchedule`, and
then includes that mutated schedule in the real export/restore journey.

The restart phase re-reads the same planned-activity ID from Room and proves the sprint mutation is
still present after the forced process restart. Both instrumentation phases emit explicit
`studyflowPhase63_64...Verified=true` status markers; `record_full_journey_evidence.py` refuses to
record Phase 63/64 PASS unless those markers are present. The authoritative final verdict requires
the initial marker, the post-restart marker, and the aggregate `phase63_64Verified=true` result.

`WorkspacePhase63_64ConnectedTest` also runs inside `:feature:workspace:connectedDebugAndroidTest`.
It builds the runbook and sprint from owned evidence and applies the sprint through the real
`ManageStudySchedule.applyVersionedPreview` boundary, so the Workspace connected gate cannot pass
without exercising Phase 63/64 behavior.

## Roadmap reconciliation

`docs/roadmap/IMPLEMENTATION_ROADMAP.md` and `docs/roadmap/FINAL_ROADMAP_INDEX.md` now record the V2
and post-V2 sequence through Phase 64. The next roadmap action is final target-machine validation; no
Phase 65 product feature is currently defined.

## Validation boundary

The overlay construction audit is source/static only. The overlay is intended to be the last
implementation overlay and its Devtool artifact requires `verifyFinalReleaseAutomatedGate`. That
real target run requires external Release signing credentials and exactly one connected Android
device. The separate manual device observations and `verifyFinalReleaseVerdict` still remain required
before `productSealed=true`.
