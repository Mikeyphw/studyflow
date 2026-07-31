# Phase 2 Implementation

Phase 2 replaces volatile plan storage with a normalized Room database while keeping
feature APIs stable. Revision r6 also seals the AGP 9 plugin topology and the JVM/Android testing boundary.

## Delivered

- Complete Android-free domain graph in `:core:model`.
- New `:core:database` Room module.
- Twenty-two plan-owned entities plus database metadata.
- Plan ownership foreign keys and cascade deletion.
- Domain-focused DAOs for structure, planning, library, practice, retention, and support.
- Persistent `RoomStudyPlanRepository`.
- Transactional create, replace, delete, clear, restore, and one-time sample seeding.
- Database mapper unit tests and cascade instrumentation coverage.
- Room schema export configuration.
- AGP 9 built-in Kotlin with the new DSL retained.
- KSP, Compose, and Serialization supplied through the included convention build.
- Regression checks forbidding `org.jetbrains.kotlin.android` in Android conventions.
- Android-free `:core:testing` fixtures backed only by `:core:domain` and `:core:model`.
- Repository-wide validation that rejects JVM modules depending on Android modules.
- Phase 2 architecture decision, acceptance contract, validator, and Devtool phases.

## Deferred by design

- Feature-specific CRUD screens remain in their roadmap phases.
- Import/export and snapshot payload files remain Phase 4.
- Annex bytes remain Phase 9.
- Media playback remains Phase 10.
- Database migrations begin when schema version 2 is introduced.
