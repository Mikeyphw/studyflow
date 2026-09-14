# OV10 — SFLP trust, pairing, authorization & transport security

OV10 closes S4-10, S4-12, S4-13, S4-14, S4-17, S12-01, S12-07, DS2-08, DS2-09, DS2-17, DS2-18 and DS2-19.

Pairing QR v2 carries an ephemeral P-256 host key and explicit creation/expiry. The client contributes an ephemeral key; the long-term reconnect secret is AES-256-GCM encrypted under ECDH/HKDF context bound to pairing id, host, target and nonce. The grant contains no raw trust secret. Offer validation/consumption is atomic. Native RPC rejects incompatible protocol envelopes, mismatched pairing identities/workspaces and capability-ineligible sync/control traffic. Remote retryable errors now drive queue retry semantics.

The Web runtime fails closed without cryptographic randomness, validates v2 QR lifetime and native grant envelope/target/host key, uses WebCrypto ECDH/AES-GCM, stores only opaque `trustKeyId` references in localStorage while keeping non-extractable HMAC `CryptoKey` objects in IndexedDB, migrates legacy raw-secret records fail-closed to revoked state, keeps Web and Web-TV storage/identity namespaces distinct, and preserves remote retryable classification through the typed RPC error path.

Validation is integrated into the Devtool artifact hook and intentionally does not invoke or stop Gradle daemons. It checks owned finding-to-code evidence, TypeScript/runtime parity, and OV10 browser security behavior. Native compilation/unit coverage is retained in `StudyFlowOv10SecurityTest` for the campaign's later Gradle gate/final seal.
