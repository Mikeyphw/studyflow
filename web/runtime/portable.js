// TypeScript migration source. Public contracts are declared in types.ts.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_PORTABLE_BYTES = exports.PORTABLE_FORMAT_VERSION = exports.PORTABLE_FORMAT = void 0;
exports.createPortablePackage = createPortablePackage;
exports.serializePortablePackage = serializePortablePackage;
exports.parsePortablePackage = parsePortablePackage;
exports.buildPortableImportPreview = buildPortableImportPreview;
exports.portablePackageFilename = portablePackageFilename;
const domain_js_1 = require("./domain.js");
exports.PORTABLE_FORMAT = "studyflow-local-package";
exports.PORTABLE_FORMAT_VERSION = 1;
exports.MAX_PORTABLE_BYTES = 5 * 1024 * 1024;
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function counts(state) { return Object.fromEntries(domain_js_1.ENTITY_COLLECTIONS.map((name) => [name, Array.isArray(state[name]) ? state[name].length : 0])); }
function byId(items) { return new Map((items ?? []).map((item) => [item.id, item])); }
function same(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
function createPortablePackage(state, { now = () => new Date().toISOString() } = {}) {
    const errors = (0, domain_js_1.validateStudyState)(state);
    if (errors.length)
        throw new TypeError(`Cannot export invalid StudyFlow state: ${errors[0]}`);
    const exportedAt = now();
    const payload = clone(state);
    const attachmentReferences = payload.materials.filter((material) => material.attachmentId).length;
    payload.materials = payload.materials.map((material) => ({ ...material, attachmentId: null, locator: String(material.locator ?? "").startsWith("local-blob:") ? null : material.locator }));
    return { format: exports.PORTABLE_FORMAT, formatVersion: exports.PORTABLE_FORMAT_VERSION, exportedAt, stateSchemaVersion: state.schemaVersion, manifest: { activePlanId: state.activePlanId, counts: counts(payload), attachments: { references: attachmentReferences, bytesIncluded: false } }, payload };
}
function serializePortablePackage(packageObject) { return JSON.stringify(packageObject, null, 2) + "\n"; }
function parsePortablePackage(text, { now = () => new Date().toISOString() } = {}) {
    if (typeof text !== "string")
        throw new TypeError("Portable package must be JSON text");
    if (new TextEncoder().encode(text).length > exports.MAX_PORTABLE_BYTES)
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
    if (parsed.formatVersion !== exports.PORTABLE_FORMAT_VERSION)
        throw new TypeError(`Unsupported portable package version: ${parsed.formatVersion}`);
    if (!parsed.payload || typeof parsed.payload !== "object" || Array.isArray(parsed.payload))
        throw new TypeError("Portable package payload is missing");
    const candidate = (0, domain_js_1.migrateStudyState)(clone(parsed.payload), { now: now() });
    if (parsed.manifest?.attachments?.bytesIncluded !== true)
        candidate.materials = candidate.materials.map((material) => ({ ...material, attachmentId: null, locator: String(material.locator ?? "").startsWith("local-blob:") ? null : material.locator }));
    const errors = (0, domain_js_1.validateStudyState)(candidate);
    if (errors.length)
        throw new TypeError(`Portable package state is invalid: ${errors[0]}`);
    const actualCounts = counts(candidate);
    if (parsed.manifest?.counts && !same(parsed.manifest.counts, actualCounts))
        throw new TypeError("Portable package manifest counts do not match its payload");
    return { ...parsed, payload: candidate, manifest: { activePlanId: candidate.activePlanId, counts: actualCounts } };
}
function buildPortableImportPreview(currentState, packageObject) {
    const nextState = clone(packageObject.payload);
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
    return { baseRevision: currentState.revision, generatedAt: new Date().toISOString(), packageExportedAt: packageObject.exportedAt ?? null, sourceActivePlanId: nextState.activePlanId, summary: { add: added, update: updated, remove: removed, unchanged }, collections, nextState };
}
function portablePackageFilename(state, at = new Date()) {
    const plan = state.plans.find((item) => item.id === state.activePlanId);
    const stem = String(plan?.name ?? "studyflow").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "studyflow";
    const stamp = at.toISOString().replace(/[:.]/g, "-");
    return `${stem}-${stamp}.studyflow.json`;
}
