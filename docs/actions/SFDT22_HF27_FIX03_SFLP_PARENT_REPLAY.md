# StudyFlow HF27 FIX-03 — SFLP replay repairs FK-cascading plan replacement

## Observed failure

DIAG-01 and DIAG-02 on Android API 36 showed plan settings present after a
successfully committed topic transaction, and again in the independent
`after_topic_action` read. DIAG-02 then recorded `SQL_PLAN_MUTATION/sql_replace`
followed by settings missing in the production Room handle and both independent
readers. The failing UI test was `boundary_after_topic_missing`. Prior Room
regressions were green. Receipt: Actions run 42754951253, exact source SHA
47066d15acc5e897ebee22582036876ef02cd573.

## Confirmed concrete writer and fix

`RoomStudyFlowSflpProductStateBridge.applyOperation` issued a generic
`INSERT OR REPLACE INTO ${quote(table)}` for every SFLP row upsert. This
included **existing** `study_plans` rows. SQLite REPLACE deletes conflicting
rows before inserting and can fire FK `ON DELETE CASCADE` into
`study_plan_settings`, subjects and topics. The SFLP background reconciler
runs independently from the topic-save coroutine. Ordinary plan update paths
already used `@Update` correctly, so their existing safeguard stays intact.

SFLP now resolves and validates the complete schema primary key inside its
existing transaction. Existing rows use `UPDATE ... WHERE exact full PK` with
PK fields excluded from SET. Absent rows use plain `INSERT` (constraint
failure, never implicit deletion). A snapshot of the preexisting plan's exact
personalized settings row is verified to match immediately after an existing
parent-row update. Explicit tombstones still use exact-key DELETE. Peer-supplied
PK fields must match the upsert's value fields and known table schema. Unknown
columns, mismatched PKs, and null PKs are rejected, with the entire SFLP
replay batch rolled back; arbitrary payloads are not interpolated into SQL.

**No default regeneration, destructive migration, suppressed exception, or
automatic schema change is introduced.** DIAG-01 and DIAG-02 monitoring
remain in place until the hosted workflow validates the fix.

## Mandatory tests and acceptance

Three new real Android test methods exercise the production
`createStudyFlowDataLayer().sflpProductStateBridge` against the actual Room
SQLite file on a disposable hosted emulator: (1) replay of an existing plan
with customized settings and owned subject/topic, including cold Room reopen
and duplicate-operation idempotence, (2) malformed/mismatched peer keys
rejected without local mutation, (3) ordinary new-parent INSERT and explicit
DELETE/tombstone support. A fixture-specific plan ID is deleted during
cleanup. Neither this nor the existing two independent Room tests counts as
one of the five real Compose journeys.

The exact-SHA focused Android workflow and full Android acceptance runner
require the additional three JUnit results. No raw instrumentation logs are
uploaded. Historical source hash assertions and privacy/Room contracts must
pass unweakened. The full gate **still requires five real UI tests, seven
unique screenshots, an actual new PID after force-stop, and mandatory source
and emulator success**, on a reviewed PR SHA.

An offline launcher pass is not Android qualification. Do not merge PR #15,
release, or remove diagnostic hardening until hosted evidence is reviewed.
