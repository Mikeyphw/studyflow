// Portable package v1 compatibility + v2 full local ownership package.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_PORTABLE_V2_BYTES = exports.MAX_PORTABLE_V1_BYTES = exports.PORTABLE_FORMAT_VERSION = exports.LEGACY_PORTABLE_FORMAT_VERSION = exports.PORTABLE_FORMAT = void 0;
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
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function counts(state) { return Object.fromEntries(domain_js_1.ENTITY_COLLECTIONS.map((name) => [name, Array.isArray(state[name]) ? state[name].length : 0])); }
function byId(items) { return new Map((items ?? []).map((item) => [item.id, item])); }
function same(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
function byteLength(text) { return new TextEncoder().encode(text).length; }
function stableValue(value) { if (Array.isArray(value))
    return value.map(stableValue); if (value && typeof value === "object") {
    const out = {};
    for (const key of Object.keys(value).sort())
        out[key] = stableValue(value[key]);
    return out;
} return value; }
function stableStringify(value) { return JSON.stringify(stableValue(value)); }
function randomSuffix() { return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`; }
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
    const payload = clone(state);
    const attachmentReferences = payload.materials.filter((material) => material.attachmentId).length;
    payload.materials = payload.materials.map((material) => ({ ...material, attachmentId: null, locator: String(material.locator ?? "").startsWith("local-blob:") ? null : material.locator }));
    return { format: exports.PORTABLE_FORMAT, formatVersion: exports.LEGACY_PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(payload), attachments: { references: attachmentReferences, bytesIncluded: false } }, payload };
}
async function createPortablePackageV2(state, attachments, { now = () => new Date().toISOString() } = {}) {
    const errors = (0, domain_js_1.validateStudyState)(state);
    if (errors.length)
        throw new TypeError(`Cannot export invalid StudyFlow state: ${errors[0]}`);
    const payload = clone(state);
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
        attachmentPayloads.push({ id: record.id, name: record.name, mimeType: record.mimeType || "application/octet-stream", sizeBytes: bytes.byteLength, lastModified: record.lastModified ?? null, createdAt: record.createdAt, sha256, base64: bytesToBase64(bytes) });
    }
    const payloadSha256 = await sha256Text(stableStringify(payload));
    return { format: exports.PORTABLE_FORMAT, formatVersion: exports.PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(payload), attachments: { references: refs.length, bytesIncluded: true, count: attachmentPayloads.length, totalBytes }, checksums: { algorithm: "SHA-256", payload: payloadSha256, attachments: Object.fromEntries(attachmentPayloads.map(a => [a.id, a.sha256])) } }, payload, attachments: attachmentPayloads };
}
function serializePortablePackage(packageObject) { return JSON.stringify(packageObject, null, 2) + "\n"; }
function portablePackageVersion(text) { try {
    const parsed = JSON.parse(text);
    return parsed?.format === exports.PORTABLE_FORMAT && Number.isInteger(parsed?.formatVersion) ? parsed.formatVersion : null;
}
catch {
    return null;
} }
function validateParsedState(parsed, now, stripAttachments) {
    if (!parsed.payload || typeof parsed.payload !== "object" || Array.isArray(parsed.payload))
        throw new TypeError("Portable package payload is missing");
    const candidate = (0, domain_js_1.migrateStudyState)(clone(parsed.payload), { now: now() });
    if (stripAttachments)
        candidate.materials = candidate.materials.map((material) => ({ ...material, attachmentId: null, locator: String(material.locator ?? "").startsWith("local-blob:") ? null : material.locator }));
    const errors = (0, domain_js_1.validateStudyState)(candidate);
    if (errors.length)
        throw new TypeError(`Portable package state is invalid: ${errors[0]}`);
    const actualCounts = counts(candidate);
    if (parsed.manifest?.counts && !same(parsed.manifest.counts, actualCounts))
        throw new TypeError("Portable package manifest counts do not match its payload");
    return { candidate, actualCounts };
}
function parsePortablePackage(text, { now = () => new Date().toISOString() } = {}) {
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
    return { ...parsed, payload: candidate, manifest: { activePlanId: candidate.activePlanId, counts: actualCounts, attachments: { references: parsed.manifest?.attachments?.references ?? 0, bytesIncluded: false } } };
}
async function parsePortablePackageV2(text, { now = () => new Date().toISOString() } = {}) {
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
    if (parsed.manifest?.attachments?.bytesIncluded !== true)
        throw new TypeError("Portable v2 package must include attachment bytes");
    const { candidate, actualCounts } = validateParsedState(parsed, now, false);
    const payloadHash = await sha256Text(stableStringify(candidate));
    if (payloadHash !== parsed.manifest?.checksums?.payload)
        throw new TypeError("Portable package state checksum mismatch");
    const records = [];
    const seen = new Set();
    for (const raw of Array.isArray(parsed.attachments) ? parsed.attachments : []) {
        const id = String(raw?.id ?? "").trim();
        if (!id || seen.has(id))
            throw new TypeError(`Portable attachment id is missing or duplicated: ${id || "<empty>"}`);
        seen.add(id);
        const bytes = base64ToBytes(String(raw?.base64 ?? ""));
        if (bytes.byteLength !== Number(raw?.sizeBytes))
            throw new TypeError(`Portable attachment size mismatch: ${id}`);
        const actual = await sha256Bytes(bytes);
        if (actual !== String(raw?.sha256 ?? "") || actual !== parsed.manifest?.checksums?.attachments?.[id])
            throw new TypeError(`Portable attachment checksum mismatch: ${id}`);
        records.push({ id, name: String(raw?.name ?? "attachment"), mimeType: String(raw?.mimeType ?? "application/octet-stream"), sizeBytes: bytes.byteLength, lastModified: raw?.lastModified == null ? null : Number(raw.lastModified), createdAt: String(raw?.createdAt ?? parsed.exportedAt ?? now()), sha256: actual, base64: String(raw.base64) });
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
    return { baseRevision: currentState.revision, generatedAt: new Date().toISOString(), packageExportedAt: packageObject.exportedAt ?? null, sourceActivePlanId: packageObject.payload?.activePlanId ?? null, mode, summary: { add: added, update: updated, remove: removed, unchanged }, collections, nextState, attachmentRecords, attachmentIdMap };
}
function remapId(map, value) { return value == null ? null : (map.get(value) ?? value); }
function cloneImportedPlan(current, source, sourcePlanId, attachmentRecords) {
    const sourcePlan = source.plans.find(p => p.id === sourcePlanId);
    if (!sourcePlan)
        throw new TypeError("Portable package active plan is missing");
    const map = new Map();
    const prefix = { plans: "plan", subjects: "subject", topics: "topic", activities: "activity", sessions: "session", goals: "goal", materials: "material", flashcards: "flashcard", reviews: "review", questions: "question", evidence: "evidence", errorEntries: "error" };
    map.set(sourcePlan.id, `plan-import-${randomSuffix()}`);
    for (const name of domain_js_1.ENTITY_COLLECTIONS) {
        for (const item of source[name] ?? []) {
            if (name !== "plans" && item.planId !== sourcePlanId)
                continue;
            if (name === "plans" && item.id !== sourcePlanId)
                continue;
            if (!map.has(item.id))
                map.set(item.id, `${prefix[name] ?? name}-import-${randomSuffix()}`);
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
    if (plan.configuration?.publicExam?.mockBlueprint?.sections)
        plan.configuration.publicExam.mockBlueprint.sections = plan.configuration.publicExam.mockBlueprint.sections.map((s) => ({ ...s, subjectId: remapId(map, s.subjectId) }));
    next.plans.push(plan);
    const remapEntity = (name, item) => { const x = clone(item); x.id = map.get(item.id); x.planId = plan.id; for (const field of ["subjectId", "topicId", "activityId", "sessionId", "questionId", "reviewId", "previousReviewId", "parentTopicId"])
        if (field in x)
            x[field] = remapId(map, x[field]); if (name === "reviews" && x.sourceId)
        x.sourceId = remapId(map, x.sourceId); if (name === "materials" && x.attachmentId) {
        const old = x.attachmentId;
        x.attachmentId = attachmentIdMap[old] ?? old;
        if (String(x.locator ?? "").startsWith("local-blob:"))
            x.locator = `local-blob:${x.attachmentId}`;
    } return x; };
    for (const name of domain_js_1.ENTITY_COLLECTIONS) {
        if (name === "plans")
            continue;
        for (const item of source[name].filter((x) => x.planId === sourcePlanId))
            next[name].push(remapEntity(name, item));
    }
    const sourceCycle = source.studyCycles?.[sourcePlanId] ?? source.studyCycle;
    next.studyCycles[plan.id] = { subjectIds: (sourceCycle?.subjectIds ?? []).map((id) => remapId(map, id)).filter(Boolean), cursor: sourceCycle?.cursor ?? 0 };
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
    if (mode === "new-plan") {
        if (currentState.focus?.status !== "idle")
            throw new TypeError("Finish or cancel the current focus session before importing a plan as new");
        const records = (packageObject.attachmentRecords ?? []);
        const sourcePlanId = packageObject.payload?.activePlanId;
        if (!sourcePlanId)
            throw new TypeError("Portable package has no active plan to import");
        const cloned = cloneImportedPlan(currentState, packageObject.payload, sourcePlanId, records);
        return diffPreview(currentState, cloned.next, packageObject, "new-plan", cloned.mappedAttachments, cloned.attachmentIdMap);
    }
    const nextState = clone(packageObject.payload);
    return diffPreview(currentState, nextState, packageObject, "replace", packageObject.attachmentRecords ?? [], {});
}
async function repositorySnapshot(repository) { const out = []; for (const meta of await repository.list()) {
    const record = await repository.get(meta.id);
    if (record)
        out.push(record);
} return out; }
async function writePayload(repository, payload) { const bytes = base64ToBytes(payload.base64); const blob = new Blob([bytes], { type: payload.mimeType }); await repository.put(blob, { id: payload.id, name: payload.name }); }
async function applyPortableImportWithAttachments(store, preview, repository) {
    const records = preview.attachmentRecords ?? [];
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
    const plan = state.plans.find((item) => item.id === state.activePlanId);
    const stem = String(plan?.name ?? "studyflow").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "studyflow";
    const stamp = at.toISOString().replace(/[:.]/g, "-");
    return `${stem}-${stamp}.studyflow.json`;
}
