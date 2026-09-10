# SFLP18 + SFLP19 — Notification/scheduling sync and media/material streaming handoff

SFLP18 adds peer-neutral notification and schedule reminder sync. Android, Desktop, Web,
Web TV, and Android TV receive targeted reminder/review/session notifications through SFLP
messages instead of one platform owning notification truth.

SFLP19 adds read-only material streaming handoff. A peer can offer PDF/image/audio/video/html
material as a bounded chunked stream, and receiver peers acknowledge monotonic chunks by stream,
chunk index, byte offset, and content hash. TV-class peers may present/read streams, but they do not
receive library import or workspace write authority.

## Guardrails

- `notification.deliver` is target-gated and route capability-gated.
- `notification.delivery_ack` reports accepted, duplicate, expired, or rejected.
- `schedule.notification_change` converts reminder/policy changes to normal SFLP domain operations.
- `material.stream_offer` requires read/presentation capabilities only.
- `material.chunk` is monotonic by chunk index and byte offset.
- `material.chunk_ack` records accepted, duplicate, rejected, or complete.
- Android TV and Web TV reject material offers requiring `library.import` or `*.write` authority.

## Validation

Run `verifySflp18Sflp19NotificationMediaSync` on the final validation pass. Intermediate overlay
application remains safe with `--no-validate`.
