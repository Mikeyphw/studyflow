# TVOV01 Android TV app module

TVOV01 creates the first real StudyFlow TV APK target without turning TV support into a fork.

## Delivered

- Added `:app-tv` to `settings.gradle.kts`.
- Added a separate TV package id: `com.mikeyphw.studyflow.tv`.
- Added a Leanback launcher activity.
- Marked touchscreen as not required and Leanback as required.
- Added a minimal native Android TV placeholder screen.
- Added a TV build contract and unit test.
- Updated `studyflow_tv` Devtool validation to compile, test, package, and lint `:app-tv`.

## Size-first boundaries

TVOV01 deliberately avoids:

- `project(":app")`
- phone/tablet feature modules
- Compose runtime dependencies
- AndroidX TV UI dependencies
- MediaRouter
- Google Cast SDK
- duplicated repositories, databases, or sync engines

The TV APK starts as a receiving shell. Later overlays may add dependencies only when the size-budget
verifier and module-boundary contracts are updated intentionally.

## Next overlay

TVOV02 should measure and harden release packaging size before feature/UI dependencies are added.


## TVOV01 v2 correction

The shared Android application convention keeps Compose enabled for the phone app, but disables the Compose compiler and Compose build feature for `:app-tv`. This preserves the size-first native placeholder and avoids adding Compose Runtime to the TV APK before the TV UI phase.
