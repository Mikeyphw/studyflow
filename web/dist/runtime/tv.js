"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveTvBrowserMode = resolveTvBrowserMode;
exports.spatialScore = spatialScore;
exports.installTvBrowserAdapter = installTvBrowserAdapter;
const TV_USER_AGENT = /(?:Tizen|SMART[- ]?TV|SmartTV|Web0S|WebOS|NetCast|HbbTV|BRAVIA|GoogleTV|Android TV|AFT[A-Z0-9]+)/i;
const FOCUSABLE_SELECTOR = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),summary,[role="button"],[role="tab"],[tabindex]';
const TEXT_INPUT_TYPES = new Set(["text", "search", "url", "tel", "email", "password", "number", "date", "time", "month", "week", "datetime-local", "range"]);
function currentSearch() { return typeof globalThis.location === "undefined" ? "" : globalThis.location.search; }
function currentUserAgent() { return typeof globalThis.navigator === "undefined" ? "" : globalThis.navigator.userAgent; }
function resolveTvBrowserMode(search = currentSearch(), userAgent = currentUserAgent()) {
    var _a;
    const params = new URLSearchParams(search);
    const requested = ((_a = params.get("tv")) !== null && _a !== void 0 ? _a : "").trim().toLowerCase();
    if (["1", "true", "on", "yes"].includes(requested))
        return { enabled: true, source: "query", forced: true };
    if (["0", "false", "off", "no"].includes(requested))
        return { enabled: false, source: "query", forced: true };
    if (TV_USER_AGENT.test(userAgent))
        return { enabled: true, source: "user-agent", forced: false };
    return { enabled: false, source: "none", forced: false };
}
function isVisible(element) {
    var _a;
    if (element.hidden || element.getAttribute("aria-hidden") === "true" || element.getAttribute("aria-disabled") === "true" || element.tabIndex < 0)
        return false;
    const style = (_a = globalThis.getComputedStyle) === null || _a === void 0 ? void 0 : _a.call(globalThis, element);
    if (style && (style.display === "none" || style.visibility === "hidden"))
        return false;
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && element.getClientRects().length > 0;
}
function activeModal() {
    const dialogs = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')].filter(isVisible);
    return dialogs.length ? dialogs[dialogs.length - 1] : null;
}
function focusables() { var _a; const scope = (_a = activeModal()) !== null && _a !== void 0 ? _a : document; return [...scope.querySelectorAll(FOCUSABLE_SELECTOR)].filter(isVisible); }
function center(rect) { return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }; }
function spatialScore(source, candidate, direction) {
    const a = center(source), b = center(candidate), dx = b.x - a.x, dy = b.y - a.y, tolerance = 3;
    let primary, cross, beam;
    if (direction === "left") {
        if (dx >= -tolerance)
            return null;
        primary = -dx;
        cross = Math.abs(dy);
        beam = (source.height + candidate.height) / 2;
    }
    else if (direction === "right") {
        if (dx <= tolerance)
            return null;
        primary = dx;
        cross = Math.abs(dy);
        beam = (source.height + candidate.height) / 2;
    }
    else if (direction === "up") {
        if (dy >= -tolerance)
            return null;
        primary = -dy;
        cross = Math.abs(dx);
        beam = (source.width + candidate.width) / 2;
    }
    else {
        if (dy <= tolerance)
            return null;
        primary = dy;
        cross = Math.abs(dx);
        beam = (source.width + candidate.width) / 2;
    }
    const offBeam = Math.max(0, cross - beam);
    const distance = Math.hypot(dx, dy);
    return primary + offBeam * 3 + cross * .18 + distance * .015;
}
function keyDirection(event) {
    const key = event.key || { 37: "ArrowLeft", 38: "ArrowUp", 39: "ArrowRight", 40: "ArrowDown" }[event.keyCode] || "";
    if (key === "ArrowLeft")
        return "left";
    if (key === "ArrowRight")
        return "right";
    if (key === "ArrowUp")
        return "up";
    if (key === "ArrowDown")
        return "down";
    return null;
}
function controlOwnsArrow(target) {
    if (!(target instanceof HTMLElement))
        return false;
    if (target.isContentEditable || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement)
        return true;
    if (target instanceof HTMLInputElement)
        return TEXT_INPUT_TYPES.has((target.type || "text").toLowerCase());
    return false;
}
function sourceRect(active) {
    if (active instanceof HTMLElement) {
        const rect = active.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0)
            return rect;
    }
    const x = globalThis.innerWidth / 2, y = globalThis.innerHeight / 2;
    return { left: x, right: x, top: y, bottom: y, width: 0, height: 0 };
}
function moveSpatialFocus(direction) {
    const active = document.activeElement;
    const source = sourceRect(active);
    let best = null, bestScore = Number.POSITIVE_INFINITY;
    for (const candidate of focusables()) {
        if (candidate === active)
            continue;
        const score = spatialScore(source, candidate.getBoundingClientRect(), direction);
        if (score != null && score < bestScore) {
            bestScore = score;
            best = candidate;
        }
    }
    if (!best)
        return false;
    try {
        best.focus({ preventScroll: true });
    }
    catch {
        best.focus();
    }
    try {
        best.scrollIntoView({ block: "center", inline: "nearest" });
    }
    catch {
        best.scrollIntoView();
    }
    return true;
}
function installDpadNavigation() {
    const onKeyDown = (event) => {
        const direction = keyDirection(event);
        if (!direction || event.altKey || event.ctrlKey || event.metaKey)
            return;
        const target = event.target instanceof Element ? event.target : null;
        if (controlOwnsArrow(target))
            return;
        if ((target === null || target === void 0 ? void 0 : target.closest('[role="tablist"]')) && (direction === "left" || direction === "right"))
            return;
        if (moveSpatialFocus(direction)) {
            event.preventDefault();
            event.stopPropagation();
        }
    };
    addEventListener("keydown", onKeyDown, true);
    return () => removeEventListener("keydown", onKeyDown, true);
}
function supportsFlexGap() {
    const probe = document.createElement("div"), a = document.createElement("div"), b = document.createElement("div");
    probe.style.display = "flex";
    probe.style.flexDirection = "column";
    probe.style.rowGap = "1px";
    probe.style.position = "absolute";
    probe.style.visibility = "hidden";
    a.style.height = "1px";
    b.style.height = "1px";
    probe.append(a, b);
    document.body.appendChild(probe);
    const supported = probe.scrollHeight === 3;
    probe.remove();
    return supported;
}
function applyCapabilityFlags() {
    var _a, _b;
    const root = document.documentElement;
    root.dataset.studyflowTvSecureContext = globalThis.isSecureContext ? "true" : "false";
    root.dataset.studyflowTvIndexedDb = typeof globalThis.indexedDB !== "undefined" ? "true" : "false";
    root.dataset.studyflowTvSubtleCrypto = typeof ((_a = globalThis.crypto) === null || _a === void 0 ? void 0 : _a.subtle) !== "undefined" ? "true" : "false";
    root.dataset.studyflowTvServiceWorker = typeof ((_b = globalThis.navigator) === null || _b === void 0 ? void 0 : _b.serviceWorker) !== "undefined" ? "true" : "false";
    root.dataset.studyflowTvFlexGap = supportsFlexGap() ? "true" : "false";
    root.dataset.studyflowTvInputMode = "remote-first";
}
function installTvBrowserAdapter() {
    const resolution = resolveTvBrowserMode();
    if (typeof document === "undefined")
        return { resolution, dispose: () => { } };
    const root = document.documentElement;
    root.dataset.studyflowTv = resolution.enabled ? "true" : "false";
    root.dataset.studyflowTvMode = resolution.source;
    applyCapabilityFlags();
    const disposeDpad = resolution.enabled ? installDpadNavigation() : () => { };
    return { resolution, dispose: () => { disposeDpad(); delete root.dataset.studyflowTv; delete root.dataset.studyflowTvMode; delete root.dataset.studyflowTvSecureContext; delete root.dataset.studyflowTvIndexedDb; delete root.dataset.studyflowTvSubtleCrypto; delete root.dataset.studyflowTvServiceWorker; delete root.dataset.studyflowTvFlexGap; delete root.dataset.studyflowTvInputMode; } };
}
