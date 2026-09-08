# TVOV20 — Real-device acceptance and release seal

TVOV20 adds a real connected-device acceptance path and the final optimized TV APK release gate.

The final Release seal requires the optimized Release APK, R8 mapping evidence, the 25 MiB target, package/Leanback identity, the cumulative TV contract chain, TV unit tests, and Debug/Release lint. Production signing is verified with `apksigner` whenever all `STUDYFLOW_RELEASE_*` credentials are configured; absence of local signing secrets is reported as a signing skip rather than silently claiming a production-signed artifact.

`verifyStudyFlowTvRealDeviceAcceptance` is device-conditional. When exactly one usable ADB device (or `STUDYFLOW_TV_SERIAL`) is available it must be a Leanback device, the Debug APK is installed/upgraded, `StudyFlowTvActivity` launches, D-pad key events are accepted, the process remains alive, and the installed package reports version code 20 / `0.1.0-tv20`. When ADB is unavailable or no usable device is connected, the task exits successfully with a clean skip and does not claim physical-device acceptance.

Release truthfulness: TVOV20 does not invent a Cast SDK or network transport. TVOV16's transport-deferred boundary remains visible; this seal proves the TV APK, package, D-pad shell, shared control/cast state contract, and optional connected-TV acceptance, not an unimplemented wire transport between two physical devices.
