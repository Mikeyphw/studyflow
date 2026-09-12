# ATT01–ATT12 compact transport adversarial recheck

This third independent recheck targeted the remaining place where an implementation could be structurally present but semantically non-deliverable: ATT08 compact surfaces selected by ATT10 and then carried across ATT07/SFLP.

## Findings corrected

### Desktop compact ownership

Desktop already rendered a real mini dashboard through `DesktopCompactCompanionAttentionBridge`, but its ATT07 capability list omitted `WIDGET_OR_BADGE`. Because ATT10 `COMPACT_ONLY` deliberately filters candidates to `WIDGET_OR_BADGE`, the Travel/offline profile could produce no Desktop route at all. Desktop now advertises the compact surface whenever its tray mini surface is available, and shared Kotlin/Web topology advertises the same capability for trusted Desktop peers.

### Cross-device compact/silent semantics

The native `notification.deliver` envelope previously transported the ATT01 occurrence but not the selected ATT07 route. A remote target therefore could not know whether it had been selected for `WIDGET_OR_BADGE`, silent delivery, owner, or mirror semantics. `StudyFlowNotificationPayload` now optionally carries the canonical `StudyFlowAttentionDeliveryRoute`; the factory validates that the target belongs to that route, and Android/Desktop senders attach it. Android and Desktop receivers consume the transported ATT07 route instead of upgrading remote compact delivery into a normal popup.

### Android permission fallback

`ANDROID_ALARM` is no longer considered a deliverable Attention surface when notification permission is unavailable. The real widget/tile compact surface remains eligible, and Android reconciliation does not cancel due-time scheduling solely because POST_NOTIFICATIONS is denied. A route-selected `WIDGET_OR_BADGE` surface is treated as compact delivery even when the ATT10 outcome itself is `DELIVER_NOW`.

### Stale remote capability fallback

A trusted peer can lose notification/tray capability after the source built its topology snapshot. Android and Desktop now treat a rejected remote owner as a capability invalidation for the current occurrence: that owner is removed from the eligible set, ATT07 is recomputed with the same policy/ledger context, and delivery is retried against the next bounded owner. This prevents a revoked remote permission from pinning the reminder to the same stale owner forever, while preserving explicit ATT07 owner/mirror selection on every retry.

## Regression evidence

The shared tests cover Desktop compact topology, Android permission fallback, preservation of the transported ATT07 route, and rerouting after a rejected stale remote owner. A dedicated Desktop router test reproduces the former Travel/offline failure and requires `WIDGET_OR_BADGE` ownership. Android/Desktop source-contract tests and the post-seal verifier require remote route attachment and compact consumption.

The parity battery remains ATT01–ATT12 + post-seal, SFLP01–SFLP24, WEB20 semantic vectors, 47 generated Web runtime files, the complete **290/290** Web test suite, Web release validation/integrity, and clean-overlay source/payload hash reconstruction. Native Gradle execution remains an apply-time requirement on the target checkout because this audit sandbox cannot resolve the uncached Gradle 9.7.1 distribution.
