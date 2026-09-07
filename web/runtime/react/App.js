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
const portable_1 = require("../portable");
const pwa_1 = require("../pwa");
const title = (v) => v.replace(/[-_]/g, " ").replace(/\b\w/g, c => c.toUpperCase());
const pct = (v) => v == null ? "No data" : `${Math.round(v * 100)}%`;
const clock = (ms) => { const n = Math.max(0, Math.ceil(ms / 1000)); return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`; };
function useStudyState(store) { return React.useSyncExternalStore((notify) => store.subscribe(() => notify()), store.getState, store.getState); }
function Tabs({ value, setValue, items }) { return React.createElement("nav", { className: "subnav" }, items.map(([id, label]) => React.createElement("button", { key: id, type: "button", className: "tab-link", "aria-current": value === id ? "page" : undefined, onClick: () => setValue(id) }, label))); }
function Metric({ label, value, detail }) { return React.createElement("article", { className: "metric-card" },
    React.createElement("span", null, label),
    React.createElement("strong", null, value),
    React.createElement("small", null, detail)); }
function App({ store, persistence }) {
    const state = useStudyState(store);
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
    React.useEffect(() => { const h = location.hash.slice(1); if (["today", "plan", "practice", "progress"].includes(h))
        setScreen(h); const onHash = () => { const x = location.hash.slice(1); if (["today", "plan", "practice", "progress"].includes(x))
        setScreen(x); }; addEventListener("hashchange", onHash); if (!location.hash)
        history.replaceState(null, "", "#today"); return () => removeEventListener("hashchange", onHash); }, []);
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
    return React.createElement(React.Fragment, null,
        React.createElement("a", { className: "skip-link", href: "#main-content" }, "Skip to content"),
        React.createElement("header", { className: "app-header" },
            React.createElement("div", null,
                React.createElement("p", { className: "eyebrow" }, "StudyFlow Local"),
                React.createElement("h1", null, title(screen))),
            React.createElement("span", { className: "status-pill" }, persistence.backend)),
        React.createElement("main", { id: "main-content", className: "app-main" },
            screen === "today" && React.createElement(Today, { state: state, store: store, focus: focus, run: run }),
            " ",
            screen === "plan" && React.createElement(Plan, { state: state, store: store, tab: planTab, setTab: setPlanTab, preview: preview, setPreview: setPreview, persistence: persistence, run: run }),
            " ",
            screen === "practice" && React.createElement(Practice, { state: state, store: store, tab: practiceTab, setTab: setPracticeTab, questionId: questionId, setQuestionId: setQuestionId, run: run }),
            " ",
            screen === "progress" && React.createElement(Progress, { state: state, store: store, tab: progressTab, setTab: setProgressTab, persistence: persistence, portable: portable, setPortable: setPortable, portableStatus: portableStatus, setPortableStatus: setPortableStatus, run: run })),
        React.createElement("nav", { className: "bottom-nav", "aria-label": "Main navigation" }, ["today", "plan", "practice", "progress"].map(id => React.createElement("a", { key: id, href: `#${id}`, "aria-current": screen === id ? "page" : undefined },
            React.createElement("span", null, title(id))))),
        React.createElement("div", { id: "toast-region", className: "toast-region", "aria-live": "polite" }, toast && React.createElement("div", { className: "toast" }, toast)));
}
function Today({ state, store, focus, run }) { const rec = (0, domain_1.recommendNextActivity)(state); const [name, setName] = React.useState(""); const [note, setNote] = React.useState(focus.note ?? ""); const planned = state.activities.filter(a => a.planId === state.activePlanId && a.queue === "today" && a.status === "planned"); return React.createElement("section", { id: "today", "data-screen": "today", className: "screen-section" },
    React.createElement("article", { className: "hero-card" },
        React.createElement("p", { className: "eyebrow" }, "Next up"),
        React.createElement("h2", null, rec?.activity.title ?? "No queued activity"),
        React.createElement("p", { className: "muted" }, rec?.reason ?? "Add an activity or generate a schedule preview.")),
    React.createElement("div", { className: "content-grid two-column" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Focus"),
            React.createElement("div", { className: "focus-clock" }, clock(focus.remainingMs)),
            React.createElement("textarea", { value: note, onChange: (e) => setNote(e.target.value), placeholder: "Session note" }),
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
                React.createElement("input", { value: name, onChange: (e) => setName(e.target.value), placeholder: "Activity title", required: true }),
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
function Plan({ state, store, tab, setTab, preview, setPreview, persistence, run }) { const inv = (0, domain_1.derivePlanInventory)(state); const [subject, setSubject] = React.useState(""); const [topic, setTopic] = React.useState(""); const [subjectId, setSubjectId] = React.useState(state.subjects[0]?.id ?? ""); const subjects = state.subjects.filter(s => s.planId === state.activePlanId).slice().sort((a, b) => a.order - b.order); const materials = state.materials.filter(m => m.planId === state.activePlanId); return React.createElement("section", { id: "plan", "data-screen": "plan", className: "screen-section" },
    React.createElement(Tabs, { value: tab, setValue: setTab, items: [["overview", "Overview"], ["schedule", "Schedule"], ["content", "Content"], ["library", "Library"]] }),
    tab === "overview" && React.createElement("section", { id: "plan-overview", className: "tab-panel" },
        React.createElement("div", { className: "metric-grid" },
            React.createElement(Metric, { label: "Subjects", value: String(inv.subjects), detail: `${inv.topics} active topics` }),
            React.createElement(Metric, { label: "Planned", value: `${inv.plannedMinutes} min`, detail: `${inv.activities} activities` })),
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Study cycle"),
            React.createElement("ol", null, state.studyCycle.subjectIds.map((id, i) => React.createElement("li", { key: id },
                state.subjects.find(s => s.id === id)?.name ?? id,
                i === state.studyCycle.cursor ? " · current" : ""))),
            React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.advanceStudyCycle(), "Study cycle advanced.") }, "Advance"))),
    tab === "schedule" && React.createElement("section", { id: "plan-schedule", className: "tab-panel" },
        React.createElement("article", { className: "card" },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("h2", null, "Adaptive schedule"),
                React.createElement("button", { className: "button", type: "button", onClick: () => setPreview(store.previewSchedule()) }, "Preview")),
            preview ? React.createElement(React.Fragment, null,
                React.createElement("p", null,
                    preview.summary.add,
                    " add \u00B7 ",
                    preview.summary.move,
                    " move"),
                React.createElement("ul", null, preview.operations.map((op, i) => React.createElement("li", { key: i }, op.action === "move" ? `Move ${op.activityId} → ${op.to}` : `Add ${op.activity?.title ?? "activity"} · ${op.scheduledFor}`))),
                React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applySchedulePreview(preview), "Schedule preview applied."))
                        setPreview(null); } }, "Apply preview")) : React.createElement("p", { className: "muted" }, "Preview first; no schedule changes are silent."))),
    tab === "content" && React.createElement("section", { id: "plan-content", className: "tab-panel" },
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Add subject"),
                React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (subject.trim()) {
                        run(() => store.addSubject({ name: subject.trim() }), "Subject added.");
                        setSubject("");
                    } } },
                    React.createElement("input", { value: subject, onChange: (e) => setSubject(e.target.value), required: true }),
                    React.createElement("button", { className: "button", type: "submit" }, "Add"))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Add topic"),
                React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (topic.trim() && subjectId) {
                        run(() => store.addTopic({ subjectId, name: topic.trim() }), "Topic added.");
                        setTopic("");
                    } } },
                    React.createElement("select", { value: subjectId, onChange: (e) => setSubjectId(e.target.value) }, subjects.map(s => React.createElement("option", { key: s.id, value: s.id }, s.name))),
                    React.createElement("input", { value: topic, onChange: (e) => setTopic(e.target.value), required: true }),
                    React.createElement("button", { className: "button", type: "submit" }, "Add")))),
        subjects.map(s => React.createElement("article", { className: "card", key: s.id },
            React.createElement("div", { className: "card-heading-row" },
                React.createElement("h2", null, s.name),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateSubject(s.id, { archived: !s.archived }), s.archived ? "Subject restored." : "Subject archived.") }, s.archived ? "Restore" : "Archive")),
            React.createElement("ul", { className: "topic-list" }, state.topics.filter(t => t.subjectId === s.id).map(t => React.createElement("li", { key: t.id },
                React.createElement("strong", null, t.name),
                React.createElement("span", null,
                    title(t.priority),
                    " \u00B7 ",
                    title(t.status)),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.updateTopic(t.id, { status: t.status === "archived" ? "not-started" : "archived" }), "Topic updated.") }, t.status === "archived" ? "Restore" : "Archive"))))))),
    tab === "library" && React.createElement(Library, { materials: materials, store: store, persistence: persistence, run: run })); }
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
} }; return React.createElement("section", { id: "plan-library", className: "tab-panel" },
    React.createElement("article", { className: "card" },
        React.createElement("div", { className: "card-heading-row" },
            React.createElement("h2", null, "Local materials"),
            React.createElement("label", { className: "button button-quiet file-button" },
                "Add file",
                React.createElement("input", { type: "file", onChange: (e) => add(e).catch(err => console.error(err)) }))),
        React.createElement("ul", { className: "activity-list" },
            materials.map(m => React.createElement("li", { key: m.id },
                React.createElement("strong", null, m.title),
                React.createElement("span", null, m.mimeType ?? title(m.kind)),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.deleteMaterial(m.id), "Material removed.") }, "Delete"))),
            !materials.length && React.createElement("li", null, "No local materials yet.")))); }
