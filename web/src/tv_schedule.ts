"use strict";

import {deriveScheduleCalendarSnapshot, localDateKey} from "./parity_intelligence";
import type {Activity, StudyState} from "./types";
import type {StudyFlowTvSurface} from "./tv_control";

export type TvScheduleView="today"|"week";
export interface TvScheduleItem {activity:Activity;subjectName:string|null;topicName:string|null;dateLabel:string;timeLabel:string;destination:StudyFlowTvSurface|null;launchable:boolean;}
export interface TvScheduleDay {date:string;label:string;items:TvScheduleItem[];}
export interface TvScheduleSnapshot {view:TvScheduleView;windowLabel:string;visibleCount:number;plannedMinutes:number;plannedCount:number;completedCount:number;cancelledCount:number;days:TvScheduleDay[];flatItems:TvScheduleItem[];nextIndex:number;}

const addLocalDays=(key:string,days:number)=>{const [y,m,d]=key.split("-").map(Number);const date=new Date(y,m-1,d+days);return localDateKey(date)};
export function tvScheduleActivityDestination(activityType:Activity["activityType"]):StudyFlowTvSurface|null{
  if(activityType==="QUESTIONS")return "QUESTION_SET";
  if(activityType==="REVIEW"||activityType==="FLASHCARDS")return "REVIEW";
  if(activityType==="MOCK_EXAM")return "MOCK_EXAM";
  if(activityType==="WRITING")return null;
  return "STUDY_SESSION";
}
export function tvScheduleDateLabel(date:string,today:string):string{
  if(date===today)return "Today";if(date===addLocalDays(today,1))return "Tomorrow";const [y,m,d]=date.split("-").map(Number);const weekday=["Sun","Mon","Tue","Wed","Thu","Fri","Sat"][new Date(y,m-1,d).getDay()];return `${weekday} · ${date}`;
}
export function tvScheduleTimeLabel(minute:number|null):string{return minute==null?"Any time":`${String(Math.floor(minute/60)).padStart(2,"0")}:${String(minute%60).padStart(2,"0")}`;}

export function buildTvScheduleSnapshot(state:StudyState,view:TvScheduleView="today",at:Date=new Date()):TvScheduleSnapshot{
  const calendar=deriveScheduleCalendarSnapshot(state,{view:view==="today"?"today":"this-week",at});const today=localDateKey(at);const items=calendar.activities.map(activity=>{const subject=state.subjects.find(item=>item.id===activity.subjectId)?.name??null;const topic=state.topics.find(item=>item.id===activity.topicId)?.name??null;const destination=tvScheduleActivityDestination(activity.activityType);return{activity,subjectName:subject,topicName:topic,dateLabel:tvScheduleDateLabel(activity.scheduledFor??today,today),timeLabel:tvScheduleTimeLabel(activity.scheduledStartMinute),destination,launchable:activity.status==="planned"&&destination!=null};});
  const groupMap=new Map<string,TvScheduleItem[]>();for(const item of items){const key=item.activity.scheduledFor??today;const list=groupMap.get(key)??[];list.push(item);groupMap.set(key,list);}const days=[...groupMap.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([date,dayItems])=>({date,label:tvScheduleDateLabel(date,today),items:dayItems}));
  const nowMinute=at.getHours()*60+at.getMinutes();let nextIndex=items.findIndex(item=>item.activity.status==="planned"&&item.activity.scheduledFor!=null&&(item.activity.scheduledFor>today||(item.activity.scheduledFor===today&&(item.activity.scheduledStartMinute??0)>=nowMinute)));if(nextIndex<0)nextIndex=items.findIndex(item=>item.activity.status==="planned");if(nextIndex<0)nextIndex=0;
  const start=calendar.window.startsOn??today,end=calendar.window.endsOnInclusive??today;return{view,windowLabel:view==="today"?today:`${start} → ${end}`,visibleCount:calendar.visibleCount,plannedMinutes:calendar.plannedMinutes,plannedCount:calendar.activities.filter(item=>item.status==="planned").length,completedCount:calendar.activities.filter(item=>item.status==="completed").length,cancelledCount:calendar.activities.filter(item=>item.status==="cancelled").length,days,flatItems:items,nextIndex};
}
