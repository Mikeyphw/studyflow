# TVOV26 end-to-end physical-device final-seal contracts

- Physical acceptance is fail-closed and requires two real devices: exactly one Leanback TV and one non-TV Android phone.
- Both optimized signed Release APKs must install and their installed base-APK SHA-256 values must equal the exact local artifacts.
- The real public UI must prove fresh pairing, authenticated navigation, LIVE cast receipt, canonical domain persistence, TV restart/resync, phone restart/resync, STOP_CAST, and revoke.
- A missing device, unsigned Release, absent usable default-plan data, missing observation, or stale/mismatched artifact is BLOCKED; there is no skip.
- Source/build validation never counts as physical evidence. The final product seal consumes only a PASS machine-readable evidence report.

Optimized Release runtime acceptance is mandatory and bound to the exact installed artifact hashes.

Pending STOP_CAST intent is also persisted until acknowledgment so a phone-process restart cannot resurrect a cast the user already stopped.
