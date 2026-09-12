# ATT11 — Notification Reliability Evidence / Delivery Audit

ATT11 adds a shared reliability-evidence model on top of the ATT01 ledger and persists the latest ATT07/ATT10 route decision as optional audit metadata on each canonical ledger entry.

Delivered work:

- shared Kotlin evidence, category analytics, platform diagnostics, route-audit model, and deterministic JSON/CSV export;
- ATT01 ledger preservation/recording of route audit without creating a parallel store;
- Android delivery-router recording plus Reminders reliability/audit UI and copyable JSON/CSV exports;
- Desktop route recording, runtime reliability snapshot, Settings diagnostics, and JSON/CSV copy actions;
- Web/PWA parity with actual-subscription-only Web Push reliability, route audit recording, generated runtime/dist parity, Notification Center analytics, and export actions;
- Android TV and Web TV truthful read-only foreground reliability projections;
- regression tests, product documentation, capability-matrix updates, and `verifyAtt11NotificationReliabilityEvidenceAudit` validation wiring.

Canonical design and product semantics are documented in `docs/product/NOTIFICATION_RELIABILITY_EVIDENCE_AUDIT.md`.
