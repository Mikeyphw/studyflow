# ADR-0005: AGP built-in Kotlin with unified compiler plugins

## Status

Accepted for Phase 2.

## Context

AGP 9 enables built-in Kotlin and the new Android DSL by default. The Phase 2 Room module also
requires KSP. Applying KSP through a module-level version-catalog alias while Compose and other
Kotlin compiler plugins are supplied by the included `build-logic` build can split Kotlin-facing
plugins across Gradle classloaders. That topology caused configuration to fail before project
sources were compiled.

Temporarily opting out of built-in Kotlin also requires opting out of the new AGP DSL. Keeping only
one of those compatibility switches creates an invalid hybrid configuration.

## Decision

- Keep `android.builtInKotlin=true`.
- Keep the AGP 9 new DSL enabled.
- Do not apply `org.jetbrains.kotlin.android` to Android modules.
- Put Compose, Serialization, and KSP plugin implementations on the included `build-logic`
  classpath.
- Apply KSP through `studyflow.android.library.room`.
- Configure Room KSP arguments inside that convention plugin.
- Apply Serialization through `studyflow.kotlin.library.serialization`.
- Do not preload KSP or Serialization in the root project or apply them directly in modules.

## Consequences

AGP remains the single owner of Kotlin compilation for Android modules. KSP 2.3.10 can integrate
with AGP 9 built-in Kotlin, while all additional compiler plugins retain one explicit classloader
owner. Android module build files remain small and the project avoids the temporary legacy-DSL
compatibility path that AGP 10 will remove.
