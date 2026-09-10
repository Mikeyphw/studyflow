# Desktop DOV22 — System / Light / Dark appearance

DOV22 turns the persisted `ThemeMode` preference into the real Desktop Compose theme.

## Delivered

- `SYSTEM_DEFAULT` follows Compose/JVM system dark-theme state.
- `LIGHT` and `DARK` force Material light/dark palettes.
- The composition root observes preference changes immediately and rebuilds `MaterialTheme.colors`.
- The Desktop shell no longer contains hard-coded light-only sidebar, secondary-text, border or tag colors; these derive from the active Material palette.
- Settings presents localized System default / Light / Dark labels.
- Theme selection persists across Desktop workspace restart.

Dynamic Android color is intentionally not claimed for Desktop; the Desktop appearance contract is System/Light/Dark.
