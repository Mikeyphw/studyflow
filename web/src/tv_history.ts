"use strict";

export type TvHistoryScreen="today"|"plan"|"practice"|"progress"|"session"|"questions"|"mock"|"review"|"schedule";
export type TvHistoryOverlay="search"|"settings"|"material"|null;
export interface TvHistoryEntry {
  version:1;
  screen:TvHistoryScreen;
  overlay:TvHistoryOverlay;
  overlayId:string|null;
}

const SCREENS=new Set<TvHistoryScreen>(["today","plan","practice","progress","session","questions","mock","review","schedule"]);
const OVERLAYS=new Set<Exclude<TvHistoryOverlay,null>>(["search","settings","material"]);
export const TV_HISTORY_STATE_KEY="studyflowTvHistory";

export function screenFromHash(hash:string):TvHistoryScreen{
 const candidate=hash.replace(/^#/,"") as TvHistoryScreen;
 return SCREENS.has(candidate)?candidate:"today";
}

export function readTvHistoryEntry(state:unknown,hash:string):TvHistoryEntry{
 const fallback:TvHistoryEntry={version:1,screen:screenFromHash(hash),overlay:null,overlayId:null};
 if(!state||typeof state!=="object")return fallback;
 const raw=(state as Record<string,unknown>)[TV_HISTORY_STATE_KEY];
 if(!raw||typeof raw!=="object")return fallback;
 const value=raw as Record<string,unknown>;
 const screen=SCREENS.has(value.screen as TvHistoryScreen)?value.screen as TvHistoryScreen:fallback.screen;
 const overlay=OVERLAYS.has(value.overlay as Exclude<TvHistoryOverlay,null>)?value.overlay as Exclude<TvHistoryOverlay,null>:null;
 const overlayId=overlay==="material"&&typeof value.overlayId==="string"&&value.overlayId?value.overlayId:null;
 return{version:1,screen,overlay,overlayId};
}

export function tvHistoryState(entry:TvHistoryEntry):Record<string,TvHistoryEntry>{return{[TV_HISTORY_STATE_KEY]:entry};}
export function tvHistoryUrl(screen:TvHistoryScreen):string{const query=typeof location==="undefined"?"":location.search;return`${typeof location==="undefined"?"":location.pathname}${query}#${screen}`;}

export function replaceTvHistoryEntry(entry:TvHistoryEntry):void{
 if(typeof history==="undefined")return;history.replaceState(tvHistoryState(entry),"",tvHistoryUrl(entry.screen));
}
export function pushTvHistoryEntry(entry:TvHistoryEntry):void{
 if(typeof history==="undefined")return;history.pushState(tvHistoryState(entry),"",tvHistoryUrl(entry.screen));
}
