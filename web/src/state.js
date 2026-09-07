"use strict";

import {
    buildSchedulePreview, createActivity, createErrorEntry, createEvidence, createFocusState, createInitialStudyState, createQuestion, createReview, createStudySession,
    createStudyCycleState, createSubject, createTopic, focusRemainingMs, gradeQuestion, migrateStudyState, selectActivitiesForQueue, validateStudyState,
} from "./domain.js";

function cloneState(state) { return JSON.parse(JSON.stringify(state)); }
function isoMs(value) { const ms = Date.parse(value); if (!Number.isFinite(ms)) throw new TypeError(`Invalid timestamp: ${value}`); return ms; }
function persistenceMessage(status, reason = null) {
    if (status === "loaded") return "Local state loaded from this browser.";
    if (status === "migrated") return "Older local state migrated safely to the current schema.";
    if (status === "saved") return "Saved locally on this device.";
    if (status === "empty") return "Local state initialized on this device.";
    if (status === "invalid") return reason ?? "Saved local state was invalid; using a safe in-memory baseline.";
    if (status === "unavailable") return reason ?? "Local persistence is unavailable; changes remain in memory only.";
    if (status === "cleared") return "Local state reset to the StudyFlow sample baseline.";
    return reason ?? "Local state ready.";
}
function referenceCollections(state) { return [state.activities, state.sessions, state.materials, state.reviews, state.questions, state.evidence, state.errorEntries]; }

