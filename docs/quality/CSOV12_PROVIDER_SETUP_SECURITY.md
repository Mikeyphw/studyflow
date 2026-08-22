# CSOV12 provider setup security

CSOV12 adds the provider setup safety layer for optional cloud sync.

Acceptance criteria:

- Keep offline remains available on a fresh install.
- Local folder setup uses a SAF tree URI and does not require an account.
- WebDAV setup requires HTTPS by default.
- WebDAV setup forbids credentials embedded in the endpoint URL.
- Remote providers require encrypted remote backups before saving.
- Diagnostics expose provider/location/secret-handle status only, never raw passwords or tokens.
