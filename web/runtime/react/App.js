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
const content_import_1 = require("../content_import");
const pwa_1 = require("../pwa");
const hardening_1 = require("../hardening");
const release_1 = require("../release");
const title = (v) => v.replace(/[-_]/g, " ").replace(/\b\w/g, c => c.toUpperCase());
const pct = (v) => v == null ? "No data" : `${Math.round(v * 100)}%`;
const clock = (ms) => { const n = Math.max(0, Math.ceil(ms / 1000)); return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`; };
const modes = [["EXAM_PLAN", "Concurso público"], ["PROGRAMMING_COURSE", "Programming course"], ["LANGUAGE_DECK", "Language deck"], ["READING_STUDY", "Reading study"], ["GENERAL_SKILL", "General skill"]];
function useStudyState(store) { return React.useSyncExternalStore((notify) => store.subscribe(() => notify()), store.getState, store.getState); }
function Tabs({ value, setValue, items, prefix, label }) { const refs = React.useRef([]); const move = (e, index) => { let next = index; if (e.key === "ArrowRight")
    next = (index + 1) % items.length;
else if (e.key === "ArrowLeft")
    next = (index - 1 + items.length) % items.length;
else if (e.key === "Home")
    next = 0;
else if (e.key === "End")
    next = items.length - 1;
else
    return; e.preventDefault(); const id = items[next][0]; setValue(id); refs.current?.[next]?.focus(); }; return React.createElement("nav", { className: "subnav", "aria-label": label, role: "tablist" }, items.map(([id, text], index) => React.createElement("button", { key: id, ref: (node) => { if (refs.current)
        refs.current[index] = node; }, id: `tab-${prefix}-${id}`, type: "button", role: "tab", className: "tab-link", "aria-selected": value === id, "aria-controls": `${prefix}-${id}`, tabIndex: value === id ? 0 : -1, onKeyDown: (e) => move(e, index), onClick: () => setValue(id) }, text))); }
function Metric({ label, value, detail }) { return React.createElement("article", { className: "metric-card" },
    React.createElement("span", null, label),
    React.createElement("strong", null, value),
    React.createElement("small", null, detail)); }
function App({ store, persistence }) {
    const state = useStudyState(store);
    const mainRef = React.useRef(null);
    const [screen, setScreen] = React.useState("today");
    const [planTab, setPlanTab] = React.useState("overview");
    const [practiceTab, setPracticeTab] = React.useState("reviews");
    const [progressTab, setProgressTab] = React.useState("summary");
    const [preview, setPreview] = React.useState(null);
    const [portable, setPortable] = React.useState(null);
    const [portableStatus, setPortableStatus] = React.useState("No package selected.");
    const [questionId, setQuestionId] = React.useState(state.questions[0]?.id ?? null);
    const [toast, setToast] = React.useState(null);
    const [, tick] = React.useState(0);
    React.useEffect(() => { const onHash = () => { const x = location.hash.slice(1); if (["today", "plan", "practice", "progress"].includes(x))
        setScreen(x); }; onHash(); addEventListener("hashchange", onHash); if (!location.hash)
        history.replaceState(null, "", "#today"); return () => removeEventListener("hashchange", onHash); }, []);
    React.useEffect(() => { mainRef.current?.focus({ preventScroll: true }); }, [screen]);
    React.useEffect(() => { const id = setInterval(() => tick(v => v + 1), 1000); return () => clearInterval(id); }, []);
    React.useEffect(() => { if (!toast)
        return; const id = setTimeout(() => setToast(null), 2500); return () => clearTimeout(id); }, [toast]);
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
    return React.createElement(React.Fragment, null,
        React.createElement("a", { className: "skip-link", href: "#main-content" }, "Skip to content"),
        React.createElement("header", { className: "app-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "StudyFlow Local"),
                React.createElement("h1", { id: "screen-title" }, title(screen)),
                React.createElement("p", { className: "muted compact-copy" }, activePlan ? modes.find(([id]) => id === activePlan.mode)?.[1] : "No active plan")),
            React.createElement("span", { className: "status-pill" },
                "v",
                release_1.STUDYFLOW_LOCAL_RELEASE.version,
                " \u00B7 ",
                persistence.backend)),
        React.createElement("main", { id: "main-content", ref: mainRef, tabIndex: -1, "aria-labelledby": "screen-title", className: "app-main" },
            screen === "today" && React.createElement(Today, { state: state, store: store, focus: focus, run: run }),
            " ",
            screen === "plan" && React.createElement(Plan, { state: state, store: store, tab: planTab, setTab: setPlanTab, preview: preview, setPreview: setPreview, persistence: persistence, run: run }),
            " ",
            screen === "practice" && React.createElement(Practice, { state: state, store: store, tab: practiceTab, setTab: setPracticeTab, questionId: questionId, setQuestionId: setQuestionId, run: run }),
            " ",
            screen === "progress" && React.createElement(Progress, { state: state, store: store, tab: progressTab, setTab: setProgressTab, persistence: persistence, portable: portable, setPortable: setPortable, portableStatus: portableStatus, setPortableStatus: setPortableStatus, run: run })),
        React.createElement("nav", { className: "bottom-nav", "aria-label": "Main navigation" }, ["today", "plan", "practice", "progress"].map(id => React.createElement("a", { key: id, href: `#${id}`, "aria-current": screen === id ? "page" : undefined },
            React.createElement("span", null, title(id))))),
        React.createElement("div", { id: "toast-region", className: "toast-region", role: "status", "aria-live": "polite", "aria-atomic": "true" }, toast && React.createElement("div", { className: "toast" }, toast)));
}
function Today({ state, store, focus, run }) { const rec = (0, domain_1.recommendNextActivity)(state); const [name, setName] = React.useState(""); const [note, setNote] = React.useState(focus.note ?? ""); const planned = state.activities.filter(a => a.planId === state.activePlanId && a.queue === "today" && a.status === "planned"); return React.createElement("section", { id: "today", "data-screen": "today", className: "screen-section" },
    React.createElement("article", { className: "hero-card" },
        React.createElement("p", { className: "eyebrow" }, "Next up"),
        React.createElement("h2", null, rec?.activity.title ?? "No queued activity"),
        React.createElement("p", { className: "muted" }, rec?.reason ?? "Add an activity or generate a schedule preview.")),
    React.createElement(ModeToday, { state: state }),
    React.createElement("div", { className: "content-grid two-column" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Focus"),
            React.createElement("div", { className: "focus-clock", "aria-label": `Focus time remaining ${clock(focus.remainingMs)}` }, clock(focus.remainingMs)),
            React.createElement("textarea", { "aria-label": "Focus session note", value: note, onChange: (e) => setNote(e.target.value), placeholder: "Session note" }),
            React.createElement("div", { className: "button-row" },
                focus.status !== "running" && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => run(() => store.startFocus({ note }), "Focus started.") }, focus.status === "paused" ? "Resume" : "Start"),
                focus.status === "running" && React.createElement("button", { className: "button", type: "button", onClick: () => run(() => store.pauseFocus(), "Focus paused.") }, "Pause"),
                focus.status !== "idle" && React.createElement(React.Fragment, null,
                    React.createElement("button", { className: "button", type: "button", onClick: () => run(() => store.completeFocus({ note }), "Focus completed and evidenced.") }, "Complete"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.cancelFocus({ note }), "Focus cancelled.") }, "Cancel")))),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Quick Add"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim()) {
                    run(() => store.addActivity({ title: name.trim(), kind: "study", durationMinutes: 25 }), "Activity added locally.");
                    setName("");
                } } },
                React.createElement("input", { "aria-label": "Activity title", value: name, onChange: (e) => setName(e.target.value), placeholder: "Activity title", required: true }),
                React.createElement("button", { className: "button", type: "submit" }, "Add 25 min activity")))),
    React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Today queue"),
        React.createElement("ul", { className: "activity-list" },
            planned.map(a => React.createElement("li", { key: a.id },
                React.createElement("strong", null, a.title),
                React.createElement("span", null,
                    a.durationMinutes,
                    " min \u00B7 ",
                    title(a.kind)),
                React.createElement("button", { type: "button", className: "button button-quiet", onClick: () => run(() => store.startFocus({ activityId: a.id, durationMinutes: a.durationMinutes }), "Focus started.") }, "Focus"))),
            !planned.length && React.createElement("li", null, "No planned activities remain today.")))); }