export function createStudyStore({ repository, initialStateFactory = createInitialStudyState, now = () => new Date().toISOString() } = {}) {
    if (!repository || typeof repository.load !== "function" || typeof repository.save !== "function") throw new TypeError("createStudyStore requires a repository with load/save methods");
    const loaded = repository.load();
    let state = loaded.state ? migrateStudyState(cloneState(loaded.state), { now: now() }) : initialStateFactory({ now: now() });
    let persistence = { status: loaded.status, message: persistenceMessage(loaded.status, loaded.reason), durable: loaded.status !== "unavailable" && loaded.status !== "invalid" };
    const listeners = new Set();
    if (!loaded.state && loaded.status === "empty" || loaded.status === "migrated") {
        const seeded = repository.save(state);
        persistence = { status: loaded.status === "migrated" && seeded.status === "saved" ? "migrated" : seeded.status, message: loaded.status === "migrated" && seeded.status === "saved" ? persistenceMessage("migrated") : seeded.status === "saved" ? "Local state initialized and saved on this device." : persistenceMessage(seeded.status, seeded.reason), durable: seeded.status === "saved" };
    }
    function notify(reason) { for (const listener of listeners) listener(state, persistence, reason); }
    function commit(nextState, reason) {
        const migrated = migrateStudyState(nextState, { now: now() });
        const errors = validateStudyState(migrated);
        if (errors.length) throw new TypeError(`Invalid StudyFlow state: ${errors[0]}`);
        state = { ...cloneState(migrated), revision: state.revision + 1 };
        const saved = repository.save(state);
        persistence = { status: saved.status, message: persistenceMessage(saved.status, saved.reason), durable: saved.status === "saved" };
        notify(reason);
        return state;
    }
    function activePlanId() { if (!state.activePlanId) throw new TypeError("No active plan"); return state.activePlanId; }
    function nowIso() { return now(); }
    function focusSnapshot(at = nowIso()) {
        const focus = createFocusState(state.focus ?? {});
        return { ...focus, remainingMs: focusRemainingMs(focus, at) };
    }
    function finishFocus(status, note = null) {
        const at = nowIso();
        const focus = focusSnapshot(at);
        if (focus.status === "idle") return null;
        const activity = focus.activityId ? state.activities.find((candidate) => candidate.id === focus.activityId) : null;
        const currentRunMs = focus.status === "running" && focus.lastResumedAt ? Math.max(0, isoMs(at) - isoMs(focus.lastResumedAt)) : 0;
        const elapsedMs = focus.elapsedMs + currentRunMs;
        const durationMinutes = elapsedMs > 0 ? Math.max(1, Math.round(elapsedMs / 60_000)) : null;
        const session = createStudySession({
            planId: activePlanId(), activityId: activity?.id ?? null, subjectId: activity?.subjectId ?? null, topicId: activity?.topicId ?? null,
            startedAt: focus.startedAt ?? at, endedAt: at, durationMinutes, status, note: note ?? focus.note, createdAt: at, updatedAt: at,
        });
        let activities = state.activities;
        let evidence = state.evidence;
        if (status === "completed") {
            activities = state.activities.map((candidate) => candidate.id === activity?.id ? { ...candidate, status: "completed", updatedAt: at } : candidate);
            evidence = [...state.evidence, createEvidence({ planId: activePlanId(), subjectId: activity?.subjectId ?? null, topicId: activity?.topicId ?? null, activityId: activity?.id ?? null, sessionId: session.id, kind: "focus-session-completed", observedAt: at, value: { durationMinutes: durationMinutes ?? 0 }, provenance: "session", createdAt: at, updatedAt: at })];
        }
        commit({ ...state, activities, sessions: [...state.sessions, session], evidence, focus: createFocusState({ durationMinutes: focus.durationMinutes }) }, `focus-${status}`);
        return session;
    }

    return {
        getState: () => state,
        getSnapshot: () => cloneState(state),
        getPersistenceStatus: () => ({ ...persistence }),
        getFocusSnapshot: (at) => focusSnapshot(at),
        subscribe(listener) { if (typeof listener !== "function") throw new TypeError("listener must be a function"); listeners.add(listener); return () => listeners.delete(listener); },
        addActivity(input) {
            const at = nowIso();
            const activity = createActivity({ ...input, planId: input.planId ?? activePlanId(), createdAt: input.createdAt ?? at, updatedAt: input.updatedAt ?? at });
            commit({ ...state, activities: [...state.activities, activity] }, "activity-added"); return activity;
        },
        startFocus({ activityId = null, durationMinutes = null, note = null } = {}) {
            const at = nowIso();
            const current = focusSnapshot(at);
            if (current.status === "running") return current;
            const activity = activityId ? state.activities.find((candidate) => candidate.id === activityId && candidate.status === "planned") : selectActivitiesForQueue(state, "today")[0] ?? null;
            if (activityId && !activity) throw new TypeError("Focus activity must reference a planned activity");
            const configuredMinutes = durationMinutes ?? current.durationMinutes ?? activity?.durationMinutes ?? 25;
            const baseRemaining = current.status === "paused" ? current.remainingMs : Number(configuredMinutes) * 60_000;
            const startedAt = current.status === "paused" ? current.startedAt : at;
            const elapsedMs = current.status === "paused" ? current.elapsedMs : 0;
            const next = createFocusState({ status: "running", activityId: activity?.id ?? current.activityId, durationMinutes: configuredMinutes, remainingMs: baseRemaining, elapsedMs, startedAt, lastResumedAt: at, endsAt: new Date(isoMs(at) + baseRemaining).toISOString(), note: note ?? current.note });
            commit({ ...state, focus: next }, current.status === "paused" ? "focus-resumed" : "focus-started"); return next;
        },
        pauseFocus() {
            const at = nowIso(); const current = focusSnapshot(at); if (current.status !== "running") return current;
            const runMs = current.lastResumedAt ? Math.max(0, isoMs(at) - isoMs(current.lastResumedAt)) : 0;
            const next = createFocusState({ ...current, status: "paused", remainingMs: current.remainingMs, elapsedMs: current.elapsedMs + runMs, lastResumedAt: null, endsAt: null });
            commit({ ...state, focus: next }, "focus-paused"); return next;
        },
        completeFocus({ note = null } = {}) { return finishFocus("completed", note); },
        cancelFocus({ note = null } = {}) { return finishFocus("cancelled", note); },
        resetFocus() { const current = focusSnapshot(); const next = createFocusState({ durationMinutes: current.durationMinutes }); commit({ ...state, focus: next }, "focus-reset"); return next; },
        setFocusNote(note) { const next = createFocusState({ ...state.focus, note }); commit({ ...state, focus: next }, "focus-note-updated"); return next; },
        setStudyCycle(subjectIds) {
            const allowed = new Set(state.subjects.filter((subject) => subject.planId === activePlanId() && !subject.archived).map((subject) => subject.id));
            const ids = [...new Set(subjectIds)];
            if (ids.some((id) => !allowed.has(id))) throw new TypeError("Study cycle subjects must be active subjects in the current plan");
            const cycle = createStudyCycleState({ subjectIds: ids, cursor: 0 });
            commit({ ...state, studyCycle: cycle }, "study-cycle-updated"); return cycle;
        },
        advanceStudyCycle() {
            const cycle = createStudyCycleState(state.studyCycle ?? {});
            if (!cycle.subjectIds.length) return cycle;
            const next = createStudyCycleState({ ...cycle, cursor: (cycle.cursor + 1) % cycle.subjectIds.length });
            commit({ ...state, studyCycle: next }, "study-cycle-advanced"); return next;
        },
        addSubject(input) {
            const at=nowIso(); const existing=state.subjects.filter((subject)=>subject.planId===activePlanId());
            const subject=createSubject({ ...input, planId: activePlanId(), order: input.order ?? existing.length, createdAt: at, updatedAt: at });
            commit({ ...state, subjects:[...state.subjects, subject] }, "subject-added"); return subject;
        },
        updateSubject(id, changes) {
            const current=state.subjects.find((subject)=>subject.id===id); if(!current) throw new TypeError("Subject not found"); const at=nowIso();
            const updated=createSubject({ ...current, ...changes, id: current.id, planId: current.planId, createdAt: current.createdAt, updatedAt: at });
            commit({ ...state, subjects:state.subjects.map((subject)=>subject.id===id?updated:subject) }, "subject-updated"); return updated;
        },
        deleteSubject(id) {
            const current=state.subjects.find((subject)=>subject.id===id); if(!current) throw new TypeError("Subject not found");
            if(state.topics.some((topic)=>topic.subjectId===id)) throw new TypeError("Archive or remove this subject's topics before deleting it");
            if(referenceCollections(state).some((collection)=>collection.some((entity)=>entity.subjectId===id))) throw new TypeError("Subject has study history or linked records and cannot be deleted; archive it instead");
            commit({ ...state, subjects:state.subjects.filter((subject)=>subject.id!==id) }, "subject-deleted");
        },
        moveSubject(id, direction) {
            const ordered=state.subjects.filter((subject)=>subject.planId===activePlanId()).sort((a,b)=>(a.order??0)-(b.order??0)); const index=ordered.findIndex((subject)=>subject.id===id);
            const target=index+(direction==="up"?-1:1); if(index<0||target<0||target>=ordered.length) return; [ordered[index],ordered[target]]=[ordered[target],ordered[index]];
            const ranks=new Map(ordered.map((subject,i)=>[subject.id,i])); const at=nowIso(); commit({ ...state, subjects:state.subjects.map((subject)=>ranks.has(subject.id)?{...subject,order:ranks.get(subject.id),updatedAt:at}:subject) }, "subject-reordered");
        },
        addTopic(input) {
            const subject=state.subjects.find((candidate)=>candidate.id===input.subjectId && candidate.planId===activePlanId()); if(!subject) throw new TypeError("Topic requires a subject in the active plan");
            const at=nowIso(); const siblings=state.topics.filter((topic)=>topic.subjectId===subject.id); const topic=createTopic({ ...input, planId:activePlanId(), order:input.order??siblings.length, createdAt:at, updatedAt:at });
            commit({ ...state, topics:[...state.topics,topic] }, "topic-added"); return topic;
        },
        updateTopic(id, changes) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) throw new TypeError("Topic not found"); const subjectId=changes.subjectId??current.subjectId; const subject=state.subjects.find((candidate)=>candidate.id===subjectId && candidate.planId===current.planId); if(!subject) throw new TypeError("Topic subject must belong to the same plan"); const at=nowIso();
            const updated=createTopic({ ...current,...changes,id:current.id,planId:current.planId,subjectId,createdAt:current.createdAt,updatedAt:at }); commit({ ...state,topics:state.topics.map((topic)=>topic.id===id?updated:topic) },"topic-updated"); return updated;
        },
        deleteTopic(id) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) throw new TypeError("Topic not found");
            if(referenceCollections(state).some((collection)=>collection.some((entity)=>entity.topicId===id)) || state.reviews.some((review)=>review.sourceType==="topic"&&review.sourceId===id)) throw new TypeError("Topic has study history or linked records and cannot be deleted; archive it instead");
            commit({ ...state,topics:state.topics.filter((topic)=>topic.id!==id) },"topic-deleted");
        },
        moveTopic(id, direction) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) return; const ordered=state.topics.filter((topic)=>topic.subjectId===current.subjectId).sort((a,b)=>(a.order??0)-(b.order??0)); const index=ordered.findIndex((topic)=>topic.id===id); const target=index+(direction==="up"?-1:1); if(index<0||target<0||target>=ordered.length)return; [ordered[index],ordered[target]]=[ordered[target],ordered[index]]; const ranks=new Map(ordered.map((topic,i)=>[topic.id,i])); const at=nowIso(); commit({ ...state,topics:state.topics.map((topic)=>ranks.has(topic.id)?{...topic,order:ranks.get(topic.id),updatedAt:at}:topic) },"topic-reordered");
        },
        previewSchedule(options = {}) { return buildSchedulePreview(state, { ...options, at: options.at ?? nowIso() }); },
        applySchedulePreview(preview) {
            if (!preview || typeof preview !== "object" || !Array.isArray(preview.operations)) throw new TypeError("Schedule preview is required");
            if (preview.baseRevision !== state.revision) throw new TypeError("Schedule preview is stale; preview again before applying");
            const at = nowIso(); let activities = state.activities.map((activity) => ({ ...activity }));
            for (const operation of preview.operations) {
                if (operation.action === "move") {
                    const index = activities.findIndex((activity) => activity.id === operation.activityId && activity.status === "planned");
                    if (index < 0) throw new TypeError(`Schedule move references missing planned activity: ${operation.activityId}`);
                    const scheduledFor = operation.to; const today = at.slice(0,10);
                    activities[index] = { ...activities[index], scheduledFor, queue: scheduledFor === today ? "today" : "future", updatedAt: at };
                } else if (operation.action === "add") {
                    const activity = createActivity({ ...operation.activity, planId: activePlanId(), scheduledFor: operation.scheduledFor, origin: operation.activity.origin ?? "schedule", createdAt: at, updatedAt: at });
                    activities.push(activity);
                } else throw new TypeError(`Unsupported schedule operation: ${operation.action}`);
            }
            commit({ ...state, activities }, "schedule-preview-applied");
            return preview;
        },
        addQuestion(input) {
            const at = nowIso(); const question = createQuestion({ ...input, planId: input.planId ?? activePlanId(), createdAt: at, updatedAt: at });
            commit({ ...state, questions: [...state.questions, question] }, "question-added"); return question;
        },
        answerQuestion(questionId, answer, { durationSeconds = null, errorCategory = "other", note = null } = {}) {
            const at = nowIso(); const question = state.questions.find((candidate) => candidate.id === questionId && candidate.planId === activePlanId());
            if (!question) throw new TypeError("Question not found in the active plan");
            const result = gradeQuestion(question, answer);
            const attempt = createEvidence({
                planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, questionId: question.id, kind: "question-attempt", observedAt: at, provenance: "question",
                value: { gradable: result.gradable, correct: result.correct, submitted: result.submitted, expected: result.expected, durationSeconds: durationSeconds == null ? null : Math.max(0, Number(durationSeconds) || 0) }, createdAt: at, updatedAt: at,
            });
            let reviews = state.reviews; let errorEntries = state.errorEntries; let review = null; let errorEntry = null;
            if (result.gradable && result.correct === false) {
                review = state.reviews.find((candidate) => candidate.status === "due" && candidate.sourceType === "question" && candidate.sourceId === question.id) ?? null;
                if (!review) {
                    review = createReview({ planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, dueAt: new Date(Date.parse(at)+86_400_000).toISOString(), status: "due", sourceType: "question", sourceId: question.id, createdAt: at, updatedAt: at });
                    reviews = [...reviews, review];
                }
                const existing = state.errorEntries.find((candidate) => candidate.status === "open" && candidate.questionId === question.id && candidate.category === errorCategory);
                if (existing) {
                    errorEntry = createErrorEntry({ ...existing, recurrenceCount: existing.recurrenceCount + 1, reviewId: review.id, note: note ?? existing.note, updatedAt: at });
                    errorEntries = state.errorEntries.map((candidate) => candidate.id === existing.id ? errorEntry : candidate);
                } else {
                    errorEntry = createErrorEntry({ planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, questionId: question.id, reviewId: review.id, category: errorCategory, note, observedAt: at, createdAt: at, updatedAt: at });
                    errorEntries = [...state.errorEntries, errorEntry];
                }
            }
            commit({ ...state, evidence: [...state.evidence, attempt], reviews, errorEntries }, "question-answered");
            return { result, attempt, review, errorEntry };
        },
        completeReview(reviewId, { rating = null } = {}) {
            const at = nowIso(); const review = state.reviews.find((candidate) => candidate.id === reviewId && candidate.planId === activePlanId());
            if (!review) throw new TypeError("Review not found in the active plan");
            if (review.status !== "due") return review;
            const completed = createReview({ ...review, status: "completed", updatedAt: at });
            const evidence = createEvidence({ planId: activePlanId(), subjectId: review.subjectId, topicId: review.topicId, kind: "review-completed", observedAt: at, provenance: "user", value: { reviewId: review.id, rating }, createdAt: at, updatedAt: at });
            commit({ ...state, reviews: state.reviews.map((candidate) => candidate.id === review.id ? completed : candidate), evidence: [...state.evidence, evidence] }, "review-completed");
            return completed;
        },
        resolveError(errorId, { note = null } = {}) {
            const at = nowIso(); const entry = state.errorEntries.find((candidate) => candidate.id === errorId && candidate.planId === activePlanId());
            if (!entry) throw new TypeError("Error entry not found in the active plan");
            const resolved = createErrorEntry({ ...entry, status: "resolved", resolvedAt: at, note: note ?? entry.note, updatedAt: at });
            commit({ ...state, errorEntries: state.errorEntries.map((candidate) => candidate.id === errorId ? resolved : candidate) }, "error-resolved");
            return resolved;
        },
        replaceState(nextState, reason="state-replaced") { return commit(nextState,reason); },
        resetLocalState() {
            const cleared=typeof repository.clear==="function"?repository.clear():{status:"cleared"}; state=initialStateFactory({now:nowIso()}); const saved=repository.save(state); const status=saved.status==="saved"?"cleared":saved.status; persistence={status,message:saved.status==="saved"?persistenceMessage("cleared"):persistenceMessage(saved.status,saved.reason??cleared.reason),durable:saved.status==="saved"}; notify("state-reset"); return state;
        },
    };
}
