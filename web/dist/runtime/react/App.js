"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.App = App;
const React = __importStar(require("react"));
const domain_1 = require("../domain");
const modes_1 = require("../modes");
const portable_1 = require("../portable");
const encrypted_portable_1 = require("../encrypted_portable");
const content_import_1 = require("../content_import");
const question_import_1 = require("../question_import");
const mode_import_1 = require("../mode_import");
const intelligence_1 = require("../intelligence");
const refinement_1 = require("../refinement");
const pwa_1 = require("../pwa");
const hardening_1 = require("../hardening");
const release_1 = require("../release");
const product_1 = require("../product");
const task_workflows_1 = require("../task_workflows");
const parity_intelligence_1 = require("../parity_intelligence");
const promise_cleanup_1 = require("../promise_cleanup");
const retention_viewer_1 = require("../retention_viewer");
const tv_control_1 = require("../tv_control");
const tv_history_1 = require("../tv_history");
const tv_session_1 = require("../tv_session");
const tv_question_set_1 = require("../tv_question_set");
const tv_mock_exam_1 = require("../tv_mock_exam");
const tv_review_notebook_1 = require("../tv_review_notebook");
const tv_progress_1 = require("../tv_progress");
const tv_schedule_1 = require("../tv_schedule");
const tv_library_1 = require("../tv_library");
const compact_attention_1 = require("../compact_attention");
const calendar_attention_1 = require("../calendar_attention");
const title = (v) => v.replace(/[-_]/g, " ").replace(/\b\w/g, c => c.toUpperCase());
const pct = (v) => v == null ? "No data" : `${Math.round(v * 100)}%`;
const clock = (ms) => { const n = Math.max(0, Math.ceil(ms / 1000)); return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`; };
const modes = [["EXAM_PLAN", "Concurso público"], ["PROGRAMMING_COURSE", "Programming course"], ["LANGUAGE_DECK", "Language deck"], ["READING_STUDY", "Reading study"], ["GENERAL_SKILL", "General skill"]];
const isTvBrowserUi = () => typeof document !== "undefined" && document.documentElement.dataset.studyflowTv === "true";
function TvContinueElsewhere({ title: heading, detail }) { return React.createElement("article", { className: "card tv-continue-card", "data-tv-workflow": "continue-elsewhere" },
    React.createElement("p", { className: "eyebrow" }, "Remote-friendly mode"),
    React.createElement("h2", null, heading),
    React.createElement("p", { className: "muted" }, detail),
    React.createElement("p", { className: "tv-continue-hint" }, "Continue this edit on a phone or computer. Viewing and D-pad-friendly actions stay available on TV.")); }
function useStudyState(store) { return React.useSyncExternalStore((notify) => store.subscribe(() => notify()), store.getState, store.getState); }
function Tabs({ value, setValue, items, prefix, label }) { const refs = React.useRef([]); const move = (e, index) => { var _a, _b; let next = index; if (e.key === "ArrowRight")
    next = (index + 1) % items.length;
else if (e.key === "ArrowLeft")
    next = (index - 1 + items.length) % items.length;
else if (e.key === "Home")
    next = 0;
else if (e.key === "End")
    next = items.length - 1;
else
    return; e.preventDefault(); const id = items[next][0]; setValue(id); (_b = (_a = refs.current) === null || _a === void 0 ? void 0 : _a[next]) === null || _b === void 0 ? void 0 : _b.focus(); }; return React.createElement("nav", { className: "subnav", "aria-label": label, role: "tablist" }, items.map(([id, text], index) => React.createElement("button", { key: id, ref: (node) => { if (refs.current)
        refs.current[index] = node; }, id: `tab-${prefix}-${id}`, type: "button", role: "tab", className: "tab-link", "aria-selected": value === id, "aria-controls": `${prefix}-${id}`, tabIndex: value === id ? 0 : -1, onKeyDown: (e) => move(e, index), onClick: () => setValue(id) }, text))); }
function Metric({ label, value, detail }) { return React.createElement("article", { className: "metric-card" },
    React.createElement("span", null, label),
    React.createElement("strong", null, value),
    React.createElement("small", null, detail)); }
function useTvReceiverSnapshot(enabled) {
    const [snapshot, setSnapshot] = React.useState({ state: enabled ? "connecting" : "disabled", pairingCode: null, expiresAtEpochMillis: null, deviceName: "StudyFlow Browser TV", transport: "none", message: enabled ? "Starting TV control receiver." : "TV control receiver disabled." });
    React.useEffect(() => { const handle = (0, tv_control_1.installTvControlReceiver)(enabled); const sync = () => setSnapshot(handle.getSnapshot()); sync(); const unsubscribe = handle.subscribe(sync); return () => { unsubscribe(); handle.dispose(); }; }, [enabled]);
    return snapshot;
}
function TvPairingBanner({ snapshot }) { if (snapshot.state === "paired")
    return React.createElement("div", { className: "tv-pairing-compact", role: "status" },
        React.createElement("span", { className: "status-pill" },
            "Phone paired \u00B7 ",
            snapshot.transport.toUpperCase())); return React.createElement("article", { className: "tv-pairing-banner", "aria-live": "polite" },
    React.createElement("div", null,
        React.createElement("p", { className: "eyebrow" }, "Phone remote"),
        React.createElement("h2", null, snapshot.state === "pairing" && snapshot.pairingCode ? `Pairing code ${snapshot.pairingCode}` : "TV control relay"),
        React.createElement("p", { className: "muted" }, snapshot.message)),
    snapshot.state === "pairing" && snapshot.pairingCode && React.createElement("div", { className: "tv-pairing-code", "aria-label": `Pairing code ${snapshot.pairingCode}` }, snapshot.pairingCode),
    React.createElement("p", { className: "tv-pairing-instruction" },
        "Open ",
        React.createElement("strong", null, "/remote"),
        " on a phone connected to this LAN.")); }
function TvCastBanner({ cast, onStop }) { return React.createElement("article", { className: "tv-cast-banner", role: "status" },
    React.createElement("div", null,
        React.createElement("p", { className: "eyebrow" }, "Phone presentation"),
        React.createElement("h2", null, cast.title),
        cast.subtitle && React.createElement("p", { className: "muted" }, cast.subtitle),
        cast.progressPercent != null && React.createElement("progress", { max: "100", value: cast.progressPercent },
            cast.progressPercent,
            "%")),
    React.createElement("button", { className: "button button-quiet", type: "button", onClick: onStop }, "Stop presentation")); }
function tvSurfaceDestination(surface) { if (surface === "SETTINGS")
    return { screen: "today", overlay: "settings" }; if (surface === "STUDY_SESSION")
    return { screen: "session" }; if (surface === "QUESTION_SET")
    return { screen: "questions" }; if (surface === "REVIEW")
    return { screen: "review" }; if (surface === "MOCK_EXAM")
    return { screen: "mock" }; if (surface === "PROGRESS")
    return { screen: "progress" }; if (surface === "SCHEDULE")
    return { screen: "schedule" }; if (surface === "LIBRARY")
    return { screen: "library" }; return { screen: "today" }; }
function useWebAttentionSnapshot(runtime) { const [snapshot, setSnapshot] = React.useState(() => { var _a; return (_a = runtime === null || runtime === void 0 ? void 0 : runtime.snapshot()) !== null && _a !== void 0 ? _a : null; }); React.useEffect(() => { if (!runtime) {
    setSnapshot(null);
    return;
} const sync = () => setSnapshot(runtime.snapshot()); sync(); const unsubscribe = runtime.subscribe(sync); return () => unsubscribe(); }, [runtime]); return snapshot; }
const attentionCategoryOrder = ["planned_study", "due_review", "missed_activity", "daily_summary", "session_ending", "session_completed", "exam_countdown", "schedule_change", "plan_warning", "sync_attention"];
function attentionCategoryLabel(category) { return title(category); }
function attentionEntryTime(entry) { const value = entry.state === "scheduled" ? entry.occurrence.scheduledAtEpochMillis : entry.updatedAtEpochMillis; try {
    return new Date(value).toLocaleString();
}
catch {
    return String(value);
} }
function App({ store, persistence, attentionRuntime }) {
    var _a, _b, _c;
    const state = useStudyState(store);
    const tvBrowser = isTvBrowserUi();
    const mainRef = React.useRef(null);
    const [screen, setScreen] = React.useState(() => tvBrowser ? (0, tv_history_1.screenFromHash)(location.hash) : (["today", "plan", "practice", "progress"].includes(location.hash.slice(1)) ? location.hash.slice(1) : "today"));
    const [planTab, setPlanTab] = React.useState("overview");
    const [practiceTab, setPracticeTab] = React.useState("reviews");
    const [progressTab, setProgressTab] = React.useState("summary");
    const [preview, setPreview] = React.useState(null);
    const [portable, setPortable] = React.useState(null);
    const [portableStatus, setPortableStatus] = React.useState("No package selected.");
    const [questionId, setQuestionId] = React.useState((_b = (_a = state.questions[0]) === null || _a === void 0 ? void 0 : _a.id) !== null && _b !== void 0 ? _b : null);
    const [practiceContext, setPracticeContext] = React.useState(null);
    const [toast, setToast] = React.useState(null);
    const [searchOpen, setSearchOpen] = React.useState(false);
    const [settingsOpen, setSettingsOpen] = React.useState(false);
    const [attentionOpen, setAttentionOpen] = React.useState(false);
    const attentionSnapshot = useWebAttentionSnapshot(attentionRuntime);
    const [tvMaterialId, setTvMaterialId] = React.useState(null);
    const [tvCast, setTvCast] = React.useState(null);
    const [tvSetupDismissed, setTvSetupDismissed] = React.useState(false);
    const tvReceiver = useTvReceiverSnapshot(tvBrowser);
    const [, tick] = React.useState(0);
    React.useEffect(() => { if (!tvBrowser) {
        const onHash = () => { const x = location.hash.slice(1); if (["today", "plan", "practice", "progress"].includes(x))
            setScreen(x); };
        onHash();
        addEventListener("hashchange", onHash);
        if (!location.hash)
            history.replaceState(null, "", "#today");
        return () => removeEventListener("hashchange", onHash);
    } const apply = (entry) => { setScreen(entry.screen); setSearchOpen(entry.overlay === "search"); setSettingsOpen(entry.overlay === "settings"); setTvMaterialId(entry.overlay === "material" ? entry.overlayId : null); }; let entry = (0, tv_history_1.readTvHistoryEntry)(history.state, location.hash); if (!(history.state && typeof history.state === "object" && tv_history_1.TV_HISTORY_STATE_KEY in history.state))
        (0, tv_history_1.replaceTvHistoryEntry)(entry); apply(entry); const onPop = () => { const next = (0, tv_history_1.readTvHistoryEntry)(history.state, location.hash); if (document.documentElement.dataset.studyflowTvExamActive === "true" && next.screen !== "mock") {
        const current = { version: 1, screen: "mock", overlay: null, overlayId: null };
        (0, tv_history_1.pushTvHistoryEntry)(current);
        apply(current);
        dispatchEvent(new CustomEvent("studyflow:tv-exam-back"));
        return;
    } apply(next); }; addEventListener("popstate", onPop); return () => removeEventListener("popstate", onPop); }, [tvBrowser]);
    React.useEffect(() => { var _a; if (document.documentElement.dataset.studyflowTv !== "true") {
        (_a = mainRef.current) === null || _a === void 0 ? void 0 : _a.focus({ preventScroll: true });
        return;
    } const main = mainRef.current; if (!main)
        return; const id = requestAnimationFrame(() => { const target = main.querySelector('[role="tab"][aria-selected="true"],button:not([disabled]),a[href],select:not([disabled]),input:not([disabled]),textarea:not([disabled]),summary'); if (target) {
        try {
            target.focus({ preventScroll: true });
        }
        catch {
            target.focus();
        }
    }
    else
        main.focus({ preventScroll: true }); }); return () => cancelAnimationFrame(id); }, [screen]);
    React.useEffect(() => { const id = setInterval(() => tick(v => v + 1), 1000); return () => clearInterval(id); }, []);
    React.useEffect(() => { if (!toast)
        return; const id = setTimeout(() => setToast(null), 2500); return () => clearTimeout(id); }, [toast]);
    React.useEffect(() => { const handle = (0, product_1.installDocumentLocalization)(state.preferences.locale); return () => handle.dispose(); }, [state.preferences.locale]);
    React.useEffect(() => { const id = setTimeout(() => { document.documentElement.dataset.studyflowInteractive = "ready"; }, 0); return () => { clearTimeout(id); delete document.documentElement.dataset.studyflowInteractive; }; }, []);
    const run = (fn, msg) => { try {
        const r = fn();
        if (msg)
            setToast(msg);
        return r;
    }
    catch (e) {
        setToast(e instanceof Error ? e.message : String(e));
        return null;
    } };
    const focus = store.getFocusSnapshot(new Date().toISOString());
    const activePlan = state.plans.find(p => p.id === state.activePlanId);
    const applyTvEntry = (entry) => { setScreen(entry.screen); setSearchOpen(entry.overlay === "search"); setSettingsOpen(entry.overlay === "settings"); setTvMaterialId(entry.overlay === "material" ? entry.overlayId : null); };
    const navigateScreen = (next, replace = false) => { if (tvBrowser && screen === "mock" && next !== "mock" && document.documentElement.dataset.studyflowTvExamActive === "true") {
        dispatchEvent(new CustomEvent("studyflow:tv-exam-back"));
        return;
    } if (tvBrowser) {
        const entry = { version: 1, screen: next, overlay: null, overlayId: null };
        if (replace)
            (0, tv_history_1.replaceTvHistoryEntry)(entry);
        else
            (0, tv_history_1.pushTvHistoryEntry)(entry);
        applyTvEntry(entry);
        return;
    } location.hash = `#${next}`; };
    const openTvOverlay = (overlay, overlayId = null) => { if (!tvBrowser) {
        if (overlay === "search")
            setSearchOpen(true);
        if (overlay === "settings")
            setSettingsOpen(true);
        return;
    } const entry = { version: 1, screen, overlay, overlayId }; (0, tv_history_1.pushTvHistoryEntry)(entry); applyTvEntry(entry); };
    const closeTvOverlay = () => { if (tvBrowser) {
        const entry = (0, tv_history_1.readTvHistoryEntry)(history.state, location.hash);
        if (entry.overlay) {
            history.back();
            return;
        }
    } setSearchOpen(false); setSettingsOpen(false); setTvMaterialId(null); };
    const navigateTvSurface = (surface, replace = false) => { const destination = tvSurfaceDestination(surface); if (destination.tab) {
        if (destination.screen === "plan")
            setPlanTab(destination.tab);
        if (destination.screen === "practice")
            setPracticeTab(destination.tab);
        if (destination.screen === "progress")
            setProgressTab(destination.tab);
    } if (destination.overlay) {
        if (replace) {
            const entry = { version: 1, screen, overlay: destination.overlay, overlayId: null };
            (0, tv_history_1.replaceTvHistoryEntry)(entry);
            applyTvEntry(entry);
        }
        else
            openTvOverlay(destination.overlay);
        return;
    } navigateScreen(destination.screen, replace); };
    React.useEffect(() => { const key = (e) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k" || e.key === "/" && !(e.target instanceof HTMLInputElement) && !(e.target instanceof HTMLTextAreaElement)) {
        e.preventDefault();
        openTvOverlay("search");
    } if (e.key === "Escape") {
        if (tvBrowser) {
            const entry = (0, tv_history_1.readTvHistoryEntry)(history.state, location.hash);
            if (entry.overlay) {
                e.preventDefault();
                history.back();
            }
        }
        else {
            setSearchOpen(false);
            setSettingsOpen(false);
        }
    } }; addEventListener("keydown", key); return () => removeEventListener("keydown", key); }, [tvBrowser, screen]);
    React.useEffect(() => { if (!tvBrowser)
        return; const trigger = (command) => { var _a; const scope = (_a = document.querySelector('[role="dialog"][aria-modal="true"]')) !== null && _a !== void 0 ? _a : document; const nodes = [...scope.querySelectorAll("[data-tv-control-action]")]; const exact = nodes.find(node => node.dataset.tvControlAction === command.action && (command.targetId == null || node.dataset.tvControlTarget === command.targetId) && (command.optionId == null || node.dataset.tvControlOption === command.optionId)); const optionRequired = command.surface === "REVIEW" && (command.action === "COMPLETE" || command.action === "MARK_FOR_REVIEW"); const fallback = command.targetId == null && command.optionId == null && !optionRequired ? nodes.find(node => node.dataset.tvControlAction === command.action) : undefined; const node = exact !== null && exact !== void 0 ? exact : fallback; if (node && "click" in node) {
        node.click();
        return true;
    } return false; }; const onControl = (event) => { const command = event.detail; if (command.action === "OPEN") {
        if (command.targetId && trigger(command))
            return;
        navigateTvSurface(command.surface);
        if (command.targetId)
            requestAnimationFrame(() => trigger(command));
        return;
    } if (command.action === "BACK") {
        if (document.documentElement.dataset.studyflowTvExamActive === "true") {
            dispatchEvent(new CustomEvent("studyflow:tv-exam-back"));
            return;
        }
        history.back();
        return;
    } if (command.action === "STOP_CAST") {
        setTvCast(null);
        return;
    } if (!trigger(command))
        setToast(`Phone command ${title(command.action)} is not available on this TV surface.`); }; const onCast = (event) => { const cast = event.detail; setTvCast(cast); navigateTvSurface(cast.surface); }; addEventListener("studyflow:tv-control", onControl); addEventListener("studyflow:tv-cast", onCast); return () => { removeEventListener("studyflow:tv-control", onControl); removeEventListener("studyflow:tv-cast", onCast); }; }, [tvBrowser, screen, planTab, practiceTab, progressTab]);
    const openSearchResult = (item) => { if (item.planId !== state.activePlanId && !run(() => store.switchPlan(item.planId), "Plan switched for search result."))
        return; let destination = "plan"; if (item.type === "question") {
        setQuestionId(item.id);
        setPracticeContext(null);
        setPracticeTab("questions");
        destination = tvBrowser ? "questions" : "practice";
    }
    else if (item.type === "error") {
        setPracticeContext(null);
        setPracticeTab("errors");
        destination = tvBrowser ? "review" : "practice";
    }
    else if (item.type === "material") {
        setPlanTab("library");
        if (tvBrowser) {
            navigateScreen("library", true);
            requestAnimationFrame(() => openTvOverlay("material", item.id));
            return;
        }
    }
    else if (item.type === "subject" || item.type === "topic") {
        setPlanTab("content");
    }
    else {
        setPlanTab("overview");
    } navigateScreen(destination, tvBrowser); };
    const launchActivity = (activityId) => { const route = run(() => (0, task_workflows_1.routeActivityDeepLink)(state, activityId)); if (!route)
        return; setPracticeContext(route); const activity = state.activities.find(item => item.id === activityId); if (route.screen === "practice") {
        setPracticeTab(route.practiceTab);
        navigateScreen(tvBrowser ? (route.practiceTab === "questions" ? "questions" : route.practiceTab === "tests" ? "mock" : route.practiceTab === "reviews" ? "review" : "practice") : "practice");
        return;
    } if (route.focusMode === "writing") {
        if (tvBrowser) {
            setToast("Writing Workspace continues on a phone or computer.");
            return;
        }
        if (!run(() => store.startWritingSession({ activityId, prompt: activity.title, durationMinutes: activity.durationMinutes }), "Writing Workspace opened."))
            return;
    }
    else if (route.focusMode === "focus") {
        if (!run(() => store.startFocus({ activityId, durationMinutes: activity.durationMinutes }), "Focus started."))
            return;
        if (tvBrowser) {
            navigateScreen("session");
            return;
        }
    } navigateScreen("today"); };
    return React.createElement(React.Fragment, null,
        React.createElement("a", { className: "skip-link", href: "#main-content" }, "Skip to content"),
        React.createElement("header", { className: "app-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "StudyFlow Local"),
                React.createElement("h1", { id: "screen-title" }, title(screen)),
                React.createElement("p", { className: "muted compact-copy" }, activePlan ? (_c = modes.find(([id]) => id === activePlan.mode)) === null || _c === void 0 ? void 0 : _c[1] : "No active plan")),
            React.createElement("div", { className: "header-actions" },
                tvBrowser ? React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => openTvOverlay("search") }, (0, product_1.uiText)(state.preferences.locale, "search")) : React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setSearchOpen(true) }, (0, product_1.uiText)(state.preferences.locale, "search")),
                tvBrowser ? React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => openTvOverlay("settings") }, (0, product_1.uiText)(state.preferences.locale, "settings")) : React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setSettingsOpen(true) }, (0, product_1.uiText)(state.preferences.locale, "settings")),
                !tvBrowser && attentionRuntime && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setAttentionOpen(true) },
                    "Notifications",
                    attentionSnapshot && attentionSnapshot.badgeCount > 0 ? ` (${attentionSnapshot.badgeCount})` : ""),
                React.createElement("span", { className: "status-pill" },
                    "v",
                    release_1.STUDYFLOW_LOCAL_RELEASE.version,
                    " \u00B7 ",
                    persistence.backend),
                tvBrowser && React.createElement("span", { className: "status-pill tv-status-pill" },
                    "TV browser \u00B7 ",
                    document.documentElement.dataset.studyflowTvSecureContext === "true" ? "secure" : "LAN HTTP"))),
        React.createElement("main", { id: "main-content", ref: mainRef, tabIndex: -1, "aria-labelledby": "screen-title", className: "app-main" },
            tvBrowser && React.createElement(TvPairingBanner, { snapshot: tvReceiver }),
            " ",
            tvBrowser && tvCast && React.createElement(TvCastBanner, { cast: tvCast, onStop: () => setTvCast(null) }),
            " ",
            screen === "today" && React.createElement(Today, { state: state, store: store, focus: focus, run: run, onLaunch: launchActivity, onSessionOpen: () => navigateScreen("session") }),
            " ",
            screen === "plan" && React.createElement(Plan, { state: state, store: store, tab: planTab, setTab: setPlanTab, preview: preview, setPreview: setPreview, persistence: persistence, run: run, tvMaterialId: tvMaterialId, onTvMaterialOpen: (id) => openTvOverlay("material", id), onTvMaterialClose: closeTvOverlay, onTvScheduleOpen: () => navigateScreen("schedule") }),
            " ",
            screen === "practice" && React.createElement(Practice, { state: state, store: store, tab: practiceTab, setTab: setPracticeTab, questionId: questionId, setQuestionId: setQuestionId, context: practiceContext, run: run, onQuestionSetOpen: () => navigateScreen("questions"), onMockOpen: () => navigateScreen("mock"), onReviewOpen: (mode = "reviews") => { setPracticeTab(mode); navigateScreen("review"); } }),
            " ",
            screen === "progress" && (tvBrowser ? React.createElement(TvProgressAnalytics, { state: state, onOpenSurface: (surface) => navigateTvSurface(surface) }) : React.createElement(Progress, { state: state, store: store, tab: progressTab, setTab: setProgressTab, persistence: persistence, portable: portable, setPortable: setPortable, portableStatus: portableStatus, setPortableStatus: setPortableStatus, run: run })),
            " ",
            tvBrowser && screen === "session" && React.createElement(TvStudySession, { state: state, store: store, focus: focus, run: run, onBack: () => navigateScreen("today") }),
            " ",
            tvBrowser && screen === "questions" && React.createElement(TvQuestionSet, { state: state, store: store, questionId: questionId, setQuestionId: setQuestionId, context: practiceContext, run: run, onBack: () => navigateScreen("practice") }),
            " ",
            tvBrowser && screen === "mock" && React.createElement(TvMockExamStudio, { state: state, store: store, run: run, onBack: () => navigateScreen("practice") }),
            " ",
            tvBrowser && screen === "review" && React.createElement(TvReviewNotebook, { state: state, store: store, run: run, initialMode: practiceTab === "errors" ? "errors" : "reviews", onBack: () => navigateScreen("practice") }),
            " ",
            tvBrowser && screen === "schedule" && React.createElement(TvScheduleSurface, { state: state, onLaunch: launchActivity, onOpenSurface: (surface) => navigateTvSurface(surface), onBack: () => navigateScreen("plan") }),
            " ",
            tvBrowser && screen === "library" && React.createElement(TvLibrarySurface, { state: state, persistence: persistence, materialId: tvMaterialId, onMaterialOpen: (id) => openTvOverlay("material", id), onMaterialClose: closeTvOverlay, onBack: () => navigateScreen("plan") })),
        React.createElement("nav", { className: "bottom-nav", "aria-label": "Main navigation" }, (tvBrowser ? ["today", "plan", "practice", "progress", "schedule", "library"] : ["today", "plan", "practice", "progress"]).map(id => React.createElement("a", { key: id, href: `#${id}`, "aria-current": screen === id ? "page" : undefined, onClick: (event) => { if (tvBrowser) {
                event.preventDefault();
                navigateScreen(id);
            } } },
            React.createElement("span", null, title(id))))),
        searchOpen && React.createElement(GlobalSearchDialog, { state: state, onClose: closeTvOverlay, onOpen: openSearchResult }),
        " ",
        settingsOpen && React.createElement(SettingsDialog, { state: state, store: store, persistence: persistence, onClose: closeTvOverlay, run: run }),
        " ",
        attentionOpen && attentionRuntime && React.createElement(WebAttentionCenterDialog, { state: state, runtime: attentionRuntime, snapshot: attentionSnapshot !== null && attentionSnapshot !== void 0 ? attentionSnapshot : attentionRuntime.snapshot(), onClose: () => setAttentionOpen(false), run: run }),
        " ",
        !state.preferences.onboardingCompleted && (tvBrowser ? (tvSetupDismissed ? null : React.createElement(TvOnboardingHandoff, { onDismiss: () => setTvSetupDismissed(true) })) : React.createElement(OnboardingWizard, { state: state, store: store, run: run })),
        React.createElement("div", { id: "toast-region", className: "toast-region", role: "status", "aria-live": "polite", "aria-atomic": "true" }, toast && React.createElement("div", { className: "toast" }, toast)));
}
function WebAttentionCenterDialog({ state, runtime, snapshot, onClose, run }) { var _a, _b, _c; const [serverKey, setServerKey] = React.useState(""); const prefs = snapshot.preferences; const recent = snapshot.entries.slice(0, 24); const compactProjection = (0, compact_attention_1.projectWebAttentionToCompactCompanions)(snapshot); const setCategory = (category, enabled) => runtime.setPreferences({ categories: { [category]: enabled } }); const setNumber = (key, value) => runtime.setPreferences({ [key]: value }); return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
    React.createElement("section", { className: "modal-card settings-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "web-attention-title" },
        React.createElement("div", { className: "dialog-title" },
            React.createElement("h2", { id: "web-attention-title" }, "Notification Center"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, (0, product_1.uiText)(state.preferences.locale, "close"))),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, "Web/PWA capability"),
                React.createElement("p", { className: "muted" }, "Browser notifications are optional. StudyFlow still keeps an in-app notification center and foreground reminders when system notifications are disabled."),
                React.createElement("ul", { className: "activity-list" },
                    React.createElement("li", null,
                        React.createElement("strong", null, "Permission"),
                        React.createElement("span", null, snapshot.permission)),
                    React.createElement("li", null,
                        React.createElement("strong", null, "Service worker"),
                        React.createElement("span", null, snapshot.capabilities.serviceWorker ? "Ready" : "Unavailable")),
                    React.createElement("li", null,
                        React.createElement("strong", null, "Push foundation"),
                        React.createElement("span", null, snapshot.backgroundDelivery)),
                    React.createElement("li", null,
                        React.createElement("strong", null, "App badge"),
                        React.createElement("span", null, snapshot.capabilities.badge ? "Supported" : "Unsupported")),
                    React.createElement("li", null,
                        React.createElement("strong", null, "Last scan"),
                        React.createElement("span", null, snapshot.lastScanAtEpochMillis ? new Date(snapshot.lastScanAtEpochMillis).toLocaleString() : "Not scanned yet")),
                    React.createElement("li", null,
                        React.createElement("strong", null, "Delivery router"),
                        React.createElement("span", null,
                            snapshot.routeSummary.ownedCount,
                            " owned \u00B7 ",
                            snapshot.routeSummary.suppressedCount,
                            " suppressed"))),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-primary", type: "button", disabled: snapshot.permission === "unsupported", onClick: () => run(() => { void runtime.requestBrowserNotifications(); }, "Browser notification permission requested.") }, "Enable browser notifications"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => runtime.setPreferences({ systemNotificationsEnabled: false }), "Browser notifications disabled inside StudyFlow.") }, "Use in-app only"))),
            React.createElement("article", { className: "card wide-card" },
                React.createElement("h3", null, "ATT10 quiet hours and focus policy"),
                React.createElement("p", { className: "muted" }, "ATT10 changes delivery policy without deleting reminders. Deferred, silent, compact, routed and escalated decisions stay auditable in the ATT01 ledger. A closed browser is not treated as a guaranteed alarm owner unless Web Push is actually subscribed."),
                React.createElement("label", null,
                    "Focus profile",
                    React.createElement("select", { "aria-label": "Web attention focus profile", value: prefs.focusPolicy.profile, onChange: (e) => runtime.setFocusPolicy({ profile: e.target.value }) },
                        React.createElement("option", { value: "balanced" }, "Balanced"),
                        React.createElement("option", { value: "exam_mode" }, "Exam mode"),
                        React.createElement("option", { value: "work_mode" }, "Work mode"),
                        React.createElement("option", { value: "sleep_mode" }, "Sleep mode"),
                        React.createElement("option", { value: "travel_offline" }, "Travel / offline"),
                        React.createElement("option", { value: "minimal_tv" }, "Minimal / TV"))),
                React.createElement("label", { className: "check-row" },
                    React.createElement("input", { "aria-label": "Web ATT10 quiet hours enabled", type: "checkbox", checked: (_b = (_a = prefs.focusPolicy.quietWindow) === null || _a === void 0 ? void 0 : _a.enabled) !== null && _b !== void 0 ? _b : false, onChange: (e) => { var _a; return runtime.setFocusPolicy({ quietWindow: { ...((_a = prefs.focusPolicy.quietWindow) !== null && _a !== void 0 ? _a : { startMinute: 1320, endMinute: 420, timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone }), enabled: e.target.checked } }); } }),
                    "Quiet hours enabled"),
                prefs.focusPolicy.quietWindow && React.createElement("div", { className: "button-row" },
                    React.createElement("label", null,
                        "Quiet start minute",
                        React.createElement("input", { "aria-label": "Web ATT10 quiet start minute", type: "number", min: "0", max: "1439", value: prefs.focusPolicy.quietWindow.startMinute, onChange: (e) => runtime.setFocusPolicy({ quietWindow: { ...prefs.focusPolicy.quietWindow, startMinute: Number(e.target.value) } }) })),
                    React.createElement("label", null,
                        "Quiet end minute",
                        React.createElement("input", { "aria-label": "Web ATT10 quiet end minute", type: "number", min: "0", max: "1439", value: prefs.focusPolicy.quietWindow.endMinute, onChange: (e) => runtime.setFocusPolicy({ quietWindow: { ...prefs.focusPolicy.quietWindow, endMinute: Number(e.target.value) } }) })),
                    React.createElement("label", null,
                        "Timezone",
                        React.createElement("input", { "aria-label": "Web ATT10 timezone", value: (_c = prefs.focusPolicy.quietWindow.timezoneId) !== null && _c !== void 0 ? _c : "UTC", onChange: (e) => runtime.setFocusPolicy({ quietWindow: { ...prefs.focusPolicy.quietWindow, timezoneId: e.target.value } }) }))),
                React.createElement("h4", null, "Per-category escalation"),
                React.createElement("div", { className: "content-grid two-column" }, attentionCategoryOrder.map(category => { var _a; const policy = prefs.focusPolicy.categoryPolicies.find(value => value.category === category); return React.createElement("label", { key: `focus-${category}` },
                    attentionCategoryLabel(category),
                    React.createElement("select", { "aria-label": `Web ATT10 ${category} escalation mode`, value: (_a = policy === null || policy === void 0 ? void 0 : policy.mode) !== null && _a !== void 0 ? _a : "normal", onChange: (e) => runtime.setFocusCategoryMode(category, e.target.value) },
                        React.createElement("option", { value: "normal" }, "Normal"),
                        React.createElement("option", { value: "silent" }, "Silent"),
                        React.createElement("option", { value: "defer" }, "Defer in quiet hours"),
                        React.createElement("option", { value: "compact_only" }, "Compact only"),
                        React.createElement("option", { value: "route_to_phone" }, "Route to phone"),
                        React.createElement("option", { value: "mirror_if_unhandled" }, "Mirror if unhandled"),
                        React.createElement("option", { value: "require_user_action" }, "Require action"))); }))),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, "Reminder timing"),
                React.createElement("label", null,
                    "Study lead time",
                    React.createElement("input", { "aria-label": "Web study reminder lead minutes", type: "number", min: "0", max: "240", value: prefs.reminderLeadMinutes, onChange: (e) => setNumber("reminderLeadMinutes", Number(e.target.value)) })),
                React.createElement("label", null,
                    "Missed delay",
                    React.createElement("input", { "aria-label": "Web missed activity delay minutes", type: "number", min: "0", max: "720", value: prefs.missedDelayMinutes, onChange: (e) => setNumber("missedDelayMinutes", Number(e.target.value)) })),
                React.createElement("label", null,
                    "Session warning",
                    React.createElement("input", { "aria-label": "Web session warning lead minutes", type: "number", min: "0", max: "60", value: prefs.sessionLeadMinutes, onChange: (e) => setNumber("sessionLeadMinutes", Number(e.target.value)) })),
                React.createElement("label", null,
                    "Daily summary minute",
                    React.createElement("input", { "aria-label": "Web daily summary minute", type: "number", min: "0", max: "1439", value: prefs.dailySummaryMinute, onChange: (e) => setNumber("dailySummaryMinute", Number(e.target.value)) }))),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, "Reminder categories"),
                attentionCategoryOrder.map(category => React.createElement("label", { key: category, className: "check-row" },
                    React.createElement("input", { "aria-label": `Web category ${category}`, type: "checkbox", checked: prefs.categories[category], onChange: (e) => setCategory(category, e.target.checked) }),
                    attentionCategoryLabel(category)))),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, "Optional Web Push foundation"),
                React.createElement("p", { className: "muted" }, "Push is available only when a StudyFlow relay/application-server key is configured. This records browser subscription capability without pretending closed-browser alarms work everywhere."),
                React.createElement("input", { "aria-label": "Web Push application server key", value: serverKey, onChange: (e) => setServerKey(e.target.value), placeholder: "VAPID public key" }),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-quiet", type: "button", disabled: !snapshot.capabilities.push || !serverKey.trim(), onClick: () => run(() => { void runtime.subscribePush(serverKey); }, "Web Push subscription requested.") }, "Subscribe"),
                    React.createElement("button", { className: "button button-quiet", type: "button", disabled: !snapshot.pushSubscription, onClick: () => run(() => { void runtime.unsubscribePush(); }, "Web Push subscription removed.") }, "Unsubscribe")),
                snapshot.pushSubscription && React.createElement("p", { className: "muted" }, "Subscribed endpoint stored locally.")),
            React.createElement("article", { className: "card wide-card" },
                React.createElement("h3", null, "Compact companion surface"),
                React.createElement("p", { className: "muted" }, "Phone-owned wearable, lock-screen, quick tile and compact Web projections mirror the ATT07 parent route. They never become independent owners."),
                React.createElement("p", { className: "muted" },
                    compactProjection.diagnostics.projectedCount,
                    " compact cards \u00B7 ",
                    compactProjection.diagnostics.suppressedCompanionIds.length,
                    " suppressed \u00B7 ",
                    compactProjection.diagnostics.explanation),
                React.createElement("ul", { className: "activity-list" },
                    compactProjection.cards.map(card => React.createElement("li", { key: card.companionId },
                        React.createElement("div", null,
                            React.createElement("strong", null,
                                card.companionDisplayName,
                                " \u00B7 ",
                                title(card.renderMode)),
                            React.createElement("span", null,
                                card.title,
                                " \u00B7 ",
                                card.body)),
                        React.createElement("span", null, card.actions.map(action => action.label).join(" · ")))),
                    !compactProjection.cards.length && React.createElement("li", null, "No compact companion card is active yet."))),
            React.createElement("article", { className: "card wide-card" },
                React.createElement("h3", null, "Upcoming and recent attention"),
                React.createElement("p", { className: "muted" },
                    snapshot.upcomingCount,
                    " upcoming \u00B7 ",
                    snapshot.recentCount,
                    " recent \u00B7 badge ",
                    snapshot.badgeCount),
                React.createElement("ul", { className: "activity-list" },
                    recent.map(entry => { var _a; return React.createElement("li", { key: entry.occurrence.occurrenceId },
                        React.createElement("div", null,
                            React.createElement("strong", null,
                                attentionCategoryLabel(entry.occurrence.category),
                                " \u00B7 ",
                                title(entry.state)),
                            React.createElement("span", null,
                                attentionEntryTime(entry),
                                " \u00B7 ",
                                ((_a = entry.occurrence.payload) === null || _a === void 0 ? void 0 : _a.summary) ? String(entry.occurrence.payload.summary) : entry.occurrence.occurrenceId,
                                entry.policyAudit ? ` · ATT10 ${entry.policyAudit.outcome}` : "")),
                        React.createElement("div", { className: "button-row" },
                            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { void runtime.applyAction(entry.occurrence.occurrenceId, "open"); }, "Reminder opened.") }, "Open"),
                            entry.state !== "dismissed" && entry.state !== "completed" && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { void runtime.applyAction(entry.occurrence.occurrenceId, "snooze", 10); }, "Reminder snoozed.") }, "Snooze"),
                            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { void runtime.applyAction(entry.occurrence.occurrenceId, "dismiss"); }, "Reminder dismissed.") }, "Dismiss"))); }),
                    !recent.length && React.createElement("li", null, "No Web reminders have been scheduled yet. Keep StudyFlow open or enable notifications to test delivery.")))))); }
