# D7 Runtime Self-Check and Support Export Seal

The runtime self-check suite is limited to `DebugProbeCatalog.safeSuiteIds`; every run stores terminal probe results only and keeps the newest bounded history.

The support bundle seal inspects generated ZIP entry names before sharing. Allowed entries are limited to recorder outputs: `events/current.jsonl`, `environment.json`, `manifest.json`, `redaction-report.txt`, and `checksums.sha256`.

The seal fingerprints rejected entry names rather than showing raw paths.
