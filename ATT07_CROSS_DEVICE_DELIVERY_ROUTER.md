# ATT07 — Cross-Device Delivery Router

ATT07 adds the shared routing layer that chooses exactly which StudyFlow clients should own,
mirror, suppress, or hand off an Attention occurrence. It is intentionally layered above the
existing SFLP peer descriptor instead of modifying the protocol-v1 capability golden list.

## Product contract

- **Single owner.** Every deliverable occurrence has at most one `OWNER` route target.
- **Bounded mirrors.** Mirrors are optional, unique, and capped by routing context.
- **Ledger-first dedupe.** A delivered/acknowledged/started/completed/snoozed/rescheduled/
  dismissed/missed/expired/cancelled ATT01 ledger entry suppresses redelivery on other devices.
- **foreground-truthful TV delivery.** Android TV and Web TV can own only foreground banner routes;
  they never claim closed-device alarms.
- **Durable notifier preference.** Android alarms, Web Push, system notifications, and Desktop
  tray/system notifications outrank foreground-only surfaces when no client is actively focused.
- **Explicit target safety.** `targetDeviceIds` prevents accidental broadcast and suppresses
  non-target devices even when they are otherwise capable.
- **Handoff request contract.** `StudyFlowAttentionHandoffRequest` is the canonical move-to-device
  request model for TV, Web, Desktop, and Android surfaces. ATT07 does not define a second
  transport; dispatch/state transfer remains owned by the generic SFLP handoff/transport layer.

## Delivered surface wiring

- Shared Kotlin router: `StudyFlowAttentionDeliveryRouter.kt`.
- Android bridge: `AndroidStudyFlowAttentionRouterBridge.kt` exposes ATT02 alarm/notification/
  widget truth as router capability input.
- Desktop bridge: `DesktopAttentionDeliveryRouterBridge.kt` exposes ATT04 tray/native-notify
  truth as router capability input.
- Android TV bridge: `StudyFlowTvAttentionDeliveryRouterBridge.kt` exposes ATT06 foreground-only
  banner truth as router capability input.
- Web router: `web/src/attention_router.ts` plus generated `runtime/` and `dist/runtime/` parity.
- Web Notification Center now reports local router ownership/suppression counts.

## Validation

Run `verifyAtt07CrossDeviceDeliveryRouter` to execute the source contract, shared Kotlin router
tests, Web router tests, and prior ATT01-ATT06 regression gates.