function GlobalSearchDialog({ state, onClose, onOpen }) { const [query, setQuery] = React.useState(""); const results = (0, product_1.searchStudyState)(state, query, { limit: 50 }); return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
    React.createElement("section", { className: "modal-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "global-search-title" },
        React.createElement("div", { className: "dialog-title" },
            React.createElement("h2", { id: "global-search-title" }, (0, product_1.uiText)(state.preferences.locale, "search")),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, (0, product_1.uiText)(state.preferences.locale, "close"))),
        React.createElement("input", { autoFocus: !isTvBrowserUi(), "aria-label": "Search local study data", value: query, onChange: (e) => setQuery(e.target.value), placeholder: "Subjects, topics, questions, materials, errors\u2026" }),
        React.createElement("p", { className: "muted" },
            "Local-only search \u00B7 Ctrl/\u2318+K or / \u00B7 ",
            state.preferences.showArchivedInSearch ? "archived plans included" : "active plans only"),
        React.createElement("ul", { className: "search-results" },
            results.map(item => React.createElement("li", { key: `${item.type}:${item.id}` },
                React.createElement("button", { type: "button", onClick: () => onOpen(item) },
                    React.createElement("strong", null, item.title),
                    React.createElement("span", null,
                        title(item.type),
                        " \u00B7 ",
                        item.detail)))),
            query.trim().length >= 2 && !results.length && React.createElement("li", null, (0, product_1.uiText)(state.preferences.locale, "noResults"))))); }
function SettingsDialog({ state, store, persistence, onClose, run }) { var _a, _b, _c, _d, _e; const plan = (_a = state.plans.find(p => p.id === state.activePlanId)) !== null && _a !== void 0 ? _a : null; const schedule = plan === null || plan === void 0 ? void 0 : plan.configuration.schedule; const review = (plan === null || plan === void 0 ? void 0 : plan.mode) === "EXAM_PLAN" ? (_c = (_b = plan.configuration.publicExam) === null || _b === void 0 ? void 0 : _b.reviewIntervalsDays) !== null && _c !== void 0 ? _c : [1, 7, 30] : null; const [capacity, setCapacity] = React.useState((_d = schedule === null || schedule === void 0 ? void 0 : schedule.dailyCapacityMinutes) !== null && _d !== void 0 ? _d : 120); const [intervals, setIntervals] = React.useState((_e = review === null || review === void 0 ? void 0 : review.join(", ")) !== null && _e !== void 0 ? _e : "1, 7, 30"); if (isTvBrowserUi())
    return React.createElement(TvSettingsDialog, { state: state, store: store, persistence: persistence, onClose: onClose, run: run }); return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
    React.createElement("section", { className: "modal-card settings-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "settings-title" },
        React.createElement("div", { className: "dialog-title" },
            React.createElement("h2", { id: "settings-title" }, (0, product_1.uiText)(state.preferences.locale, "settings")),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, (0, product_1.uiText)(state.preferences.locale, "close"))),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, (0, product_1.uiText)(state.preferences.locale, "language")),
                React.createElement("select", { "aria-label": "Application language", value: state.preferences.locale, onChange: (e) => run(() => store.setPreferences({ locale: e.target.value }), "Language preference saved locally.") },
                    React.createElement("option", { value: "en" }, "English"),
                    React.createElement("option", { value: "pt-BR" }, "Portugu\u00EAs (Brasil)")),
                React.createElement("label", { className: "check-row" },
                    React.createElement("input", { "aria-label": "Include archived plans in global search", type: "checkbox", checked: state.preferences.showArchivedInSearch, onChange: (e) => run(() => store.setPreferences({ showArchivedInSearch: e.target.checked }), "Search preference saved.") }),
                    "Include archived plans in global search"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setPreferences({ onboardingCompleted: false }), "Onboarding reopened.") }, "Run onboarding again")),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, "Plan preferences"),
                plan ? React.createElement(React.Fragment, null,
                    React.createElement("label", null,
                        "Daily capacity (minutes)",
                        React.createElement("input", { "aria-label": "Settings daily capacity", type: "number", min: "15", max: "1440", value: capacity, onChange: (e) => setCapacity(Number(e.target.value)) })),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setScheduleConfiguration({ dailyCapacityMinutes: capacity }), "Schedule preference saved.") }, "Save capacity"),
                    review && React.createElement(React.Fragment, null,
                        React.createElement("label", null,
                            "Review intervals (days)",
                            React.createElement("input", { "aria-label": "Settings review intervals", value: intervals, onChange: (e) => setIntervals(e.target.value) })),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setReviewIntervalsDays(intervals.split(/[,\s]+/).filter(Boolean).map(Number)), "Review preference saved.") }, "Save review policy"))) : React.createElement("p", null, "No active plan.")),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, (0, product_1.uiText)(state.preferences.locale, "privacy")),
                React.createElement("p", null, (0, product_1.uiText)(state.preferences.locale, "localOnly")),
                React.createElement("ul", null,
                    React.createElement("li", null, "Required account/backend: none"),
                    React.createElement("li", null, "Telemetry/ads: none"),
                    React.createElement("li", null,
                        "Persistence: ",
                        persistence.backend),
                    React.createElement("li", null, "Portable backup: v2 includes attachment bytes and SHA-256 integrity")),
                React.createElement("p", { className: "muted" }, (0, product_1.uiText)(state.preferences.locale, "encryption"))),
            React.createElement(StorageHealthCard, { state: state, persistence: persistence })))); }
function TvSettingsDialog({ state, store, persistence, onClose, run }) { return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
    React.createElement("section", { className: "modal-card settings-card tv-settings-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "settings-title" },
        React.createElement("div", { className: "dialog-title" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "TV browser"),
                React.createElement("h2", { id: "settings-title" }, (0, product_1.uiText)(state.preferences.locale, "settings"))),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, (0, product_1.uiText)(state.preferences.locale, "close"))),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, (0, product_1.uiText)(state.preferences.locale, "language")),
                React.createElement("select", { "aria-label": "Application language", value: state.preferences.locale, onChange: (e) => run(() => store.setPreferences({ locale: e.target.value }), "Language preference saved locally.") },
                    React.createElement("option", { value: "en" }, "English"),
                    React.createElement("option", { value: "pt-BR" }, "Portugu\u00EAs (Brasil)")),
                React.createElement("label", { className: "check-row" },
                    React.createElement("input", { "aria-label": "Include archived plans in global search", type: "checkbox", checked: state.preferences.showArchivedInSearch, onChange: (e) => run(() => store.setPreferences({ showArchivedInSearch: e.target.checked }), "Search preference saved.") }),
                    "Include archived plans in global search")),
            React.createElement("article", { className: "card" },
                React.createElement("h3", null, (0, product_1.uiText)(state.preferences.locale, "privacy")),
                React.createElement("p", null, (0, product_1.uiText)(state.preferences.locale, "localOnly")),
                React.createElement("ul", null,
                    React.createElement("li", null, "Required account/backend: none"),
                    React.createElement("li", null, "Telemetry/ads: none"),
                    React.createElement("li", null,
                        "Persistence: ",
                        persistence.backend),
                    React.createElement("li", null, "TV mode: remote-first presentation"))),
            React.createElement(StorageHealthCard, { state: state, persistence: persistence, allowCleanup: false }),
            React.createElement(TvContinueElsewhere, { title: "Plan and backup settings", detail: "Daily capacity, review intervals, onboarding resets, imports, encrypted backups and destructive state controls are intentionally not edited with a TV remote." })))); }
function TvOnboardingHandoff({ onDismiss }) { return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
    React.createElement("section", { className: "modal-card tv-settings-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "tv-setup-title" },
        React.createElement("p", { className: "eyebrow" }, "TV browser"),
        React.createElement("h2", { id: "tv-setup-title" }, "Finish initial setup on phone or computer"),
        React.createElement("p", null, "Creating a plan, choosing a study mode and importing starter content require the authoring workspace and are intentionally not exposed to a TV remote."),
        React.createElement("p", { className: "muted" }, "If this browser already has local StudyFlow data, you can dismiss this notice and continue with the available read-only and low-input TV workflows."),
        React.createElement("button", { className: "button button-primary", type: "button", onClick: onDismiss }, "Continue with local data"))); }
function OnboardingWizard({ state, store, run }) {
    var _a, _b, _c, _d;
    const [locale, setLocale] = React.useState(state.preferences.locale);
    const [mode, setMode] = React.useState((_b = (_a = state.plans.find(p => p.id === state.activePlanId)) === null || _a === void 0 ? void 0 : _a.mode) !== null && _b !== void 0 ? _b : "EXAM_PLAN");
    const [name, setName] = React.useState((_d = (_c = state.plans.find(p => p.id === state.activePlanId)) === null || _c === void 0 ? void 0 : _c.name) !== null && _d !== void 0 ? _d : "My Study Plan");
    const [template, setTemplate] = React.useState("public-exam-12-week");
    const bb = template === "bb-agente-comercial-pre-edital", blueprint = !bb && template !== "blank" ? refinement_1.SAMPLE_PLAN_BLUEPRINTS[template] : null;
    const choose = (value) => { const next = value; setTemplate(next); if (next === "bb-agente-comercial-pre-edital") {
        setMode("EXAM_PLAN");
        setName(locale === "pt-BR" ? "BB Agente Comercial — pré-edital" : "BB Agente Comercial — pre-edital");
        return;
    } const sample = next !== "blank" ? refinement_1.SAMPLE_PLAN_BLUEPRINTS[next] : null; if (sample) {
        setMode(sample.mode);
        setName(sample.label[locale]);
    } };
    const fmtTime = (minute) => `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;
    const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    const fromIsoDay = (d) => d === 7 ? 0 : d;
    return React.createElement("div", { className: "modal-backdrop onboarding-backdrop", role: "presentation" },
        React.createElement("section", { className: "modal-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "onboarding-title" },
            React.createElement("h2", { id: "onboarding-title" }, (0, product_1.uiText)(locale, "onboarding")),
            React.createElement("p", { className: "muted" }, "Choose a local workspace. Starter templates are preview-first: nothing is created until you press the create button."),
            React.createElement("label", null,
                (0, product_1.uiText)(locale, "language"),
                React.createElement("select", { "aria-label": "Onboarding language", value: locale, onChange: (e) => { const next = e.target.value; setLocale(next); if (blueprint)
                        setName(blueprint.label[next]); } },
                    React.createElement("option", { value: "en" }, "English"),
                    React.createElement("option", { value: "pt-BR" }, "Portugu\u00EAs (Brasil)"))),
            React.createElement("label", null,
                "Starter template",
                React.createElement("select", { "aria-label": "Onboarding starter template", value: template, onChange: (e) => choose(e.target.value) },
                    React.createElement("option", { value: "public-exam-12-week" }, "12-week public exam"),
                    React.createElement("option", { value: "university-semester" }, "University semester"),
                    React.createElement("option", { value: "certification-bootcamp" }, "Certification bootcamp"),
                    React.createElement("option", { value: "free-study-habit" }, "Free-study habit"),
                    React.createElement("option", { value: "bb-agente-comercial-pre-edital" }, "BB Agente Comercial \u2014 pre-edital"),
                    React.createElement("option", { value: "blank" }, "Blank workspace"))),
            blueprint && React.createElement("article", { className: "mode-banner" },
                React.createElement("strong", null, blueprint.label[locale]),
                React.createElement("span", null,
                    blueprint.subjects.length,
                    " subjects \u00B7 ",
                    blueprint.subjects.reduce((n, x) => n + x.topics.length, 0),
                    " topics \u00B7 ",
                    blueprint.weeklyGoalHours,
                    " h/week \u00B7 preferred session ",
                    blueprint.preferredSessionMinutes,
                    " min"),
                React.createElement("span", { className: "muted" },
                    "Local only \u00B7 preview first \u00B7 user initiated \u00B7 adaptive planning ",
                    blueprint.adaptivePlanningEnabled ? "on" : "off"),
                React.createElement("ul", { className: "activity-list" }, blueprint.subjects.map(subject => React.createElement("li", { key: subject.key },
                    React.createElement("strong", null, subject.name[locale]),
                    React.createElement("span", null, subject.topics.map(t => t.name[locale]).join(" · "))))),
                React.createElement("h3", null, "Weekly schedule preview"),
                React.createElement("ul", { className: "activity-list" }, blueprint.scheduleRules.map((r, i) => { var _a, _b; return React.createElement("li", { key: i },
                    React.createElement("strong", null,
                        days[fromIsoDay(r.weekday)],
                        " ",
                        fmtTime(r.startMinute),
                        " \u00B7 ",
                        r.durationMinutes,
                        " min"),
                    React.createElement("span", null, r.subjectKey ? (_b = (_a = blueprint.subjects.find(x => x.key === r.subjectKey)) === null || _a === void 0 ? void 0 : _a.name[locale]) !== null && _b !== void 0 ? _b : "Study block" : "Flexible study block")); }))),
            bb && React.createElement("article", { className: "mode-banner" },
                React.createElement("strong", null, task_workflows_1.BB_AGENTE_COMERCIAL_PRE_EDITAL),
                React.createElement("span", { className: "muted" }, "Banco do Brasil \u00B7 Escritur\u00E1rio \u2014 Agente Comercial \u00B7 Funda\u00E7\u00E3o Cesgranrio \u00B7 Sele\u00E7\u00E3o Externa 2022/001 reference only."),
                React.createElement("span", { className: "muted" }, "8 objective subjects \u00B7 100 reference points \u00B7 70 objective questions \u00B7 separate essay threshold 70/100 \u00B7 19 h/week \u00B7 1/7/30 reviews \u00B7 80% weekly accuracy \u00B7 1 writing session/week."),
                React.createElement("span", { className: "muted" }, "No 2026 edital or exam date is fabricated.")),
            React.createElement("label", null,
                "Study mode",
                React.createElement("select", { "aria-label": "Onboarding study mode", disabled: bb || Boolean(blueprint), value: mode, onChange: (e) => setMode(e.target.value) }, modes.map(([id, label]) => React.createElement("option", { key: id, value: id }, label)))),
            React.createElement("label", null,
                "Plan name",
                React.createElement("input", { "aria-label": "Onboarding plan name", value: name, onChange: (e) => setName(e.target.value), maxLength: 120 })),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-primary", type: "button", onClick: () => run(() => store.completeOnboarding({ locale, mode, planName: name, useSample: false, template }), bb ? "BB Agente Comercial reference workspace created." : blueprint ? "Starter plan created from the approved preview." : "Local workspace ready.") }, (0, product_1.uiText)(locale, "start")),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setPreferences({ locale, onboardingCompleted: true }), "Sample workspace kept.") }, (0, product_1.uiText)(locale, "skip")))));
}
function TvGoalQuickActions({ state, store, run }) { const goals = state.goals.filter(g => g.planId === state.activePlanId); if (!goals.length)
    return React.createElement(TvContinueElsewhere, { title: "Goals", detail: "Goal creation remains on phone/computer. There are no existing goals to manage from the remote." }); return React.createElement("article", { className: "card tv-goal-quick-actions" },
    React.createElement("div", { className: "card-heading-row" },
        React.createElement("div", null,
            React.createElement("p", { className: "eyebrow" }, "Goals"),
            React.createElement("h2", null, "Low-input goal controls"),
            React.createElement("p", { className: "muted" }, "Completion and pause/resume stay available on TV; creating or editing goal targets remains on phone/computer."))),
    React.createElement("div", { className: "tv-goal-grid" }, goals.map(g => { var _a; const progress = (0, domain_1.deriveGoalProgress)(state, g); return React.createElement("article", { className: "nested-panel", key: g.id },
        React.createElement("strong", null,
            g.name,
            g.completedAt ? " · completed" : ""),
        React.createElement("span", { className: "muted" }, (_a = progress.actual) !== null && _a !== void 0 ? _a : "No data",
            " / ",
            g.target,
            " \u00B7 ",
            g.active ? "active" : "paused"),
        React.createElement("div", { className: "button-row" },
            !g.completedAt && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeGoal(g.id), "Goal marked complete and added to the Evidence Timeline.") }, "Mark complete"),
            React.createElement("button", { className: "button button-quiet", type: "button", disabled: Boolean(g.completedAt), onClick: () => run(() => store.toggleGoal(g.id), g.active ? "Goal paused." : "Goal resumed.") }, g.active ? "Pause" : "Resume"))); }))); }
function Today({ state, store, focus, run, onLaunch, onSessionOpen }) { var _a, _b, _c, _d; const tv = isTvBrowserUi(); const rec = (0, domain_1.recommendNextActivity)(state); const [name, setName] = React.useState(""); const [note, setNote] = React.useState((_a = focus.note) !== null && _a !== void 0 ? _a : ""); const [activityType, setActivityType] = React.useState("THEORY"); const planned = state.activities.filter(a => a.planId === state.activePlanId && a.queue === "today" && a.status === "planned"); const activeActivity = focus.activityId ? (_b = state.activities.find(a => a.id === focus.activityId)) !== null && _b !== void 0 ? _b : null : null; const writing = (0, task_workflows_1.writingActivity)(activeActivity); return React.createElement("section", { id: "today", "data-screen": "today", className: "screen-section" },
    React.createElement("article", { className: "hero-card" },
        React.createElement("p", { className: "eyebrow" }, "Next up"),
        React.createElement("h2", null, (_c = rec === null || rec === void 0 ? void 0 : rec.activity.title) !== null && _c !== void 0 ? _c : "No queued activity"),
        React.createElement("p", { className: "muted" }, (_d = rec === null || rec === void 0 ? void 0 : rec.reason) !== null && _d !== void 0 ? _d : "Add an activity or generate a schedule preview.")),
    React.createElement(ModeToday, { state: state }),
    React.createElement(StudyCycleTodayCard, { state: state, store: store, run: run }),
    tv && React.createElement(TvGoalQuickActions, { state: state, store: store, run: run }),
    React.createElement("div", { className: "content-grid two-column" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Focus"),
            React.createElement("div", { className: "focus-clock", "aria-label": `Focus time remaining ${clock(focus.remainingMs)}` }, clock(focus.remainingMs)),
            !writing && !tv && React.createElement("textarea", { "aria-label": "Focus session note", value: note, onChange: (e) => setNote(e.target.value), placeholder: "Session note" }),
            !writing && tv && React.createElement("p", { className: "muted tv-inline-note" }, "Session-note editing is available on phone/computer; focus controls remain available here."),
            writing && React.createElement("p", { className: "muted" }, "This is a WRITING session. Draft, rubric and mandatory completion checklist are owned by the Writing Workspace below; the generic Finish action is intentionally unavailable."),
            React.createElement("div", { className: "button-row" },
                focus.status !== "running" && !writing && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": focus.status === "paused" ? "RESUME" : "START", onClick: () => run(() => store.startFocus({ note }), "Focus started.") }, focus.status === "paused" ? "Resume" : "Start"),
                focus.status !== "running" && writing && activeActivity && !tv && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => run(() => { var _a, _b, _c; return store.startWritingSession({ activityId: activeActivity.id, prompt: (_c = (_b = (0, task_workflows_1.decodeWritingSessionRecord)((_a = (0, task_workflows_1.activeWritingSession)(state)) === null || _a === void 0 ? void 0 : _a.note)) === null || _b === void 0 ? void 0 : _b.prompt) !== null && _c !== void 0 ? _c : activeActivity.title, durationMinutes: activeActivity.durationMinutes }); }, "Writing resumed.") }, "Resume Writing"),
                focus.status === "running" && React.createElement("button", { className: "button", type: "button", "data-tv-control-action": "PAUSE", onClick: () => run(() => store.pauseFocus(), "Focus paused.") }, "Pause"),
                focus.status !== "idle" && React.createElement(React.Fragment, null,
                    !writing && React.createElement("button", { className: "button", type: "button", "data-tv-control-action": "COMPLETE", onClick: () => run(() => store.completeFocus({ note }), "Focus completed and evidenced.") }, "Complete"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.cancelFocus({ note }), "Focus cancelled.") }, "Cancel")),
                tv && !writing && focus.status !== "idle" && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "OPEN", onClick: onSessionOpen }, "Open Study Session"),
                tv && !writing && focus.status === "idle" && (rec === null || rec === void 0 ? void 0 : rec.activity) && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", "data-tv-control-target": rec.activity.id, onClick: () => onLaunch(rec.activity.id) }, "Start TV session"))),
        tv ? React.createElement(TvContinueElsewhere, { title: "Quick Add", detail: "Creating and naming activities is kept off-TV to avoid long virtual-keyboard entry." }) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Quick Add"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim()) {
                    run(() => store.addActivity({ title: name.trim(), kind: (0, task_workflows_1.activityKindForType)(activityType), activityType, durationMinutes: activityType === "WRITING" ? 60 : 25 }), "Activity added locally.");
                    setName("");
                } } },
                React.createElement("input", { "aria-label": "Activity title", value: name, onChange: (e) => setName(e.target.value), placeholder: "Activity title", required: true }),
                React.createElement("select", { "aria-label": "Study activity type", value: activityType, onChange: (e) => setActivityType(e.target.value) }, ["THEORY", "VIDEO", "READING", "CUSTOM", "QUESTIONS", "FLASHCARDS", "REVIEW", "MOCK_EXAM", "WRITING"].map(type => React.createElement("option", { key: type, value: type }, title(type)))),
                React.createElement("button", { className: "button", type: "submit" }, "Add activity")))),
    writing && (tv ? React.createElement(TvContinueElsewhere, { title: "Writing Workspace", detail: "Essay drafting, rubric scoring and checklist editing require a keyboard-sized workspace." }) : React.createElement(WritingWorkspace, { state: state, store: store, run: run })),
    React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Today queue"),
        React.createElement("ul", { className: "activity-list" },
            planned.map(a => React.createElement("li", { key: a.id },
                React.createElement("strong", null, a.title),
                React.createElement("span", null,
                    a.durationMinutes,
                    " min \u00B7 ",
                    title(a.activityType)),
                React.createElement("button", { type: "button", className: "button button-quiet", "data-tv-control-action": "OPEN", "data-tv-control-target": a.id, onClick: () => onLaunch(a.id) },
                    "Open ",
                    title(a.activityType)))),
            !planned.length && React.createElement("li", null, "No planned activities remain today.")))); }
function WritingWorkspace({ state, store, run }) { var _a, _b, _c, _d; const session = (0, task_workflows_1.activeWritingSession)(state); const record = (0, task_workflows_1.decodeWritingSessionRecord)(session === null || session === void 0 ? void 0 : session.note); const progress = (0, task_workflows_1.deriveWritingWeeklyProgress)(state, new Date()); const history = state.sessions.filter(item => item.planId === state.activePlanId && item.status === "completed" && (0, task_workflows_1.decodeWritingSessionRecord)(item.note) != null).sort((a, b) => { var _a, _b; return String((_a = b.endedAt) !== null && _a !== void 0 ? _a : "").localeCompare(String((_b = a.endedAt) !== null && _b !== void 0 ? _b : "")); }); const [prompt, setPrompt] = React.useState((_a = record === null || record === void 0 ? void 0 : record.prompt) !== null && _a !== void 0 ? _a : ""); const [draft, setDraft] = React.useState((_b = record === null || record === void 0 ? void 0 : record.draftText) !== null && _b !== void 0 ? _b : ""); const [rubric, setRubric] = React.useState((_c = record === null || record === void 0 ? void 0 : record.rubric) !== null && _c !== void 0 ? _c : { themeAndTask: 0, textType: 0, cohesion: 0, argumentation: 0, standardLanguage: 0 }); const [checklist, setChecklist] = React.useState((_d = record === null || record === void 0 ? void 0 : record.checklist) !== null && _d !== void 0 ? _d : { taskReviewed: false, structureReviewed: false, cohesionReviewed: false, languageReviewed: false }); React.useEffect(() => { var _a, _b; const next = (0, task_workflows_1.decodeWritingSessionRecord)((_a = (0, task_workflows_1.activeWritingSession)(state)) === null || _a === void 0 ? void 0 : _a.note); if (next) {
    setPrompt(next.prompt);
    setDraft(next.draftText);
    setRubric((_b = next.rubric) !== null && _b !== void 0 ? _b : { themeAndTask: 0, textType: 0, cohesion: 0, argumentation: 0, standardLanguage: 0 });
    setChecklist(next.checklist);
} }, [session === null || session === void 0 ? void 0 : session.id, session === null || session === void 0 ? void 0 : session.updatedAt]); const score = (0, task_workflows_1.writingRubricTotal)(rubric); const dimension = (key, label) => React.createElement("label", null,
    label,
    React.createElement("input", { "aria-label": `Writing rubric ${label}`, type: "number", min: "0", max: "20", value: rubric[key], onChange: (e) => setRubric({ ...rubric, [key]: Math.max(0, Math.min(20, Number(e.target.value) || 0)) }) })); const check = (key, label) => React.createElement("label", { className: "check-row" },
    React.createElement("input", { "aria-label": `Writing checklist ${label}`, type: "checkbox", checked: checklist[key], onChange: (e) => setChecklist({ ...checklist, [key]: e.target.checked }) }),
    label); return React.createElement("article", { className: "card wide-card", id: "writing-workspace" },
    React.createElement("div", { className: "card-heading-row" },
        React.createElement("div", null,
            React.createElement("h2", null, "Writing Workspace"),
            React.createElement("p", { className: "muted" }, "Durable draft lives in the canonical StudySession note payload. Five 0\u201320 dimensions total 0\u2013100; all checklist items are mandatory before Finish.")),
        React.createElement("strong", null, progress.target == null ? `${progress.completed} completed this week` : `${progress.completed}/${progress.target} weekly writing sessions`)),
    session && React.createElement(React.Fragment, null,
        React.createElement("label", null,
            "Theme / task",
            React.createElement("textarea", { "aria-label": "Writing prompt", value: prompt, onChange: (e) => setPrompt(e.target.value), placeholder: "Writing theme / task" })),
        React.createElement("label", null,
            "Draft",
            React.createElement("textarea", { className: "writing-editor", "aria-label": "Writing draft", value: draft, onChange: (e) => setDraft(e.target.value), placeholder: "Write the full draft here" })),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("div", { className: "stack-form" },
                React.createElement("h3", null,
                    "Rubric \u00B7 ",
                    score,
                    "/100"),
                dimension("themeAndTask", "Theme and task"),
                dimension("textType", "Text type"),
                dimension("cohesion", "Cohesion"),
                dimension("argumentation", "Argumentation"),
                dimension("standardLanguage", "Standard language")),
            React.createElement("div", { className: "stack-form" },
                React.createElement("h3", null, "Completion checklist"),
                check("taskReviewed", "Task reviewed"),
                check("structureReviewed", "Structure reviewed"),
                check("cohesionReviewed", "Cohesion reviewed"),
                check("languageReviewed", "Standard language reviewed"))),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button", type: "button", onClick: () => run(() => store.saveWritingDraft({ prompt, draftText: draft }), "Writing draft saved in StudySession.") }, "Save draft"),
            React.createElement("button", { className: "button button-primary", type: "button", disabled: !prompt.trim() || !draft.trim() || !(0, task_workflows_1.writingChecklistComplete)(checklist), onClick: () => run(() => store.completeWriting({ prompt, draftText: draft, rubric, checklist }), "Writing session completed and evidenced.") }, "Finish Writing"))),
    React.createElement("details", null,
        React.createElement("summary", null,
            "Writing history \u00B7 ",
            history.length),
        React.createElement("ul", { className: "activity-list" },
            history.map(item => { var _a, _b; const r = (0, task_workflows_1.decodeWritingSessionRecord)(item.note); return React.createElement("li", { key: item.id },
                React.createElement("strong", null, r.prompt),
                React.createElement("span", null, (_b = (_a = item.endedAt) === null || _a === void 0 ? void 0 : _a.slice(0, 10)) !== null && _b !== void 0 ? _b : "",
                    " \u00B7 ",
                    r.rubric ? `${(0, task_workflows_1.writingRubricTotal)(r.rubric)}/100` : "No rubric")); }),
            !history.length && React.createElement("li", null, "No completed writing sessions yet.")))); }
function ModeToday({ state }) { var _a, _b; const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return null; if (plan.mode === "EXAM_PLAN") {
    const g = (0, modes_1.deriveFinalStretchGuidance)(plan);
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null,
            "Concurso mode \u00B7 ",
            g.band === "unknown" ? "exam date not confirmed" : `${g.daysRemaining} days · ${g.band}`),
        React.createElement("span", { className: "muted" }, "Final-stretch guidance is derived only from a confirmed date."));
} if (plan.mode === "PROGRAMMING_COURSE")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null, (_a = plan.configuration.programming) === null || _a === void 0 ? void 0 :
            _a.language,
            " course"),
        React.createElement("span", { className: "muted" }, "Exercises remain local; browser StudyFlow does not execute arbitrary shell commands.")); if (plan.mode === "LANGUAGE_DECK")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null, (_b = plan.configuration.language) === null || _b === void 0 ? void 0 :
            _b.language,
            " language deck"),
        React.createElement("span", { className: "muted" }, "Recognition, production, fill-in, usage, mistake spotting and sentence creation.")); if (plan.mode === "READING_STUDY")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null, "Reading study"),
        React.createElement("span", { className: "muted" }, "Progress is recorded from explicit reading updates.")); return React.createElement("article", { className: "card mode-banner" },
    React.createElement("strong", null, "General skill"),
    React.createElement("span", { className: "muted" }, "Drill outcomes become evidence only when you record them.")); }
function StudyCycleTodayCard({ state, store, run }) { var _a, _b, _c, _d; const plan = state.plans.find(p => p.id === state.activePlanId); if (!(0, promise_cleanup_1.shouldOfferStudyCycle)(plan))
    return null; const cycle = (_b = (_a = state.studyCycles) === null || _a === void 0 ? void 0 : _a[plan.id]) !== null && _b !== void 0 ? _b : state.studyCycle, currentId = (_c = cycle.subjectIds[cycle.cursor]) !== null && _c !== void 0 ? _c : null, current = state.subjects.find(subject => subject.id === currentId); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Study Cycle"),
    React.createElement("p", null,
        React.createElement("strong", null, (_d = current === null || current === void 0 ? void 0 : current.name) !== null && _d !== void 0 ? _d : "No active cycle subject")),
    React.createElement("p", { className: "muted" }, "Available here because the persisted schedule strategy is STUDY_CYCLE."),
    React.createElement("div", { className: "button-row" },
        React.createElement("button", { className: "button button-quiet", type: "button", disabled: !currentId, onClick: () => run(() => store.advanceStudyCycle(), "Study cycle advanced.") }, "Advance"),
        React.createElement("button", { className: "button button-quiet", type: "button", disabled: !currentId, onClick: () => run(() => store.skipStudyCycleSubject(), "Current cycle subject skipped.") }, "Skip"),
        React.createElement("button", { className: "button button-quiet", type: "button", disabled: !currentId, onClick: () => run(() => store.moveCurrentCycleSubjectToEnd(), "Current cycle subject moved to end.") }, "Move current to end"))); }
function TopicTree({ state, store, subjectId, parentTopicId = null, run }) {
    const topics = state.topics.filter(t => { var _a; return t.subjectId === subjectId && ((_a = t.parentTopicId) !== null && _a !== void 0 ? _a : null) === parentTopicId; }).slice().sort((a, b) => a.order - b.order || a.name.localeCompare(b.name));
    if (!topics.length)
        return null;
    return React.createElement("ul", { className: parentTopicId ? "activity-list nested-topic-list" : "activity-list" }, topics.map(t => React.createElement("li", { key: t.id },
        React.createElement("div", { className: "topic-row" },
            React.createElement("div", null,
                React.createElement("strong", null, t.name),
                React.createElement("span", null,
                    title(t.priority),
                    " \u00B7 ",
                    title(t.status))),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.moveTopic(t.id, "up"), "Topic moved.") }, "\u2191"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.moveTopic(t.id, "down"), "Topic moved.") }, "\u2193"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateTopic(t.id, { status: t.status === "archived" ? "not-started" : "archived" }), t.status === "archived" ? "Topic restored." : "Topic archived.") }, t.status === "archived" ? "Restore" : "Archive"))),
        React.createElement(TopicTree, { state: state, store: store, subjectId: subjectId, parentTopicId: t.id, run: run }))));
}
function Plan({ state, store, tab, setTab, preview, setPreview, persistence, run, tvMaterialId, onTvMaterialOpen, onTvMaterialClose, onTvScheduleOpen }) {
    var _a, _b, _c, _d, _e;
    const inv = (0, domain_1.derivePlanInventory)(state);
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const activePlans = state.plans.filter(p => p.status !== "archived");
    const archivedPlans = state.plans.filter(p => p.status === "archived");
    const [newPlanName, setNewPlanName] = React.useState("");
    const [newPlanMode, setNewPlanMode] = React.useState("GENERAL_SKILL");
    const [rename, setRename] = React.useState((_a = plan === null || plan === void 0 ? void 0 : plan.name) !== null && _a !== void 0 ? _a : "");
    React.useEffect(() => { var _a; return setRename((_a = plan === null || plan === void 0 ? void 0 : plan.name) !== null && _a !== void 0 ? _a : ""); }, [plan === null || plan === void 0 ? void 0 : plan.id, plan === null || plan === void 0 ? void 0 : plan.name]);
    const [subject, setSubject] = React.useState("");
    const [topic, setTopic] = React.useState("");
    const [subjectId, setSubjectId] = React.useState((_c = (_b = state.subjects.find(s => s.planId === state.activePlanId)) === null || _b === void 0 ? void 0 : _b.id) !== null && _c !== void 0 ? _c : "");
    const [parentTopicId, setParentTopicId] = React.useState("");
    const subjects = state.subjects.filter(s => s.planId === state.activePlanId).slice().sort((a, b) => a.order - b.order);
    const materials = state.materials.filter(m => m.planId === state.activePlanId);
    const topicChoices = state.topics.filter(t => t.planId === state.activePlanId && t.subjectId === subjectId && t.status !== "archived").slice().sort((a, b) => a.order - b.order);
    React.useEffect(() => { var _a, _b; if (!subjects.some(s => s.id === subjectId)) {
        setSubjectId((_b = (_a = subjects[0]) === null || _a === void 0 ? void 0 : _a.id) !== null && _b !== void 0 ? _b : "");
        setParentTopicId("");
    } }, [state.activePlanId, subjects.length]);
    const [importText, setImportText] = React.useState("");
    const [importFormat, setImportFormat] = React.useState("txt");
    const [importMode, setImportMode] = React.useState("merge");
    const [importTarget, setImportTarget] = React.useState("active");
    const [importPlanName, setImportPlanName] = React.useState("");
    const [contentPreview, setContentPreview] = React.useState(null);
    React.useEffect(() => setContentPreview(null), [state.activePlanId, importText, importFormat, importMode, importTarget, importPlanName]);
    const previewImport = () => run(() => { var _a; const next = store.previewPlanContentImport(importText, { format: importFormat, mode: importMode, target: importTarget, planName: importTarget === "new-plan" ? importPlanName : undefined, planMode: (_a = plan === null || plan === void 0 ? void 0 : plan.mode) !== null && _a !== void 0 ? _a : newPlanMode }); setContentPreview(next); return next; }, "Plan Content import preview generated.");
    const loadImportFile = async (e) => { var _a; const file = (_a = e.target.files) === null || _a === void 0 ? void 0 : _a[0]; if (!file)
        return; try {
        const source = await file.text();
        setImportText(source);
        setImportFormat((0, content_import_1.detectPlanContentImportFormat)(file.name || file.type, source));
    }
    catch (error) {
        run(() => { throw error; });
    }
    finally {
        e.target.value = "";
    } };
    if (isTvBrowserUi())
        return React.createElement(TvPlan, { state: state, store: store, tab: tab, setTab: setTab, persistence: persistence, run: run, tvMaterialId: tvMaterialId !== null && tvMaterialId !== void 0 ? tvMaterialId : null, onTvMaterialOpen: onTvMaterialOpen, onTvMaterialClose: onTvMaterialClose, onTvScheduleOpen: onTvScheduleOpen });
    return React.createElement("section", { id: "plan", "data-screen": "plan", className: "screen-section" },
        React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "plan", label: "Plan sections", items: [["overview", "Overview"], ["schedule", "Schedule"], ["content", "Content"], ["library", "Library"]] }),
        tab === "overview" && React.createElement("section", { id: "plan-overview", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "metric-grid" },
                React.createElement(Metric, { label: "Subjects", value: String(inv.subjects), detail: `${inv.topics} active topics` }),
                React.createElement(Metric, { label: "Planned", value: `${inv.plannedMinutes} min`, detail: `${inv.activities} activities` })),
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Plan workspace"),
                    React.createElement("select", { "aria-label": "Active study plan", value: (_d = state.activePlanId) !== null && _d !== void 0 ? _d : "", onChange: (e) => run(() => store.switchPlan(e.target.value), "Active plan switched.") }, activePlans.map(item => React.createElement("option", { key: item.id, value: item.id }, item.name))),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (plan && rename.trim())
                            run(() => store.renamePlan(plan.id, rename.trim()), "Plan renamed."); } },
                        React.createElement("input", { "aria-label": "Rename active plan", value: rename, onChange: (e) => setRename(e.target.value), required: true }),
                        React.createElement("button", { className: "button", type: "submit" }, "Rename")),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button button-quiet", type: "button", disabled: !plan, onClick: () => plan && run(() => store.duplicatePlan(plan.id, { name: `${plan.name} copy`, activate: true }), "Plan structure duplicated and activated.") }, "Duplicate"),
                        React.createElement("button", { className: "button button-quiet", type: "button", disabled: !plan || activePlans.length < 2, onClick: () => plan && run(() => store.setPlanArchived(plan.id, true), "Plan archived; history retained.") }, "Archive")),
                    archivedPlans.length > 0 && React.createElement("div", null,
                        React.createElement("h3", null, "Archived plans"),
                        React.createElement("ul", { className: "activity-list" }, archivedPlans.map(item => React.createElement("li", { key: item.id },
                            React.createElement("strong", null, item.name),
                            React.createElement("div", { className: "button-row" },
                                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setPlanArchived(item.id, false), "Plan restored.") }, "Restore"),
                                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deletePlan(item.id), "Empty archived plan deleted.") }, "Delete if empty"))))))),
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Create plan"),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (newPlanName.trim()) {
                            run(() => store.createPlan({ name: newPlanName.trim(), mode: newPlanMode }, { activate: true }), "Plan created and activated.");
                            setNewPlanName("");
                        } } },
                        React.createElement("input", { "aria-label": "New plan name", value: newPlanName, onChange: (e) => setNewPlanName(e.target.value), required: true }),
                        React.createElement("select", { "aria-label": "New plan study mode", value: newPlanMode, onChange: (e) => setNewPlanMode(e.target.value) }, modes.map(([id, label]) => React.createElement("option", { key: id, value: id }, label))),
                        React.createElement("button", { className: "button button-primary", type: "submit" }, "Create plan")),
                    React.createElement("p", { className: "muted" }, "Plans have isolated content, schedules, evidence and study-cycle cursors."))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Study mode"),
                React.createElement("select", { "aria-label": "Study mode", value: (_e = plan === null || plan === void 0 ? void 0 : plan.mode) !== null && _e !== void 0 ? _e : "GENERAL_SKILL", onChange: (e) => run(() => store.setPlanMode(e.target.value), "Study mode updated.") }, modes.map(([id, label]) => React.createElement("option", { key: id, value: id }, label)))),
            React.createElement(ModeWorkspace, { state: state, store: store, run: run }),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Study cycle"),
                React.createElement("ol", null, state.studyCycle.subjectIds.map((id, i) => { var _a, _b; return React.createElement("li", { key: id }, (_b = (_a = state.subjects.find(s => s.id === id)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : id,
                    i === state.studyCycle.cursor ? " · current" : ""); })),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.advanceStudyCycle(), "Study cycle advanced.") }, "Advance"))),
        tab === "schedule" && React.createElement(ScheduleWorkspace, { state: state, store: store, preview: preview, setPreview: setPreview, run: run }),
        tab === "content" && React.createElement("section", { id: "plan-content", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Add subject"),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (subject.trim()) {
                            const added = run(() => store.addSubject({ name: subject.trim() }), "Subject added.");
                            if (added && !subjectId)
                                setSubjectId(added.id);
                            setSubject("");
                        } } },
                        React.createElement("input", { "aria-label": "Subject name", value: subject, onChange: (e) => setSubject(e.target.value), required: true }),
                        React.createElement("button", { className: "button", type: "submit" }, "Add"))),
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Add nested topic"),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (topic.trim() && subjectId) {
                            run(() => store.addTopic({ subjectId, parentTopicId: parentTopicId || null, name: topic.trim() }), "Topic added.");
                            setTopic("");
                        } } },
                        React.createElement("select", { "aria-label": "Topic subject", value: subjectId, onChange: (e) => { setSubjectId(e.target.value); setParentTopicId(""); } }, subjects.map(s => React.createElement("option", { key: s.id, value: s.id }, s.name))),
                        React.createElement("select", { "aria-label": "Parent topic", value: parentTopicId, onChange: (e) => setParentTopicId(e.target.value) },
                            React.createElement("option", { value: "" }, "Root topic"),
                            topicChoices.map(t => React.createElement("option", { key: t.id, value: t.id }, t.name))),
                        React.createElement("input", { "aria-label": "Topic name", value: topic, onChange: (e) => setTopic(e.target.value), required: true }),
                        React.createElement("button", { className: "button", type: "submit" }, "Add")))),
            React.createElement("article", { className: "card" },
                React.createElement("div", { className: "card-heading-row" },
                    React.createElement("div", null,
                        React.createElement("h2", null, "Import Plan Content"),
                        React.createElement("p", { className: "muted" }, "TXT, Markdown, CSV, TSV and JSON are parsed locally. Preview/diff is mandatory before mutation.")),
                    React.createElement("label", { className: "button button-quiet file-button" },
                        "Choose file",
                        React.createElement("input", { "aria-label": "Plan Content import file", type: "file", accept: ".txt,.md,.markdown,.csv,.tsv,.json,text/plain,text/markdown,text/csv,application/json", onChange: loadImportFile }))),
                React.createElement("div", { className: "content-grid two-column" },
                    React.createElement("div", { className: "stack-form" },
                        React.createElement("select", { "aria-label": "Plan Content import format", value: importFormat, onChange: (e) => setImportFormat(e.target.value) },
                            React.createElement("option", { value: "txt" }, "TXT"),
                            React.createElement("option", { value: "markdown" }, "Markdown"),
                            React.createElement("option", { value: "csv" }, "CSV"),
                            React.createElement("option", { value: "tsv" }, "TSV"),
                            React.createElement("option", { value: "json" }, "JSON")),
                        React.createElement("select", { "aria-label": "Plan Content import mode", value: importMode, onChange: (e) => setImportMode(e.target.value) },
                            React.createElement("option", { value: "merge" }, "Merge only"),
                            React.createElement("option", { value: "reconcile" }, "Reconcile + archive missing")),
                        React.createElement("select", { "aria-label": "Plan Content import target", value: importTarget, onChange: (e) => setImportTarget(e.target.value) },
                            React.createElement("option", { value: "active" }, "Current plan"),
                            React.createElement("option", { value: "new-plan" }, "Import as new plan")),
                        importTarget === "new-plan" && React.createElement("input", { "aria-label": "Imported new plan name", value: importPlanName, onChange: (e) => setImportPlanName(e.target.value), placeholder: "New plan name", required: true }),
                        React.createElement("button", { className: "button", type: "button", disabled: !importText.trim(), onClick: previewImport }, "Preview import")),
                    React.createElement("textarea", { "aria-label": "Plan Content import source", value: importText, onChange: (e) => setImportText(e.target.value), placeholder: 'Subject: Constitutional Law\nTopic: Fundamental rights > Individual rights' })),
                contentPreview && React.createElement("div", { className: "import-preview" },
                    React.createElement("h3", null, contentPreview.ready ? "Preview ready" : "Preview blocked"),
                    React.createElement("p", null,
                        contentPreview.summary.subjectsAdd,
                        " subjects \u00B7 ",
                        contentPreview.summary.topicsAdd,
                        " topics \u00B7 ",
                        contentPreview.summary.update,
                        " updates \u00B7 ",
                        contentPreview.summary.reactivate,
                        " reactivations \u00B7 ",
                        contentPreview.summary.archive,
                        " archives \u00B7 ",
                        contentPreview.summary.duplicate,
                        " duplicates"),
                    contentPreview.issues.length > 0 && React.createElement("ul", null, contentPreview.issues.map((issue, index) => React.createElement("li", { key: `${issue.code}-${index}` },
                        React.createElement("strong", null, title(issue.severity)),
                        " \u00B7 ",
                        issue.message))),
                    React.createElement("details", null,
                        React.createElement("summary", null,
                            "Diff operations \u00B7 ",
                            contentPreview.operations.length),
                        React.createElement("ul", null, contentPreview.operations.map((op, index) => { var _a; return React.createElement("li", { key: index },
                            title(op.action),
                            " \u00B7 ",
                            [op.subjectName, ...((_a = op.topicPath) !== null && _a !== void 0 ? _a : [])].join(" > ")); }))),
                    React.createElement("button", { className: "button button-primary", type: "button", disabled: !contentPreview.ready, onClick: () => { if (run(() => store.applyPlanContentImportPreview(contentPreview), contentPreview.target === "new-plan" ? "New plan imported and activated." : "Plan Content import applied.")) {
                            setContentPreview(null);
                            setImportText("");
                        } } }, "Apply import preview"))),
            subjects.map(s => { var _a; return React.createElement("article", { className: "card", key: s.id },
                React.createElement("div", { className: "card-heading-row" },
                    React.createElement("div", null,
                        React.createElement("h2", null, s.name),
                        React.createElement("p", { className: "muted" },
                            "Weight ", (_a = s.weight) !== null && _a !== void 0 ? _a : "No data",
                            " \u00B7 ",
                            s.archived ? "Archived" : "Active")),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateSubject(s.id, { archived: !s.archived }), s.archived ? "Subject restored." : "Subject archived.") }, s.archived ? "Restore" : "Archive"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateSubject(s.id, { notes: s.notes ? null : "Review this subject" }), "Subject notes updated.") }, "Notes"))),
                React.createElement(TopicTree, { state: state, store: store, subjectId: s.id, run: run })); })),
        tab === "library" && React.createElement(Library, { materials: materials, store: store, persistence: persistence, run: run }));
}
function minuteOfDay(value) { const [h, m] = value.split(":").map(Number); return Math.max(0, Math.min(1439, (Number.isFinite(h) ? h : 0) * 60 + (Number.isFinite(m) ? m : 0))); }
function minuteClock(value) { if (value == null)
    return "Any time"; return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`; }
