# TVOV18 — Android TV size optimization, R8, and resource shrinking

TVOV18 turns the TV size promise into an executable optimized-Release gate.

Implemented:
- Release uses R8 minification plus resource shrinking with Android's optimized default ProGuard configuration;
- broad keep rules are intentionally absent so unused TV presentation code/resources remain shrinkable;
- dependency metadata is omitted from APK/AAB packaging where AGP supports the standard `dependenciesInfo` DSL;
- `StudyFlowTvReleaseOptimizationPolicy` independently models the 25 MiB target, 35 MiB hard ceiling, and required R8/shrinker evidence;
- `verifyStudyFlowTvSizeOptimization` proves the source/build configuration and dependency boundary before packaging;
- `verifyStudyFlowTvOptimizedReleaseArtifact` depends on `:app-tv:assembleRelease`, requires a Release APK plus R8 `mapping.txt`, verifies ZIP integrity, and fails unless the Release APK is at or below the 25 MiB target;
- Release lint is part of the `studyflow_tv` Devtool validation lane.

TVOV18 does not claim signing, install/upgrade, launcher packaging, or physical-device acceptance. Those remain TVOV19/TVOV20 work.
