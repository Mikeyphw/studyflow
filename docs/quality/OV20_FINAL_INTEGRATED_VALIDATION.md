# OV20 Final Integrated Validation Seal

## Purpose

Close the OV01–OV20 public-exam / BB pre-edital specialization with a fail-closed validation layer,
without introducing new product features or a second persistence/scheduling/analytics engine.

## Source gates embedded in the overlay

The OV20 source verifier requires the complete OV01–OV19 specialization verifier inventory and
executes the highest-level seals needed to avoid recursive duplicate work on every apply:

- OV19 promise-audit contract (which protects the final specialization invariants);
- BH01–BH08 bug-hunt audit;
- CPOV00 toolchain plus CPOV13/reconciliation seals;
- selected historical post-V2/Phase acceptance contracts that guard scheduling, onboarding,
  final-stretch, release truthfulness, and final-user readiness;

During OV20 artifact construction, the individual OV01–OV19 and CPOV component verifiers are also
run directly before packaging.
- Gradle 9.7.1 and NDK 29.0.14206865 pins;
- EN / pt-BR resource-key parity for the OV19-touched surfaces;
- the final promise-audit invariants (scheduled manual activities, subject-owned recurring blocks,
  seven strategy types, safe enum restoration, canonical exam-date resolution, and no raw debug
  edital-matrix presentation).

## Reconciliations in this seal

OV20 also closes four historical source-contract drifts found by the broad sweep:

1. wraps the older Phase-62 onboarding subject mapping so its own 120-column acceptance gate passes;
2. updates the Phase-63 verifier to recognize the OV18 canonical `PublicExamDateResolver` contract
   instead of requiring the superseded direct `plan.examDateIso` check;
3. updates final-user-readiness validation to recognize the current `DeleteOverflowMenu` confirmation
   helper while retaining the requirement for an actual `AlertDialog` and confirmation resource;
4. reconciles Debug Workbench D8's minSdk-23 `java.time` guard with the repository's existing
   core-library-desugaring baseline, requiring both desugaring enablement and `desugar_jdk_libs`
   before Android UI `java.time` usage is accepted.
5. converts the remaining `feature:workspace` count-bearing strings reported by Android lint into
   real EN/pt-BR plural resources and uses `pluralStringResource`, including pt-BR `many` forms, so
   the final workspace lint is not intentionally shipped with `PluralsCandidate`/`MissingQuantity` debt.

The manual-activity dialog also uses safe enum restoration (`enumValueOrDefault`) so process restore
or a future enum evolution cannot crash on a stale saved enum name.

## Target-machine gate

The source overlay intentionally does not invoke Gradle from `validate.sh`. The authoritative
Devtool apply must use `--validate` **without** trailing `--task` overrides: in this Devtool,
`--task` defines an exclusive validation plan and suppresses the configured preflight, compile,
resource, unit-test, lint, package, and conditional-instrumentation phases. The configured Android
target gate covers repository/source preflight, Debug/Release compilation and resources, the full
module unit-test set, Debug/Release lint, and Debug APK assembly. Ordinary validation deliberately
does not emit Release APK/AAB artifacts when external signing is unavailable: Devtool treats an
`app-release-unsigned.apk` as a release-signing error, and the repository must not weaken that check.
The existing credentialed `verifyFinalReleaseHostGate` / `run_final_release_acceptance.sh` path remains
the only Release APK/AAB packaging gate and fails closed unless all external signing inputs are present.

`verifyOv19PromiseAudit` and `verifyOv20FinalIntegration` are also wired into `checkAll` and the
configured validation preflight so later repository validation cannot silently omit this seal.

Before ordinary package validation, `prepareDevtoolValidationArtifacts` removes stale Release APK/AAB
outputs only when signing inputs are incomplete, so a rolled-back or interrupted unsigned build cannot
poison a later Debug-only validation artifact scan.

Connected Android tests remain conditional on a usable device under the repository's existing
instrumentation policy. NDK-host and no-device ADB warnings are environmental evidence, not reasons
to fabricate successful native/device acceptance.

## Verdict semantics

A green source seal plus a green target-machine Gradle gate means the Android repository is
build/test/package-clean for the exercised tasks. There is **no claim of connected/manual device acceptance**
when no usable device was present.
