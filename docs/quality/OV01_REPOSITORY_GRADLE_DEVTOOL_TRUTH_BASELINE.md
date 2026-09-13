# OV01 — Repository / Gradle / Devtool truth baseline

OV01 is Overlay 1 of 30 in the final deduplicated StudyFlow remediation campaign. It closes the twelve S0 build/tooling findings before domain or cross-device remediation begins.

## Delivered contracts

- Root execution-time Gradle closures were moved to standalone Python verifiers and cross-project ordering now uses lazy `TaskCollection.configureEach` wiring rather than `gradle.projectsEvaluated`.
- Phone Release packaging owns a signing preflight. When a Release package is selected, every Release task is ordered after that preflight so missing secrets fail before compilation/package work.
- `verifyRepositoryContracts` is a dependency-only aggregate. Historical validators have individual Gradle task ownership and the seven formerly duplicated scripts are referenced by exactly one `Exec` owner each.
- Root verification `Exec` tasks share declared repository inputs, command identity, and a unique success stamp. Static Python source verifiers may be cached; report-producing, runtime, release, device, and evidence gates are explicitly always-run.
- Devtool `@everything` includes `@quality`, direct Gradle defaults match the standard four-worker/2048 MiB/768 MiB policy, and broad AGP warning suppression is removed.
- POSIX/Windows wrapper launchers restore normal generated-wrapper behavior while retaining the pinned Gradle distribution checksum.
- `core:data` no longer duplicates desugaring configuration owned by the Android convention plugin.
- Main-build and included-build dependency locking are enabled. `scripts/refresh_gradle_dependency_integrity.sh` resolves all project dependency reports with `--write-locks --write-verification-metadata sha256`; `verify_gradle_dependency_integrity.py --require-materialized` rejects missing/empty locks or checksum metadata. The signed Release host gate requires this materialized integrity state.
- Devtool runtime registry/history files are ignored and `ov01_prune_stale_devtool_state.py` removes only the three known stale runtime files. `.devtool/` is intentionally not inventoried by the overlay because it is Devtool-owned runtime state.

## Validation ownership

`verifyOv01RepositoryGradleDevtoolTruth` is the behavioral/source contract for S0-01 through S0-12. It is included in the `studyflow_quality` target. The complete campaign still follows the roadmap rule: OV01–OV29 apply with `--no-validate`; OV30 owns the terminal full validation.

Dependency checksum/lock files are environment-generated evidence, not fabricated source. After applying OV01, run `scripts/ov01_finalize_target_state.sh` on the target Termux checkout. It removes only the known stale Devtool runtime files, materializes real `gradle.lockfile` / `verification-metadata.xml` state, stages only those generated/deletion paths, and amends the OV01 commit. A Release host gate cannot pass until that has happened.
