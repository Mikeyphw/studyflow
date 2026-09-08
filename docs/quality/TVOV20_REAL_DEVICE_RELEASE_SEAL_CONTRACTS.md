# TVOV20 real-device/release seal contracts

The Release APK gate requires optimized Release packaging, R8 `mapping.txt`, package/Leanback identity, ZIP integrity, unit tests, Debug/Release lint, and a Release APK no larger than 25 MiB.

Connected-device acceptance is conditional and fail-closed once a device is selected: it must expose Leanback, accept install/upgrade of the TV APK, launch the TV activity, survive D-pad smoke input, and expose the expected installed package/version. A missing or unusable ADB device is a clean no-device skip and must never be represented as physical-device acceptance.

Production signing is verified when signing credentials are present. Missing local signing credentials are reported as a signing skip; partially configured credentials are rejected by the TV Gradle configuration.

The seal keeps TVOV16's network transport deferral explicit. It validates the shared paired-phone/cast intent-state contract but does not claim a physical phone-to-TV wire transport that is not present in source.
