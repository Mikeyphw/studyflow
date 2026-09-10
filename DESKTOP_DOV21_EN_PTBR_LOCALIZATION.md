# Desktop DOV21 — English / pt-BR localization

DOV21 makes language selection a real Desktop presentation concern rather than a stored preference with no visible effect.

## Delivered

- Central `DesktopTextCatalog` resolves `SYSTEM_DEFAULT`, `ENGLISH`, and `PORTUGUESE_BRAZIL` without importing Android/i18n modules.
- `SYSTEM_DEFAULT` follows the JVM locale (`pt*` -> pt-BR, otherwise English).
- The Desktop composition root observes persisted `AppPreferences.language` and immediately re-composes after language changes.
- Primary navigation, shell labels, dashboard, Plan, Library, Planner, Practice, Reviews, Writing, content modes, Concurso, Analytics, Search and Settings strings are routed through `desktopText`.
- pt-BR translations cover every static `desktopText` key in DOV21; unknown/dynamic strings fail safely to the canonical English source text.
- Language selection survives restart through the DOV08 workspace persistence store.

This remains JVM-safe and does not add `core:i18n` or Android resource dependencies to the Desktop boundary.
