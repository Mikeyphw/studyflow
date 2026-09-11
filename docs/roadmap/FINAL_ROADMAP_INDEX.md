# Final Roadmap Index

This file preserves the historical Phase 29 index contract while recording the current delivered
StudyFlow product roadmap through Phase 64.

- Phase 0-1: product contract and Android foundation.
- Phase 2: domain model and Room persistence.
- Phase 3: Study Plan lifecycle.
- Phase 4: portable `.studyplan` packages.
- Phase 5: subjects and nested topics.
- Phase 6: schedules and strategy.
- Phase 7: sessions and timer execution.
- Phase 8: question bank, practice tests, and mock exams.
- Phase 9: reviews, flashcards, and spaced repetition.
- Phase 10: library resources and annex metadata.
- Phase 11: goals, notes, and progress command center.
- Phase 12: analytics and insights.
- Phase 13: settings and backup access.
- Phase 14: UX polish and accessibility baseline.
- Phase 15: release-readiness baseline.
- Phase 16: roadmap and validation truth cleanup.
- Phase 17: annex file storage and portability.
- Phase 18: material viewer and media playback.
- Phase 19: practice execution engine.
- Phase 20: wrong-answer notebook and remediation loop.
- Phase 21: reminders and widgets foundation.
- Phase 22: global search and smart recommendations.
- Phase 23: privacy, encryption, and backup hardening.
- Phase 24: offline local checkpoint foundation.
- Phase 25: gap-closure seal.
- Phase 26: release validation seal.
- Phase 27: runtime smoke workbench.
- Phase 28: backup/restore drill seal.
- Phase 29: release-candidate source polish.
- Phase 30-37: product truthfulness, interaction/documentation cleanup, release identity, and device seal framework.
- Phase 38-47: complete UI/UX V2 workspace migration.
- Phase 48-49: readiness and adaptive reschedule.
- Phase 50-51: weakness drills and question import.
- Phase 52-53: Mock Exam Studio and widget actions.
- Phase 54-55: reminder reliability and evidence timeline.
- Phase 56-57: post-V2 integration and device-readiness polish.
- Phase 58-59: local-first checkpoint queue and weekly coach.
- Phase 60-61: smart content cleanup and local exam report export.
- Phase 62: onboarding and sample plan wizard.
- Phase 63: exam runbook.
- Phase 64: preview-first seven-day study sprint.
- No Phase 65 feature is defined; CPOV00-CPOV13 are public-exam specialization overlays over the Phase 64 product baseline.

Cloud sync overlay roadmap:

- CSOV00-CSOV01: local-first optional-cloud language and settings foundation.
- CSOV02: encrypted sync checkpoint bundle format.
- CSOV03: provider abstraction.
- CSOV04-CSOV11: optional onboarding, manual provider transfer, merge/conflict review, background sync, and final seal.

CSOV02-CSOV03 were the source foundations. Later CSOV04-CSOV15 overlays are now present in the
repository, including real manual SAF-folder transfer and the optional WebDAV / Nextcloud provider lane.
Fresh installs still remain sync-off by default.

For current cross-platform scope, use `docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md`; this index is
historical roadmap sequencing. The final target-machine validation and evidence pass remains required
before product sealing.


## Current post-SFLP cross-platform completion track

The Android numbered product roadmap remains capped at Phase 64. Current cross-platform work is tracked
separately in `docs/roadmap/CROSS_PLATFORM_PARITY_CONNECTIVITY_ROADMAP.md` and the machine-readable
`docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json`. The ordered remaining implementation/evidence track is:

1. **DOV08-DOV24 — COMPLETE** — applicable Desktop parity closed by the DOV24 acceptance seal.
2. **TVOV27-TVOV31 — COMPLETE** — native-TV gaps closed by the Android-TV/Web-TV common-fixture seal.
3. **WEB20 — COMPLETE** — shared language-neutral semantic golden vectors across Kotlin, TypeScript, and Desktop.
4. **PRESENCE01 — COMPLETE** — optional ephemeral peer/activity presence distinct from transport health and replication correctness.
5. **PARITY04 — NEXT** — canonical five-client full-mesh/product-parity acceptance seal.
6. **REL-FINAL evidence execution** — run the already-implemented REL01-REL08 release machinery against the
   completed product, retain signed artifact/device/restart/restore/interoperability evidence, and close only when
   every required observation is pass rather than pending/blocked.

There is no SFLP25 and no Phase 65 implied by this list. SFLP24 remains the terminal SFLP protocol seal;
PARITY04 owns the old draft's full-mesh acceptance promise.


Post-SFLP release operation track:

- REL01: target-machine release validation runner for signed APK/AAB, connected Android, restore/restart,
  and explicit manual device-evidence collection. This is not Phase 65 and does not extend SFLP beyond
  SFLP24.

## REL02 release evidence exporter

REL02 follows REL01 with a final evidence report exporter and failure triage bundle. It does not add product scope; it organizes the evidence required to decide whether the existing release gates are pass, pending, or blocked.

## REL03/REL04 artifact identity and rollout manifest

REL03/REL04 is a release-operation overlay after SFLP24 and REL02. It binds exact signed artifacts, install-smoke observations, and channel rollout readiness into report files for final review.

## REL05/REL06

REL05 exports public/internal release notes from changelog evidence. REL06 builds the rollback package manifest and command template from REL01-REL05 evidence. This continues the release-operation track after REL03/REL04 and before final release-ops consolidation.

## REL07/REL08

REL07 closes the release operator checklist gate for REL01 through REL06 evidence. REL08 creates the final release-ops evidence seal and bundle. This is the final release-operation track gate after SFLP24 and REL01-REL06.
