# CPOV00 — NDK 29 + Gradle 9.7.1 Toolchain Baseline

CPOV00 establishes the technical baseline required before the Concurso Público feature overlays.

## Delivered

- Gradle wrapper upgraded from 9.7.0 to **9.7.1**.
- Gradle 9.7.1 distribution SHA-256 pinned to `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
- `studyflow_android` and `studyflow_desktop` Devtool targets aligned to Gradle 9.7.1.
- Toolchain policy changed from an exact 9.7.0-only contract to **stable Gradle >= 9.7.0**.
- Prerelease labels remain rejected by the stable-version parser.
- Android NDK centralized as `STUDYFLOW_NDK_VERSION = "29.0.14206865"`.
- Application and Android library conventions both consume the canonical NDK version.
- Devtool Android preparation is pinned to the same NDK 29.0.14206865 version.
- Historical Phase 1/remediation validators no longer reintroduce an exact Gradle 9.7.0 requirement.
- `verifyCpov00Toolchain` covers wrapper parsing/checksum, Gradle comparator behavior, Devtool parity,
  canonical NDK ownership, and stale active 9.7.0-only contracts.

## Validation gate

The overlay asks Devtool to run:

- `verifyCpov00Toolchain`
- `verifyStableToolchainBaseline`
- `verifyRepositoryContracts`
- `:core:model:test`
- `:app:compileDebugKotlin`
- `:app:testReleaseUnitTest`
- `:desktopApp:compileKotlin`

This intentionally checks both Android and desktop lanes before CPOV01 begins.