function ModeToday({ state }) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return null; if (plan.mode === "EXAM_PLAN") {
    const g = (0, modes_1.deriveFinalStretchGuidance)(plan);
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null,
            "Concurso mode \u00B7 ",
            g.band === "unknown" ? "exam date not confirmed" : `${g.daysRemaining} days · ${g.band}`),
        React.createElement("span", { className: "muted" }, "Final-stretch guidance is derived only from a confirmed date."));
} if (plan.mode === "PROGRAMMING_COURSE")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null,
            plan.configuration.programming?.language,
            " course"),
        React.createElement("span", { className: "muted" }, "Exercises remain local; browser StudyFlow does not execute arbitrary shell commands.")); if (plan.mode === "LANGUAGE_DECK")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null,
            plan.configuration.language?.language,
            " language deck"),
        React.createElement("span", { className: "muted" }, "Recognition, production, fill-in, usage, mistake spotting and sentence creation.")); if (plan.mode === "READING_STUDY")
    return React.createElement("article", { className: "card mode-banner" },
        React.createElement("strong", null, "Reading study"),
        React.createElement("span", { className: "muted" }, "Progress is recorded from explicit reading updates.")); return React.createElement("article", { className: "card mode-banner" },
    React.createElement("strong", null, "General skill"),
    React.createElement("span", { className: "muted" }, "Drill outcomes become evidence only when you record them.")); }
