# SFLP04 + SFLP05 — device identity, trust, pairing, and transport foundation

## Scope

This milestone completes the platform-neutral identity/trust layer and transport/discovery abstraction required
before StudyFlow can become a multi-peer workspace network. It does not yet claim that Android, Desktop, Web,
Android TV, or Web TV have been bound to the new APIs; those bindings remain SFLP06-SFLP12 work.

## SFLP04 delivery

- Stable `StudyFlowDeviceId` ownership through `StudyFlowDeviceIdentityStore` and
  `StudyFlowDeviceIdentityManager`. Platform adapters choose the durable storage backend; refreshing a device
  name/capability set does not change the device identity.
- Remembered `StudyFlowTrustedPeerRecord` entries through `StudyFlowTrustStore`, including explicit trusted and
  revoked states, last-seen updates, peer-descriptor refresh, forget, and revocation without retaining the secret.
- Six-digit pairing codes with 120-second maximum offers, random 24-byte nonces, a ten-failed-attempt lockout,
  random 32-byte shared trust secrets, explicit cancellation, and self-pairing rejection.
- QR pairing using the same canonical pairing request as manual code entry. The QR carries the pairing id,
  nonce, code, and transport endpoint hints; it does not create a second pairing protocol.
- Reciprocal trust grants so both peers remember the same shared secret after explicit user-authorized pairing.
- One-time reconnect challenges with 30-second default lifetime and HMAC-SHA256 possession proofs. Successful
  proofs update last-seen state; challenges are consumed and cannot be replayed. Revoked peers cannot reconnect.
- Language-neutral JSON schema/golden fixtures for pairing, grant, reconnect, and transport endpoint contracts.

## SFLP05 delivery

- `StudyFlowTransport`, `StudyFlowListeningTransport`, `StudyFlowConnection`, and
  `StudyFlowConnectionListener` separate SFLP messages from physical transport APIs.
- `StudyFlowTransportRegistry` resolves transports by canonical scheme rather than client type.
- Canonical endpoint support for `sflp+tcp`, `ws`, `wss`, `sflp+relay`, and loopback/reference transports.
- Manual endpoint parsing for host:port, bracketed IPv6, TCP URIs, WebSocket URLs, and relay URLs.
- Real native LAN TCP with bounded connect/read timeouts, TCP no-delay, server/listener support, and the same
  four-byte big-endian length framing used by the existing native TV link. SFLP v1 caps a frame at 1 MiB.
- `StudyFlowAdapterTransport` provides a real adapter seam for browser/native WebSocket and future relay dialers
  without importing Android, browser, Binder, NSD, or service APIs into `:core:link`.
- `StudyFlowManualDiscovery` and `StudyFlowCompositeDiscovery` provide explicit endpoint discovery and
  deterministic de-duplication.
- `StudyFlowMdnsContract` defines the generic `_studyflow._tcp.` advertisement/TXT shape. Android NSD and
  Desktop mDNS adapters can translate this record without changing SFLP semantics.

## Security and truthfulness

The trust secret is deliberately a storage-layer concern: platform implementations should use their secure
storage facilities when available. The core does not pretend that a transport is encrypted merely because the
peer is trusted. WSS/relay security is owned by their transport adapters; plain LAN TCP is authenticated only
when SFLP reconnect/pairing semantics are used above it.

The old Android-TV `_studyflow-tv._tcp.` implementation remains in place until the SFLP11 migration. SFLP05
adds the generic contract; it does not silently rewrite the production TV path before its client-integration stage.

## Validation

`verifySflp04Sflp05IdentityTransport` depends on the SFLP01-SFLP03 seals and `:core:link:test`. Tests cover:

- stable identity refresh;
- code and QR pairing;
- offer expiry and ten-attempt lockout;
- reciprocal remembered trust and fresh re-pairing after revocation;
- one-time reconnect proof and revocation refusal;
- endpoint parsing, frame bounds, and real loopback TCP traffic;
- WebSocket/relay adapter registration;
- mDNS record projection/resolution and composite discovery de-duplication;
- golden JSON contract round-trips.
