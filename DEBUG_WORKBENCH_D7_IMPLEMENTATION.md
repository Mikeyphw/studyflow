# StudyFlow Debug Workbench D7: Runtime Self-Check and Support Export Seal

D7 adds the runtime self-check suite and support-bundle export seal on top of D6.

Implemented scope:

- `RuntimeSelfCheckRun` and `RuntimeSelfCheckHistory` retain only bounded, terminal, sanitized safe-suite results.
- `StudyFlowRuntimeSelfCheckSuite` orchestrates the read-only probe catalog and records start/terminal events.
- The suite rejects visible-side-effect probes and preserves cancellation and fatal JVM errors.
- The Debug Workbench Reports surface shows retained self-check history.
- Support-bundle ZIPs are inspected before sharing through an allowlist seal.
- The seal rejects traversal entries, database payloads, missing manifests, missing checksums, and missing redaction reports.
- Support bundle seal failures return an explicit UI result and do not launch the share sheet.

D7 does not add destructive probes, system log collection, automatic upload, alarm enumeration, or Android Auto Backup execution.
