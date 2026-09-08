"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.planMaterialViewer = planMaterialViewer;
exports.materialViewerIsInline = materialViewerIsInline;
exports.materialViewerNeedsAttachment = materialViewerNeedsAttachment;
function mime(material) { return String(material.mimeType ?? "").trim().toLowerCase() || "application/octet-stream"; }
function safeWebUri(raw) { const value = String(raw ?? "").trim(); if (!value)
    return null; try {
    const u = new URL(value);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : null;
}
catch {
    return null;
} }
function planMaterialViewer(material) {
    if (!material || typeof material !== "object")
        throw new TypeError("Material is required");
    const m = mime(material);
    if (material.attachmentId) {
        const kind = m === "application/pdf" ? "local-pdf" : m.startsWith("image/") ? "local-image" : m.startsWith("audio/") ? "local-audio" : m.startsWith("video/") ? "local-video" : m.startsWith("text/") || m === "application/json" || m === "application/xml" || m === "application/javascript" ? "local-text" : "local-download";
        return { kind, title: material.title, mimeType: m, attachmentId: material.attachmentId, uri: null, reason: null };
    }
    const locator = String(material.locator ?? "").trim();
    if (!locator)
        return { kind: "missing", title: material.title, mimeType: material.mimeType, attachmentId: null, uri: null, reason: "Material has no local attachment or source URI." };
    if (locator.startsWith("local-blob:"))
        return { kind: "missing", title: material.title, mimeType: material.mimeType, attachmentId: null, uri: null, reason: "Local attachment metadata is missing." };
    const uri = safeWebUri(locator);
    if (uri)
        return { kind: "web-page", title: material.title, mimeType: material.mimeType, attachmentId: null, uri, reason: null };
    return { kind: "invalid", title: material.title, mimeType: material.mimeType, attachmentId: null, uri: null, reason: "Only local attachments and explicit HTTP(S) resources can be opened in the web app." };
}
function materialViewerIsInline(plan) { return ["local-text", "local-image", "local-audio", "local-video", "local-pdf"].includes(plan.kind); }
function materialViewerNeedsAttachment(plan) { return plan.kind.startsWith("local-"); }
