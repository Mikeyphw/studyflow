#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "core/model/src/main/kotlin/com/mikeyphw/studyflow/core/model/StudySyncProviderSetup.kt": [
        "StudySyncProviderSetupAction",
        "KEEP_OFFLINE",
        "StudySyncSecretHandle",
        "validateWebDav",
        "HTTPS_REQUIRED",
        "USERINFO_FORBIDDEN",
        "REMOTE_ENCRYPTION_REQUIRED",
        "diagnosticsFor",
        "redactUsername",
    ],
    "core/model/src/test/kotlin/com/mikeyphw/studyflow/core/model/StudySyncProviderSetupTest.kt": [
        "keep offline remains an available setup action",
        "webdav setup requires https credential handles",
        "provider diagnostics never expose raw webdav credentials",
        "remote providers require encrypted remote backups",
    ],
    "docs/quality/CSOV12_PROVIDER_SETUP_SECURITY.md": [
        "CSOV12 provider setup security",
        "forbids credentials embedded",
        "never raw passwords or tokens",
    ],
}
failures = []
for rel, markers in checks.items():
    path = ROOT / rel
    if not path.exists():
        failures.append(f"missing {rel}")
        continue
    text = path.read_text()
    for marker in markers:
        if marker not in text:
            failures.append(f"{rel}: missing {marker}")
if failures:
    raise SystemExit("CSOV12_PROVIDER_SETUP_SECURITY failed:\n" + "\n".join(failures))
print("CSOV12_PROVIDER_SETUP_SECURITY: PASS")
