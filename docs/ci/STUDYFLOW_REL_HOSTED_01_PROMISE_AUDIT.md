# REL-HOSTED-01 — promise-by-promise acceptance audit

| Promise | Source implementation | Local source evidence | Hosted/device evidence |
|---|---|---|---|
| Green CI evidence recorded | SF-DT-55 with exact commit and successful run IDs | Commit/job inspection at creation | **ACCEPTED** pre-existing 5/5 + 5/5 |
| GitHub-only candidate builds for phone+TV | `.github/workflows/studyflow-hosted-release.yml`, distinct `hostedCandidate` build types | Source checks, no local Gradle | **PENDING** first hosted execution |
| Existing production `assembleRelease` signing preserved | Existing app release preflight and TV packaging `require` not relaxed | Source invariant regression | **PENDING** hosted Gradle compatibility |
| Zero keys on GitHub | No repository/Actions secrets, only signing-free candidate build | Source contract; fail-closed runner checks | **PENDING** hosted inventory |
| Candidate SHA/run/package/unsigned proof | `scripts/studyflow_hosted_candidate.py stage`, APK+AAB manifest | Adversarial receipt validation | **PENDING** real packages |
| Private offline signing | `sign` mode, external personal PKCS#12, apksigner+jarsigner, per-file hashes | Source-only safeguards; no real key used | **PENDING** actual local keys + SDK tools |
| REL01 plan integration without forged PASS | `hostedSignedCandidateReceipts` in planning report; no changes to final verdict semantics | Source guard tests | **PENDING** signed receipts |
| CI regression and tamper resistance | Source-stage test and 10 regression cases | PASS in fixture | **PENDING** hosted CI |
| Full REL01/REL-FINAL connected/restore/real-device release verdict | Intentionally **NOT** fabricated | Source boundary documented | **OPEN** follow-up workflow + physical device evidence |

**Hard boundary:** an unsigned GitHub candidate or signed offline candidate does not prove
`release-host-gates.json`, `connected-test-gates.json`, `full-journey.json`, `device-acceptance.json`,
`final-release-verdict.json` or `release-ops-final-seal.json`. REL-HOSTED-01 is staging, not
release-ready; existing devtool local-Gradle automation remains incompatible with GitHub-only.