function TvTopicTree({ state, subjectId, parentId = null }) { const rows = state.topics.filter(t => { var _a; return t.planId === state.activePlanId && t.subjectId === subjectId && t.status !== "archived" && ((_a = t.parentTopicId) !== null && _a !== void 0 ? _a : null) === parentId; }).slice().sort((a, b) => a.order - b.order); if (!rows.length)
    return null; return React.createElement("ul", { className: "tv-topic-tree" }, rows.map(topic => React.createElement("li", { key: topic.id },
    React.createElement("strong", null, topic.name),
    React.createElement(TvTopicTree, { state: state, subjectId: subjectId, parentId: topic.id })))); }
function TvPlan({ state, store, tab, setTab, persistence, run, tvMaterialId, onTvMaterialOpen, onTvMaterialClose, onTvScheduleOpen }) { var _a, _b; const inv = (0, domain_1.derivePlanInventory)(state); const plan = (_a = state.plans.find(p => p.id === state.activePlanId)) !== null && _a !== void 0 ? _a : null; const plans = state.plans.filter(p => p.status !== "archived"); const subjects = state.subjects.filter(s => s.planId === state.activePlanId).slice().sort((a, b) => a.order - b.order); const materials = state.materials.filter(m => m.planId === state.activePlanId); const upcoming = state.activities.filter(a => a.planId === state.activePlanId && a.status !== "completed" && Boolean(a.scheduledFor)).slice().sort((a, b) => { var _a, _b, _c, _d; return `${(_a = a.scheduledFor) !== null && _a !== void 0 ? _a : ""}:${String((_b = a.scheduledStartMinute) !== null && _b !== void 0 ? _b : 0).padStart(4, "0")}`.localeCompare(`${(_c = b.scheduledFor) !== null && _c !== void 0 ? _c : ""}:${String((_d = b.scheduledStartMinute) !== null && _d !== void 0 ? _d : 0).padStart(4, "0")}`); }).slice(0, 24); return React.createElement("section", { id: "plan", "data-screen": "plan", className: "screen-section tv-plan" },
    React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "plan", label: "Plan sections", items: [["overview", "Overview"], ["schedule", "Schedule"], ["content", "Content"], ["library", "Library"]] }),
    tab === "overview" && React.createElement("section", { id: "plan-overview", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("div", { className: "metric-grid" },
            React.createElement(Metric, { label: "Subjects", value: String(inv.subjects), detail: `${inv.topics} active topics` }),
            React.createElement(Metric, { label: "Planned", value: `${inv.plannedMinutes} min`, detail: `${inv.activities} activities` }),
            React.createElement(Metric, { label: "Mode", value: plan ? title(plan.mode) : "No plan", detail: "TV reference view" })),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Active plan"),
            plans.length ? React.createElement("select", { "aria-label": "Active study plan", value: (_b = state.activePlanId) !== null && _b !== void 0 ? _b : "", onChange: (e) => run(() => store.switchPlan(e.target.value), "Active plan switched.") }, plans.map(item => React.createElement("option", { key: item.id, value: item.id }, item.name))) : React.createElement("p", null, "No active plan."),
            React.createElement("p", { className: "muted" }, "Plan switching is remote-friendly. Renaming, duplication, archiving, mode editing and detailed configuration stay on phone/computer.")),
        React.createElement(TvContinueElsewhere, { title: "Plan editing", detail: "TV mode presents the plan as a readable study dashboard rather than duplicating desktop authoring forms." })),
    tab === "schedule" && React.createElement("section", { id: "plan-schedule", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("h2", null, "Upcoming schedule"),
                    React.createElement("p", { className: "muted" }, "Read-only TV view. Schedule generation, bulk selection and rescheduling remain on phone/computer.")),
                onTvScheduleOpen && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": "plan-open-schedule", onClick: onTvScheduleOpen }, "Open TV Schedule")),
            React.createElement("ul", { className: "activity-list tv-schedule-list" },
                upcoming.map(a => React.createElement("li", { key: a.id },
                    React.createElement("div", null,
                        React.createElement("strong", null, a.title),
                        React.createElement("span", null,
                            a.scheduledFor,
                            " \u00B7 ",
                            a.scheduledStartMinute == null ? "time not set" : `${String(Math.floor(a.scheduledStartMinute / 60)).padStart(2, "0")}:${String(a.scheduledStartMinute % 60).padStart(2, "0")}`,
                            " \u00B7 ",
                            a.durationMinutes,
                            " min")),
                    React.createElement("span", { className: "status-pill" }, title(a.activityType)))),
                !upcoming.length && React.createElement("li", null, "No upcoming scheduled activities."))),
        React.createElement(TvContinueElsewhere, { title: "Schedule editing", detail: "Adaptive previews, locked-item changes and bulk schedule operations are intentionally not exposed to a TV remote." })),
    tab === "content" && React.createElement("section", { id: "plan-content", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Subjects & topics"),
            React.createElement("p", { className: "muted" }, "Reference hierarchy only. Imports and content authoring stay off-TV."),
            React.createElement("div", { className: "tv-subject-grid" },
                subjects.map(subject => React.createElement("section", { className: "nested-panel", key: subject.id },
                    React.createElement("h3", null, subject.name),
                    React.createElement(TvTopicTree, { state: state, subjectId: subject.id }))),
                !subjects.length && React.createElement("p", null, "No subjects in this plan."))),
        React.createElement(TvContinueElsewhere, { title: "Content authoring & import", detail: "Adding subjects/topics, large text imports and reconciliation previews require a phone/computer workspace." })),
    tab === "library" && React.createElement(Library, { materials: materials, store: store, persistence: persistence, run: run, tvReadOnly: true, controlledViewingId: tvMaterialId, onViewingOpen: onTvMaterialOpen, onViewingClose: onTvMaterialClose })); }
