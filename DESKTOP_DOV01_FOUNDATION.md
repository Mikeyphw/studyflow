# DOV01 — Desktop Foundation

This overlay prepares StudyFlow for a Compose Multiplatform Desktop app without
rewriting Android business logic.

## Implemented

- Adds `studyflow_desktop` as the default Devtool target while preserving
  `studyflow_android`.
- Configures the desktop target as a JVM/Gradle target with low-resource
  validation settings for Termux/chroot workflows.
- Includes `:desktopApp` in Gradle settings so later overlays can add the
  Compose application shell.
- Adds Compose Multiplatform, Compose compiler, and Kotlin JVM plugin aliases to
  the version catalog.
- Adds desktop package hygiene to `.gitignore`.
- Adds a static foundation verifier, `verifyStudyFlowDesktopFoundation`.
- Adds `desktopApp/README.md` documenting the desktop boundary.

## Not implemented yet

DOV01 intentionally does not create the desktop UI shell, dependency graph, or
packaging tasks. Those belong to DOV02 and later overlays.

## Validation

For this interim overlay, apply with `--no-validate`. A lightweight static check
is available after apply:

```bash
devtool -r "$HOME/Code/studyflow" --target studyflow_desktop validate   --task verifyStudyFlowDesktopFoundation
```
