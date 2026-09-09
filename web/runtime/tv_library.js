"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TV_LIBRARY_TEXT_PAGE_CHARS = void 0;
exports.tvLibraryMaterials = tvLibraryMaterials;
exports.tvLibraryNextIndex = tvLibraryNextIndex;
exports.paginateTvText = paginateTvText;
exports.tvPdfPageUrl = tvPdfPageUrl;
const retention_viewer_1 = require("./retention_viewer");
exports.TV_LIBRARY_TEXT_PAGE_CHARS = 3600;
function tvLibraryMaterials(materials, filter) {
    const sorted = materials.slice().sort((a, b) => Number(a.archived) - Number(b.archived) || a.title.localeCompare(b.title) || a.id.localeCompare(b.id));
    if (filter === "local")
        return sorted.filter(material => Boolean(material.attachmentId));
    if (filter === "reference")
        return sorted.filter(material => !material.attachmentId && (0, retention_viewer_1.planMaterialViewer)(material).kind === "web-page");
    return sorted;
}
function tvLibraryNextIndex(current, total, delta) {
    if (total <= 0)
        return 0;
    return Math.max(0, Math.min(total - 1, current + delta));
}
function paginateTvText(text, maxChars = exports.TV_LIBRARY_TEXT_PAGE_CHARS) {
    const normalized = String(text !== null && text !== void 0 ? text : "").replace(/\r\n?/g, "\n");
    const limit = Math.max(600, Math.floor(maxChars));
    if (normalized.length <= limit)
        return [normalized];
    const pages = [];
    let cursor = 0;
    while (cursor < normalized.length) {
        let end = Math.min(normalized.length, cursor + limit);
        if (end < normalized.length) {
            const floor = cursor + Math.floor(limit * .6);
            const newline = normalized.lastIndexOf("\n", end);
            const space = normalized.lastIndexOf(" ", end);
            const boundary = Math.max(newline, space);
            if (boundary >= floor)
                end = boundary + 1;
        }
        pages.push(normalized.slice(cursor, end).trimEnd());
        cursor = end;
    }
    return pages.length ? pages : [""];
}
function tvPdfPageUrl(url, page) {
    const base = String(url !== null && url !== void 0 ? url : "").split("#")[0];
    const safePage = Math.max(1, Math.floor(Number(page) || 1));
    return `${base}#page=${safePage}&zoom=page-width`;
}