function ScheduleWorkspace({ state, store, preview, setPreview, run }) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const config = plan === null || plan === void 0 ? void 0 : plan.configuration.schedule;
    const [capacity, setCapacity] = React.useState(String((_a = config === null || config === void 0 ? void 0 : config.dailyCapacityMinutes) !== null && _a !== void 0 ? _a : 120));
    const [weekday, setWeekday] = React.useState("1");
    const [start, setStart] = React.useState("18:00");
    const [endTime, setEndTime] = React.useState("20:00");
    const [fixedTitle, setFixedTitle] = React.useState("");
    const [fixedDate, setFixedDate] = React.useState("");
    const [fixedStart, setFixedStart] = React.useState("18:00");
    const [fixedDuration, setFixedDuration] = React.useState("60");
    const [calendarView, setCalendarView] = React.useState("this-week");
    const [subjectFilter, setSubjectFilter] = React.useState("");
    const [typeFilter, setTypeFilter] = React.useState("");
    const [statusFilter, setStatusFilter] = React.useState("");
    const [provenanceFilter, setProvenanceFilter] = React.useState("");
    const [calendarSearch, setCalendarSearch] = React.useState("");
    const [showPastCalendarActivities, setShowPastCalendarActivities] = React.useState(false);
    const [selected, setSelected] = React.useState([]);
    const [manualTitle, setManualTitle] = React.useState("");
    const [manualSubject, setManualSubject] = React.useState("");
    const [manualTopic, setManualTopic] = React.useState("");
    const [manualDate, setManualDate] = React.useState((0, parity_intelligence_1.localDateKey)(new Date()));
    const [manualTime, setManualTime] = React.useState("18:00");
    const [manualDuration, setManualDuration] = React.useState("30");
    const [manualType, setManualType] = React.useState("THEORY");
    const [recTitle, setRecTitle] = React.useState("");
    const [recSubject, setRecSubject] = React.useState("");
    const [recTopic, setRecTopic] = React.useState("");
    const [recWeekday, setRecWeekday] = React.useState("1");
    const [recTime, setRecTime] = React.useState("18:00");
    const [recDuration, setRecDuration] = React.useState("45");
    const [recType, setRecType] = React.useState("THEORY");
    React.useEffect(() => { var _a; setCapacity(String((_a = plan === null || plan === void 0 ? void 0 : plan.configuration.schedule.dailyCapacityMinutes) !== null && _a !== void 0 ? _a : 120)); setPreview(null); setSelected([]); }, [plan === null || plan === void 0 ? void 0 : plan.id]);
    React.useEffect(() => setSelected([]), [calendarView, subjectFilter, typeFilter, statusFilter, provenanceFilter, calendarSearch, showPastCalendarActivities]);
    const calendar = (0, parity_intelligence_1.deriveScheduleCalendarSnapshot)(state, { view: calendarView, subjectId: subjectFilter || null, activityType: typeFilter || null, status: statusFilter || null, provenance: provenanceFilter || null, query: calendarSearch, includePast: showPastCalendarActivities });
    const subjects = state.subjects.filter(s => s.planId === state.activePlanId && !s.archived).slice().sort((a, b) => a.order - b.order);
    const manualTopics = state.topics.filter(t => t.planId === state.activePlanId && t.status !== "archived" && (!manualSubject || t.subjectId === manualSubject));
    const recurringTopics = state.topics.filter(t => t.planId === state.activePlanId && t.status !== "archived" && (!recSubject || t.subjectId === recSubject));
    const allSelected = calendar.activities.length > 0 && calendar.activities.every(a => selected.includes(a.id));
    const saveCapacity = () => run(() => store.setScheduleConfiguration({ dailyCapacityMinutes: Number(capacity) }), "Daily schedule capacity saved.");
    const addWindow = () => run(() => { var _a; return store.setScheduleConfiguration({ availability: [...((_a = config === null || config === void 0 ? void 0 : config.availability) !== null && _a !== void 0 ? _a : []), { id: `window-${Date.now()}`, weekday: Number(weekday), startMinute: minuteOfDay(start), endMinute: minuteOfDay(endTime) }] }); }, "Availability window added.");
    const addFixed = () => { if (!fixedTitle.trim() || !fixedDate)
        return; const result = run(() => { var _a; return store.setScheduleConfiguration({ fixedBlocks: [...((_a = config === null || config === void 0 ? void 0 : config.fixedBlocks) !== null && _a !== void 0 ? _a : []), { id: `fixed-${Date.now()}`, title: fixedTitle.trim(), date: fixedDate, weekday: null, startMinute: minuteOfDay(fixedStart), durationMinutes: Number(fixedDuration) }] }); }, "Fixed block added."); if (result)
        setFixedTitle(""); };
    const addManual = () => { if (!manualTitle.trim() || !manualDate)
        return; const result = run(() => store.addScheduledActivity({ title: manualTitle.trim(), activityType: manualType, durationMinutes: Number(manualDuration), subjectId: manualSubject || null, topicId: manualTopic || null, scheduledFor: manualDate, scheduledStartMinute: minuteOfDay(manualTime) }), "Manual scheduled activity created with local date/time."); if (result)
        setManualTitle(""); };
    const addRecurring = () => { if (!recTitle.trim())
        return; const result = run(() => { var _a; return store.setScheduleConfiguration({ recurringBlocks: [...((_a = config === null || config === void 0 ? void 0 : config.recurringBlocks) !== null && _a !== void 0 ? _a : []), { id: `recurring-${Date.now()}`, title: recTitle.trim(), subjectId: recSubject || null, topicId: recTopic || null, weekday: Number(recWeekday), startMinute: minuteOfDay(recTime), durationMinutes: Number(recDuration), activityType: recType, enabled: true }] }); }, "Weekly recurring block saved with subject/topic ownership."); if (result)
        setRecTitle(""); };
    const bulkLock = (locked) => { if (!selected.length)
        return; run(() => { for (const id of selected)
        store.setActivityLocked(id, locked); return true; }, locked ? "Schedule placement locked." : "Schedule placement unlocked."); };
    return React.createElement("section", { id: "plan-schedule", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Study strategy"),
            React.createElement("p", { className: "muted" }, "All seven canonical strategies are persisted. Study Cycle actions appear on Today only when STUDY_CYCLE is selected."),
            React.createElement("select", { "aria-label": "Study strategy type", value: (_b = config === null || config === void 0 ? void 0 : config.strategyType) !== null && _b !== void 0 ? _b : "FIXED_WEEKLY", onChange: (e) => run(() => store.setScheduleConfiguration({ strategyType: e.target.value }), "Study strategy updated.") }, promise_cleanup_1.STUDY_STRATEGY_TYPES.map(strategy => React.createElement("option", { key: strategy, value: strategy }, title(strategy))))),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Capacity & availability"),
                React.createElement("div", { className: "stack-form" },
                    React.createElement("input", { "aria-label": "Daily study capacity minutes", type: "number", min: "30", max: "1440", value: capacity, onChange: (e) => setCapacity(e.target.value) }),
                    React.createElement("button", { className: "button", type: "button", onClick: saveCapacity }, "Save daily capacity"),
                    React.createElement("select", { "aria-label": "Availability weekday", value: weekday, onChange: (e) => setWeekday(e.target.value) },
                        React.createElement("option", { value: "0" }, "Sunday"),
                        React.createElement("option", { value: "1" }, "Monday"),
                        React.createElement("option", { value: "2" }, "Tuesday"),
                        React.createElement("option", { value: "3" }, "Wednesday"),
                        React.createElement("option", { value: "4" }, "Thursday"),
                        React.createElement("option", { value: "5" }, "Friday"),
                        React.createElement("option", { value: "6" }, "Saturday")),
                    React.createElement("input", { "aria-label": "Availability start time", type: "time", value: start, onChange: (e) => setStart(e.target.value) }),
                    React.createElement("input", { "aria-label": "Availability end time", type: "time", value: endTime, onChange: (e) => setEndTime(e.target.value) }),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: addWindow }, "Add availability window")),
                React.createElement("ul", { className: "activity-list" },
                    ((_c = config === null || config === void 0 ? void 0 : config.availability) !== null && _c !== void 0 ? _c : []).map(w => React.createElement("li", { key: w.id },
                        React.createElement("strong", null, ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][w.weekday]),
                        React.createElement("span", null,
                            minuteClock(w.startMinute),
                            "\u2013",
                            minuteClock(w.endMinute)),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { var _a; return store.setScheduleConfiguration({ availability: ((_a = config === null || config === void 0 ? void 0 : config.availability) !== null && _a !== void 0 ? _a : []).filter(x => x.id !== w.id) }); }, "Availability removed.") }, "Remove"))),
                    !((_d = config === null || config === void 0 ? void 0 : config.availability) !== null && _d !== void 0 ? _d : []).length && React.createElement("li", null, "Capacity-only scheduling; no clock windows configured."))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Fixed commitments"),
                React.createElement("div", { className: "stack-form" },
                    React.createElement("input", { "aria-label": "Fixed block title", value: fixedTitle, onChange: (e) => setFixedTitle(e.target.value), placeholder: "Work / class / appointment" }),
                    React.createElement("input", { "aria-label": "Fixed block date", type: "date", value: fixedDate, onChange: (e) => setFixedDate(e.target.value) }),
                    React.createElement("input", { "aria-label": "Fixed block start time", type: "time", value: fixedStart, onChange: (e) => setFixedStart(e.target.value) }),
                    React.createElement("input", { "aria-label": "Fixed block duration minutes", type: "number", min: "5", max: "1440", value: fixedDuration, onChange: (e) => setFixedDuration(e.target.value) }),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: addFixed }, "Add fixed block")),
                React.createElement("ul", { className: "activity-list" },
                    ((_e = config === null || config === void 0 ? void 0 : config.fixedBlocks) !== null && _e !== void 0 ? _e : []).map(b => { var _a; return React.createElement("li", { key: b.id },
                        React.createElement("strong", null, b.title),
                        React.createElement("span", null, (_a = b.date) !== null && _a !== void 0 ? _a : `weekday ${b.weekday}`,
                            " \u00B7 ",
                            minuteClock(b.startMinute),
                            " \u00B7 ",
                            b.durationMinutes,
                            " min"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { var _a; return store.setScheduleConfiguration({ fixedBlocks: ((_a = config === null || config === void 0 ? void 0 : config.fixedBlocks) !== null && _a !== void 0 ? _a : []).filter(x => x.id !== b.id) }); }, "Fixed block removed.") }, "Remove")); }),
                    !((_f = config === null || config === void 0 ? void 0 : config.fixedBlocks) !== null && _f !== void 0 ? _f : []).length && React.createElement("li", null, "No fixed commitments configured.")))),
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Manual scheduled activity"),
                React.createElement("p", { className: "muted" }, "Subject, topic, local date and local clock time are persisted explicitly."),
                React.createElement("div", { className: "stack-form" },
                    React.createElement("input", { "aria-label": "Manual scheduled activity title", value: manualTitle, onChange: (e) => setManualTitle(e.target.value), placeholder: "Activity title" }),
                    React.createElement("select", { "aria-label": "Manual scheduled activity subject", value: manualSubject, onChange: (e) => { setManualSubject(e.target.value); setManualTopic(""); } },
                        React.createElement("option", { value: "" }, "No subject"),
                        subjects.map(x => React.createElement("option", { key: x.id, value: x.id }, x.name))),
                    React.createElement("select", { "aria-label": "Manual scheduled activity topic", value: manualTopic, onChange: (e) => setManualTopic(e.target.value) },
                        React.createElement("option", { value: "" }, "No topic"),
                        manualTopics.map(x => React.createElement("option", { key: x.id, value: x.id }, x.name))),
                    React.createElement("select", { "aria-label": "Manual scheduled activity type", value: manualType, onChange: (e) => setManualType(e.target.value) }, ["THEORY", "VIDEO", "READING", "CUSTOM", "QUESTIONS", "FLASHCARDS", "REVIEW", "MOCK_EXAM", "WRITING"].map(x => React.createElement("option", { key: x, value: x }, title(x)))),
                    React.createElement("input", { "aria-label": "Manual scheduled activity date", type: "date", value: manualDate, onChange: (e) => setManualDate(e.target.value) }),
                    React.createElement("input", { "aria-label": "Manual scheduled activity time", type: "time", value: manualTime, onChange: (e) => setManualTime(e.target.value) }),
                    React.createElement("input", { "aria-label": "Manual scheduled activity duration", type: "number", min: "5", max: "240", value: manualDuration, onChange: (e) => setManualDuration(e.target.value) }),
                    React.createElement("button", { className: "button", type: "button", onClick: addManual }, "Create scheduled activity"))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Weekly recurring blocks"),
                React.createElement("p", { className: "muted" }, "Recurring rules retain the selected subject/topic and exact local weekly start. Preview materializes them without silently moving them."),
                React.createElement("div", { className: "stack-form" },
                    React.createElement("input", { "aria-label": "Recurring block title", value: recTitle, onChange: (e) => setRecTitle(e.target.value), placeholder: "Recurring block title" }),
                    React.createElement("select", { "aria-label": "Recurring block subject", value: recSubject, onChange: (e) => { setRecSubject(e.target.value); setRecTopic(""); } },
                        React.createElement("option", { value: "" }, "No subject"),
                        subjects.map(x => React.createElement("option", { key: x.id, value: x.id }, x.name))),
                    React.createElement("select", { "aria-label": "Recurring block topic", value: recTopic, onChange: (e) => setRecTopic(e.target.value) },
                        React.createElement("option", { value: "" }, "No topic"),
                        recurringTopics.map(x => React.createElement("option", { key: x.id, value: x.id }, x.name))),
                    React.createElement("select", { "aria-label": "Recurring block weekday", value: recWeekday, onChange: (e) => setRecWeekday(e.target.value) }, ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].map((d, i) => React.createElement("option", { key: i, value: i }, d))),
                    React.createElement("input", { "aria-label": "Recurring block time", type: "time", value: recTime, onChange: (e) => setRecTime(e.target.value) }),
                    React.createElement("input", { "aria-label": "Recurring block duration", type: "number", min: "5", max: "240", value: recDuration, onChange: (e) => setRecDuration(e.target.value) }),
                    React.createElement("select", { "aria-label": "Recurring block activity type", value: recType, onChange: (e) => setRecType(e.target.value) }, ["THEORY", "VIDEO", "READING", "CUSTOM", "QUESTIONS", "FLASHCARDS", "REVIEW", "MOCK_EXAM", "WRITING"].map(x => React.createElement("option", { key: x, value: x }, title(x)))),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: addRecurring }, "Add weekly block")),
                React.createElement("ul", { className: "activity-list" },
                    ((_g = config === null || config === void 0 ? void 0 : config.recurringBlocks) !== null && _g !== void 0 ? _g : []).map(block => { var _a, _b; return React.createElement("li", { key: block.id },
                        React.createElement("strong", null, block.title),
                        React.createElement("span", null,
                            ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][block.weekday],
                            " \u00B7 ",
                            minuteClock(block.startMinute),
                            " \u00B7 ",
                            block.durationMinutes,
                            " min \u00B7 ", (_b = (_a = subjects.find(s => s.id === block.subjectId)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : "No subject",
                            " \u00B7 ",
                            title(block.activityType)),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { var _a; return store.setScheduleConfiguration({ recurringBlocks: ((_a = config === null || config === void 0 ? void 0 : config.recurringBlocks) !== null && _a !== void 0 ? _a : []).filter(x => x.id !== block.id) }); }, "Recurring block removed.") }, "Remove")); }),
                    !((_h = config === null || config === void 0 ? void 0 : config.recurringBlocks) !== null && _h !== void 0 ? _h : []).length && React.createElement("li", null, "No weekly recurring blocks configured.")))),
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("h2", null, "Readable calendar schedule"),
                    React.createElement("p", { className: "muted" }, "Today and week windows use the browser/device local date. Weeks are Monday\u2013Sunday; past Calendar entries stay collapsed until requested."))),
            React.createElement("div", { className: "button-row", role: "group", "aria-label": "Schedule calendar view" }, [['today', 'Today'], ['this-week', 'This week'], ['next-week', 'Next week'], ['calendar', 'Calendar']].map(([id, label]) => React.createElement("button", { key: id, type: "button", className: calendarView === id ? "button" : "button button-quiet", "aria-pressed": calendarView === id, onClick: () => setCalendarView(id) }, label))),
            React.createElement("div", { className: "button-row" },
                React.createElement("input", { "aria-label": "Schedule search", value: calendarSearch, onChange: (e) => setCalendarSearch(e.target.value), placeholder: "Search title, subject or topic" }),
                React.createElement("select", { "aria-label": "Schedule subject filter", value: subjectFilter, onChange: (e) => setSubjectFilter(e.target.value) },
                    React.createElement("option", { value: "" }, "All subjects"),
                    subjects.map(x => React.createElement("option", { key: x.id, value: x.id }, x.name))),
                React.createElement("select", { "aria-label": "Schedule activity type filter", value: typeFilter, onChange: (e) => setTypeFilter(e.target.value) },
                    React.createElement("option", { value: "" }, "All activity types"),
                    ["THEORY", "VIDEO", "READING", "CUSTOM", "QUESTIONS", "FLASHCARDS", "REVIEW", "MOCK_EXAM", "WRITING"].map(x => React.createElement("option", { key: x, value: x }, title(x)))),
                React.createElement("select", { "aria-label": "Schedule status filter", value: statusFilter, onChange: (e) => setStatusFilter(e.target.value) },
                    React.createElement("option", { value: "" }, "All statuses"),
                    React.createElement("option", { value: "planned" }, "Planned"),
                    React.createElement("option", { value: "completed" }, "Completed"),
                    React.createElement("option", { value: "cancelled" }, "Cancelled")),
                React.createElement("select", { "aria-label": "Schedule provenance filter", value: provenanceFilter, onChange: (e) => setProvenanceFilter(e.target.value) },
                    React.createElement("option", { value: "" }, "All provenance"),
                    React.createElement("option", { value: "manual" }, "Manual"),
                    React.createElement("option", { value: "adaptive" }, "Adaptive"))),
            calendarView === "calendar" && React.createElement("label", { className: "check-row" },
                React.createElement("input", { "aria-label": "Show past calendar activities", type: "checkbox", checked: showPastCalendarActivities, onChange: (e) => setShowPastCalendarActivities(e.target.checked) }),
                "Show past activities",
                !showPastCalendarActivities && calendar.hiddenPastCount > 0 ? ` (${calendar.hiddenPastCount} hidden)` : ""),
            React.createElement("p", null,
                React.createElement("strong", null,
                    calendar.visibleCount,
                    " visible activities"),
                " \u00B7 ",
                React.createElement("strong", null,
                    calendar.plannedMinutes,
                    " planned minutes"),
                calendar.window.bounded ? ` · ${calendar.window.startsOn} → ${calendar.window.endsOnInclusive}` : " · full schedule"),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", disabled: !calendar.activities.length, onClick: () => setSelected(allSelected ? [] : calendar.activities.map(a => a.id)) }, allSelected ? "Clear visible selection" : "Select visible"),
                React.createElement("button", { className: "button button-quiet", type: "button", disabled: !selected.length, onClick: () => bulkLock(true) }, "Lock selected"),
                React.createElement("button", { className: "button button-quiet", type: "button", disabled: !selected.length, onClick: () => bulkLock(false) }, "Unlock selected")),
            React.createElement("ul", { className: "activity-list" },
                calendar.activities.map(a => { var _a, _b, _c; return React.createElement("li", { key: a.id },
                    React.createElement("label", null,
                        React.createElement("input", { "aria-label": `Select schedule activity ${a.title}`, type: "checkbox", checked: selected.includes(a.id), onChange: (e) => setSelected(e.target.checked ? [...new Set([...selected, a.id])] : selected.filter(id => id !== a.id)) }),
                        React.createElement("strong", null, a.title)),
                    React.createElement("span", { className: "muted" }, (_a = a.scheduledFor) !== null && _a !== void 0 ? _a : "Unscheduled",
                        " \u00B7 ",
                        minuteClock(a.scheduledStartMinute),
                        " \u00B7 ",
                        a.durationMinutes,
                        " min \u00B7 ",
                        title(a.activityType),
                        " \u00B7 ",
                        a.status,
                        " \u00B7 ", (_c = (_b = a.scheduleProvenance) === null || _b === void 0 ? void 0 : _b.source) !== null && _c !== void 0 ? _c : "manual",
                        a.locked ? " · locked" : "")); }),
                !calendar.activities.length && React.createElement("li", null, "No activities in the visible window."))),
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("h2", null, "Adaptive schedule preview"),
                    React.createElement("p", { className: "muted" }, "Conflicts, overload and work that cannot fit stay visible; locked activities are never moved.")),
                React.createElement("button", { className: "button", type: "button", onClick: () => setPreview(store.previewSchedule({ days: 7, maxBlocks: 14 })) }, "Preview 7 days")),
            preview ? React.createElement(React.Fragment, null,
                React.createElement("p", null,
                    React.createElement("strong", null, preview.summary.add),
                    " add \u00B7 ",
                    React.createElement("strong", null, preview.summary.move),
                    " move \u00B7 ",
                    React.createElement("strong", null, preview.summary.conflicts),
                    " conflicts \u00B7 ",
                    React.createElement("strong", null, preview.summary.unscheduled),
                    " unscheduled"),
                preview.conflicts.length > 0 && React.createElement("details", { open: true },
                    React.createElement("summary", null, "Conflicts"),
                    React.createElement("ul", null, preview.conflicts.map((c, i) => React.createElement("li", { key: i },
                        c.date,
                        " \u00B7 ",
                        c.message)))),
                React.createElement("ul", null, preview.operations.map((op, i) => { var _a, _b; return React.createElement("li", { key: i },
                    op.action === "move" ? `Move ${op.activityId} → ${op.to} ${minuteClock(op.toStartMinute)}` : `Add ${(_b = (_a = op.activity) === null || _a === void 0 ? void 0 : _a.title) !== null && _b !== void 0 ? _b : "activity"} · ${op.scheduledFor} ${minuteClock(op.scheduledStartMinute)}`,
                    " \u00B7 ",
                    op.reason); })),
                preview.unscheduled.length > 0 && React.createElement("details", null,
                    React.createElement("summary", null, "Unscheduled work"),
                    React.createElement("ul", null, preview.unscheduled.map((x, i) => React.createElement("li", { key: i },
                        x.title,
                        " \u00B7 ",
                        x.durationMinutes,
                        " min \u00B7 ",
                        x.reason)))),
                React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applySchedulePreview(preview), "Schedule preview applied."))
                        setPreview(null); } }, "Apply preview")) : React.createElement("p", { className: "muted" }, "Preview first; no schedule changes are silent.")));
}
function cockpitIssueText(issue, locale) {
    var _a;
    const pt = { "no-confirmed-exam-date": "Sem data oficial confirmada; nenhuma contagem regressiva oficial é exibida.", "no-reference-notice": "Nenhum edital de referência foi registrado.", "reference-publication-date-unknown": "A data de publicação do edital de referência é desconhecida.", "stale-reference-notice": "O edital de referência ultrapassou a janela de verificação configurada." };
    const en = { "no-confirmed-exam-date": "No confirmed official exam date; no official countdown is shown.", "no-reference-notice": "No reference notice has been recorded.", "reference-publication-date-unknown": "The reference notice publication date is unknown.", "stale-reference-notice": "The reference notice is older than the configured verification window." };
    return (_a = (locale === "pt-BR" ? pt : en)[issue]) !== null && _a !== void 0 ? _a : issue;
}
function StudyPhaseEditor({ phase, phases, store, run }) {
    var _a, _b;
    const [draft, setDraft] = React.useState({ ...phase });
    React.useEffect(() => setDraft({ ...phase }), [phase]);
    const number = (value) => value === "" ? null : Number(value);
    const save = (e) => { e.preventDefault(); const next = { ...draft, theoryRatioPercent: Number(draft.theoryRatioPercent), questionRatioPercent: Number(draft.questionRatioPercent), reviewRatioPercent: Number(draft.reviewRatioPercent), questionGoal: number(draft.questionGoal), mockExamGoal: number(draft.mockExamGoal) }; run(() => store.setStudyPhaseDefinitions(phases.map(x => x.id === phase.id ? next : x)), "Study phase saved."); };
    return React.createElement("form", { className: "stack-form", onSubmit: save },
        React.createElement("div", { className: "button-row" },
            React.createElement("input", { "aria-label": `Study phase label ${phase.id}`, value: draft.label, onChange: (e) => setDraft({ ...draft, label: e.target.value }) }),
            React.createElement("select", { "aria-label": `Study phase kind ${phase.id}`, value: draft.kind, onChange: (e) => setDraft({ ...draft, kind: e.target.value }) },
                React.createElement("option", { value: "foundation" }, "Foundation"),
                React.createElement("option", { value: "expansion" }, "Expansion"),
                React.createElement("option", { value: "consolidation" }, "Consolidation"),
                React.createElement("option", { value: "questions-reviews" }, "Questions & reviews"),
                React.createElement("option", { value: "custom" }, "Custom"))),
        React.createElement("div", { className: "button-row" },
            React.createElement("label", null,
                "Starts",
                React.createElement("input", { "aria-label": `Study phase start ${phase.id}`, type: "date", value: draft.startsOn, onChange: (e) => setDraft({ ...draft, startsOn: e.target.value }) })),
            React.createElement("label", null,
                "Ends",
                React.createElement("input", { "aria-label": `Study phase end ${phase.id}`, type: "date", value: draft.endsOn, onChange: (e) => setDraft({ ...draft, endsOn: e.target.value }) }))),
        React.createElement("div", { className: "button-row" },
            React.createElement("label", null,
                "Theory %",
                React.createElement("input", { "aria-label": `Theory allocation ${phase.id}`, type: "number", min: "0", max: "100", value: draft.theoryRatioPercent, onChange: (e) => setDraft({ ...draft, theoryRatioPercent: e.target.value }) })),
            React.createElement("label", null,
                "Questions %",
                React.createElement("input", { "aria-label": `Question allocation ${phase.id}`, type: "number", min: "0", max: "100", value: draft.questionRatioPercent, onChange: (e) => setDraft({ ...draft, questionRatioPercent: e.target.value }) })),
            React.createElement("label", null,
                "Review %",
                React.createElement("input", { "aria-label": `Review allocation ${phase.id}`, type: "number", min: "0", max: "100", value: draft.reviewRatioPercent, onChange: (e) => setDraft({ ...draft, reviewRatioPercent: e.target.value }) }))),
        React.createElement("div", { className: "button-row" },
            React.createElement("label", null,
                "Question target",
                React.createElement("input", { "aria-label": `Question target ${phase.id}`, type: "number", min: "0", value: (_a = draft.questionGoal) !== null && _a !== void 0 ? _a : "", onChange: (e) => setDraft({ ...draft, questionGoal: e.target.value }) })),
            React.createElement("label", null,
                "Mock target",
                React.createElement("input", { "aria-label": `Mock target ${phase.id}`, type: "number", min: "0", value: (_b = draft.mockExamGoal) !== null && _b !== void 0 ? _b : "", onChange: (e) => setDraft({ ...draft, mockExamGoal: e.target.value }) }))),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button", type: "submit" }, "Save phase"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setStudyPhaseDefinitions(phases.filter(x => x.id !== phase.id)), "Study phase removed.") }, "Remove")),
        draft.allocationIsStudyFlowSuggested && React.createElement("span", { className: "muted" }, "Allocation is a StudyFlow planning suggestion, not an official edital value."));
}
function MaterialReferenceControls({ material, store, run }) {
    const freshness = (0, modes_1.deriveMaterialReferenceFreshness)(material);
    const [replacement, setReplacement] = React.useState("");
    const tracked = freshness.provenance.origin != null;
    return React.createElement("div", { className: "stack-form" },
        React.createElement("span", { className: "muted" },
            "Reference: ",
            freshness.status.replace(/-/g, " "),
            freshness.ageDays == null ? "" : ` · ${freshness.ageDays} days since verification`,
            freshness.provenance.supersedesMaterialId ? ` · supersedes ${freshness.provenance.supersedesMaterialId}` : "",
            material.archived ? " · archived history" : ""),
        !tracked && !material.archived && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setMaterialReferenceProvenance(material.id, { origin: "EDITAL_2022", verifiedOn: null }), "Material tracked as edital-2022 reference; verification is still required.") }, "Track as edital 2022 reference"),
        tracked && !material.archived && React.createElement(React.Fragment, null,
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.verifyMaterialReference(material.id), "Reference verification date recorded.") }, "Verify reference now")),
            React.createElement("input", { "aria-label": `Replacement source for ${material.title}`, value: replacement, onChange: (e) => setReplacement(e.target.value), placeholder: "Replacement URL / locator" }),
            React.createElement("button", { className: "button button-quiet", type: "button", disabled: !replacement.trim(), onClick: () => { const next = run(() => store.replaceMaterialReference(material.id, { locator: replacement.trim() }), "Reference replaced; previous material archived for provenance."); if (next)
                    setReplacement(""); } }, "Replace reference")));
}
function PublicExamWorkspace({ state, store, run }) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const c = plan.configuration.publicExam;
    const [org, setOrg] = React.useState((_a = c.target.organization) !== null && _a !== void 0 ? _a : "");
    const [role, setRole] = React.useState((_b = c.target.role) !== null && _b !== void 0 ? _b : "");
    const [board, setBoard] = React.useState((_c = c.target.board) !== null && _c !== void 0 ? _c : "");
    const [editalRef, setEditalRef] = React.useState((_d = c.target.editalRef) !== null && _d !== void 0 ? _d : "");
    const [examDate, setExamDate] = React.useState((_e = c.target.examDate) !== null && _e !== void 0 ? _e : "");
    const [dateStatus, setDateStatus] = React.useState(c.target.dateStatus);
    const [stage, setStage] = React.useState("");
    const [notice, setNotice] = React.useState("");
    const [noticeRef, setNoticeRef] = React.useState((_f = c.target.editalRef) !== null && _f !== void 0 ? _f : "");
    const [noticePublished, setNoticePublished] = React.useState("");
    const [noticePreview, setNoticePreview] = React.useState(null);
    const [law, setLaw] = React.useState("");
    const [reviewDays, setReviewDays] = React.useState(c.reviewIntervalsDays.join(", "));
    const dashboard = (0, modes_1.derivePublicExamDashboard)(state);
    const guidance = (0, modes_1.deriveFinalStretchGuidance)(plan);
    const cockpit = (0, modes_1.derivePreEditalCockpit)(plan);
    const activePhase = (0, modes_1.deriveActiveStudyPhase)(plan);
    const matrix = (0, modes_1.deriveEditalMatrix)(state);
    const priorities = (0, modes_1.derivePublicExamNextActions)(state);
    return React.createElement("div", { className: "content-grid two-column" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Concurso target"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); run(() => store.updatePublicExamTarget({ organization: org || null, role: role || null, board: board || null, editalRef: editalRef || null, examDate: examDate || null, dateStatus }), "Concurso target saved."); } },
                React.createElement("input", { "aria-label": "Organization", value: org, onChange: (e) => setOrg(e.target.value), placeholder: "Organization" }),
                React.createElement("input", { "aria-label": "Role or cargo", value: role, onChange: (e) => setRole(e.target.value), placeholder: "Role / cargo" }),
                React.createElement("input", { "aria-label": "Exam board or banca", value: board, onChange: (e) => setBoard(e.target.value), placeholder: "Exam board / banca" }),
                React.createElement("input", { "aria-label": "Reference edital", value: editalRef, onChange: (e) => setEditalRef(e.target.value), placeholder: "Reference notice / edital" }),
                React.createElement("input", { "aria-label": "Exam date", type: "date", value: examDate, onChange: (e) => setExamDate(e.target.value) }),
                React.createElement("select", { "aria-label": "Exam date status", value: dateStatus, onChange: (e) => setDateStatus(e.target.value) },
                    React.createElement("option", { value: "unknown" }, "Date unknown"),
                    React.createElement("option", { value: "expected" }, "Expected date"),
                    React.createElement("option", { value: "confirmed" }, "Confirmed date")),
                React.createElement("button", { className: "button", type: "submit" }, "Save target")),
            React.createElement("p", { className: "muted" }, "Identity is accepted only when organization, role and board are all filled or all blank."),
            React.createElement("label", null,
                "Study phase",
                React.createElement("select", { "aria-label": "Public exam study phase", value: c.studyPhase, onChange: (e) => run(() => store.setPublicExamStudyPhase(e.target.value), "Study phase updated.") },
                    React.createElement("option", { value: "pre-edital" }, "Pre-edital"),
                    React.createElement("option", { value: "post-edital" }, "Post-edital"),
                    React.createElement("option", { value: "final-stretch" }, "Final stretch"))),
            React.createElement("div", null,
                React.createElement("strong", null, "Local starter templates"),
                React.createElement("div", { className: "button-row" },
                    Object.values(modes_1.PUBLIC_EXAM_STARTER_TEMPLATES).map(t => React.createElement("button", { key: t.id, className: "button button-quiet", type: "button", onClick: () => run(() => store.applyPublicExamStarterTemplate(t.id), `${t.label} starter applied.`) }, t.label)),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.provisionBbAgenteComercialPreset({ activate: true }), "New BB Agente Comercial reference plan created and activated.") }, "Create BB Agente Comercial preset plan")),
                React.createElement("span", { className: "muted" }, "Templates never invent organization, banca, edital or exam date. The BB preset uses Sele\u00E7\u00E3o Externa 2022/001 only as an explicit historical reference."))),
        React.createElement("article", { className: "card", id: "pre-edital-cockpit" },
            React.createElement("h2", null, "Truthful pre-edital cockpit"),
            !cockpit ? React.createElement("p", { className: "muted" }, "Available for Concurso plans.") : !cockpit.preEdital ? React.createElement("p", null,
                React.createElement("strong", null, "Post-edital context:"),
                " confirmed exam date recorded. The pre-edital verification issues are cleared.") : React.createElement(React.Fragment, null,
                React.createElement("p", null,
                    React.createElement("strong", null, "No official countdown."),
                    " ",
                    cockpit.resolvedDate.source === "expected-target" ? "The saved exam date is an expectation, not an official date." : "A confirmed exam date has not been recorded."),
                React.createElement("p", { className: "muted" },
                    "Reference: ", (_g = cockpit.referenceLabel) !== null && _g !== void 0 ? _g : "none",
                    cockpit.referenceAgeDays == null ? "" : ` · ${cockpit.referenceAgeDays} days old`,
                    " \u00B7 stale warning after ",
                    cockpit.staleReferenceDays,
                    " days"),
                React.createElement("ul", { className: "activity-list" }, cockpit.issues.map(issue => React.createElement("li", { key: issue },
                    React.createElement("strong", null, cockpitIssueText(issue, state.preferences.locale))))),
                React.createElement("a", { className: "button button-quiet", href: "#edital-revision" }, "Review assumptions in edital workflow"))),
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("h2", null, "Study phase planner"),
                    React.createElement("p", { className: "muted" }, "Editable dated phases cannot overlap. Theory + questions + review must total 100%.")),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.applyBbStudyPhaseSuggestions(), "BB Sep–Dec 2026 phase suggestions applied.") }, "Use BB Sep\u2013Dec 2026 suggestions")),
            React.createElement("p", null,
                React.createElement("strong", null, "Active dated phase:"),
                " ",
                activePhase ? `${activePhase.label} · ${activePhase.theoryRatioPercent}% theory / ${activePhase.questionRatioPercent}% questions / ${activePhase.reviewRatioPercent}% review` : "No dated phase active today"),
            c.phaseDefinitions.map(phase => React.createElement("details", { key: phase.id, open: true },
                React.createElement("summary", null,
                    phase.label,
                    " \u00B7 ",
                    phase.startsOn,
                    " \u2192 ",
                    phase.endsOn),
                React.createElement(StudyPhaseEditor, { phase: phase, phases: c.phaseDefinitions, store: store, run: run }))),
            !c.phaseDefinitions.length && React.createElement("p", { className: "muted" }, "No dated phase definitions yet."),
            React.createElement("button", { className: "button button-quiet", type: "button", disabled: c.phaseDefinitions.length >= 5, onClick: () => { const kinds = ["foundation", "expansion", "consolidation", "questions-reviews", "custom"]; const kind = kinds.find(k => !c.phaseDefinitions.some(p => p.kind === k)); if (!kind)
                    return; const day = new Date().toISOString().slice(0, 10); run(() => store.setStudyPhaseDefinitions([...c.phaseDefinitions, { id: `phase-${Date.now().toString(36)}`, kind, label: title(kind), startsOn: day, endsOn: day, theoryRatioPercent: 60, questionRatioPercent: 25, reviewRatioPercent: 15, questionGoal: null, mockExamGoal: null, allocationIsStudyFlowSuggested: false }]), "Study phase added."); } }, "Add phase"),
            React.createElement("p", { className: "muted" }, "BB allocation ratios are StudyFlow planning suggestions, not official edital values. The scheduler uses the active dated allocation for generated-week study/question/review blocks.")),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Concurso dashboard"),
            React.createElement("div", { className: "metric-grid compact-grid" },
                React.createElement(Metric, { label: "Edital coverage", value: pct((_h = dashboard === null || dashboard === void 0 ? void 0 : dashboard.coverage) !== null && _h !== void 0 ? _h : null), detail: `${(_j = dashboard === null || dashboard === void 0 ? void 0 : dashboard.noticeRevisionCount) !== null && _j !== void 0 ? _j : 0} accepted revisions` }),
                React.createElement(Metric, { label: "Question accuracy", value: pct((_k = dashboard === null || dashboard === void 0 ? void 0 : dashboard.accuracy) !== null && _k !== void 0 ? _k : null), detail: `${(_l = dashboard === null || dashboard === void 0 ? void 0 : dashboard.reviewBacklog) !== null && _l !== void 0 ? _l : 0} due reviews` }),
                React.createElement(Metric, { label: "Cutoff gap", value: (dashboard === null || dashboard === void 0 ? void 0 : dashboard.cutoffGap) == null ? "No data" : `${dashboard.cutoffGap.toFixed(1)} pp`, detail: "Only after a recorded mock + cutoff" }),
                React.createElement(Metric, { label: "Weighted mastery", value: pct((_m = dashboard === null || dashboard === void 0 ? void 0 : dashboard.weightedMastery) !== null && _m !== void 0 ? _m : null), detail: "Subject weight \u00D7 observed topic progress" }),
                React.createElement(Metric, { label: "Accuracy 30d", value: pct((_o = dashboard === null || dashboard === void 0 ? void 0 : dashboard.accuracy30) !== null && _o !== void 0 ? _o : null), detail: "Graded attempts in rolling window" })),
            React.createElement("p", null,
                React.createElement("strong", null, "Final stretch:"),
                " ",
                guidance.band === "unknown" ? "No confirmed exam date" : `${guidance.daysRemaining} days · ${guidance.band}`),
            React.createElement("details", null,
                React.createElement("summary", null, "Subject evidence"),
                React.createElement("ul", null, matrix.map(r => { const subject = state.subjects.find(s => s.id === r.subjectId); const known = [(subject === null || subject === void 0 ? void 0 : subject.weight) != null ? `weight ${subject.weight}` : null, (subject === null || subject === void 0 ? void 0 : subject.examIncidence) != null ? `incidence ${subject.examIncidence}%` : null, r.mastery != null ? `mastery ${Math.round(r.mastery * 100)}%` : null, r.backlog > 0 ? `backlog ${r.backlog}` : null].filter(Boolean); return React.createElement("li", { key: r.subjectId },
                    React.createElement("strong", null, r.subjectName),
                    React.createElement("span", null, known.length ? known.join(" · ") : "No observed subject evidence yet")); }))),
            React.createElement("details", null,
                React.createElement("summary", null, "Edital Matrix \u00B7 detailed importance/mastery"),
                React.createElement("ul", null, matrix.map(r => { var _a, _b, _c; return React.createElement("li", { key: r.subjectId },
                    React.createElement("strong", null, r.subjectName),
                    " \u00B7 priority ", (_a = r.priority) !== null && _a !== void 0 ? _a : "No data",
                    " \u00B7 mastery ",
                    pct(r.mastery),
                    " ",
                    React.createElement("input", { "aria-label": `Exam incidence ${r.subjectName}`, type: "number", min: "0", max: "100", placeholder: "Incidence %", value: (_c = (_b = state.subjects.find(s => s.id === r.subjectId)) === null || _b === void 0 ? void 0 : _b.examIncidence) !== null && _c !== void 0 ? _c : "", onChange: (e) => run(() => store.updateSubject(r.subjectId, { examIncidence: e.target.value === "" ? null : Number(e.target.value) }), "Incidence updated.") })); }))),
            React.createElement("details", null,
                React.createElement("summary", null, "Explainable next actions"),
                React.createElement("ul", null, priorities.map(x => { var _a; return React.createElement("li", { key: (_a = x.topicId) !== null && _a !== void 0 ? _a : x.subjectId },
                    x.explanation,
                    " \u00B7 ",
                    x.score); })))),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Exam stages"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (stage.trim()) {
                    run(() => store.addExamStage({ name: stage.trim() }), "Exam stage added.");
                    setStage("");
                } } },
                React.createElement("input", { "aria-label": "Exam stage name", value: stage, onChange: (e) => setStage(e.target.value), placeholder: "Stage name" }),
                React.createElement("button", { className: "button", type: "submit" }, "Add stage")),
            React.createElement("ul", null, c.stages.map(s => React.createElement("li", { key: s.id },
                s.name,
                " \u00B7 ",
                s.status,
                s.type !== "other" ? ` · ${title(s.type)}` : "",
                s.weight != null ? ` · ${s.weight} points` : "",
                s.minimumScorePercent != null ? ` · minimum ${s.minimumScorePercent}/100` : "",
                s.referenceQuestionCount != null ? ` · ${s.referenceQuestionCount} reference questions` : ""))),
            c.bbReferencePreset && React.createElement("details", { open: true },
                React.createElement("summary", null,
                    "BB fixed-weekly reference rhythm \u00B7 ",
                    c.bbReferencePreset.weeklyScheduledMinutes,
                    " min/week"),
                React.createElement("p", { className: "muted" }, "The configured availability windows mirror the supplied Monday\u2013Saturday reference rhythm. The 19-hour goal is retained independently; no 2026 exam date is created."),
                React.createElement("ul", null, c.bbReferencePreset.scheduleRhythm.map((b, i) => { var _a; return React.createElement("li", { key: i },
                    "Day ",
                    b.weekday,
                    " \u00B7 ",
                    String(Math.floor(b.startMinute / 60)).padStart(2, "0"),
                    ":",
                    String(b.startMinute % 60).padStart(2, "0"),
                    " \u00B7 ",
                    b.durationMinutes,
                    " min \u00B7 ", (_a = b.subjectName) !== null && _a !== void 0 ? _a : "general review/mock/writing window"); })))),
        React.createElement("article", { className: "card", id: "edital-revision" },
            React.createElement("h2", null, "Edital revision"),
            React.createElement("input", { "aria-label": "Edital revision reference", value: noticeRef, onChange: (e) => setNoticeRef(e.target.value), placeholder: "Reference / label / URL" }),
            React.createElement("input", { "aria-label": "Edital publication date", type: "date", value: noticePublished, onChange: (e) => setNoticePublished(e.target.value) }),
            React.createElement("textarea", { "aria-label": "Edital revision text", value: notice, onChange: (e) => { setNotice(e.target.value); setNoticePreview(null); }, placeholder: "Disciplina: Direito Constitucional\n- Direitos fundamentais\n- Administração pública" }),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button", type: "button", onClick: () => { const p = run(() => store.previewExamNotice(notice)); if (p)
                        setNoticePreview(p); } }, "Preview edital"),
                noticePreview && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applyExamNoticePreview(noticePreview, { reference: noticeRef || null, publishedAt: noticePublished || null }), "Edital revision applied; removed content archived."))
                        setNoticePreview(null); } }, "Apply preview")),
            noticePreview && React.createElement("p", { className: "muted" },
                noticePreview.subjects.length,
                " subjects \u00B7 ",
                noticePreview.duplicates.length,
                " duplicates \u00B7 ",
                noticePreview.unrecognized.length,
                " unrecognized")),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Legislation"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (law.trim()) {
                    run(() => store.addLegislation({ title: law.trim() }), "Legislation added locally.");
                    setLaw("");
                } } },
                React.createElement("input", { "aria-label": "Law, code, or regulation", value: law, onChange: (e) => setLaw(e.target.value), placeholder: "Law / code / regulation" }),
                React.createElement("button", { className: "button", type: "submit" }, "Add legislation")),
            React.createElement("ul", { className: "activity-list" },
                c.legislation.map(d => React.createElement("li", { key: d.id },
                    React.createElement("div", null,
                        React.createElement("strong", null, d.title),
                        React.createElement("span", null,
                            d.verifiedAt ? `Verified ${d.verifiedAt.slice(0, 10)}` : "Not verified",
                            " \u00B7 ",
                            d.provisions.length,
                            " provisions \u00B7 ",
                            d.evidenceLinks.length,
                            " evidence links")),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.verifyLegislation(d.id), "Legislation verification timestamp recorded.") }, "Verify now"))),
                !c.legislation.length && React.createElement("li", null, "No legislation documents."))),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Mock blueprint"),
            React.createElement("label", null,
                "Total questions",
                React.createElement("input", { "aria-label": "Mock total questions", type: "number", min: "1", max: "500", value: c.mockBlueprint.totalQuestions, onChange: (e) => run(() => store.setMockBlueprint({ totalQuestions: Number(e.target.value) }), "Mock blueprint updated.") })),
            React.createElement("label", null,
                "Duration (min)",
                React.createElement("input", { "aria-label": "Mock duration minutes", type: "number", min: "5", value: c.mockBlueprint.durationMinutes, onChange: (e) => run(() => store.setMockBlueprint({ durationMinutes: Number(e.target.value) }), "Mock blueprint updated.") })),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); const days = reviewDays.split(/[ ,;]+/).filter(Boolean).map(Number); run(() => store.setReviewIntervalsDays(days), "Review intervals updated."); } },
                React.createElement("label", null,
                    "Review intervals (days)",
                    React.createElement("input", { "aria-label": "Review interval days", value: reviewDays, onChange: (e) => setReviewDays(e.target.value), placeholder: "1, 7, 30" })),
                React.createElement("button", { className: "button button-quiet", type: "submit" }, "Save review policy")),
            React.createElement("p", { className: "muted" }, "Weighted mocks use question points and section weights. Unanswered questions stay neutral. Review progression follows the configured intervals.")));
}
function CodeBlock({ code, language }) { return React.createElement("pre", { className: "code-block", "data-language": language },
    React.createElement("code", null, (0, modes_1.tokenizeCode)(code, language).map((t, i) => React.createElement("span", { key: i, className: `code-${t.kind}` }, t.text)))); }
