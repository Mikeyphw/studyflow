# ATT01 — Unified Attention Domain + SFLP Correctness Foundation

ATT01 establishes the durable cross-platform attention foundation used by the later StudyFlow notification,
reminder, widget, and device-routing overlays. It intentionally does not claim that every platform already has a
native user-facing reminder surface; ATT02-ATT06 own those surfaces.

## Delivered scope

- Shared `StudyFlowAttentionOccurrence` domain with stable occurrence IDs, categories, priorities, actions,
  delivery policies, quiet hours, targets, expiry, payload, and lifecycle state.
- Durable `StudyFlowAttentionLedger` with persisted occurrence state, source sequence watermarks, duplicate
  suppression, legacy delivered aliases, bounded retention, invalid-state recovery, lifecycle validation, and
  diagnostics.
- SFLP18 schedule changes now retain the complete reminder change as `attentionScheduleChange` while preserving
  the legacy `notificationScheduleSync = "sflp18"` marker. `attentionSchema = "v1"` signals the lossless form.
- SFLP notification delivery can carry a canonical attention occurrence. Legacy notification payloads are still
  accepted and deterministically projected into an occurrence.
- Android, Desktop, Web/PWA, and Android TV persistence adapters are included. Web uses localStorage with an
  in-memory fallback where storage is unavailable.
- Android startup migrates existing scheduled reminder bookkeeping and legacy delivered occurrence IDs into the
  attention ledger without destroying the existing reminder store.
- Android Reminder/Notification Lab exposes attention-ledger diagnostics.
- Golden SFLP vectors and cross-runtime tests cover lossless schedule semantics and durable delivery dedupe.

## Compatibility

Older SFLP18 peers continue to see the same operation types and the legacy `notificationScheduleSync = "sflp18"`
marker. New peers prefer `attentionScheduleChange` when present. Unknown attention fields are ignored by the
existing tolerant JSON configuration.

## Product truth

ATT01 is infrastructure and correctness. It does not claim native background reminder delivery on Desktop, Web,
Android TV, or Web TV. Later ATT overlays wire platform-native delivery and UX on top of this shared state.

## Validation

The focused gate is `verifyAtt01UnifiedAttentionFoundation`. It covers shared Kotlin tests, diagnostics tests,
Desktop persistence tests, Android/Android TV unit-test compilation paths, Web TypeScript/runtime parity and ATT01
behavior tests, plus the ATT01 source/contract verifier.

During the ATT01-ATT11 roadmap campaign the overlay may be applied with `--no-validate`; the validation task stays
embedded in the artifact and wired into repository validation for later focused/final gates.
