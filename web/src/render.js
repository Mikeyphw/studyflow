"use strict";

import { derivePlanInventory, recommendNextActivity, selectActivitiesForQueue, selectActivePlan, selectAllSubjectsForPlan, selectAllTopicsForSubject, selectCurrentCycleSubject, selectOverdueReviews } from "./domain.js";
const byId=(id)=>document.getElementById(id);
const titleCase=(value)=>String(value||"").split("-").map((part)=>part.slice(0,1).toUpperCase()+part.slice(1)).join(" ");
function emptyListMessage(list,title,detail){const item=document.createElement("li");item.className="empty-state";const strong=document.createElement("strong");strong.textContent=title;const span=document.createElement("span");span.textContent=detail;item.append(strong,span);list.append(item);}
function button(label,action,id,direction=null){const b=document.createElement("button");b.type="button";b.className="button button-quiet button-small";b.textContent=label;b.dataset[action]=id;if(direction)b.dataset.direction=direction;return b;}

export function renderRecommendation(state){
    const recommendation=recommendNextActivity(state); const title=byId("recommendation-title"); const reason=byId("recommendation-reason"); const signals=byId("recommendation-signals"); const duration=byId("recommendation-duration");
    if(!title||!reason||!signals||!duration)return; signals.replaceChildren();
    if(!recommendation){title.textContent="No queued action";reason.textContent="There is no planned activity in today's local queue.";duration.textContent="No data";return;}
    title.textContent=recommendation.activity.title; reason.textContent=recommendation.reason; duration.textContent=`${recommendation.activity.durationMinutes} min`;
    for(const signal of recommendation.signals){const li=document.createElement("li");li.textContent=signal;signals.append(li);}
}

export function renderTodayActivities(state){
    const list=byId("today-activity-list");if(!list)return;list.replaceChildren();const activities=selectActivitiesForQueue(state,"today");
    if(!activities.length){emptyListMessage(list,"Nothing queued for today","Use Quick Add to create a local activity.");return;}
    for(const activity of activities){const item=document.createElement("li");item.dataset.kind=activity.kind;item.dataset.entityId=activity.id;const body=document.createElement("div");const strong=document.createElement("strong");strong.textContent=activity.title;const meta=document.createElement("span");meta.textContent=`${titleCase(activity.kind)} · ${activity.durationMinutes} min · ${titleCase(activity.origin)}`;body.append(strong,meta);const focus=button("Focus","focusActivity",activity.id);item.append(body,focus);list.append(item);}
}

export function renderFocusState(state, snapshot){
    const timer=byId("focus-timer"); const status=byId("focus-status"); const start=byId("focus-start"); const pause=byId("focus-pause"); const complete=byId("focus-complete"); const cancel=byId("focus-cancel"); const select=byId("focus-activity"); const note=byId("focus-note"); const duration=byId("focus-duration"); const history=byId("focus-history");
    const remaining=Math.max(0,snapshot.remainingMs);const seconds=Math.ceil(remaining/1000);if(timer)timer.textContent=`${String(Math.floor(seconds/60)).padStart(2,"0")}:${String(seconds%60).padStart(2,"0")}`;
    if(status)status.textContent=snapshot.status==="idle"?"Ready. Start a focus block when you choose.":snapshot.status==="running"?`Running until ${new Date(snapshot.endsAt).toLocaleTimeString([], {hour:"2-digit",minute:"2-digit"})}.`:"Paused. The remaining time is saved locally.";
    if(start){start.textContent=snapshot.status==="paused"?"Resume":"Start";start.disabled=snapshot.status==="running";} if(pause)pause.disabled=snapshot.status!=="running"; if(complete)complete.disabled=snapshot.status==="idle"; if(cancel)cancel.disabled=snapshot.status==="idle";
    if(select){const previous=select.value||snapshot.activityId||"";select.replaceChildren();const none=document.createElement("option");none.value="";none.textContent="Unlinked focus block";select.append(none);for(const activity of selectActivitiesForQueue(state,"today")){const option=document.createElement("option");option.value=activity.id;option.textContent=`${activity.title} · ${activity.durationMinutes} min`;select.append(option);}select.value=snapshot.activityId??previous;if(snapshot.status!=="idle")select.disabled=true;else select.disabled=false;}
    if(note&&document.activeElement!==note)note.value=snapshot.note??"";if(duration&&document.activeElement!==duration){duration.value=String(snapshot.durationMinutes);duration.disabled=snapshot.status!=="idle";}
    if(history){history.replaceChildren();const sessions=state.sessions.slice().sort((a,b)=>Date.parse(b.endedAt??b.startedAt)-Date.parse(a.endedAt??a.startedAt)).slice(0,5);if(!sessions.length){emptyListMessage(history,"No focus history yet","Completed and cancelled focus sessions will appear here.");}else for(const session of sessions){const item=document.createElement("li");const activity=state.activities.find((candidate)=>candidate.id===session.activityId);const strong=document.createElement("strong");strong.textContent=activity?.title??"Unlinked focus block";const meta=document.createElement("span");meta.textContent=`${titleCase(session.status)} · ${session.durationMinutes??0} min · ${new Date(session.startedAt).toLocaleString()}`;item.append(strong,meta);history.append(item);}}
    const overdue=byId("today-overdue-review-count");if(overdue)overdue.textContent=String(selectOverdueReviews(state).length);
    const cycle=byId("study-cycle-next");const currentCycle=selectCurrentCycleSubject(state);if(cycle)cycle.textContent=currentCycle?.name??"No active cycle";
}

