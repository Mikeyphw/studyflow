# Phase 26 Validation

Phase 26 validates the validator itself.

Source checks:

- `scripts/verify_phase_26.py`
- `ReleaseValidationSealFactoryTest`
- `ReleaseValidationSealFactory.requiredKinds()` covers every `ReleaseValidationGateKind`

Target checks still required through Devtool:

- Gradle configuration;
- `checkAll`;
- resource merge;
- lint;
- APK assembly;
- at least one device or emulator smoke pass.
