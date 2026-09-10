# REL01 — Target release validation runner

REL01 is the post-SFLP operational overlay. It does not add another protocol feature after SFLP24 and
it is not a Phase 65. It adds a target-machine runner around the already defined final release gates.

The runner keeps the final verdict fail-closed:

- signed Release APK and AAB evidence must come from the target build host;
- connected Android suites must run against exactly one attached device;
- the restore/restart journey must bind to the exact signed APK SHA-256;
- manual device observations must be recorded through `record_final_device_acceptance.py` with
  non-empty evidence text; and
- `verifyFinalReleaseVerdict` remains the only source of `releaseReady=true`.

Use `python3 scripts/run_target_release_validation.py --mode plan` to generate the local run plan and
then run the automated/device/verdict modes on the target Android build machine.
