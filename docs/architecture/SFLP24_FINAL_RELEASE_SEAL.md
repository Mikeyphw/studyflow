# SFLP24 — Final release validation seal

SFLP24 closes the cross-platform SFLP series with a deterministic final validation seal. The seal is
not a new sync capability; it is the release gate that proves SFLP01 through SFLP23 remain wired into
Gradle, Devtool, protocol fixtures, platform tests, and documentation.

The final gate is `verifySflp24FinalReleaseSeal`. It depends on the SFLP22/SFLP23 consolidated gate
and the platform unit-test lanes for `:core:link`, Android, Android TV, and Desktop. The Web runtime
is covered by the existing SFLP verifier scripts and final protocol golden vector checks.

The final validation plan is language-neutral:

- `release.validation_final_seal`
- `protocol/sflp/v1/sflp-final-release-seal.schema.json`
- `protocol/sflp/v1/golden/final-release-validation-plan.json`
- `core/link/src/test/resources/sflp/v1/final-release-validation-plan.json`

Apply this final seal overlay with validation enabled. Earlier SFLP overlays intentionally used
`--no-validate`; SFLP24 is the point where the complete chain should run.
