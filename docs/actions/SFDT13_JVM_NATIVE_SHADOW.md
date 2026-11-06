# SFDT13: source-bound native JVM shadow and Gradle metadata provenance

This phase extends StudyFlow's successful SFDT12 independent **Node** provider qualification to its **real JVM source** (`:core:model`) without changing `.devtool.toml`, authoritative CI, source signing, Android builds or release status.

## What hosted CI actually does

1. Checks out the exact candidate Git SHA with read-only credentials.
2. Checks the existing GitHub-only Gradle guard, wrapper 9.7.1, and legacy Desktop CI.
3. Checks out the separately merged Devtool RM21/RM12 source at exact SHA `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`.
4. Runs RM12's **read-only** source admission (no fake Gradle lock or verification metadata). A blocker is a blocker, not a pass.
5. Builds the actual StudyFlow `:core:model:jar` via its own Gradle wrapper, **on GitHub Actions only**, and checks real class/archive bytes with Devtool's `verify_jar`.
6. Generates a **candidate** `gradle/verification-metadata.xml` from real GitHub-hosted dependency resolution, validates its SHA-256 inventory, and uploads it for separately reviewed inclusion. It is never auto-committed.

The source-only test and byte-level shadow do not claim RM21 JVM acceptance or a release. GitHub Actions remains the sole Gradle execution host. The next step requires reviewing and source-pinning genuine dependency verification metadata, merging Devtool's RM22 hosted runner environment fix, then running `jvm-jar` with the exact upstream merged Devtool commit, `--jvm-module :core:model`, `--gradle-version 9.7.1`, and a warmed dependency cache. The RM21 native runner's `--offline` constraint remains in force.

**Known blockers as of source inspection:** RM12 requires checked-in dependency verification/locks; StudyFlow root lacks these. The RM21 child currently strips `RUNNER_OS` (RM22 correction included separately). Do not add an empty lockfile or a fake verification XML to satisfy a textual presence test.

## Interpretation

- `NATIVE_JVM_JAR_BYTE_VERIFIED_NOT_RM21_QUALIFIED` = successful real class archive built and checked on GitHub, not an independent consumer acceptance.
- `SOURCE_REVIEW_BLOCKED_NO_CUTOVER` = RM12 static gate did not admit the current source, with the exact reason retained in the report.
- The generated verification metadata is a **candidate** and must not be treated as a production dependency lock without reviewing its source/breadth.
- `release_qualified`, `signing_qualified`, `device_qualified`, and `rm21_qualified` remain false.
