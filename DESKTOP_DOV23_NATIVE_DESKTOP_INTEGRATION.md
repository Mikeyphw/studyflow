# Desktop DOV23 — native Desktop integration

DOV23 replaces the production no-op platform adapter with JVM Desktop/AWT/Swing integration while keeping all native effects behind testable ports.

## Delivered

- Swing file chooser for import and export destinations, with headless-safe cancellation.
- `java.awt.Desktop` open-with and HTTP(S) browser launching through a filesystem port.
- System-tray informational notifications when supported, with safe no-op fallback on headless/unsupported hosts.
- Library file picker plus native external open actions for copied local materials and web references.
- Native chooser shortcuts for portability/import/export paths.
- Startup `studyflow://` routing for routes, Study Plans and Library materials.
- Production bootstrap defaults to `NativeDesktopPlatformPorts`; tests and embedders can continue using `NoOpDesktopPlatformPorts` or fakes.

Deep-link routing is application-level and accepts a `studyflow://...` startup argument. OS installer/protocol registration can be supplied by packaging without changing routing semantics.
