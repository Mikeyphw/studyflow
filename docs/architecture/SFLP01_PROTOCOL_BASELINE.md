# SFLP01 — StudyFlow Link Protocol v1 baseline

## Status

Implemented protocol contract. No transport, trust store, operation replication, or universal peer mesh is
claimed by SFLP01; those remain SFLP02-SFLP19 roadmap work.

## Purpose

SFLP is the platform-neutral wire contract shared by Android, Desktop, Web, Android TV, and Web TV. It
replaces pair-specific protocol semantics with one message vocabulary and capability model. Transport is
explicitly outside this milestone so the same envelope can later travel over native TCP/WebSocket, browser
WebSocket, a relay, or another compatible transport.

## Versioning

The first wire version is `1.0`.

Peers advertise one supported major plus a minimum and maximum minor version. Negotiation succeeds only
when the major matches and the minor ranges overlap. The negotiated version is the highest minor in the
overlap. A major-version mismatch is incompatible and must fail closed with
`link.incompatible_protocol`.

Within a negotiated major version, decoders may ignore unknown envelope fields so additive minor-version
extensions do not require a flag day. A sender must still advertise the capability required by an
operation before the receiver is expected to accept it.

## Envelope

Every message contains:

- protocol version;
- globally unique message ID;
- workspace ID;
- sender device ID;
- message type: request, response, event, or error;
- dotted lower-case message name;
- send timestamp in Unix epoch milliseconds;
- optional correlation ID;
- JSON-object payload.

Responses and errors require a correlation ID. Requests and events do not carry one.

The language-neutral schema lives at `protocol/sflp/v1/sflp-envelope.schema.json`.

## Stable identities

SFLP01 establishes wire-safe wrappers for:

- `StudyFlowWorkspaceId`;
- `StudyFlowDeviceId`;
- `StudyFlowEntityId`;
- `StudyFlowOperationId`;
- `StudyFlowMessageId`;
- `StudyFlowEntityRevision`.

SFLP02 will assign mutation-journal semantics to operation IDs and revisions. SFLP01 only guarantees their
wire identity and validation contract.

## Device descriptors and capabilities

Every peer may describe its client kind, display name, supported protocol range, and capabilities. Client
kinds are Android, Desktop, Web, Android TV, and Web TV.

The v1 capability vocabulary covers workspace read/write, plan and schedule access, study-session
execution/control/presentation, questions, reviews, goals, library/material presentation, documents,
Writing Workspace, analytics, backup/import, presentation source/sink, remote control, delegated text
input, output devices, and input-device classes.

Capabilities are dotted lower-case tokens such as:

- `workspace.read`;
- `session.execute`;
- `device.presentation_sink`;
- `text_input.provide`.

Unsupported operations must be rejected explicitly rather than silently ignored.

## Message families

SFLP01 reserves the baseline link messages:

- `link.hello` for protocol/capability negotiation;
- `link.ping` for liveness at the logical protocol level;
- `link.capabilities_changed` for a peer capability refresh.

Domain operation messages are intentionally deferred to SFLP02.

## Error model

The structured error payload contains a dotted error code, human-readable message, retryable flag, and
optional string details. Baseline codes include:

- `link.incompatible_protocol`;
- `link.unsupported_capability`;
- `link.invalid_message`;
- `link.unauthorized`;
- `link.conflict`;
- `link.unavailable`;
- `link.internal_error`.

Authorization and pairing semantics are not defined until SFLP04.

## Golden vectors

Canonical language-neutral examples live under `protocol/sflp/v1/golden/` for all four message families:
request, response, event, and error. The Kotlin `:core:link` tests consume mirrored resource copies and
must round-trip them semantically. Later TypeScript/Web and other client implementations must consume the
same root vectors rather than inventing platform-specific examples.

## Compatibility boundary

SFLP01 deliberately does not define:

- discovery;
- sockets or WebSockets;
- pairing and trust;
- encryption/session credentials;
- domain mutation journal entries;
- replication/reconciliation;
- handoff/presentation/control semantics beyond capability names.

Those boundaries keep protocol framing stable while later roadmap overlays implement the behavior in
small, testable layers.