function ModeImportTools({ state, store, run }) { const [source, setSource] = React.useState(""); const [format, setFormat] = React.useState("csv"); const [preview, setPreview] = React.useState(null); const inspect = () => { const f = source.trim() ? format : (0, mode_import_1.detectModeImportFormat)(source); const next = run(() => store.previewModeImport(source, f)); if (next)
    setPreview(next); }; return React.createElement("article", { className: "card" },
    React.createElement("h3", null, "Import template"),
    React.createElement("select", { "aria-label": "Mode import format", value: format, onChange: (e) => setFormat(e.target.value) },
        React.createElement("option", { value: "csv" }, "CSV"),
        React.createElement("option", { value: "markdown" }, "Markdown")),
    React.createElement("textarea", { "aria-label": "Mode import source", value: source, onChange: (e) => setSource(e.target.value), placeholder: "Paste programming-course or language-deck rows" }),
    React.createElement("div", { className: "button-row" },
        React.createElement("button", { className: "button button-quiet", type: "button", onClick: inspect }, "Preview import"),
        preview && preview.summary.add > 0 && React.createElement("button", { className: "button", type: "button", onClick: () => { if (run(() => store.applyModeImportPreview(preview), "Mode import applied.")) {
                setPreview(null);
                setSource("");
            } } },
            "Apply ",
            preview.summary.add)),
    preview && React.createElement("p", { className: "muted" },
        preview.summary.add,
        " add \u00B7 ",
        preview.summary.duplicate,
        " duplicate \u00B7 ",
        preview.summary.invalid,
        " invalid. Preview does not mutate the plan.")); }
function ProgrammingWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.programming; const [name, setName] = React.useState(""); const [prompt, setPrompt] = React.useState(""); const [output, setOutput] = React.useState({}); return React.createElement("div", { className: "content-grid two-column" },
    React.createElement("article", { className: "card" },
        React.createElement("h2", null,
            "Programming course \u00B7 ",
            cfg.language),
        React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim() && prompt.trim()) {
                run(() => store.addProgrammingExercise({ title: name.trim(), prompt: prompt.trim(), starterCode: "// Start here", files: [] }), "Coding exercise added.");
                setName("");
                setPrompt("");
            } } },
            React.createElement("input", { "aria-label": "Exercise title", value: name, onChange: (e) => setName(e.target.value), placeholder: "Exercise title" }),
            React.createElement("textarea", { "aria-label": "Exercise prompt", value: prompt, onChange: (e) => setPrompt(e.target.value), placeholder: "Exercise prompt" }),
            React.createElement("button", { className: "button", type: "submit" }, "Add exercise")),
        React.createElement("p", { className: "muted" }, "Safety boundary: StudyFlow never runs arbitrary shell commands or pasted code. You can edit code locally and record observed output from a trusted runtime.")),
    React.createElement(ModeImportTools, { state: state, store: store, run: run }),
    cfg.exercises.map(x => { var _a; return React.createElement("article", { className: "card", key: x.id },
        React.createElement("h3", null, x.title),
        React.createElement("p", null, x.prompt),
        React.createElement("span", { className: "muted" },
            title(x.difficulty),
            " \u00B7 ",
            title(x.status),
            " \u00B7 ",
            x.language),
        React.createElement(CodeBlock, { code: x.starterCode, language: x.language }),
        x.files.map(f => React.createElement("details", { key: f.path },
            React.createElement("summary", null, f.path),
            React.createElement(CodeBlock, { code: f.content, language: f.language }))),
        React.createElement("input", { "aria-label": `Observed output for ${x.title}`, value: (_a = output[x.id]) !== null && _a !== void 0 ? _a : "", onChange: (e) => setOutput({ ...output, [x.id]: e.target.value }), placeholder: "Observed output from trusted runtime" }),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { var _a; return store.recordProgrammingExercise(x.id, { observedOutput: (_a = output[x.id]) !== null && _a !== void 0 ? _a : null }); }, "Attempt recorded without executing code.") }, "Record attempt"),
            React.createElement("button", { className: "button", type: "button", onClick: () => run(() => { var _a; return store.recordProgrammingExercise(x.id, { completed: true, observedOutput: (_a = output[x.id]) !== null && _a !== void 0 ? _a : null }); }, "Exercise completion evidenced.") }, "Mark completed"),
            x.solutionNotes && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.revealProgrammingSolution(x.id), `Solution reveal recorded: ${x.solutionNotes}`) }, "Reveal solution")),
        x.expectedOutput && React.createElement("p", { className: "muted" }, "Expected output is stored for explicit comparison after an attempt.")); })); }
function LanguageWorkspace({ state, store, run }) { var _a, _b, _c, _d; const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.language; const [expression, setExpression] = React.useState(""); const [meaning, setMeaning] = React.useState(""); const [selected, setSelected] = React.useState((_b = (_a = cfg.expressions[0]) === null || _a === void 0 ? void 0 : _a.id) !== null && _b !== void 0 ? _b : ""); const [direction, setDirection] = React.useState("recognition"); const [answer, setAnswer] = React.useState(""); const [rating, setRating] = React.useState(3); const card = (_d = (_c = cfg.expressions.find(x => x.id === selected)) !== null && _c !== void 0 ? _c : cfg.expressions[0]) !== null && _d !== void 0 ? _d : null; const prompt = card ? (0, modes_1.buildLanguageReviewPrompt)(card, direction) : null; return React.createElement("div", { className: "content-grid two-column" },
    React.createElement("article", { className: "card" },
        React.createElement("h2", null,
            "Language deck \u00B7 ",
            cfg.language),
        React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (expression.trim() && meaning.trim()) {
                run(() => store.addLanguageExpression({ expression: expression.trim(), meaning: meaning.trim() }), "Expression added.");
                setExpression("");
                setMeaning("");
            } } },
            React.createElement("input", { "aria-label": "Language expression or idiom", value: expression, onChange: (e) => setExpression(e.target.value), placeholder: "Expression / idiom" }),
            React.createElement("input", { "aria-label": "Expression meaning", value: meaning, onChange: (e) => setMeaning(e.target.value), placeholder: "Meaning" }),
            React.createElement("button", { className: "button", type: "submit" }, "Add expression")),
        React.createElement("ul", null, cfg.expressions.map(x => React.createElement("li", { key: x.id },
            React.createElement("strong", null, x.expression),
            " \u2014 ",
            x.meaning,
            x.register ? ` · ${x.register}` : "")))),
    React.createElement(ModeImportTools, { state: state, store: store, run: run }),
    React.createElement("article", { className: "card wide-card" },
        React.createElement("h3", null, "Six-direction review studio"),
        !card ? React.createElement("p", null, "No expressions yet.") : React.createElement(React.Fragment, null,
            React.createElement("select", { "aria-label": "Language review expression", value: card.id, onChange: (e) => { setSelected(e.target.value); setAnswer(""); } }, cfg.expressions.map(x => React.createElement("option", { key: x.id, value: x.id }, x.expression))),
            React.createElement("select", { "aria-label": "Language review direction", value: direction, onChange: (e) => { setDirection(e.target.value); setAnswer(""); } }, cfg.reviewDirections.map(x => React.createElement("option", { key: x, value: x }, title(x)))),
            React.createElement("p", null,
                React.createElement("strong", null, prompt === null || prompt === void 0 ? void 0 : prompt.prompt)),
            (prompt === null || prompt === void 0 ? void 0 : prompt.reason) && React.createElement("p", { className: "muted" }, prompt.reason),
            React.createElement("textarea", { "aria-label": "Language review answer", disabled: !(prompt === null || prompt === void 0 ? void 0 : prompt.ready), value: answer, onChange: (e) => setAnswer(e.target.value), placeholder: (prompt === null || prompt === void 0 ? void 0 : prompt.manualReview) ? "Write your response; grade it manually" : "Your answer" }),
            React.createElement("label", null,
                "Rating 0\u20135",
                React.createElement("input", { "aria-label": "Language review rating", type: "number", min: "0", max: "5", value: rating, onChange: (e) => setRating(Number(e.target.value)) })),
            React.createElement("button", { className: "button", type: "button", disabled: !(prompt === null || prompt === void 0 ? void 0 : prompt.ready), onClick: () => run(() => store.recordLanguageReview(card.id, direction, rating, { answer }), (prompt === null || prompt === void 0 ? void 0 : prompt.manualReview) ? "Manual-review language evidence recorded." : "Language review recorded.") }, "Record review"),
            (prompt === null || prompt === void 0 ? void 0 : prompt.expected) && React.createElement("details", null,
                React.createElement("summary", null, "Expected answer"),
                React.createElement("p", null, prompt.expected))))); }
function ReadingWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.reading; const [name, setName] = React.useState(""); const [units, setUnits] = React.useState(100); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Reading study"),
    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim()) {
            run(() => store.addReadingItem({ title: name.trim(), totalUnits: units, unit: "pages" }), "Reading item added.");
            setName("");
        } } },
        React.createElement("input", { "aria-label": "Reading item title", value: name, onChange: (e) => setName(e.target.value), placeholder: "Book / article" }),
        React.createElement("input", { "aria-label": "Total reading units", type: "number", min: "1", value: units, onChange: (e) => setUnits(Number(e.target.value)) }),
        React.createElement("button", { className: "button", type: "submit" }, "Add reading")),
    cfg.items.map(x => React.createElement("div", { className: "mode-item", key: x.id },
        React.createElement("strong", null, x.title),
        React.createElement("span", null,
            x.currentUnit,
            "/",
            x.totalUnits,
            " ",
            x.unit),
        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateReadingProgress(x.id, Math.min(x.totalUnits, x.currentUnit + 10)), "Reading progress evidenced.") }, "+10")))); }
function GeneralSkillWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.general; const [name, setName] = React.useState(""); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "General skill"),
    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim()) {
            run(() => store.addGeneralSkillDrill({ title: name.trim(), metric: "score", target: 10, unit: "points" }), "Skill drill added.");
            setName("");
        } } },
        React.createElement("input", { "aria-label": "Skill drill name", value: name, onChange: (e) => setName(e.target.value), placeholder: "Drill name" }),
        React.createElement("button", { className: "button", type: "submit" }, "Add drill")),
    cfg.drills.map(x => React.createElement("div", { className: "mode-item", key: x.id },
        React.createElement("strong", null, x.title),
        React.createElement("span", null,
            "Target ",
            x.target,
            " ",
            x.unit),
        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.recordGeneralSkillDrill(x.id, x.target), "Drill result evidenced.") }, "Record target")))); }
function ModeWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return null; if (plan.mode === "EXAM_PLAN")
    return React.createElement(PublicExamWorkspace, { state: state, store: store, run: run }); if (plan.mode === "PROGRAMMING_COURSE")
    return React.createElement(ProgrammingWorkspace, { state: state, store: store, run: run }); if (plan.mode === "LANGUAGE_DECK")
    return React.createElement(LanguageWorkspace, { state: state, store: store, run: run }); if (plan.mode === "READING_STUDY")
    return React.createElement(ReadingWorkspace, { state: state, store: store, run: run }); return React.createElement(GeneralSkillWorkspace, { state: state, store: store, run: run }); }
