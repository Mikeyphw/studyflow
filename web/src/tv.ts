"use strict";

export type TvBrowserModeSource="query"|"user-agent"|"none";
export interface TvBrowserModeResolution { enabled:boolean; source:TvBrowserModeSource; forced:boolean; }
export interface TvBrowserAdapterHandle { resolution:TvBrowserModeResolution; dispose:()=>void; }

type Direction="left"|"right"|"up"|"down";
type RectLike={left:number;right:number;top:number;bottom:number;width:number;height:number};

const TV_USER_AGENT=/(?:Tizen|SMART[- ]?TV|SmartTV|Web0S|WebOS|NetCast|HbbTV|BRAVIA|GoogleTV|Android TV|AFT[A-Z0-9]+)/i;
const FOCUSABLE_SELECTOR='a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),summary,[role="button"],[role="tab"],[tabindex]';
const TEXT_INPUT_TYPES=new Set(["text","search","url","tel","email","password","number","date","time","month","week","datetime-local","range"]);

function currentSearch():string{return typeof globalThis.location==="undefined"?"":globalThis.location.search;}
function currentUserAgent():string{return typeof globalThis.navigator==="undefined"?"":globalThis.navigator.userAgent;}

export function resolveTvBrowserMode(search=currentSearch(),userAgent=currentUserAgent()):TvBrowserModeResolution{
 const params=new URLSearchParams(search);const requested=(params.get("tv")??"").trim().toLowerCase();
 if(["1","true","on","yes"].includes(requested))return{enabled:true,source:"query",forced:true};
 if(["0","false","off","no"].includes(requested))return{enabled:false,source:"query",forced:true};
 if(TV_USER_AGENT.test(userAgent))return{enabled:true,source:"user-agent",forced:false};
 return{enabled:false,source:"none",forced:false};
}

function isVisible(element:HTMLElement):boolean{
 if(element.hidden||element.getAttribute("aria-hidden")==="true"||element.getAttribute("aria-disabled")==="true"||element.tabIndex<0)return false;
 const style=globalThis.getComputedStyle?.(element);if(style&&(style.display==="none"||style.visibility==="hidden"))return false;
 const rect=element.getBoundingClientRect();return rect.width>0&&rect.height>0&&element.getClientRects().length>0;
}

function activeModal():HTMLElement|null{
 const dialogs=[...document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]')].filter(isVisible);return dialogs.length?dialogs[dialogs.length-1]:null;
}

function focusables():HTMLElement[]{const scope=activeModal()??document;return [...scope.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)].filter(isVisible);}
function center(rect:RectLike){return{x:rect.left+rect.width/2,y:rect.top+rect.height/2};}

export function spatialScore(source:RectLike,candidate:RectLike,direction:Direction):number|null{
 const a=center(source),b=center(candidate),dx=b.x-a.x,dy=b.y-a.y,tolerance=3;
 let primary:number,cross:number,beam:number;
 if(direction==="left"){if(dx>=-tolerance)return null;primary=-dx;cross=Math.abs(dy);beam=(source.height+candidate.height)/2;}
 else if(direction==="right"){if(dx<=tolerance)return null;primary=dx;cross=Math.abs(dy);beam=(source.height+candidate.height)/2;}
 else if(direction==="up"){if(dy>=-tolerance)return null;primary=-dy;cross=Math.abs(dx);beam=(source.width+candidate.width)/2;}
 else {if(dy<=tolerance)return null;primary=dy;cross=Math.abs(dx);beam=(source.width+candidate.width)/2;}
 const offBeam=Math.max(0,cross-beam);const distance=Math.hypot(dx,dy);return primary+offBeam*3+cross*.18+distance*.015;
}

function keyDirection(event:KeyboardEvent):Direction|null{
 const key=event.key||({37:"ArrowLeft",38:"ArrowUp",39:"ArrowRight",40:"ArrowDown"} as Record<number,string>)[event.keyCode]||"";
 if(key==="ArrowLeft")return"left";if(key==="ArrowRight")return"right";if(key==="ArrowUp")return"up";if(key==="ArrowDown")return"down";return null;
}
function controlOwnsArrow(target:Element|null):boolean{
 if(!(target instanceof HTMLElement))return false;if(target.isContentEditable||target instanceof HTMLTextAreaElement||target instanceof HTMLSelectElement)return true;
 if(target instanceof HTMLInputElement)return TEXT_INPUT_TYPES.has((target.type||"text").toLowerCase());return false;
}
function sourceRect(active:Element|null):RectLike{
 if(active instanceof HTMLElement){const rect=active.getBoundingClientRect();if(rect.width>0&&rect.height>0)return rect;}
 const x=globalThis.innerWidth/2,y=globalThis.innerHeight/2;return{left:x,right:x,top:y,bottom:y,width:0,height:0};
}
function moveSpatialFocus(direction:Direction):boolean{
 const active=document.activeElement;const source=sourceRect(active);let best:HTMLElement|null=null,bestScore=Number.POSITIVE_INFINITY;
 for(const candidate of focusables()){if(candidate===active)continue;const score=spatialScore(source,candidate.getBoundingClientRect(),direction);if(score!=null&&score<bestScore){bestScore=score;best=candidate;}}
 if(!best)return false;try{best.focus({preventScroll:true});}catch{best.focus();}try{best.scrollIntoView({block:"center",inline:"nearest"});}catch{best.scrollIntoView();}return true;
}
function installDpadNavigation():()=>void{
 const onKeyDown=(event:KeyboardEvent)=>{const direction=keyDirection(event);if(!direction||event.altKey||event.ctrlKey||event.metaKey)return;const target=event.target instanceof Element?event.target:null;
  if(controlOwnsArrow(target))return;if(target?.closest('[role="tablist"]')&&(direction==="left"||direction==="right"))return;
  if(moveSpatialFocus(direction)){event.preventDefault();event.stopPropagation();}
 };
 addEventListener("keydown",onKeyDown,true);return()=>removeEventListener("keydown",onKeyDown,true);
}

