# Debug Workbench D2 Shell

The shell is a secondary Settings destination. It reads only the already-redacted D1 JSONL timeline and exposes no raw database, preferences, paths, URIs, or user-authored study content.

The Reports surface can copy a sanitized status, copy the sanitized timeline, clear local diagnostic history, and share a checksummed support ZIP through the existing non-exported FileProvider. The only newly exposed path is `cache/support-bundles/`.

The shell uses Compose semantics for its primary heading and standard Material controls for accessibility and touch targets. There is no automatic upload.
