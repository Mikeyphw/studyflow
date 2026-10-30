# OV01-R4R4 | deep-hosted failure remediation and promise audit

Base commit: `aef8c0fa5e997d79bf02043598a6647ad248852a` (R4R3). Failed hosted run: `41138136150`.

| Promise | Source change | Source evidence | Hosted gate |
| --- | --- | --- | --- |
| DOV10/11 stop failing due to missing task names | `.devtool.toml` adds DOV10-DOV22 and DOV24 to desktop test and unit-test native phase | Runs all `verify_desktop_dov*.py` source verifiers and negative mutation; exact names present twice | Re-run deep desktop |
| Native/Web OV09 uses pinned TypeScript | Android deep also gets Node 22 and `npm ci` in `web`, using committed `package-lock.json` | Workflow parse, source contract, package lock pinned 5.8.3; no PATH-only compiler | Re-run deep Android |
| Unsigned parity no longer requires signed APK/AAB | GitHub-only plan resolves `validation.phases.preflight` instead of release-bearing parity `tasks.build` | Negative unit tests reject a terminal seal and package task; native `tasks.build` and `tasks.test` retain terminal seal | Re-run deep parity |
| Preserve full release signing gates | No changes to `app/build.gradle.kts` or release workflows | Source static signing preflight guards unchanged | Signed release/device qualification remains OPEN |
| Keep EXO/Gradle evidence truthful | `qualified_release=false`, source SHA, actual planned Devtool tasks; prior receipts unchanged | Source-only receipt tests, no local Gradle | Receipt review on hosted run |
| Preserve gains | Quality, TV, source CI configuration untouched | Source-only checks plus transaction inventory | Re-run deep + standard CI |

No Gradle is run locally. This patch is not a claim that the full native parity release seal passed; the latter still requires signing and physical evidence. CI source-only tests cannot prove JVM test graph behavior or artifact installability.
