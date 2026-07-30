# Validation Report

Date: 2026-07-30
Artifact: StudyFlow Android Phase 0 and Phase 1 repository

## Completed checks

| Check | Result |
|---|---|
| Phase 0 product-contract validator | Passed, 10 required documents |
| Phase 1 foundation validator | Passed, 11 required modules |
| English and pt-BR string-key parity | Passed |
| Android locale configuration contract | Passed |
| Hardcoded user-facing string scan | Passed |
| AGP built-in Kotlin contract | Passed |
| Source formatting contract | Passed |
| Core model Kotlin compilation | Passed |
| `.devtool.toml` TOML parsing | Passed |
| Uploaded Devtool project-config parser | Passed |

## Devtool contract

The repository includes the target:

```text
studyflow_android
```

Configured validation phases:

1. `preflight`
2. `compile`
3. `resources`
4. `unit-test`
5. `package`
6. `lint`

The configuration uses Android-safe execution defaults, one Gradle worker, no daemon, low-priority
validation, split Gradle phases, and affected-module lint support.

## Environment-limited check

A full Android Gradle build was not executed in the artifact-generation container because it did not
provide an Android SDK and compatible installed Gradle launcher. The repository contains a pinned
Gradle 9.5.0 distribution configuration and is intended to be built or validated through Devtool using
the `studyflow_android` target.

Run the complete gate in the destination environment with:

```bash
devtool --copy --auto-hud --hud-mode desktop-window --yes \
  -r /path/to/studyflow-android-phase01 \
  --target studyflow_android \
  validate
```

Or run the repository entry point:

```bash
./scripts/validate_repo.sh
```
