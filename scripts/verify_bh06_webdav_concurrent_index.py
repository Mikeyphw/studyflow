#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
PROVIDER = ROOT / "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyFlowSyncProvider.kt"
TESTS = ROOT / "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/WebDavStudyFlowSyncProviderTest.kt"
DOC = ROOT / "docs/quality/BH06_WEBDAV_CONCURRENT_INDEX_ACCEPTANCE.md"


def require(value: bool, message: str) -> None:
    if not value:
        raise AssertionError(message)


def main() -> None:
    for path in (PROVIDER, TESTS, DOC):
        require(path.is_file(), f"missing BH06 file: {path.relative_to(ROOT)}")

    provider = PROVIDER.read_text()
    tests = TESTS.read_text()

    require('private val indexMutationMutex = Mutex()' in provider,
            "same-process WebDAV index mutations are not serialized")
    require('"If-None-Match" to "*"' in provider,
            "create-only HTTP precondition is missing")
    require('mapOf("If-Match" to revision)' in provider,
            "ETag compare-and-swap is missing")
    require('revision = response.headerValue("ETag")' in provider,
            "index ETag is not captured from GET")
    require('response.code !in IndexConflictStatusCodes' in provider,
            "409/412 retry path is missing")
    require('const val MaximumIndexMutationAttempts = 4' in provider,
            "WebDAV index retries must remain bounded")
    require('const val MaximumWebDavIndexBytes = 2 * 1024 * 1024' in provider,
            "WebDAV index byte budget changed")
    require('rows.size <= 2_048' in provider,
            "WebDAV index row budget is missing")
    require('duplicate checkpoint ids' in provider and 'duplicate checkpoint file names' in provider,
            "WebDAV index uniqueness validation is missing")
    require('existing.body.contentEquals(bundle.encryptedBytes)' in provider,
            "existing bundle path is reused without byte-for-byte idempotency proof")
    require('server must provide an ETag for safe concurrent sync-index updates' in provider,
            "provider still permits unsafe unconditional updates of an existing index")

    for name in (
        'concurrent index change is retried without losing peer checkpoint',
        'delete retries index compare and swap without erasing concurrent peer row',
        'retry after partial index failure reuses identical remote bundle without overwriting it',
        'webdav index rejects duplicate checkpoint ids',
    ):
        require(f'fun `{name}`()' in tests, f"missing BH06 regression: {name}")

    require('request.headers["If-None-Match"] == "*"' in tests,
            "tests do not assert create-only preconditions")
    require('request.headers.containsKey("If-Match")' in tests,
            "tests do not assert index CAS preconditions")

    print("BH06_WEBDAV_CONCURRENT_INDEX: PASS")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"BH06_WEBDAV_CONCURRENT_INDEX: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
