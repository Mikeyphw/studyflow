"use strict";
import type {
  Activity, EvidenceTimelineEvent, QuestionQuotaPlan, QuestionQuotaSubject, ReadinessExplanation,
  ReadinessSignal, ScheduleCalendarSnapshot, ScheduleCalendarView, StudyState,
} from "./types";
import { deriveActiveStudyPhase } from "./exam_foundation";
import { daysUntilOfficialExam } from "./promise_cleanup";

const DAY_MS=86_400_000;
const clamp=(n:number,min=0,max=100)=>Math.max(min,Math.min(max,n));
const pct=(n:number,d:number)=>d<=0?0:clamp(Math.round(n*100/d));
const cleanDate=(value:string)=>/^\d{4}-\d{2}-\d{2}$/.test(value)?value:null;

export function localDateKey(value:string|number|Date=new Date()):string{
  const d=value instanceof Date?new Date(value.getTime()):new Date(value);
  if(Number.isNaN(d.getTime()))throw new TypeError("Invalid local date");
  return `${String(d.getFullYear()).padStart(4,"0")}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
}
function localDate(value:string):Date{const m=/^(\d{4})-(\d{2})-(\d{2})$/.exec(value);if(!m)throw new TypeError(`Invalid date-only value: ${value}`);const d=new Date(Number(m[1]),Number(m[2])-1,Number(m[3]),12,0,0,0);if(localDateKey(d)!==value)throw new TypeError(`Invalid date-only value: ${value}`);return d;}
function addLocalDays(value:string,days:number):string{const d=localDate(value);d.setDate(d.getDate()+days);return localDateKey(d);}
function localDayNumber(value:string):number{const [y,m,d]=value.split("-").map(Number);return Math.floor(Date.UTC(y,m-1,d)/DAY_MS);}
function daysBetween(start:string,end:string):number{return localDayNumber(end)-localDayNumber(start);}
function mondayFor(value:string):string{const d=localDate(value),offset=(d.getDay()+6)%7;d.setDate(d.getDate()-offset);return localDateKey(d);}
function activePlan(state:StudyState){return state.plans.find(p=>p.id===state.activePlanId)??null;}
function valueObject(e:any):Record<string,any>{return e&&typeof e.value==="object"&&e.value&&!Array.isArray(e.value)?e.value as Record<string,any>:{};}
export function readinessBandForScore(score:number):ReadinessExplanation["band"]{return score>=80?"on-track":score>=65?"watch":score>=45?"at-risk":"critical";}
const bandFor=readinessBandForScore;
function signal(id:string,label:string,value:number|null,reason:string,weight:number,evidenceCount:number):ReadinessSignal{return{id,label,value,status:value==null?"unknown":value>=80?"good":"risk",reason,weight,severity:value==null?null:bandFor(value),evidenceCount};}
function weightedPresent(items:Array<[number|null,number]>):number|null{const present=items.filter(([v,w])=>v!=null&&w>0) as Array<[number,number]>;const total=present.reduce((n,[,w])=>n+w,0);return total?clamp(Math.round(present.reduce((n,[v,w])=>n+clamp(v)*w,0)/total)):null;}

export function deriveExamReadinessParity(state:StudyState,at:Date=new Date()):ReadinessExplanation{
  const plan=activePlan(state);if(!plan)return{value:null,band:null,confidence:"none",reason:"No active plan",daysUntilExam:null,evidenceDimensionCount:0,evidenceSampleCount:0,signals:[]};
  const planId=plan.id,today=localDateKey(at),weekAgo=at.getTime()-7*DAY_MS;
  const topics=state.topics.filter(t=>t.planId===planId&&t.status!=="archived");
  const observedTopicIds=new Set(state.evidence.filter(e=>e.planId===planId&&e.topicId).map(e=>e.topicId as string));
  const covered=topics.filter(t=>observedTopicIds.has(t.id)).length;
  const coverage=topics.length?pct(covered,topics.length):null;

  const graded=state.evidence.filter(e=>e.planId===planId&&e.kind==="question-attempt"&&valueObject(e).gradable===true);
  const correct=graded.filter(e=>valueObject(e).correct===true).length;
  const rawAccuracy=graded.length?pct(correct,graded.length):null;
  const practiceHealth=rawAccuracy==null?null:graded.length<20?Math.floor((rawAccuracy+55)/2):rawAccuracy;

  const due=state.reviews.filter(r=>r.planId===planId&&r.status==="due");
  const overdue=due.filter(r=>Date.parse(r.dueAt)<at.getTime());
  const completedReviews=state.reviews.filter(r=>r.planId===planId&&r.status==="completed");
  const reviewEvidenceCount=due.length+completedReviews.length;
  const reviewHealth=reviewEvidenceCount?clamp((100-(overdue.length*9+due.length*3))|0):null;

  const mocks=state.evidence.filter(e=>e.planId===planId&&e.kind==="mock-exam-result").filter(e=>Number.isFinite(Number(valueObject(e).percent))).sort((a,b)=>Date.parse(b.observedAt)-Date.parse(a.observedAt)).slice(0,3);
  const mockHealth=mocks.length?clamp(Math.round(mocks.reduce((n,e)=>n+Number(valueObject(e).percent),0)/mocks.length)):null;

  const recentSessions=state.sessions.filter(s=>s.planId===planId&&s.status==="completed"&&Date.parse(s.endedAt??s.updatedAt)>=weekAgo&&Date.parse(s.endedAt??s.updatedAt)<=at.getTime());
  const activeDays=new Set(recentSessions.map(s=>localDateKey(s.endedAt??s.updatedAt))).size;
  const consistency=recentSessions.length?pct(activeDays,5):null;

  const observedSchedule=state.activities.filter(a=>a.planId===planId&&a.scheduledFor&&((a.status!=="planned")||a.scheduledFor<=today));
  const scheduleHealth=observedSchedule.length?pct(observedSchedule.filter(a=>a.status==="completed").length,observedSchedule.length):null;

  const daysUntilExam=plan.mode==="EXAM_PLAN"?daysUntilOfficialExam(plan,at):null;
  const studyHoursGoal=state.goals.find(g=>g.planId===planId&&g.active&&g.period==="weekly"&&g.metric==="study-hours");
  const studyMinutesGoal=state.goals.find(g=>g.planId===planId&&g.active&&g.period==="weekly"&&g.metric==="study-minutes");
  const weeklyGoalHours=studyHoursGoal?.target??(studyMinutesGoal?studyMinutesGoal.target/60:null);
  const pendingMinutes=state.activities.filter(a=>a.planId===planId&&a.status==="planned").reduce((n,a)=>n+a.durationMinutes,0);
  let timeFeasibility:number|null=null;
  if(daysUntilExam!=null&&weeklyGoalHours!=null&&weeklyGoalHours>0){if(daysUntilExam===0)timeFeasibility=coverage;else if(pendingMinutes<=0){const horizon=daysUntilExam>=30?100:daysUntilExam>=14?95:daysUntilExam>=7?90:85;timeFeasibility=coverage==null?horizon:Math.floor((coverage+horizon)/2);}else{const available=weeklyGoalHours*60*(daysUntilExam/7);timeFeasibility=clamp(Math.round(available/pendingMinutes*100));}}

  const availableDimensions=[coverage,reviewHealth,practiceHealth,mockHealth,consistency,scheduleHealth,timeFeasibility].filter(v=>v!=null).length;
  const evidenceSampleCount=recentSessions.length+graded.length+mocks.length+reviewEvidenceCount+observedSchedule.length;
  const behavioralDimensions=[reviewHealth,practiceHealth,mockHealth,consistency,scheduleHealth].filter(v=>v!=null).length;
  const minimum=mocks.length>=1||graded.length>=30||recentSessions.length>=3||(behavioralDimensions>=2&&evidenceSampleCount>=10);
  const value=minimum?weightedPresent([[coverage,24],[reviewHealth,18],[practiceHealth,14],[mockHealth,14],[consistency,10],[scheduleHealth,10],[timeFeasibility,10]]):null;
  const confidence=value==null?"none":availableDimensions>=5&&graded.length>=200&&recentSessions.length>=5&&mocks.length>=2?"high":availableDimensions>=4&&(graded.length>=50||recentSessions.length>=5||mocks.length>=2)?"medium":"low";
  const weakAreaCount=new Set(state.errorEntries.filter(e=>e.planId===planId&&e.status==="open"&&e.recurrenceCount>1).map(e=>e.subjectId??e.topicId??e.id)).size;
  const signals:ReadinessSignal[]=[
    signal("coverage","Coverage",coverage,coverage==null?"No active syllabus coverage can be derived.":`${covered}/${topics.length} active topics have evidence.`,24,covered),
    signal("reviews","Reviews",reviewHealth,reviewHealth==null?"No review evidence yet.":`${due.length} due · ${overdue.length} overdue reviews.`,18,reviewEvidenceCount),
    signal("practice","Practice",practiceHealth,practiceHealth==null?"No graded question evidence yet.":`${rawAccuracy}% raw accuracy over ${graded.length} graded attempts${graded.length<20?"; small-sample adjustment applied":""}.`,14,graded.length),
    signal("mocks","Mock exams",mockHealth,mockHealth==null?"No submitted mock score evidence yet.":`Recent ${mocks.length} submitted mock score${mocks.length===1?"":"s"} average ${mockHealth}%.`,14,mocks.length),
    signal("consistency","Consistency",consistency,consistency==null?"No completed study sessions in the last seven days.":`${activeDays}/5 target study days in the last seven days.`,10,recentSessions.length),
    signal("schedule","Schedule",scheduleHealth,scheduleHealth==null?"No scheduled activity has become observable yet.":`${observedSchedule.filter(a=>a.status==="completed").length}/${observedSchedule.length} observable scheduled activities completed.`,10,observedSchedule.length),
    signal("time-left","Time left",timeFeasibility,daysUntilExam==null?"No confirmed exam date is set.":weeklyGoalHours==null?`Exam is in ${daysUntilExam} days; no weekly study-time goal is configured.`:`Exam is in ${daysUntilExam} days with ${weeklyGoalHours} configured study hours/week.`,10,daysUntilExam==null?0:1),
  ];
  if(weakAreaCount>0)signals.push(signal("weak-areas","Weak areas",clamp(100-weakAreaCount*8),`${weakAreaCount} recurring weak area${weakAreaCount===1?"":"s"} need targeted remediation.`,0,weakAreaCount));
  const reason=value==null?`Insufficient behavioral evidence · ${availableDimensions}/7 dimensions · ${evidenceSampleCount} samples`:`${availableDimensions}/7 dimensions · ${evidenceSampleCount} samples · ${confidence} confidence`;
  return{value,band:value==null?null:bandFor(value),confidence,reason,daysUntilExam,evidenceDimensionCount:availableDimensions,evidenceSampleCount,signals};
}

function normalized(values:number[],equalFallback:boolean):number[]{const positive=values.map(v=>Math.max(0,v)),sum=positive.reduce((a,b)=>a+b,0);if(sum>0)return positive.map(v=>v/sum);return equalFallback&&values.length?values.map(()=>1/values.length):values.map(()=>0);}
function allocateExactly(total:number,shares:number[]):number[]{if(!shares.length)return[];if(total<=0)return shares.map(()=>0);const exact=shares.map(s=>s*total),base=exact.map(Math.floor);let left=total-base.reduce((a,b)=>a+b,0);const order=exact.map((_,i)=>i).sort((a,b)=>(exact[b]-base[b])-(exact[a]-base[a])||a-b);for(const i of order){if(left<=0)break;base[i]++;left--;}return base;}
function evidenceInDateRange(e:{observedAt:string},start:string,end:string):boolean{const key=localDateKey(e.observedAt);return key>=start&&key<=end;}

export function derivePublicExamQuestionQuotaPlan(state:StudyState,at:Date=new Date()):QuestionQuotaPlan|null{
  const plan=activePlan(state);if(!plan||plan.mode!=="EXAM_PLAN")return null;const today=localDateKey(at),phase=deriveActiveStudyPhase(plan,today);if(!phase||phase.questionGoal==null)return null;
  const subjects=state.subjects.filter(s=>s.planId===plan.id&&!s.archived);if(!subjects.length)return null;
  const attempts=state.evidence.filter(e=>e.planId===plan.id&&e.kind==="question-attempt"&&evidenceInDateRange(e,phase.startsOn,phase.endsOn));
  const mockEvidence=state.evidence.filter(e=>e.planId===plan.id&&e.kind==="mock-exam-result"&&evidenceInDateRange(e,phase.startsOn,phase.endsOn));
  const mockAnswered=mockEvidence.reduce((n,e)=>n+(Array.isArray(valueObject(e).questionIds)?valueObject(e).questionIds.length:Number(valueObject(e).questionsAnswered)||0),0);
  const completed=attempts.length+mockAnswered,remaining=Math.max(0,phase.questionGoal-completed),remainingDays=Math.max(1,daysBetween(today,phase.endsOn)+1),dailyTarget=remaining?Math.ceil(remaining/remainingDays):0,horizon=Math.min(7,remainingDays),nextSeven=remaining?Math.min(remaining,Math.ceil(remaining*horizon/remainingDays)):0;
  const perAnswered=subjects.map(s=>attempts.filter(e=>e.subjectId===s.id).length);const totalAnswered=perAnswered.reduce((a,b)=>a+b,0);
  const perAccuracy=subjects.map(s=>{const scoped=attempts.filter(e=>e.subjectId===s.id&&valueObject(e).gradable===true);return scoped.length?pct(scoped.filter(e=>valueObject(e).correct===true).length,scoped.length):null;});
  const recurring=subjects.map(s=>state.errorEntries.filter(e=>e.planId===plan.id&&e.subjectId===s.id&&e.recurrenceCount>1).length);
  const overdue=subjects.map(s=>state.reviews.filter(r=>r.planId===plan.id&&r.subjectId===s.id&&r.status==="due"&&Date.parse(r.dueAt)<=at.getTime()).length);
  const weightShares=normalized(subjects.map(s=>Number(s.weight)||0),true);const answeredShares=subjects.map((_,i)=>totalAnswered?perAnswered[i]/totalAnswered:0);
  const underShares=normalized(weightShares.map((w,i)=>Math.max(0,w-answeredShares[i])),false);const accuracyShares=normalized(perAccuracy.map(v=>v==null?0:100-v),false);const errorShares=normalized(recurring,false);const reviewShares=normalized(overdue,false);
  const priorityShares=normalized(subjects.map((_,i)=>.35*weightShares[i]+.20*underShares[i]+.20*accuracyShares[i]+.15*errorShares[i]+.10*reviewShares[i]),true);const allocations=allocateExactly(nextSeven,priorityShares);
  const rows:QuestionQuotaSubject[]=subjects.map((s,i)=>({subjectId:s.id,subjectName:s.name,weeklyQuestions:allocations[i],phaseQuestionsAnswered:perAnswered[i],recentAccuracyPercent:perAccuracy[i],recurringErrors:recurring[i],overdueReviews:overdue[i],priorityShare:priorityShares[i],examWeightShare:weightShares[i],underPracticeShare:underShares[i],accuracyDeficitShare:accuracyShares[i],recurringErrorShare:errorShares[i],overdueReviewShare:reviewShares[i]})).sort((a,b)=>b.weeklyQuestions-a.weeklyQuestions||a.subjectName.localeCompare(b.subjectName));
  return{source:"phase",configured:phase.questionGoal,completed,remaining,dailyTarget,reason:remaining?`${remaining} phase questions remain; ${nextSeven} are allocated across the next ${horizon} day${horizon===1?"":"s"}.`:"Active phase question target is complete.",phaseId:phase.id,phaseLabel:phase.label,remainingDays,nextSevenDaysQuestions:nextSeven,subjects:rows};
}

export function deriveGenericQuestionQuotaPlan(state:StudyState,at:Date=new Date()):QuestionQuotaPlan{
  const plan=activePlan(state),configured=plan?.configuration.practice.weeklyQuestionQuota??null,today=localDateKey(at),monday=mondayFor(today),completed=state.evidence.filter(e=>e.planId===plan?.id&&e.kind==="question-attempt"&&localDateKey(e.observedAt)>=monday&&localDateKey(e.observedAt)<=today).length;
  if(configured==null)return{source:"none",configured:null,completed,remaining:null,dailyTarget:null,reason:"No weekly question quota is configured.",phaseId:null,phaseLabel:null,remainingDays:null,nextSevenDaysQuestions:null,subjects:[]};const elapsed=(localDate(today).getDay()+6)%7,daysLeft=Math.max(1,7-elapsed),remaining=Math.max(0,configured-completed);return{source:"practice",configured,completed,remaining,dailyTarget:Math.ceil(remaining/daysLeft),reason:remaining?`${remaining} questions remain in the configured weekly quota.`:"Weekly question quota is complete.",phaseId:null,phaseLabel:null,remainingDays:daysLeft,nextSevenDaysQuestions:remaining,subjects:[]};
}

export function buildUnifiedEvidenceTimeline(state:StudyState,{planId=state.activePlanId,limit=50,at=new Date()}:{planId?:string|null;limit?:number;at?:Date}={}):EvidenceTimelineEvent[]{
  const events:EvidenceTimelineEvent[]=[];const within=(id:string)=>!planId||id===planId;const sessionActivityIds=new Set(state.sessions.filter(s=>within(s.planId)&&s.status==="completed"&&s.activityId).map(s=>s.activityId as string));
  for(const s of state.sessions.filter(s=>within(s.planId))){if(s.status!=="completed"&&s.status!=="cancelled")continue;const when=s.endedAt??s.startedAt;events.push({id:`session:${s.id}`,kind:s.status==="completed"?"session-completed":"session-cancelled",occurredAt:when,label:state.activities.find(a=>a.id===s.activityId)?.title??"Study session",minutes:s.durationMinutes,count:null,percent:null,source:"session"});}
  for(const a of state.activities.filter(a=>within(a.planId))){if(a.status!=="completed"&&a.status!=="cancelled")continue;if(a.status==="completed"&&sessionActivityIds.has(a.id))continue;events.push({id:`activity:${a.id}`,kind:a.status==="completed"?"planned-activity-completed":"planned-activity-cancelled",occurredAt:a.updatedAt,label:a.title,minutes:a.durationMinutes,count:null,percent:null,source:"activity"});}
  for(const r of state.reviews.filter(r=>within(r.planId))){if(r.status==="completed"&&r.completedAt)events.push({id:`review:${r.id}:completed`,kind:"review-completed",occurredAt:r.completedAt,label:"Review",minutes:null,count:r.repetitionCount,percent:null,source:"review"});else if(r.status==="due"&&Date.parse(r.dueAt)<=at.getTime())events.push({id:`review:${r.id}:due`,kind:"review-due",occurredAt:r.dueAt,label:"Review due",minutes:null,count:r.repetitionCount,percent:null,source:"review"});}
  for(const q of state.questions.filter(q=>within(q.planId)))events.push({id:`question:${q.id}`,kind:"question-added",occurredAt:q.createdAt,label:q.prompt.slice(0,80),minutes:null,count:q.choices.length||null,percent:null,source:"question"});
  for(const e of state.evidence.filter(e=>within(e.planId))){const v=valueObject(e);if(e.kind==="mock-exam-result")events.push({id:`mock:${e.id}`,kind:"mock-exam-submitted",occurredAt:e.observedAt,label:"Mock exam",minutes:null,count:Array.isArray(v.questionIds)?v.questionIds.length:null,percent:Number.isFinite(Number(v.percent))?Number(v.percent):null,source:"mock"});else if(!["focus-session-completed","writing-session-completed","review-completed","question-attempt"].includes(e.kind))events.push({id:`evidence:${e.id}`,kind:e.kind,occurredAt:e.observedAt,label:e.kind.replace(/[-_]/g," "),minutes:Number.isFinite(Number(v.durationMinutes))?Number(v.durationMinutes):null,count:null,percent:Number.isFinite(Number(v.percent))?Number(v.percent):null,source:"evidence"});}
  for(const g of state.goals.filter(g=>within(g.planId)&&g.completedAt))events.push({id:`goal:${g.id}`,kind:"goal-completed",occurredAt:g.completedAt as string,label:g.name,minutes:null,count:null,percent:100,source:"goal"});
  for(const m of state.materials.filter(m=>within(m.planId)&&m.kind==="note"&&!m.archived))events.push({id:`note:${m.id}`,kind:"note-updated",occurredAt:m.updatedAt,label:m.title,minutes:null,count:m.tags.length||null,percent:null,source:"note"});
  return events.filter(e=>Date.parse(e.occurredAt)<=at.getTime()).sort((a,b)=>Date.parse(b.occurredAt)-Date.parse(a.occurredAt)||a.id.localeCompare(b.id)).slice(0,Math.max(1,Math.min(50,Math.trunc(limit)||50)));
}

export function scheduleCalendarWindow(view:ScheduleCalendarView,at:Date=new Date()):{startsOn:string|null;endsOnInclusive:string|null;bounded:boolean}{const today=localDateKey(at);if(view==="today")return{startsOn:today,endsOnInclusive:today,bounded:true};if(view==="this-week"){const m=mondayFor(today);return{startsOn:m,endsOnInclusive:addLocalDays(m,6),bounded:true};}if(view==="next-week"){const m=addLocalDays(mondayFor(today),7);return{startsOn:m,endsOnInclusive:addLocalDays(m,6),bounded:true};}return{startsOn:null,endsOnInclusive:null,bounded:false};}
export function deriveScheduleCalendarSnapshot(state:StudyState,{view="this-week",at=new Date(),subjectId=null,activityType=null,status=null,provenance=null,query="",includePast=false}:{view?:ScheduleCalendarView;at?:Date;subjectId?:string|null;activityType?:Activity["activityType"]|null;status?:Activity["status"]|null;provenance?:"manual"|"adaptive"|null;query?:string;includePast?:boolean}={}):ScheduleCalendarSnapshot{
  const planId=state.activePlanId,window=scheduleCalendarWindow(view,at),today=localDateKey(at),normalizedQuery=String(query??"").trim().toLowerCase();let activities=state.activities.filter(a=>!planId||a.planId===planId);let hiddenPastCount=0;if(window.bounded)activities=activities.filter(a=>a.scheduledFor!=null&&a.scheduledFor>=window.startsOn!&&a.scheduledFor<=window.endsOnInclusive!);else if(!includePast){hiddenPastCount=activities.filter(a=>a.scheduledFor!=null&&a.scheduledFor<today).length;activities=activities.filter(a=>a.scheduledFor==null||a.scheduledFor>=today);}if(subjectId)activities=activities.filter(a=>a.subjectId===subjectId);if(activityType)activities=activities.filter(a=>a.activityType===activityType);if(status)activities=activities.filter(a=>a.status===status);if(provenance)activities=activities.filter(a=>(a.scheduleProvenance?.source??"manual")===provenance);if(normalizedQuery)activities=activities.filter(a=>{const subject=state.subjects.find(s=>s.id===a.subjectId)?.name??"",topic=state.topics.find(t=>t.id===a.topicId)?.name??"";return `${a.title} ${subject} ${topic}`.toLowerCase().includes(normalizedQuery)});activities=activities.slice().sort((a,b)=>String(a.scheduledFor??"9999").localeCompare(String(b.scheduledFor??"9999"))||(a.scheduledStartMinute??9999)-(b.scheduledStartMinute??9999)||a.title.localeCompare(b.title));return{view,window,activities,visibleCount:activities.length,plannedMinutes:activities.reduce((n,a)=>n+a.durationMinutes,0),hiddenPastCount,query:normalizedQuery,includesPast:view!=="calendar"||includePast};
}
