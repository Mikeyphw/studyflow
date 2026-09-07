// TypeScript migration source. Public contracts are declared in types.ts.
// @ts-nocheck -- legacy internals preserved byte-for-byte for W14 behavioral parity; new/changed code is typed.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createStudyStore = createStudyStore;
const domain_js_1 = require("./domain.js");
const modes_js_1 = require("./modes.js");
function cloneState(state) { return JSON.parse(JSON.stringify(state)); }
function isoMs(value) { const ms = Date.parse(value); if (!Number.isFinite(ms))
    throw new TypeError(`Invalid timestamp: ${value}`); return ms; }
function persistenceMessage(status, reason = null) {
    if (status === "loaded")
        return "Local state loaded from this browser.";
    if (status === "migrated")
        return "Older local state migrated safely to the current schema.";
    if (status === "recovered")
        return reason ?? "Recovered the last verified local checkpoint.";
    if (status === "saved")
        return "Saved locally on this device.";
    if (status === "empty")
        return "Local state initialized on this device.";
    if (status === "invalid")
        return reason ?? "Saved local state was invalid; using a safe in-memory baseline.";
    if (status === "unavailable")
        return reason ?? "Local persistence is unavailable; changes remain in memory only.";
    if (status === "cleared")
        return "Local state reset to the StudyFlow sample baseline.";
    return reason ?? "Local state ready.";
}
function referenceCollections(state) { return [state.activities, state.sessions, state.materials, state.reviews, state.questions, state.evidence, state.errorEntries]; }
function createStudyStore({ repository, initialStateFactory = domain_js_1.createInitialStudyState, now = () => new Date().toISOString() } = {}) {
    if (!repository || typeof repository.load !== "function" || typeof repository.save !== "function")
        throw new TypeError("createStudyStore requires a repository with load/save methods");
    const loaded = repository.load();
    let state = loaded.state ? (0, domain_js_1.migrateStudyState)(cloneState(loaded.state), { now: now() }) : initialStateFactory({ now: now() });
    let persistence = { status: loaded.status, message: persistenceMessage(loaded.status, loaded.reason), durable: loaded.status !== "unavailable" && loaded.status !== "invalid" };
    const listeners = new Set();
    if (!loaded.state && loaded.status === "empty" || loaded.status === "migrated") {
        const seeded = repository.save(state);
        persistence = { status: loaded.status === "migrated" && seeded.status === "saved" ? "migrated" : seeded.status, message: loaded.status === "migrated" && seeded.status === "saved" ? persistenceMessage("migrated") : seeded.status === "saved" ? "Local state initialized and saved on this device." : persistenceMessage(seeded.status, seeded.reason), durable: seeded.status === "saved" };
    }
    function notify(reason) { for (const listener of listeners)
        listener(state, persistence, reason); }
    function commit(nextState, reason) {
        const migrated = (0, domain_js_1.migrateStudyState)(nextState, { now: now() });
        const errors = (0, domain_js_1.validateStudyState)(migrated);
        if (errors.length)
            throw new TypeError(`Invalid StudyFlow state: ${errors[0]}`);
        state = { ...cloneState(migrated), revision: state.revision + 1 };
        const saved = repository.save(state);
        persistence = { status: saved.status, message: persistenceMessage(saved.status, saved.reason), durable: saved.status === "saved" };
        notify(reason);
        return state;
    }
    function activePlanId() { if (!state.activePlanId)
        throw new TypeError("No active plan"); return state.activePlanId; }
    function nowIso() { return now(); }
    function reviewIntervals(planId = activePlanId()) {
        const plan = state.plans.find((candidate) => candidate.id === planId);
        const configured = plan?.mode === "EXAM_PLAN" ? plan.configuration.publicExam?.reviewIntervalsDays : null;
        return Array.isArray(configured) && configured.length ? [...configured] : [1, 7, 30];
    }
    function normalizeReviewGrade(value) {
        if (value == null || value === "")
            return 3;
        const labels = { again: 1, hard: 2, good: 3, easy: 5 };
        if (typeof value === "string" && value.toLowerCase() in labels)
            return labels[value.toLowerCase()];
        const grade = Number(value);
        if (!Number.isFinite(grade))
            throw new TypeError("Review rating must be a grade from 0 to 5");
        return Math.max(0, Math.min(5, Math.round(grade)));
    }
    function focusSnapshot(at = nowIso()) {
        const focus = (0, domain_js_1.createFocusState)(state.focus ?? {});
        return { ...focus, remainingMs: (0, domain_js_1.focusRemainingMs)(focus, at) };
    }
    function finishFocus(status, note = null) {
        const at = nowIso();
        const focus = focusSnapshot(at);
        if (focus.status === "idle")
            return null;
        const activity = focus.activityId ? state.activities.find((candidate) => candidate.id === focus.activityId) : null;
        const currentRunMs = focus.status === "running" && focus.lastResumedAt ? Math.max(0, isoMs(at) - isoMs(focus.lastResumedAt)) : 0;
        const elapsedMs = focus.elapsedMs + currentRunMs;
        const durationMinutes = elapsedMs > 0 ? Math.max(1, Math.round(elapsedMs / 60000)) : null;
        const session = (0, domain_js_1.createStudySession)({
            planId: activePlanId(), activityId: activity?.id ?? null, subjectId: activity?.subjectId ?? null, topicId: activity?.topicId ?? null,
            startedAt: focus.startedAt ?? at, endedAt: at, durationMinutes, status, note: note ?? focus.note, createdAt: at, updatedAt: at,
        });
        let activities = state.activities;
        let evidence = state.evidence;
        if (status === "completed") {
            activities = state.activities.map((candidate) => candidate.id === activity?.id ? { ...candidate, status: "completed", updatedAt: at } : candidate);
            evidence = [...state.evidence, (0, domain_js_1.createEvidence)({ planId: activePlanId(), subjectId: activity?.subjectId ?? null, topicId: activity?.topicId ?? null, activityId: activity?.id ?? null, sessionId: session.id, kind: "focus-session-completed", observedAt: at, value: { durationMinutes: durationMinutes ?? 0 }, provenance: "session", createdAt: at, updatedAt: at })];
        }
        commit({ ...state, activities, sessions: [...state.sessions, session], evidence, focus: (0, domain_js_1.createFocusState)({ durationMinutes: focus.durationMinutes }) }, `focus-${status}`);
        return session;
    }
    return {
        getState: () => state,
        getSnapshot: () => cloneState(state),
        getPersistenceStatus: () => ({ ...persistence }),
        getFocusSnapshot: (at) => focusSnapshot(at),
        subscribe(listener) { if (typeof listener !== "function")
            throw new TypeError("listener must be a function"); listeners.add(listener); return () => listeners.delete(listener); },
        setPlanMode(mode) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current)
                throw new TypeError("Active plan not found");
            const at = nowIso();
            const updated = (0, domain_js_1.createPlan)({ ...current, mode, configuration: {}, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map((plan) => plan.id === current.id ? updated : plan) }, "plan-mode-updated");
            return updated;
        },
        updatePublicExamTarget(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Public-exam target requires an EXAM_PLAN");
            const at = nowIso();
            const target = { ...current.configuration.publicExam.target, ...input };
            const updated = (0, domain_js_1.createPlan)({ ...current, examDate: target.examDate ?? current.examDate, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, target } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map((plan) => plan.id === current.id ? updated : plan) }, "public-exam-target-updated");
            return updated;
        },
        addExamStage(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Exam stages require an EXAM_PLAN");
            const at = nowIso();
            const stage = { id: input.id ?? `stage-${Date.now().toString(36)}`, name: String(input.name ?? "").trim(), date: input.date ?? null, status: input.status ?? "planned" };
            if (!stage.name)
                throw new TypeError("Exam stage name is required");
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, stages: [...current.configuration.publicExam.stages, stage] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map((plan) => plan.id === current.id ? updated : plan) }, "exam-stage-added");
            return updated;
        },
        previewExamNotice(source) { return (0, modes_js_1.buildExamNoticePreview)(state, source); },
        applyExamNoticePreview(preview) {
            if (!preview || preview.baseRevision !== state.revision)
                throw new TypeError("Edital preview is stale; preview again before applying");
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Edital import requires an EXAM_PLAN");
            const at = nowIso();
            let subjects = state.subjects.map((x) => ({ ...x })), topics = state.topics.map((x) => ({ ...x }));
            const touchedSubjects = new Set(), touchedTopics = new Set();
            let added = 0, archived = 0;
            for (const row of preview.subjects) {
                let subject = subjects.find((x) => x.planId === current.id && x.name.trim().toLowerCase() === row.name.trim().toLowerCase());
                if (!subject) {
                    subject = (0, domain_js_1.createSubject)({ planId: current.id, name: row.name, order: subjects.filter(x => x.planId === current.id).length, createdAt: at, updatedAt: at });
                    subjects.push(subject);
                    added++;
                }
                else if (subject.archived) {
                    subject = (0, domain_js_1.createSubject)({ ...subject, archived: false, createdAt: subject.createdAt, updatedAt: at });
                    subjects = subjects.map(x => x.id === subject.id ? subject : x);
                }
                touchedSubjects.add(subject.id);
                for (const name of row.topics) {
                    let topic = topics.find(x => x.subjectId === subject.id && x.name.trim().toLowerCase() === name.trim().toLowerCase());
                    if (!topic) {
                        topic = (0, domain_js_1.createTopic)({ planId: current.id, subjectId: subject.id, name, order: topics.filter(x => x.subjectId === subject.id).length, createdAt: at, updatedAt: at });
                        topics.push(topic);
                        added++;
                    }
                    else if (topic.status === "archived") {
                        topic = (0, domain_js_1.createTopic)({ ...topic, status: "not-started", createdAt: topic.createdAt, updatedAt: at });
                        topics = topics.map(x => x.id === topic.id ? topic : x);
                    }
                    touchedTopics.add(topic.id);
                }
            }
            for (const topic of topics.filter(x => x.planId === current.id && x.status !== "archived")) {
                if (!touchedTopics.has(topic.id)) {
                    topics = topics.map(x => x.id === topic.id ? { ...x, status: "archived", updatedAt: at } : x);
                    archived++;
                }
            }
            for (const subject of subjects.filter(x => x.planId === current.id && !x.archived)) {
                if (!touchedSubjects.has(subject.id)) {
                    subjects = subjects.map(x => x.id === subject.id ? { ...x, archived: true, updatedAt: at } : x);
                    archived++;
                }
            }
            const revision = { id: `notice-${Date.now().toString(36)}`, acceptedAt: at, sourceHash: preview.sourceHash, added, archived };
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, noticeRevisions: [...current.configuration.publicExam.noticeRevisions, revision] } }, createdAt: current.createdAt, updatedAt: at });
            const cycleIds = subjects.filter(x => x.planId === current.id && !x.archived).sort((a, b) => a.order - b.order).map(x => x.id);
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x), subjects, topics, studyCycle: (0, domain_js_1.createStudyCycleState)({ subjectIds: cycleIds, cursor: Math.min(state.studyCycle.cursor, Math.max(0, cycleIds.length - 1)) }) }, "exam-notice-applied");
            return updated;
        },
        addLegislation(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Legislation requires an EXAM_PLAN");
            const at = nowIso();
            const title = String(input.title ?? "").trim();
            if (!title)
                throw new TypeError("Legislation title is required");
            const document = { id: `law-${Date.now().toString(36)}`, title, reference: input.reference ?? null, notes: input.notes ?? null, provisions: (input.provisions ?? []).map((p, i) => ({ id: `provision-${Date.now().toString(36)}-${i}`, label: String(p.label ?? `Provision ${i + 1}`), text: String(p.text ?? ""), status: "unseen" })) };
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, legislation: [...current.configuration.publicExam.legislation, document] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "legislation-added");
            return updated;
        },
        setReviewIntervalsDays(intervals) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Review intervals require an EXAM_PLAN");
            const values = Array.isArray(intervals) ? intervals.map(Number) : [];
            if (values.length < 1 || values.length > 8 || values.some((value) => !Number.isInteger(value) || value < 1 || value > 365) || values.some((value, index) => index > 0 && value < values[index - 1]))
                throw new TypeError("Review intervals must contain 1 to 8 non-decreasing whole-day values from 1 to 365");
            const at = nowIso();
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, reviewIntervalsDays: values } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "review-policy-updated");
            return updated;
        },
        setMockBlueprint(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "EXAM_PLAN")
                throw new TypeError("Mock blueprint requires an EXAM_PLAN");
            const at = nowIso();
            const blueprint = (0, modes_js_1.normalizeMockBlueprint)({ ...current.configuration.publicExam.mockBlueprint, ...input });
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, publicExam: { ...current.configuration.publicExam, mockBlueprint: blueprint } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "mock-blueprint-updated");
            return updated;
        },
        recordMockExam(preview, answers) {
            if (!preview || preview.baseRevision !== state.revision)
                throw new TypeError("Mock preview is stale; preview again before recording");
            if (!preview.ready)
                throw new TypeError("Mock preview cannot be recorded because the question bank is insufficient");
            const at = nowIso();
            const score = (0, modes_js_1.scoreMockExam)(preview, answers, state);
            const evidence = (0, domain_js_1.createEvidence)({ planId: activePlanId(), kind: "mock-exam-result", observedAt: at, provenance: "user", value: { ...score, blueprint: preview.blueprint, questionIds: preview.questionIds }, createdAt: at, updatedAt: at });
            commit({ ...state, evidence: [...state.evidence, evidence] }, "mock-exam-recorded");
            return evidence;
        },
        addProgrammingExercise(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "PROGRAMMING_COURSE")
                throw new TypeError("Programming exercises require PROGRAMMING_COURSE mode");
            const at = nowIso();
            const exercise = (0, modes_js_1.createProgrammingExercise)({ ...input, language: input.language ?? current.configuration.programming.language });
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, programming: { ...current.configuration.programming, exercises: [...current.configuration.programming.exercises, exercise] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "programming-exercise-added");
            return updated;
        },
        updateProgrammingExercise(id, changes) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "PROGRAMMING_COURSE")
                throw new TypeError("Programming exercises require PROGRAMMING_COURSE mode");
            const exists = current.configuration.programming.exercises.find(x => x.id === id);
            if (!exists)
                throw new TypeError("Programming exercise not found");
            const at = nowIso();
            const exercise = (0, modes_js_1.createProgrammingExercise)({ ...exists, ...changes, id: exists.id });
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, programming: { ...current.configuration.programming, exercises: current.configuration.programming.exercises.map(x => x.id === id ? exercise : x) } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "programming-exercise-updated");
            return updated;
        },
        recordProgrammingExercise(id, { completed = false, note = null } = {}) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "PROGRAMMING_COURSE")
                throw new TypeError("Programming evidence requires PROGRAMMING_COURSE mode");
            const exercise = current.configuration.programming.exercises.find(x => x.id === id);
            if (!exercise)
                throw new TypeError("Programming exercise not found");
            const at = nowIso();
            const evidence = (0, domain_js_1.createEvidence)({ planId: current.id, kind: "programming-exercise", observedAt: at, provenance: "user", value: { exerciseId: id, completed: Boolean(completed), note }, createdAt: at, updatedAt: at });
            const nextExercise = (0, modes_js_1.createProgrammingExercise)({ ...exercise, status: completed ? "completed" : "attempted" });
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, programming: { ...current.configuration.programming, exercises: current.configuration.programming.exercises.map(x => x.id === id ? nextExercise : x) } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x), evidence: [...state.evidence, evidence] }, "programming-exercise-recorded");
            return evidence;
        },
        addLanguageExpression(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "LANGUAGE_DECK")
                throw new TypeError("Language expressions require LANGUAGE_DECK mode");
            const at = nowIso();
            const expression = (0, modes_js_1.createLanguageExpression)(input);
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, language: { ...current.configuration.language, expressions: [...current.configuration.language.expressions, expression] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "language-expression-added");
            return updated;
        },
        recordLanguageReview(expressionId, direction, rating, { answer = null, note = null } = {}) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "LANGUAGE_DECK")
                throw new TypeError("Language review requires LANGUAGE_DECK mode");
            const expression = current.configuration.language.expressions.find(x => x.id === expressionId);
            if (!expression)
                throw new TypeError("Language expression not found");
            if (!current.configuration.language.reviewDirections.includes(direction))
                throw new TypeError("Unsupported language review direction");
            const at = nowIso();
            const manual = direction === "sentence-creation";
            const value = { expressionId, direction, rating: Math.max(0, Math.min(5, Number(rating) || 0)), answer, note, manualReview: manual };
            const evidence = (0, domain_js_1.createEvidence)({ planId: current.id, kind: "language-review", observedAt: at, provenance: "user", value, createdAt: at, updatedAt: at });
            const interval = value.rating >= 4 ? 7 : value.rating >= 3 ? 3 : 1;
            const review = (0, domain_js_1.createReview)({ planId: current.id, dueAt: new Date(Date.parse(at) + interval * 86400000).toISOString(), status: "due", sourceType: "language-expression", sourceId: null, createdAt: at, updatedAt: at });
            commit({ ...state, evidence: [...state.evidence, evidence], reviews: [...state.reviews, review] }, "language-review-recorded");
            return evidence;
        },
        addReadingItem(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "READING_STUDY")
                throw new TypeError("Reading items require READING_STUDY mode");
            const at = nowIso();
            const item = (0, modes_js_1.createReadingItem)(input);
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, reading: { items: [...current.configuration.reading.items, item] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "reading-item-added");
            return updated;
        },
        updateReadingProgress(id, currentUnit) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "READING_STUDY")
                throw new TypeError("Reading progress requires READING_STUDY mode");
            const item = current.configuration.reading.items.find(x => x.id === id);
            if (!item)
                throw new TypeError("Reading item not found");
            const at = nowIso();
            const next = (0, modes_js_1.createReadingItem)({ ...item, currentUnit, status: Number(currentUnit) >= item.totalUnits ? "completed" : "reading" });
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, reading: { items: current.configuration.reading.items.map(x => x.id === id ? next : x) } }, createdAt: current.createdAt, updatedAt: at });
            const evidence = (0, domain_js_1.createEvidence)({ planId: current.id, kind: "reading-progress", observedAt: at, provenance: "user", value: { itemId: id, currentUnit: next.currentUnit, totalUnits: next.totalUnits }, createdAt: at, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x), evidence: [...state.evidence, evidence] }, "reading-progress-recorded");
            return evidence;
        },
        addGeneralSkillDrill(input) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "GENERAL_SKILL")
                throw new TypeError("Skill drills require GENERAL_SKILL mode");
            const at = nowIso();
            const drill = (0, modes_js_1.createGeneralSkillDrill)(input);
            const updated = (0, domain_js_1.createPlan)({ ...current, configuration: { ...current.configuration, general: { drills: [...current.configuration.general.drills, drill] } }, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, plans: state.plans.map(x => x.id === current.id ? updated : x) }, "skill-drill-added");
            return updated;
        },
        recordGeneralSkillDrill(id, value, { note = null } = {}) {
            const current = state.plans.find((plan) => plan.id === activePlanId());
            if (!current || current.mode !== "GENERAL_SKILL")
                throw new TypeError("Skill evidence requires GENERAL_SKILL mode");
            const drill = current.configuration.general.drills.find(x => x.id === id);
            if (!drill)
                throw new TypeError("Skill drill not found");
            const at = nowIso();
            const evidence = (0, domain_js_1.createEvidence)({ planId: current.id, kind: "skill-drill", observedAt: at, provenance: "user", value: { drillId: id, value: Number(value), target: drill.target, unit: drill.unit, note }, createdAt: at, updatedAt: at });
            commit({ ...state, evidence: [...state.evidence, evidence] }, "skill-drill-recorded");
            return evidence;
        },
        addActivity(input) {
            const at = nowIso();
            const activity = (0, domain_js_1.createActivity)({ ...input, planId: input.planId ?? activePlanId(), createdAt: input.createdAt ?? at, updatedAt: input.updatedAt ?? at });
            commit({ ...state, activities: [...state.activities, activity] }, "activity-added");
            return activity;
        },
        startFocus({ activityId = null, durationMinutes = null, note = null } = {}) {
            const at = nowIso();
            const current = focusSnapshot(at);
            if (current.status === "running")
                return current;
            const activity = activityId ? state.activities.find((candidate) => candidate.id === activityId && candidate.status === "planned") : (0, domain_js_1.selectActivitiesForQueue)(state, "today")[0] ?? null;
            if (activityId && !activity)
                throw new TypeError("Focus activity must reference a planned activity");
            const configuredMinutes = durationMinutes ?? current.durationMinutes ?? activity?.durationMinutes ?? 25;
            const baseRemaining = current.status === "paused" ? current.remainingMs : Number(configuredMinutes) * 60000;
            const startedAt = current.status === "paused" ? current.startedAt : at;
            const elapsedMs = current.status === "paused" ? current.elapsedMs : 0;
            const next = (0, domain_js_1.createFocusState)({ status: "running", activityId: activity?.id ?? current.activityId, durationMinutes: configuredMinutes, remainingMs: baseRemaining, elapsedMs, startedAt, lastResumedAt: at, endsAt: new Date(isoMs(at) + baseRemaining).toISOString(), note: note ?? current.note });
            commit({ ...state, focus: next }, current.status === "paused" ? "focus-resumed" : "focus-started");
            return next;
        },
        pauseFocus() {
            const at = nowIso();
            const current = focusSnapshot(at);
            if (current.status !== "running")
                return current;
            const runMs = current.lastResumedAt ? Math.max(0, isoMs(at) - isoMs(current.lastResumedAt)) : 0;
            const next = (0, domain_js_1.createFocusState)({ ...current, status: "paused", remainingMs: current.remainingMs, elapsedMs: current.elapsedMs + runMs, lastResumedAt: null, endsAt: null });
            commit({ ...state, focus: next }, "focus-paused");
            return next;
        },
        completeFocus({ note = null } = {}) { return finishFocus("completed", note); },
        cancelFocus({ note = null } = {}) { return finishFocus("cancelled", note); },
        resetFocus() { const current = focusSnapshot(); const next = (0, domain_js_1.createFocusState)({ durationMinutes: current.durationMinutes }); commit({ ...state, focus: next }, "focus-reset"); return next; },
        setFocusNote(note) { const next = (0, domain_js_1.createFocusState)({ ...state.focus, note }); commit({ ...state, focus: next }, "focus-note-updated"); return next; },
        setStudyCycle(subjectIds) {
            const allowed = new Set(state.subjects.filter((subject) => subject.planId === activePlanId() && !subject.archived).map((subject) => subject.id));
            const ids = [...new Set(subjectIds)];
            if (ids.some((id) => !allowed.has(id)))
                throw new TypeError("Study cycle subjects must be active subjects in the current plan");
            const cycle = (0, domain_js_1.createStudyCycleState)({ subjectIds: ids, cursor: 0 });
            commit({ ...state, studyCycle: cycle }, "study-cycle-updated");
            return cycle;
        },
        advanceStudyCycle() {
            const cycle = (0, domain_js_1.createStudyCycleState)(state.studyCycle ?? {});
            if (!cycle.subjectIds.length)
                return cycle;
            const next = (0, domain_js_1.createStudyCycleState)({ ...cycle, cursor: (cycle.cursor + 1) % cycle.subjectIds.length });
            commit({ ...state, studyCycle: next }, "study-cycle-advanced");
            return next;
        },
        addSubject(input) {
            const at = nowIso();
            const existing = state.subjects.filter((subject) => subject.planId === activePlanId());
            const subject = (0, domain_js_1.createSubject)({ ...input, planId: activePlanId(), order: input.order ?? existing.length, createdAt: at, updatedAt: at });
            commit({ ...state, subjects: [...state.subjects, subject] }, "subject-added");
            return subject;
        },
        updateSubject(id, changes) {
            const current = state.subjects.find((subject) => subject.id === id);
            if (!current)
                throw new TypeError("Subject not found");
            const at = nowIso();
            const updated = (0, domain_js_1.createSubject)({ ...current, ...changes, id: current.id, planId: current.planId, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, subjects: state.subjects.map((subject) => subject.id === id ? updated : subject) }, "subject-updated");
            return updated;
        },
        deleteSubject(id) {
            const current = state.subjects.find((subject) => subject.id === id);
            if (!current)
                throw new TypeError("Subject not found");
            if (state.topics.some((topic) => topic.subjectId === id))
                throw new TypeError("Archive or remove this subject's topics before deleting it");
            if (referenceCollections(state).some((collection) => collection.some((entity) => entity.subjectId === id)))
                throw new TypeError("Subject has study history or linked records and cannot be deleted; archive it instead");
            commit({ ...state, subjects: state.subjects.filter((subject) => subject.id !== id) }, "subject-deleted");
        },
        moveSubject(id, direction) {
            const ordered = state.subjects.filter((subject) => subject.planId === activePlanId()).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
            const index = ordered.findIndex((subject) => subject.id === id);
            const target = index + (direction === "up" ? -1 : 1);
            if (index < 0 || target < 0 || target >= ordered.length)
                return;
            [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
            const ranks = new Map(ordered.map((subject, i) => [subject.id, i]));
            const at = nowIso();
            commit({ ...state, subjects: state.subjects.map((subject) => ranks.has(subject.id) ? { ...subject, order: ranks.get(subject.id), updatedAt: at } : subject) }, "subject-reordered");
        },
        addTopic(input) {
            const subject = state.subjects.find((candidate) => candidate.id === input.subjectId && candidate.planId === activePlanId());
            if (!subject)
                throw new TypeError("Topic requires a subject in the active plan");
            const at = nowIso();
            const siblings = state.topics.filter((topic) => topic.subjectId === subject.id);
            const topic = (0, domain_js_1.createTopic)({ ...input, planId: activePlanId(), order: input.order ?? siblings.length, createdAt: at, updatedAt: at });
            commit({ ...state, topics: [...state.topics, topic] }, "topic-added");
            return topic;
        },
        updateTopic(id, changes) {
            const current = state.topics.find((topic) => topic.id === id);
            if (!current)
                throw new TypeError("Topic not found");
            const subjectId = changes.subjectId ?? current.subjectId;
            const subject = state.subjects.find((candidate) => candidate.id === subjectId && candidate.planId === current.planId);
            if (!subject)
                throw new TypeError("Topic subject must belong to the same plan");
            const at = nowIso();
            const updated = (0, domain_js_1.createTopic)({ ...current, ...changes, id: current.id, planId: current.planId, subjectId, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, topics: state.topics.map((topic) => topic.id === id ? updated : topic) }, "topic-updated");
            return updated;
        },
        deleteTopic(id) {
            const current = state.topics.find((topic) => topic.id === id);
            if (!current)
                throw new TypeError("Topic not found");
            if (referenceCollections(state).some((collection) => collection.some((entity) => entity.topicId === id)) || state.reviews.some((review) => review.sourceType === "topic" && review.sourceId === id))
                throw new TypeError("Topic has study history or linked records and cannot be deleted; archive it instead");
            commit({ ...state, topics: state.topics.filter((topic) => topic.id !== id) }, "topic-deleted");
        },
        moveTopic(id, direction) {
            const current = state.topics.find((topic) => topic.id === id);
            if (!current)
                return;
            const ordered = state.topics.filter((topic) => topic.subjectId === current.subjectId).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
            const index = ordered.findIndex((topic) => topic.id === id);
            const target = index + (direction === "up" ? -1 : 1);
            if (index < 0 || target < 0 || target >= ordered.length)
                return;
            [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
            const ranks = new Map(ordered.map((topic, i) => [topic.id, i]));
            const at = nowIso();
            commit({ ...state, topics: state.topics.map((topic) => ranks.has(topic.id) ? { ...topic, order: ranks.get(topic.id), updatedAt: at } : topic) }, "topic-reordered");
        },
        previewSchedule(options = {}) { return (0, domain_js_1.buildSchedulePreview)(state, { ...options, at: options.at ?? nowIso() }); },
        applySchedulePreview(preview) {
            if (!preview || typeof preview !== "object" || !Array.isArray(preview.operations))
                throw new TypeError("Schedule preview is required");
            if (preview.baseRevision !== state.revision)
                throw new TypeError("Schedule preview is stale; preview again before applying");
            const at = nowIso();
            let activities = state.activities.map((activity) => ({ ...activity }));
            for (const operation of preview.operations) {
                if (operation.action === "move") {
                    const index = activities.findIndex((activity) => activity.id === operation.activityId && activity.status === "planned");
                    if (index < 0)
                        throw new TypeError(`Schedule move references missing planned activity: ${operation.activityId}`);
                    const scheduledFor = operation.to;
                    const today = at.slice(0, 10);
                    activities[index] = { ...activities[index], scheduledFor, queue: scheduledFor === today ? "today" : "future", updatedAt: at };
                }
                else if (operation.action === "add") {
                    const activity = (0, domain_js_1.createActivity)({ ...operation.activity, planId: activePlanId(), scheduledFor: operation.scheduledFor, origin: operation.activity.origin ?? "schedule", createdAt: at, updatedAt: at });
                    activities.push(activity);
                }
                else
                    throw new TypeError(`Unsupported schedule operation: ${operation.action}`);
            }
            commit({ ...state, activities }, "schedule-preview-applied");
            return preview;
        },
        addMaterial(input) {
            const at = nowIso();
            const material = (0, domain_js_1.createMaterial)({ ...input, planId: input.planId ?? activePlanId(), createdAt: input.createdAt ?? at, updatedAt: input.updatedAt ?? at });
            commit({ ...state, materials: [...state.materials, material] }, "material-added");
            return material;
        },
        deleteMaterial(materialId) {
            const current = state.materials.find((material) => material.id === materialId && material.planId === activePlanId());
            if (!current)
                throw new TypeError("Material not found in the active plan");
            commit({ ...state, materials: state.materials.filter((material) => material.id !== materialId) }, "material-deleted");
            return current;
        },
        addGoal(input) {
            const at = nowIso();
            const goal = (0, domain_js_1.createGoal)({ ...input, planId: input.planId ?? activePlanId(), createdAt: at, updatedAt: at });
            commit({ ...state, goals: [...state.goals, goal] }, "goal-added");
            return goal;
        },
        toggleGoal(goalId) {
            const current = state.goals.find((goal) => goal.id === goalId && goal.planId === activePlanId());
            if (!current)
                throw new TypeError("Goal not found in the active plan");
            const at = nowIso();
            const updated = (0, domain_js_1.createGoal)({ ...current, active: !current.active, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, goals: state.goals.map((goal) => goal.id === goalId ? updated : goal) }, "goal-toggled");
            return updated;
        },
        deleteGoal(goalId) {
            const current = state.goals.find((goal) => goal.id === goalId && goal.planId === activePlanId());
            if (!current)
                throw new TypeError("Goal not found in the active plan");
            commit({ ...state, goals: state.goals.filter((goal) => goal.id !== goalId) }, "goal-deleted");
        },
        applyPortableImportPreview(preview) {
            if (!preview || typeof preview !== "object" || !preview.nextState)
                throw new TypeError("Portable import preview is required");
            if (preview.baseRevision !== state.revision)
                throw new TypeError("Portable import preview is stale; preview the package again before applying");
            const candidate = (0, domain_js_1.migrateStudyState)(cloneState(preview.nextState), { now: nowIso() });
            const errors = (0, domain_js_1.validateStudyState)(candidate);
            if (errors.length)
                throw new TypeError(`Portable import state is invalid: ${errors[0]}`);
            return commit({ ...candidate, revision: state.revision }, "portable-import-applied");
        },
        addQuestion(input) {
            const at = nowIso();
            const question = (0, domain_js_1.createQuestion)({ ...input, planId: input.planId ?? activePlanId(), createdAt: at, updatedAt: at });
            commit({ ...state, questions: [...state.questions, question] }, "question-added");
            return question;
        },
        answerQuestion(questionId, answer, { durationSeconds = null, errorCategory = "other", note = null } = {}) {
            const at = nowIso();
            const question = state.questions.find((candidate) => candidate.id === questionId && candidate.planId === activePlanId());
            if (!question)
                throw new TypeError("Question not found in the active plan");
            const result = (0, domain_js_1.gradeQuestion)(question, answer);
            const attempt = (0, domain_js_1.createEvidence)({
                planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, questionId: question.id, kind: "question-attempt", observedAt: at, provenance: "question",
                value: { gradable: result.gradable, correct: result.correct, submitted: result.submitted, expected: result.expected, durationSeconds: durationSeconds == null ? null : Math.max(0, Number(durationSeconds) || 0) }, createdAt: at, updatedAt: at,
            });
            let reviews = state.reviews;
            let errorEntries = state.errorEntries;
            let review = null;
            let errorEntry = null;
            if (result.gradable && result.correct === false) {
                review = state.reviews.find((candidate) => candidate.status === "due" && candidate.sourceType === "question" && candidate.sourceId === question.id) ?? null;
                if (!review) {
                    const intervals = reviewIntervals();
                    review = (0, domain_js_1.createReview)({ planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, dueAt: new Date(Date.parse(at) + intervals[0] * 86400000).toISOString(), status: "due", sourceType: "question", sourceId: question.id, intervalIndex: 0, intervalDays: intervals[0], repetitionCount: 0, createdAt: at, updatedAt: at });
                    reviews = [...reviews, review];
                }
                const existing = state.errorEntries.find((candidate) => candidate.status === "open" && candidate.questionId === question.id && candidate.category === errorCategory);
                if (existing) {
                    errorEntry = (0, domain_js_1.createErrorEntry)({ ...existing, recurrenceCount: existing.recurrenceCount + 1, reviewId: review.id, note: note ?? existing.note, updatedAt: at });
                    errorEntries = state.errorEntries.map((candidate) => candidate.id === existing.id ? errorEntry : candidate);
                }
                else {
                    errorEntry = (0, domain_js_1.createErrorEntry)({ planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, questionId: question.id, reviewId: review.id, category: errorCategory, note, observedAt: at, createdAt: at, updatedAt: at });
                    errorEntries = [...state.errorEntries, errorEntry];
                }
            }
            commit({ ...state, evidence: [...state.evidence, attempt], reviews, errorEntries }, "question-answered");
            return { result, attempt, review, errorEntry };
        },
        completeReview(reviewId, { rating = null } = {}) {
            const at = nowIso();
            const review = state.reviews.find((candidate) => candidate.id === reviewId && candidate.planId === activePlanId());
            if (!review)
                throw new TypeError("Review not found in the active plan");
            if (review.status !== "due")
                return review;
            const grade = normalizeReviewGrade(rating);
            const intervals = reviewIntervals(review.planId);
            const currentIndex = Math.max(0, Math.min(intervals.length - 1, Number.isInteger(review.intervalIndex) ? review.intervalIndex : 0));
            const nextIndex = grade < 3 ? 0 : Math.min(currentIndex + 1, intervals.length - 1);
            const nextInterval = intervals[nextIndex];
            const completed = (0, domain_js_1.createReview)({ ...review, status: "completed", completedAt: at, updatedAt: at });
            const duplicate = review.sourceId == null ? null : state.reviews.find((candidate) => candidate.id !== review.id && candidate.status === "due" && candidate.planId === review.planId && candidate.sourceType === review.sourceType && candidate.sourceId === review.sourceId) ?? null;
            const successor = duplicate ?? (0, domain_js_1.createReview)({ planId: review.planId, subjectId: review.subjectId, topicId: review.topicId, dueAt: new Date(Date.parse(at) + nextInterval * 86400000).toISOString(), status: "due", sourceType: review.sourceType, sourceId: review.sourceId, intervalIndex: nextIndex, intervalDays: nextInterval, repetitionCount: (review.repetitionCount ?? 0) + 1, previousReviewId: review.id, createdAt: at, updatedAt: at });
            let reviews = state.reviews.map((candidate) => candidate.id === review.id ? completed : candidate);
            if (!duplicate)
                reviews = [...reviews, successor];
            const errorEntries = state.errorEntries.map((entry) => entry.status === "open" && entry.reviewId === review.id ? (0, domain_js_1.createErrorEntry)({ ...entry, reviewId: successor.id, updatedAt: at }) : entry);
            const evidence = (0, domain_js_1.createEvidence)({ planId: activePlanId(), subjectId: review.subjectId, topicId: review.topicId, kind: "review-completed", observedAt: at, provenance: "user", value: { reviewId: review.id, rating, grade, intervalIndex: currentIndex, intervalDays: review.intervalDays, policyIntervalsDays: intervals, nextReviewId: successor.id, nextIntervalIndex: nextIndex, nextIntervalDays: nextInterval, nextDueAt: successor.dueAt }, createdAt: at, updatedAt: at });
            commit({ ...state, reviews, errorEntries, evidence: [...state.evidence, evidence] }, "review-completed");
            return completed;
        },
        resolveError(errorId, { note = null } = {}) {
            const at = nowIso();
            const entry = state.errorEntries.find((candidate) => candidate.id === errorId && candidate.planId === activePlanId());
            if (!entry)
                throw new TypeError("Error entry not found in the active plan");
            const resolved = (0, domain_js_1.createErrorEntry)({ ...entry, status: "resolved", resolvedAt: at, note: note ?? entry.note, updatedAt: at });
            commit({ ...state, errorEntries: state.errorEntries.map((candidate) => candidate.id === errorId ? resolved : candidate) }, "error-resolved");
            return resolved;
        },
        replaceState(nextState, reason = "state-replaced") { return commit(nextState, reason); },
        resetLocalState() {
            const cleared = typeof repository.clear === "function" ? repository.clear() : { status: "cleared" };
            state = initialStateFactory({ now: nowIso() });
            const saved = repository.save(state);
            const status = saved.status === "saved" ? "cleared" : saved.status;
            persistence = { status, message: saved.status === "saved" ? persistenceMessage("cleared") : persistenceMessage(saved.status, saved.reason ?? cleared.reason), durable: saved.status === "saved" };
            notify("state-reset");
            return state;
        },
    };
}
