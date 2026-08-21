# CMOV-11-R3 — Content Modes Final Integration Seal

CMOV-11-R3 replaces CMOV-11-R2 after the device-side final validation exposed
historic repository-contract collisions with the new content-mode schema versions
and Android package-visibility policy.

## Scope

This final seal verifies the whole content-mode chain:

- CMOV-01 content mode foundation
- CMOV-02 programming course model
- CMOV-03 code blocks and syntax highlighting
- CMOV-04 Termux runtime foundation
- CMOV-05 programming course UI
- CMOV-06 coding exercise Termux execution planning
- CMOV-07 language deck and idiom model
- CMOV-08 language review modes
- CMOV-09 import templates
- CMOV-10 mode-aware analytics

## R3 corrections

- Repository contract verifiers now accept schema versions newer than v4 while
  still requiring the v4 integrity migration and schema export.
- The final app acceptance verifier now accepts migration chains that extend
  beyond Migration3To4.
- The Android manifest keeps the Termux RUN_COMMAND permission and non-exported
  result receiver without adding a `<queries>` block, preserving the Phase 18
  package-visibility contract.
- The Termux bridge no longer depends on package-manager service resolution;
  it sends explicit RUN_COMMAND service intents and reports launch failures
  through the existing result type.
- The final content-mode verifier now enforces the no-queries Termux policy.

## Validation

The overlay wires `verifyContentModesFinalIntegrationSeal` and keeps the final
validation path through `verifyRepositoryContracts`, Phase 56/57, Phase 58/59,
unit tests, lint, and release Kotlin compilation.