function TvMaterialViewer({ material, persistence, onClose }) {
    var _a;
    const plan = (0, retention_viewer_1.planMaterialViewer)(material);
    const [url, setUrl] = React.useState(null);
    const [text, setText] = React.useState(null);
    const [error, setError] = React.useState(plan.reason);
    const [textPage, setTextPage] = React.useState(0);
    const [pdfPage, setPdfPage] = React.useState(1);
    const [imageZoom, setImageZoom] = React.useState(100);
    const mediaRef = React.useRef(null);
    React.useEffect(() => { setTextPage(0); setPdfPage(1); setImageZoom(100); setUrl(null); setText(null); setError(plan.reason); let revoked = null, cancelled = false; (async () => { var _a; if (!(0, retention_viewer_1.materialViewerNeedsAttachment)(plan) || !plan.attachmentId)
        return; const record = await ((_a = persistence.attachments) === null || _a === void 0 ? void 0 : _a.get(plan.attachmentId)); if (cancelled)
        return; if (!record) {
        setError("Local attachment bytes are missing from IndexedDB.");
        return;
    } if (plan.kind === "local-text") {
        setText(await record.blob.text());
        return;
    } const next = URL.createObjectURL(record.blob); revoked = next; setUrl(next); })().catch(err => !cancelled && setError(err instanceof Error ? err.message : String(err))); return () => { cancelled = true; if (revoked)
        URL.revokeObjectURL(revoked); }; }, [material.id, material.updatedAt]);
    React.useEffect(() => { if (plan.kind !== "local-audio" && plan.kind !== "local-video")
        return; const listener = () => { const media = mediaRef.current; if (!media)
        return; if (media.paused) {
        const result = media.play();
        if (result && typeof result.catch === "function")
            result.catch(() => { });
    }
    else
        media.pause(); }; addEventListener("studyflow:tv-media-toggle", listener); return () => removeEventListener("studyflow:tv-media-toggle", listener); }, [plan.kind, url]);
    const pages = (0, tv_library_1.paginateTvText)(text !== null && text !== void 0 ? text : "");
    const currentTextPage = Math.max(0, Math.min(textPage, pages.length - 1));
    const moveText = (delta) => setTextPage(value => Math.max(0, Math.min(pages.length - 1, value + delta)));
    const movePdf = (delta) => setPdfPage(value => Math.max(1, value + delta));
    const zoomImage = (delta) => setImageZoom(value => Math.max(50, Math.min(200, value + delta)));
    return React.createElement("div", { className: "modal-backdrop tv-material-backdrop", role: "presentation" },
        React.createElement("section", { className: "modal-card material-viewer tv-material-viewer", role: "dialog", "aria-modal": "true", "aria-labelledby": "material-viewer-title" },
            React.createElement("div", { className: "dialog-title" },
                React.createElement("div", null,
                    React.createElement("p", { className: "eyebrow" }, "TV Library"),
                    React.createElement("h2", { id: "material-viewer-title" }, material.title),
                    React.createElement("p", { className: "muted" }, (_a = plan.mimeType) !== null && _a !== void 0 ? _a : "unknown MIME",
                        " \u00B7 ",
                        title(plan.kind),
                        material.archived ? " · archived" : "")),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, "Close")),
            error && React.createElement("p", { role: "alert" }, error),
            plan.kind === "web-page" && plan.uri && React.createElement("article", { className: "card" },
                React.createElement("h3", null, "External web reference"),
                React.createElement("p", { className: "muted" }, "Opening a new browser tab can transfer focus differently across TV browsers. Back returns to StudyFlow when the browser supports normal tab/history behavior."),
                React.createElement("a", { className: "button button-primary", href: plan.uri, target: "_blank", rel: "noopener noreferrer" }, "Open web resource")),
            plan.kind === "local-text" && text != null && React.createElement(React.Fragment, null,
                React.createElement("div", { className: "tv-material-toolbar" },
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: currentTextPage <= 0, onClick: () => moveText(-1) }, "Previous page"),
                    React.createElement("span", { className: "status-pill" },
                        "Page ",
                        currentTextPage + 1,
                        " / ",
                        pages.length),
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: currentTextPage >= pages.length - 1, onClick: () => moveText(1) }, "Next page")),
                React.createElement("pre", { className: "material-text-viewer tv-material-text-page" }, pages[currentTextPage])),
            plan.kind === "local-image" && url && React.createElement(React.Fragment, null,
                React.createElement("div", { className: "tv-material-toolbar" },
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: imageZoom <= 50, onClick: () => zoomImage(-25) }, "Zoom out"),
                    React.createElement("span", { className: "status-pill" },
                        imageZoom,
                        "%"),
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: imageZoom >= 200, onClick: () => zoomImage(25) }, "Zoom in")),
                React.createElement("div", { className: "tv-material-image-stage" },
                    React.createElement("img", { className: "material-image-viewer", style: { transform: `scale(${imageZoom / 100})`, transformOrigin: "top center" }, src: url, alt: material.title }))),
            plan.kind === "local-audio" && url && React.createElement(React.Fragment, null,
                React.createElement("audio", { ref: (node) => { mediaRef.current = node; }, controls: true, src: url }),
                React.createElement("p", { className: "muted" }, "The TV Play/Pause media key controls this audio when the browser exposes that key.")),
            " ",
            plan.kind === "local-video" && url && React.createElement(React.Fragment, null,
                React.createElement("video", { ref: (node) => { mediaRef.current = node; }, className: "material-video-viewer", controls: true, src: url }),
                React.createElement("p", { className: "muted" }, "The TV Play/Pause media key controls this video when the browser exposes that key.")),
            " ",
            plan.kind === "local-pdf" && url && React.createElement(React.Fragment, null,
                React.createElement("div", { className: "tv-material-toolbar" },
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: pdfPage <= 1, onClick: () => movePdf(-1) }, "Previous PDF page"),
                    React.createElement("span", { className: "status-pill" },
                        "PDF page ",
                        pdfPage),
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", onClick: () => movePdf(1) }, "Next PDF page")),
                React.createElement("iframe", { className: "material-pdf-viewer tv-material-pdf-viewer", title: material.title, src: (0, tv_library_1.tvPdfPageUrl)(url, pdfPage) }),
                React.createElement("p", { className: "muted" },
                    "Page navigation uses the standard PDF ",
                    React.createElement("code", null, "#page="),
                    " fragment. Some older TV browsers may ignore it; use Open PDF if the embedded viewer does not move."),
                React.createElement("a", { className: "button button-quiet", href: url, target: "_blank", rel: "noopener noreferrer" }, "Open PDF")),
            plan.kind === "local-download" && url && React.createElement("p", null,
                React.createElement("a", { className: "button button-primary", href: url, download: material.title }, "Open / download local file")),
            React.createElement("p", { className: "muted" }, "Viewer classification is local and MIME-based. Native Android FileProvider/external-handler mechanics are intentionally not reproduced in the browser.")));
}
function BrowserMaterialViewer({ material, persistence, onClose }) {
    var _a;
    const plan = (0, retention_viewer_1.planMaterialViewer)(material);
    const [url, setUrl] = React.useState(null);
    const [text, setText] = React.useState(null);
    const [error, setError] = React.useState(plan.reason);
    React.useEffect(() => { let revoked = null, cancelled = false; (async () => { var _a; if (!(0, retention_viewer_1.materialViewerNeedsAttachment)(plan) || !plan.attachmentId)
        return; const record = await ((_a = persistence.attachments) === null || _a === void 0 ? void 0 : _a.get(plan.attachmentId)); if (cancelled)
        return; if (!record) {
        setError("Local attachment bytes are missing from IndexedDB.");
        return;
    } if (plan.kind === "local-text") {
        setText(await record.blob.text());
        return;
    } const next = URL.createObjectURL(record.blob); revoked = next; setUrl(next); })().catch(err => !cancelled && setError(err instanceof Error ? err.message : String(err))); return () => { cancelled = true; if (revoked)
        URL.revokeObjectURL(revoked); }; }, [material.id, material.updatedAt]);
    return React.createElement("div", { className: "modal-backdrop", role: "presentation" },
        React.createElement("section", { className: "modal-card material-viewer", role: "dialog", "aria-modal": "true", "aria-labelledby": "material-viewer-title" },
            React.createElement("div", { className: "dialog-title" },
                React.createElement("div", null,
                    React.createElement("h2", { id: "material-viewer-title" }, "Material Viewer"),
                    React.createElement("p", { className: "muted" },
                        material.title,
                        " \u00B7 ", (_a = plan.mimeType) !== null && _a !== void 0 ? _a : "unknown MIME",
                        " \u00B7 ",
                        title(plan.kind))),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: onClose }, "Close")),
            error && React.createElement("p", { role: "alert" }, error),
            plan.kind === "web-page" && plan.uri && React.createElement("p", null,
                React.createElement("a", { className: "button button-primary", href: plan.uri, target: "_blank", rel: "noopener noreferrer" }, "Open web resource")),
            plan.kind === "local-text" && text != null && React.createElement("pre", { className: "material-text-viewer" }, text),
            plan.kind === "local-image" && url && React.createElement("img", { className: "material-image-viewer", src: url, alt: material.title }),
            " ",
            plan.kind === "local-audio" && url && React.createElement("audio", { controls: true, src: url }),
            " ",
            plan.kind === "local-video" && url && React.createElement("video", { className: "material-video-viewer", controls: true, src: url }),
            " ",
            plan.kind === "local-pdf" && url && React.createElement("iframe", { className: "material-pdf-viewer", title: material.title, src: url }),
            " ",
            plan.kind === "local-download" && url && React.createElement("p", null,
                React.createElement("a", { className: "button button-primary", href: url, download: material.title }, "Open / download local file")),
            React.createElement("p", { className: "muted" }, "Viewer classification is local and MIME-based. Native Android FileProvider/external-handler mechanics are intentionally not reproduced in the browser.")));
}
function MaterialViewer({ material, persistence, onClose }) { return isTvBrowserUi() ? React.createElement(TvMaterialViewer, { material: material, persistence: persistence, onClose: onClose }) : React.createElement(BrowserMaterialViewer, { material: material, persistence: persistence, onClose: onClose }); }
function Library({ materials, store, persistence, run, tvReadOnly = false, controlledViewingId, onViewingOpen, onViewingClose }) {
    var _a;
    const [localViewing, setLocalViewing] = React.useState(null);
    const viewing = controlledViewingId === undefined ? localViewing : (_a = materials.find(item => item.id === controlledViewingId)) !== null && _a !== void 0 ? _a : null;
    const openViewing = (material) => { if (onViewingOpen)
        onViewingOpen(material.id);
    else
        setLocalViewing(material); };
    const closeViewing = () => { if (onViewingClose)
        onViewingClose();
    else
        setLocalViewing(null); };
    const add = async (e) => { var _a; const file = (_a = e.target.files) === null || _a === void 0 ? void 0 : _a[0]; if (!file || !persistence.attachments)
        return; if (file.size > 50 * 1024 * 1024)
        throw new Error("Attachment exceeds the 50 MiB local limit"); let meta = null; try {
        meta = await persistence.attachments.put(file);
        store.addMaterial({ title: file.name, kind: "file", attachmentId: meta.id, locator: `local-blob:${meta.id}`, mimeType: meta.mimeType, sizeBytes: meta.sizeBytes });
    }
    catch (err) {
        if (meta)
            await persistence.attachments.delete(meta.id);
        throw err;
    }
    finally {
        e.target.value = "";
    } };
    return React.createElement("section", { id: "plan-library", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("h2", null, "Local materials & reference provenance"),
                    React.createElement("p", { className: "muted" }, "Material Viewer opens local attachments by MIME without uploading them.")),
                !tvReadOnly && React.createElement("label", { className: "button button-quiet file-button" },
                    "Add file",
                    React.createElement("input", { "aria-label": "Local material file", type: "file", onChange: (e) => add(e).catch(err => console.error(err)) }))),
            React.createElement("p", { className: "muted" }, "Tracked edital references use the existing material record. StudyFlow warns after 90 days without re-verification; that is a product policy, not an edital rule."),
            tvReadOnly && React.createElement("p", { className: "tv-inline-note" }, "TV reference mode keeps Material Viewer available while upload, delete and provenance editing stay on phone/computer."),
            React.createElement("ul", { className: "activity-list" },
                materials.map(m => { var _a; return React.createElement("li", { key: m.id },
                    React.createElement("div", null,
                        React.createElement("strong", null, m.title),
                        React.createElement("span", null, (_a = m.mimeType) !== null && _a !== void 0 ? _a : title(m.kind),
                            m.locator ? ` · ${m.locator}` : ""),
                        !tvReadOnly && React.createElement(MaterialReferenceControls, { material: m, store: store, run: run })),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": m.id, onClick: () => openViewing(m) }, "Open"),
                        !tvReadOnly && !m.archived && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deleteMaterial(m.id), "Material removed.") }, "Delete"))); }),
                !materials.length && React.createElement("li", null, "No local materials yet."))),
        viewing && React.createElement(MaterialViewer, { material: viewing, persistence: persistence, onClose: closeViewing }));
}
function Practice({ state, store, tab, setTab, questionId, setQuestionId, context, run, onQuestionSetOpen, onMockOpen, onReviewOpen }) {
    var _a, _b, _c, _d, _e, _f;
    const dueAll = (0, domain_1.selectDueReviews)(state), errors = (0, domain_1.selectOpenErrors)(state), plan = state.plans.find(p => p.id === state.activePlanId);
    const [filter, setFilter] = React.useState({});
    React.useEffect(() => { if (!context || context.screen !== "practice")
        return; setFilter(context.topicId ? { topicId: context.topicId } : context.subjectId ? { subjectId: context.subjectId } : {}); }, [context === null || context === void 0 ? void 0 : context.activityId]);
    const due = ((context === null || context === void 0 ? void 0 : context.screen) === "practice" && context.practiceTab === "reviews" ? dueAll.filter(r => context.topicId ? r.topicId === context.topicId : context.subjectId ? r.subjectId === context.subjectId : true) : dueAll).sort((a, b) => (context === null || context === void 0 ? void 0 : context.reviewId) === a.id ? -1 : (context === null || context === void 0 ? void 0 : context.reviewId) === b.id ? 1 : a.dueAt.localeCompare(b.dueAt));
    const filtered = (0, modes_1.filterPracticeQuestions)(state, filter);
    const current = (_b = (_a = filtered.find(q => q.id === questionId)) !== null && _a !== void 0 ? _a : filtered[0]) !== null && _b !== void 0 ? _b : null;
    const [answer, setAnswer] = React.useState("");
    const [importText, setImportText] = React.useState("");
    const [importPreview, setImportPreview] = React.useState(null);
    const [drill, setDrill] = React.useState(null);
    const [setName, setSetName] = React.useState("");
    const [manualPrompt, setManualPrompt] = React.useState("");
    const [manualBoard, setManualBoard] = React.useState("");
    const [manualSource, setManualSource] = React.useState("");
    const [manualTags, setManualTags] = React.useState("");
    const [flashFront, setFlashFront] = React.useState("");
    const [flashBack, setFlashBack] = React.useState("");
    const [flashType, setFlashType] = React.useState("BASIC");
    const [flashCloze, setFlashCloze] = React.useState("");
    const [flashTags, setFlashTags] = React.useState("");
    const flashcards = state.flashcards.filter(f => f.planId === state.activePlanId).sort((a, b) => Number(a.suspended) - Number(b.suspended) || b.updatedAt.localeCompare(a.updatedAt));
    const saved = (_c = plan === null || plan === void 0 ? void 0 : plan.configuration.practice.savedSets) !== null && _c !== void 0 ? _c : [];
    const quota = (_d = plan === null || plan === void 0 ? void 0 : plan.configuration.practice.weeklyQuestionQuota) !== null && _d !== void 0 ? _d : null;
    const weekAttempts = state.evidence.filter(e => e.planId === state.activePlanId && e.kind === "question-attempt" && Date.parse(e.observedAt) >= Date.now() - 7 * 86400000).length;
    if (isTvBrowserUi())
        return React.createElement(TvPractice, { state: state, store: store, tab: tab, setTab: setTab, questionId: questionId, setQuestionId: setQuestionId, context: context, run: run, onQuestionSetOpen: onQuestionSetOpen, onMockOpen: onMockOpen, onReviewOpen: onReviewOpen });
    return React.createElement("section", { id: "practice", "data-screen": "practice", className: "screen-section" },
        (context === null || context === void 0 ? void 0 : context.screen) === "practice" && React.createElement("article", { className: "mode-banner" },
            React.createElement("strong", null,
                title(context.activityType),
                " activity context"),
            React.createElement("span", { className: "muted" }, context.topicId ? "Topic filter preserved exactly." : context.subjectId ? "Subject filter preserved." : "Plan-wide context.")),
        React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "practice", label: "Practice sections", items: [["reviews", "Reviews"], ["questions", "Questions"], ["tests", "Tests"], ["errors", "Errors"]] }),
        tab === "reviews" && React.createElement("section", { id: "practice-reviews", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Due reviews"),
                    (context === null || context === void 0 ? void 0 : context.activityType) === "FLASHCARDS" && React.createElement("p", { className: "muted" }, "Flashcards activity context \u00B7 due cards are shown first and unrelated review types are hidden."),
                    React.createElement("ul", { className: "activity-list" },
                        due.filter(r => (context === null || context === void 0 ? void 0 : context.activityType) === "FLASHCARDS" ? r.sourceType === "flashcard" : true).map(r => { var _a, _b; const card = r.sourceType === "flashcard" && r.sourceId ? state.flashcards.find(f => f.id === r.sourceId) : null; return React.createElement("li", { key: r.id },
                            React.createElement("strong", null, card ? card.front : (_a = r.sourceType) !== null && _a !== void 0 ? _a : "Review"),
                            React.createElement("span", null, card ? `${card.back} · ${card.type}` : `${r.dueAt} · interval ${(_b = r.intervalDays) !== null && _b !== void 0 ? _b : "—"}d`),
                            React.createElement("div", { className: "button-row" },
                                React.createElement("button", { className: "button button-quiet", onClick: () => run(() => store.completeReview(r.id, { rating: "again" }), "Review reset to first interval.") }, "Again"),
                                React.createElement("button", { className: "button", onClick: () => run(() => store.completeReview(r.id, { rating: "good" }), "Review advanced.") }, "Good"),
                                React.createElement("button", { className: "button button-primary", onClick: () => run(() => store.completeReview(r.id, { rating: "easy" }), "Review advanced.") }, "Easy"))); }),
                        !due.filter(r => (context === null || context === void 0 ? void 0 : context.activityType) === "FLASHCARDS" ? r.sourceType === "flashcard" : true).length && React.createElement("li", null, "No due reviews."))),
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Flashcards"),
                    React.createElement("p", { className: "muted" }, "Persisted BASIC/CLOZE cards schedule an immediate first review. Suspending a card dismisses its pending review until resumed."),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (!flashFront.trim() || !flashBack.trim())
                            return; const added = run(() => { var _a, _b; return store.addFlashcard({ front: flashFront.trim(), back: flashBack.trim(), type: flashType, clozeText: flashType === "CLOZE" ? flashCloze.trim() : null, tags: flashTags.split(",").map(x => x.trim()).filter(Boolean), subjectId: (_a = context === null || context === void 0 ? void 0 : context.subjectId) !== null && _a !== void 0 ? _a : null, topicId: (_b = context === null || context === void 0 ? void 0 : context.topicId) !== null && _b !== void 0 ? _b : null }); }, "Flashcard created and first review scheduled."); if (added) {
                            setFlashFront("");
                            setFlashBack("");
                            setFlashCloze("");
                            setFlashTags("");
                        } } },
                        React.createElement("select", { "aria-label": "Flashcard type", value: flashType, onChange: (e) => setFlashType(e.target.value) },
                            React.createElement("option", { value: "BASIC" }, "Basic"),
                            React.createElement("option", { value: "CLOZE" }, "Cloze")),
                        React.createElement("input", { "aria-label": "Flashcard front", value: flashFront, onChange: (e) => setFlashFront(e.target.value), placeholder: "Front" }),
                        React.createElement("textarea", { "aria-label": "Flashcard back", value: flashBack, onChange: (e) => setFlashBack(e.target.value), placeholder: "Back" }),
                        flashType === "CLOZE" && React.createElement("textarea", { "aria-label": "Flashcard cloze text", value: flashCloze, onChange: (e) => setFlashCloze(e.target.value), placeholder: "Cloze text", required: true }),
                        React.createElement("input", { "aria-label": "Flashcard tags", value: flashTags, onChange: (e) => setFlashTags(e.target.value), placeholder: "tags, comma-separated" }),
                        React.createElement("button", { className: "button button-primary", type: "submit" }, "Create flashcard")),
                    React.createElement("ul", { className: "activity-list" },
                        flashcards.map(f => React.createElement("li", { key: f.id },
                            React.createElement("div", null,
                                React.createElement("strong", null, f.front),
                                React.createElement("span", null,
                                    f.back,
                                    " \u00B7 ",
                                    f.type,
                                    " \u00B7 ",
                                    f.tags.join(", ") || "no tags",
                                    f.suspended ? " · suspended" : "")),
                            React.createElement("div", { className: "button-row" },
                                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setFlashcardSuspended(f.id, !f.suspended), f.suspended ? "Flashcard resumed and review scheduled." : "Flashcard suspended and pending reviews dismissed.") }, f.suspended ? "Resume" : "Suspend"),
                                !f.suspended && !state.reviews.some(r => r.sourceType === "flashcard" && r.sourceId === f.id && r.status === "completed") && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deleteFlashcard(f.id), "Unused flashcard deleted.") }, "Delete")))),
                        !flashcards.length && React.createElement("li", null, "No flashcards yet."))))),
        tab === "questions" && React.createElement("section", { id: "practice-questions", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Question practice \u00B7 Question bank"),
                    React.createElement("h3", null, "Banca / evidence filters"),
                    React.createElement("div", { className: "stack-form" },
                        React.createElement("select", { "aria-label": "Question evidence filter", value: (_e = filter.evidence) !== null && _e !== void 0 ? _e : "", onChange: (e) => setFilter({ ...filter, evidence: e.target.value || undefined }) },
                            React.createElement("option", { value: "" }, "All evidence"),
                            React.createElement("option", { value: "wrong" }, "Wrong"),
                            React.createElement("option", { value: "correct" }, "Correct"),
                            React.createElement("option", { value: "unseen" }, "Unseen")),
                        React.createElement("select", { "aria-label": "Question difficulty filter", value: (_f = filter.difficulty) !== null && _f !== void 0 ? _f : "", onChange: (e) => setFilter({ ...filter, difficulty: e.target.value || undefined }) },
                            React.createElement("option", { value: "" }, "All difficulty"),
                            React.createElement("option", { value: "easy" }, "Easy"),
                            React.createElement("option", { value: "medium" }, "Medium"),
                            React.createElement("option", { value: "hard" }, "Hard")),
                        React.createElement("label", null,
                            React.createElement("input", { "aria-label": "Favorite questions only", type: "checkbox", checked: filter.favorite === true, onChange: (e) => setFilter({ ...filter, favorite: e.target.checked ? true : undefined }) }),
                            " Favorites only")),
                    React.createElement("p", { className: "muted" },
                        filtered.length,
                        " matching questions \u00B7 weekly quota ",
                        quota == null ? "not configured" : `${weekAttempts}/${quota}`),
                    React.createElement("label", null,
                        "Weekly question quota",
                        React.createElement("input", { "aria-label": "Weekly question quota", type: "number", min: "1", value: quota !== null && quota !== void 0 ? quota : "", onChange: (e) => run(() => store.setPracticeConfiguration({ weeklyQuestionQuota: e.target.value === "" ? null : Number(e.target.value) }), "Question quota updated.") })),
                    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (manualPrompt.trim()) {
                            run(() => store.addQuestion({ prompt: manualPrompt.trim(), examBoard: manualBoard.trim() || null, source: manualSource.trim() || null, tags: manualTags.split(",").map(x => x.trim()).filter(Boolean) }), "Question added.");
                            setManualPrompt("");
                            setManualBoard("");
                            setManualSource("");
                            setManualTags("");
                        } } },
                        React.createElement("input", { "aria-label": "Manual question prompt", value: manualPrompt, onChange: (e) => setManualPrompt(e.target.value), placeholder: "Manual question prompt" }),
                        React.createElement("input", { "aria-label": "Manual question board", value: manualBoard, onChange: (e) => setManualBoard(e.target.value), placeholder: "Exact banca, optional" }),
                        React.createElement("input", { "aria-label": "Manual question source", value: manualSource, onChange: (e) => setManualSource(e.target.value), placeholder: "Source, optional" }),
                        React.createElement("input", { "aria-label": "Manual question tags", value: manualTags, onChange: (e) => setManualTags(e.target.value), placeholder: "tags, comma-separated" }),
                        React.createElement("button", { className: "button button-quiet", type: "submit" }, "Add manual question")),
                    React.createElement("ul", { className: "activity-list" }, filtered.slice(0, 50).map(q => { var _a, _b, _c; return React.createElement("li", { key: q.id },
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setQuestionId(q.id) }, q.prompt),
                        React.createElement("span", null, (_a = q.examBoard) !== null && _a !== void 0 ? _a : "No banca",
                            " \u00B7 ", (_b = q.year) !== null && _b !== void 0 ? _b : "No year",
                            " \u00B7 ", (_c = q.difficulty) !== null && _c !== void 0 ? _c : "No difficulty")); })),
                    current && React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (run(() => store.answerQuestion(current.id, answer), "Question attempt recorded.")) {
                            setAnswer("");
                        } } },
                        React.createElement("strong", null, current.prompt),
                        React.createElement("input", { "aria-label": "Question answer", value: answer, onChange: (e) => setAnswer(e.target.value), placeholder: "Answer" }),
                        React.createElement("button", { className: "button button-primary", type: "submit" }, "Submit answer"))),
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Question import"),
                    React.createElement("p", { className: "muted" }, "CSV, TSV/Anki-style and Markdown. Preview rejects duplicates before mutation."),
                    React.createElement("textarea", { "aria-label": "Question import source", value: importText, onChange: (e) => { setImportText(e.target.value); setImportPreview(null); }, placeholder: 'prompt,answer,subject,topic,board,source,tags,year\n"Question?","Answer","Law","Rights","CEBRASPE","Official bank","bb;reference",2025' }),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button", type: "button", onClick: () => setImportPreview(store.previewQuestionImport(importText, (0, question_import_1.detectQuestionImportFormat)(importText))) }, "Preview import"),
                        (importPreview === null || importPreview === void 0 ? void 0 : importPreview.ready) && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applyQuestionImportPreview(importPreview), "Question import applied.")) {
                                setImportText("");
                                setImportPreview(null);
                            } } }, "Apply accepted questions")),
                    importPreview && React.createElement("p", { className: "muted" },
                        importPreview.summary.accepted,
                        " accepted \u00B7 ",
                        importPreview.summary.duplicates,
                        " duplicates \u00B7 ",
                        importPreview.summary.invalid,
                        " invalid"),
                    React.createElement("h3", null, "Saved sets"),
                    React.createElement("input", { "aria-label": "Saved practice set name", value: setName, onChange: (e) => setSetName(e.target.value), placeholder: "Set name" }),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => { if (setName.trim()) {
                            run(() => store.savePracticeSet(setName.trim(), filter, filtered.map(q => q.id)), "Practice set saved.");
                            setSetName("");
                        } } }, "Save current filter"),
                    React.createElement("ul", null, saved.map(x => React.createElement("li", { key: x.id },
                        x.name,
                        " \u00B7 ",
                        x.questionIds.length,
                        " questions ",
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deletePracticeSet(x.id), "Practice set deleted.") }, "Delete"))))))),
        tab === "tests" && React.createElement("section", { id: "practice-tests", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Weakness drill"),
                React.createElement("p", { className: "muted" }, "Built only from persisted wrong answers, open errors and due reviews. Unrelated questions are excluded."),
                React.createElement("button", { className: "button", type: "button", onClick: () => setDrill((0, modes_1.buildWeaknessDrill)(state, { limit: 10, minQuestions: 2 })) }, "Preview weakness drill"),
                drill && React.createElement(React.Fragment, null,
                    React.createElement("p", null, drill.ready ? `${drill.questionIds.length} matched questions` : drill.reasons[0]),
                    React.createElement("ul", null, drill.signals.map((x, i) => React.createElement("li", { key: i },
                        x.reasons.join(", "),
                        " \u00B7 score ",
                        x.score))))),
            (plan === null || plan === void 0 ? void 0 : plan.mode) === "EXAM_PLAN" && React.createElement(PublicExamMock, { state: state, store: store, run: run })),
        tab === "errors" && React.createElement("section", { id: "practice-errors", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Error Notebook"),
                React.createElement("ul", { className: "activity-list" },
                    errors.map(e => React.createElement("li", { key: e.id },
                        React.createElement("div", null,
                            React.createElement("strong", null, title(e.category)),
                            React.createElement("span", null,
                                "Recurrence ",
                                e.recurrenceCount)),
                        React.createElement("select", { "aria-label": `Reclassify error ${e.id}`, value: e.category, onChange: (ev) => run(() => store.reclassifyError(e.id, ev.target.value), "Error reclassified.") }, ["content-gap", "forgotten", "concept-confusion", "interpretation", "distraction", "calculation", "time-pressure", "disputed", "other"].map(c => React.createElement("option", { key: c, value: c }, title(c)))),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.resolveError(e.id), "Error resolved.") }, "Resolve"))),
                    !errors.length && React.createElement("li", null, "No open errors.")))));
}
function TvPractice({ state, store, tab, setTab, questionId, setQuestionId, context, run, onQuestionSetOpen, onMockOpen, onReviewOpen }) {
    var _a, _b, _c;
    const due = (0, domain_1.selectDueReviews)(state).filter(r => (context === null || context === void 0 ? void 0 : context.screen) === "practice" && context.practiceTab === "reviews" ? (context.topicId ? r.topicId === context.topicId : context.subjectId ? r.subjectId === context.subjectId : true) : true).sort((a, b) => a.dueAt.localeCompare(b.dueAt));
    const errors = (0, domain_1.selectOpenErrors)(state);
    const questions = (0, modes_1.filterPracticeQuestions)(state, (context === null || context === void 0 ? void 0 : context.topicId) ? { topicId: context.topicId } : (context === null || context === void 0 ? void 0 : context.subjectId) ? { subjectId: context.subjectId } : {}).slice(0, 40);
    const current = (_b = (_a = questions.find(q => q.id === questionId)) !== null && _a !== void 0 ? _a : questions[0]) !== null && _b !== void 0 ? _b : null;
    const currentIndex = current ? questions.findIndex(q => q.id === current.id) : -1;
    const [revealed, setRevealed] = React.useState(null);
    const [drill, setDrill] = React.useState(null);
    const activePlan = (_c = state.plans.find(p => p.id === state.activePlanId)) !== null && _c !== void 0 ? _c : null;
    const mockPreview = (activePlan === null || activePlan === void 0 ? void 0 : activePlan.mode) === "EXAM_PLAN" ? (0, modes_1.previewMockExam)(state) : null;
    React.useEffect(() => setRevealed(null), [current === null || current === void 0 ? void 0 : current.id]);
    return React.createElement("section", { id: "practice", "data-screen": "practice", className: "screen-section tv-practice" },
        (context === null || context === void 0 ? void 0 : context.screen) === "practice" && React.createElement("article", { className: "mode-banner" },
            React.createElement("strong", null,
                title(context.activityType),
                " activity context"),
            React.createElement("span", { className: "muted" }, "Remote-first presentation keeps the routed subject/topic context.")),
        React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "practice", label: "Practice sections", items: [["reviews", "Reviews"], ["questions", "Questions"], ["tests", "Tests"], ["errors", "Errors"]] }),
        tab === "reviews" && React.createElement("section", { id: "practice-reviews", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Review queue"),
                React.createElement("h2", null,
                    due.length,
                    " due review",
                    due.length === 1 ? "" : "s"),
                React.createElement("p", { className: "muted" }, "Open the dedicated TV Review surface for large prompts, answer/explanation reveal, Again/Hard/Good/Easy grading and Error Notebook follow-up."),
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", onClick: () => onReviewOpen("reviews") }, "Start TV reviews")),
            React.createElement("ul", { className: "activity-list" },
                due.slice(0, 6).map(r => { var _a, _b; return React.createElement("li", { key: r.id },
                    React.createElement("strong", null, (_a = r.sourceType) !== null && _a !== void 0 ? _a : "Review"),
                    React.createElement("span", null,
                        r.dueAt,
                        " \u00B7 interval ", (_b = r.intervalDays) !== null && _b !== void 0 ? _b : "—",
                        "d")); }),
                !due.length && React.createElement("li", null, "No due reviews."))),
        tab === "questions" && React.createElement("section", { id: "practice-questions", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Question browser"),
                    React.createElement("ul", { className: "activity-list tv-question-list" },
                        questions.map(q => { var _a, _b, _c; return React.createElement("li", { key: q.id },
                            React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": q.id, "aria-pressed": (current === null || current === void 0 ? void 0 : current.id) === q.id, onClick: () => setQuestionId(q.id) }, q.prompt),
                            React.createElement("span", null, (_a = q.examBoard) !== null && _a !== void 0 ? _a : "No banca",
                                " \u00B7 ", (_b = q.year) !== null && _b !== void 0 ? _b : "No year",
                                " \u00B7 ", (_c = q.difficulty) !== null && _c !== void 0 ? _c : "No difficulty")); }),
                        !questions.length && React.createElement("li", null, "No matching questions."))),
                React.createElement("article", { className: "card tv-question-stage" },
                    React.createElement("h2", null, "Question review"),
                    current ? React.createElement(React.Fragment, null,
                        React.createElement("strong", { className: "tv-question-prompt" }, current.prompt),
                        React.createElement("p", { className: "muted" }, "Use this surface for remote reading/review. Choose Start TV question set for answer-choice practice and persisted grading."),
                        React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", "data-tv-control-target": current.id, onClick: onQuestionSetOpen }, "Start TV question set"),
                        React.createElement("div", { className: "button-row" },
                            React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: currentIndex <= 0, onClick: () => currentIndex > 0 && setQuestionId(questions[currentIndex - 1].id) }, "Previous"),
                            React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "SHOW_EXPLANATION", "data-tv-control-target": current.id, onClick: () => setRevealed(revealed === current.id ? null : current.id) }, revealed === current.id ? "Hide answer" : "Reveal answer"),
                            React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: currentIndex < 0 || currentIndex >= questions.length - 1, onClick: () => currentIndex >= 0 && currentIndex < questions.length - 1 && setQuestionId(questions[currentIndex + 1].id) }, "Next")),
                        revealed === current.id && React.createElement("div", { className: "tv-answer-reveal", role: "status" },
                            React.createElement("span", { className: "eyebrow" }, "Expected answer"),
                            React.createElement("strong", null, current.answer))) : React.createElement("p", null, "No question selected."))),
            React.createElement(TvContinueElsewhere, { title: "Question authoring & import", detail: "Question-bank authoring, imports, saved-set naming and quota editing are keyboard-oriented workflows." })),
        tab === "tests" && React.createElement("section", { id: "practice-tests", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Mock Exam Studio"),
                React.createElement("h2", null, mockPreview ? mockPreview.ready ? `${mockPreview.questionIds.length} frozen questions ready` : `Question bank incomplete` : `Available for Concurso plans`),
                React.createElement("p", { className: "muted" }, "The TV Mock Exam uses the active plan's saved blueprint, freezes its local question selection, runs a timer, supports flags and persists one canonical mock-exam-result on submission."),
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", onClick: onMockOpen, disabled: !(mockPreview === null || mockPreview === void 0 ? void 0 : mockPreview.ready) }, "Open TV Mock Exam"),
                mockPreview && !mockPreview.ready && React.createElement("p", { className: "tv-inline-note" }, mockPreview.deficits.join("; "))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Weakness drill"),
                React.createElement("p", { className: "muted" }, "Preview a drill from persisted wrong answers, open errors and due reviews without typing."),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setDrill((0, modes_1.buildWeaknessDrill)(state, { limit: 10, minQuestions: 2 })) }, "Preview weakness drill"),
                drill && React.createElement(React.Fragment, null,
                    React.createElement("p", null, drill.ready ? `${drill.questionIds.length} matched questions` : drill.reasons[0]),
                    React.createElement("ul", null, drill.signals.map((x, i) => React.createElement("li", { key: i },
                        x.reasons.join(", "),
                        " \u00B7 score ",
                        x.score))))),
            React.createElement(TvContinueElsewhere, { title: "Mock blueprint configuration", detail: "Changing banca/source/tag filters, disputed-question policy and passing thresholds stays on phone/computer. TV executes the saved blueprint." })),
        tab === "errors" && React.createElement("section", { id: "practice-errors", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Error Notebook"),
                React.createElement("h2", null,
                    errors.length,
                    " open error",
                    errors.length === 1 ? "" : "s"),
                React.createElement("p", { className: "muted" }, "The dedicated Review surface includes D-pad-first Error Notebook browsing, category changes and resolve actions without text entry."),
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": "review-errors", onClick: () => onReviewOpen("errors") }, "Open TV Error Notebook")),
            React.createElement("ul", { className: "activity-list" },
                errors.slice(0, 8).map(e => React.createElement("li", { key: e.id },
                    React.createElement("strong", null, title(e.category)),
                    React.createElement("span", null,
                        "Recurrence ",
                        e.recurrenceCount,
                        e.note ? ` · ${e.note}` : ""))),
                !errors.length && React.createElement("li", null, "No open errors."))));
}
function PublicExamMock({ state, store, run }) {
    var _a, _b, _c, _d, _e;
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const saved = (_a = plan.configuration.publicExam) === null || _a === void 0 ? void 0 : _a.mockBlueprint;
    const [preview, setPreview] = React.useState(null);
    const [answers, setAnswers] = React.useState({});
    const [board, setBoard] = React.useState((_b = saved === null || saved === void 0 ? void 0 : saved.examBoard) !== null && _b !== void 0 ? _b : "");
    const [source, setSource] = React.useState((_c = saved === null || saved === void 0 ? void 0 : saved.sourceFilter) !== null && _c !== void 0 ? _c : "");
    const [tags, setTags] = React.useState(((_d = saved === null || saved === void 0 ? void 0 : saved.requiredTags) !== null && _d !== void 0 ? _d : []).join(", "));
    const [includeDisputed, setIncludeDisputed] = React.useState(Boolean(saved === null || saved === void 0 ? void 0 : saved.includeDisputed));
    const [passing, setPassing] = React.useState((saved === null || saved === void 0 ? void 0 : saved.minimumOverallScorePercent) == null ? "" : String(saved.minimumOverallScorePercent));
    const currentInput = () => ({ examBoard: board.trim() || null, sourceFilter: source.trim() || null, requiredTags: tags.split(",").map(x => x.trim()).filter(Boolean), includeDisputed, minimumOverallScorePercent: passing === "" ? null : Number(passing) });
    const bb = ((_e = plan.configuration.publicExam) === null || _e === void 0 ? void 0 : _e.bbReferencePreset) ? (0, modes_1.previewBbReferenceMockExam)(state) : null;
    const submit = () => { if (!preview)
        return; const result = run(() => store.recordMockExam(preview, answers), "Mock result recorded."); if (result) {
        const value = result.value;
        const passed = value === null || value === void 0 ? void 0 : value.passed;
        setPreview(null);
        setAnswers({});
        if (passed === true)
            run(() => true, `Mock recorded · passed ${value.percent}% / ${value.minimumOverallScorePercent}% threshold.`);
        else if (passed === false)
            run(() => true, `Mock recorded · ${value.percent}% below ${value.minimumOverallScorePercent}% threshold.`);
    } };
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Mock Exam Studio"),
        React.createElement("p", { className: "muted" }, "Freeze a local question selection before starting. Source, tag, disputed-question and passing-score controls are explicit and never inferred."),
        React.createElement("div", { className: "stack-form" },
            React.createElement("input", { "aria-label": "Mock exam board filter", value: board, onChange: (e) => setBoard(e.target.value), placeholder: "Exact banca, optional" }),
            React.createElement("input", { "aria-label": "Mock source filter", value: source, onChange: (e) => setSource(e.target.value), placeholder: "Source contains, optional" }),
            React.createElement("input", { "aria-label": "Mock tag filter", value: tags, onChange: (e) => setTags(e.target.value), placeholder: "required tags, comma-separated" }),
            React.createElement("label", null,
                React.createElement("input", { "aria-label": "Include disputed questions", type: "checkbox", checked: includeDisputed, onChange: (e) => setIncludeDisputed(e.target.checked) }),
                " Include disputed questions"),
            React.createElement("input", { "aria-label": "Mock passing score", type: "number", min: "0", max: "100", value: passing, onChange: (e) => setPassing(e.target.value), placeholder: "Passing score %, optional" })),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button", type: "button", onClick: () => setPreview((0, modes_1.previewMockExam)(state, currentInput())) }, "Preview frozen blueprint"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setMockBlueprint(currentInput()), "Mock Studio controls saved to this plan.") }, "Save controls")),
        bb && React.createElement("section", { className: "nested-panel" },
            React.createElement("h3", null, "BB Agente Comercial \u00B7 reference mock"),
            React.createElement("p", { className: "muted" }, "70 objective questions \u00B7 Cesgranrio exact-name matching \u00B7 300 min reference full mock \u00B7 100 reference points. Question counts are derived from reference point weights, not claimed as official per-subject item counts."),
            bb.preview ? React.createElement(React.Fragment, null,
                React.createElement("ul", null, bb.preview.blueprint.sections.map(section => { var _a; const subject = state.subjects.find(s => s.id === section.subjectId); return React.createElement("li", { key: section.id }, (_a = subject === null || subject === void 0 ? void 0 : subject.name) !== null && _a !== void 0 ? _a : section.subjectId,
                    ": ",
                    section.count,
                    " questions \u00B7 ",
                    section.weight,
                    " points"); })),
                React.createElement("p", null, bb.preview.ready ? "Local question coverage is complete." : `Question bank is short by ${bb.missingQuestionCount} matching Cesgranrio question(s).`),
                React.createElement("p", { className: "muted" },
                    "Essay training remains separate. Reference eliminatory minimum: ",
                    bb.essayMinimumScorePercent,
                    "/100. No objective cutoff is invented."),
                React.createElement("button", { className: "button button-quiet", type: "button", disabled: !bb.preview.ready, onClick: () => setPreview(bb.preview) }, "Use BB reference mock preview")) : React.createElement("p", { className: "muted" }, "The active plan no longer matches all eight BB reference subjects/weights.")),
        preview && React.createElement(React.Fragment, null,
            React.createElement("p", null, preview.ready ? `${preview.questionIds.length} questions ready${preview.blueprint.minimumOverallScorePercent == null ? "" : ` · passing score ${preview.blueprint.minimumOverallScorePercent}%`}` : preview.deficits.join("; ")),
            preview.ready && preview.questionIds.map(id => { var _a, _b; const q = state.questions.find(x => x.id === id); return React.createElement("label", { key: id },
                q.prompt,
                React.createElement("small", null, (_a = q.examBoard) !== null && _a !== void 0 ? _a : "No banca",
                    q.source ? ` · ${q.source}` : "",
                    q.tags.length ? ` · ${q.tags.join(", ")}` : ""),
                React.createElement("input", { "aria-label": `Mock answer ${id}`, value: (_b = answers[id]) !== null && _b !== void 0 ? _b : "", onChange: (e) => setAnswers({ ...answers, [id]: e.target.value }) })); }),
            preview.ready && React.createElement("button", { className: "button button-primary", type: "button", onClick: submit }, "Submit mock")));
}
function TvLibrarySurface({ state, persistence, materialId, onMaterialOpen, onMaterialClose, onBack }) {
    var _a, _b, _c, _d, _e, _f, _g;
    const all = state.materials.filter(material => material.planId === state.activePlanId);
    const [filter, setFilter] = React.useState("all");
    const visible = (0, tv_library_1.tvLibraryMaterials)(all, filter);
    const [index, setIndex] = React.useState(0);
    React.useEffect(() => { setIndex(0); }, [filter, state.activePlanId]);
    React.useEffect(() => { setIndex(value => (0, tv_library_1.tvLibraryNextIndex)(value, visible.length, 0)); }, [visible.length]);
    const current = (_a = visible[index]) !== null && _a !== void 0 ? _a : null;
    const viewerPlan = current ? (0, retention_viewer_1.planMaterialViewer)(current) : null;
    const subject = (current === null || current === void 0 ? void 0 : current.subjectId) ? (_b = state.subjects.find(item => item.id === current.subjectId)) !== null && _b !== void 0 ? _b : null : null;
    const topic = (current === null || current === void 0 ? void 0 : current.topicId) ? (_c = state.topics.find(item => item.id === current.topicId)) !== null && _c !== void 0 ? _c : null : null;
    const move = (delta) => { const next = (0, tv_library_1.tvLibraryNextIndex)(index, visible.length, delta); setIndex(next); requestAnimationFrame(() => { var _a; return (_a = document.querySelector(`[data-tv-library-index="${next}"]`)) === null || _a === void 0 ? void 0 : _a.focus(); }); };
    const choose = (next) => setIndex((0, tv_library_1.tvLibraryNextIndex)(next, visible.length, 0));
    const setMode = (next) => setFilter(next);
    return React.createElement("section", { id: "tv-library", "data-screen": "library", className: "screen-section tv-library-surface" },
        React.createElement("article", { className: "hero-card tv-library-hero" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "Library & references"),
                React.createElement("h2", null,
                    all.length,
                    " material",
                    all.length === 1 ? "" : "s",
                    " in ", (_e = (_d = state.plans.find(plan => plan.id === state.activePlanId)) === null || _d === void 0 ? void 0 : _d.name) !== null && _e !== void 0 ? _e : "active plan"),
                React.createElement("p", { className: "muted" }, "Local attachments stay in IndexedDB. TV Library is read-only and never uploads, deletes, or edits reference provenance.")),
            React.createElement("div", { className: "tv-library-count" },
                React.createElement("strong", null, visible.length),
                React.createElement("span", null, title(filter)))),
        React.createElement("nav", { className: "subnav tv-library-filter", "aria-label": "Library filter" },
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": filter === "all", "data-tv-control-action": "OPEN", "data-tv-control-target": "library-all", onClick: () => setMode("all") }, "All"),
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": filter === "local", "data-tv-control-action": "OPEN", "data-tv-control-target": "library-local", onClick: () => setMode("local") }, "Local files"),
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": filter === "reference", "data-tv-control-action": "OPEN", "data-tv-control-target": "library-reference", onClick: () => setMode("reference") }, "Web references")),
        current ? React.createElement("div", { className: "content-grid two-column tv-library-layout" },
            React.createElement("article", { className: "card tv-library-list-card" },
                React.createElement("h2", null, "Browse materials"),
                React.createElement("div", { className: "tv-library-list", role: "listbox", "aria-label": "Study materials" }, visible.map((material, materialIndex) => { var _a; return React.createElement("button", { key: material.id, type: "button", role: "option", "aria-selected": materialIndex === index, className: `tv-library-item${materialIndex === index ? " is-selected" : ""}`, "data-tv-library-index": materialIndex, "data-tv-control-action": "OPEN", "data-tv-control-target": material.id, onClick: () => choose(materialIndex) },
                    React.createElement("span", null,
                        React.createElement("strong", null, material.title),
                        React.createElement("small", null, (_a = material.mimeType) !== null && _a !== void 0 ? _a : title(material.kind),
                            material.archived ? " · archived" : "")),
                    React.createElement("span", { className: "status-pill" }, title((0, retention_viewer_1.planMaterialViewer)(material).kind))); }))),
            React.createElement("article", { className: "card tv-library-stage" },
                React.createElement("div", { className: "tv-library-stage-meta" },
                    React.createElement("span", { className: "status-pill" },
                        index + 1,
                        " / ",
                        visible.length),
                    React.createElement("span", { className: "status-pill" }, title((_f = viewerPlan === null || viewerPlan === void 0 ? void 0 : viewerPlan.kind) !== null && _f !== void 0 ? _f : current.kind))),
                React.createElement("h2", null, current.title),
                React.createElement("p", { className: "muted" }, [subject === null || subject === void 0 ? void 0 : subject.name, topic === null || topic === void 0 ? void 0 : topic.name, (_g = current.mimeType) !== null && _g !== void 0 ? _g : title(current.kind)].filter(Boolean).join(" · ")),
                current.locator && React.createElement("p", { className: "tv-library-locator" }, current.locator),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1) }, "Previous"),
                    React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", "data-tv-control-target": current.id, onClick: () => onMaterialOpen(current.id) }, "Open material"),
                    React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= visible.length - 1, onClick: () => move(1) }, "Next")),
                (viewerPlan === null || viewerPlan === void 0 ? void 0 : viewerPlan.reason) && React.createElement("p", { className: "tv-inline-note" }, viewerPlan.reason))) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "No materials in this filter"),
            React.createElement("p", { className: "muted" }, "Switch filters or add/import materials from a phone/computer.")),
        React.createElement(TvContinueElsewhere, { title: "Library editing", detail: "File upload, delete, reference-provenance editing and large metadata changes remain on phone/computer. TV keeps browsing and Material Viewer controls." }),
        React.createElement("article", { className: "card tv-library-footer" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Plan"),
            React.createElement("span", { className: "muted" }, "Use Previous/Next to browse; Start opens the selected material. Back closes the viewer before leaving Library.")),
        materialId && all.find(material => material.id === materialId) && React.createElement(MaterialViewer, { material: all.find(material => material.id === materialId), persistence: persistence, onClose: onMaterialClose }));
}
function TvStudySession({ state, store, focus, run, onBack }) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k;
    const active = focus.activityId ? (_a = state.activities.find(a => a.id === focus.activityId)) !== null && _a !== void 0 ? _a : null : null;
    const recommended = (_c = (_b = (0, domain_1.recommendNextActivity)(state)) === null || _b === void 0 ? void 0 : _b.activity) !== null && _c !== void 0 ? _c : null;
    const [sessionActivityId, setSessionActivityId] = React.useState((_e = (_d = active === null || active === void 0 ? void 0 : active.id) !== null && _d !== void 0 ? _d : recommended === null || recommended === void 0 ? void 0 : recommended.id) !== null && _e !== void 0 ? _e : null);
    const activity = sessionActivityId ? (_f = state.activities.find(a => a.id === sessionActivityId)) !== null && _f !== void 0 ? _f : null : active !== null && active !== void 0 ? active : recommended;
    const [completed, setCompleted] = React.useState(false);
    const [manualSegment, setManualSegment] = React.useState(null);
    const duration = focus.status === "idle" ? ((_g = activity === null || activity === void 0 ? void 0 : activity.durationMinutes) !== null && _g !== void 0 ? _g : focus.durationMinutes) : focus.durationMinutes;
    const segments = (0, tv_session_1.buildTvStudySessionSegments)(duration);
    const automaticSegment = focus.status === "idle" ? 0 : (0, tv_session_1.tvStudySessionSegmentIndex)(segments, duration, focus.remainingMs);
    const segmentIndex = Math.min(segments.length - 1, manualSegment !== null && manualSegment !== void 0 ? manualSegment : automaticSegment);
    const phase = (0, tv_session_1.tvStudySessionPhase)(focus.status, completed);
    const progress = completed ? 100 : focus.status === "idle" ? 0 : (0, tv_session_1.tvStudySessionProgressPercent)(duration, focus.remainingMs);
    const writing = (0, task_workflows_1.writingActivity)(activity);
    React.useEffect(() => { if (focus.activityId && !completed && focus.activityId !== sessionActivityId) {
        setSessionActivityId(focus.activityId);
        setManualSegment(null);
    } }, [focus.activityId, completed, sessionActivityId]);
    const startOrResume = () => { if (writing || completed)
        return; const result = run(() => { var _a; return store.startFocus(focus.status === "paused" ? {} : { activityId: (_a = activity === null || activity === void 0 ? void 0 : activity.id) !== null && _a !== void 0 ? _a : null, durationMinutes: duration }); }, focus.status === "paused" ? "Study Session resumed." : "Study Session started."); if (result)
        setCompleted(false); };
    const toggle = () => { if (completed || writing)
        return; if (focus.status === "running")
        run(() => store.pauseFocus(), "Study Session paused.");
    else
        startOrResume(); };
    React.useEffect(() => { const listener = () => toggle(); addEventListener("studyflow:tv-media-toggle", listener); return () => removeEventListener("studyflow:tv-media-toggle", listener); }, [focus.status, activity === null || activity === void 0 ? void 0 : activity.id, completed, writing]);
    const complete = () => { const result = run(() => store.completeFocus(), "Study Session completed and evidenced."); if (result)
        setCompleted(true); };
    return React.createElement("section", { id: "tv-study-session", "data-screen": "session", className: "screen-section tv-session-surface" },
        React.createElement("article", { className: "hero-card tv-session-hero" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" },
                    "Study Session \u00B7 ",
                    title(phase)),
                React.createElement("h2", null, (_h = activity === null || activity === void 0 ? void 0 : activity.title) !== null && _h !== void 0 ? _h : "Open a planned activity from Today"),
                React.createElement("p", { className: "muted" }, activity ? `${title(activity.activityType)} · ${duration} min · ${segments.length} presentation segment${segments.length === 1 ? "" : "s"}` : "No activity is ready for this TV session.")),
            React.createElement("div", { className: "tv-session-clock", "aria-label": `Study Session time remaining ${clock(focus.remainingMs)}` }, phase === "ready" ? `${duration}:00` : phase === "completed" ? "Done" : clock(focus.remainingMs)),
            React.createElement("progress", { max: "100", value: progress },
                progress,
                "%")),
        writing ? React.createElement(TvContinueElsewhere, { title: "Writing Workspace", detail: "The active activity is Writing. Drafting, rubric scoring and checklist completion remain on phone/computer." }) : React.createElement(React.Fragment, null,
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Session plan"),
                    React.createElement("ol", { className: "tv-session-segments" }, segments.map((segment, index) => React.createElement("li", { key: segment.id, "aria-current": index === segmentIndex ? "step" : undefined, className: index < segmentIndex ? "is-done" : index === segmentIndex ? "is-current" : "" },
                        React.createElement("span", null, index + 1),
                        React.createElement("div", null,
                            React.createElement("strong", null, segment.label),
                            React.createElement("small", null,
                                segment.minutes,
                                " min")))))),
                React.createElement("article", { className: "card tv-session-now" },
                    React.createElement("p", { className: "eyebrow" }, "Current segment"),
                    React.createElement("h2", null, (_k = (_j = segments[segmentIndex]) === null || _j === void 0 ? void 0 : _j.label) !== null && _k !== void 0 ? _k : "Focused study"),
                    React.createElement("p", { className: "muted" }, "Remote Play/Pause, the on-screen controls and the paired phone all target the same canonical focus session."),
                    React.createElement("div", { className: "button-row" },
                        phase === "ready" && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", onClick: startOrResume, disabled: !activity }, "Start"),
                        phase === "paused" && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "RESUME", onClick: startOrResume }, "Resume"),
                        phase === "running" && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "PAUSE", onClick: () => run(() => store.pauseFocus(), "Study Session paused.") }, "Pause"),
                        phase !== "completed" && React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: segmentIndex >= segments.length - 1, onClick: () => setManualSegment(Math.min(segments.length - 1, segmentIndex + 1)) }, "Next segment"),
                        phase !== "ready" && phase !== "completed" && React.createElement("button", { className: "button", type: "button", "data-tv-control-action": "COMPLETE", onClick: complete }, "Complete"),
                        phase === "completed" && React.createElement("button", { className: "button button-primary", type: "button", onClick: onBack }, "Return to Today")))),
            React.createElement("article", { className: "card tv-session-footer" },
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Today"),
                React.createElement("span", { className: "muted" }, "Segment changes are presentation-only; StudyFlow evidence remains the canonical Study Session timer and completion record."))));
}
function TvQuestionSet({ state, store, questionId, setQuestionId, context, run, onBack }) {
    var _a, _b, _c, _d, _e, _f, _g;
    const filter = (context === null || context === void 0 ? void 0 : context.topicId) ? { topicId: context.topicId } : (context === null || context === void 0 ? void 0 : context.subjectId) ? { subjectId: context.subjectId } : {};
    const questions = (0, modes_1.filterPracticeQuestions)(state, filter).slice(0, tv_question_set_1.TV_QUESTION_SET_LIMIT);
    const current = (_b = (_a = questions.find(q => q.id === questionId)) !== null && _a !== void 0 ? _a : questions[0]) !== null && _b !== void 0 ? _b : null;
    const index = current ? questions.findIndex(q => q.id === current.id) : -1;
    const [selection, setSelection] = React.useState(null);
    const [revealed, setRevealed] = React.useState(false);
    const [completed, setCompleted] = React.useState(false);
    const [attempts, setAttempts] = React.useState({});
    React.useEffect(() => { if (questions.length && !questions.some(q => q.id === questionId))
        setQuestionId(questions[0].id); }, [state.revision, context === null || context === void 0 ? void 0 : context.activityId, questionId]);
    React.useEffect(() => { setSelection(null); setRevealed(false); }, [current === null || current === void 0 ? void 0 : current.id]);
    const submit = () => { if (!current || selection == null)
        return; const response = run(() => store.answerQuestion(current.id, selection), "Question attempt recorded from TV."); if (response === null || response === void 0 ? void 0 : response.result) {
        setAttempts(previous => { var _a; return ({ ...previous, [current.id]: (_a = response.result.correct) !== null && _a !== void 0 ? _a : null }); });
        setRevealed(true);
    } };
    const move = (delta) => { if (!questions.length)
        return; const next = Math.max(0, Math.min(questions.length - 1, index + delta)); setQuestionId(questions[next].id); };
    const answered = Object.keys(attempts).length;
    const correct = Object.values(attempts).filter(v => v === true).length;
    const progress = completed ? 100 : (0, tv_question_set_1.tvQuestionProgressPercent)(Math.max(0, index), questions.length);
    if (completed)
        return React.createElement("section", { id: "tv-question-set", "data-screen": "questions", className: "screen-section tv-question-set-surface" },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Question Set complete"),
                React.createElement("h2", null,
                    answered,
                    " answered \u00B7 ",
                    correct,
                    " correct"),
                React.createElement("p", { className: "muted" }, "Every submitted answer was written to the canonical StudyFlow question-attempt evidence stream."),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-primary", type: "button", onClick: onBack }, "Return to Practice"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => { setAttempts({}); setCompleted(false); if (questions[0])
                            setQuestionId(questions[0].id); } }, "Restart presentation set"))));
    return React.createElement("section", { id: "tv-question-set", "data-screen": "questions", className: "screen-section tv-question-set-surface" },
        React.createElement("article", { className: "hero-card tv-question-set-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" },
                    "Question Set \u00B7 ",
                    questions.length ? `${index + 1} / ${questions.length}` : "No questions"),
                React.createElement("h2", null, (_c = current === null || current === void 0 ? void 0 : current.examBoard) !== null && _c !== void 0 ? _c : "Practice questions"),
                React.createElement("p", { className: "muted" }, current ? `${(_d = current.year) !== null && _d !== void 0 ? _d : "Year not set"} · ${title((_e = current.difficulty) !== null && _e !== void 0 ? _e : "difficulty not set")} · ${current.disputed ? "Disputed" : "Canonical local question"}` : "Open or import questions on phone/computer first.")),
            React.createElement("progress", { max: "100", value: progress },
                progress,
                "%")),
        current ? React.createElement("article", { className: "card tv-question-set-stage" },
            React.createElement("strong", { className: "tv-question-set-prompt" }, current.prompt),
            current.choices.length ? React.createElement("div", { className: "tv-choice-grid", role: "group", "aria-label": "Answer choices" }, current.choices.map((choice, choiceIndex) => React.createElement("button", { key: choiceIndex, type: "button", className: `tv-choice${selection === choice ? " is-selected" : ""}`, "aria-pressed": selection === choice, "data-tv-control-action": "SELECT_ANSWER", "data-tv-control-target": current.id, "data-tv-control-option": (0, tv_question_set_1.tvQuestionChoiceOptionId)(choiceIndex), onClick: () => setSelection(choice) },
                React.createElement("span", null, (0, tv_question_set_1.tvQuestionChoiceLabel)(choiceIndex)),
                React.createElement("strong", null, choice)))) : React.createElement(TvContinueElsewhere, { title: "Typed-answer question", detail: "This question has no selectable choices, so graded answer entry stays on phone/computer. You can still reveal its stored answer/explanation and move through the set." }),
            React.createElement("div", { className: "button-row tv-question-set-actions" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1) }, "Previous"),
                current.choices.length > 0 && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "SUBMIT", disabled: selection == null, onClick: submit }, "Submit choice"),
                React.createElement("button", { className: "button", type: "button", "data-tv-control-action": "SHOW_EXPLANATION", "data-tv-control-target": current.id, onClick: () => setRevealed(v => !v) }, revealed ? "Hide explanation" : "Show explanation"),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= questions.length - 1, onClick: () => move(1) }, "Next"),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "COMPLETE", onClick: () => setCompleted(true) }, "Complete set")),
            revealed && React.createElement("div", { className: "tv-question-feedback", role: "status" },
                React.createElement("p", { className: "eyebrow" }, "Answer & explanation"),
                React.createElement("strong", null, (_f = current.answer) !== null && _f !== void 0 ? _f : "No canonical answer stored."),
                React.createElement("p", null, (_g = current.explanation) !== null && _g !== void 0 ? _g : "No explanation stored for this question."),
                current.id in attempts && React.createElement("p", { className: attempts[current.id] === true ? "tv-result-correct" : attempts[current.id] === false ? "tv-result-wrong" : "muted" }, attempts[current.id] === true ? "Your submitted choice was correct." : attempts[current.id] === false ? "Your submitted choice was incorrect and StudyFlow scheduled the canonical review/error evidence." : "The attempt was recorded but this question is not automatically gradable."))) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "No matching questions"),
            React.createElement("p", { className: "muted" }, "Question authoring/import stays on phone/computer. Return to Practice to choose another context.")),
        React.createElement("article", { className: "card tv-question-set-footer" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Practice"),
            React.createElement("span", { className: "muted" }, "Answer selection uses D-pad focus and Enter/OK; paired-phone A\u2013D buttons use the same SELECT_ANSWER protocol action.")));
}
function TvMockExamStudio({ state, store, run, onBack }) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const activePlan = (_a = state.plans.find(p => p.id === state.activePlanId)) !== null && _a !== void 0 ? _a : null;
    const [preview] = React.useState(() => (activePlan === null || activePlan === void 0 ? void 0 : activePlan.mode) === "EXAM_PLAN" ? (0, modes_1.previewMockExam)(state) : null);
    const [phase, setPhase] = React.useState("ready");
    const [index, setIndex] = React.useState(0);
    const [answers, setAnswers] = React.useState({});
    const [flagged, setFlagged] = React.useState(() => new Set());
    const [startedAt, setStartedAt] = React.useState(null);
    const [pausedAt, setPausedAt] = React.useState(null);
    const [pausedAccumulated, setPausedAccumulated] = React.useState(0);
    const [result, setResult] = React.useState(null);
    const [showDetail, setShowDetail] = React.useState(false);
    const [submitPrompt, setSubmitPrompt] = React.useState(false);
    const [abandonPrompt, setAbandonPrompt] = React.useState(false);
    const questions = ((_b = preview === null || preview === void 0 ? void 0 : preview.questionIds) !== null && _b !== void 0 ? _b : []).map(id => state.questions.find(q => q.id === id)).filter(Boolean);
    const current = (_c = questions[index]) !== null && _c !== void 0 ? _c : null;
    const duration = (_d = preview === null || preview === void 0 ? void 0 : preview.blueprint.durationMinutes) !== null && _d !== void 0 ? _d : 60;
    const remaining = (0, tv_mock_exam_1.tvMockRemainingMs)(duration, startedAt, Date.now(), pausedAt, pausedAccumulated);
    const progress = phase === "results" ? 100 : (0, tv_mock_exam_1.tvMockProgressPercent)(duration, remaining);
    const answered = (0, tv_mock_exam_1.tvMockAnsweredCount)(answers);
    const selection = current ? (_e = answers[current.id]) !== null && _e !== void 0 ? _e : null : null;
    const active = phase === "running" || phase === "paused";
    React.useEffect(() => { if (active)
        document.documentElement.dataset.studyflowTvExamActive = "true";
    else
        delete document.documentElement.dataset.studyflowTvExamActive; return () => { delete document.documentElement.dataset.studyflowTvExamActive; }; }, [active]);
    React.useEffect(() => { const listener = () => { if (active)
        setAbandonPrompt(true); }; addEventListener("studyflow:tv-exam-back", listener); return () => removeEventListener("studyflow:tv-exam-back", listener); }, [active]);
    React.useEffect(() => { if (!active)
        return; const listener = (event) => { event.preventDefault(); event.returnValue = ""; }; addEventListener("beforeunload", listener); return () => removeEventListener("beforeunload", listener); }, [active]);
    const record = () => { if (!preview)
        return; const evidence = run(() => store.recordMockExam(preview, answers), "Mock exam result recorded from TV."); if (!evidence)
        return; setResult(evidence.value); setPhase("results"); setSubmitPrompt(false); setShowDetail(false); delete document.documentElement.dataset.studyflowTvExamActive; };
    React.useEffect(() => { if (phase === "running" && remaining <= 0)
        record(); }, [phase, remaining]);
    const start = () => { if (!(preview === null || preview === void 0 ? void 0 : preview.ready))
        return; setStartedAt(Date.now()); setPausedAt(null); setPausedAccumulated(0); setPhase("running"); };
    const pause = () => { if (phase !== "running")
        return; setPausedAt(Date.now()); setPhase("paused"); };
    const resume = () => { if (phase !== "paused" || pausedAt == null)
        return; setPausedAccumulated(v => v + Math.max(0, Date.now() - pausedAt)); setPausedAt(null); setPhase("running"); };
    const move = (delta) => setIndex(v => Math.max(0, Math.min(questions.length - 1, v + delta)));
    const toggleFlag = () => { if (!current)
        return; setFlagged(previous => { const next = new Set(previous); if (next.has(current.id))
        next.delete(current.id);
    else
        next.add(current.id); return next; }); };
    const askBack = () => { if (active) {
        setAbandonPrompt(true);
        return;
    } onBack(); };
    const abandon = () => { delete document.documentElement.dataset.studyflowTvExamActive; setAbandonPrompt(false); onBack(); };
    if (!activePlan || activePlan.mode !== "EXAM_PLAN")
        return React.createElement("section", { id: "tv-mock-exam", "data-screen": "mock", className: "screen-section tv-mock-surface" },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Mock Exam Studio"),
                React.createElement("h2", null, "Concurso plan required"),
                React.createElement("p", { className: "muted" }, "TV mock execution uses the active EXAM_PLAN mock blueprint."),
                React.createElement("button", { className: "button button-primary", type: "button", onClick: onBack }, "Return to Practice")));
    if (!(preview === null || preview === void 0 ? void 0 : preview.ready))
        return React.createElement("section", { id: "tv-mock-exam", "data-screen": "mock", className: "screen-section tv-mock-surface" },
            React.createElement("article", { className: "hero-card" },
                React.createElement("p", { className: "eyebrow" }, "Mock Exam Studio"),
                React.createElement("h2", null, "Question bank is not ready"),
                React.createElement("p", { className: "muted" }, (preview === null || preview === void 0 ? void 0 : preview.deficits.join("; ")) || "No frozen mock preview is available."),
                React.createElement("button", { className: "button button-primary", type: "button", onClick: onBack }, "Return to Practice")));
    if (phase === "results" && result)
        return React.createElement("section", { id: "tv-mock-exam", "data-screen": "mock", className: "screen-section tv-mock-surface" },
            React.createElement("article", { className: "hero-card tv-mock-result-hero" },
                React.createElement("div", null,
                    React.createElement("p", { className: "eyebrow" }, "Mock Exam complete"),
                    React.createElement("h2", null,
                        result.percent == null ? "No percent" : `${result.percent}%`,
                        " \u00B7 ",
                        result.passed == null ? "No passing threshold" : result.passed ? "Passed" : "Below threshold"),
                    React.createElement("p", { className: "muted" },
                        result.correct,
                        " correct \u00B7 ",
                        result.wrong,
                        " wrong \u00B7 ",
                        result.unanswered,
                        " unanswered \u00B7 ",
                        flagged.size,
                        " marked for review")),
                React.createElement("div", { className: "tv-mock-score" }, result.percent == null ? "—" : `${result.percent}%`)),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "REVIEW_RESULTS", onClick: () => setShowDetail(v => !v) }, showDetail ? "Hide results" : "Review results"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Return to Practice")),
            showDetail && React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Section results"),
                    React.createElement("ul", { className: "activity-list" }, ((_f = result.sections) !== null && _f !== void 0 ? _f : []).map((section) => { var _a, _b; return React.createElement("li", { key: section.sectionId },
                        React.createElement("strong", null, section.subjectId ? (_b = (_a = state.subjects.find(s => s.id === section.subjectId)) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : section.sectionId : section.sectionId),
                        React.createElement("span", null,
                            section.correct,
                            " correct \u00B7 ",
                            section.wrong,
                            " wrong \u00B7 ",
                            section.unanswered,
                            " unanswered \u00B7 ",
                            section.percent,
                            "%")); }))),
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Marked questions"),
                    React.createElement("ul", { className: "activity-list" },
                        [...flagged].map(id => { var _a; const q = state.questions.find(x => x.id === id); return React.createElement("li", { key: id },
                            React.createElement("strong", null, (_a = q === null || q === void 0 ? void 0 : q.prompt) !== null && _a !== void 0 ? _a : id),
                            React.createElement("span", null, answers[id] ? "Answered" : "Unanswered")); }),
                        !flagged.size && React.createElement("li", null, "No questions were marked.")))));
    return React.createElement("section", { id: "tv-mock-exam", "data-screen": "mock", className: "screen-section tv-mock-surface" },
        React.createElement("article", { className: "hero-card tv-mock-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" },
                    "Mock Exam Studio \u00B7 ",
                    phase === "ready" ? "Ready" : phase === "paused" ? "Paused" : "Running"),
                React.createElement("h2", null, (_g = preview.blueprint.examBoard) !== null && _g !== void 0 ? _g : activePlan.name),
                React.createElement("p", { className: "muted" },
                    questions.length,
                    " frozen questions \u00B7 ",
                    duration,
                    " min \u00B7 ",
                    answered,
                    " answered \u00B7 ",
                    flagged.size,
                    " marked")),
            React.createElement("div", { className: "tv-mock-clock", "aria-label": `Mock time remaining ${clock(remaining)}` }, clock(remaining)),
            React.createElement("progress", { max: "100", value: progress },
                progress,
                "%")),
        phase === "ready" ? React.createElement("article", { className: "card tv-mock-ready" },
            React.createElement("h2", null, "Frozen local exam"),
            React.createElement("p", { className: "muted" }, "Starting locks this in-memory preview for the attempt. Answers are persisted only once, as the canonical mock-exam-result, when you submit or time expires."),
            React.createElement("div", { className: "metric-grid compact-grid" },
                React.createElement(Metric, { label: "Questions", value: String(questions.length), detail: "Frozen selection" }),
                React.createElement(Metric, { label: "Duration", value: `${duration} min`, detail: "Saved blueprint" }),
                React.createElement(Metric, { label: "Scoring", value: title(preview.blueprint.scoring), detail: preview.blueprint.minimumOverallScorePercent == null ? "No passing threshold" : `${preview.blueprint.minimumOverallScorePercent}% passing threshold` })),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", onClick: start }, "Start mock exam"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Practice"))) : current ? React.createElement("article", { className: "card tv-mock-question" },
            React.createElement("div", { className: "tv-mock-question-meta" },
                React.createElement("span", { className: "status-pill" },
                    "Question ",
                    index + 1,
                    " / ",
                    questions.length),
                flagged.has(current.id) && React.createElement("span", { className: "status-pill" }, "Marked for review"),
                React.createElement("span", { className: "muted" }, (_h = current.examBoard) !== null && _h !== void 0 ? _h : "No banca",
                    current.points != null ? ` · ${current.points} pt` : "")),
            React.createElement("strong", { className: "tv-mock-prompt" }, current.prompt),
            current.choices.length ? React.createElement("div", { className: "tv-choice-grid", role: "group", "aria-label": "Mock exam answer choices" }, current.choices.map((choice, choiceIndex) => React.createElement("button", { key: choiceIndex, type: "button", className: `tv-choice${selection === choice ? " is-selected" : ""}`, "aria-pressed": selection === choice, disabled: phase !== "running", "data-tv-control-action": "SELECT_ANSWER", "data-tv-control-target": current.id, "data-tv-control-option": (0, tv_mock_exam_1.tvMockChoiceOptionId)(choiceIndex), onClick: () => setAnswers(previous => ({ ...previous, [current.id]: choice })) },
                React.createElement("span", null, (0, tv_question_set_1.tvQuestionChoiceLabel)(choiceIndex)),
                React.createElement("strong", null, choice)))) : React.createElement(TvContinueElsewhere, { title: "Typed-answer mock question", detail: "This frozen question has no choice buttons. It remains unanswered on TV; typed entry can be used only in the phone/computer Mock Studio." }),
            React.createElement("div", { className: "button-row tv-mock-actions" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1) }, "Previous"),
                React.createElement("button", { className: "button", type: "button", "data-tv-control-action": "MARK_FOR_REVIEW", "data-tv-control-target": current.id, onClick: toggleFlag }, flagged.has(current.id) ? "Unmark review" : "Mark for review"),
                phase === "running" ? React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PAUSE", onClick: pause }, "Pause") : React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "RESUME", onClick: resume }, "Resume"),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= questions.length - 1, onClick: () => move(1) }, "Next"),
                !submitPrompt && React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "SUBMIT", onClick: () => setSubmitPrompt(true) }, "Submit exam")),
            submitPrompt && React.createElement("div", { className: "tv-mock-confirm", role: "alert" },
                React.createElement("strong", null, "Submit this mock now?"),
                React.createElement("p", { className: "muted" },
                    answered,
                    "/",
                    questions.length,
                    " questions answered. Unanswered questions remain neutral under the canonical scorer."),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "SUBMIT", onClick: record }, "Confirm submit"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setSubmitPrompt(false) }, "Continue exam")))) : null,
        React.createElement("article", { className: "card tv-mock-footer" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: askBack }, "Back to Practice"),
            React.createElement("span", { className: "muted" },
                "Question progress ",
                (0, tv_mock_exam_1.tvMockQuestionProgressPercent)(index, questions.length),
                "% \u00B7 browser/remote Back is guarded while the exam is active.")),
        abandonPrompt && React.createElement("div", { className: "modal-backdrop", role: "presentation" },
            React.createElement("section", { className: "modal-card tv-exam-exit-guard", role: "dialog", "aria-modal": "true", "aria-labelledby": "tv-exam-exit-title" },
                React.createElement("h2", { id: "tv-exam-exit-title" }, "Leave active mock exam?"),
                React.createElement("p", null, "This attempt has not been recorded. Leaving discards the in-memory answers and marks."),
                React.createElement("div", { className: "button-row" },
                    React.createElement("button", { className: "button button-primary", type: "button", onClick: () => setAbandonPrompt(false) }, "Continue exam"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: abandon }, "Discard and leave")))));
}
function TvReviewNotebook({ state, store, run, initialMode, onBack }) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q;
    const reviews = (0, domain_1.selectDueReviews)(state).sort((a, b) => a.dueAt.localeCompare(b.dueAt));
    const errors = (0, domain_1.selectOpenErrors)(state);
    const [mode, setMode] = React.useState(initialMode);
    const [index, setIndex] = React.useState(0);
    const [revealed, setRevealed] = React.useState(false);
    const [completedCount, setCompletedCount] = React.useState(0);
    const currentReview = (_b = (_a = reviews[index]) !== null && _a !== void 0 ? _a : reviews[0]) !== null && _b !== void 0 ? _b : null;
    const currentError = (_d = (_c = errors[index]) !== null && _c !== void 0 ? _c : errors[0]) !== null && _d !== void 0 ? _d : null;
    const initialTotal = React.useRef(reviews.length);
    React.useEffect(() => { setIndex(v => mode === "reviews" ? (0, tv_review_notebook_1.tvReviewIndexAfterCompletion)(v, reviews.length) : (0, tv_review_notebook_1.tvReviewIndexAfterCompletion)(v, errors.length)); setRevealed(false); }, [mode, reviews.length, errors.length]);
    const focusPrimary = () => requestAnimationFrame(() => { var _a; return (_a = document.querySelector('[data-tv-review-primary="true"]')) === null || _a === void 0 ? void 0 : _a.focus(); });
    const rate = (rating) => { if (!currentReview)
        return; const done = run(() => store.completeReview(currentReview.id, { rating: rating }), `Review graded ${title(rating)} from TV.`); if (done) {
        setCompletedCount(v => v + 1);
        setRevealed(false);
        focusPrimary();
    } };
    const reviewQuestion = (currentReview === null || currentReview === void 0 ? void 0 : currentReview.sourceType) === "question" && currentReview.sourceId ? (_e = state.questions.find(q => q.id === currentReview.sourceId)) !== null && _e !== void 0 ? _e : null : null;
    const reviewCard = (currentReview === null || currentReview === void 0 ? void 0 : currentReview.sourceType) === "flashcard" && currentReview.sourceId ? (_f = state.flashcards.find(card => card.id === currentReview.sourceId)) !== null && _f !== void 0 ? _f : null : null;
    const reviewPrompt = (_h = (_g = reviewCard === null || reviewCard === void 0 ? void 0 : reviewCard.front) !== null && _g !== void 0 ? _g : reviewQuestion === null || reviewQuestion === void 0 ? void 0 : reviewQuestion.prompt) !== null && _h !== void 0 ? _h : (currentReview ? `${title((_j = currentReview.sourceType) !== null && _j !== void 0 ? _j : "review")} review` : "No due review");
    const reviewAnswer = (_l = (_k = reviewCard === null || reviewCard === void 0 ? void 0 : reviewCard.back) !== null && _k !== void 0 ? _k : reviewQuestion === null || reviewQuestion === void 0 ? void 0 : reviewQuestion.answer) !== null && _l !== void 0 ? _l : "Use your study material to recall the answer.";
    const reviewExplanation = (_m = reviewQuestion === null || reviewQuestion === void 0 ? void 0 : reviewQuestion.explanation) !== null && _m !== void 0 ? _m : ((reviewCard === null || reviewCard === void 0 ? void 0 : reviewCard.type) === "CLOZE" && reviewCard.clozeText ? reviewCard.clozeText : null);
    const errorQuestion = (currentError === null || currentError === void 0 ? void 0 : currentError.questionId) ? (_o = state.questions.find(q => q.id === currentError.questionId)) !== null && _o !== void 0 ? _o : null : null;
    const move = (delta, total) => { if (total <= 0)
        return; setIndex(v => Math.max(0, Math.min(total - 1, v + delta))); setRevealed(false); };
    const reclassify = (category) => { if (!currentError)
        return; run(() => store.reclassifyError(currentError.id, category), `Error reclassified as ${title(category)}.`); };
    const resolve = () => { if (!currentError)
        return; const done = run(() => store.resolveError(currentError.id), "Error resolved from TV."); if (done)
        focusPrimary(); };
    return React.createElement("section", { id: "tv-review-notebook", "data-screen": "review", className: "screen-section tv-review-surface" },
        React.createElement("article", { className: "hero-card tv-review-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "Review & Error Notebook"),
                React.createElement("h2", null, mode === "reviews" ? `${reviews.length} due reviews` : `${errors.length} open errors`),
                React.createElement("p", { className: "muted" }, "Remote-first review uses canonical StudyFlow scheduling, grading and Error Notebook records.")),
            React.createElement("progress", { max: "100", value: (0, tv_review_notebook_1.tvReviewProgressPercent)(completedCount, Math.max(1, initialTotal.current)) },
                (0, tv_review_notebook_1.tvReviewProgressPercent)(completedCount, Math.max(1, initialTotal.current)),
                "%")),
        React.createElement("nav", { className: "subnav tv-review-mode", "aria-label": "Review surface mode" },
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": mode === "reviews", "data-tv-control-action": "OPEN", "data-tv-control-target": "review-reviews", onClick: () => { setMode("reviews"); setIndex(0); } }, "Reviews"),
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": mode === "errors", "data-tv-control-action": "OPEN", "data-tv-control-target": "review-errors", onClick: () => { setMode("errors"); setIndex(0); } }, "Error Notebook")),
        mode === "reviews" ? React.createElement(React.Fragment, null, currentReview ? React.createElement("article", { className: "card tv-review-stage" },
            React.createElement("div", { className: "tv-review-meta" },
                React.createElement("span", { className: "status-pill" },
                    index + 1,
                    " / ",
                    reviews.length),
                React.createElement("span", { className: "muted" },
                    "Due ",
                    currentReview.dueAt,
                    " \u00B7 interval ", (_p = currentReview.intervalDays) !== null && _p !== void 0 ? _p : "—",
                    "d")),
            React.createElement("strong", { className: "tv-review-prompt" }, reviewPrompt),
            revealed ? React.createElement("div", { className: "tv-review-answer", role: "status" },
                React.createElement("p", { className: "eyebrow" }, "Answer"),
                React.createElement("strong", null, reviewAnswer),
                reviewExplanation && React.createElement("p", null, reviewExplanation)) : React.createElement("button", { className: "button button-primary", type: "button", "data-tv-review-primary": "true", "data-tv-control-action": "SHOW_EXPLANATION", "data-tv-control-target": currentReview.id, onClick: () => setRevealed(true) }, "Show answer"),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1, reviews.length) }, "Previous"),
                tv_review_notebook_1.TV_REVIEW_RATINGS.map(rating => React.createElement("button", { key: rating, className: rating === "good" ? "button button-primary" : "button", type: "button", "data-tv-control-action": "COMPLETE", "data-tv-control-target": currentReview.id, "data-tv-control-option": (0, tv_review_notebook_1.tvReviewRatingOptionId)(rating), onClick: () => rate(rating) }, title(rating))),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= reviews.length - 1, onClick: () => move(1, reviews.length) }, "Next"))) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Review queue complete"),
            React.createElement("p", { className: "muted" }, "No reviews are due right now. Completed grades have already scheduled their canonical successor reviews."),
            React.createElement("button", { className: "button button-primary", type: "button", "data-tv-review-primary": "true", onClick: () => { setMode("errors"); setIndex(0); } }, "Open Error Notebook"))) : React.createElement(React.Fragment, null, currentError ? React.createElement("article", { className: "card tv-error-stage" },
            React.createElement("div", { className: "tv-review-meta" },
                React.createElement("span", { className: "status-pill" },
                    "Error ",
                    index + 1,
                    " / ",
                    errors.length),
                React.createElement("span", { className: "status-pill" },
                    "Recurrence ",
                    currentError.recurrenceCount)),
            React.createElement("strong", { className: "tv-review-prompt" }, (_q = errorQuestion === null || errorQuestion === void 0 ? void 0 : errorQuestion.prompt) !== null && _q !== void 0 ? _q : `${title(currentError.category)} error`),
            React.createElement("div", { className: "tv-review-answer" },
                React.createElement("p", { className: "eyebrow" }, "Current classification"),
                React.createElement("strong", null, title(currentError.category)),
                currentError.note && React.createElement("p", null, currentError.note),
                (errorQuestion === null || errorQuestion === void 0 ? void 0 : errorQuestion.answer) && React.createElement("p", null,
                    React.createElement("strong", null, "Canonical answer:"),
                    " ",
                    errorQuestion.answer),
                (errorQuestion === null || errorQuestion === void 0 ? void 0 : errorQuestion.explanation) && React.createElement("p", null, errorQuestion.explanation)),
            React.createElement("div", { className: "tv-error-categories", "aria-label": "Error category choices" }, tv_review_notebook_1.TV_ERROR_CATEGORIES.map(category => React.createElement("button", { key: category, className: `button button-quiet${currentError.category === category ? " is-selected" : ""}`, type: "button", "data-tv-control-action": "MARK_FOR_REVIEW", "data-tv-control-target": currentError.id, "data-tv-control-option": (0, tv_review_notebook_1.tvErrorCategoryOptionId)(category), onClick: () => reclassify(category) }, title(category)))),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1, errors.length) }, "Previous"),
                React.createElement("button", { className: "button button-primary", type: "button", "data-tv-review-primary": "true", "data-tv-control-action": "COMPLETE", "data-tv-control-target": currentError.id, "data-tv-control-option": "resolve-error", onClick: resolve }, "Resolve error"),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= errors.length - 1, onClick: () => move(1, errors.length) }, "Next"))) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Error Notebook clear"),
            React.createElement("p", { className: "muted" }, "No open errors remain in the active plan."),
            React.createElement("button", { className: "button button-primary", type: "button", "data-tv-review-primary": "true", onClick: () => { setMode("reviews"); setIndex(0); } }, "Return to reviews"))),
        React.createElement("article", { className: "card tv-review-footer" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Practice"),
            React.createElement("span", { className: "muted" }, "Typed notes and flashcard authoring stay on phone/computer; grading, category changes and resolution persist directly here.")));
}
function TvProgressAnalytics({ state, onOpenSurface }) {
    const snapshot = (0, tv_progress_1.buildTvProgressSnapshot)(state, new Date());
    const noData = (value, suffix = "%") => value == null ? "No data" : `${value}${suffix}`;
    const action = (label, target, surface, primary = false) => React.createElement("button", { className: primary ? "button button-primary" : "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": target, onClick: () => onOpenSurface(surface) }, label);
    return React.createElement("section", { id: "tv-progress", "data-screen": "progress", className: "screen-section tv-progress-surface" },
        React.createElement("article", { className: "hero-card tv-progress-hero" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "Progress & analytics"),
                React.createElement("h2", null, snapshot.planName),
                React.createElement("p", { className: "muted" },
                    "Evidence window ",
                    snapshot.weekLabel,
                    ". Unknown metrics stay explicitly unknown.")),
            React.createElement("div", { className: "tv-progress-readiness" },
                React.createElement("span", null, "Readiness"),
                React.createElement("strong", null, noData(snapshot.readinessPercent)),
                React.createElement("small", null, snapshot.readinessReason))),
        React.createElement("div", { className: "metric-grid tv-progress-metrics" },
            React.createElement(Metric, { label: "Study this week", value: `${snapshot.weeklyStudyMinutes} min`, detail: "Completed sessions" }),
            React.createElement(Metric, { label: "Question volume", value: String(snapshot.weeklyQuestionAttempts), detail: "Attempts in the last 7 days" }),
            React.createElement(Metric, { label: "Accuracy", value: noData(snapshot.accuracyPercent), detail: "Graded persisted attempts" }),
            React.createElement(Metric, { label: "Coverage", value: noData(snapshot.coveragePercent), detail: "Topics with canonical evidence" }),
            React.createElement(Metric, { label: "Review health", value: noData(snapshot.reviewCompletionPercent), detail: "Completed vs due/completed pool" })),
        React.createElement("article", { className: "card tv-progress-actions" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "Remote actions"),
                React.createElement("h2", null, "What to do next"),
                React.createElement("p", { className: "muted" }, "These buttons only navigate to existing StudyFlow TV flows; analytics never mutates evidence.")),
            React.createElement("div", { className: "button-row" },
                action("Today", "progress-today", "TODAY"),
                action("Questions", "progress-questions", "QUESTION_SET", true),
                action("Reviews", "progress-reviews", "REVIEW"),
                action("Mock Exam", "progress-mock", "MOCK_EXAM"),
                action("Schedule", "progress-schedule", "SCHEDULE"))),
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("div", null,
                    React.createElement("p", { className: "eyebrow" }, "Weak subjects"),
                    React.createElement("h2", null, snapshot.weakSubjects.length ? `${snapshot.weakSubjects.length} attention area${snapshot.weakSubjects.length === 1 ? "" : "s"}` : "No active weakness signal"))),
            snapshot.weakSubjects.length ? React.createElement("div", { className: "tv-progress-subject-grid" }, snapshot.weakSubjects.map((row, index) => React.createElement("article", { className: "nested-panel tv-progress-subject", key: row.subjectId },
                React.createElement("div", { className: "tv-progress-subject-heading" },
                    React.createElement("div", null,
                        React.createElement("strong", null, row.name),
                        React.createElement("span", { className: "muted" }, row.reason)),
                    React.createElement("span", { className: "status-pill" },
                        "score ",
                        row.attentionScore)),
                React.createElement("div", { className: "tv-progress-subject-stats" },
                    React.createElement("span", null,
                        row.weeklyStudyMinutes,
                        " min"),
                    React.createElement("span", null,
                        row.weeklyQuestionAttempts,
                        " questions"),
                    React.createElement("span", null, row.accuracyPercent == null ? "Accuracy No data" : `${row.accuracyPercent}% accuracy`),
                    React.createElement("span", null,
                        row.coveredTopics,
                        "/",
                        row.activeTopics,
                        " topics")),
                React.createElement("button", { className: index === 0 ? "button button-primary" : "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": `progress-weak-${index}`, onClick: () => onOpenSurface(row.recommendedSurface) },
                    "Open ",
                    title(row.recommendedSurface))))) : React.createElement("p", { className: "muted" }, "Persisted accuracy, coverage, review and Error Notebook evidence does not currently identify a weak subject.")),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "All subjects"),
            React.createElement("div", { className: "tv-progress-subject-grid compact" },
                snapshot.subjects.map(row => React.createElement("article", { className: "nested-panel", key: row.subjectId },
                    React.createElement("strong", null, row.name),
                    React.createElement("span", { className: "muted" },
                        row.weeklyStudyMinutes,
                        " min \u00B7 ",
                        row.weeklyQuestionAttempts,
                        " questions \u00B7 ",
                        row.accuracyPercent == null ? "accuracy No data" : `${row.accuracyPercent}% accuracy`,
                        " \u00B7 ",
                        row.coveredTopics,
                        "/",
                        row.activeTopics,
                        " topics"))),
                !snapshot.subjects.length && React.createElement("p", null, "No subjects in the active plan."))),
        React.createElement(TvContinueElsewhere, { title: "Goals, exports & maintenance", detail: "Goal creation, report downloads, cleanup application, portable backup/import and destructive state maintenance remain on phone/computer. Existing goal completion and pause/resume stay available from Today." }));
}
function TvScheduleSurface({ state, onLaunch, onOpenSurface, onBack }) {
    var _a, _b;
    const [view, setView] = React.useState("today");
    const snapshot = (0, tv_schedule_1.buildTvScheduleSnapshot)(state, view, new Date());
    const [index, setIndex] = React.useState(snapshot.nextIndex);
    const current = (_a = snapshot.flatItems[Math.max(0, Math.min(index, snapshot.flatItems.length - 1))]) !== null && _a !== void 0 ? _a : null;
    React.useEffect(() => { setIndex(snapshot.nextIndex); }, [view, state.activePlanId]);
    React.useEffect(() => { if (snapshot.flatItems.length === 0) {
        setIndex(0);
        return;
    } setIndex(value => Math.max(0, Math.min(value, snapshot.flatItems.length - 1))); }, [snapshot.flatItems.length]);
    const focusIndex = (next) => { if (!snapshot.flatItems.length)
        return; const clamped = Math.max(0, Math.min(next, snapshot.flatItems.length - 1)); setIndex(clamped); requestAnimationFrame(() => { var _a; return (_a = document.querySelector(`[data-tv-schedule-index="${clamped}"]`)) === null || _a === void 0 ? void 0 : _a.focus(); }); };
    const move = (delta) => focusIndex(index + delta);
    const launch = () => { if (current === null || current === void 0 ? void 0 : current.launchable)
        onLaunch(current.activity.id); };
    return React.createElement("section", { id: "tv-schedule", "data-screen": "schedule", className: "screen-section tv-schedule-surface" },
        React.createElement("article", { className: "hero-card tv-schedule-hero" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "Read-only schedule"),
                React.createElement("h2", null, view === "today" ? "Today" : "This week"),
                React.createElement("p", { className: "muted" },
                    snapshot.windowLabel,
                    " \u00B7 canonical scheduled activities only. Editing and rescheduling remain on phone/computer.")),
            React.createElement("div", { className: "tv-schedule-count" },
                React.createElement("strong", null, snapshot.visibleCount),
                React.createElement("span", null, "scheduled items"))),
        React.createElement("div", { className: "metric-grid" },
            React.createElement(Metric, { label: "Scheduled", value: `${snapshot.plannedMinutes} min`, detail: "Visible schedule duration" }),
            React.createElement(Metric, { label: "Planned", value: String(snapshot.plannedCount), detail: "Ready to study" }),
            React.createElement(Metric, { label: "Completed", value: String(snapshot.completedCount), detail: "Already completed" }),
            React.createElement(Metric, { label: "Cancelled", value: String(snapshot.cancelledCount), detail: "Retained schedule history" })),
        React.createElement("nav", { className: "subnav tv-schedule-mode", "aria-label": "Schedule view" },
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": view === "today", "data-tv-control-action": "OPEN", "data-tv-control-target": "schedule-today", onClick: () => setView("today") }, "Today"),
            React.createElement("button", { className: "tab-link", type: "button", "aria-pressed": view === "week", "data-tv-control-action": "OPEN", "data-tv-control-target": "schedule-week", onClick: () => setView("week") }, "This week")),
        current ? React.createElement("article", { className: "card tv-schedule-now" },
            React.createElement("div", { className: "tv-schedule-now-meta" },
                React.createElement("span", { className: "status-pill" },
                    index + 1,
                    " / ",
                    snapshot.flatItems.length),
                React.createElement("span", { className: "status-pill" }, title(current.activity.status)),
                React.createElement("span", { className: "muted" },
                    current.dateLabel,
                    " \u00B7 ",
                    current.timeLabel,
                    " \u00B7 ",
                    current.activity.durationMinutes,
                    " min")),
            React.createElement("strong", { className: "tv-schedule-title" }, current.activity.title),
            React.createElement("p", { className: "muted" }, [current.subjectName, current.topicName, title(current.activity.activityType)].filter(Boolean).join(" · ")),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "PREVIOUS", disabled: index <= 0, onClick: () => move(-1) }, "Previous"),
                current.launchable ? React.createElement("button", { className: "button button-primary", type: "button", "data-tv-control-action": "START", "data-tv-control-target": current.activity.id, onClick: launch },
                    "Open ",
                    title((_b = current.destination) !== null && _b !== void 0 ? _b : "STUDY_SESSION")) : React.createElement("button", { className: "button", type: "button", disabled: true }, current.activity.activityType === "WRITING" ? "Continue writing on phone/computer" : `${title(current.activity.status)} item`),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "NEXT", disabled: index >= snapshot.flatItems.length - 1, onClick: () => move(1) }, "Next"))) : React.createElement("article", { className: "card" },
            React.createElement("h2", null, "No scheduled activity"),
            React.createElement("p", { className: "muted" }, "Nothing is scheduled in this view. Schedule creation remains on phone/computer.")),
        React.createElement("div", { className: "tv-schedule-days" }, snapshot.days.map(day => React.createElement("section", { className: "card tv-schedule-day", key: day.date },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("h2", null, day.label),
                React.createElement("span", { className: "status-pill" }, day.items.length)),
            React.createElement("div", { className: "tv-schedule-item-grid" }, day.items.map(item => { const itemIndex = snapshot.flatItems.findIndex(candidate => candidate.activity.id === item.activity.id); return React.createElement("button", { key: item.activity.id, type: "button", className: `tv-schedule-item${itemIndex === index ? " is-selected" : ""}`, "data-tv-schedule-index": itemIndex, onClick: () => setIndex(itemIndex) },
                React.createElement("span", { className: "tv-schedule-item-time" }, item.timeLabel),
                React.createElement("span", null,
                    React.createElement("strong", null, item.activity.title),
                    React.createElement("small", null, [item.subjectName, title(item.activity.activityType), title(item.activity.status)].filter(Boolean).join(" · ")))); }))))),
        React.createElement("article", { className: "card tv-schedule-footer" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: onBack }, "Back to Plan"),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": "schedule-progress", onClick: () => onOpenSurface("PROGRESS") }, "Progress"),
                React.createElement("button", { className: "button button-quiet", type: "button", "data-tv-control-action": "OPEN", "data-tv-control-target": "schedule-today-workspace", onClick: () => onOpenSurface("TODAY") }, "Today workspace"))));
}
function Progress({ state, store, tab, setTab, persistence, portable, setPortable, portableStatus, setPortableStatus, run }) {
    var _a;
    const m = (0, domain_1.deriveProgressMetrics)(state), timeline = (0, domain_1.buildEvidenceTimeline)(state, { limit: 30, at: new Date().toISOString() }), integrity = (0, domain_1.buildContentIntegrityReport)(state), goals = state.goals.filter(g => g.planId === state.activePlanId), modeAnalytics = (0, modes_1.deriveModeAnalytics)(state), readiness = (0, intelligence_1.deriveReadinessExplanation)(state), coach = (0, intelligence_1.deriveWeeklyCoach)(state), quota = (0, intelligence_1.deriveQuestionQuotaPlan)(state);
    const [goal, setGoal] = React.useState("");
    const tv = isTvBrowserUi();
    return React.createElement("section", { id: "progress", "data-screen": "progress", className: "screen-section" },
        React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "progress", label: "Progress sections", items: [["summary", "Summary"], ["performance", "Performance"], ["goals", "Goals"], ["tools", "Tools"]] }),
        tab === "summary" && React.createElement("section", { id: "progress-summary", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "metric-grid" },
                React.createElement(Metric, { label: "Readiness", value: m.readiness == null ? "No data" : `${m.readiness}%`, detail: m.readinessReason }),
                React.createElement(Metric, { label: "Study this week", value: `${m.weeklyStudyMinutes} min`, detail: "Completed sessions" }),
                React.createElement(Metric, { label: "Accuracy", value: pct(m.accuracy), detail: `${m.gradedAttempts} graded attempts` }),
                React.createElement(Metric, { label: "Coverage", value: pct(m.topicCoverage), detail: `${m.coveredTopics}/${m.activeTopics} evidenced topics` })),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Exam readiness model"),
                React.createElement("p", null,
                    React.createElement("strong", null, readiness.value == null ? "No readiness score" : `${readiness.value}% · ${title((_a = readiness.band) !== null && _a !== void 0 ? _a : "")}`)),
                React.createElement("p", { className: "muted" },
                    readiness.reason,
                    readiness.daysUntilExam == null ? "" : ` · ${readiness.daysUntilExam} days to confirmed exam`),
                React.createElement("ul", { className: "activity-list" }, readiness.signals.map(s => { var _a; return React.createElement("li", { key: s.id },
                    React.createElement("div", null,
                        React.createElement("strong", null, s.label),
                        React.createElement("span", null,
                            s.value == null ? "No data" : `${s.value}% · ${title((_a = s.severity) !== null && _a !== void 0 ? _a : "")}`,
                            " \u00B7 weight ",
                            s.weight,
                            "% \u00B7 evidence ",
                            s.evidenceCount)),
                    React.createElement("small", null, s.reason)); }))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Mode-aware analytics"),
                React.createElement("div", { className: "metric-grid compact-grid" }, modeAnalytics.metrics.map(x => { const low = x.label.toLowerCase(), ratio = typeof x.value === "number" && x.value >= 0 && x.value <= 1 && (low.includes("accuracy") || low.includes("progress") || low.includes("coverage")); return React.createElement(Metric, { key: x.label, label: x.label, value: ratio ? pct(x.value) : x.value == null ? "No data" : String(x.value), detail: modeAnalytics.mode ? title(modeAnalytics.mode) : "No plan" }); }))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Weekly coach"),
                React.createElement("p", { className: "muted" }, readiness.value == null ? "Readiness: No data" : `Readiness: ${readiness.value}% · ${readiness.confidence}`),
                React.createElement("ul", { className: "activity-list" }, coach.items.map(i => React.createElement("li", { key: i.id },
                    React.createElement("strong", null, i.title),
                    React.createElement("span", null, i.reason),
                    i.action && React.createElement("small", null, i.action)))),
                React.createElement("p", { className: "muted" },
                    "Question quota: ",
                    quota.configured == null ? "not configured" : `${quota.completed}/${quota.configured} · ${quota.remaining} remaining`),
                quota.source === "phase" && React.createElement(React.Fragment, null,
                    React.createElement("p", null,
                        React.createElement("strong", null,
                            quota.nextSevenDaysQuestions,
                            " questions in the next seven-day quota"),
                        " \u00B7 phase ",
                        quota.phaseLabel),
                    React.createElement("ul", { className: "activity-list" }, quota.subjects.map(row => React.createElement("li", { key: row.subjectId },
                        React.createElement("div", null,
                            React.createElement("strong", null,
                                row.subjectName,
                                ": ",
                                row.weeklyQuestions),
                            React.createElement("span", null,
                                Math.round(row.priorityShare * 100),
                                "% priority share \u00B7 ",
                                row.phaseQuestionsAnswered,
                                " phase questions \u00B7 accuracy ",
                                row.recentAccuracyPercent == null ? "No data" : `${row.recentAccuracyPercent}%`,
                                " \u00B7 ",
                                row.recurringErrors,
                                " recurring errors \u00B7 ",
                                row.overdueReviews,
                                " overdue reviews")),
                        React.createElement("small", null, "35% exam weight \u00B7 20% under-practice \u00B7 20% accuracy deficit \u00B7 15% recurring errors \u00B7 10% overdue reviews"))))))),
        tab === "performance" && React.createElement("section", { id: "progress-performance", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Unified Evidence Timeline"),
                React.createElement("p", { className: "muted" }, "Newest-first, plan-local, bounded to 30 events. Sessions, planned activities, reviews, questions, mock results, completed goals, note materials and other canonical evidence share one view."),
                React.createElement("ul", { className: "activity-list" },
                    timeline.map(x => React.createElement("li", { key: x.id },
                        React.createElement("div", null,
                            React.createElement("strong", null, title(x.kind)),
                            React.createElement("span", null, x.label)),
                        React.createElement("span", null,
                            x.occurredAt,
                            " \u00B7 ",
                            title(x.source),
                            x.minutes == null ? "" : ` · ${x.minutes} min`,
                            x.count == null ? "" : ` · ${x.count}`,
                            x.percent == null ? "" : ` · ${Math.round(x.percent)}%`))),
                    !timeline.length && React.createElement("li", null, "No evidence recorded yet.")))),
        tab === "goals" && React.createElement("section", { id: "progress-goals", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Goals"),
                !tv && React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (goal.trim()) {
                        run(() => store.addGoal({ name: goal.trim(), metric: "study-minutes", target: 120, period: "weekly" }), "Goal added.");
                        setGoal("");
                    } } },
                    React.createElement("input", { "aria-label": "Weekly goal name", value: goal, onChange: (e) => setGoal(e.target.value), placeholder: "Weekly goal", required: true }),
                    React.createElement("button", { className: "button", type: "submit" }, "Add 120 min goal")),
                tv && React.createElement("p", { className: "tv-inline-note" }, "Goal creation stays on phone/computer; completion and pause/resume remain available on TV."),
                goals.map(g => { var _a; const p = (0, domain_1.deriveGoalProgress)(state, g); return React.createElement("div", { className: "goal-row", key: g.id },
                    React.createElement("div", null,
                        React.createElement("strong", null,
                            g.name,
                            g.completedAt ? " · completed" : ""),
                        React.createElement("span", { className: "muted" }, (_a = p.actual) !== null && _a !== void 0 ? _a : "No data",
                            " / ",
                            g.target,
                            g.completedAt ? ` · ${g.completedAt}` : "")),
                    React.createElement("div", { className: "button-row" },
                        !g.completedAt && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeGoal(g.id), "Goal marked complete and added to the Evidence Timeline.") }, "Mark complete"),
                        React.createElement("button", { className: "button button-quiet", type: "button", disabled: Boolean(g.completedAt), onClick: () => run(() => store.toggleGoal(g.id)) }, g.active ? "Pause" : "Resume"))); }))),
        tab === "tools" && React.createElement("section", { id: "progress-tools", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Content Integrity"),
                    React.createElement("strong", null, integrity.valid ? "Local state references are valid" : "Integrity issue detected"),
                    React.createElement("p", { className: "muted" }, integrity.valid ? `${Object.values(integrity.counts).reduce((a, b) => a + b, 0)} structured records checked.` : integrity.errors[0])),
                tv ? React.createElement(TvContinueElsewhere, { title: "Portable backups & imports", detail: "File selection, encrypted passwords and destructive import application are deliberately excluded from remote-only TV mode." }) : React.createElement(PortablePackageTools, { state: state, store: store, persistence: persistence, portable: portable, setPortable: setPortable, portableStatus: portableStatus, setPortableStatus: setPortableStatus }),
                " ",
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Local state"),
                    React.createElement("p", { className: "muted" }, store.getPersistenceStatus().message),
                    React.createElement("p", { className: "muted" },
                        "Backend: ",
                        persistence.backend),
                    !tv && React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.resetLocalState(), "Sample data restored.") }, "Reset sample data"),
                    tv && React.createElement("p", { className: "tv-inline-note" }, "Destructive reset is unavailable in TV mode.")),
                React.createElement(StorageHealthCard, { state: state, persistence: persistence }),
                tv ? React.createElement(TvContinueElsewhere, { title: "Coach exports & schedule mutations", detail: "Report downloads, smart cleanup and sprint application stay on phone/computer; analytics above remain fully visible here." }) : React.createElement(IntelligenceTools, { state: state, store: store, run: run }),
                " ",
                React.createElement(ReleaseCard, { state: state, persistence: persistence }),
                !tv && React.createElement(CalendarExportTools, { state: state, run: run }),
                " ",
                !tv && React.createElement(PwaTools, null))));
}
function IntelligenceTools({ state, store, run }) {
    const [cleanup, setCleanup] = React.useState(null);
    const [sprint, setSprint] = React.useState(null);
    const runbook = (0, intelligence_1.buildExamRunbook)(state);
    const download = (name, type, bytes) => { const url = URL.createObjectURL(new Blob([bytes], { type })), a = document.createElement("a"); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url); };
    const reportName = () => `studyflow-exam-report-${new Date().toISOString().slice(0, 10)}`;
    const downloadHtml = () => { const report = (0, intelligence_1.buildExamReportSnapshot)(state); download(`${reportName()}.html`, "text/html", (0, intelligence_1.renderExamReportHtml)(report)); };
    const downloadPdf = () => { const report = (0, intelligence_1.buildExamReportSnapshot)(state); download(`${reportName()}.pdf`, "application/pdf", (0, intelligence_1.renderExamReportPdf)(report)); };
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Coach & exam tools"),
        React.createElement("p", { className: "muted" },
            "Runbook: ",
            runbook.status === "ready" ? `${runbook.daysRemaining} days · ${runbook.phase}` : runbook.status === "no-date" ? "No confirmed exam date" : "Available for Concurso plans"),
        runbook.items.map(x => React.createElement("p", { key: x.id },
            React.createElement("strong", null, x.title),
            React.createElement("br", null),
            React.createElement("span", { className: "muted" },
                title(x.window),
                " \u00B7 ",
                x.reason))),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setCleanup((0, intelligence_1.buildContentCleanupPreview)(state)) }, "Preview smart cleanup"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => setSprint((0, intelligence_1.buildStudySprintPreview)(state)) }, "Preview 7-day sprint"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: downloadHtml }, "Export HTML exam report"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: downloadPdf }, "Export PDF exam report")),
        cleanup && React.createElement("div", null,
            React.createElement("p", null,
                React.createElement("strong", null,
                    "Content health ",
                    cleanup.healthScorePercent,
                    "/100"),
                " \u00B7 ",
                cleanup.issues.reduce((n, x) => n + x.count, 0),
                " finding(s)."),
            React.createElement("p", { className: "muted" }, "Preview only. Cleanup actions are non-destructive review/navigation instructions and require your confirmation on the canonical content surface."),
            React.createElement("ul", null, cleanup.issues.map(x => React.createElement("li", { key: x.id },
                React.createElement("strong", null,
                    title(x.kind),
                    " \u00B7 ",
                    title(x.severity),
                    " \u00B7 ",
                    x.count),
                ": ",
                x.label,
                " \u2014 ",
                x.reason,
                x.canonicalValue && React.createElement("small", null,
                    " \u00B7 canonical ",
                    x.canonicalValue))))),
        sprint && React.createElement("div", null,
            React.createElement("p", null,
                React.createElement("strong", null,
                    "7-day sprint \u00B7 ",
                    title(sprint.focus)),
                " \u00B7 ",
                sprint.candidateCount,
                " existing candidate(s) \u00B7 ",
                sprint.mutations.length,
                " executable change(s)."),
            React.createElement("p", { className: "muted" }, "The sprint cannot create IDs, move starts, change status, touch locked activities, or increase duration."),
            React.createElement("ul", null, sprint.mutations.map(m => React.createElement("li", { key: m.activityId },
                m.activityId,
                ": ",
                title(m.currentType),
                " \u2192 ",
                title(m.proposedType),
                " \u00B7 ",
                m.currentDurationMinutes,
                " \u2192 ",
                m.proposedDurationMinutes,
                " min \u00B7 ",
                m.scheduledFor))),
            sprint.executable && React.createElement("button", { className: "button", type: "button", onClick: () => { if (run(() => store.applyStudySprintPreview(sprint), "Seven-day sprint applied without moving or creating activities."))
                    setSprint(null); } }, "Apply sprint preview")));
}
function StorageHealthCard({ state, persistence, allowCleanup = true }) { const [report, setReport] = React.useState(null); const [busy, setBusy] = React.useState(false); const check = async (clean = false) => { setBusy(true); try {
    setReport(clean ? await (0, hardening_1.purgeOrphanAttachments)(state, persistence) : await (0, hardening_1.auditBrowserStorage)(state, persistence));
}
finally {
    setBusy(false);
} }; return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Storage health"),
    React.createElement("p", { className: "muted", role: "status", "aria-live": "polite" }, report ? `${title(report.status)} · ${report.messages.join(" ")}` : "Not checked in this session."),
    report && React.createElement("p", { className: "muted" },
        report.referencedAttachments,
        " referenced \u00B7 ",
        report.storedAttachments,
        " stored \u00B7 ",
        report.missingAttachmentIds.length,
        " missing \u00B7 ",
        report.orphanAttachmentIds.length,
        " orphan"),
    React.createElement("div", { className: "button-row" },
        React.createElement("button", { className: "button button-quiet", type: "button", disabled: busy, onClick: () => void check(false) }, busy ? "Checking…" : "Check storage"),
        allowCleanup && Boolean(report === null || report === void 0 ? void 0 : report.orphanAttachmentIds.length) && React.createElement("button", { className: "button button-quiet", type: "button", disabled: busy, onClick: () => void check(true) }, "Clean orphan files"))); }