function installRemoteBackNavigation():()=>void{
 const onKeyDown=(event:KeyboardEvent)=>{const code=event.keyCode;const back=event.key==="BrowserBack"||event.key==="GoBack"||code===461||code===10009;if(!back)return;const target=event.target instanceof Element?event.target:null;if(controlOwnsArrow(target))return;event.preventDefault();event.stopPropagation();history.back();};
 addEventListener("keydown",onKeyDown,true);return()=>removeEventListener("keydown",onKeyDown,true);
}

function installMediaToggleNavigation():()=>void{
 const onKeyDown=(event:KeyboardEvent)=>{const code=event.keyCode;const media=event.key==="MediaPlayPause"||event.key==="Play"||event.key==="Pause"||code===179||code===415||code===19;if(!media)return;event.preventDefault();event.stopPropagation();dispatchEvent(new CustomEvent("studyflow:tv-media-toggle"));};
 addEventListener("keydown",onKeyDown,true);return()=>removeEventListener("keydown",onKeyDown,true);
}


function supportsFlexGap():boolean{
 const probe=document.createElement("div"),a=document.createElement("div"),b=document.createElement("div");probe.style.display="flex";probe.style.flexDirection="column";probe.style.rowGap="1px";probe.style.position="absolute";probe.style.visibility="hidden";a.style.height="1px";b.style.height="1px";probe.append(a,b);document.body.appendChild(probe);const supported=probe.scrollHeight===3;probe.remove();return supported;
}

function applyCapabilityFlags():void{
 const root=document.documentElement;root.dataset.studyflowTvSecureContext=globalThis.isSecureContext?"true":"false";
 root.dataset.studyflowTvIndexedDb=typeof globalThis.indexedDB!=="undefined"?"true":"false";
 root.dataset.studyflowTvSubtleCrypto=typeof globalThis.crypto?.subtle!=="undefined"?"true":"false";
 root.dataset.studyflowTvServiceWorker=typeof globalThis.navigator?.serviceWorker!=="undefined"?"true":"false";
 root.dataset.studyflowTvFlexGap=supportsFlexGap()?"true":"false";
 root.dataset.studyflowTvInputMode="remote-first";
}

export function installTvBrowserAdapter():TvBrowserAdapterHandle{
 const resolution=resolveTvBrowserMode();if(typeof document==="undefined")return{resolution,dispose:()=>{}};
 const root=document.documentElement;root.dataset.studyflowTv=resolution.enabled?"true":"false";root.dataset.studyflowTvMode=resolution.source;applyCapabilityFlags();
 const disposeDpad=resolution.enabled?installDpadNavigation():()=>{};const disposeBack=resolution.enabled?installRemoteBackNavigation():()=>{};const disposeMedia=resolution.enabled?installMediaToggleNavigation():()=>{};
 return{resolution,dispose:()=>{disposeDpad();disposeBack();disposeMedia();delete root.dataset.studyflowTv;delete root.dataset.studyflowTvMode;delete root.dataset.studyflowTvSecureContext;delete root.dataset.studyflowTvIndexedDb;delete root.dataset.studyflowTvSubtleCrypto;delete root.dataset.studyflowTvServiceWorker;delete root.dataset.studyflowTvFlexGap;delete root.dataset.studyflowTvInputMode;}};
}

export {
 TV_PARITY_MIN_ZOOM,TV_PARITY_MAX_ZOOM,TV_PARITY_ZOOM_STEP,TV_PARITY_ERROR_CATEGORIES,
 tvParityPage,tvParityZoom,tvParityGoalActions,tvParityNextErrorCategory,
 tvParityErrorResolutionAction,tvParitySearchScore,
} from "./tv_semantic_parity";
