# OV05 — Duplication, migrations & snapshot determinism

Overlay **5 of 30** closes S2-A1, S2-05, S2-06, S2-07, S2-11 and S2-13.

- Study Plan duplication now copies the language graph and Termux request/result history, including parent-scoped tables that do not carry `studyPlanId` themselves.
- Practice/mock `configurationJson` is rewritten after duplication so `questionIds`, frozen question snapshots and answer records point at the copied Question IDs.
- Migration 2→3 normalizes linked target duration to the canonical 5..720-minute domain, and schema 15 explicitly replaces managed triggers so upgraded databases receive the same bound.
- Historical schema snapshots 7 and 8 are checked in and covered by sequential 6→7→8→9 migration tests.
- Logical snapshots exclude Room/Android metadata and the app-owned FTS virtual/shadow table family, and every source table is serialized in primary-key order.
- Annex orphan cleanup counts bytes only after the physical delete succeeds.

Run `scripts/validate_ov05_duplication_migrations_snapshots.sh` after applying the overlay.
