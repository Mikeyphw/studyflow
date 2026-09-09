"use strict";
import type { Material } from "./types";
import { planMaterialViewer } from "./retention_viewer";

export type TvLibraryFilter="all"|"local"|"reference";
export const TV_LIBRARY_TEXT_PAGE_CHARS=3600;

export function tvLibraryMaterials(materials:Material[],filter:TvLibraryFilter):Material[]{
 const sorted=materials.slice().sort((a,b)=>Number(a.archived)-Number(b.archived)||a.title.localeCompare(b.title)||a.id.localeCompare(b.id));
 if(filter==="local")return sorted.filter(material=>Boolean(material.attachmentId));
 if(filter==="reference")return sorted.filter(material=>!material.attachmentId&&planMaterialViewer(material).kind==="web-page");
 return sorted;
}

export function tvLibraryNextIndex(current:number,total:number,delta:number):number{
 if(total<=0)return 0;
 return Math.max(0,Math.min(total-1,current+delta));
}

export function paginateTvText(text:string,maxChars=TV_LIBRARY_TEXT_PAGE_CHARS):string[]{
 const normalized=String(text??"").replace(/\r\n?/g,"\n");
 const limit=Math.max(600,Math.floor(maxChars));
 if(normalized.length<=limit)return[normalized];
 const pages:string[]=[];let cursor=0;
 while(cursor<normalized.length){
  let end=Math.min(normalized.length,cursor+limit);
  if(end<normalized.length){
   const floor=cursor+Math.floor(limit*.6);
   const newline=normalized.lastIndexOf("\n",end);
   const space=normalized.lastIndexOf(" ",end);
   const boundary=Math.max(newline,space);
   if(boundary>=floor)end=boundary+1;
  }
  pages.push(normalized.slice(cursor,end).trimEnd());
  cursor=end;
 }
 return pages.length?pages:[""];
}

export function tvPdfPageUrl(url:string,page:number):string{
 const base=String(url??"").split("#")[0];
 const safePage=Math.max(1,Math.floor(Number(page)||1));
 return `${base}#page=${safePage}&zoom=page-width`;
}