function Practice({ state, store, tab, setTab, questionId, setQuestionId, run }) { const due = (0, domain_1.selectDueReviews)(state), errors = (0, domain_1.selectOpenErrors)(state), stats = (0, domain_1.deriveQuestionStats)(state), questions = state.questions.filter(q => q.planId === state.activePlanId), q = questions.find(x => x.id === questionId) ?? questions[0] ?? null; const [answer, setAnswer] = React.useState(""); const [prompt, setPrompt] = React.useState(""); return React.createElement("section", { id: "practice", "data-screen": "practice", className: "screen-section" },
    React.createElement(Tabs, { value: tab, setValue: setTab, items: [["reviews", "Reviews"], ["questions", "Questions"], ["tests", "Tests"], ["errors", "Errors"]] }),
    tab === "reviews" && React.createElement("section", { id: "practice-reviews", className: "tab-panel" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null,
                "Due reviews \u00B7 ",
                due.length),
            React.createElement("ul", { className: "activity-list" },
                due.map(r => React.createElement("li", { key: r.id },
                    React.createElement("strong", null, state.topics.find(t => t.id === r.topicId)?.name ?? "Review"),
                    React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.completeReview(r.id), "Review completed and evidenced.") }, "Complete"))),
                !due.length && React.createElement("li", null, "No reviews due.")))),
    tab === "questions" && React.createElement("section", { id: "practice-questions", className: "tab-panel" },
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Question practice"),
                React.createElement("p", { className: "muted" }, stats.accuracy == null ? "No graded accuracy" : `${Math.round(stats.accuracy * 100)}% graded accuracy`),
                React.createElement("select", { value: q?.id ?? "", onChange: (e) => { setQuestionId(e.target.value); setAnswer(""); } }, questions.map(x => React.createElement("option", { key: x.id, value: x.id }, x.prompt))),
                q && React.createElement(React.Fragment, null,
                    React.createElement("h3", null, q.prompt),
                    q.choices.length ? q.choices.map(c => React.createElement("label", { className: "choice", key: c },
                        React.createElement("input", { type: "radio", name: "answer", value: c, checked: answer === c, onChange: (e) => setAnswer(e.target.value) }),
                        React.createElement("span", null, c))) : React.createElement("input", { value: answer, onChange: (e) => setAnswer(e.target.value), placeholder: "Type answer" }),
                    React.createElement("button", { className: "button button-primary", type: "button", disabled: !answer.trim(), onClick: () => { if (run(() => store.answerQuestion(q.id, answer), "Attempt recorded."))
                            setAnswer(""); } }, "Submit"))),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Add question"),
                React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (prompt.trim()) {
                        run(() => store.addQuestion({ prompt: prompt.trim() }), "Ungraded question added.");
                        setPrompt("");
                    } } },
                    React.createElement("textarea", { value: prompt, onChange: (e) => setPrompt(e.target.value), required: true }),
                    React.createElement("button", { className: "button", type: "submit" }, "Save"))))),
    tab === "tests" && React.createElement("section", { id: "practice-tests", className: "tab-panel" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Tests"),
            React.createElement("p", { className: "muted" }, "Mock Exam Studio remains a later workflow; this screen does not fabricate test results."))),
    tab === "errors" && React.createElement("section", { id: "practice-errors", className: "tab-panel" },
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
function Progress({ state, store, tab, setTab, persistence, portable, setPortable, portableStatus, setPortableStatus, run }) { const m = (0, domain_1.deriveProgressMetrics)(state), timeline = (0, domain_1.buildEvidenceTimeline)(state, { limit: 30 }), integrity = (0, domain_1.buildContentIntegrityReport)(state), goals = state.goals.filter(g => g.planId === state.activePlanId); const [goal, setGoal] = React.useState(""); const exportData = () => { const pkg = (0, portable_1.createPortablePackage)(state); const url = URL.createObjectURL(new Blob([(0, portable_1.serializePortablePackage)(pkg)], { type: "application/json" })); const a = document.createElement("a"); a.href = url; a.download = (0, portable_1.portablePackageFilename)(state); a.click(); URL.revokeObjectURL(url); setPortableStatus("Portable package exported."); }; const importData = async (e) => { const file = e.target.files?.[0]; if (!file)
    return; try {
    const p = (0, portable_1.buildPortableImportPreview)(state, (0, portable_1.parsePortablePackage)(await file.text()));
    setPortable(p);
    setPortableStatus(`Preview ready: ${p.summary.add} add · ${p.summary.update} update · ${p.summary.remove} remove.`);
}
catch (err) {
    setPortable(null);
    setPortableStatus(err instanceof Error ? err.message : String(err));
}
finally {
    e.target.value = "";
} }; return React.createElement("section", { id: "progress", "data-screen": "progress", className: "screen-section" },
    React.createElement(Tabs, { value: tab, setValue: setTab, items: [["summary", "Summary"], ["performance", "Performance"], ["goals", "Goals"], ["tools", "Tools"]] }),
    tab === "summary" && React.createElement("section", { id: "progress-summary", className: "tab-panel" },
        React.createElement("div", { className: "metric-grid" },
            React.createElement(Metric, { label: "Readiness", value: m.readiness == null ? "No data" : `${m.readiness}%`, detail: m.readinessReason }),
            React.createElement(Metric, { label: "Study this week", value: `${m.weeklyStudyMinutes} min`, detail: "Completed sessions" }),
            React.createElement(Metric, { label: "Accuracy", value: pct(m.accuracy), detail: `${m.gradedAttempts} graded attempts` }),
            React.createElement(Metric, { label: "Coverage", value: pct(m.topicCoverage), detail: `${m.coveredTopics}/${m.activeTopics} evidenced topics` }))),
    tab === "performance" && React.createElement("section", { id: "progress-performance", className: "tab-panel" },
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
    tab === "goals" && React.createElement("section", { id: "progress-goals", className: "tab-panel" },
        React.createElement("article", { className: "card" },
            React.createElement("h2", null, "Goals"),
            React.createElement("form", { className: "stack-form", onSubmit: (e) => { e.preventDefault(); if (goal.trim()) {
                    run(() => store.addGoal({ name: goal.trim(), metric: "study-minutes", target: 120, period: "weekly" }), "Goal added.");
                    setGoal("");
                } } },
                React.createElement("input", { value: goal, onChange: (e) => setGoal(e.target.value), placeholder: "Weekly goal", required: true }),
                React.createElement("button", { className: "button", type: "submit" }, "Add 120 min goal")),
            goals.map(g => { const p = (0, domain_1.deriveGoalProgress)(state, g); return React.createElement("div", { className: "goal-row", key: g.id },
                React.createElement("div", null,
                    React.createElement("strong", null, g.name),
                    React.createElement("span", { className: "muted" },
                        p.actual ?? "No data",
                        " / ",
                        g.target)),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.toggleGoal(g.id)) }, g.active ? "Pause" : "Resume")); }))),
    tab === "tools" && React.createElement("section", { id: "progress-tools", className: "tab-panel" },
        React.createElement("div", { className: "content-grid two-column" },
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Content Integrity"),
                React.createElement("strong", null, integrity.valid ? "Local state references are valid" : "Integrity issue detected"),
                React.createElement("p", { className: "muted" }, integrity.valid ? `${Object.values(integrity.counts).reduce((a, b) => a + b, 0)} structured records checked.` : integrity.errors[0])),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Portable package"),
                React.createElement("button", { className: "button", type: "button", onClick: exportData }, "Export package"),
                React.createElement("label", { className: "button button-quiet file-button" },
                    "Choose import",
                    React.createElement("input", { type: "file", accept: ".json,.studyflow.json,application/json", onChange: importData })),
                React.createElement("p", { className: "muted" }, portableStatus),
                portable && React.createElement("button", { className: "button button-primary", type: "button", onClick: () => { if (run(() => store.applyPortableImportPreview(portable), "Import applied.")) {
                        setPortable(null);
                        setPortableStatus("Imported state applied locally.");
                    } } }, "Apply preview")),
            React.createElement("article", { className: "card" },
                React.createElement("h2", null, "Local state"),
                React.createElement("p", { className: "muted" }, store.getPersistenceStatus().message),
                React.createElement("p", { className: "muted" },
                    "Backend: ",
                    persistence.backend),
                React.createElement("button", { className: "button button-quiet", type: "button", onClick: () => run(() => store.resetLocalState(), "Sample data restored.") }, "Reset sample data")),
            React.createElement(PwaTools, null)))); }
function PwaTools() { const status = React.useRef(null), install = React.useRef(null); React.useEffect(() => { const register = pwa_1.registerStudyFlowPwa; register({ statusElement: status.current, installButton: install.current }); }, []); return React.createElement("article", { className: "card" },
    React.createElement("h2", null, "Install & offline"),
    React.createElement("p", { id: "pwa-status", ref: status, className: "muted", "aria-live": "polite" }, "Checking PWA support\u2026"),
    React.createElement("button", { id: "install-app", ref: install, className: "button button-quiet", type: "button", hidden: true }, "Install StudyFlow")); }
