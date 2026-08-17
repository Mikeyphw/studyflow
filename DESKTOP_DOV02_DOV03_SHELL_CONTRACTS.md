# StudyFlow Desktop DOV02/DOV03 — Shell and Shared Boundary

This overlay creates the first Compose Multiplatform Desktop application shell
and seals the initial shared-module boundary.

## DOV02 — Desktop shell

- Adds `desktopApp/build.gradle.kts` as a Kotlin JVM + Compose Multiplatform
  desktop module.
- Adds `Main.kt` with a real desktop `Window` entry point.
- Adds `StudyFlowDesktopApp.kt` with a minimal, bootable shell view.
- Adds app metadata and desktop icon/resource placeholders.
- Adds a JUnit smoke contract for shell state and boundary behavior.

## DOV03 — Shared app contracts boundary

- Allows desktop to depend only on JVM-safe shared modules for now:
  `:core:model`, `:core:domain`, and `:core:portability`.
- Blocks Android application, Android feature, Android persistence, Android
  navigation, and Android design-system modules from the desktop dependency
  graph.
- Adds `StudyFlowDesktopBoundary` so the rule is visible in source, testable,
  and enforceable by the static verifier.
- Adds `DesktopPlatformPorts` plus safe no-op adapters for future filesystem,
  file-dialog, and notification work without importing Android APIs.
- Adds `verifyStudyFlowDesktopShellContracts` to check the module graph and
  desktop source imports without needing an Android device.

## Deferred intentionally

- Real repository wiring and persistence adapters are deferred to DOV04.
- Dashboard/library workflows are deferred to DOV05.
- Native packaging polish remains deferred to DOV10.
