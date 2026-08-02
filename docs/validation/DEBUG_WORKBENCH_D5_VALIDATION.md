# D5 validation

Required focused tasks:

- `:core:diagnostics:test`
- `:core:data:testDebugUnitTest`
- `:feature:settings:compileDebugKotlin`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`

The D5 gates cover aggregate-only database evidence, migration-chain reachability, non-destructive drill
model invariants, missing-plan behavior, event pairing, cancellation, recoverable failures, and fatal errors.
