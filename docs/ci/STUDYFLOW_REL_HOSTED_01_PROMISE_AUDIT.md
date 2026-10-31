# REL-HOSTED-01 — promise-by-promise acceptance audit

| Promise | Source implementation | Local source evidence | Hosted/device evidence |
|---|---|---|---|
| Green CI evidence recorded | SF-DT-55 and post-REL-HOSTED-01 CI runs | Hosted runs at `6178499c195952288f5e45981bf84c3601ceae3e` | **ACCEPTED** regular CI 41207374268, deep CI 41207374915 |
| GitHub-only candidate builds for phone+TV | `.github/workflows/studyflow-hosted-release.yml`, distinct `hostedCandidate` build types | R1 matrix correction and negative test selection regressions | Phone **PASS** (41207377489), TV **FAILED** before Gradle task execution (incorrect `testReleaseUnitTest`); corrected source **HOSTED PENDING** |
| Existing production `assembleRelease` signing preserved | Existing app release preflight and TV packaging `require` not relaxed | Personal signing contract 11/11 pass; neither `build.gradle.kts` modified by R1 | Release packaging/real signing **PENDING** |
| Zero keys on GitHub | No repository/Actions secrets, only signing-free candidate build | Source contract; fail-closed runner checks | **PENDING** hosted inventory |
| Candidate SHA/run/package/unsigned proof | `scripts/studyflow_hosted_candidate.py stage`, APK+AAB manifest | Adversarial receipt validation | Phone artifact `studyflow-hosted-app-unsigned-41207377489` uploaded; TV **PENDING** |
| Private offline signing | `sign` mode, external personal PKCS#12, apksigner+jarsigner, per-file hashes | Source-only safeguards; no real key used | **PENDING** actual local keys + SDK tools |
| REL01 plan integration without forged PASS | `hostedSignedCandidateReceipts` in planning report; no changes to final verdict semantics | Source guard tests | **PENDING** signed receipts |
| CI regression and tamper resistance | Source-stage test and 11 regression methods (including 5 adversarial matrix mutations) | 11/11 source tests pass; source-only, no Gradle | Re-run hosted workflow at updated source SHA **PENDING** |
| Full REL01/REL-FINAL connected/restore/real-device release verdict | Intentionally **NOT** fabricated | Source boundary documented | **OPEN** follow-up workflow + physical device evidence |

**Hard boundary:** an unsigned GitHub candidate or signed offline candidate does not prove
`release-host-gates.json`, `connected-test-gates.json`, `full-journey.json`, `device-acceptance.json`,
`final-release-verdict.json` or `release-ops-final-seal.json`. REL-HOSTED-01 is staging, not
release-ready; existing devtool local-Gradle automation remains incompatible with GitHub-only.

## REL-HOSTED-01-R1 — observed failure and corrective promise audit (2026-10-31)

**Observed source:** `6178499c195952288f5e45981bf84c3601ceae3e`.

| Promise | Evidence | R1 disposition |
|---|---|---|
| Main CI and Devtool deep jobs remain green | Regular 41207374268, manual/deep 41207374915 | **ACCEPTED at old commit**, rerun at new SHA after R1 recommended |
| Correct TV JVM test selection | Hosted run 41207377489, job 124555147271 failed while locating `:app-tv:testReleaseUnitTest`; TV only has default debug JVM tests | **SOURCE FIXED** by explicit matrix task `:app-tv:testDebugUnitTest`; hosted acceptance **PENDING** |
| Preserve phone Release JVM testing | Run 41207377489, phone job 124555147225 passed and staged candidate | **SOURCE PRESERVED** `:app:testReleaseUnitTest`; new SHA hosted acceptance **PENDING** |
| Hosted APK/AAB, package-identity and unsigned validation | Phone staging step succeeded, artifact ID 13044578695; TV staging skipped following Gradle task selection failure | Phone **ACCEPTED at old SHA**; TV **OPEN** |
| Preserve failure diagnostics independently of candidate staging | Original failed TV run skipped staging/upload, leaving no TV candidate package | **SOURCE ADDED** `always()` JVM/lint artifact step; no fake candidate on failure |
| Reject reintroduction of generic Release task | New test validates matrix + bindings, rejects 5 mutated workflow variants and TV source drift | **11/11 SOURCE PASS**; hosted source gate still required |
| Private signing, release preflight and final evidence gates unchanged | No signing code, build-type configuration, or release verdict mutator changed | **SOURCE PRESERVED**; actual signed/device acceptance **OPEN** |

**Important:** This R1 patch repairs a task-selection failure. It does not prove `:app-tv:lintRelease`, TV candidate compilation, or TV unsigned-package verification will succeed until GitHub actually runs them. Never substitute successful debug CI for a successful hosted TV candidate.

## REL-HOSTED-01-R2 — TV signing preflight and Gradle configuration-cache repair (2026-10-31)

- **Observed hosted evidence:** [`41219199002`](https://github.com/Mikeyphw/studyflow/actions/runs/41219199002), commit `57cdb57dac0d9b3a492f67cae58bc225af42a63f`. Source audit and phone candidate succeeded; TV candidate failed at `:app-tv:packageReleaseResources` before staging.
- **First failure:** TV's legacy `tasks.configureEach` injected a `doFirst { require(signing) }` into every `packageRelease*` task. `packageReleaseResources` is also required for unsigned candidate or lint tasks, so the guard was incorrectly applied.
- **Second failure:** That `doFirst` action captured the Gradle build-script object, producing the configuration-cache serialization failure.
- **R2 source change:** A dedicated `Exec` preflight runs `scripts/ov01_release_gate.py signing --surface tv` only for final signed Release packaging or signing tasks. It preserves all-four legacy signing-variable requirements or permanently configured personal signing. Shared Release resource and unit-test tasks remain independent, and hosted candidates still use their separate unsigned build type.
- **Source acceptance:** Hosted candidate and permanent personal-signing regression suites plus Actions source verifier must pass. Real hosted TV APK/AAB creation is **PENDING new-SHA GitHub execution**.
- **No claims:** Not a signed release, no physical-device qualification, and no local Gradle in Termux. The phone's successfully produced unsigned artifact is not evidence of TV success.
