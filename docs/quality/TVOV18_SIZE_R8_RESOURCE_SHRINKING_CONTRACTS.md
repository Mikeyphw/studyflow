# TVOV18 size/R8/resource-shrinking contracts

- `:app-tv` Release must set `isMinifyEnabled = true` and `isShrinkResources = true`, making R8 minification and resource shrinking mandatory.
- Release must use `getDefaultProguardFile("proguard-android-optimize.txt")` plus the TV rules file.
- The TV rules file must not introduce a broad keep-all rule for `com.mikeyphw.studyflow`.
- The optimized artifact verifier must build/check a Release APK, require R8 `mapping.txt`, verify APK ZIP integrity, and enforce the 25 MiB product target (35 MiB remains the absolute architecture ceiling).
- `StudyFlowTvReleaseOptimizationPolicy` is separately unit-tested for target, above-target, missing-evidence, and hard-ceiling cases.
- The TV dependency budget remains unchanged: no Compose Runtime, AndroidX TV UI, Cast SDK, MediaRouter, `core:data`, `core:database`, or phone feature-module fanout.
