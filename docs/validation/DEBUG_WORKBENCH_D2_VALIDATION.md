# Debug Workbench D2 Validation

Required target-side gates: `:core:diagnostics:test`, `:core:navigation:test`, `:feature:settings:compileDebugKotlin`, `:feature:settings:testDebugUnitTest`, `:app:compileDebugKotlin`, resources, lint, and debug packaging.

Independent artifact audits must parse JSONL with escaped values, reject malformed lines without crashing, verify narrow FileProvider scope, and prove that copy/export surfaces consume only D1-sanitized data.
