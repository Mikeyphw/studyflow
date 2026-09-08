# TVOV04 focus primitive contracts

TVOV04 freezes the first native Android TV D-pad/focus layer.

## Contracts

- The focus policy is pure Kotlin.
- Remote input is represented as small commands: Up, Down, Left, Right, Select, Back, and Play/Pause.
- Android remote key codes map to the same small command enum.
- Focus targets use a 64dp minimum.
- Initial focus is deterministic and skips disabled items.
- Directional movement does not wrap at row/column edges.
- Select, Back, and Play/Pause do not unexpectedly move focus.
- Visible focus must be provided by native drawable state.
- No Compose Runtime is introduced.
- No AndroidX TV UI, Cast SDK, MediaRouter, phone feature modules, `core:data`, or `core:database` are introduced to `:app-tv`.
