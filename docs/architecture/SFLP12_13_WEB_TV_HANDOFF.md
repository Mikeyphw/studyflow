# SFLP12 + SFLP13 Web TV connectivity and activity handoff

This overlay completes SFLP12 Web TV connectivity and SFLP13 activity handoff on top of the
SFLP01-SFLP11 foundation.

## SFLP12 Web TV connectivity — delivered

- Web TV advertises the `web_tv` client kind and presentation-sink capability.
- Web TV offers a receiver QR/code payload marked `studyflow.sflp.web_tv.receiver.v1`.
- Receiver offers carry workspace identity, endpoint hints, expiry and the same SFLP trust flow.
- Web TV accepts projection snapshots only when the source declares presentation-sink and D-pad
  capability requirements.
- Web TV accepts remote commands only when they target the receiver device.
- Web TV remains a peer in the SFLP mesh; it is not a phone-owned spoke.
- Browser persistence, manual/WebSocket endpoints, queue/frontier state and offline cache continue
  to be used from SFLP10.

## SFLP13 activity handoff — delivered

- Activity IDs, handoff IDs, cursors, snapshots, offers, accepts, resume events and rejections have
  typed language-neutral models.
- Handoff messages use canonical SFLP names: `activity.handoff_offer`, `activity.handoff_accept`,
  `activity.handoff_resume` and `activity.handoff_reject`.
- Offers are expiring, one-time and target-aware.
- A registry rejects consumed, expired, wrong-target and missing-capability handoffs.
- TV receivers reject write-capability handoffs and only accept presentation/read/control resumes.
- Android, Desktop, Android TV and Web/Web TV have platform bridges or runtime controllers for
  publishing and accepting handoff state without making Android a required hub.
- Handoff changes are state transfer/projection events; canonical mutations still flow through the
  SFLP02/SFLP03 journal and SFLP07 reconnect reconciliation.

## Validation

`verifySflp12Sflp13WebTvHandoff` chains the SFLP10/SFLP11 predecessor seal, validates the new
Kotlin contracts, verifies Web runtime/source/dist parity, checks JSON schema/golden vectors and
keeps the SFLP promise chain intact.
