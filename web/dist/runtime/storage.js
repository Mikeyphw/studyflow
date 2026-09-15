"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LEGACY_STORAGE_KEYS = exports.STORAGE_KEY = void 0;
exports.createBrowserRepository = createBrowserRepository;
exports.createBrowserPersistence = createBrowserPersistence;
const domain_1 = require("./domain");
const indexeddb_1 = require("./indexeddb");
exports.STORAGE_KEY = `studyflow.local.state.v${domain_1.STATE_SCHEMA_VERSION}`;
exports.LEGACY_STORAGE_KEYS = Object.freeze(["studyflow.local.state.v6", "studyflow.local.state.v5", "studyflow.local.state.v4", "studyflow.local.state.v3", "studyflow.local.state.v2", "studyflow.local.state.v1"]);
function resolveBrowserStorage() { var _a; try {
    return (_a = globalThis.localStorage) !== null && _a !== void 0 ? _a : null;
}
catch {
    return null;
} }
function result(status, extra = {}) { return { status, ...extra }; }
function parseState(raw, now) {
    let state;
    try {
        state = JSON.parse(raw);
    }
    catch {
        return result("invalid", { state: null, reason: "Saved local state is not valid JSON." });
    }
    const migrated = (0, domain_1.migrateStudyState)(state, { now });
    const errors = (0, domain_1.validateStudyState)(migrated);
    if (errors.length)
        return result("invalid", { state: null, reason: `Saved local state failed validation: ${errors[0]}` });
    const originalVersion = typeof state === "object" && state !== null && "schemaVersion" in state ? Number(state.schemaVersion) : null;
    return result(originalVersion === domain_1.STATE_SCHEMA_VERSION ? "loaded" : "migrated", { state: migrated, reason: null });
}
function createBrowserRepository({ storage = resolveBrowserStorage(), key = exports.STORAGE_KEY, legacyKeys = exports.LEGACY_STORAGE_KEYS, now = () => new Date().toISOString(), } = {}) {
    return {
        backend: "localstorage", key,
        load() {
            if (!storage)
                return result("unavailable", { state: null, reason: "Browser local storage is unavailable." });
            try {
                const raw = storage.getItem(key);
                if (raw != null)
                    return parseState(raw, now());
                for (const legacyKey of legacyKeys) {
                    if (legacyKey === key)
                        continue;
                    const legacyRaw = storage.getItem(legacyKey);
                    if (legacyRaw == null)
                        continue;
                    const parsed = parseState(legacyRaw, now());
                    if (parsed.state)
                        return { ...parsed, status: "migrated", legacyKey };
                    return parsed;
                }
                return result("empty", { state: null, reason: null });
            }
            catch (error) {
                return result("unavailable", { state: null, reason: `Local storage read failed: ${error instanceof Error ? error.message : String(error)}` });
            }
        },
        save(state) {
            if (!storage)
                return result("unavailable", { reason: "Browser local storage is unavailable." });
            const errors = (0, domain_1.validateStudyState)(state);
            if (errors.length)
                return result("invalid", { reason: `Refused to save invalid state: ${errors[0]}` });
            try {
                storage.setItem(key, JSON.stringify(state));
            }
            catch (error) {
                return result("unavailable", { reason: `Local storage write failed: ${error instanceof Error ? error.message : String(error)}` });
            }
            return result("saved", { reason: null });
        },
        clear() { if (!storage)
            return result("unavailable", { reason: "Browser local storage is unavailable." }); try {
            storage.removeItem(key);
            for (const legacyKey of legacyKeys)
                storage.removeItem(legacyKey);
        }
        catch (error) {
            return result("unavailable", { reason: `Local storage clear failed: ${error instanceof Error ? error.message : String(error)}` });
        } return result("cleared", { reason: null }); },
    };
}
function createUnavailableRepository(reasonText) {
    return {
        backend: "unavailable",
        load() { return result("unavailable", { state: null, reason: reasonText }); },
        save() { return result("unavailable", { reason: reasonText }); },
        clear() { return result("unavailable", { reason: reasonText }); },
        async flush() { throw new TypeError(reasonText); },
    };
}
function unavailableAttachments(reason) {
    const fail = async () => { throw new TypeError(reason); };
    return { put: fail, get: fail, list: async () => [], delete: async () => { await fail(); }, clear: async () => undefined };
}
async function createBrowserPersistence({ storage = resolveBrowserStorage(), indexedDBFactory = globalThis.indexedDB, now = () => new Date().toISOString(), dbName, } = {}) {
    const legacyRepository = createBrowserRepository({ storage, now });
    if (!(indexedDBFactory === null || indexedDBFactory === void 0 ? void 0 : indexedDBFactory.open))
        return { backend: "localstorage", repository: legacyRepository, attachments: unavailableAttachments("Attachment storage requires IndexedDB."), fallbackReason: "IndexedDB is unavailable; using localStorage compatibility mode." };
    try {
        return await (0, indexeddb_1.createIndexedDbPersistence)({ indexedDBFactory, legacyRepository, now, dbName });
    }
    catch (error) {
        const message = `IndexedDB failed to initialize; StudyFlow is running with persistence disabled instead of silently falling back: ${error instanceof Error ? error.message : String(error)}`;
        return { backend: "unavailable", repository: createUnavailableRepository(message), attachments: unavailableAttachments("Attachment storage is unavailable because IndexedDB failed to initialize."), fallbackReason: message };
    }
}
