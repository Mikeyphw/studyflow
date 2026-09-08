# TVOV24 — Android TV real cast/state synchronization

TVOV24 closes the presentation-wire gap left intentionally open by TVOV23. The Android phone controller now starts an explicit live cast for the default StudyFlow plan, subscribes to the canonical TVOV21 repository-backed presentation feed, and delivers the latest typed snapshot over the already-paired/authenticated TVOV22 local link.

The synchronizer serializes delivery so a newer repository snapshot supersedes older pending work. The TV returns a minimal presentation receipt containing the exact accepted snapshot id and generation time; the phone reports LIVE only after that receipt matches the snapshot it sent. Continuity refresh also carries the current receipt, allowing the controller to detect drift and request a resync.

Discovery/reconnect does not create a second cast stack. If the TV disappears while cast is active, the latest canonical snapshot remains queued in memory and is resent when the paired receiver is rediscovered. Explicit STOP_CAST clears the TV presentation inbox back to the truthful waiting state. If STOP_CAST is requested while the TV is unavailable, the phone keeps a pending remote stop and retries after rediscovery while the controller remains active.

The transport remains the bounded local TCP/NSD layer from TVOV22. No Cast SDK, MediaRouter, raw database rows, Room entities, or arbitrary UI trees are introduced. Domain-action persistence convergence remains TVOV25; TVOV24 synchronizes canonical presentation state but does not pretend the TV-local interaction reducers already write back to the phone-owned StudyFlow repositories.

The TV package advances to version code 24 / `0.1.0-tv24`.