function topicMatches(topic,query,filter){if(query&&!`${topic.name} ${topic.notes??""}`.toLowerCase().includes(query))return false;if(filter==="archived")return topic.status==="archived";if(filter==="active")return topic.status!=="archived";if(filter==="high")return topic.status!=="archived"&&topic.priority==="high";return true;}
export function renderPlanContent(state,{query="",filter="active"}={}){
    const container=byId("plan-subject-list");if(!container)return;container.replaceChildren();const normalizedQuery=query.trim().toLowerCase();let rendered=0;
    for(const subject of selectAllSubjectsForPlan(state)){
        const allTopics=selectAllTopicsForSubject(state,subject.id);
        if(filter==="active"&&subject.archived)continue;
        if(filter==="high"&&subject.archived)continue;
        let topics=allTopics.filter((topic)=>topicMatches(topic,normalizedQuery,filter));
        if(filter==="archived"&&subject.archived)topics=allTopics.filter((topic)=>!normalizedQuery||`${topic.name} ${topic.notes??""}`.toLowerCase().includes(normalizedQuery));
        const subjectMatches=!normalizedQuery||`${subject.name} ${subject.notes??""}`.toLowerCase().includes(normalizedQuery);
        if(filter==="archived"&&!subject.archived&&!topics.length)continue;
        if(filter==="high"&&!topics.length)continue;
        if(!subjectMatches&&!topics.length)continue;
        rendered+=1;const details=document.createElement("details");details.open=rendered===1;details.dataset.entityId=subject.id;details.dataset.archived=String(subject.archived);const summary=document.createElement("summary");const heading=document.createElement("span");heading.textContent=subject.name;const count=document.createElement("span");count.className="muted";count.textContent=`${topics.length} ${topics.length===1?"topic":"topics"}${subject.weight!=null?` · weight ${subject.weight}`:""}${subject.archived?" · archived":""}`;summary.append(heading,count);details.append(summary);
        const controls=document.createElement("div");controls.className="entity-actions";controls.append(button("Edit","editSubject",subject.id),button("Add topic","addTopic",subject.id),button("↑","moveSubject",subject.id,"up"),button("↓","moveSubject",subject.id,"down"));details.append(controls);if(subject.notes){const notes=document.createElement("p");notes.className="entity-notes muted";notes.textContent=subject.notes;details.append(notes);}
        const list=document.createElement("ul");list.className="topic-list";if(!topics.length)emptyListMessage(list,"No matching topics","Change the filter or add a topic.");else for(const topic of topics){const item=document.createElement("li");item.dataset.entityId=topic.id;item.dataset.status=topic.status;const body=document.createElement("div");const label=document.createElement("strong");label.textContent=topic.name;const evidenceCount=state.evidence.filter((e)=>e.topicId===topic.id).length;const meta=document.createElement("span");meta.className="muted";meta.textContent=`${titleCase(topic.priority)} · ${titleCase(topic.status)} · ${evidenceCount?`${evidenceCount} evidence record${evidenceCount===1?"":"s"}`:"No evidence"}`;body.append(label,meta);if(topic.notes){const note=document.createElement("small");note.textContent=topic.notes;body.append(note);}const actions=document.createElement("div");actions.className="entity-actions";actions.append(button("Edit","editTopic",topic.id),button("↑","moveTopic",topic.id,"up"),button("↓","moveTopic",topic.id,"down"));item.append(body,actions);list.append(item);}details.append(list);container.append(details);
    }
    if(!rendered){const empty=document.createElement("div");empty.className="empty-state";empty.innerHTML="<strong>No matching content</strong><span>Try another search or filter.</span>";container.append(empty);}
}

export function renderPlanInventory(state){const plan=selectActivePlan(state);const inventory=derivePlanInventory(state);const planName=byId("active-plan-name"),subjectCount=byId("plan-subject-count"),topicCount=byId("plan-topic-count");if(planName)planName.textContent=plan?.name??"No active plan";if(subjectCount)subjectCount.textContent=String(inventory.subjects);if(topicCount)topicCount.textContent=String(inventory.topics);}
export function renderPersistenceStatus(persistence){const output=byId("persistence-status");if(!output)return;output.textContent=persistence.message;output.dataset.persistence=persistence.durable?"durable":"memory";}
export function renderStudyState(state,persistence,ui={}){renderRecommendation(state);renderTodayActivities(state);renderFocusState(state,ui.focusSnapshot??state.focus);renderPlanContent(state,ui.content??{});renderPlanInventory(state);renderPersistenceStatus(persistence);}
