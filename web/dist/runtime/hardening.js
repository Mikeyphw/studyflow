"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.auditBrowserStorage = auditBrowserStorage;
exports.purgeOrphanAttachments = purgeOrphanAttachments;
const domain_1 = require("./domain");
async function auditBrowserStorage(state, persistence, { now = () => new Date().toISOString() } = {}) {
    const stateErrors = (0, domain_1.validateStudyState)(state);
    try {
        await persistence.repository.flush?.();
    }
    catch (error) {
        const message = `Pending structured-state write failed: ${error instanceof Error ? error.message : String(error)}`;
        return { status: "problem", checkedAt: now(), backend: persistence.backend, stateValid: stateErrors.length === 0, attachmentStorageAvailable: persistence.backend === "indexeddb" && Boolean(persistence.attachments), referencedAttachments: state.materials.filter((material) => material.attachmentId).length, storedAttachments: 0, missingAttachmentIds: [], orphanAttachmentIds: [], messages: [message] };
    }
    const referenced = [...new Set(state.materials.map((material) => material.attachmentId).filter((id) => Boolean(id)))].sort();
    const messages = [];
    if (stateErrors.length)
        messages.push(`Structured state is invalid: ${stateErrors[0]}`);
    if (persistence.backend !== "indexeddb" || !persistence.attachments) {
        if (referenced.length)
            messages.push(`${referenced.length} attachment reference(s) cannot be checked without IndexedDB attachment storage.`);
        messages.push("Structured state is using compatibility storage; local attachment bytes are unavailable in this browser context.");
        return { status: stateErrors.length ? "problem" : "degraded", checkedAt: now(), backend: persistence.backend, stateValid: stateErrors.length === 0, attachmentStorageAvailable: false, referencedAttachments: referenced.length, storedAttachments: 0, missingAttachmentIds: referenced, orphanAttachmentIds: [], messages };
    }
    try {
        const stored = await persistence.attachments.list();
        const storedIds = new Set(stored.map((item) => item.id));
        const referencedIds = new Set(referenced);
        const missing = referenced.filter((id) => !storedIds.has(id));
        const orphan = stored.map((item) => item.id).filter((id) => !referencedIds.has(id)).sort();
        if (missing.length)
            messages.push(`${missing.length} material attachment reference(s) have no local blob.`);
        if (orphan.length)
            messages.push(`${orphan.length} unreferenced local attachment blob(s) can be cleaned safely.`);
        if (!messages.length)
            messages.push("Structured state and local attachment references are consistent.");
        return { status: stateErrors.length || missing.length ? "problem" : orphan.length ? "degraded" : "healthy", checkedAt: now(), backend: persistence.backend, stateValid: stateErrors.length === 0, attachmentStorageAvailable: true, referencedAttachments: referenced.length, storedAttachments: stored.length, missingAttachmentIds: missing, orphanAttachmentIds: orphan, messages };
    }
    catch (error) {
        messages.push(`Attachment storage check failed: ${error instanceof Error ? error.message : String(error)}`);
        return { status: "problem", checkedAt: now(), backend: persistence.backend, stateValid: stateErrors.length === 0, attachmentStorageAvailable: false, referencedAttachments: referenced.length, storedAttachments: 0, missingAttachmentIds: [], orphanAttachmentIds: [], messages };
    }
}
async function purgeOrphanAttachments(state, persistence) {
    const report = await auditBrowserStorage(state, persistence);
    if (!persistence.attachments || !report.attachmentStorageAvailable)
        return report;
    for (const id of report.orphanAttachmentIds)
        await persistence.attachments.delete(id);
    return auditBrowserStorage(state, persistence);
}
