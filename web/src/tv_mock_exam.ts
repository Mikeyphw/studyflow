"use strict";

export type TvMockExamPhase="ready"|"running"|"paused"|"results";

export function tvMockChoiceOptionId(index:number):string{return `choice-${Math.max(0,Math.trunc(index))}`;}
export function tvMockRemainingMs(durationMinutes:number,startedAtEpochMillis:number|null,nowEpochMillis:number,pausedAtEpochMillis:number|null,pausedAccumulatedMs:number):number{
 const total=Math.max(1,Number(durationMinutes)||1)*60_000;
 if(startedAtEpochMillis==null)return total;
 const endOfElapsed=pausedAtEpochMillis==null?nowEpochMillis:pausedAtEpochMillis;
 const elapsed=Math.max(0,endOfElapsed-startedAtEpochMillis-Math.max(0,pausedAccumulatedMs||0));
 return Math.max(0,total-elapsed);
}
export function tvMockProgressPercent(durationMinutes:number,remainingMs:number):number{
 const total=Math.max(1,Number(durationMinutes)||1)*60_000;
 const remaining=Math.max(0,Math.min(total,Number(remainingMs)||0));
 return Math.max(0,Math.min(100,Math.round((1-remaining/total)*100)));
}
export function tvMockAnsweredCount(answers:Record<string,string>):number{return Object.values(answers).filter(value=>String(value??"").trim().length>0).length;}
export function tvMockQuestionProgressPercent(index:number,total:number):number{return total<=0?0:Math.max(0,Math.min(100,Math.round(((Math.max(0,index)+1)/total)*100)));}
