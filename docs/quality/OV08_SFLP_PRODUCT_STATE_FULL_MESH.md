# OV08 — SFLP product-state bridge and full-mesh mutation projection

Overlay 8 of 30 closes S4-01, S4-02, S4-09, DS2-01 and DS2-56 at the native protocol/data boundary.

The Android data owner now has a generic row-delta bridge over the canonical Room graph. A baseline scan records stable primary-key state; ordinary repository writes become `product.row_upsert` / `product.row_delete` journal operations on the canonical `product-state-v1` workspace. Reconciled operations are transactionally projected back through the same Room database and the baseline is refreshed after projection to prevent echo loops.

Trust and workspace authority are separate. Production Android, Android TV and Desktop peers persist explicit workspace membership. Sync RPC rejects an authenticated trusted peer that lacks writable membership for the requested workspace. Revocation removes the peer's workspace grants as well as transport/queue/topology state.

Android and Android TV now have continuous 15-second reconciliation drivers. Android registers active plan workspaces and the product-state workspace; TV registers a plan workspace when it emits a persistent action. Native TV persistent actions are promoted into the shared SFLP journal as `tv.domain_action_requested` instead of depending exclusively on the legacy phone-poll outbox. Android writable peers drain those operations through the existing canonical idempotent domain-action executor, after which the resulting Room mutation is picked up by the product-state bridge.

The legacy local TV outbox remains a compatibility transport during the campaign, but it is no longer the sole persistence path. Web/native transport interoperability remains owned by OV09; OV08 establishes the shared journal/membership semantics it must consume.
