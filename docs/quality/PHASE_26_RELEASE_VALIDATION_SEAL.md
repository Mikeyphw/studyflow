# Phase 26 Release Validation Source Seal

Status: source-level release validation model implemented; this is not target Release acceptance.

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
- The historical generic completed-seal helper has been removed; OV-13 requires explicit PASS evidence for every required release gate.
- No Room migration, network permission, sync provider, exact-alarm permission, or destructive validation behavior was added.
