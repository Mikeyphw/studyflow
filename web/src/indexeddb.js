"use strict";

import { migrateStudyState, validateStudyState } from "./domain.js";

export const STUDYFLOW_DB_NAME = "studyflow-local";
export const STUDYFLOW_DB_VERSION = 1;
export const STATE_STORE = "state";
export const ATTACHMENT_STORE = "attachments";
export const CURRENT_STATE_KEY = "current";

function clone(value) { return value == null ? value : JSON.parse(JSON.stringify(value)); }
function requestResult(request) {
    return new Promise((resolve, reject) => {
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error("IndexedDB request failed"));
    });
}
function transactionDone(transaction) {
    return new Promise((resolve, reject) => {
        transaction.oncomplete = () => resolve();
        transaction.onerror = () => reject(transaction.error ?? new Error("IndexedDB transaction failed"));
        transaction.onabort = () => reject(transaction.error ?? new Error("IndexedDB transaction aborted"));
    });
}
function idFactory() {
    return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2,12)}`;
}

export async function openStudyFlowDatabase({ indexedDBFactory = globalThis.indexedDB, name = STUDYFLOW_DB_NAME } = {}) {
    if (!indexedDBFactory?.open) throw new TypeError("IndexedDB is unavailable");
    const request = indexedDBFactory.open(name, STUDYFLOW_DB_VERSION);
    request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STATE_STORE)) db.createObjectStore(STATE_STORE);
        if (!db.objectStoreNames.contains(ATTACHMENT_STORE,)) db.createObjectStore(ATTACHMENT_STORE, { keyPath: "id" });
    };
    return requestResult(request);
}

async function readCurrentState(db) {
    const transaction = db.transaction(STATE_STORE, "readonly");
    const request = transaction.objectStore(STATE_STORE).get(CURRENT_STATE_KEY);
    const value = await requestResult(request);
    await transactionDone(transaction);
    return value ?? null;
}
async function writeCurrentState(db, state) {
    const transaction = db.transaction(STATE_STORE, "readwrite");
    transaction.objectStore(STATE_STORE).put(clone(state), CURRENT_STATE_KEY);
    await transactionDone(transaction);
}
async function clearAll(db) {
    const transaction = db.transaction([STATE_STORE, ATTACHMENT_STORE], "readwrite");
    transaction.objectStore(STATE_STORE).clear();
    transaction.objectStore(ATTACHMENT_STORE).clear();
    await transactionDone(transaction);
}

export function createIndexedDbAttachmentRepository(db, { now = () => new Date().toISOString() } = {}) {
    if (!db) throw new TypeError("Attachment repository requires an IndexedDB database");
    return {
        backend: "indexeddb",
        async put(file, { id = null, name = null } = {}) {
            if (!(file instanceof Blob)) throw new TypeError("Attachment must be a Blob or File");
            const record = {
                id: id ?? `attachment-${idFactory()}`,
                name: name ?? (typeof file.name === "string" && file.name ? file.name : "attachment"),
                mimeType: file.type || "application/octet-stream",
                sizeBytes: file.size,
                lastModified: Number.isFinite(Number(file.lastModified)) ? Number(file.lastModified) : null,
                createdAt: now(),
                blob: file,
            };
            const transaction = db.transaction(ATTACHMENT_STORE, "readwrite");
            transaction.objectStore(ATTACHMENT_STORE).put(record);
            await transactionDone(transaction);
            const { blob, ...metadata } = record;
            return metadata;
        },
        async get(id) {
            const transaction = db.transaction(ATTACHMENT_STORE, "readonly");
            const record = await requestResult(transaction.objectStore(ATTACHMENT_STORE).get(id));
            await transactionDone(transaction);
            return record ?? null;
        },
        async list() {
            const transaction = db.transaction(ATTACHMENT_STORE, "readonly");
            const records = await requestResult(transaction.objectStore(ATTACHMENT_STORE).getAll());
            await transactionDone(transaction);
            return (records ?? []).map(({ blob, ...metadata }) => metadata).sort((a,b)=>String(a.name).localeCompare(String(b.name)));
        },
        async delete(id) {
            const transaction = db.transaction(ATTACHMENT_STORE, "readwrite");
            transaction.objectStore(ATTACHMENT_STORE).delete(id);
            await transactionDone(transaction);
        },
        async clear() {
            const transaction = db.transaction(ATTACHMENT_STORE, "readwrite");
            transaction.objectStore(ATTACHMENT_STORE).clear();
            await transactionDone(transaction);
        },
    };
}

export async function createIndexedDbPersistence({ indexedDBFactory = globalThis.indexedDB, legacyRepository = null, dbName = STUDYFLOW_DB_NAME, now = () => new Date().toISOString() } = {}) {
    const db = await openStudyFlowDatabase({ indexedDBFactory, name: dbName });
    let raw = await readCurrentState(db);
    let status = "empty";
    let migrationReason = null;
    if (raw == null && legacyRepository?.load) {
        const legacy = legacyRepository.load();
        if (legacy?.state) {
            raw = legacy.state;
            status = "migrated";
            migrationReason = "Migrated browser localStorage state into IndexedDB.";
        }
    }
    let cache = null;
    if (raw != null) {
        const candidate = migrateStudyState(clone(raw), { now: now() });
        const errors = validateStudyState(candidate);
        if (errors.length) throw new TypeError(`IndexedDB state failed validation: ${errors[0]}`);
        cache = candidate;
        if (status !== "migrated") status = raw.schemaVersion === candidate.schemaVersion ? "loaded" : "migrated";
        await writeCurrentState(db, cache);
        if (status === "migrated" && legacyRepository?.clear) legacyRepository.clear();
    }
    let queue = Promise.resolve();
    let lastAsyncError = null;
    const repository = {
        backend: "indexeddb",
        load() { return { status, state: clone(cache), reason: migrationReason }; },
        save(state) {
            const errors = validateStudyState(state);
            if (errors.length) return { status: "invalid", reason: `Refused to save invalid state: ${errors[0]}` };
            cache = clone(state); status = "loaded"; lastAsyncError = null;
            queue = queue.then(() => writeCurrentState(db, cache)).catch((error) => { lastAsyncError = error; });
            return { status: "saved", reason: null };
        },
        clear() {
            cache = null; status = "empty"; lastAsyncError = null;
            queue = queue.then(() => clearAll(db)).catch((error) => { lastAsyncError = error; });
            if (legacyRepository?.clear) legacyRepository.clear();
            return { status: "cleared", reason: null };
        },
        async flush() {
            await queue;
            if (lastAsyncError) throw lastAsyncError;
        },
        close() { db.close?.(); },
    };
    return { backend: "indexeddb", repository, attachments: createIndexedDbAttachmentRepository(db, { now }), db };
}
