"use strict";

import type {
  Activity, BbAgenteComercialReferencePreset, BbReferenceRhythmBlock, ExamStage, Goal,
  PracticeRouteContext, StudyActivityType, StudyState, WritingChecklist, WritingRubric,
  WritingSessionRecord, WritingWeeklyProgress,
} from "./types";

export const BB_AGENTE_COMERCIAL_PRE_EDITAL = "BB_AGENTE_COMERCIAL_PRE_EDITAL" as const;
export const BB_REFERENCE_NOTICE = "Seleção Externa 2022/001";
export const BB_REFERENCE_NOTICE_URL = "https://www.bb.com.br/docs/portal/dipes/Edital-de-Abertura-de-Selecao-Externa-2022-01.pdf";
export const BB_OBJECTIVE_QUESTION_COUNT = 70;
export const BB_OBJECTIVE_MAXIMUM_POINTS = 100;
export const BB_ESSAY_MINIMUM_SCORE_PERCENT = 70;
export const BB_WEEKLY_GOAL_HOURS = 19;
export const BB_WEEKLY_WRITING_SESSION_GOAL = 1;
export const BB_WEEKLY_ACCURACY_GOAL_PERCENT = 80;
export const BB_REFERENCE_SCHEDULED_MINUTES = 1140;

export const BB_SUBJECT_WEIGHTS = Object.freeze([
  ["Português", 15],
  ["Inglês", 5],
  ["Matemática", 7.5],
  ["Atualidades do Mercado Financeiro", 5],
  ["Matemática Financeira", 7.5],
  ["Conhecimentos Bancários", 15],
  ["Tecnologia da Informação", 22.5],
  ["Vendas e Negociação", 22.5],
] as const);

export const BB_REFERENCE_RHYTHM:readonly BbReferenceRhythmBlock[] = Object.freeze([
  {weekday:1,startMinute:9*60,durationMinutes:90,subjectName:"Português"},
  {weekday:1,startMinute:10*60+40,durationMinutes:90,subjectName:"Tecnologia da Informação"},
  {weekday:2,startMinute:9*60,durationMinutes:90,subjectName:"Vendas e Negociação"},
  {weekday:2,startMinute:10*60+40,durationMinutes:90,subjectName:"Conhecimentos Bancários"},
  {weekday:3,startMinute:9*60,durationMinutes:90,subjectName:"Matemática"},
  {weekday:3,startMinute:10*60+40,durationMinutes:90,subjectName:"Tecnologia da Informação"},
  {weekday:4,startMinute:9*60,durationMinutes:90,subjectName:"Vendas e Negociação"},
  {weekday:4,startMinute:10*60+40,durationMinutes:90,subjectName:"Conhecimentos Bancários"},
  {weekday:5,startMinute:9*60,durationMinutes:60,subjectName:"Matemática Financeira"},
  {weekday:5,startMinute:10*60+10,durationMinutes:60,subjectName:"Atualidades do Mercado Financeiro"},
  {weekday:5,startMinute:11*60+20,durationMinutes:60,subjectName:"Inglês"},
  {weekday:6,startMinute:9*60,durationMinutes:120,subjectName:null},
  {weekday:6,startMinute:11*60+10,durationMinutes:60,subjectName:null},
  {weekday:6,startMinute:12*60+20,durationMinutes:40,subjectName:null},
  {weekday:6,startMinute:13*60+10,durationMinutes:20,subjectName:null},
]);

export function createBbAgenteComercialReferencePreset():BbAgenteComercialReferencePreset {
  const objectivePoints=BB_SUBJECT_WEIGHTS.reduce((sum,[,weight])=>sum+weight,0);
  if(objectivePoints!==BB_OBJECTIVE_MAXIMUM_POINTS)throw new TypeError("BB reference subject weights must total 100 points");
  const scheduledMinutes=BB_REFERENCE_RHYTHM.reduce((sum,block)=>sum+block.durationMinutes,0);
  if(scheduledMinutes!==BB_REFERENCE_SCHEDULED_MINUTES)throw new TypeError("BB reference rhythm must total 1140 scheduled minutes");
  return {
    id:BB_AGENTE_COMERCIAL_PRE_EDITAL,
    referenceNotice:BB_REFERENCE_NOTICE,
    referenceNoticeUrl:BB_REFERENCE_NOTICE_URL,
    objectiveQuestionCount:BB_OBJECTIVE_QUESTION_COUNT,
    objectiveMaximumPoints:BB_OBJECTIVE_MAXIMUM_POINTS,
    essayMinimumScorePercent:BB_ESSAY_MINIMUM_SCORE_PERCENT,
    weeklyGoalHours:BB_WEEKLY_GOAL_HOURS,
    weeklyScheduledMinutes:BB_REFERENCE_SCHEDULED_MINUTES,
    weeklyWritingSessionGoal:BB_WEEKLY_WRITING_SESSION_GOAL,
    weeklyAccuracyGoalPercent:BB_WEEKLY_ACCURACY_GOAL_PERCENT,
    reviewIntervalsDays:[1,7,30],
    scheduleRhythm:BB_REFERENCE_RHYTHM.map(block=>({...block})),
  };
}

