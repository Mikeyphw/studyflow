# StudyFlow OV01-R4R8 — GitHub hosted remediation

Source bound to `d6a61fd4e78a7b762bcb593c68ccfb7c8dfe4f01`.

| Promise | Implementation | Local evidence | Hosted gate |
| --- | --- | --- | --- |
| OV16 works outside a Devtool transaction | Use checked-in source runner; only inspect ephemeral hook when present; reject daemon stop | Source regression + OV16 runner | Deep Android CI |
| SFLP04/05 120-column rule without altered pairing | Reformat only three offending Kotlin declarations | Strict SFLP04/05 source verifier and mutation checks | Deep desktop CI |
| Notification permission race is safe | Checked permission + `SecurityException` handling for local and remote post; no false platform-delivered status | Source contract/mutation checks | Android lint & device |
| CI uploads all lint findings | Always-upload XML/HTML/SARIF and JVM test reports in standard + deep Android | YAML source checks | Hosted artifact receipt |
| Regression/ledger are permanent | Hosted R4R8 source test and cumulative SF-DT-43..46 | Python source suite | GitHub source job |

Release certificate, signed distribution, physical device, native EXO acceptance remain independent. CI indicated 12 lint errors but publicly visible job log included only the first. No claim is made that the other 11 are fixed. On permission revocation *after* SFLP reducer acceptance, remote logical delivery journal rollback remains open; avoid presenting local-post failure as a successful remote OS notification.
