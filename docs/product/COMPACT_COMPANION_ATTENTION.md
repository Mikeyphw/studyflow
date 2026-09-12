# Compact Companion Attention

ATT08 makes small surfaces useful without overstating platform capability. Compact companion
surfaces are **phone-owned** or parent-owned projections of the ATT07 delivery route. They provide a
bounded card, at most three actions, optional haptics, and a handoff action back to a full StudyFlow
client.

## Surfaces

- Android wearable glance: private by default, haptic capable, up to three actions.
- Android lock-screen chip: private by default, two actions.
- Android quick settings tile: one quick action, no independent delivery claim.
- Desktop mini dashboard: full text allowed because it is attached to the Desktop app session.
- Web compact panel/badge: generated from the Web Notification Center snapshot.
- TV remote overlay: foreground-only projection for remote-safe attention.

## Routing rule

Compact companions never become independent owners. A compact card is rendered only when its parent
phone, Desktop, Web, or TV client is the ATT07 owner or mirror. Handled ATT01 ledger states suppress
compact replay. Disconnected, stale or muted companions are suppressed and listed in diagnostics.

## Privacy rule

Wearable and lock-screen surfaces hide sensitive text unless the parent client explicitly marks that
surface safe. Default copy uses category-level text such as “Review due” or “Study soon.”
