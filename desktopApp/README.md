# StudyFlow Desktop App

This module hosts the Compose Multiplatform Desktop application.

DOV02 adds the first bootable desktop shell. DOV03 seals the initial shared
boundary so desktop can reuse JVM-safe StudyFlow logic without copying Android
screens or depending on Android-only modules.

## Shared boundary

Allowed desktop project dependencies for this stage:

- `:core:model`
- `:core:domain`
- `:core:portability`

Forbidden for this stage:

- `:app`
- `:feature:*`
- Android persistence modules such as `:core:database` and `:core:data`
- Android design/navigation/resources modules such as `:core:designsystem`,
  `:core:navigation`, and `:core:i18n`

Do not place Android-only APIs or copied Android screen logic here.

Keep desktop UI, filesystem, shortcuts, tray, and native packaging adapters
behind small desktop-specific boundaries. The `DesktopPlatformPorts` interfaces reserve desktop-safe seams for future
filesystem, file-dialog, tray, and notification adapters. Real repository wiring
and persistence adapters are intentionally deferred to the next desktop overlay.
