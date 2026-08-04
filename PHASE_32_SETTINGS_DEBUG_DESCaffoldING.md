# Phase 32: Settings and developer diagnostics de-scaffolding

Phase 32 removes the last visible diagnostic scaffolding from the normal Settings experience and makes the debug surface explicitly developer/support scoped.

Delivered:

- Normal Settings continues to show only user-facing app settings and keeps diagnostics behind the debug-build gate.
- The Settings entry is renamed to Developer diagnostics instead of Debug Workbench.
- Default fallback panels in the diagnostics route no longer say that probes or labs will arrive later.
- The probes fallback opens the runtime probe set already connected to the build.
- The labs fallback is an honest empty state for diagnostic routes without lab data.
- The Phase 32 verifier rejects placeholder/future wording and verifies EN/PT-BR resource parity.

Runtime behavior:

- Release builds keep the diagnostics entry hidden through `diagnosticsAvailable = BuildConfig.DEBUG`.
- The real app host still injects typed probes and diagnostic labs, so the fallback panels are only safety nets for previews/tests/alternate hosts.
