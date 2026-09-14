# Remediation OV13 — Android peer lifecycle & LAN discovery recovery

Overlay 13 of 30 moves the campaign into the Android lifecycle and attention wave by fixing the peer startup/discovery defects that remained after the SFLP protocol overlays.

## Scope

Owned findings: `S5-01`, `S5-02`, `S5-05`, `S5-06`, `S5-07`, `S5-08`, `S5-10`, `S5-11`, `DS2-07`, `DS2-39`.

## Product changes

- Generic Android SFLP startup is no longer an unconditional `Application` side effect. Startup is controlled by persisted user opt-in plus the platform local-network permission gate.
- `AndroidStudyFlowLinkPeer` exposes explicit lifecycle state, enable/disable APIs, permission-gated start, safe `startIfUserEnabled()`, and close/restart behavior that reattaches discovery sources.
- Generic Android NSD and phone-controller TV receiver discovery both preserve desired state, serialize resolution, retry resolution failures, and schedule rediscovery after transient NSD errors.
- LAN endpoint generation filters loopback/wildcard hosts while preserving IPv6 scope identifiers such as `%wlan0` in the endpoint host.
- Pairing QR hints now fail closed when no externally reachable endpoint exists instead of advertising `127.0.0.1` to another device.
- The manual sync folder picker releases a persistable SAF grant if provider configuration fails after the grant is taken.

## Validation

`verifyOv13AndroidPeerLifecycleLanRecovery` compiles/tests the Android app and link modules, then executes `scripts/validate_ov13_android_peer_lifecycle_lan_recovery.sh`. The validation hook invokes the Gradle task automatically during overlay application and does not stop Gradle daemons.
