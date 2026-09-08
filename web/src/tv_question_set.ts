"use strict";

export const TV_QUESTION_SET_LIMIT=40;
export function tvQuestionChoiceOptionId(index:number):string{return `choice-${Math.max(0,Math.trunc(index))}`;}
export function tvQuestionChoiceIndex(optionId:string|null|undefined):number|null{
 const match=/^choice-(\d+)$/.exec(String(optionId??""));if(!match)return null;const index=Number(match[1]);return Number.isSafeInteger(index)&&index>=0?index:null;
}
export function tvQuestionProgressPercent(index:number,total:number):number{
 if(total<=0)return 0;return Math.max(0,Math.min(100,Math.round(((Math.max(0,index)+1)/total)*100)));
}
export function tvQuestionChoiceLabel(index:number):string{
 const alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ";return index>=0&&index<alphabet.length?alphabet[index]:String(index+1);
}
