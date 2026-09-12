# ATT08 — Compact Companion Attention

ATT08 adds StudyFlow's wearable and compact companion attention surface. It is intentionally
**phone-owned** and route-owned: a watch, lock-screen chip, quick tile, Desktop mini panel, Web
compact panel, or TV remote overlay mirrors the ATT07 parent route but never becomes an independent
owner of a reminder.

## Delivered

- Shared Kotlin compact companion domain model in `core:link`.
- `StudyFlowCompactCompanionProjector` for bounded compact card projection.
- ATT01 ledger suppression so handled reminders do not replay on watches or compact chips.
- Sensitive-text hiding by default on wearable/lock-screen surfaces.
- Maximum three compact actions with D-pad/touch-safe labels.
- Android phone-owned wearable, lock-screen and quick-tile bridge.
- Desktop mini dashboard projection bridge, advertised to ATT07 as `WIDGET_OR_BADGE` whenever the tray mini surface is available.
- Android TV foreground remote-overlay projection bridge.
- Web compact companion runtime with generated `runtime/` and `dist/runtime/` parity.
- Web Notification Center compact companion section.
- Tests, docs and `verifyAtt08CompactCompanionAttention`.

## Product truth

ATT08 does not claim an independent Wear OS application, background watch alarm, or closed-TV
compact delivery. Compact surfaces are mirrors attached to a capable parent device. Native cross-device delivery carries the selected ATT07 route so a remote compact target remains compact instead of being upgraded into a system notification. When the ATT07
route has no parent owner, or the ATT01 ledger already handled the occurrence, compact cards are
suppressed rather than redelivered.

## Validation

Run:

```bash
python3 scripts/verify_att08_compact_companion_attention.py
./gradlew verifyAtt08CompactCompanionAttention
```
