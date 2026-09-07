// TypeScript migration source. Public contracts are declared in types.ts.
// @ts-nocheck -- legacy internals preserved byte-for-byte for W14 behavioral parity; new/changed code is typed.
"use strict";

import {
    createActivity, createErrorEntry, createEvidence, createFocusState, createInitialStudyState, createQuestion, createReview, createStudySession, createPlan as makePlan,
    createStudyCycleState, createSubject, createTopic, createGoal, createMaterial, focusRemainingMs, gradeQuestion, migrateStudyState, selectActivitiesForQueue, validateStudyState,
} from "./domain.js";
import { buildPlanContentImportPreview } from "./content_import.js";
import { buildQuestionImportPreview } from "./question_import.js";
import { buildModeImportPreview } from "./mode_import.js";
import { buildContentCleanupPreview } from "./intelligence.js";
import { buildAdaptiveSchedulePreview, normalizeScheduleConfiguration } from "./schedule.js";
import { assessProgrammingAttempt, buildExamNoticePreview, buildLanguageReviewPrompt, createGeneralSkillDrill, createLanguageExpression, createProgrammingExercise, createReadingItem, normalizeMockBlueprint, previewMockExam, scoreMockExam, PUBLIC_EXAM_STARTER_TEMPLATES } from "./modes.js";

