# ATT01 unified attention domain

StudyFlow models a reminder as semantic attention state rather than as a platform alarm. One occurrence owns a
stable ID, workspace/schedule identity, category, priority, schedule/expiry, timezone and quiet-hour context,
available actions, delivery policy, targets, and payload. Platform alarms, system notifications, tray messages,
service-worker notifications, and TV banners are delivery mechanisms built on top of that state.

`StudyFlowAttentionLedger` is the durable receipt and lifecycle journal. It stores the latest occurrence state and
per-source SFLP sequence watermark, rejects already handled occurrences, retains migrated legacy delivered IDs,
and recovers fail-safe to an empty current-schema snapshot if persisted data cannot be decoded. Platform adapters
persist the encoded shared snapshot without redefining its semantics.

SFLP18 schedule operations remain backward recognizable through `notificationScheduleSync = "sflp18"`. ATT01 adds
`attentionSchema = "v1"` and `attentionScheduleChange`, which contains the complete serialized reminder change.
This prevents the previous lossy conversion that discarded reminder time, quiet hours, payload, and future
attention fields. Legacy peers can ignore the new fields; ATT01-aware peers can round-trip the complete change.

ATT01 also makes a strict distinction between SFLP message acceptance and user-facing delivery. A client may
synchronize and durably acknowledge an attention occurrence without yet owning a native background reminder
surface. ATT02-ATT06 deliver those platform experiences.

## ATT11 reliability/audit extension

ATT11 keeps ATT01 as the canonical store and adds optional route-audit metadata to each ledger entry. The audit records the most recent ATT07 owner/mirror decision, counts and explanation alongside the existing optional ATT10 policy audit. Reliability analytics and exports are derived views over this ledger; they do not introduce a second persistence model or advance lifecycle state.
