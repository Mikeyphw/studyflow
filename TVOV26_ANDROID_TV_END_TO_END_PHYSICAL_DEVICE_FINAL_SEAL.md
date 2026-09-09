# TVOV26 — End-to-end physical-device final seal

TVOV26 is the final corrective Android TV overlay. It is deliberately fail-closed: the physical product seal requires two real devices (one non-TV Android phone and one Leanback TV), optimized signed Release artifacts, and machine-readable physical evidence. There is no skip for absent ADB devices.

The runner drives the public Release UI with ADB/UIAutomator. It proves fresh six-digit pairing, authenticated phone navigation, live cast acknowledgment, a real canonical domain mutation on the phone-owned persistence layer, TV restart/resync, phone restart/resync, STOP_CAST returning the TV to the truthful waiting state, and revoke blocking paired control. Exact local Release APK SHA-256 values must match the bytes installed on both devices.

TVOV26 also fixes the final restart gap by privately persisting only the user's explicit active-cast plan id. It never persists StudyFlow presentation data outside canonical repositories. Stop cast, revoke, forget, expiry/session invalidation, or a changed default plan clears the intent.

Source validation proves this framework and all TVOV00–26 contracts exist. Physical evidence remains separate and mandatory: `verifyStudyFlowTvPhysicalTwoDeviceAcceptance` records it and `verifyStudyFlowTvFinalProductSeal` refuses to seal without it.

Optimized Release runtime acceptance is mandatory and bound to the exact installed artifact hashes.

Pending STOP_CAST intent is also persisted until acknowledgment so a phone-process restart cannot resurrect a cast the user already stopped.

Release-path hardening: `:core:tvlink` now ships the required `consumer-rules.pro`. The shared Android-library convention declares that consumer file for every Android library, so the final TVOV26 contract audits all such modules before optimized Release packaging.
