# StudyFlow Desktop App

This module is reserved for the Compose Multiplatform Desktop application.

DOV01 only wires repository and Devtool foundation so future overlays can add
the app shell without rewriting Android business logic. The desktop app must
reuse JVM-safe core modules first, especially `core:model`, `core:domain`, and
`core:portability`.

Do not place Android-only APIs or copied Android screen logic here. Desktop UI,
filesystem, shortcuts, tray, and native packaging adapters should stay behind
small desktop-specific boundaries.
