# ATT11 notification reliability evidence and delivery audit

ATT11 turns the existing ATT01 Attention ledger into a deterministic reliability and audit projection. It does **not** add a second reminder database. Every metric, route row, platform diagnostic, and JSON/CSV export is derived from the canonical ledger entry plus the ATT10 policy audit and the latest ATT07 route audit stored on that entry.

## Evidence model

For each Attention occurrence ATT11 reports the current delivery state, schedule/update/delivery timestamps, delivery-attempt count, source/target device, ATT10 policy decision, and ATT07 owner/mirror route. The aggregate view separates terminal state from policy outcome so the product can distinguish, for example, a reminder that was deferred by quiet hours and later delivered from one that was never delivered.

The primary counters are total, active, delivered, missed, deferred, snoozed, dismissed, done, silent, compact-only, routed-to-phone, escalated-mirror, duplicate-suppressed, user-action-required, delivery attempts, audited routes, and average non-negative delivery delay. Category analytics provide total/delivered/missed/done counts without inventing new state.

## Route audit

Every ATT07/ATT10 routing decision can be recorded on the ATT01 ledger as compact audit metadata: decision time, route reason, owner, bounded mirrors, candidate/eligible counts, suppressed devices, policy outcome, and explanation. Reconciliation and lifecycle transitions preserve that metadata. This makes ownership decisions inspectable without replaying transient device presence after the fact.

## Platform reliability truth

| Client | ATT11 reliability interpretation |
| --- | --- |
| Android | Background-capable only when the current Android capability reports notification permission plus an Android alarm or system-notification surface. |
| Desktop | Runtime/tray/native-notification evidence is shown while StudyFlow Desktop is running. ATT11 does not claim daemon or launch-on-login delivery. |
| Web/PWA | Background-capable only when an **actual Web Push subscription** is represented. Notification API or Push API capability alone remains foreground-only. |
| Android TV | Read-only, foreground-only reliability projection. Closed-TV delivery belongs to a capable phone/Desktop/Web owner. |
| Web TV | Read-only, foreground-only projection of the normal Web ATT11 snapshot. |

Offline/stale devices are never presented as reliable current owners. A blocked or degraded surface still retains ATT01 evidence so reliability problems do not erase reminder history.

## Product surfaces

Android Reminders exposes aggregate evidence, current platform health, recent route audits, and user-initiated JSON/CSV copy actions. Desktop Settings exposes the same canonical projection and copy actions. The Web/PWA Notification Center exposes the same metrics, route rows, truthful Web Push status, and JSON/CSV export. TV surfaces intentionally remain read-only and direct export/closed-TV delivery to a capable companion client.

## Export semantics and privacy

JSON exports the full ATT11 evidence snapshot. CSV is a compact occurrence-oriented audit table with occurrence/workspace IDs, category/state, policy outcome, route reason, owner/mirrors, timestamps, and attempt count. Export is explicitly user initiated. Because IDs and routing metadata can reveal study activity and device topology, exported files/text should be treated as private user data.

## Compatibility rules

ATT11 preserves the ATT01 schema version and adds optional route-audit metadata so older persisted entries remain readable. ATT10 policy audit remains optional. ATT07 single-owner/bounded-mirror behavior is unchanged. ATT11 analytics are projections only and must never mutate canonical lifecycle state merely to make a metric look complete.
