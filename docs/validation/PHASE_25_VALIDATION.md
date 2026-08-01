# Phase 25 Validation

The artifact validator runs Phase 0 through Phase 25 source contracts and checks the post-MVP gap closure
markers. It intentionally does not claim Android SDK execution.

Required Devtool phases after apply:

- Gradle help/configuration
- app and affected module Kotlin compilation
- unit-test compilation and execution
- app resource processing
- app lint
- app debug assembly
- optional real-device smoke test for notification and widget delivery

## Phase 25 r2 checks

The Phase 25 verifier now checks the quiet-hour invariant for all reminder candidates and requires the due-review anti-spam reschedule guard. The pure planner test suite covers quiet-hour suppression and delivery-reschedule eligibility.
