# ADR-0026: Room-backed global discovery

## Decision

Use Room queries plus deterministic Kotlin ranking instead of AppSearch. StudyFlow keeps Room as its canonical
source of truth and avoids the AppSearch annotation processor conflict with AGP 9 built-in Kotlin.

Search excludes archived plans and archived child content by default. The recommendation engine receives a
stable projection and uses explicit tie-breakers, making every result reproducible in tests and explainable in UI.
