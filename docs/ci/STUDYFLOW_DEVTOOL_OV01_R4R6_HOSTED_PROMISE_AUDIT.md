# StudyFlow OV01-R4R6 — hosted failure remediation and promise audit

Baseline: GitHub `main` commit `8fefedfd4ba6e5d9f3e8dab78a4a5047e4e63d3f` (R4R5). Source-bound overlay for **only** this baseline.

## Scope, actual defects and implemented repairs

| Promise | GitHub evidence | Concrete delivery | Acceptance |
|---|---|---|---|
| Compile Android phone Release unit tests | Runs 41151347707, 41151658992: `StudyFlowWidgetActionPolicyOv14Test.kt:44:17` null for `String` | Empty-string notes test fixture. `StudySession.notes` non-null model and release-signing gate unchanged. | Source check; hosted Kotlin compile pending |
| Complete desktop Devtool aggregate ownership | Deep run 41151658992: ShellContracts and Workflows not exposed | Register all four previously missing desktop aggregate owners (Foundation, ShellContracts, Workflows, ATT04) in test and native unit-test plans. Assert all 22 aggregate owners exactly once in each plan with negative mutation tests. | Source verifiers; hosted deep desktop pending |
| Preserve both web seals in correct order | Deep run 41151658992: OV30 split expected legacy-first | OV30 split validator now requires OV30R2 then OV30 before build/test/lint/release:check; reject missing or inverted gates. Web package scripts remain unchanged. | Source verifiers; hosted deep parity pending |
| Prevent recurrence | Previous source gate passed despite all three defects | New source-only R4R6 regression invoked by hosted source job; verify_studyflow_actions asserts it remains wired. | Hosted source stage pending |
| Carry cumulative improvement backlog | Developer working preference | SF-DT-36 through SF-DT-38 with run IDs, changed files, status and remaining evidence in improvement ledger. | Recorded |

## Safety and evidence boundary

- The R4R5 unsigned-signing correction has **already passed the prior signing preflight failure**: hosted Gradle reached Kotlin unit-test compilation. R4R6 does not change app/build.gradle.kts, signing credentials, APK/AAB task policy, or phone testBuildType.
- Source regressions execute **Python only**, never local Gradle or remote build dispatch.
- Only **GitHub-hosted** Gradle execution can establish release test compilation and subsequent CI pass. The current standard CI and all five deep-profile statuses on the new commit are **pending**.
- This patch does not qualify a signed release, physical device, or native Devtool EXO receipt.