function PortablePackageTools({ state, store, persistence, portable, setPortable, portableStatus, setPortableStatus }) {
    var _a, _b, _c;
    const [mode, setMode] = React.useState("replace");
    const [exportPassword, setExportPassword] = React.useState("");
    const [exportConfirm, setExportConfirm] = React.useState("");
    const [importPassword, setImportPassword] = React.useState("");
    const [encryptedFile, setEncryptedFile] = React.useState(null);
    const downloadText = (pkg) => { const url = URL.createObjectURL(new Blob([(0, portable_1.serializePortablePackage)(pkg)], { type: "application/json" })); const a = document.createElement("a"); a.href = url; a.download = (0, portable_1.portablePackageFilename)(state); a.click(); URL.revokeObjectURL(url); };
    const downloadBytes = (bytes, name) => { const url = URL.createObjectURL(new Blob([bytes], { type: "application/octet-stream" })); const a = document.createElement("a"); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url); };
    const exportFull = async () => { try {
        setPortableStatus("Building checksum-verified full package…");
        const pkg = await (0, portable_1.createPortablePackageV2)(state, persistence.attachments);
        downloadText(pkg);
        setPortableStatus(`Portable v2 exported with ${pkg.manifest.attachments.count} attachment payloads.`);
    }
    catch (err) {
        setPortableStatus(err instanceof Error ? err.message : String(err));
    } };
    const exportEncrypted = async () => { try {
        if (exportPassword !== exportConfirm)
            throw new TypeError("Encrypted export passwords do not match");
        if (exportPassword.length < encrypted_portable_1.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH)
            throw new TypeError(`Encrypted StudyFlow exports require at least ${encrypted_portable_1.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH} characters.`);
        setPortableStatus("Building and encrypting authenticated portable v2 package…");
        const pkg = await (0, portable_1.createPortablePackageV2)(state, persistence.attachments);
        const bytes = await (0, encrypted_portable_1.encryptPortableText)((0, portable_1.serializePortablePackage)(pkg), exportPassword);
        downloadBytes(bytes, (0, encrypted_portable_1.encryptedPortableFilename)((0, portable_1.portablePackageFilename)(state)));
        setExportPassword("");
        setExportConfirm("");
        setPortableStatus("Encrypted portable v2 exported with AES-256-GCM + PBKDF2-HMAC-SHA256. The password is not stored.");
    }
    catch (err) {
        setPortableStatus(err instanceof Error ? err.message : String(err));
    } };
    const exportLegacy = () => { const pkg = (0, portable_1.createPortablePackage)(state); downloadText(pkg); setPortableStatus("State-only portable v1 exported for backward compatibility."); };
    const parseText = async (text) => { var _a; const version = (0, portable_1.portablePackageVersion)(text); const parsed = version === 2 ? await (0, portable_1.parsePortablePackageV2)(text) : (0, portable_1.parsePortablePackage)(text); const next = (0, portable_1.buildPortableImportPreview)(state, parsed, { mode }); setPortable(next); setPortableStatus(`Portable v${version} preview · ${next.summary.add} add · ${next.summary.update} update · ${next.summary.remove} remove${((_a = next.attachmentRecords) === null || _a === void 0 ? void 0 : _a.length) ? ` · ${next.attachmentRecords.length} attachment payloads` : ""}.`); };
    const importData = async (e) => { var _a; const file = (_a = e.target.files) === null || _a === void 0 ? void 0 : _a[0]; if (!file)
        return; try {
        const bytes = new Uint8Array(await file.arrayBuffer());
        if ((0, encrypted_portable_1.isEncryptedPortableBytes)(bytes)) {
            setPortable(null);
            setEncryptedFile(file);
            setPortableStatus("Encrypted package selected. Enter its password, then preview. No password is stored.");
            return;
        }
        setEncryptedFile(null);
        await parseText(new TextDecoder().decode(bytes));
    }
    catch (err) {
        setPortable(null);
        setEncryptedFile(null);
        setPortableStatus(err instanceof Error ? err.message : String(err));
    }
    finally {
        e.target.value = "";
    } };
    const previewEncrypted = async () => { if (!encryptedFile)
        return; try {
        const text = await (0, encrypted_portable_1.decryptPortableText)(await encryptedFile.arrayBuffer(), importPassword);
        await parseText(text);
        setEncryptedFile(null);
        setImportPassword("");
    }
    catch (err) {
        setPortable(null);
        setPortableStatus(err instanceof Error ? err.message : String(err));
    } };
    const apply = async () => { var _a; if (!portable)
        return; try {
        if ((_a = portable.attachmentRecords) === null || _a === void 0 ? void 0 : _a.length)
            await (0, portable_1.applyPortableImportWithAttachments)(store, portable, persistence.attachments);
        else
            store.applyPortableImportPreview(portable);
        setPortable(null);
        setPortableStatus(portable.mode === "new-plan" ? "Imported package as a new active plan." : "Imported package replaced local structured state.");
    }
    catch (err) {
        setPortableStatus(err instanceof Error ? err.message : String(err));
    } };
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Portable package v2"),
        React.createElement("p", { className: "muted" }, "Full export includes attachment bytes, MIME metadata and SHA-256 checksums. Encrypted export wraps that complete package in an authenticated versioned envelope; the password never leaves this page or gets persisted."),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button", type: "button", onClick: () => void exportFull() }, "Export full v2"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: exportLegacy }, "Export state-only v1")),
        React.createElement("section", { className: "nested-panel" },
            React.createElement("h3", null, "Encrypted backup"),
            React.createElement("p", { className: "muted" }, "AES-256-GCM \u00B7 PBKDF2-HMAC-SHA256 \u00B7 210,000 iterations \u00B7 random 16-byte salt + 12-byte nonce \u00B7 authenticated envelope header. Password loss is unrecoverable."),
            React.createElement("input", { "aria-label": "Encrypted export password", type: "password", autoComplete: "new-password", minLength: encrypted_portable_1.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH, value: exportPassword, onChange: (e) => setExportPassword(e.target.value), placeholder: "Export password" }),
            React.createElement("input", { "aria-label": "Confirm encrypted export password", type: "password", autoComplete: "new-password", minLength: encrypted_portable_1.ENCRYPTED_PORTABLE_MIN_PASSWORD_LENGTH, value: exportConfirm, onChange: (e) => setExportConfirm(e.target.value), placeholder: "Confirm password" }),
            React.createElement("button", { className: "button button-primary", type: "button", onClick: () => void exportEncrypted() }, "Export encrypted v2")),
        React.createElement("select", { "aria-label": "Portable import mode", value: mode, onChange: (e) => { setMode(e.target.value); setPortable(null); } },
            React.createElement("option", { value: "replace" }, "Replace current local state"),
            React.createElement("option", { value: "new-plan" }, "Import active package plan as new")),
        React.createElement("label", { className: "button button-quiet file-button" },
            "Choose import",
            React.createElement("input", { "aria-label": "Import StudyFlow package", type: "file", accept: ".json,.studyflow.json,.studyflow.enc,application/json,application/octet-stream", onChange: importData })),
        encryptedFile && React.createElement("section", { className: "nested-panel" },
            React.createElement("strong", null, encryptedFile.name),
            React.createElement("input", { "aria-label": "Encrypted import password", type: "password", autoComplete: "current-password", value: importPassword, onChange: (e) => setImportPassword(e.target.value), placeholder: "Backup password" }),
            React.createElement("button", { className: "button", type: "button", onClick: () => void previewEncrypted() }, "Decrypt and preview")),
        React.createElement("p", { className: "muted", role: "status" }, portableStatus),
        portable && React.createElement(React.Fragment, null,
            React.createElement("p", { className: "muted" },
                "Mode: ", (_a = portable.mode) !== null && _a !== void 0 ? _a : "replace",
                " \u00B7 attachments: ", (_c = (_b = portable.attachmentRecords) === null || _b === void 0 ? void 0 : _b.length) !== null && _c !== void 0 ? _c : 0),
            React.createElement("button", { className: "button button-primary", type: "button", onClick: () => void apply() }, "Apply preview")));
}
function ReleaseCard({ state, persistence }) { const readiness = (0, release_1.evaluateReleaseReadiness)(state, persistence.backend); return React.createElement("article", { className: "card" },
    React.createElement("h2", null,
        "StudyFlow Local ",
        release_1.STUDYFLOW_LOCAL_RELEASE.version),
    React.createElement("strong", null, readiness.status === "ready" ? "Release checks ready" : readiness.status === "degraded" ? "Release checks ready with browser limitations" : "Release check blocked"),
    React.createElement("p", { className: "muted" },
        "State schema v",
        release_1.STUDYFLOW_LOCAL_RELEASE.stateSchema,
        " \u00B7 portable format v",
        release_1.STUDYFLOW_LOCAL_RELEASE.portableFormat,
        " \u00B7 local-first"),
    React.createElement("ul", null, readiness.reasons.map((reason) => React.createElement("li", { key: reason }, reason)))); }
