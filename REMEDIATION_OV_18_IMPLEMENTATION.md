# Remediation OV18 implementation

Overlay 18 of 30 implements native TV durable canonical state and action journal handling.

The TV domain-action outbox is now backed by persisted snapshots and fails with explicit backpressure when full. TV-local command IDs are reserved through a committed persistent sequence. Presentation, destination, search, and material-viewer cursors are checkpointed outside Activity memory. Local phone↔TV credentials are synchronously committed before success is reported, and the dedicated TV transport declares SFLP trust as the canonical boundary.
