# UI/UX V2 Acceptance

## Source acceptance

- Four typed workspace roots exist and are the adaptive shell destinations.
- Root selection is derived from the current navigation destination.
- Root navigation uses `saveState`, `restoreState`, and `launchSingleTop`.
- Route arguments contain IDs and enums, not domain snapshots.
- Legacy feature routes translate to the correct workspace tabs.
- Full editor routes remain distinct from compatibility routes.
- Today, Plan, Practice, and Progress consume real domain managers.
- Focus mode persists target duration and reconstructs timer state from the session.
- Room schema 3 and migration 2 to 3 match the entity definition.
- English and Brazilian Portuguese workspace resources have exact parity.
- Workspace Kotlin and Kotlin DSL files obey the 120-character limit.
- No scoped Compose modifier extension is imported directly.
- No directional icon uses a non-auto-mirrored variant.
- V2 verification is part of repository, Devtool test, compile, lint, and package contracts.

## Target-host acceptance

The following remain evidence-based target checks and must not be inferred from source validation:

- clean compile and resource processing under AGP 9.3.0, Gradle 9.5.0, JDK 17, and SDK 37;
- unit, instrumentation, and Room migration tests;
- Android lint and APK assembly;
- compact portrait, landscape, foldable, and larger-width navigation behavior;
- predictive back and edge-to-edge insets;
- process death and active focus-session restoration;
- TalkBack, switch access, keyboard traversal, and 200 percent font scale;
- fresh install and upgrade from schema 2;
- reminders, widgets, search results, backup, and restore entry-point compatibility.
