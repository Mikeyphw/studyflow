"use strict";

export const TV_PARITY_MIN_ZOOM=50;
export const TV_PARITY_MAX_ZOOM=300;
export const TV_PARITY_ZOOM_STEP=25;
export const TV_PARITY_ERROR_CATEGORIES=[
 "CONTENT_GAP","FORGOTTEN","CONCEPT_CONFUSION","INTERPRETATION","DISTRACTION",
 "CALCULATION","TIME_PRESSURE","DISPUTED","OTHER",
] as const;

export function tvParityPage(current:number,total:number,delta:number):number{
 if(total<=0)return 0;
 return Math.max(0,Math.min(total-1,current+delta));
}

export function tvParityZoom(current:number,delta:number):number{
 return Math.max(TV_PARITY_MIN_ZOOM,Math.min(TV_PARITY_MAX_ZOOM,current+delta));
}

export function tvParityGoalActions(input:{completed:boolean;paused:boolean;canComplete:boolean}):string[]{
 if(input.completed)return[];
 if(input.paused)return["RESUME"];
 return input.canComplete?["COMPLETE","PAUSE"]:["PAUSE"];
}

export function tvParityNextErrorCategory(current:string):string{
 const index=TV_PARITY_ERROR_CATEGORIES.indexOf(current.toUpperCase() as typeof TV_PARITY_ERROR_CATEGORIES[number]);
 return TV_PARITY_ERROR_CATEGORIES[(index+1)%TV_PARITY_ERROR_CATEGORIES.length];
}

export function tvParityErrorResolutionAction(locallyResolved:boolean):string{
 return locallyResolved?"REOPEN":"RESOLVE";
}

export function tvParitySearchScore(query:string,title:string,subtitle:string):number|null{
 const terms=query.trim().toLowerCase().split(/\s+/).filter(Boolean);
 const normalizedTitle=title.toLowerCase(),normalizedSubtitle=subtitle.toLowerCase();
 if(terms.some(term=>!normalizedTitle.includes(term)&&!normalizedSubtitle.includes(term)))return null;
 return terms.reduce((sum,term)=>sum+(normalizedTitle===term?100:normalizedTitle.startsWith(term)?70:
  normalizedTitle.includes(term)?45:20),0);
}
