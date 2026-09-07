# TVOV02 dependency budget contracts

TVOV02 keeps the Android TV APK small and clean before any heavier UI/control stack is introduced.

## Contracts

- Data extraction rules are explicit for Android 12+ device transfer/cloud backup behavior.
- Legacy full-backup rules remain explicit for older Android backup behavior.
- `android:allowBackup` remains `false`.
- No fixed screen orientation is declared by the TV activity.
- No minSdk-guarded `resizeableActivity` attribute is declared.
- The TV banner is a layer-list, not a large vector resource.
- The `:app-tv` dependency budget still excludes Cast SDK, MediaRouter, AndroidX TV UI, Compose Runtime, phone feature modules, `core:data`, and `core:database`.
- Release minification and resource shrinking remain enabled.
