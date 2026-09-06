# BH06 — WebDAV concurrent index acceptance

BH06 closes lost-update and ambiguous-index failure modes in the optional WebDAV / Nextcloud provider.

Required guarantees:

1. **Remote bundle writes are create-only.** Checkpoint uploads use `If-None-Match: *`; an existing path is reused only when a GET proves its bytes are identical. Different content is never overwritten silently.
2. **Index creation is atomic.** A missing `studyflow-sync-index.tsv` is created with `If-None-Match: *`.
3. **Index updates use compare-and-swap.** Existing indexes require an ETag and are written with `If-Match` against the revision that was read.
4. **Concurrent changes are retried without lost updates.** HTTP 409/412 causes a fresh read/merge/write, bounded to four attempts. Exhaustion fails safely instead of replacing a newer index.
5. **Same-process mutations are serialized.** Upload/delete index mutations share a coroutine `Mutex`; ETag CAS remains the cross-process/device guard.
6. **Index input is bounded and unambiguous.** Reads/writes are capped at 2 MiB and 2,048 rows; duplicate checkpoint IDs, duplicate bundle filenames, unsafe filenames, invalid hashes, negative metadata, or plaintext checkpoint rows are rejected.
7. **Delete participates in the same CAS loop.** A concurrent peer row is retained while the requested checkpoint is removed.

Validation:

- `python3 scripts/verify_bh06_webdav_concurrent_index.py`
- `python3 scripts/verify_csov10_real_providers.py`
- `python3 scripts/verify_csov12_provider_setup_security.py`
- Gradle task `:core:domain:test`