function CalendarExportTools({ state, run }) { const [privacy, setPrivacy] = React.useState("full_details"); const [includeCompleted, setIncludeCompleted] = React.useState(false); const feed = React.useMemo(() => (0, calendar_attention_1.buildStudyFlowCalendarExport)(state, { privacyMode: privacy, includeCompleted }), [state, privacy, includeCompleted]); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Calendar export"),
    React.createElement("p", { className: "muted" }, "ATT09 exports an ICS calendar and matching attention reminders. Export is user-initiated; StudyFlow does not silently mutate Google, Outlook, Apple, or system calendars."),
    React.createElement("p", { className: "muted" },
        (0, calendar_attention_1.calendarAttentionSummary)(feed),
        " \u00B7 ",
        feed.diagnostics.completedSuppressedCount,
        " completed hidden \u00B7 ",
        feed.diagnostics.outsideWindowSuppressedCount,
        " outside window"),
    React.createElement("label", null,
        "Privacy",
        React.createElement("select", { "aria-label": "Calendar export privacy", value: privacy, onChange: (e) => setPrivacy(e.target.value) },
            React.createElement("option", { value: "full_details" }, "Full details"),
            React.createElement("option", { value: "private_title" }, "Private title"),
            React.createElement("option", { value: "busy_only" }, "Busy only"))),
    React.createElement("label", { className: "check-row" },
        React.createElement("input", { "aria-label": "Include completed calendar activities", type: "checkbox", checked: includeCompleted, onChange: (e) => setIncludeCompleted(e.target.checked) }),
        "Include completed/cancelled blocks"),
    React.createElement("div", { className: "button-row" },
        React.createElement("button", { className: "button button-primary", type: "button", onClick: () => run(() => (0, calendar_attention_1.downloadStudyFlowCalendarIcs)(feed), "Calendar ICS exported.") }, "Export .ics"),
        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => { var _a; return (_a = navigator.clipboard) === null || _a === void 0 ? void 0 : _a.writeText(feed.icsText); }, "Calendar ICS copied.") }, "Copy ICS"))); }
function PwaTools() { const status = React.useRef(null), install = React.useRef(null); React.useEffect(() => { const handle = (0, pwa_1.registerStudyFlowPwa)({ statusElement: status.current, installButton: install.current }); return () => handle.dispose(); }, []); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Install & offline"),
    React.createElement("p", { id: "pwa-status", ref: status, className: "muted", role: "status", "aria-live": "polite", "aria-atomic": "true" }, "Checking PWA support\u2026"),
    React.createElement("button", { id: "install-app", ref: install, className: "button button-quiet", type: "button", hidden: true }, "Install StudyFlow")); }
