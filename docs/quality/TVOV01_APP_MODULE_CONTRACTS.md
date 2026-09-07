# TVOV01 app module contracts

TVOV01 introduces a real Android TV module while keeping StudyFlow size-conscious.

Required contracts:

- `settings.gradle.kts` includes `:app-tv`.
- `app-tv` is an Android application module, not a library and not a fork of `:app`.
- The application id is `com.mikeyphw.studyflow.tv`.
- The manifest exposes a Leanback launcher and marks touchscreen as optional.
- The module has no phone feature-module dependencies in TVOV01.
- The module has no Cast SDK, MediaRouter, AndroidX TV UI, or Compose runtime dependencies in TVOV01.
- Release configuration enables minification and resource shrinking for the future TV release artifact.
- `studyflow_tv` Devtool validation runs real `:app-tv` compile, unit-test, package, and lint tasks.


## Compose boundary

`app-tv` uses the shared Android application convention, but the convention must disable Compose for `:app-tv` until a later measured UI overlay intentionally adds a TV UI dependency. TVOV01 must not add Compose Runtime merely to satisfy the Compose compiler plugin.
