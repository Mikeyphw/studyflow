# Debug Workbench D3 probe contract

- Twelve typed probes, with explicit safety, timeout and default-plan requirements.
- Read-only suite excludes every visible-side-effect probe.
- No destructive probe exists in D3.
- Machine facts are re-sanitized before UI or timeline use.
- Platform limitations are represented as blocked or warning states.
- Process-exit descriptions and trace streams are never collected.
- Backup dry-run must report zero changed database rows.
