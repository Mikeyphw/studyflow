// Portable package v1 compatibility + v2 full local ownership package.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_PLAN_CONTENT_IMPORT_BYTES = exports.MAX_PORTABLE_V2_BYTES = exports.MAX_PORTABLE_V1_BYTES = exports.PORTABLE_FORMAT_VERSION = exports.LEGACY_PORTABLE_FORMAT_VERSION = exports.PORTABLE_FORMAT = void 0;
exports.assertPortableByteLength = assertPortableByteLength;
exports.sanitizePortableRuntimeState = sanitizePortableRuntimeState;
exports.stableStringify = stableStringify;
exports.createPortablePackage = createPortablePackage;
exports.createPortablePackageV2 = createPortablePackageV2;
exports.serializePortablePackage = serializePortablePackage;
exports.portablePackageVersion = portablePackageVersion;
exports.parsePortablePackage = parsePortablePackage;
exports.parsePortablePackageV2 = parsePortablePackageV2;
exports.buildPortableImportPreview = buildPortableImportPreview;
exports.applyPortableImportWithAttachments = applyPortableImportWithAttachments;
exports.portablePackageFilename = portablePackageFilename;
const domain_js_1 = require("./domain.js");
exports.PORTABLE_FORMAT = "studyflow-local-package";
exports.LEGACY_PORTABLE_FORMAT_VERSION = 1;
exports.PORTABLE_FORMAT_VERSION = 2;
exports.MAX_PORTABLE_V1_BYTES = 5 * 1024 * 1024;
exports.MAX_PORTABLE_V2_BYTES = 200 * 1024 * 1024;
exports.MAX_PLAN_CONTENT_IMPORT_BYTES = 5 * 1024 * 1024;
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function counts(state) { return Object.fromEntries(domain_js_1.ENTITY_COLLECTIONS.map((name) => [name, Array.isArray(state[name]) ? state[name].length : 0])); }
function byId(items) { return new Map((items !== null && items !== void 0 ? items : []).map((item) => [item.id, item])); }
function same(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
function byteLength(text) { return new TextEncoder().encode(text).length; }
function stableValue(value) { if (Array.isArray(value))
    return value.map(stableValue); if (value && typeof value === "object") {
    const out = {};
    for (const key of Object.keys(value).sort())
        out[key] = stableValue(value[key]);
    return out;
} return value; }
function assertPortableByteLength(byteCount, maximumBytes, label = "Portable import") { if (!Number.isFinite(byteCount) || byteCount < 0)
    throw new TypeError(`${label} size is unavailable; refusing to import before materialization.`); if (byteCount > maximumBytes)
    throw new TypeError(`${label} exceeds the ${Math.floor(maximumBytes / (1024 * 1024))} MiB local import limit.`); }
function sanitizePortableRuntimeState(state) { var _a, _b; const next = clone(state); next.focus = (0, domain_js_1.createFocusState)({ durationMinutes: (_b = (_a = state.focus) === null || _a === void 0 ? void 0 : _a.durationMinutes) !== null && _b !== void 0 ? _b : 25 }); return next; }
function stableStringify(value) { return JSON.stringify(stableValue(value)); }
function randomSuffix() { var _a, _b, _c; return (_c = (_b = (_a = globalThis.crypto) === null || _a === void 0 ? void 0 : _a.randomUUID) === null || _b === void 0 ? void 0 : _b.call(_a)) !== null && _c !== void 0 ? _c : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`; }
function bytesToBase64(bytes) { let binary = ""; const chunk = 0x8000; for (let i = 0; i < bytes.length; i += chunk)
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk)); return btoa(binary); }
function base64ToBytes(base64) { let binary; try {
    binary = atob(base64);
}
catch {
    throw new TypeError("Portable attachment payload is not valid base64");
} const bytes = new Uint8Array(binary.length); for (let i = 0; i < binary.length; i++)
    bytes[i] = binary.charCodeAt(i); return bytes; }
async function sha256Bytes(bytes) { const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes); return [...new Uint8Array(digest)].map(v => v.toString(16).padStart(2, "0")).join(""); }
async function sha256Text(text) { return sha256Bytes(new TextEncoder().encode(text)); }
// W11/W12 compatibility API: deliberately continues to emit the state-only v1 package.
function createPortablePackage(state, { now = () => new Date().toISOString() } = {}) {
    const errors = (0, domain_js_1.validateStudyState)(state);
    if (errors.length)
        throw new TypeError(`Cannot export invalid StudyFlow state: ${errors[0]}`);
    const exportedAt = now();
    const payload = sanitizePortableRuntimeState(state);
    const attachmentReferences = payload.materials.filter((material) => material.attachmentId).length;
    payload.materials = payload.materials.map((material) => { var _a; return ({ ...material, attachmentId: null, locator: String((_a = material.locator) !== null && _a !== void 0 ? _a : "").startsWith("local-blob:") ? null : material.locator }); });
    return { format: exports.PORTABLE_FORMAT, formatVersion: exports.LEGACY_PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(payload), attachments: { references: attachmentReferences, bytesIncluded: false } }, payload };
}
async function createPortablePackageV2(state, attachments, { now = () => new Date().toISOString() } = {}) {
    var _a;
    const errors = (0, domain_js_1.validateStudyState)(state);
    if (errors.length)
        throw new TypeError(`Cannot export invalid StudyFlow state: ${errors[0]}`);
    const payload = sanitizePortableRuntimeState(state);
    const exportedAt = now();
    const refs = [...new Set(payload.materials.map(m => m.attachmentId).filter(Boolean))];
    if (refs.length && !attachments)
        throw new TypeError("Full portable export requires local attachment storage");
    const attachmentPayloads = [];
    let totalBytes = 0;
    for (const attachmentId of refs) {
        const record = await attachments.get(attachmentId);
        if (!record)
            throw new TypeError(`Attachment referenced by state is missing: ${attachmentId}`);
        const bytes = new Uint8Array(await record.blob.arrayBuffer());
        const sha256 = await sha256Bytes(bytes);
        totalBytes += bytes.byteLength;
        attachmentPayloads.push({ id: record.id, name: record.name, mimeType: record.mimeType || "application/octet-stream", sizeBytes: bytes.byteLength, lastModified: (_a = record.lastModified) !== null && _a !== void 0 ? _a : null, createdAt: record.createdAt, sha256, base64: bytesToBase64(bytes) });
    }
    const payloadSha256 = await sha256Text(stableStringify(payload));
    return { format: exports.PORTABLE_FORMAT, formatVersion: exports.PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(payload), attachments: { references: refs.length, bytesIncluded: true, count: attachmentPayloads.length, totalBytes }, checksums: { algorithm: "SHA-256", payload: payloadSha256, attachments: Object.fromEntries(attachmentPayloads.map(a => [a.id, a.sha256])) } }, payload, attachments: attachmentPayloads };
}
function serializePortablePackage(packageObject) { return JSON.stringify(packageObject, null, 2) + "\n"; }
function portablePackageVersion(text) { try {
    const parsed = JSON.parse(text);
    return (parsed === null || parsed === void 0 ? void 0 : parsed.format) === exports.PORTABLE_FORMAT && Number.isInteger(parsed === null || parsed === void 0 ? void 0 : parsed.formatVersion) ? parsed.formatVersion : null;
}
catch {
    return null;
} }
function validateParsedState(parsed, now, stripAttachments) {
    var _a;
    if (!parsed.payload || typeof parsed.payload !== "object" || Array.isArray(parsed.payload))
        throw new TypeError("Portable package payload is missing");
    const candidate = sanitizePortableRuntimeState((0, domain_js_1.migrateStudyState)(clone(parsed.payload), { now: now() }));
    if (stripAttachments)
        candidate.materials = candidate.materials.map((material) => { var _a; return ({ ...material, attachmentId: null, locator: String((_a = material.locator) !== null && _a !== void 0 ? _a : "").startsWith("local-blob:") ? null : material.locator }); });
    const errors = (0, domain_js_1.validateStudyState)(candidate);
    if (errors.length)
        throw new TypeError(`Portable package state is invalid: ${errors[0]}`);
    const actualCounts = counts(candidate);
    if (((_a = parsed.manifest) === null || _a === void 0 ? void 0 : _a.counts) && !same(parsed.manifest.counts, actualCounts))
        throw new TypeError("Portable package manifest counts do not match its payload");
    return { candidate, actualCounts };
}
function parsePortablePackage(text, { now = () => new Date().toISOString() } = {}) {
    var _a, _b, _c;
    if (typeof text !== "string")
        throw new TypeError("Portable package must be JSON text");
    if (byteLength(text) > exports.MAX_PORTABLE_V1_BYTES)
        throw new TypeError("Portable package exceeds the 5 MiB local import limit");
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch {
        throw new TypeError("Portable package is not valid JSON");
    }
    if (!parsed || parsed.format !== exports.PORTABLE_FORMAT)
        throw new TypeError("Not a StudyFlow Local portable package");
    if (parsed.formatVersion !== exports.LEGACY_PORTABLE_FORMAT_VERSION)
        throw new TypeError(`Unsupported portable package version for legacy parser: ${parsed.formatVersion}`);
    const { candidate, actualCounts } = validateParsedState(parsed, now, true);
    return { ...parsed, payload: candidate, manifest: { activePlanId: candidate.activePlanId, counts: actualCounts, attachments: { references: (_c = (_b = (_a = parsed.manifest) === null || _a === void 0 ? void 0 : _a.attachments) === null || _b === void 0 ? void 0 : _b.references) !== null && _c !== void 0 ? _c : 0, bytesIncluded: false } } };
}
async function parsePortablePackageV2(text, { now = () => new Date().toISOString() } = {}) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p;
    if (typeof text !== "string")
        throw new TypeError("Portable package must be JSON text");
    if (byteLength(text) > exports.MAX_PORTABLE_V2_BYTES)
        throw new TypeError("Portable v2 package exceeds the 200 MiB local import limit");
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch {
        throw new TypeError("Portable package is not valid JSON");
    }
    if (!parsed || parsed.format !== exports.PORTABLE_FORMAT)
        throw new TypeError("Not a StudyFlow Local portable package");
    if (parsed.formatVersion === exports.LEGACY_PORTABLE_FORMAT_VERSION)
        return parsePortablePackage(text, { now });
    if (parsed.formatVersion !== exports.PORTABLE_FORMAT_VERSION)
        throw new TypeError(`Unsupported portable package version: ${parsed.formatVersion}`);
    if (((_b = (_a = parsed.manifest) === null || _a === void 0 ? void 0 : _a.attachments) === null || _b === void 0 ? void 0 : _b.bytesIncluded) !== true)
        throw new TypeError("Portable v2 package must include attachment bytes");
    const { candidate, actualCounts } = validateParsedState(parsed, now, false);
    const payloadHash = await sha256Text(stableStringify(candidate));
    if (payloadHash !== ((_d = (_c = parsed.manifest) === null || _c === void 0 ? void 0 : _c.checksums) === null || _d === void 0 ? void 0 : _d.payload))
        throw new TypeError("Portable package state checksum mismatch");
    const records = [];
    const seen = new Set();
    for (const raw of Array.isArray(parsed.attachments) ? parsed.attachments : []) {
        const id = String((_e = raw === null || raw === void 0 ? void 0 : raw.id) !== null && _e !== void 0 ? _e : "").trim();
        if (!id || seen.has(id))
            throw new TypeError(`Portable attachment id is missing or duplicated: ${id || "<empty>"}`);
        seen.add(id);
        const bytes = base64ToBytes(String((_f = raw === null || raw === void 0 ? void 0 : raw.base64) !== null && _f !== void 0 ? _f : ""));
        if (bytes.byteLength !== Number(raw === null || raw === void 0 ? void 0 : raw.sizeBytes))
            throw new TypeError(`Portable attachment size mismatch: ${id}`);
        const actual = await sha256Bytes(bytes);
        if (actual !== String((_g = raw === null || raw === void 0 ? void 0 : raw.sha256) !== null && _g !== void 0 ? _g : "") || actual !== ((_k = (_j = (_h = parsed.manifest) === null || _h === void 0 ? void 0 : _h.checksums) === null || _j === void 0 ? void 0 : _j.attachments) === null || _k === void 0 ? void 0 : _k[id]))
            throw new TypeError(`Portable attachment checksum mismatch: ${id}`);
        records.push({ id, name: String((_l = raw === null || raw === void 0 ? void 0 : raw.name) !== null && _l !== void 0 ? _l : "attachment"), mimeType: String((_m = raw === null || raw === void 0 ? void 0 : raw.mimeType) !== null && _m !== void 0 ? _m : "application/octet-stream"), sizeBytes: bytes.byteLength, lastModified: (raw === null || raw === void 0 ? void 0 : raw.lastModified) == null ? null : Number(raw.lastModified), createdAt: String((_p = (_o = raw === null || raw === void 0 ? void 0 : raw.createdAt) !== null && _o !== void 0 ? _o : parsed.exportedAt) !== null && _p !== void 0 ? _p : now()), sha256: actual, base64: String(raw.base64) });
    }
    const referenced = [...new Set(candidate.materials.map((m) => m.attachmentId).filter(Boolean))];
    for (const id of referenced)
        if (!seen.has(id))
            throw new TypeError(`Portable package is missing attachment bytes: ${id}`);
    if (Number(parsed.manifest.attachments.count) !== records.length || Number(parsed.manifest.attachments.references) !== referenced.length)
        throw new TypeError("Portable package attachment manifest does not match payload");
    return { ...parsed, payload: candidate, manifest: { ...parsed.manifest, counts: actualCounts }, attachmentRecords: records };
}
function diffPreview(currentState, nextState, packageObject, mode, attachmentRecords = [], attachmentIdMap = {}) {
    var _a, _b, _c;
    const collections = {};
    let added = 0, updated = 0, removed = 0, unchanged = 0;
    for (const name of domain_js_1.ENTITY_COLLECTIONS) {
        const before = byId(currentState[name]), after = byId(nextState[name]);
        let a = 0, u = 0, r = 0, sameCount = 0;
        for (const [id, item] of after) {
            if (!before.has(id))
                a++;
            else if (!same(before.get(id), item))
                u++;
            else
                sameCount++;
        }
        for (const id of before.keys())
            if (!after.has(id))
                r++;
        collections[name] = { add: a, update: u, remove: r, unchanged: sameCount };
        added += a;
        updated += u;
        removed += r;
        unchanged += sameCount;
    }
    return { baseRevision: currentState.revision, generatedAt: new Date().toISOString(), packageExportedAt: (_a = packageObject.exportedAt) !== null && _a !== void 0 ? _a : null, sourceActivePlanId: (_c = (_b = packageObject.payload) === null || _b === void 0 ? void 0 : _b.activePlanId) !== null && _c !== void 0 ? _c : null, mode, summary: { add: added, update: updated, remove: removed, unchanged }, collections, nextState, attachmentRecords, attachmentIdMap };
}
function remapId(map, value) { var _a; return value == null ? null : ((_a = map.get(value)) !== null && _a !== void 0 ? _a : value); }
function cloneImportedPlan(current, source, sourcePlanId, attachmentRecords) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j;
    const sourcePlan = source.plans.find(p => p.id === sourcePlanId);
    if (!sourcePlan)
        throw new TypeError("Portable package active plan is missing");
    const map = new Map();
    const prefix = { plans: "plan", subjects: "subject", topics: "topic", activities: "activity", sessions: "session", goals: "goal", materials: "material", flashcards: "flashcard", reviews: "review", questions: "question", evidence: "evidence", errorEntries: "error" };
    map.set(sourcePlan.id, `plan-import-${randomSuffix()}`);
    for (const name of domain_js_1.ENTITY_COLLECTIONS) {
        for (const item of (_a = source[name]) !== null && _a !== void 0 ? _a : []) {
            if (name !== "plans" && item.planId !== sourcePlanId)
                continue;
            if (name === "plans" && item.id !== sourcePlanId)
                continue;
            if (!map.has(item.id))
                map.set(item.id, `${(_b = prefix[name]) !== null && _b !== void 0 ? _b : name}-import-${randomSuffix()}`);
        }
    }
    const attachmentIdMap = {};
    for (const m of source.materials.filter(x => x.planId === sourcePlanId && x.attachmentId)) {
        attachmentIdMap[m.attachmentId] = `attachment-import-${randomSuffix()}`;
    }
    const next = clone(current);
    const plan = clone(sourcePlan);
    plan.id = map.get(sourcePlan.id);
    plan.name = `${sourcePlan.name} (imported)`;
    plan.status = "active";
    if ((_e = (_d = (_c = plan.configuration) === null || _c === void 0 ? void 0 : _c.publicExam) === null || _d === void 0 ? void 0 : _d.mockBlueprint) === null || _e === void 0 ? void 0 : _e.sections)
        plan.configuration.publicExam.mockBlueprint.sections = plan.configuration.publicExam.mockBlueprint.sections.map((s) => ({ ...s, subjectId: remapId(map, s.subjectId) }));
    next.plans.push(plan);
    const remapEntity = (name, item) => { var _a, _b; const x = clone(item); x.id = map.get(item.id); x.planId = plan.id; for (const field of ["subjectId", "topicId", "activityId", "sessionId", "questionId", "reviewId", "previousReviewId", "parentTopicId"])
        if (field in x)
            x[field] = remapId(map, x[field]); if (name === "reviews" && x.sourceId)
        x.sourceId = remapId(map, x.sourceId); if (name === "materials" && x.attachmentId) {
        const old = x.attachmentId;
        x.attachmentId = (_a = attachmentIdMap[old]) !== null && _a !== void 0 ? _a : old;
        if (String((_b = x.locator) !== null && _b !== void 0 ? _b : "").startsWith("local-blob:"))
            x.locator = `local-blob:${x.attachmentId}`;
    } return x; };
    for (const name of domain_js_1.ENTITY_COLLECTIONS) {
        if (name === "plans")
            continue;
        for (const item of source[name].filter((x) => x.planId === sourcePlanId))
            next[name].push(remapEntity(name, item));
    }
    const sourceCycle = (_g = (_f = source.studyCycles) === null || _f === void 0 ? void 0 : _f[sourcePlanId]) !== null && _g !== void 0 ? _g : source.studyCycle;
    next.studyCycles[plan.id] = { subjectIds: ((_h = sourceCycle === null || sourceCycle === void 0 ? void 0 : sourceCycle.subjectIds) !== null && _h !== void 0 ? _h : []).map((id) => remapId(map, id)).filter(Boolean), cursor: (_j = sourceCycle === null || sourceCycle === void 0 ? void 0 : sourceCycle.cursor) !== null && _j !== void 0 ? _j : 0 };
    next.activePlanId = plan.id;
    next.studyCycle = clone(next.studyCycles[plan.id]);
    next.focus = { ...next.focus, status: "idle", activityId: null, startedAt: null, lastResumedAt: null, endsAt: null };
    const mappedAttachments = attachmentRecords.filter(a => attachmentIdMap[a.id]).map(a => ({ ...a, id: attachmentIdMap[a.id] }));
    const errors = (0, domain_js_1.validateStudyState)(next);
    if (errors.length)
        throw new TypeError(`Imported plan clone is invalid: ${errors[0]}`);
    return { next, attachmentIdMap, mappedAttachments };
}
function buildPortableImportPreview(currentState, packageObject, { mode = "replace" } = {}) {
    var _a, _b, _c, _d;
    if (mode === "new-plan") {
        if (((_a = currentState.focus) === null || _a === void 0 ? void 0 : _a.status) !== "idle")
            throw new TypeError("Finish or cancel the current focus session before importing a plan as new");
        const records = ((_b = packageObject.attachmentRecords) !== null && _b !== void 0 ? _b : []);
        const sourcePlanId = (_c = packageObject.payload) === null || _c === void 0 ? void 0 : _c.activePlanId;
        if (!sourcePlanId)
            throw new TypeError("Portable package has no active plan to import");
        const cloned = cloneImportedPlan(currentState, packageObject.payload, sourcePlanId, records);
        return diffPreview(currentState, cloned.next, packageObject, "new-plan", cloned.mappedAttachments, cloned.attachmentIdMap);
    }
    const nextState = clone(packageObject.payload);
    return diffPreview(currentState, nextState, packageObject, "replace", (_d = packageObject.attachmentRecords) !== null && _d !== void 0 ? _d : [], {});
}
async function repositorySnapshot(repository) { const out = []; for (const meta of await repository.list()) {
    const record = await repository.get(meta.id);
    if (record)
        out.push(record);
} return out; }
async function writePayload(repository, payload) { const bytes = base64ToBytes(payload.base64); const blob = new Blob([bytes], { type: payload.mimeType }); await repository.put(blob, { id: payload.id, name: payload.name }); }
async function applyPortableImportWithAttachments(store, preview, repository) {
    var _a;
    const records = (_a = preview.attachmentRecords) !== null && _a !== void 0 ? _a : [];
    if (records.length && !repository)
        throw new TypeError("Portable package contains attachment bytes but attachment storage is unavailable");
    if (preview.baseRevision !== store.getState().revision)
        throw new TypeError("Portable import preview is stale; preview the package again before applying");
    if (!repository)
        return store.applyPortableImportPreview(preview);
    if (preview.mode === "new-plan") {
        const written = [];
        try {
            for (const record of records) {
                await writePayload(repository, record);
                written.push(record.id);
            }
            return store.applyPortableImportPreview(preview);
        }
        catch (error) {
            for (const id of written)
                await repository.delete(id).catch(() => { });
            throw error;
        }
    }
    const backup = await repositorySnapshot(repository);
    try {
        await repository.clear();
        for (const record of records)
            await writePayload(repository, record);
        return store.applyPortableImportPreview(preview);
    }
    catch (error) {
        await repository.clear().catch(() => { });
        for (const record of backup)
            await repository.put(record.blob, { id: record.id, name: record.name }).catch(() => { });
        throw error;
    }
}
function portablePackageFilename(state, at = new Date()) {
    var _a;
    const plan = state.plans.find((item) => item.id === state.activePlanId);
    const stem = String((_a = plan === null || plan === void 0 ? void 0 : plan.name) !== null && _a !== void 0 ? _a : "studyflow").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "studyflow";
    const stamp = at.toISOString().replace(/[:.]/g, "-");
    return `${stem}-${stamp}.studyflow.json`;
}
