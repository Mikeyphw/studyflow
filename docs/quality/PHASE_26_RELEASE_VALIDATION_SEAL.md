# Phase 26 Release Validation Seal

Status: source-level release validation seal implemented.

Phase 26 separates artifact/source validation from target-machine build validation. The app now models release gates as explicit evidence items so a green marker verifier cannot be mistaken for a completed Android build.

Required gates:

- artifact manifest and inventory checks;
- source-contract validators through Phase 26;
- Gradle configuration;
- main source compilation;
- test source compilation;
- all `checkAll` unit tests;
- Room schema and migration validation;
- Android resource processing;
- lint;
- APK assembly;
- device smoke probes;
- final Git staging path simulation.

Promise audit:

- `ReleaseValidationSealFactory.sourceOnlySeal` intentionally blocks release until target-machine evidence exists.
- `ReleaseValidationSealFactory.completedSeal` requires every gate kind to pass.
- No Room migration, network permission, sync provider, exact-alarm permission, or destructive validation behavior was added.
