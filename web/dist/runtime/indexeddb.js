"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RECOVERY_STATE_KEY = exports.CURRENT_STATE_KEY = exports.ATTACHMENT_STORE = exports.STATE_STORE = exports.STUDYFLOW_DB_VERSION = exports.STUDYFLOW_DB_NAME = void 0;
exports.openStudyFlowDatabase = openStudyFlowDatabase;
exports.createIndexedDbAttachmentRepository = createIndexedDbAttachmentRepository;
exports.createIndexedDbPersistence = createIndexedDbPersistence;
const domain_1 = require("./domain");
exports.STUDYFLOW_DB_NAME = "studyflow-local";
exports.STUDYFLOW_DB_VERSION = 1;
exports.STATE_STORE = "state";
exports.ATTACHMENT_STORE = "attachments";
exports.CURRENT_STATE_KEY = "current";
exports.RECOVERY_STATE_KEY = "recovery";
function clone(value) { return value == null ? value : structuredClone(value); }
function requestResult(request) { return new Promise((resolve, reject) => { request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error ?? new Error("IndexedDB request failed")); }); }
function transactionDone(transaction) { return new Promise((resolve, reject) => { transaction.oncomplete = () => resolve(); transaction.onerror = () => reject(transaction.error ?? new Error("IndexedDB transaction failed")); transaction.onabort = () => reject(transaction.error ?? new Error("IndexedDB transaction aborted")); }); }
function idFactory() { return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`; }
async function openStudyFlowDatabase({ indexedDBFactory = globalThis.indexedDB, name = exports.STUDYFLOW_DB_NAME } = {}) {
    if (!indexedDBFactory?.open)
        throw new TypeError("IndexedDB is unavailable");
    const request = indexedDBFactory.open(name, exports.STUDYFLOW_DB_VERSION);
    request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(exports.STATE_STORE))
            db.createObjectStore(exports.STATE_STORE);
        if (!db.objectStoreNames.contains(exports.ATTACHMENT_STORE))
            db.createObjectStore(exports.ATTACHMENT_STORE, { keyPath: "id" });
    };
    return requestResult(request);
}
async function readStoredState(db, key) {
    const tx = db.transaction(exports.STATE_STORE, "readonly");
    const value = await requestResult(tx.objectStore(exports.STATE_STORE).get(key));
    await transactionDone(tx);
    return value ?? null;
}
async function writeCurrentOnly(db, state) {
    const tx = db.transaction(exports.STATE_STORE, "readwrite");
    tx.objectStore(exports.STATE_STORE).put(clone(state), exports.CURRENT_STATE_KEY);
    await transactionDone(tx);
}
async function writeStateWithRecovery(db, next, previous) {
    const tx = db.transaction(exports.STATE_STORE, "readwrite");
    const store = tx.objectStore(exports.STATE_STORE);
    if (previous)
        store.put(clone(previous), exports.RECOVERY_STATE_KEY);
    store.put(clone(next), exports.CURRENT_STATE_KEY);
    await transactionDone(tx);
}
async function clearAll(db) {
    const tx = db.transaction([exports.STATE_STORE, exports.ATTACHMENT_STORE], "readwrite");
    tx.objectStore(exports.STATE_STORE).clear();
    tx.objectStore(exports.ATTACHMENT_STORE).clear();
    await transactionDone(tx);
}
function checkedState(raw, now) {
    const candidate = (0, domain_1.migrateStudyState)(clone(raw), { now });
    const errors = (0, domain_1.validateStudyState)(candidate);
    if (errors.length)
        throw new TypeError(`IndexedDB state failed validation: ${errors[0]}`);
    return candidate;
}
function createIndexedDbAttachmentRepository(db, { now = () => new Date().toISOString() } = {}) {
    if (!db)
        throw new TypeError("Attachment repository requires an IndexedDB database");
    return {
        async put(file, { id = null, name = null } = {}) {
            if (!(file instanceof Blob))
                throw new TypeError("Attachment must be a Blob or File");
            const fileName = file instanceof File && file.name ? file.name : "attachment";
            const lastModified = file instanceof File && Number.isFinite(file.lastModified) ? file.lastModified : null;
            const record = { id: id ?? `attachment-${idFactory()}`, name: name ?? fileName, mimeType: file.type || "application/octet-stream", sizeBytes: file.size, lastModified, createdAt: now(), blob: file };
            const tx = db.transaction(exports.ATTACHMENT_STORE, "readwrite");
            tx.objectStore(exports.ATTACHMENT_STORE).put(record);
            await transactionDone(tx);
            const { blob: _, ...metadata } = record;
            return metadata;
        },
        async get(id) {
            const tx = db.transaction(exports.ATTACHMENT_STORE, "readonly");
            const record = await requestResult(tx.objectStore(exports.ATTACHMENT_STORE).get(id));
            await transactionDone(tx);
            return record ?? null;
        },
        async list() {
            const tx = db.transaction(exports.ATTACHMENT_STORE, "readonly");
            const records = await requestResult(tx.objectStore(exports.ATTACHMENT_STORE).getAll());
            await transactionDone(tx);
            return (records ?? []).map(({ blob: _, ...metadata }) => metadata).sort((a, b) => a.name.localeCompare(b.name));
        },
        async delete(id) { const tx = db.transaction(exports.ATTACHMENT_STORE, "readwrite"); tx.objectStore(exports.ATTACHMENT_STORE).delete(id); await transactionDone(tx); },
        async clear() { const tx = db.transaction(exports.ATTACHMENT_STORE, "readwrite"); tx.objectStore(exports.ATTACHMENT_STORE).clear(); await transactionDone(tx); },
    };
}
async function createIndexedDbPersistence({ indexedDBFactory = globalThis.indexedDB, legacyRepository = null, dbName = exports.STUDYFLOW_DB_NAME, now = () => new Date().toISOString() } = {}) {
    const db = await openStudyFlowDatabase({ indexedDBFactory, name: dbName });
    let raw = await readStoredState(db, exports.CURRENT_STATE_KEY);
    let status = "empty";
    let reason = null;
    let cache = null;
    if (raw == null && legacyRepository?.load) {
        const legacy = legacyRepository.load();
        if (legacy?.state) {
            raw = legacy.state;
            status = "migrated";
            reason = "Migrated browser localStorage state into IndexedDB.";
        }
    }
    if (raw != null) {
        try {
            const candidate = checkedState(raw, now());
            cache = candidate;
            if (status !== "migrated")
                status = raw.schemaVersion === candidate.schemaVersion ? "loaded" : "migrated";
            await writeStateWithRecovery(db, candidate, raw.schemaVersion === candidate.schemaVersion ? null : raw);
            if (status === "migrated" && legacyRepository?.clear)
                legacyRepository.clear();
        }
        catch (currentError) {
            const recoveryRaw = await readStoredState(db, exports.RECOVERY_STATE_KEY);
            if (!recoveryRaw)
                throw currentError;
            try {
                cache = checkedState(recoveryRaw, now());
                status = "recovered";
                reason = "Recovered the last verified local checkpoint after the current IndexedDB state failed validation.";
                await writeCurrentOnly(db, cache);
            }
            catch {
                throw currentError;
            }
        }
    }
    let queue = Promise.resolve();
    let lastAsyncError = null;
    const repository = {
        backend: "indexeddb",
        load() { return { status, state: clone(cache), reason }; },
        save(state) {
            const errors = (0, domain_1.validateStudyState)(state);
            if (errors.length)
                return { status: "invalid", reason: `Refused to save invalid state: ${errors[0]}` };
            const previous = clone(cache);
            const next = clone(state);
            cache = next;
            status = "loaded";
            reason = null;
            lastAsyncError = null;
            queue = queue.then(() => writeStateWithRecovery(db, next, previous)).catch((error) => { lastAsyncError = error; });
            return { status: "saved", reason: null };
        },
        clear() {
            cache = null;
            status = "empty";
            reason = null;
            lastAsyncError = null;
            queue = queue.then(() => clearAll(db)).catch((error) => { lastAsyncError = error; });
            legacyRepository?.clear?.();
            return { status: "cleared", reason: null };
        },
        async flush() { await queue; if (lastAsyncError)
            throw lastAsyncError; },
        close() { db.close(); },
    };
    return { backend: "indexeddb", repository, attachments: createIndexedDbAttachmentRepository(db, { now }), db };
}
