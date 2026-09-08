"use strict";

import type {StudyFlowTvSurface} from "./tv_control";
import type {StudyState} from "./types";
import {deriveProgressMetrics} from "./domain";

const DAY_MS=86_400_000;

type ValueRecord=Record<string,unknown>;
const valueRecord=(value:unknown):ValueRecord=>value&&typeof value==="object"?value as ValueRecord:{};
const observedMs=(value:string|null|undefined)=>{const ms=Date.parse(String(value??""));return Number.isFinite(ms)?ms:null};
const percent=(value:number|null)=>value==null?null:Math.max(0,Math.min(100,Math.round(value*100)));
const isoDay=(date:Date)=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;

export interface TvProgressSubjectSnapshot {
  subjectId:string;
  name:string;
  weeklyStudyMinutes:number;
  weeklyQuestionAttempts:number;
  accuracyPercent:number|null;
  coveredTopics:number;
  activeTopics:number;
  dueReviews:number;
  openErrors:number;
  attentionScore:number;
  recommendedSurface:StudyFlowTvSurface;
  reason:string;
}

export interface TvProgressSnapshot {
  planId:string|null;
  planName:string;
  weekLabel:string;
  weeklyStudyMinutes:number;
  weeklyQuestionAttempts:number;
  accuracyPercent:number|null;
  readinessPercent:number|null;
  readinessReason:string;
  reviewCompletionPercent:number|null;
  coveragePercent:number|null;
  subjects:TvProgressSubjectSnapshot[];
  weakSubjects:TvProgressSubjectSnapshot[];
}

export function tvProgressRecommendedSurface(input:{accuracyPercent:number|null;dueReviews:number;openErrors:number;coveredTopics:number;activeTopics:number}):StudyFlowTvSurface{
  if(input.openErrors>0||input.dueReviews>0)return "REVIEW";
  if(input.accuracyPercent!=null&&input.accuracyPercent<75)return "QUESTION_SET";
  if(input.activeTopics>0&&input.coveredTopics<input.activeTopics)return "STUDY_SESSION";
  return "QUESTION_SET";
}

export function buildTvProgressSnapshot(state:StudyState,at:Date=new Date()):TvProgressSnapshot{
  const planId=state.activePlanId;const plan=state.plans.find(item=>item.id===planId)??null;const metrics=deriveProgressMetrics(state,{at:at.toISOString(),planId});const atMs=at.getTime();const sinceMs=atMs-7*DAY_MS;
  const weeklyEvidence=state.evidence.filter(item=>item.planId===planId&&item.kind==="question-attempt").filter(item=>{const ms=observedMs(item.observedAt);return ms!=null&&ms>=sinceMs&&ms<=atMs});
  const weeklySessions=state.sessions.filter(item=>item.planId===planId&&item.status==="completed").filter(item=>{const ms=observedMs(item.endedAt??item.updatedAt??item.startedAt);return ms!=null&&ms>=sinceMs&&ms<=atMs});
  const subjectOrder=new Map(state.subjects.filter(subject=>subject.planId===planId).slice().sort((a,b)=>a.order-b.order||a.name.localeCompare(b.name)).map((subject,index)=>[subject.id,index]));
  const rows=state.subjects.filter(subject=>subject.planId===planId&&!subject.archived).map(subject=>{
    const topics=state.topics.filter(topic=>topic.planId===planId&&topic.subjectId===subject.id&&topic.status!=="archived");const topicIds=new Set(topics.map(topic=>topic.id));const covered=new Set(state.evidence.filter(item=>item.planId===planId&&item.topicId!=null&&topicIds.has(item.topicId)).map(item=>item.topicId as string));
    const attempts=weeklyEvidence.filter(item=>(item.subjectId??state.questions.find(question=>question.id===item.questionId)?.subjectId??null)===subject.id);const gradable=attempts.filter(item=>valueRecord(item.value).gradable===true);const correct=gradable.filter(item=>valueRecord(item.value).correct===true).length;const accuracyPercent=gradable.length?Math.round(correct/gradable.length*100):null;
    const weeklyStudyMinutes=weeklySessions.reduce((total,session)=>{const activity=state.activities.find(item=>item.id===session.activityId);return activity?.subjectId===subject.id?total+Number(session.durationMinutes??0):total},0);
    const dueReviews=state.reviews.filter(review=>review.planId===planId&&review.subjectId===subject.id&&review.status==="due"&&Date.parse(review.dueAt)<=atMs).length;const openErrors=state.errorEntries.filter(error=>error.planId===planId&&error.subjectId===subject.id&&error.status==="open").length;const uncovered=Math.max(0,topics.length-covered.size);const accuracyPenalty=accuracyPercent==null?0:Math.max(0,80-accuracyPercent)/10;const coveragePenalty=topics.length?uncovered/topics.length*4:0;const attentionScore=Math.round((openErrors*3+dueReviews*2+accuracyPenalty+coveragePenalty)*100)/100;
    const recommendedSurface=tvProgressRecommendedSurface({accuracyPercent,dueReviews,openErrors,coveredTopics:covered.size,activeTopics:topics.length});const reasons:string[]=[];if(openErrors)reasons.push(`${openErrors} open error${openErrors===1?"":"s"}`);if(dueReviews)reasons.push(`${dueReviews} due review${dueReviews===1?"":"s"}`);if(accuracyPercent!=null&&accuracyPercent<80)reasons.push(`${accuracyPercent}% recent accuracy`);if(uncovered)reasons.push(`${uncovered} topic${uncovered===1?"":"s"} without evidence`);if(!reasons.length)reasons.push("No current weakness signal");
    return{subjectId:subject.id,name:subject.name,weeklyStudyMinutes,weeklyQuestionAttempts:attempts.length,accuracyPercent,coveredTopics:covered.size,activeTopics:topics.length,dueReviews,openErrors,attentionScore,recommendedSurface,reason:reasons.join(" · ")};
  }).sort((a,b)=>(subjectOrder.get(a.subjectId)??999)-(subjectOrder.get(b.subjectId)??999)||a.name.localeCompare(b.name));
  const weakSubjects=rows.filter(row=>row.attentionScore>0).slice().sort((a,b)=>b.attentionScore-a.attentionScore||a.name.localeCompare(b.name)).slice(0,6);const weekStart=new Date(atMs-6*DAY_MS);const weeklyGradable=weeklyEvidence.filter(item=>valueRecord(item.value).gradable===true);const weeklyCorrect=weeklyGradable.filter(item=>valueRecord(item.value).correct===true).length;const weeklyAccuracyPercent=weeklyGradable.length?Math.round(weeklyCorrect/weeklyGradable.length*100):null;
  return{planId,planName:plan?.name??"No active plan",weekLabel:`${isoDay(weekStart)} → ${isoDay(at)}`,weeklyStudyMinutes:metrics.weeklyStudyMinutes,weeklyQuestionAttempts:weeklyEvidence.length,accuracyPercent:weeklyAccuracyPercent,readinessPercent:metrics.readiness,readinessReason:metrics.readinessReason,reviewCompletionPercent:percent(metrics.reviewCompletionRate),coveragePercent:percent(metrics.topicCoverage),subjects:rows,weakSubjects};
}
