#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        raise AssertionError(f"missing required OV24 file: {rel}")
    return path.read_text()


def check(name: str, condition: bool, detail: str) -> None:
    if not condition:
        raise AssertionError(f"{name}: {detail}")


def run(command, cwd=ROOT):
    result = subprocess.run(command, cwd=cwd, text=True, capture_output=True)
    if result.returncode != 0:
        raise AssertionError(
            f"command failed ({' '.join(command)}):\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}"
        )
    return result


def main() -> int:
    checks = []
    def require(name: str, condition: bool, detail: str) -> None:
        check(name, condition, detail)
        checks.append(name)

    indexeddb = read("web/src/indexeddb.ts")
    storage = read("web/src/storage.ts")
    portable = read("web/src/portable.ts")
    app = read("web/src/react/App.tsx")
    pwa = read("web/src/pwa.ts")
    main_ts = read("web/src/main.tsx")
    attention = read("web/src/attention.ts")
    sflp = read("web/src/sflp_link.ts")
    fake_idb = read("web/tests/fake_indexeddb.mjs")
    tests = read("web/tests/test_ov24_web_persistence_bootstrap_secret_hygiene.py")
    build = read("build.gradle.kts")
    hook = read("scripts/validate_ov24_web_persistence_bootstrap_secret_hygiene.sh")
    docs = read("docs/remediation/OV24_WEB_PERSISTENCE_BOOTSTRAP_PORTABLE_SECRET_HYGIENE.md")
    changelog = read("CHANGELOG.md")
    ledger = read("docs/roadmap/FINAL_AUDIT_REMEDIATION_CLOSURE_LEDGER.md")

    require("OV24-pending-save", "status=\"pending\"" in indexeddb and "IndexedDB write queued" in indexeddb, "IndexedDB saves must stay pending until the transaction commits")
    require("OV24-durable-conflict", "durableWriteConflict" in indexeddb and "another StudyFlow tab" in indexeddb and "expectedRevision" in indexeddb, "multi-tab stale writes must be rejected before commit")
    require("OV24-transaction-abort", "tx.abort()" in indexeddb and "transactionDone(tx)" in indexeddb, "conflicting IndexedDB writes must abort and await transaction completion")
    require("OV24-idb-open-failure-visible", "createUnavailableRepository" in storage and "persistence disabled instead of silently falling back" in storage, "IndexedDB open failure must be visible and must not silently downgrade to localStorage")
    require("OV24-localstorage-compat-only-when-no-idb", "IndexedDB is unavailable; using localStorage compatibility mode" in storage and "legacyRepository=createBrowserRepository" in storage.replace(" ", ""), "localStorage compatibility must remain explicit only when IndexedDB API is absent")
    require("OV24-size-bound-imports", "MAX_PLAN_CONTENT_IMPORT_BYTES" in portable and "assertPortableByteLength" in portable and "before materialization" in portable, "portable and plan-content imports must be byte bounded before materialization")
    require("OV24-runtime-focus-sanitized", "sanitizePortableRuntimeState" in portable and "createFocusState({durationMinutes" in portable.replace(" ", ""), "portable import/export must strip live focus runtime state")
    require("OV24-ui-bounded-readers", "readBoundedFileText(file,MAX_PLAN_CONTENT_IMPORT_BYTES" in app.replace(" ", "") and "readBoundedFileBytes(file,MAX_ENCRYPTED_PORTABLE_BYTES" in app.replace(" ", ""), "UI file readers must check file.size before reading bytes/text")
    require("OV24-secret-clearing", "finally{setExportPassword(\"\");setExportConfirm(\"\")}" in app and "finally{setImportPassword(\"\")}" in app, "portable passwords must be cleared on success and failure")
    require("OV24-attachment-honesty", "File upload is disabled because this browser session has no IndexedDB attachment storage" in app and "Material removed and local blob cleaned" in app, "attachment-less sessions must be honest and delete blobs with materials")
    require("OV24-async-success-copy", "isPromiseLike(r)" in app and ".then((value)=>{if(msg)setToast(msg);returnvalue})" in app.replace(" ", ""), "async UI success copy must wait for promise fulfillment")
    require("OV24-sw-retry", "registrationPromise = null" in pwa, "service-worker registration failure must not poison later retries")
    require("OV24-bfcache-resume", "event.persisted" in main_ts and "attentionRuntime?.start()" in main_ts and "terminalPageHide" in main_ts, "BFCache restore must resume attention runtime without disposing bridges")
    require("OV24-denied-storage-attention", "safeBrowserLocalStorage" in attention and "options.storage===undefined?safeBrowserLocalStorage()" in attention.replace(" ", ""), "attention bootstrap must tolerate localStorage getter denial")
    require("OV24-denied-storage-sflp", "StudyFlowMemoryStorage" in sflp and "installStudyFlowWebSflpPeer" in sflp and "if(!storage)return null" in sflp, "web SFLP bootstrap must tolerate localStorage getter denial")
    require("OV24-fake-idb-abort", "abort()" in fake_idb, "fake IndexedDB must model transaction aborts for behavior tests")
    require("OV24-focused-tests", "test_indexeddb_save_is_pending_until_flush_and_stale_tab_is_rejected" in tests and "test_portable_runtime_focus_is_sanitized" in tests and "test_denied_localstorage_does_not_crash_bootstrap" in tests, "focused OV24 behavior tests are missing")
    require("OV24-gradle-task", "verifyOv24WebPersistenceBootstrapSecretHygiene" in build, "Gradle verifier task missing")
    require("OV24-validation-hook", "verify_ov24_web_persistence_bootstrap_secret_hygiene.py" in hook and "gradle --stop" not in hook and "--stop" not in hook, "validation hook must run OV24 verifier without stopping daemons")
    require("OV24-docs", "Overlay 24 of 30" in docs and "Web persistence, bootstrap, portable import & secret hygiene" in docs, "implementation docs missing overlay identity")
    require("OV24-changelog", "OV24" in changelog and "Web persistence" in changelog, "changelog missing OV24 entry")
    require("OV24-ledger", "OV24 — Web persistence" in ledger and "S10-14" in ledger and "DS2-48" in ledger, "closure ledger missing OV24 findings")

    run(["node", "scripts/compile_runtime.mjs"], cwd=WEB)
    run([sys.executable, "-m", "unittest", "tests/test_ov24_web_persistence_bootstrap_secret_hygiene.py", "-v"], cwd=WEB)
    print(f"OV24 Web persistence/bootstrap/portable/secret hygiene source evidence: PASS ({len(checks)} checks)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