function cloneState(state) { return JSON.parse(JSON.stringify(state)); }
function isoMs(value) { const ms = Date.parse(value); if (!Number.isFinite(ms)) throw new TypeError(`Invalid timestamp: ${value}`); return ms; }
function persistenceMessage(status, reason = null) {
    if (status === "loaded") return "Local state loaded from this browser.";
    if (status === "migrated") return "Older local state migrated safely to the current schema.";
    if (status === "recovered") return reason ?? "Recovered the last verified local checkpoint.";
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
    function reviewIntervals(planId = activePlanId()) {
        const plan = state.plans.find((candidate) => candidate.id === planId);
        const configured = plan?.mode === "EXAM_PLAN" ? plan.configuration.publicExam?.reviewIntervalsDays : null;
        return Array.isArray(configured) && configured.length ? [...configured] : [1, 7, 30];
    }
    function normalizeReviewGrade(value) {
        if (value == null || value === "") return 3;
        const labels = { again: 1, hard: 2, good: 3, easy: 5 };
        if (typeof value === "string" && value.toLowerCase() in labels) return labels[value.toLowerCase()];
        const grade = Number(value);
        if (!Number.isFinite(grade)) throw new TypeError("Review rating must be a grade from 0 to 5");
        return Math.max(0, Math.min(5, Math.round(grade)));
    }
    function planCycle(planId, sourceState = state) {
        const allowed = sourceState.subjects.filter((subject) => subject.planId === planId && !subject.archived).map((subject) => subject.id);
        const raw = sourceState.studyCycles?.[planId] ?? { subjectIds: allowed, cursor: 0 };
        const cycle = createStudyCycleState(raw); const filtered = cycle.subjectIds.filter((subjectId) => allowed.includes(subjectId));
        return createStudyCycleState({ subjectIds: filtered.length ? filtered : allowed, cursor: cycle.cursor });
    }
    function withPlanCycle(sourceState, planId, cycle) {
        const canonical = createStudyCycleState(cycle); const studyCycles = { ...(sourceState.studyCycles ?? {}), [planId]: canonical };
        return { ...sourceState, studyCycles, studyCycle: sourceState.activePlanId === planId ? canonical : sourceState.studyCycle };
    }
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
        createPlan(input, { activate = true } = {}) {
            const at=nowIso(); const plan=makePlan({ ...input, mode:input.mode??"GENERAL_SKILL", status:"active", createdAt:at, updatedAt:at });
            const cycle=createStudyCycleState(); const shouldActivate=activate||!state.activePlanId;
            const next={...state,plans:[...state.plans,plan],studyCycles:{...(state.studyCycles??{}),[plan.id]:cycle},activePlanId:shouldActivate?plan.id:state.activePlanId,studyCycle:shouldActivate?cycle:state.studyCycle};
            commit(next,"plan-created"); return plan;
        },
        renamePlan(planId, name) {
            const current=state.plans.find((plan)=>plan.id===planId); if(!current)throw new TypeError("Plan not found"); const at=nowIso();
            const updated=makePlan({...current,name,createdAt:current.createdAt,updatedAt:at}); commit({...state,plans:state.plans.map((plan)=>plan.id===planId?updated:plan)},"plan-renamed"); return updated;
        },
        switchPlan(planId) {
            const target=state.plans.find((plan)=>plan.id===planId); if(!target)throw new TypeError("Plan not found"); if(target.status==="archived")throw new TypeError("Archived plans must be restored before they can be activated");
            if(state.focus?.status!=="idle")throw new TypeError("Finish or cancel the current focus session before switching plans");
            const cycle=planCycle(planId); commit({...state,activePlanId:planId,studyCycle:cycle,studyCycles:{...(state.studyCycles??{}),[planId]:cycle}},"plan-switched"); return target;
        },
        setPlanArchived(planId, archived = true) {
            const current=state.plans.find((plan)=>plan.id===planId); if(!current)throw new TypeError("Plan not found"); const at=nowIso();
            if(Boolean(archived)===(current.status==="archived"))return current;
            if(archived&&planId===state.activePlanId&&state.focus?.status!=="idle")throw new TypeError("Finish or cancel the current focus session before archiving the active plan");
            let activePlanId=state.activePlanId; let studyCycle=state.studyCycle;
            if(archived&&planId===state.activePlanId){const replacement=state.plans.find((plan)=>plan.id!==planId&&plan.status!=="archived");if(!replacement)throw new TypeError("Cannot archive the only active plan");activePlanId=replacement.id;studyCycle=planCycle(replacement.id);}
            const updated=makePlan({...current,status:archived?"archived":"active",createdAt:current.createdAt,updatedAt:at}); const cycles={...(state.studyCycles??{})}; if(!cycles[planId])cycles[planId]=planCycle(planId);
            commit({...state,plans:state.plans.map((plan)=>plan.id===planId?updated:plan),activePlanId,studyCycle,studyCycles:cycles},archived?"plan-archived":"plan-restored"); return updated;
        },
        duplicatePlan(planId, { name = null, activate = false } = {}) {
            const source=state.plans.find((plan)=>plan.id===planId); if(!source)throw new TypeError("Plan not found"); const at=nowIso();
            const plan=makePlan({...source,id:undefined,name:name??`${source.name} copy`,status:"active",configuration:cloneState(source.configuration),createdAt:at,updatedAt:at});
            const subjectMap=new Map(); const subjects=[]; for(const item of state.subjects.filter((subject)=>subject.planId===planId).sort((a,b)=>(a.order??0)-(b.order??0))){const copy=createSubject({...item,id:undefined,planId:plan.id,createdAt:at,updatedAt:at});subjectMap.set(item.id,copy.id);subjects.push(copy);}
            const topicMap=new Map(); const topics=[]; let pending=state.topics.filter((topic)=>topic.planId===planId).map((topic)=>({...topic})); let guard=0;
            while(pending.length&&guard++<pending.length+5){let progressed=false;for(let index=pending.length-1;index>=0;index-=1){const item=pending[index];if(item.parentTopicId&&!topicMap.has(item.parentTopicId))continue;const copy=createTopic({...item,id:undefined,planId:plan.id,subjectId:subjectMap.get(item.subjectId),parentTopicId:item.parentTopicId?topicMap.get(item.parentTopicId):null,createdAt:at,updatedAt:at});topicMap.set(item.id,copy.id);topics.push(copy);pending.splice(index,1);progressed=true;}if(!progressed)throw new TypeError("Cannot duplicate plan because its topic hierarchy is invalid");}
            const questions=state.questions.filter((question)=>question.planId===planId).map((item)=>createQuestion({...item,id:undefined,planId:plan.id,subjectId:item.subjectId?subjectMap.get(item.subjectId)??null:null,topicId:item.topicId?topicMap.get(item.topicId)??null:null,createdAt:at,updatedAt:at}));
            const goals=state.goals.filter((goal)=>goal.planId===planId).map((item)=>createGoal({...item,id:undefined,planId:plan.id,createdAt:at,updatedAt:at}));
            const activities=state.activities.filter((activity)=>activity.planId===planId&&activity.status==="planned").map((item)=>createActivity({...item,id:undefined,planId:plan.id,subjectId:item.subjectId?subjectMap.get(item.subjectId)??null:null,topicId:item.topicId?topicMap.get(item.topicId)??null:null,createdAt:at,updatedAt:at}));
            const sourceCycle=planCycle(planId); const cycle=createStudyCycleState({subjectIds:sourceCycle.subjectIds.map((id)=>subjectMap.get(id)).filter(Boolean),cursor:sourceCycle.cursor}); const cycles={...(state.studyCycles??{}),[plan.id]:cycle};
            commit({...state,plans:[...state.plans,plan],subjects:[...state.subjects,...subjects],topics:[...state.topics,...topics],questions:[...state.questions,...questions],goals:[...state.goals,...goals],activities:[...state.activities,...activities],studyCycles:cycles,activePlanId:activate?plan.id:state.activePlanId,studyCycle:activate?cycle:state.studyCycle},"plan-duplicated"); return plan;
        },
        deletePlan(planId) {
            const current=state.plans.find((plan)=>plan.id===planId); if(!current)throw new TypeError("Plan not found"); if(planId===state.activePlanId)throw new TypeError("Switch to another plan before deleting this plan"); if(current.status!=="archived")throw new TypeError("Archive the plan before deleting it");
            const owned=[state.subjects,state.topics,state.activities,state.sessions,state.goals,state.materials,state.reviews,state.questions,state.evidence,state.errorEntries].some((collection)=>collection.some((entity)=>entity.planId===planId)); if(owned)throw new TypeError("Plan still owns content or history and cannot be deleted; keep it archived instead");
            const cycles={...(state.studyCycles??{})};delete cycles[planId];commit({...state,plans:state.plans.filter((plan)=>plan.id!==planId),studyCycles:cycles},"plan-deleted");
        },
        setPlanMode(mode) {
            const current=state.plans.find((plan)=>plan.id===activePlanId()); if(!current) throw new TypeError("Active plan not found"); const at=nowIso();
            const updated=makePlan({ ...current, mode, configuration:{}, createdAt:current.createdAt, updatedAt:at });
            commit({ ...state, plans:state.plans.map((plan)=>plan.id===current.id?updated:plan) }, "plan-mode-updated"); return updated;
        },
        setPracticeConfiguration(input) {const current=state.plans.find(plan=>plan.id===activePlanId());if(!current)throw new TypeError("Active plan not found");const at=nowIso();const practice={...current.configuration.practice,...input};const updated=makePlan({...current,configuration:{...current.configuration,practice},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(p=>p.id===current.id?updated:p)},"practice-config-updated");return updated;},
        savePracticeSet(name,filter={},questionIds=[]) {const current=state.plans.find(plan=>plan.id===activePlanId());if(!current)throw new TypeError("Active plan not found");const trimmed=String(name??"").trim();if(!trimmed)throw new TypeError("Practice set name is required");const at=nowIso();const ids=(questionIds.length?questionIds:state.questions.filter(q=>q.planId===current.id).map(q=>q.id)).filter(id=>state.questions.some(q=>q.id===id&&q.planId===current.id));const set={id:`practice-set-${Date.now().toString(36)}`,name:trimmed,questionIds:[...new Set(ids)],filter:{...filter},createdAt:at};const updated=makePlan({...current,configuration:{...current.configuration,practice:{...current.configuration.practice,savedSets:[...current.configuration.practice.savedSets,set]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(p=>p.id===current.id?updated:p)},"practice-set-saved");return set;},
        deletePracticeSet(id) {const current=state.plans.find(plan=>plan.id===activePlanId());if(!current)throw new TypeError("Active plan not found");const at=nowIso();const updated=makePlan({...current,configuration:{...current.configuration,practice:{...current.configuration.practice,savedSets:current.configuration.practice.savedSets.filter(x=>x.id!==id)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(p=>p.id===current.id?updated:p)},"practice-set-deleted");return updated;},
        setPublicExamStudyPhase(phase) {if(!["pre-edital","post-edital","final-stretch"].includes(String(phase)))throw new TypeError("Unsupported public-exam study phase");const current=state.plans.find(plan=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Study phase requires EXAM_PLAN");const at=nowIso();const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,studyPhase:phase}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(p=>p.id===current.id?updated:p)},"public-exam-phase-updated");return updated;},
        applyPublicExamStarterTemplate(templateId) {const template=PUBLIC_EXAM_STARTER_TEMPLATES[templateId];if(!template)throw new TypeError("Unknown public-exam starter template");const current=state.plans.find(plan=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Starter template requires EXAM_PLAN");const at=nowIso();const updated=makePlan({...current,configuration:{...current.configuration,practice:{...current.configuration.practice,weeklyQuestionQuota:template.weeklyQuestionQuota},publicExam:{...current.configuration.publicExam,reviewIntervalsDays:[...template.reviewIntervalsDays],mockBlueprint:{...current.configuration.publicExam.mockBlueprint,totalQuestions:template.mockQuestions}}},createdAt:current.createdAt,updatedAt:at});const wanted=[{name:"Weekly questions",metric:"graded-questions",target:template.weeklyQuestionQuota,period:"weekly"},{name:"Weekly study time",metric:"study-minutes",target:300,period:"weekly"}];let goals=[...state.goals];for(const g of wanted)if(!goals.some(x=>x.planId===current.id&&x.name===g.name))goals.push(createGoal({...g,planId:current.id,active:true,createdAt:at,updatedAt:at}));commit({...state,plans:state.plans.map(p=>p.id===current.id?updated:p),goals},"public-exam-starter-template-applied");return updated;},
        updatePublicExamTarget(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId()); if(!current||current.mode!=="EXAM_PLAN") throw new TypeError("Public-exam target requires an EXAM_PLAN"); const at=nowIso();
            const target={...current.configuration.publicExam.target,...input}; const updated=makePlan({ ...current, examDate:target.examDate??current.examDate, configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,target}}, createdAt:current.createdAt, updatedAt:at });
            commit({ ...state, plans:state.plans.map((plan)=>plan.id===current.id?updated:plan) }, "public-exam-target-updated"); return updated;
        },
        addExamStage(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId()); if(!current||current.mode!=="EXAM_PLAN") throw new TypeError("Exam stages require an EXAM_PLAN"); const at=nowIso();
            const stage={id:input.id??`stage-${Date.now().toString(36)}`,name:String(input.name??"").trim(),date:input.date??null,status:input.status??"planned"}; if(!stage.name)throw new TypeError("Exam stage name is required");
            const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,stages:[...current.configuration.publicExam.stages,stage]}},createdAt:current.createdAt,updatedAt:at});
            commit({...state,plans:state.plans.map((plan)=>plan.id===current.id?updated:plan)},"exam-stage-added");return updated;
        },
        previewExamNotice(source) { return buildExamNoticePreview(state,source); },
        applyExamNoticePreview(preview) {
            if(!preview||preview.baseRevision!==state.revision) throw new TypeError("Edital preview is stale; preview again before applying"); const current=state.plans.find((plan)=>plan.id===activePlanId()); if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Edital import requires an EXAM_PLAN"); const at=nowIso();
            const currentFingerprint=(()=>{const ss=state.subjects.filter(x=>x.planId===current.id).map(x=>`${x.name}|${x.archived}`).sort();const tt=state.topics.filter(x=>x.planId===current.id).map(x=>`${x.subjectId}|${x.parentTopicId??""}|${x.name}|${x.status}`).sort();let h=2166136261;const raw=JSON.stringify([ss,tt]);for(let i=0;i<raw.length;i++){h^=raw.charCodeAt(i);h=Math.imul(h,16777619);}return (h>>>0).toString(16).padStart(8,"0")})();if(preview.syllabusFingerprint!==currentFingerprint)throw new TypeError("Edital preview syllabus changed; preview again before applying");
            let subjects=state.subjects.map(x=>({...x})),topics=state.topics.map(x=>({...x}));let added=0,archived=0,reactivated=0,unchanged=0;const desiredSubjects=new Set(),desiredTopics=new Set();
            for(const row of preview.subjects){let subject=subjects.find(x=>x.planId===current.id&&x.name.trim().toLowerCase()===row.name.trim().toLowerCase());if(!subject){subject=createSubject({planId:current.id,name:row.name,order:subjects.filter(x=>x.planId===current.id).length,createdAt:at,updatedAt:at});subjects.push(subject);added++;}else if(subject.archived){subject=createSubject({...subject,archived:false,createdAt:subject.createdAt,updatedAt:at});subjects=subjects.map(x=>x.id===subject.id?subject:x);reactivated++;}else unchanged++;desiredSubjects.add(subject.id);for(const path of row.topicPaths??row.topics.map(x=>[x])){let parentId=null;for(const name of path){let topic=topics.find(x=>x.subjectId===subject.id&&(x.parentTopicId??null)===parentId&&x.name.trim().toLowerCase()===name.trim().toLowerCase());if(!topic){topic=createTopic({planId:current.id,subjectId:subject.id,parentTopicId:parentId,name,order:topics.filter(x=>x.subjectId===subject.id&&(x.parentTopicId??null)===parentId).length,createdAt:at,updatedAt:at});topics.push(topic);added++;}else if(topic.status==="archived"){topic=createTopic({...topic,status:"not-started",createdAt:topic.createdAt,updatedAt:at});topics=topics.map(x=>x.id===topic.id?topic:x);reactivated++;}else unchanged++;desiredTopics.add(topic.id);parentId=topic.id;}}}
            subjects=subjects.map(x=>x.planId===current.id&&!desiredSubjects.has(x.id)&&!x.archived?(archived++,createSubject({...x,archived:true,createdAt:x.createdAt,updatedAt:at})):x);topics=topics.map(x=>x.planId===current.id&&!desiredTopics.has(x.id)&&x.status!=="archived"?(archived++,createTopic({...x,status:"archived",createdAt:x.createdAt,updatedAt:at})):x);
            const revision={id:`notice-${Date.now().toString(36)}`,acceptedAt:at,sourceHash:preview.sourceHash,added,archived,reactivated,unchanged,previousRevisionId:preview.previousRevisionId??null,syllabusFingerprint:preview.syllabusFingerprint}; const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,studyPhase:"post-edital",noticeRevisions:[...current.configuration.publicExam.noticeRevisions,revision]}},createdAt:current.createdAt,updatedAt:at});
            commit({...state,subjects,topics,plans:state.plans.map(x=>x.id===current.id?updated:x)},"exam-notice-applied");return updated;
        },
        addLegislation(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Legislation requires an EXAM_PLAN");const at=nowIso();const title=String(input.title??"").trim();if(!title)throw new TypeError("Legislation title is required");
            const document={id:`law-${Date.now().toString(36)}`,title,reference:input.reference??null,notes:input.notes??null,provisions:(input.provisions??[]).map((p,i)=>({id:`provision-${Date.now().toString(36)}-${i}`,label:String(p.label??`Provision ${i+1}`),text:String(p.text??""),status:"unseen"}))};const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,legislation:[...current.configuration.publicExam.legislation,document]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"legislation-added");return updated;
        },
        verifyLegislation(documentId,verifiedAt=nowIso()) {const current=state.plans.find(plan=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Legislation verification requires EXAM_PLAN");const docs=current.configuration.publicExam.legislation;const doc=docs.find(x=>x.id===documentId);if(!doc)throw new TypeError("Legislation document not found");const at=nowIso();const updatedDoc={...doc,verifiedAt:new Date(verifiedAt).toISOString()};const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,legislation:docs.map(x=>x.id===documentId?updatedDoc:x)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"legislation-verified");return updated;},
        linkLegislationEvidence(documentId,input) {const current=state.plans.find(plan=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Legislation evidence link requires EXAM_PLAN");const docs=current.configuration.publicExam.legislation;const doc=docs.find(x=>x.id===documentId);if(!doc)throw new TypeError("Legislation document not found");if(!doc.provisions.some(x=>x.id===input.provisionId))throw new TypeError("Legislation provision not found");if(input.questionId&&!state.questions.some(x=>x.id===input.questionId&&x.planId===current.id))throw new TypeError("Question evidence link does not belong to active plan");if(input.errorId&&!state.errorEntries.some(x=>x.id===input.errorId&&x.planId===current.id))throw new TypeError("Error evidence link does not belong to active plan");if(input.reviewId&&!state.reviews.some(x=>x.id===input.reviewId&&x.planId===current.id))throw new TypeError("Review evidence link does not belong to active plan");const at=nowIso();const link={id:`law-link-${Date.now().toString(36)}`,provisionId:input.provisionId,questionId:input.questionId??null,errorId:input.errorId??null,reviewId:input.reviewId??null,note:input.note??null,createdAt:at};const updatedDoc={...doc,evidenceLinks:[...doc.evidenceLinks,link]};const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,legislation:docs.map(x=>x.id===documentId?updatedDoc:x)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"legislation-evidence-linked");return updated;},
        setReviewIntervalsDays(intervals) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Review intervals require an EXAM_PLAN");
            const values=Array.isArray(intervals)?intervals.map(Number):[];if(values.length<1||values.length>8||values.some((value)=>!Number.isInteger(value)||value<1||value>365)||values.some((value,index)=>index>0&&value<values[index-1]))throw new TypeError("Review intervals must contain 1 to 8 non-decreasing whole-day values from 1 to 365");
            const at=nowIso();const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,reviewIntervalsDays:values}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"review-policy-updated");return updated;
        },
        setMockBlueprint(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="EXAM_PLAN")throw new TypeError("Mock blueprint requires an EXAM_PLAN");const at=nowIso();const blueprint=normalizeMockBlueprint({...current.configuration.publicExam.mockBlueprint,...input});const updated=makePlan({...current,configuration:{...current.configuration,publicExam:{...current.configuration.publicExam,mockBlueprint:blueprint}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"mock-blueprint-updated");return updated;
        },
        recordMockExam(preview, answers) {
            if(!preview||preview.baseRevision!==state.revision)throw new TypeError("Mock preview is stale; preview again before recording");if(!preview.ready)throw new TypeError("Mock preview cannot be recorded because the question bank is insufficient");const at=nowIso();const score=scoreMockExam(preview,answers,state);const evidence=createEvidence({planId:activePlanId(),kind:"mock-exam-result",observedAt:at,provenance:"user",value:{...score,blueprint:preview.blueprint,questionIds:preview.questionIds},createdAt:at,updatedAt:at});commit({...state,evidence:[...state.evidence,evidence]},"mock-exam-recorded");return evidence;
        },
        addProgrammingExercise(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="PROGRAMMING_COURSE")throw new TypeError("Programming exercises require PROGRAMMING_COURSE mode");const at=nowIso();const exercise=createProgrammingExercise({...input,language:input.language??current.configuration.programming.language});const updated=makePlan({...current,configuration:{...current.configuration,programming:{...current.configuration.programming,exercises:[...current.configuration.programming.exercises,exercise]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"programming-exercise-added");return updated;
        },
        updateProgrammingExercise(id,changes) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="PROGRAMMING_COURSE")throw new TypeError("Programming exercises require PROGRAMMING_COURSE mode");const exists=current.configuration.programming.exercises.find(x=>x.id===id);if(!exists)throw new TypeError("Programming exercise not found");const at=nowIso();const exercise=createProgrammingExercise({...exists,...changes,id:exists.id});const updated=makePlan({...current,configuration:{...current.configuration,programming:{...current.configuration.programming,exercises:current.configuration.programming.exercises.map(x=>x.id===id?exercise:x)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"programming-exercise-updated");return updated;
        },
        recordProgrammingExercise(id,{completed=false,note=null,observedOutput=null}={}) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="PROGRAMMING_COURSE")throw new TypeError("Programming evidence requires PROGRAMMING_COURSE mode");const exercise=current.configuration.programming.exercises.find(x=>x.id===id);if(!exercise)throw new TypeError("Programming exercise not found");const at=nowIso();const assessment=assessProgrammingAttempt(exercise,observedOutput,completed);const evidence=createEvidence({planId:current.id,kind:"programming-exercise",observedAt:at,provenance:"user",value:{...assessment,note},createdAt:at,updatedAt:at});const nextExercise=createProgrammingExercise({...exercise,status:completed?"completed":"attempted"});const updated=makePlan({...current,configuration:{...current.configuration,programming:{...current.configuration.programming,exercises:current.configuration.programming.exercises.map(x=>x.id===id?nextExercise:x)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x),evidence:[...state.evidence,evidence]},"programming-exercise-recorded");return evidence;
        },
        revealProgrammingSolution(id) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="PROGRAMMING_COURSE")throw new TypeError("Programming solution reveal requires PROGRAMMING_COURSE mode");const exercise=current.configuration.programming.exercises.find(x=>x.id===id);if(!exercise)throw new TypeError("Programming exercise not found");const at=nowIso();const evidence=createEvidence({planId:current.id,kind:"programming-solution-revealed",observedAt:at,provenance:"user",value:{exerciseId:id,hasSolution:Boolean(exercise.solutionNotes)},createdAt:at,updatedAt:at});commit({...state,evidence:[...state.evidence,evidence]},"programming-solution-revealed");return evidence;
        },
        setProgrammingProjectCheckpoint(projectId,checkpoint,completed=true) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="PROGRAMMING_COURSE")throw new TypeError("Programming project requires PROGRAMMING_COURSE mode");const project=current.configuration.programming.projects.find(x=>x.id===projectId);if(!project)throw new TypeError("Programming project not found");if(!project.checkpoints.includes(checkpoint))throw new TypeError("Programming checkpoint not found");const done=new Set(project.completedCheckpoints??[]);completed?done.add(checkpoint):done.delete(checkpoint);const next={...project,completedCheckpoints:[...done],status:done.size===project.checkpoints.length&&project.checkpoints.length?"completed":done.size?"active":"planned"};const at=nowIso();const updated=makePlan({...current,configuration:{...current.configuration,programming:{...current.configuration.programming,projects:current.configuration.programming.projects.map(x=>x.id===projectId?next:x)}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"programming-project-progress");return updated;
        },
        previewModeImport(source,format) {return buildModeImportPreview(state,source,format);},
        applyModeImportPreview(preview) {
            if(!preview||preview.baseRevision!==state.revision)throw new TypeError("Mode import preview is stale; preview again before applying");const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!==preview.mode)throw new TypeError("Mode import preview no longer matches the active plan");const accepted=preview.rows.filter(x=>x.status==="add");const at=nowIso();let updated=current;if(current.mode==="PROGRAMMING_COURSE"){const exercises=accepted.map((x,i)=>createProgrammingExercise({...x.payload,id:undefined,language:x.payload.language??current.configuration.programming.language}));updated=makePlan({...current,configuration:{...current.configuration,programming:{...current.configuration.programming,exercises:[...current.configuration.programming.exercises,...exercises]}},createdAt:current.createdAt,updatedAt:at});}else if(current.mode==="LANGUAGE_DECK"){const expressions=accepted.map(x=>createLanguageExpression({...x.payload,id:undefined}));updated=makePlan({...current,configuration:{...current.configuration,language:{...current.configuration.language,expressions:[...current.configuration.language.expressions,...expressions]}},createdAt:current.createdAt,updatedAt:at});}else throw new TypeError("Mode import supports programming or language plans only");commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"mode-import-applied");return updated;
        },
        addLanguageExpression(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="LANGUAGE_DECK")throw new TypeError("Language expressions require LANGUAGE_DECK mode");const at=nowIso();const expression=createLanguageExpression(input);const updated=makePlan({...current,configuration:{...current.configuration,language:{...current.configuration.language,expressions:[...current.configuration.language.expressions,expression]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"language-expression-added");return updated;
        },
        recordLanguageReview(expressionId,direction,rating,{answer=null,note=null}={}) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="LANGUAGE_DECK")throw new TypeError("Language review requires LANGUAGE_DECK mode");const expression=current.configuration.language.expressions.find(x=>x.id===expressionId);if(!expression)throw new TypeError("Language expression not found");if(!current.configuration.language.reviewDirections.includes(direction))throw new TypeError("Unsupported language review direction");const prompt=buildLanguageReviewPrompt(expression,direction);if(!prompt.ready)throw new TypeError(prompt.reason??"Language review direction is not ready");const at=nowIso();const value={expressionId,direction,rating:Math.max(0,Math.min(5,Number(rating)||0)),answer,note,manualReview:prompt.manualReview,expected:prompt.expected};const evidence=createEvidence({planId:current.id,kind:"language-review",observedAt:at,provenance:"user",value,createdAt:at,updatedAt:at});const interval=value.rating>=4?7:value.rating>=3?3:1;const existing=state.reviews.find(r=>r.planId===current.id&&r.status==="due"&&r.sourceType==="language-expression"&&r.sourceId===expressionId);const review=existing?null:createReview({planId:current.id,dueAt:new Date(Date.parse(at)+interval*86400000).toISOString(),status:"due",sourceType:"language-expression",sourceId:expressionId,createdAt:at,updatedAt:at});commit({...state,evidence:[...state.evidence,evidence],reviews:review?[...state.reviews,review]:state.reviews},"language-review-recorded");return evidence;
        },
        addReadingItem(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="READING_STUDY")throw new TypeError("Reading items require READING_STUDY mode");const at=nowIso();const item=createReadingItem(input);const updated=makePlan({...current,configuration:{...current.configuration,reading:{items:[...current.configuration.reading.items,item]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"reading-item-added");return updated;
        },
        updateReadingProgress(id,currentUnit) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="READING_STUDY")throw new TypeError("Reading progress requires READING_STUDY mode");const item=current.configuration.reading.items.find(x=>x.id===id);if(!item)throw new TypeError("Reading item not found");const at=nowIso();const next=createReadingItem({...item,currentUnit,status:Number(currentUnit)>=item.totalUnits?"completed":"reading"});const updated=makePlan({...current,configuration:{...current.configuration,reading:{items:current.configuration.reading.items.map(x=>x.id===id?next:x)}},createdAt:current.createdAt,updatedAt:at});const evidence=createEvidence({planId:current.id,kind:"reading-progress",observedAt:at,provenance:"user",value:{itemId:id,currentUnit:next.currentUnit,totalUnits:next.totalUnits},createdAt:at,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x),evidence:[...state.evidence,evidence]},"reading-progress-recorded");return evidence;
        },
        addGeneralSkillDrill(input) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="GENERAL_SKILL")throw new TypeError("Skill drills require GENERAL_SKILL mode");const at=nowIso();const drill=createGeneralSkillDrill(input);const updated=makePlan({...current,configuration:{...current.configuration,general:{drills:[...current.configuration.general.drills,drill]}},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map(x=>x.id===current.id?updated:x)},"skill-drill-added");return updated;
        },
        recordGeneralSkillDrill(id,value,{note=null}={}) {
            const current=state.plans.find((plan)=>plan.id===activePlanId());if(!current||current.mode!=="GENERAL_SKILL")throw new TypeError("Skill evidence requires GENERAL_SKILL mode");const drill=current.configuration.general.drills.find(x=>x.id===id);if(!drill)throw new TypeError("Skill drill not found");const at=nowIso();const evidence=createEvidence({planId:current.id,kind:"skill-drill",observedAt:at,provenance:"user",value:{drillId:id,value:Number(value),target:drill.target,unit:drill.unit,note},createdAt:at,updatedAt:at});commit({...state,evidence:[...state.evidence,evidence]},"skill-drill-recorded");return evidence;
        },
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
            const planId=activePlanId(); const allowed = new Set(state.subjects.filter((subject) => subject.planId === planId && !subject.archived).map((subject) => subject.id));
            const ids = [...new Set(subjectIds)];
            if (ids.some((id) => !allowed.has(id))) throw new TypeError("Study cycle subjects must be active subjects in the current plan");
            const cycle = createStudyCycleState({ subjectIds: ids, cursor: 0 });
            commit(withPlanCycle(state,planId,cycle), "study-cycle-updated"); return cycle;
        },
        advanceStudyCycle() {
            const planId=activePlanId(); const cycle = createStudyCycleState(state.studyCycle ?? {});
            if (!cycle.subjectIds.length) return cycle;
            const next = createStudyCycleState({ ...cycle, cursor: (cycle.cursor + 1) % cycle.subjectIds.length });
            commit(withPlanCycle(state,planId,next), "study-cycle-advanced"); return next;
        },
        skipStudyCycleSubject() {return this.advanceStudyCycle();},
        moveCurrentCycleSubjectToEnd() {const planId=activePlanId();const cycle=planCycle(planId);if(cycle.subjectIds.length<2)return cycle;const idx=Math.max(0,Math.min(cycle.cursor,cycle.subjectIds.length-1));const ids=[...cycle.subjectIds];const [current]=ids.splice(idx,1);ids.push(current);const next=createStudyCycleState({subjectIds:ids,cursor:Math.min(idx,ids.length-1)});commit(withPlanCycle(state,planId,next),"study-cycle-current-moved-to-end");return next;},
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
            const planId=activePlanId(); const subject=state.subjects.find((candidate)=>candidate.id===input.subjectId && candidate.planId===planId); if(!subject) throw new TypeError("Topic requires a subject in the active plan");
            const parentTopicId=input.parentTopicId??null; if(parentTopicId){const parent=state.topics.find((candidate)=>candidate.id===parentTopicId&&candidate.planId===planId&&candidate.subjectId===subject.id);if(!parent)throw new TypeError("Parent topic must belong to the same subject and active plan");}
            const at=nowIso(); const siblings=state.topics.filter((topic)=>topic.subjectId===subject.id&&(topic.parentTopicId??null)===parentTopicId); const topic=createTopic({ ...input, planId, parentTopicId, order:input.order??siblings.length, createdAt:at, updatedAt:at });
            commit({ ...state, topics:[...state.topics,topic] }, "topic-added"); return topic;
        },
        updateTopic(id, changes) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) throw new TypeError("Topic not found"); const subjectId=changes.subjectId??current.subjectId; const subject=state.subjects.find((candidate)=>candidate.id===subjectId && candidate.planId===current.planId); if(!subject) throw new TypeError("Topic subject must belong to the same plan");
            const parentTopicId=changes.parentTopicId===undefined?(current.parentTopicId??null):(changes.parentTopicId??null); if(parentTopicId===id)throw new TypeError("Topic cannot parent itself"); if(parentTopicId){const parent=state.topics.find((candidate)=>candidate.id===parentTopicId&&candidate.planId===current.planId&&candidate.subjectId===subjectId);if(!parent)throw new TypeError("Parent topic must belong to the same subject and plan");let cursor=parent;const seen=new Set([id]);while(cursor){if(seen.has(cursor.id))throw new TypeError("Topic hierarchy cannot contain a cycle");seen.add(cursor.id);cursor=cursor.parentTopicId?state.topics.find((candidate)=>candidate.id===cursor.parentTopicId):null;}}
            const at=nowIso(); const updated=createTopic({ ...current,...changes,id:current.id,planId:current.planId,subjectId,parentTopicId,createdAt:current.createdAt,updatedAt:at }); commit({ ...state,topics:state.topics.map((topic)=>topic.id===id?updated:topic) },"topic-updated"); return updated;
        },
        deleteTopic(id) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) throw new TypeError("Topic not found");
            if(state.topics.some((topic)=>topic.parentTopicId===id))throw new TypeError("Topic has child topics; move, archive, or remove them before deleting it");
            if(referenceCollections(state).some((collection)=>collection.some((entity)=>entity.topicId===id)) || state.reviews.some((review)=>review.sourceType==="topic"&&review.sourceId===id)) throw new TypeError("Topic has study history or linked records and cannot be deleted; archive it instead");
            commit({ ...state,topics:state.topics.filter((topic)=>topic.id!==id) },"topic-deleted");
        },
        moveTopic(id, direction) {
            const current=state.topics.find((topic)=>topic.id===id); if(!current) return; const ordered=state.topics.filter((topic)=>topic.subjectId===current.subjectId&&(topic.parentTopicId??null)===(current.parentTopicId??null)).sort((a,b)=>(a.order??0)-(b.order??0)); const index=ordered.findIndex((topic)=>topic.id===id); const target=index+(direction==="up"?-1:1); if(index<0||target<0||target>=ordered.length)return; [ordered[index],ordered[target]]=[ordered[target],ordered[index]]; const ranks=new Map(ordered.map((topic,i)=>[topic.id,i])); const at=nowIso(); commit({ ...state,topics:state.topics.map((topic)=>ranks.has(topic.id)?{...topic,order:ranks.get(topic.id),updatedAt:at}:topic) },"topic-reordered");
        },
        previewPlanContentImport(source, options) { return buildPlanContentImportPreview(state,source,{...options,now:nowIso}); },
        applyPlanContentImportPreview(preview) {
            if(!preview||typeof preview!=="object"||!Array.isArray(preview.operations))throw new TypeError("Plan Content import preview is required"); if(!preview.ready)throw new TypeError("Plan Content import preview contains errors and cannot be applied"); if(preview.baseRevision!==state.revision)throw new TypeError("Plan Content import preview is stale; preview again before applying");
            const at=nowIso(); let plans=state.plans.map((item)=>({...item}));let subjects=state.subjects.map((item)=>({...item}));let topics=state.topics.map((item)=>({...item}));let planId=preview.targetPlanId;const subjectRefs=new Map();const topicRefs=new Map();let activePlanId=state.activePlanId;let studyCycle=state.studyCycle;const studyCycles={...(state.studyCycles??{})};
            if(preview.target==="new-plan"){if(state.focus?.status!=="idle")throw new TypeError("Finish or cancel the current focus session before importing and activating a new plan");const plan=makePlan({name:preview.targetPlanName,mode:preview.targetPlanMode,status:"active",createdAt:at,updatedAt:at});plans.push(plan);planId=plan.id;activePlanId=plan.id;studyCycles[plan.id]=createStudyCycleState();studyCycle=studyCycles[plan.id];}
            if(!planId)throw new TypeError("Plan Content import target is missing");
            for(const op of preview.operations){
                if(op.action==="add-subject"){const siblings=subjects.filter((item)=>item.planId===planId);const item=createSubject({name:op.subjectName,planId,order:siblings.length,...op.changes,createdAt:at,updatedAt:at});subjects.push(item);subjectRefs.set(op.subjectRef,item.id);continue;}
                const subjectId=subjectRefs.get(op.subjectRef)??op.subjectRef;const subject=subjects.find((item)=>item.id===subjectId&&item.planId===planId);if(!subject)throw new TypeError(`Import operation references missing subject: ${op.subjectName}`);subjectRefs.set(op.subjectRef,subject.id);
                if(op.action==="update-subject"||op.action==="archive-subject"){const updated=createSubject({...subject,...op.changes,id:subject.id,planId,createdAt:subject.createdAt,updatedAt:at});subjects=subjects.map((item)=>item.id===subject.id?updated:item);continue;}
                const parentTopicId=op.parentRef?(topicRefs.get(op.parentRef)??op.parentRef):null;
                if(op.action==="add-topic"){const siblings=topics.filter((item)=>item.subjectId===subject.id&&(item.parentTopicId??null)===parentTopicId);const name=op.topicPath?.[op.topicPath.length-1]??"Topic";const item=createTopic({name,planId,subjectId:subject.id,parentTopicId,order:siblings.length,...op.changes,createdAt:at,updatedAt:at});topics.push(item);if(op.topicRef)topicRefs.set(op.topicRef,item.id);continue;}
                const topicId=op.topicRef?(topicRefs.get(op.topicRef)??op.topicRef):null;const topic=topicId?topics.find((item)=>item.id===topicId&&item.planId===planId):null;if(!topic)throw new TypeError(`Import operation references missing topic: ${(op.topicPath??[]).join(" > ")}`);if(op.topicRef)topicRefs.set(op.topicRef,topic.id);
                if(op.action==="update-topic"||op.action==="archive-topic"){const updated=createTopic({...topic,...op.changes,id:topic.id,planId,subjectId:subject.id,parentTopicId:topic.parentTopicId??null,createdAt:topic.createdAt,updatedAt:at});topics=topics.map((item)=>item.id===topic.id?updated:item);}
            }
            const activeSubjects=subjects.filter((item)=>item.planId===planId&&!item.archived).sort((a,b)=>(a.order??0)-(b.order??0));const prior=studyCycles[planId]??createStudyCycleState();const allowed=new Set(activeSubjects.map((item)=>item.id));let ids=prior.subjectIds.filter((id)=>allowed.has(id));if(!ids.length)ids=activeSubjects.map((item)=>item.id);studyCycles[planId]=createStudyCycleState({subjectIds:ids,cursor:prior.cursor});if(activePlanId===planId)studyCycle=studyCycles[planId];
            return commit({...state,plans,subjects,topics,activePlanId,studyCycle,studyCycles},"plan-content-import-applied");
        },
        setScheduleConfiguration(input) {
            const planId=activePlanId();const current=state.plans.find((plan)=>plan.id===planId);if(!current)throw new TypeError("No active plan");const at=nowIso();const schedule=normalizeScheduleConfiguration({...current.configuration.schedule,...input});const updated=makePlan({...current,configuration:{...current.configuration,schedule},createdAt:current.createdAt,updatedAt:at});commit({...state,plans:state.plans.map((plan)=>plan.id===planId?updated:plan)},"schedule-configuration-updated");return updated;
        },
        setActivityLocked(activityId,locked=true) {
            const current=state.activities.find((activity)=>activity.id===activityId&&activity.planId===activePlanId());if(!current)throw new TypeError("Activity not found in the active plan");if(current.status!=="planned")throw new TypeError("Only planned activities can be locked");const at=nowIso();const updated=createActivity({...current,locked:Boolean(locked),scheduleProvenance:{source:"manual",reason:Boolean(locked)?"User locked this schedule placement":"User unlocked this schedule placement",previewGeneratedAt:null,baseRevision:state.revision},createdAt:current.createdAt,updatedAt:at});commit({...state,activities:state.activities.map((activity)=>activity.id===activityId?updated:activity)},"activity-schedule-lock-updated");return updated;
        },
        previewSchedule(options = {}) { return buildAdaptiveSchedulePreview(state, { ...options, at: options.at ?? nowIso() }); },
        applySchedulePreview(preview) {
            if (!preview || typeof preview !== "object" || !Array.isArray(preview.operations)) throw new TypeError("Schedule preview is required");
            if (preview.baseRevision !== state.revision) throw new TypeError("Schedule preview is stale; preview again before applying");
            const at = nowIso(); let activities = state.activities.map((activity) => ({ ...activity }));
            for (const operation of preview.operations) {
                if (operation.action === "move") {
                    const index = activities.findIndex((activity) => activity.id === operation.activityId && activity.status === "planned");
                    if (index < 0) throw new TypeError(`Schedule move references missing planned activity: ${operation.activityId}`);
                    if(activities[index].locked) throw new TypeError(`Schedule preview cannot move locked activity: ${operation.activityId}`);
                    const scheduledFor = operation.to; const today = at.slice(0,10);
                    activities[index] = createActivity({ ...activities[index], scheduledFor, scheduledStartMinute:operation.toStartMinute??null, queue: scheduledFor === today ? "today" : "future", scheduleProvenance:{source:"adaptive",reason:operation.reason??null,previewGeneratedAt:preview.generatedAt,baseRevision:preview.baseRevision}, createdAt:activities[index].createdAt, updatedAt: at });
                } else if (operation.action === "add") {
                    const activity = createActivity({ ...operation.activity, planId: activePlanId(), scheduledFor: operation.scheduledFor, scheduledStartMinute:operation.scheduledStartMinute??null, origin: operation.activity.origin ?? "schedule", scheduleProvenance:{source:"adaptive",reason:operation.reason??null,previewGeneratedAt:preview.generatedAt,baseRevision:preview.baseRevision}, createdAt: at, updatedAt: at });
                    activities.push(activity);
                } else throw new TypeError(`Unsupported schedule operation: ${operation.action}`);
            }
            commit({ ...state, activities }, "schedule-preview-applied");
            return preview;
        },
        addMaterial(input) {
            const at=nowIso(); const material=createMaterial({ ...input, planId:input.planId??activePlanId(), createdAt:input.createdAt??at, updatedAt:input.updatedAt??at });
            commit({ ...state, materials:[...state.materials,material] }, "material-added"); return material;
        },
        deleteMaterial(materialId) {
            const current=state.materials.find((material)=>material.id===materialId&&material.planId===activePlanId());
            if(!current) throw new TypeError("Material not found in the active plan");
            commit({ ...state, materials:state.materials.filter((material)=>material.id!==materialId) }, "material-deleted"); return current;
        },
        addGoal(input) {
            const at=nowIso(); const goal=createGoal({ ...input, planId:input.planId??activePlanId(), createdAt:at, updatedAt:at });
            commit({ ...state, goals:[...state.goals,goal] }, "goal-added"); return goal;
        },
        toggleGoal(goalId) {
            const current=state.goals.find((goal)=>goal.id===goalId&&goal.planId===activePlanId()); if(!current) throw new TypeError("Goal not found in the active plan");
            const at=nowIso(); const updated=createGoal({ ...current, active:!current.active, createdAt:current.createdAt, updatedAt:at });
            commit({ ...state, goals:state.goals.map((goal)=>goal.id===goalId?updated:goal) }, "goal-toggled"); return updated;
        },
        deleteGoal(goalId) {
            const current=state.goals.find((goal)=>goal.id===goalId&&goal.planId===activePlanId()); if(!current) throw new TypeError("Goal not found in the active plan");
            commit({ ...state, goals:state.goals.filter((goal)=>goal.id!==goalId) }, "goal-deleted");
        },
        applyPortableImportPreview(preview) {
            if(!preview||typeof preview!=="object"||!preview.nextState) throw new TypeError("Portable import preview is required");
            if(preview.baseRevision!==state.revision) throw new TypeError("Portable import preview is stale; preview the package again before applying");
            const candidate=migrateStudyState(cloneState(preview.nextState),{now:nowIso()}); const errors=validateStudyState(candidate);
            if(errors.length) throw new TypeError(`Portable import state is invalid: ${errors[0]}`);
            return commit({ ...candidate, revision:state.revision }, "portable-import-applied");
        },
        previewQuestionImport(source,format) {return buildQuestionImportPreview(state,source,format);},
        applyQuestionImportPreview(preview) {if(!preview||preview.baseRevision!==state.revision)throw new TypeError("Question import preview is stale; preview again before applying");if(!preview.ready)throw new TypeError("Question import preview is not ready");const at=nowIso();const planId=activePlanId();const added=[];for(const d of preview.accepted){let subjectId=null,topicId=null;if(d.subjectName){const subj=state.subjects.find(x=>x.planId===planId&&x.name.toLowerCase()===d.subjectName.toLowerCase());subjectId=subj?.id??null;if(d.topicPath.length&&subj){let parent=null;for(const name of d.topicPath){const topic=state.topics.find(x=>x.subjectId===subj.id&&(x.parentTopicId??null)===parent&&x.name.toLowerCase()===name.toLowerCase());if(!topic){parent=null;break;}parent=topic.id;}topicId=parent;}}const q=createQuestion({...d,planId,subjectId,topicId,createdAt:at,updatedAt:at});added.push(q);}commit({...state,questions:[...state.questions,...added]},"question-import-applied");return added;},
        reclassifyError(errorId,category) {const entry=state.errorEntries.find(x=>x.id===errorId&&x.planId===activePlanId());if(!entry)throw new TypeError("Error entry not found in active plan");const at=nowIso();const updated=createErrorEntry({...entry,category,createdAt:entry.createdAt,updatedAt:at});commit({...state,errorEntries:state.errorEntries.map(x=>x.id===errorId?updated:x)},"error-reclassified");return updated;},
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
                    const intervals = reviewIntervals();
                    review = createReview({ planId: activePlanId(), subjectId: question.subjectId, topicId: question.topicId, dueAt: new Date(Date.parse(at)+intervals[0]*86_400_000).toISOString(), status: "due", sourceType: "question", sourceId: question.id, intervalIndex: 0, intervalDays: intervals[0], repetitionCount: 0, createdAt: at, updatedAt: at });
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
            const grade = normalizeReviewGrade(rating); const intervals = reviewIntervals(review.planId); const currentIndex = Math.max(0, Math.min(intervals.length - 1, Number.isInteger(review.intervalIndex) ? review.intervalIndex : 0));
            const nextIndex = grade < 3 ? 0 : Math.min(currentIndex + 1, intervals.length - 1); const nextInterval = intervals[nextIndex];
            const completed = createReview({ ...review, status: "completed", completedAt: at, updatedAt: at });
            const duplicate = review.sourceId == null ? null : state.reviews.find((candidate) => candidate.id !== review.id && candidate.status === "due" && candidate.planId === review.planId && candidate.sourceType === review.sourceType && candidate.sourceId === review.sourceId) ?? null;
            const successor = duplicate ?? createReview({ planId: review.planId, subjectId: review.subjectId, topicId: review.topicId, dueAt: new Date(Date.parse(at)+nextInterval*86_400_000).toISOString(), status: "due", sourceType: review.sourceType, sourceId: review.sourceId, intervalIndex: nextIndex, intervalDays: nextInterval, repetitionCount: (review.repetitionCount ?? 0) + 1, previousReviewId: review.id, createdAt: at, updatedAt: at });
            let reviews = state.reviews.map((candidate) => candidate.id === review.id ? completed : candidate); if (!duplicate) reviews = [...reviews, successor];
            const errorEntries = state.errorEntries.map((entry) => entry.status === "open" && entry.reviewId === review.id ? createErrorEntry({ ...entry, reviewId: successor.id, updatedAt: at }) : entry);
            const evidence = createEvidence({ planId: activePlanId(), subjectId: review.subjectId, topicId: review.topicId, kind: "review-completed", observedAt: at, provenance: "user", value: { reviewId: review.id, rating, grade, intervalIndex: currentIndex, intervalDays: review.intervalDays, policyIntervalsDays: intervals, nextReviewId: successor.id, nextIntervalIndex: nextIndex, nextIntervalDays: nextInterval, nextDueAt: successor.dueAt }, createdAt: at, updatedAt: at });
            commit({ ...state, reviews, errorEntries, evidence: [...state.evidence, evidence] }, "review-completed");
            return completed;
        },
        resolveError(errorId, { note = null } = {}) {
            const at = nowIso(); const entry = state.errorEntries.find((candidate) => candidate.id === errorId && candidate.planId === activePlanId());
            if (!entry) throw new TypeError("Error entry not found in the active plan");
            const resolved = createErrorEntry({ ...entry, status: "resolved", resolvedAt: at, note: note ?? entry.note, updatedAt: at });
            commit({ ...state, errorEntries: state.errorEntries.map((candidate) => candidate.id === errorId ? resolved : candidate) }, "error-resolved");
            return resolved;
        },
        applyContentCleanupPreview(preview) {
            if(!preview||preview.baseRevision!==state.revision)throw new TypeError("Cleanup preview is stale; preview again before applying");const canonical=buildContentCleanupPreview(state);const allowed=new Map(canonical.issues.filter(x=>x.applicable).map(x=>[x.id,x]));let next=cloneState(state);const at=nowIso();for(const requested of preview.issues.filter(x=>x.applicable)){const issue=allowed.get(requested.id);if(!issue)continue;if(issue.action==="archive-topic")next.topics=next.topics.map(t=>t.id===issue.entityId?{...t,status:"archived",updatedAt:at}:t);else if(issue.action==="dismiss-review")next.reviews=next.reviews.map(r=>r.id===issue.entityId?{...r,status:"dismissed",updatedAt:at}:r);else if(issue.action==="cancel-activity")next.activities=next.activities.map(a=>a.id===issue.entityId?{...a,status:"cancelled",updatedAt:at}:a);}commit(next,"content-cleanup-applied");return state;
        },
        replaceState(nextState, reason="state-replaced") { return commit(nextState,reason); },
        resetLocalState() {
            const cleared=typeof repository.clear==="function"?repository.clear():{status:"cleared"}; state=initialStateFactory({now:nowIso()}); const saved=repository.save(state); const status=saved.status==="saved"?"cleared":saved.status; persistence={status,message:saved.status==="saved"?persistenceMessage("cleared"):persistenceMessage(saved.status,saved.reason??cleared.reason),durable:saved.status==="saved"}; notify("state-reset"); return state;
        },
    };
}
