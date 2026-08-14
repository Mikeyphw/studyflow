# StudyFlow Remediation OV-13 — Release / Full Regression Seal

Status: **IMPLEMENTED; TARGET RELEASE EVIDENCE PENDING**

OV-13 owns RM-003 release completion, RM-059 release truthfulness, and RM-060 full-app Android
acceptance. It is the final roadmap overlay; there is no handoff-defined OV-14.

## Delivered

- Required release gates now pass only on explicit `PASS`; WARNING/BLOCKED/FAIL/missing evidence blocks.
- Removed the generic `completedSeal` success synthesizer.
- `ReleaseReadinessSeal.productSealed` consumes an authoritative, artifact-bound validation seal and
  historical phase completion is supplied explicitly instead of being hard-coded `true`.
- Host-only signing inputs remain external environment variables; no credential or keystore is shipped.
- App instrumentation targets Release and the full-journey test uses the production application container.
- The mandatory automated final gate requires external signing credentials, builds signed Release APK and AAB,
  binds exact APK identity with `apkanalyzer`/`apksigner`, verifies AAB signature integrity with `jarsigner -verify` without requiring CA trust, and
  rejects changed APK/AAB bytes by SHA-256.
- Host, exact-artifact, connected-suite, full-journey, device, and final-verdict evidence are separate generated
  files. Host and connected PASS evidence are written only by Gradle tasks after their required dependencies succeed.
  All final evidence is session-bound, and journey/device evidence verifies installed APK bytes against the candidate.
- Static acceptance output is explicitly named a source contract. A behavioral verdict contract proves missing AAB, mixed-session, incomplete-device, and mutated-artifact evidence all fail closed.
- Final regression replay now includes `FULL_APP_FINAL_USER_READINESS` in the authoritative repository contract chain.
- Final regression replay corrected the stale Phase 3 duplication contract: SQL-owned graph tables stay in
  `StudyPlanGraphDuplicator`, while annex and snapshot bytes are copied independently by
  `PlanPhysicalResourceDuplicator` before the Room transaction.
- Final replay caught two stale acceptance assumptions before packaging: temporary V2 workspace plan selection remains
  separate from the canonical default (RM-006), and Focus note drafts remain saveable until their debounced field-specific
  Room write completes (RM-025). Regression contracts now protect both canonical ownership rules.
- Historical validators that asserted superseded weaker behavior were migrated only after the stronger current production
  path was traced (journaled provisioning, inexact reminder reliability, atomic adaptive apply, real portability drills).

## Full journey

`StudyFlowFullJourneyAcceptanceTest` covers plan -> syllabus -> generated schedule -> session ->
Practice -> Mock Exam -> remediation -> review -> progress/analytics/search -> reminder/widget ->
export -> `REPLACE_EXISTING` real restore -> `CREATE_AS_NEW` real restore -> retained marker -> forced
process restart -> second instrumentation invocation -> durable re-read and cleanup.

## Validation boundary

Devtool final-overlay validation runs `verifyFinalReleaseAutomatedGate`. It intentionally fails if signing
credentials, Android/JDK verification tools, exactly one connected adb device, any required connected suite, exact
artifact verification, or the restore/restart journey is unavailable or fails. Human-only device observations remain
separate and must carry non-empty evidence text. The campaign is not product-sealed until
`verifyFinalReleaseVerdict` consumes every automated and manual evidence class and passes.

## Corrective promise audit (v2)

The post-package promise audit found that v1 still overstated the final seal. The corrected overlay
supersedes v1 and closes these gaps:

- Devtool and `validate_repo.sh` now require `verifyFinalReleaseAutomatedGate`, not the host-only gate.
- Host PASS requires all four external Release signing inputs and never accepts `app-release-unsigned.apk`.
- Room migration PASS moved out of host evidence and is earned only after the connected database suite succeeds.
- Required connected data/database/Plans/Practice/Schedule/Workspace/Release-app suites emit a separate,
  session-bound evidence class only after their Gradle tasks complete successfully.
- Exact artifact verification runs after the connected suites in the final task graph, re-hashes both APK and AAB,
  verifies APK identity/signing, and verifies complete AAB JAR-signature integrity without treating Android's normal
  self-signed certificate model as a CA-chain failure.
- The full journey now has a retained initial phase and a second post-restart instrumentation phase. The second phase
  asserts a different process token, re-reads durable restored state, then cleans up.
- Manual device acceptance requires non-empty observation text for every PASS; bare PASS flags cannot seal a release.
- The final verdict consumes host, exact-artifact, connected-suite, restore/restart journey, and manual device evidence
  from one validation session and re-hashes the artifact bytes before allowing `releaseReady=true`.
- Historical phase-completion state is explicit input to `ReleaseReadinessSeal`; it is no longer hard-coded complete.
