# StudyFlow OV01-R4R9 — consolidated hosted acceptance remediation

Base GitHub commit: `3f702ee521811a44ab7b632f2a4c7bc6555f3fe3`.

| Promise | R4R9 source evidence | What remains open |
|---|---|---|
| API 23 compatibility | API-24 annotation, guarded dispatcher calls and refresh, no app-wide minSdk change | GitHub lint, API-23 runtime smoke |
| API 24–33 tile behavior | Keep supported `startActivityAndCollapse(Intent)` fallback; scoped lint suppression | Hosted compile/lint, device |
| API 34+ correct QS launch | Immutable `PendingIntent.getActivity` and `startActivityAndCollapse(PendingIntent)` only under 34+ gate | Hosted compile/lint, device |
| ATT12 widget exact-boundary behavior | Check exact future filter and two JVM regression cases instead of old 60-second filter | Hosted deep Android |
| SFLP06/07 queue formatting | Split only offending `nextPeerAttemptAt` declaration to satisfy 120-column verifier | Hosted deep desktop |
| Active-tile refresh metadata | Add `android.service.quicksettings.ACTIVE_TILE` while preserving `BIND_QUICK_SETTINGS_TILE` on the service | Device refresh behavior |
| Permanent regressions/ledger | Add R4R9 source tests to hosted source job and SF-DT-47..51 | Hosted CI and follow-up evidence |
| No signing/security downgrade | No release tasks, secrets, or app-wide lint suppression; GitHub-only Gradle | Signed release / physical-device validation separate |

Prior [standard CI 41161662046](https://github.com/Mikeyphw/studyflow/actions/runs/41161662046) ran 121 JVM tests (all pass), 11 lint errors all in tile source, and 66 lint warnings. [Deep CI 41161662889](https://github.com/Mikeyphw/studyflow/actions/runs/41161662889) cleared OV16 and SFLP04/05 but failed ATT12 and SFLP06/07. These are the observed, not guessed, blocker scopes. Neither local source validation nor a Devtool apply substitutes for hosted Kotlin compilation, runtime Android coverage, or release acceptance.