function TopicTree({ state, store, subjectId, parentTopicId = null, run }) {
    const topics = state.topics.filter(t => t.subjectId === subjectId && (t.parentTopicId ?? null) === parentTopicId).slice().sort((a, b) => a.order - b.order || a.name.localeCompare(b.name));
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
function Plan({ state, store, tab, setTab, preview, setPreview, persistence, run }) {
    const inv = (0, domain_1.derivePlanInventory)(state);
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const activePlans = state.plans.filter(p => p.status !== "archived");
    const archivedPlans = state.plans.filter(p => p.status === "archived");
    const [newPlanName, setNewPlanName] = React.useState("");
    const [newPlanMode, setNewPlanMode] = React.useState("GENERAL_SKILL");
    const [rename, setRename] = React.useState(plan?.name ?? "");
    React.useEffect(() => setRename(plan?.name ?? ""), [plan?.id, plan?.name]);
    const [subject, setSubject] = React.useState("");
    const [topic, setTopic] = React.useState("");
    const [subjectId, setSubjectId] = React.useState(state.subjects.find(s => s.planId === state.activePlanId)?.id ?? "");
    const [parentTopicId, setParentTopicId] = React.useState("");
    const subjects = state.subjects.filter(s => s.planId === state.activePlanId).slice().sort((a, b) => a.order - b.order);
    const materials = state.materials.filter(m => m.planId === state.activePlanId);
    const topicChoices = state.topics.filter(t => t.planId === state.activePlanId && t.subjectId === subjectId && t.status !== "archived").slice().sort((a, b) => a.order - b.order);
    React.useEffect(() => { if (!subjects.some(s => s.id === subjectId)) {
        setSubjectId(subjects[0]?.id ?? "");
        setParentTopicId("");
    } }, [state.activePlanId, subjects.length]);
    const [importText, setImportText] = React.useState("");
    const [importFormat, setImportFormat] = React.useState("txt");
    const [importMode, setImportMode] = React.useState("merge");
    const [importTarget, setImportTarget] = React.useState("active");
    const [importPlanName, setImportPlanName] = React.useState("");
    const [contentPreview, setContentPreview] = React.useState(null);
    React.useEffect(() => setContentPreview(null), [state.activePlanId, importText, importFormat, importMode, importTarget, importPlanName]);
    const previewImport = () => run(() => { const next = store.previewPlanContentImport(importText, { format: importFormat, mode: importMode, target: importTarget, planName: importTarget === "new-plan" ? importPlanName : undefined, planMode: plan?.mode ?? newPlanMode }); setContentPreview(next); return next; }, "Plan Content import preview generated.");
    const loadImportFile = async (e) => { const file = e.target.files?.[0]; if (!file)
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
    return React.createElement("section", { id: "plan", "data-screen": "plan", className: "screen-section" },
        React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "plan", label: "Plan sections", items: [["overview", "Overview"], ["schedule", "Schedule"], ["content", "Content"], ["library", "Library"]] }),
        tab === "overview" && React.createElement("section", { id: "plan-overview", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
            React.createElement("div", { className: "metric-grid" },
                React.createElement(Metric, { label: "Subjects", value: String(inv.subjects), detail: `${inv.topics} active topics` }),
                React.createElement(Metric, { label: "Planned", value: `${inv.plannedMinutes} min`, detail: `${inv.activities} activities` })),
            React.createElement("div", { className: "content-grid two-column" },
                React.createElement("article", { className: "card" },
                    React.createElement("h2", null, "Plan workspace"),
                    React.createElement("select", { "aria-label": "Active study plan", value: state.activePlanId ?? "", onChange: (e) => run(() => store.switchPlan(e.target.value), "Active plan switched.") }, activePlans.map(item => React.createElement("option", { key: item.id, value: item.id }, item.name))),
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
                React.createElement("select", { "aria-label": "Study mode", value: plan?.mode ?? "GENERAL_SKILL", onChange: (e) => run(() => store.setPlanMode(e.target.value), "Study mode updated.") }, modes.map(([id, label]) => React.createElement("option", { key: id, value: id }, label)))),
            React.createElement(ModeWorkspace, { state: state, store: store, run: run }),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Study cycle"),
                React.createElement("ol", null, state.studyCycle.subjectIds.map((id, i) => React.createElement("li", { key: id },
                    state.subjects.find(s => s.id === id)?.name ?? id,
                    i === state.studyCycle.cursor ? " · current" : ""))),
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
                        React.createElement("ul", null, contentPreview.operations.map((op, index) => React.createElement("li", { key: index },
                            title(op.action),
                            " \u00B7 ",
                            [op.subjectName, ...(op.topicPath ?? [])].join(" > "))))),
                    React.createElement("button", { className: "button button-primary", type: "button", disabled: !contentPreview.ready, onClick: () => { if (run(() => store.applyPlanContentImportPreview(contentPreview), contentPreview.target === "new-plan" ? "New plan imported and activated." : "Plan Content import applied.")) {
                            setContentPreview(null);
                            setImportText("");
                        } } }, "Apply import preview"))),
            subjects.map(s => React.createElement("article", { className: "card", key: s.id },
                React.createElement("div", { className: "card-heading-row" },
                    React.createElement("div", null,
                        React.createElement("h2", null, s.name),
                        React.createElement("p", { className: "muted" },
                            "Weight ",
                            s.weight ?? "No data",
                            " \u00B7 ",
                            s.archived ? "Archived" : "Active")),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateSubject(s.id, { archived: !s.archived }), s.archived ? "Subject restored." : "Subject archived.") }, s.archived ? "Restore" : "Archive"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateSubject(s.id, { notes: s.notes ? null : "Review this subject" }), "Subject notes updated.") }, "Notes"))),
                React.createElement(TopicTree, { state: state, store: store, subjectId: s.id, run: run })))),
        tab === "library" && React.createElement(Library, { materials: materials, store: store, persistence: persistence, run: run }));
}
function minuteOfDay(value) { const [h, m] = value.split(":").map(Number); return Math.max(0, Math.min(1439, (Number.isFinite(h) ? h : 0) * 60 + (Number.isFinite(m) ? m : 0))); }
function minuteClock(value) { if (value == null)
    return "Any time"; return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`; }
function ScheduleWorkspace({ state, store, preview, setPreview, run }) {
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const config = plan?.configuration.schedule;
    const [capacity, setCapacity] = React.useState(String(config?.dailyCapacityMinutes ?? 120));
    const [weekday, setWeekday] = React.useState("1");
    const [start, setStart] = React.useState("18:00");
    const [end, setEnd] = React.useState("20:00");
    const [fixedTitle, setFixedTitle] = React.useState("");
    const [fixedDate, setFixedDate] = React.useState("");
    const [fixedStart, setFixedStart] = React.useState("18:00");
    const [fixedDuration, setFixedDuration] = React.useState("60");
    React.useEffect(() => { setCapacity(String(plan?.configuration.schedule.dailyCapacityMinutes ?? 120)); setPreview(null); }, [plan?.id]);
    const scheduled = state.activities.filter(a => a.planId === state.activePlanId && a.status === "planned").slice().sort((a, b) => String(a.scheduledFor ?? "9999").localeCompare(String(b.scheduledFor ?? "9999")) || a.title.localeCompare(b.title));
    const saveCapacity = () => run(() => store.setScheduleConfiguration({ dailyCapacityMinutes: Number(capacity) }), "Daily schedule capacity saved.");
    const addWindow = () => run(() => store.setScheduleConfiguration({ availability: [...(config?.availability ?? []), { id: `window-${Date.now()}`, weekday: Number(weekday), startMinute: minuteOfDay(start), endMinute: minuteOfDay(end) }] }), "Availability window added.");
    const addFixed = () => { if (!fixedTitle.trim() || !fixedDate)
        return; const result = run(() => store.setScheduleConfiguration({ fixedBlocks: [...(config?.fixedBlocks ?? []), { id: `fixed-${Date.now()}`, title: fixedTitle.trim(), date: fixedDate, weekday: null, startMinute: minuteOfDay(fixedStart), durationMinutes: Number(fixedDuration) }] }), "Fixed block added."); if (result)
        setFixedTitle(""); };
    return React.createElement("section", { id: "plan-schedule", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
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
                    React.createElement("input", { "aria-label": "Availability end time", type: "time", value: end, onChange: (e) => setEnd(e.target.value) }),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: addWindow }, "Add availability window")),
                React.createElement("ul", { className: "activity-list" },
                    (config?.availability ?? []).map(w => React.createElement("li", { key: w.id },
                        React.createElement("strong", null, ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][w.weekday]),
                        React.createElement("span", null,
                            minuteClock(w.startMinute),
                            "\u2013",
                            minuteClock(w.endMinute)),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setScheduleConfiguration({ availability: (config?.availability ?? []).filter(x => x.id !== w.id) }), "Availability removed.") }, "Remove"))),
                    !(config?.availability ?? []).length && React.createElement("li", null, "Capacity-only scheduling; no clock windows configured."))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Fixed commitments"),
                React.createElement("div", { className: "stack-form" },
                    React.createElement("input", { "aria-label": "Fixed block title", value: fixedTitle, onChange: (e) => setFixedTitle(e.target.value), placeholder: "Work / class / appointment" }),
                    React.createElement("input", { "aria-label": "Fixed block date", type: "date", value: fixedDate, onChange: (e) => setFixedDate(e.target.value) }),
                    React.createElement("input", { "aria-label": "Fixed block start time", type: "time", value: fixedStart, onChange: (e) => setFixedStart(e.target.value) }),
                    React.createElement("input", { "aria-label": "Fixed block duration minutes", type: "number", min: "5", max: "1440", value: fixedDuration, onChange: (e) => setFixedDuration(e.target.value) }),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: addFixed }, "Add fixed block")),
                React.createElement("ul", { className: "activity-list" },
                    (config?.fixedBlocks ?? []).map(b => React.createElement("li", { key: b.id },
                        React.createElement("strong", null, b.title),
                        React.createElement("span", null,
                            b.date ?? `weekday ${b.weekday}`,
                            " \u00B7 ",
                            minuteClock(b.startMinute),
                            " \u00B7 ",
                            b.durationMinutes,
                            " min"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setScheduleConfiguration({ fixedBlocks: (config?.fixedBlocks ?? []).filter(x => x.id !== b.id) }), "Fixed block removed.") }, "Remove"))),
                    !(config?.fixedBlocks ?? []).length && React.createElement("li", null, "No fixed commitments configured.")))),
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
                React.createElement("ul", null, preview.operations.map((op, i) => React.createElement("li", { key: i },
                    op.action === "move" ? `Move ${op.activityId} → ${op.to} ${minuteClock(op.toStartMinute)}` : `Add ${op.activity?.title ?? "activity"} · ${op.scheduledFor} ${minuteClock(op.scheduledStartMinute)}`,
                    " \u00B7 ",
                    op.reason))),
                preview.unscheduled.length > 0 && React.createElement("details", null,
                    React.createElement("summary", null, "Unscheduled work"),
                    React.createElement("ul", null, preview.unscheduled.map((x, i) => React.createElement("li", { key: i },
                        x.title,
                        " \u00B7 ",
                        x.durationMinutes,
                        " min \u00B7 ",
                        x.reason)))),
                React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applySchedulePreview(preview), "Schedule preview applied."))
                        setPreview(null); } }, "Apply preview")) : React.createElement("p", { className: "muted" }, "Preview first; no schedule changes are silent.")),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Planned activities"),
            React.createElement("ul", { className: "activity-list" }, scheduled.map(a => React.createElement("li", { key: a.id },
                React.createElement("div", null,
                    React.createElement("strong", null, a.title),
                    React.createElement("span", { className: "muted" },
                        a.scheduledFor ?? "Unscheduled",
                        " \u00B7 ",
                        minuteClock(a.scheduledStartMinute),
                        " \u00B7 ",
                        a.durationMinutes,
                        " min",
                        a.scheduleProvenance?.reason ? ` · ${a.scheduleProvenance.reason}` : "")),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.setActivityLocked(a.id, !a.locked), a.locked ? "Schedule placement unlocked." : "Schedule placement locked.") }, a.locked ? "Unlock" : "Lock"))))));
}
function PublicExamWorkspace({ state, store, run }) {
    const plan = state.plans.find(p => p.id === state.activePlanId);
    const c = plan.configuration.publicExam;
    const [org, setOrg] = React.useState(c.target.organization ?? "");
    const [role, setRole] = React.useState(c.target.role ?? "");
    const [board, setBoard] = React.useState(c.target.board ?? "");
    const [examDate, setExamDate] = React.useState(c.target.examDate ?? "");
    const [dateStatus, setDateStatus] = React.useState(c.target.dateStatus);
    const [stage, setStage] = React.useState("");
    const [notice, setNotice] = React.useState("");
    const [noticePreview, setNoticePreview] = React.useState(null);
    const [law, setLaw] = React.useState("");
    const [reviewDays, setReviewDays] = React.useState(c.reviewIntervalsDays.join(", "));
    const dashboard = (0, modes_1.derivePublicExamDashboard)(state);
    const guidance = (0, modes_1.deriveFinalStretchGuidance)(plan);
    return React.createElement("div", { className: "content-grid two-column" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Concurso target"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); run(() => store.updatePublicExamTarget({ organization: org || null, role: role || null, board: board || null, examDate: examDate || null, dateStatus }), "Concurso target saved."); } },
                React.createElement("input", { "aria-label": "Organization", value: org, onChange: (e) => setOrg(e.target.value), placeholder: "Organization" }),
                React.createElement("input", { "aria-label": "Role or cargo", value: role, onChange: (e) => setRole(e.target.value), placeholder: "Role / cargo" }),
                React.createElement("input", { "aria-label": "Exam board or banca", value: board, onChange: (e) => setBoard(e.target.value), placeholder: "Exam board / banca" }),
                React.createElement("input", { "aria-label": "Exam date", type: "date", value: examDate, onChange: (e) => setExamDate(e.target.value) }),
                React.createElement("select", { "aria-label": "Exam date status", value: dateStatus, onChange: (e) => setDateStatus(e.target.value) },
                    React.createElement("option", { value: "unknown" }, "Date unknown"),
                    React.createElement("option", { value: "expected" }, "Expected date"),
                    React.createElement("option", { value: "confirmed" }, "Confirmed date")),
                React.createElement("button", { className: "button", type: "submit" }, "Save target")),
            React.createElement("p", { className: "muted" }, "Identity is accepted only when organization, role and board are all filled or all blank.")),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Concurso dashboard"),
            React.createElement("div", { className: "metric-grid compact-grid" },
                React.createElement(Metric, { label: "Edital coverage", value: pct(dashboard?.coverage ?? null), detail: `${dashboard?.noticeRevisionCount ?? 0} accepted revisions` }),
                React.createElement(Metric, { label: "Question accuracy", value: pct(dashboard?.accuracy ?? null), detail: `${dashboard?.reviewBacklog ?? 0} due reviews` }),
                React.createElement(Metric, { label: "Cutoff gap", value: dashboard?.cutoffGap == null ? "No data" : `${dashboard.cutoffGap.toFixed(1)} pp`, detail: "Only after a recorded mock + cutoff" })),
            React.createElement("p", null,
                React.createElement("strong", null, "Final stretch:"),
                " ",
                guidance.band === "unknown" ? "No confirmed exam date" : `${guidance.daysRemaining} days · ${guidance.band}`)),
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
                s.status)))),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Edital revision"),
            React.createElement("textarea", { "aria-label": "Edital revision text", value: notice, onChange: (e) => { setNotice(e.target.value); setNoticePreview(null); }, placeholder: "Disciplina: Direito Constitucional\n- Direitos fundamentais\n- Administração pública" }),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button", type: "button", onClick: () => { const p = run(() => store.previewExamNotice(notice)); if (p)
                        setNoticePreview(p); } }, "Preview edital"),
                noticePreview && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applyExamNoticePreview(noticePreview), "Edital revision applied; removed content archived."))
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
            React.createElement("p", { className: "muted" },
                c.legislation.length,
                " local legislation documents.")),
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
function ProgrammingWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.programming; const [name, setName] = React.useState(""); const [prompt, setPrompt] = React.useState(""); return React.createElement("article", { className: "card" },
    React.createElement("h2", null,
        "Programming course \u00B7 ",
        cfg.language),
    React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (name.trim() && prompt.trim()) {
            run(() => store.addProgrammingExercise({ title: name.trim(), prompt: prompt.trim(), starterCode: "// Start here" }), "Coding exercise added.");
            setName("");
            setPrompt("");
        } } },
        React.createElement("input", { "aria-label": "Exercise title", value: name, onChange: (e) => setName(e.target.value), placeholder: "Exercise title" }),
        React.createElement("textarea", { "aria-label": "Exercise prompt", value: prompt, onChange: (e) => setPrompt(e.target.value), placeholder: "Exercise prompt" }),
        React.createElement("button", { className: "button", type: "submit" }, "Add exercise")),
    cfg.exercises.map(x => React.createElement("div", { className: "mode-item", key: x.id },
        React.createElement("strong", null, x.title),
        React.createElement("span", null,
            title(x.difficulty),
            " \u00B7 ",
            title(x.status)),
        React.createElement("pre", null,
            React.createElement("code", null, x.starterCode)),
        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.recordProgrammingExercise(x.id, { completed: true }), "Exercise completion evidenced.") }, "Mark completed"))),
    React.createElement("p", { className: "muted" }, "Browser mode stores safe exercise metadata only; arbitrary shell execution is not exposed.")); }
function LanguageWorkspace({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); const cfg = plan.configuration.language; const [expression, setExpression] = React.useState(""); const [meaning, setMeaning] = React.useState(""); return React.createElement("article", { className: "card" },
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
        x.meaning))),
    React.createElement("p", { className: "muted" }, "Review directions: recognition \u00B7 production \u00B7 fill in the blank \u00B7 natural usage \u00B7 mistake spotting \u00B7 sentence creation.")); }
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
function Library({ materials, store, persistence, run }) { const add = async (e) => { const file = e.target.files?.[0]; if (!file || !persistence.attachments)
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
} }; return React.createElement("section", { id: "plan-library", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
    React.createElement("article", { className: "card" },
        React.createElement("div", { className: "card-heading-row" },
            React.createElement("h2", null, "Local materials"),
            React.createElement("label", { className: "button button-quiet file-button" },
                "Add file",
                React.createElement("input", { "aria-label": "Local material file", type: "file", onChange: (e) => add(e).catch(err => console.error(err)) }))),
        React.createElement("ul", { className: "activity-list" },
            materials.map(m => React.createElement("li", { key: m.id },
                React.createElement("strong", null, m.title),
                React.createElement("span", null, m.mimeType ?? title(m.kind)),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deleteMaterial(m.id), "Material removed.") }, "Delete"))),
            !materials.length && React.createElement("li", null, "No local materials yet.")))); }
function Practice({ state, store, tab, setTab, questionId, setQuestionId, run }) { const due = (0, domain_1.selectDueReviews)(state), errors = (0, domain_1.selectOpenErrors)(state), stats = (0, domain_1.deriveQuestionStats)(state), questions = state.questions.filter(q => q.planId === state.activePlanId), q = questions.find(x => x.id === questionId) ?? questions[0] ?? null; const [answer, setAnswer] = React.useState(""); const [prompt, setPrompt] = React.useState(""); const [board, setBoard] = React.useState(""); const [year, setYear] = React.useState(""); const plan = state.plans.find(p => p.id === state.activePlanId); return React.createElement("section", { id: "practice", "data-screen": "practice", className: "screen-section" },
    React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "practice", label: "Practice sections", items: [["reviews", "Reviews"], ["questions", "Questions"], ["tests", "Tests"], ["errors", "Errors"]] }),
    tab === "reviews" && React.createElement("section", { id: "practice-reviews", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null,
                "Due reviews \u00B7 ",
                due.length),
            React.createElement("ul", { className: "activity-list" },
                due.map(r => React.createElement("li", { key: r.id },
                    React.createElement("strong", null, state.topics.find(t => t.id === r.topicId)?.name ?? title(r.sourceType ?? "Review")),
                    React.createElement("span", null, r.intervalDays ? `${r.intervalDays}d interval · stage ${r.intervalIndex + 1}` : "Legacy review"),
                    React.createElement("div", { className: "button-row" },
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeReview(r.id, { rating: 1 }), "Review recorded; progression reset.") }, "Again"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeReview(r.id, { rating: 3 }), "Review completed; next interval scheduled.") }, "Good"),
                        React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeReview(r.id, { rating: 5 }), "Review completed; next interval scheduled.") }, "Easy")))),
                !due.length && React.createElement("li", null, "No reviews due.")))),
    tab === "questions" && React.createElement("section", { id: "practice-questions", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Question practice"),
                React.createElement("p", { className: "muted" }, stats.accuracy == null ? "No graded accuracy" : `${Math.round(stats.accuracy * 100)}% graded accuracy`),
                React.createElement("select", { "aria-label": "Practice question", value: q?.id ?? "", onChange: (e) => { setQuestionId(e.target.value); setAnswer(""); } }, questions.map(x => React.createElement("option", { key: x.id, value: x.id }, x.prompt))),
                q && React.createElement(React.Fragment, null,
                    React.createElement("h3", null, q.prompt),
                    q.examBoard && React.createElement("p", { className: "muted" },
                        "Banca: ",
                        q.examBoard,
                        q.year ? ` · ${q.year}` : ""),
                    q.choices.length ? q.choices.map(c => React.createElement("label", { className: "choice", key: c },
                        React.createElement("input", { "aria-label": c, type: "radio", name: "answer", value: c, checked: answer === c, onChange: (e) => setAnswer(e.target.value) }),
                        React.createElement("span", null, c))) : React.createElement("input", { "aria-label": "Question answer", value: answer, onChange: (e) => setAnswer(e.target.value), placeholder: "Type answer" }),
                    React.createElement("button", { className: "button button-primary", type: "button", disabled: !answer.trim(), onClick: () => { if (run(() => store.answerQuestion(q.id, answer), "Attempt recorded."))
                            setAnswer(""); } }, "Submit"))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Add question"),
                React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (prompt.trim()) {
                        run(() => store.addQuestion({ prompt: prompt.trim(), examBoard: board || null, year: year ? Number(year) : null }), "Ungraded question added.");
                        setPrompt("");
                    } } },
                    React.createElement("textarea", { "aria-label": "Question prompt", value: prompt, onChange: (e) => setPrompt(e.target.value), required: true }),
                    plan?.mode === "EXAM_PLAN" && React.createElement(React.Fragment, null,
                        React.createElement("input", { "aria-label": "Question exam board", value: board, onChange: (e) => setBoard(e.target.value), placeholder: "Banca (optional)" }),
                        React.createElement("input", { "aria-label": "Question year", type: "number", value: year, onChange: (e) => setYear(e.target.value), placeholder: "Year" })),
                    React.createElement("button", { className: "button", type: "submit" }, "Save"))))),
    tab === "tests" && React.createElement("section", { id: "practice-tests", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement(ModePractice, { state: state, store: store, run: run })),
    tab === "errors" && React.createElement("section", { id: "practice-errors", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null,
                "Error Notebook \u00B7 ",
                errors.length),
            React.createElement("ul", { className: "activity-list" },
                errors.map(x => React.createElement("li", { key: x.id },
                    React.createElement("strong", null, state.questions.find(q => q.id === x.questionId)?.prompt ?? "Error"),
                    React.createElement("span", null,
                        title(x.category),
                        " \u00B7 recurrence ",
                        x.recurrenceCount),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.resolveError(x.id), "Error resolved; history retained.") }, "Resolve"))),
                !errors.length && React.createElement("li", null, "No open errors."))))); }
function MockExamStudio({ state, store, run }) { const [preview, setPreview] = React.useState(null); const [answers, setAnswers] = React.useState({}); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Mock Exam Studio"),
    React.createElement("button", { className: "button", type: "button", onClick: () => setPreview((0, modes_1.previewMockExam)(state)) }, "Preview mock"),
    preview && React.createElement(React.Fragment, null,
        React.createElement("p", { className: "muted" }, preview.ready ? `${preview.questionIds.length} questions ready` : `Cannot create: ${preview.deficits.join("; ")}`),
        preview.ready && preview.questionIds.map(id => { const q = state.questions.find(x => x.id === id); if (!q)
            return null; return React.createElement("div", { className: "mode-item", key: id },
            React.createElement("strong", null, q.prompt),
            q.choices.map(c => React.createElement("label", { className: "choice", key: c },
                React.createElement("input", { "aria-label": c, type: "radio", name: `mock-${id}`, checked: answers[id] === c, onChange: () => setAnswers({ ...answers, [id]: c }) }),
                c))); }),
        preview.ready && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.recordMockExam(preview, answers), "Mock exam scored and evidenced.")) {
                setPreview(null);
                setAnswers({});
            } } }, "Submit mock"))); }
function ModePractice({ state, store, run }) { const plan = state.plans.find(p => p.id === state.activePlanId); if (!plan)
    return null; if (plan.mode === "EXAM_PLAN")
    return React.createElement(MockExamStudio, { state: state, store: store, run: run }); if (plan.mode === "PROGRAMMING_COURSE")
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Programming practice"),
        plan.configuration.programming.exercises.map(x => React.createElement("div", { className: "mode-item", key: x.id },
            React.createElement("strong", null, x.title),
            React.createElement("span", null, title(x.status)),
            React.createElement("button", { className: "button", type: "button", onClick: () => run(() => store.recordProgrammingExercise(x.id, { completed: false }), "Exercise attempt evidenced.") }, "Record attempt"))),
        !plan.configuration.programming.exercises.length && React.createElement("p", null, "No exercises yet.")); if (plan.mode === "LANGUAGE_DECK")
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Language review modes"),
        plan.configuration.language.expressions.map(x => React.createElement("div", { className: "mode-item", key: x.id },
            React.createElement("strong", null, x.expression),
            React.createElement("span", null, x.meaning),
            React.createElement("div", { className: "button-row" },
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.recordLanguageReview(x.id, "recognition", 4), "Recognition review evidenced.") }, "Recognition \u2713"),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.recordLanguageReview(x.id, "sentence-creation", 3), "Sentence creation saved for manual review.") }, "Sentence creation"))))); if (plan.mode === "READING_STUDY")
    return React.createElement("article", { className: "card" },
        React.createElement("h2", null, "Reading practice"),
        React.createElement("p", { className: "muted" }, "Use Plan \u2192 Overview to record explicit reading progress; no passive progress is inferred.")); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Skill practice"),
    React.createElement("p", { className: "muted" }, "Record drill outcomes from Plan \u2192 Overview so only explicit results become evidence.")); }
function Progress({ state, store, tab, setTab, persistence, portable, setPortable, portableStatus, setPortableStatus, run }) { const m = (0, domain_1.deriveProgressMetrics)(state), timeline = (0, domain_1.buildEvidenceTimeline)(state, { limit: 30 }), integrity = (0, domain_1.buildContentIntegrityReport)(state), goals = state.goals.filter(g => g.planId === state.activePlanId), modeAnalytics = (0, modes_1.deriveModeAnalytics)(state); const [goal, setGoal] = React.useState(""); return React.createElement("section", { id: "progress", "data-screen": "progress", className: "screen-section" },
    React.createElement(Tabs, { value: tab, setValue: setTab, prefix: "progress", label: "Progress sections", items: [["summary", "Summary"], ["performance", "Performance"], ["goals", "Goals"], ["tools", "Tools"]] }),
    tab === "summary" && React.createElement("section", { id: "progress-summary", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("div", { className: "metric-grid" },
            React.createElement(Metric, { label: "Readiness", value: m.readiness == null ? "No data" : `${m.readiness}%`, detail: m.readinessReason }),
            React.createElement(Metric, { label: "Study this week", value: `${m.weeklyStudyMinutes} min`, detail: "Completed sessions" }),
            React.createElement(Metric, { label: "Accuracy", value: pct(m.accuracy), detail: `${m.gradedAttempts} graded attempts` }),
            React.createElement(Metric, { label: "Coverage", value: pct(m.topicCoverage), detail: `${m.coveredTopics}/${m.activeTopics} evidenced topics` })),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Mode-aware analytics"),
            React.createElement("div", { className: "metric-grid compact-grid" }, modeAnalytics.metrics.map(x => React.createElement(Metric, { key: x.label, label: x.label, value: typeof x.value === "number" && x.value >= 0 && x.value <= 1 && x.label.toLowerCase().includes("accuracy") || typeof x.value === "number" && x.value >= 0 && x.value <= 1 && x.label.toLowerCase().includes("progress") || typeof x.value === "number" && x.value >= 0 && x.value <= 1 && x.label.toLowerCase().includes("coverage") ? pct(x.value) : x.value == null ? "No data" : String(x.value), detail: modeAnalytics.mode ? title(modeAnalytics.mode) : "No plan" }))))),
    tab === "performance" && React.createElement("section", { id: "progress-performance", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Evidence Timeline"),
            React.createElement("ul", { className: "activity-list" },
                timeline.map(x => React.createElement("li", { key: x.id },
                    React.createElement("strong", null, title(x.kind)),
                    React.createElement("span", null,
                        x.observedAt,
                        " \u00B7 ",
                        title(x.provenance)))),
                !timeline.length && React.createElement("li", null, "No evidence recorded yet.")))),
    tab === "goals" && React.createElement("section", { id: "progress-goals", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Goals"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (goal.trim()) {
                    run(() => store.addGoal({ name: goal.trim(), metric: "study-minutes", target: 120, period: "weekly" }), "Goal added.");
                    setGoal("");
                } } },
                React.createElement("input", { "aria-label": "Weekly goal name", value: goal, onChange: (e) => setGoal(e.target.value), placeholder: "Weekly goal", required: true }),
                React.createElement("button", { className: "button", type: "submit" }, "Add 120 min goal")),
            goals.map(g => { const p = (0, domain_1.deriveGoalProgress)(state, g); return React.createElement("div", { className: "goal-row", key: g.id },
                React.createElement("div", null,
                    React.createElement("strong", null, g.name),
                    React.createElement("span", { className: "muted" },
                        p.actual ?? "No data",
                        " / ",
                        g.target)),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.toggleGoal(g.id)) }, g.active ? "Pause" : "Resume")); }))),
    tab === "tools" && React.createElement("section", { id: "progress-tools", className: "tab-panel", role: "tabpanel", tabIndex: 0 },
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Content Integrity"),
                React.createElement("strong", null, integrity.valid ? "Local state references are valid" : "Integrity issue detected"),
                React.createElement("p", { className: "muted" }, integrity.valid ? `${Object.values(integrity.counts).reduce((a, b) => a + b, 0)} structured records checked.` : integrity.errors[0])),
            React.createElement(PortablePackageTools, { state: state, store: store, persistence: persistence, portable: portable, setPortable: setPortable, portableStatus: portableStatus, setPortableStatus: setPortableStatus }),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Local state"),
                React.createElement("p", { className: "muted" }, store.getPersistenceStatus().message),
                React.createElement("p", { className: "muted" },
                    "Backend: ",
                    persistence.backend),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.resetLocalState(), "Sample data restored.") }, "Reset sample data")),
            React.createElement(StorageHealthCard, { state: state, persistence: persistence }),
            React.createElement(ReleaseCard, { state: state, persistence: persistence }),
            React.createElement(PwaTools, null)))); }
function StorageHealthCard({ state, persistence }) { const [report, setReport] = React.useState(null); const [busy, setBusy] = React.useState(false); const check = async (clean = false) => { setBusy(true); try {
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
        Boolean(report?.orphanAttachmentIds.length) && React.createElement("button", { className: "button button-quiet", type: "button", disabled: busy, onClick: () => void check(true) }, "Clean orphan files"))); }
function PortablePackageTools({ state, store, persistence, portable, setPortable, portableStatus, setPortableStatus }) {
    const [mode, setMode] = React.useState("replace");
    const download = (pkg) => { const url = URL.createObjectURL(new Blob([(0, portable_1.serializePortablePackage)(pkg)], { type: "application/json" })); const a = document.createElement("a"); a.href = url; a.download = (0, portable_1.portablePackageFilename)(state); a.click(); URL.revokeObjectURL(url); };
    const exportFull = async () => { try {
        setPortableStatus("Building checksum-verified full package…");
        const pkg = await (0, portable_1.createPortablePackageV2)(state, persistence.attachments);
        download(pkg);
        setPortableStatus(`Portable v2 exported with ${pkg.manifest.attachments.count} attachment payloads.`);
    }
    catch (err) {
        setPortableStatus(err instanceof Error ? err.message : String(err));
    } };
    const exportLegacy = () => { const pkg = (0, portable_1.createPortablePackage)(state); download(pkg); setPortableStatus("State-only portable v1 exported for backward compatibility."); };
    const importData = async (e) => { const file = e.target.files?.[0]; if (!file)
        return; try {
        const text = await file.text();
        const version = (0, portable_1.portablePackageVersion)(text);
        const parsed = version === 2 ? await (0, portable_1.parsePortablePackageV2)(text) : (0, portable_1.parsePortablePackage)(text);
        const next = (0, portable_1.buildPortableImportPreview)(state, parsed, { mode });
        setPortable(next);
        setPortableStatus(`Portable v${version} preview · ${next.summary.add} add · ${next.summary.update} update · ${next.summary.remove} remove${next.attachmentRecords?.length ? ` · ${next.attachmentRecords.length} attachment payloads` : ""}.`);
    }
    catch (err) {
        setPortable(null);
        setPortableStatus(err instanceof Error ? err.message : String(err));
    }
    finally {
        e.target.value = "";
    } };
    const apply = async () => { if (!portable)
        return; try {
        if (portable.attachmentRecords?.length)
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
        React.createElement("p", { className: "muted" }, "Full export includes attachment bytes, MIME metadata and SHA-256 checksums. Legacy v1 remains importable."),
        React.createElement("div", { className: "button-row" },
            React.createElement("button", { className: "button", type: "button", onClick: () => void exportFull() }, "Export full v2"),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: exportLegacy }, "Export state-only v1")),
        React.createElement("select", { "aria-label": "Portable import mode", value: mode, onChange: (e) => { setMode(e.target.value); setPortable(null); } },
            React.createElement("option", { value: "replace" }, "Replace current local state"),
            React.createElement("option", { value: "new-plan" }, "Import active package plan as new")),
        React.createElement("label", { className: "button button-quiet file-button" },
            "Choose import",
            React.createElement("input", { "aria-label": "Import StudyFlow package", type: "file", accept: ".json,.studyflow.json,application/json", onChange: importData })),
        React.createElement("p", { className: "muted" }, portableStatus),
        portable && React.createElement(React.Fragment, null,
            React.createElement("p", { className: "muted" },
                "Mode: ",
                portable.mode ?? "replace",
                " \u00B7 attachments: ",
                portable.attachmentRecords?.length ?? 0),
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
function PwaTools() { const status = React.useRef(null), install = React.useRef(null); React.useEffect(() => { const handle = (0, pwa_1.registerStudyFlowPwa)({ statusElement: status.current, installButton: install.current }); return () => handle.dispose(); }, []); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Install & offline"),
    React.createElement("p", { id: "pwa-status", ref: status, className: "muted", role: "status", "aria-live": "polite", "aria-atomic": "true" }, "Checking PWA support\u2026"),
    React.createElement("button", { id: "install-app", ref: install, className: "button button-quiet", type: "button", hidden: true }, "Install StudyFlow")); }