export function bbReferenceExamStages(planId="bb"):ExamStage[] {
  return [
    {id:`${planId}-stage-objective`,name:"Prova objetiva",date:null,status:"planned",type:"objective",weight:100,minimumScorePercent:null,referenceQuestionCount:70},
    {id:`${planId}-stage-essay`,name:"Redação",date:null,status:"planned",type:"essay",weight:0,minimumScorePercent:70,referenceQuestionCount:null},
  ];
}

export function bbReferenceGoals(planId:string,at:string):Goal[] {
  const base={planId,createdAt:at,updatedAt:at,active:true} as const;
  return [
    {...base,id:`${planId}-goal-weekly-study`,name:"BB weekly study goal",metric:"study-hours",target:19,period:"weekly"},
    {...base,id:`${planId}-goal-weekly-accuracy`,name:"BB weekly accuracy goal",metric:"accuracy-percent",target:80,period:"weekly"},
    {...base,id:`${planId}-goal-weekly-writing`,name:"BB weekly writing goal",metric:"writing-sessions",target:1,period:"weekly"},
  ];
}

export function normalizeStudyActivityType(value:unknown,kind:"study"|"questions"|"review"="study"):StudyActivityType {
  const raw=String(value??"").trim().toUpperCase();
  const supported:readonly StudyActivityType[]=["THEORY","VIDEO","READING","CUSTOM","QUESTIONS","FLASHCARDS","REVIEW","MOCK_EXAM","WRITING"];
  if(supported.includes(raw as StudyActivityType))return raw as StudyActivityType;
  return kind==="questions"?"QUESTIONS":kind==="review"?"REVIEW":"THEORY";
}

export function activityKindForType(type:StudyActivityType):"study"|"questions"|"review" {
  if(type==="QUESTIONS"||type==="FLASHCARDS"||type==="MOCK_EXAM")return "questions";
  if(type==="REVIEW")return "review";
  return "study";
}

export function routeActivityDeepLink(state:StudyState,activityId:string):PracticeRouteContext {
  const activity=state.activities.find(item=>item.id===activityId&&item.planId===state.activePlanId);
  if(!activity)throw new TypeError("Activity not found in the active plan");
  const activityType=normalizeStudyActivityType(activity.activityType,activity.kind);
  const base={activityId:activity.id,activityType,subjectId:activity.subjectId,topicId:activity.topicId};
  if(activityType==="MOCK_EXAM")return {...base,screen:"practice",practiceTab:"tests",focusMode:null,reviewId:null};
  if(activityType==="QUESTIONS"||activityType==="FLASHCARDS")return {...base,screen:"practice",practiceTab:"questions",focusMode:null,reviewId:null};
  if(activityType==="REVIEW"){
    const due=state.reviews.filter(review=>review.planId===activity.planId&&review.status==="due")
      .filter(review=>activity.topicId?review.topicId===activity.topicId:activity.subjectId?review.subjectId===activity.subjectId:true)
      .sort((a,b)=>a.dueAt.localeCompare(b.dueAt))[0]??null;
    return {...base,screen:"practice",practiceTab:"reviews",focusMode:null,reviewId:due?.id??null};
  }
  if(activityType==="WRITING")return {...base,screen:"today",practiceTab:null,focusMode:"writing",reviewId:null};
  return {...base,screen:"today",practiceTab:null,focusMode:"focus",reviewId:null};
}

