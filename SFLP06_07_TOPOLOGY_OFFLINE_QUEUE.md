# SFLP06 + SFLP07 — multi-peer topology and offline queue/reconnection

## Delivered

### SFLP06 multi-peer workspace topology

- Simultaneous peer registry for one workspace.
- Direct, learned, and relay route records with deterministic route selection.
- Connection-health tracking with connected/degraded/disconnected states and RTT/failure data.
- Original-source-preserving routed envelopes.
- Explicit visited-device traces and hop limits.
- Bounded message-ID deduplication for loop prevention and replay suppression.
- Targeted route selection that excludes already visited next hops.
- Broadcast forwarding that never reflects messages to visited peers.

### SFLP07 offline queue and reconnection

- Persistent outgoing queue port with an in-memory reference implementation.
- Per-peer/per-operation idempotent queue keys.
- Bounded exponential retry scheduling without dropping pending operations.
- Frontier-based acknowledgement: queue entries are removed only when peer truth covers them.
- Bidirectional reconnect reconciliation through the SFLP03 engine.
- Stale-peer catch-up even if the optimization queue is empty or lost.
- Interrupted transfer recovery from receiver frontier truth.
- Response-loss safety: an already-committed remote batch is not duplicated on reconnect.
- Batch-size and reconciliation-round bounds.
- Platform-neutral scheduling hints via next-attempt timestamps; Android/Desktop/Web bindings remain later work.

## Invariants

The journal remains canonical. The outgoing queue is only a durable delivery/retry accelerator; reconnect always
recomputes missing operations from vector frontiers, so queue loss cannot silently lose canonical state.

Routing is source preserving. Forwarding devices update only the route trace; they do not rewrite the original
SFLP envelope sender. A device may not revisit a route trace, and a message ID is accepted at most once within
the bounded dedupe window.
