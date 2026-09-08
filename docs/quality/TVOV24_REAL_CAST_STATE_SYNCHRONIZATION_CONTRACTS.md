# TVOV24 real cast/state synchronization contracts

- Live cast starts and stops only from an explicit user-present controller action.
- The cast source is the canonical TVOV21 repository-backed typed presentation feed; no raw database rows, Room models, paths, or Android UI trees cross the wire.
- Presentation delivery reuses the authenticated TVOV22 local transport and does not add Cast SDK, MediaRouter, a second socket stack, or background discovery.
- Snapshot sends are serialized and always converge toward the latest generated presentation snapshot.
- The TV acknowledges the exact retained snapshot with a small presentation receipt; LIVE must not be reported from a blind successful socket write.
- Receiver rediscovery/reconnect requests a resync of the latest snapshot while cast remains enabled.
- Explicit STOP_CAST clears the TV presentation inbox to its waiting state. A stop requested while disconnected remains pending and is retried after receiver rediscovery while the controller is alive.
- Expired/invalid pairing credentials terminate live synchronization and require explicit re-pairing.
- TVOV25 remains responsible for domain truth and persistence convergence of TV-originated/remote interaction commands.
