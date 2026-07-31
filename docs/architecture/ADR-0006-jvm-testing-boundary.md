# ADR-0006: JVM testing modules remain Android-free

## Status

Accepted for Phase 2 revision r6.

## Context

`:core:testing` is a plain Kotlin/JVM library. It originally exposed `:core:data`, which became an
Android library when Room persistence landed. A JVM consumer does not provide an Android build-type
attribute, so Gradle could not choose between `debugRuntimeElements` and `releaseRuntimeElements`.
The failure occurred while resolving `:core:testing:testRuntimeClasspath`, before tests executed.

## Decision

- Keep `:core:testing` as a plain Kotlin/JVM module.
- Allow it to depend only on Android-free modules such as `:core:model` and `:core:domain`.
- Keep Android repository implementations and Room fixtures inside Android modules or their tests.
- Reject JVM-to-Android project dependencies in `scripts/verify_phase_2.py`.

## Consequences

- `:core:testing:test` has one deterministic JVM runtime variant.
- Shared domain fixtures remain usable by JVM and Android tests.
- Android-specific fakes can still consume `:core:testing`, but the dependency never points upward
  from JVM code into Android infrastructure.
- Future module changes fail during repository-contract validation instead of deep inside Gradle
  variant resolution.
