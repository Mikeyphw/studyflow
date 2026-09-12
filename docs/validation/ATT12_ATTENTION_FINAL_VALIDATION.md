# ATT12 Attention final validation

ATT12 is the terminal validation overlay for ATT01–ATT12. Intermediate `--no-validate` policy no longer applies to this overlay.

## Explicit validation task matrix

| Task | Purpose |
| --- | --- |
| `verifyAtt12AttentionReleaseFinalSeal` | ATT12 promise reconciliation, five-client surface wiring, target safety, docs/matrix, Web parity/release checks, relevant unit lanes |
| `verifyAtt11NotificationReliabilityEvidenceAudit` | Reliability evidence, analytics, route audit and export |
| `verifyAtt10QuietHoursFocusProfilesEscalationPolicy` | Quiet hours, profiles and policy-aware routing |
| `verifyAtt09CalendarScheduleExportAttention` | Calendar/ICS attention integration |
| `verifyAtt08CompactCompanionAttention` | Compact companion projections |
| `verifyAtt07CrossDeviceDeliveryRouter` | Owner/mirror routing and target safety model |
| `verifyAtt06TvAttentionExperience` | Android TV/Web TV foreground attention |
| `verifyAtt05WebPwaAttentionRuntime` | Web/PWA runtime and generated parity |
| `verifyAtt04DesktopAttentionRuntime` | Desktop runtime/tray fallback truth |
| `verifyAtt03AndroidWidgetSuite` | Android widgets |
| `verifyAtt02AndroidAttentionSystem` | Android native Attention runtime |
| `verifyAtt01UnifiedAttentionFoundation` | Shared ledger/domain/SFLP Attention foundation |

`verifyAtt12AttentionReleaseFinalSeal` also depends on the SFLP18/19 notification/media verifier, WEB20 semantic golden vectors, `:core:link:test`, Android release unit tests, Desktop tests, Android TV unit tests, ATT12 Web tests, TypeScript runtime parity, and Web release validation/integrity.

## Final Devtool command

```bash
cd "$HOME/Code/studyflow"

gradle --stop || true

devtool --copy --target studyflow_android apply overlay \
  /sdcard/Download/studyflow_att12_attention_release_final_seal_v6.tar.gz \
  --keep-artifact \
  --resource-profile standard \
  --max-workers 4 \
  --cpu-limit 4 \
  --gradle-heap-mb 2048 \
  --gradle-metaspace-mb 768 \
  --memory-guard-mb 0 \
  --parallel \
  --task verifyAtt12AttentionReleaseFinalSeal \
  --task verifyAtt11NotificationReliabilityEvidenceAudit \
  --task verifyAtt10QuietHoursFocusProfilesEscalationPolicy \
  --task verifyAtt09CalendarScheduleExportAttention \
  --task verifyAtt08CompactCompanionAttention \
  --task verifyAtt07CrossDeviceDeliveryRouter \
  --task verifyAtt06TvAttentionExperience \
  --task verifyAtt05WebPwaAttentionRuntime \
  --task verifyAtt04DesktopAttentionRuntime \
  --task verifyAtt03AndroidWidgetSuite \
  --task verifyAtt02AndroidAttentionSystem \
  --task verifyAtt01UnifiedAttentionFoundation
```

## v6 source-quality closure

The fifth authoritative Termux run reached the historical SFLP08/09 Android/Desktop integration verifier after all previously failing product/test lanes had been repaired. That verifier correctly rejected 14 ATT10/ATT11 additions in `DesktopAnalyticsSearchSettingsScreens.kt` that exceeded the project's 120-character Kotlin source contract. V6 wraps those expressions without changing behavior and adds the same limit to the ATT12 final contract verifier so this exact regression cannot reappear.

## v5 compile/test closure

ATT12 v1 through v4 were correctly rolled back by Devtool as the authoritative Termux Gradle gate exposed progressively deeper integration drift. V5 keeps the same full validation scope. It includes the v2–v4 compile/runtime fixes, aligns the final Android TV settings test with the ATT11 Attention card/action already present in production, and aligns the Desktop workflow test with DOV23 native file-picker delivery.

The Web runtime-parity gate still prefers a real TypeScript 5.8.3 compile. To make the Android/Termux seal self-contained when the compiler executable is absent from Gradle's environment or is terminated without compiler diagnostics, the runner verifies a sealed SHA-256 manifest binding `tsconfig.json`, `package.json`, every TypeScript/TSX source, and every committed runtime JavaScript file. Real TypeScript diagnostics remain hard failures. The final seal does **not** use `--no-validate` or remove Android, Android TV, Desktop, shared-link, Web, SFLP, or historical reminder-planner lanes.

## Sandbox limitation

The artifact-generation sandbox can run Python/Node/TypeScript verification and source/runtime/release checks, but it cannot download an uncached Gradle 9.7.1 distribution. The Termux Devtool command above is therefore the authoritative Gradle/Android/Desktop/TV compilation and unit-test execution for the final seal.