const WRITING_PREFIX="studyflowWritingV1=";
const defaultChecklist=():WritingChecklist=>({taskReviewed:false,structureReviewed:false,cohesionReviewed:false,languageReviewed:false});
export function normalizeWritingChecklist(input:Partial<WritingChecklist>|null|undefined):WritingChecklist{return{taskReviewed:Boolean(input?.taskReviewed),structureReviewed:Boolean(input?.structureReviewed),cohesionReviewed:Boolean(input?.cohesionReviewed),languageReviewed:Boolean(input?.languageReviewed)}};
export function writingChecklistComplete(checklist:WritingChecklist):boolean{return checklist.taskReviewed&&checklist.structureReviewed&&checklist.cohesionReviewed&&checklist.languageReviewed;}
export function normalizeWritingRubric(input:WritingRubric):WritingRubric {
  const out={themeAndTask:Number(input.themeAndTask),textType:Number(input.textType),cohesion:Number(input.cohesion),argumentation:Number(input.argumentation),standardLanguage:Number(input.standardLanguage)};
  for(const value of Object.values(out))if(!Number.isInteger(value)||value<0||value>20)throw new TypeError("Writing rubric dimensions must be integers from 0 to 20");
  return out;
}
export function writingRubricTotal(rubric:WritingRubric):number{return rubric.themeAndTask+rubric.textType+rubric.cohesion+rubric.argumentation+rubric.standardLanguage;}
export function createWritingSessionRecord(input:Partial<WritingSessionRecord>&Pick<WritingSessionRecord,"prompt">):WritingSessionRecord {
  const prompt=String(input.prompt??"").trim();if(!prompt)throw new TypeError("Writing prompt cannot be blank");
  return {version:1,prompt,draftText:String(input.draftText??""),rubric:input.rubric?normalizeWritingRubric(input.rubric):null,checklist:normalizeWritingChecklist(input.checklist??defaultChecklist())};
}
export function encodeWritingSessionRecord(record:WritingSessionRecord):string{return WRITING_PREFIX+JSON.stringify(createWritingSessionRecord(record));}
export function decodeWritingSessionRecord(note:string|null|undefined):WritingSessionRecord|null {
  if(!String(note??"").startsWith(WRITING_PREFIX))return null;
  try{const raw=JSON.parse(String(note).slice(WRITING_PREFIX.length));if(raw?.version!==1)return null;return createWritingSessionRecord(raw);}catch{return null;}
}

function localWeekBounds(at:string|number|Date):[number,number]{const d=at instanceof Date?new Date(at):new Date(at);if(!Number.isFinite(d.getTime()))throw new TypeError("Invalid writing progress timestamp");const day=(d.getDay()+6)%7;const start=new Date(d.getFullYear(),d.getMonth(),d.getDate()-day,0,0,0,0).getTime();return[start,start+7*86400000];}
export function deriveWritingWeeklyProgress(state:StudyState,at:string|number|Date=new Date()):WritingWeeklyProgress {
  if(!state.activePlanId)return{completed:0,target:null,reached:false};const [start,end]=localWeekBounds(at);const completed=state.sessions.filter(session=>session.planId===state.activePlanId&&session.status==="completed"&&decodeWritingSessionRecord(session.note)!=null&&session.endedAt!=null).filter(session=>{const ms=Date.parse(session.endedAt!);return ms>=start&&ms<end}).length;
  const goal=state.goals.find(item=>item.planId===state.activePlanId&&item.active&&item.metric==="writing-sessions"&&item.period==="weekly");const target=goal?Math.max(1,Math.round(goal.target)):state.plans.find(plan=>plan.id===state.activePlanId)?.configuration.publicExam?.bbReferencePreset?.weeklyWritingSessionGoal??null;return{completed,target,reached:target!=null&&completed>=target};
}

export function activeWritingSession(state:StudyState):StudyState["sessions"][number]|null {
  const activityId=state.focus?.activityId??null;if(!activityId)return null;const activity=state.activities.find(item=>item.id===activityId);if(!activity||normalizeStudyActivityType(activity.activityType,activity.kind)!=="WRITING")return null;return state.sessions.filter(session=>session.planId===state.activePlanId&&session.activityId===activityId&&(session.status==="running"||session.status==="paused")).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt))[0]??null;
}

export function writingActivity(activity:Activity|null|undefined):boolean{return Boolean(activity&&normalizeStudyActivityType(activity.activityType,activity.kind)==="WRITING");}
