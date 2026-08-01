# Phase 22+23 r4 Rebase Notes

This r4 overlay rebases the Phase 22 global search/recommendations and Phase 23 privacy/encryption work onto the uploaded repository state that already contains pending Phase 21 reminders. It preserves the reminders module, route, settings entry, Gradle module include, app dependency, and validation tasks while adding the Phase 22 and 23 modules and contracts.

It also fixes the pending Phase 21 reminder time parser Kotlin string escape so Android compilation is not blocked by the existing `Regex("^(\d{2}):(\d{2})$")` source.
