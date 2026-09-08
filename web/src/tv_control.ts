"use strict";

export const TV_CONTROL_PROTOCOL_VERSION=1;
export const TV_PAIRING_CODE_LENGTH=6;
export const TV_PAIRING_TTL_SECONDS=120;

export type StudyFlowTvSurface="HOME"|"TODAY"|"STUDY_SESSION"|"QUESTION_SET"|"REVIEW"|"MOCK_EXAM"|"PROGRESS"|"SCHEDULE"|"LIBRARY"|"SETTINGS";
export type StudyFlowTvControlAction="OPEN"|"START"|"PAUSE"|"RESUME"|"NEXT"|"PREVIOUS"|"SELECT_ANSWER"|"SHOW_EXPLANATION"|"MARK_FOR_REVIEW"|"SUBMIT"|"REVIEW_RESULTS"|"COMPLETE"|"BACK"|"STOP_CAST";
export interface StudyFlowTvControlCommand {commandId:string;protocolVersion:number;surface:StudyFlowTvSurface;action:StudyFlowTvControlAction;targetId?:string|null;optionId?:string|null;issuedAtEpochMillis:number;requiresUserPresence:true;}
export interface StudyFlowTvCastState {castId:string;protocolVersion:number;surface:StudyFlowTvSurface;title:string;subtitle?:string|null;progressPercent?:number|null;activeItemId?:string|null;updatedAtEpochMillis:number;}
export interface TvReceiverSnapshot {state:"disabled"|"connecting"|"pairing"|"paired"|"revoked"|"error";pairingCode:string|null;expiresAtEpochMillis:number|null;deviceName:string;transport:"sse"|"poll"|"none";message:string;}
export interface TvControlReceiverHandle {getSnapshot:()=>TvReceiverSnapshot;subscribe:(listener:()=>void)=>()=>void;dispose:()=>void;}

type ReceiverRegistration={receiverId:string;pairingCode:string;sessionNonce:string;expiresAtEpochMillis:number;deviceName:string;paired:boolean};
type RelayEnvelope={sequence:number;type:"command"|"cast"|"pairing";payload:unknown};
const SURFACES=new Set<StudyFlowTvSurface>(["HOME","TODAY","STUDY_SESSION","QUESTION_SET","REVIEW","MOCK_EXAM","PROGRESS","SCHEDULE","LIBRARY","SETTINGS"]);
const ACTIONS=new Set<StudyFlowTvControlAction>(["OPEN","START","PAUSE","RESUME","NEXT","PREVIOUS","SELECT_ANSWER","SHOW_EXPLANATION","MARK_FOR_REVIEW","SUBMIT","REVIEW_RESULTS","COMPLETE","BACK","STOP_CAST"]);
const CASTABLE_SURFACES=new Set<StudyFlowTvSurface>(["STUDY_SESSION","QUESTION_SET","REVIEW","MOCK_EXAM","PROGRESS","SCHEDULE","LIBRARY"]);

export function isStudyFlowTvControlCommand(value:unknown):value is StudyFlowTvControlCommand{
 if(!value||typeof value!=="object")return false;const v=value as Record<string,unknown>;
 return typeof v.commandId==="string"&&v.commandId.length>0&&v.protocolVersion===TV_CONTROL_PROTOCOL_VERSION&&SURFACES.has(v.surface as StudyFlowTvSurface)&&ACTIONS.has(v.action as StudyFlowTvControlAction)&&typeof v.issuedAtEpochMillis==="number"&&v.requiresUserPresence===true;
}
export function isStudyFlowTvCastState(value:unknown):value is StudyFlowTvCastState{
 if(!value||typeof value!=="object")return false;const v=value as Record<string,unknown>;const progress=v.progressPercent;
 return typeof v.castId==="string"&&v.castId.length>0&&v.protocolVersion===TV_CONTROL_PROTOCOL_VERSION&&CASTABLE_SURFACES.has(v.surface as StudyFlowTvSurface)&&typeof v.title==="string"&&v.title.trim().length>0&&typeof v.updatedAtEpochMillis==="number"&&(progress==null||(typeof progress==="number"&&progress>=0&&progress<=100));
}

function dispatchEnvelope(envelope:RelayEnvelope):void{
 if(envelope.type==="command"&&isStudyFlowTvControlCommand(envelope.payload))globalThis.dispatchEvent(new CustomEvent("studyflow:tv-control",{detail:envelope.payload}));
 if(envelope.type==="cast"&&isStudyFlowTvCastState(envelope.payload))globalThis.dispatchEvent(new CustomEvent("studyflow:tv-cast",{detail:envelope.payload}));
}

export function installTvControlReceiver(enabled:boolean,deviceName="StudyFlow Browser TV"):TvControlReceiverHandle{
 let snapshot:TvReceiverSnapshot={state:enabled?"connecting":"disabled",pairingCode:null,expiresAtEpochMillis:null,deviceName,transport:"none",message:enabled?"Starting TV control receiver.":"TV control receiver disabled."};
 const listeners=new Set<()=>void>();let disposed=false;let source:EventSource|null=null;let abort:AbortController|null=null;let registration:ReceiverRegistration|null=null;let pollCursor=0;let refreshTimer:number|null=null;
 const publish=(next:Partial<TvReceiverSnapshot>)=>{snapshot={...snapshot,...next};for(const listener of listeners)listener();};
 const endpoint=(path:string)=>path;
 const register=async()=>{try{
  const response=await fetch(endpoint("/api/tv/receiver"),{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({deviceName})});
  if(!response.ok)throw new Error(`TV relay registration failed: HTTP ${response.status}`);registration=await response.json() as ReceiverRegistration;if(disposed)return;
  publish({state:registration.paired?"paired":"pairing",pairingCode:registration.pairingCode,expiresAtEpochMillis:registration.expiresAtEpochMillis,message:registration.paired?"Phone paired.":"Enter this code on the phone remote."});
  if(refreshTimer!=null)clearTimeout(refreshTimer);refreshTimer=globalThis.setTimeout(()=>{if(!disposed&&snapshot.state==="pairing"){source?.close();abort?.abort();registration=null;pollCursor=0;void register();}},Math.max(1000,registration.expiresAtEpochMillis-Date.now()+250));connectEvents();
 }catch(error){if(!disposed)publish({state:"error",message:error instanceof Error?error.message:String(error)});}};
 const receive=(envelope:RelayEnvelope)=>{pollCursor=Math.max(pollCursor,Number(envelope.sequence)||0);if(envelope.type==="pairing"){
   const p=envelope.payload as Record<string,unknown>;const state=String(p?.state??"");if(state==="PAIRED"){if(refreshTimer!=null){clearTimeout(refreshTimer);refreshTimer=null}publish({state:"paired",message:"Phone paired and ready."});}else if(state==="REVOKED"){publish({state:"revoked",message:"Phone pairing revoked. Creating a new pairing code."});source?.close();abort?.abort();registration=null;pollCursor=0;globalThis.setTimeout(()=>{if(!disposed)void register()},250);}
  }dispatchEnvelope(envelope);};
 const startPoll=()=>{if(!registration||disposed)return;publish({transport:"poll"});abort=new AbortController();const loop=async()=>{while(!disposed&&registration){try{const url=`/api/tv/poll?receiverId=${encodeURIComponent(registration.receiverId)}&nonce=${encodeURIComponent(registration.sessionNonce)}&after=${pollCursor}`;const res=await fetch(url,{cache:"no-store",signal:abort?.signal});if(!res.ok)throw new Error(`poll HTTP ${res.status}`);const body=await res.json() as {events:RelayEnvelope[]};for(const event of body.events||[])receive(event);}catch(error){if(disposed)return;publish({state:"error",message:`TV relay polling failed: ${error instanceof Error?error.message:String(error)}`});await new Promise(resolve=>setTimeout(resolve,1500));}}};void loop();};
 const connectEvents=()=>{if(!registration||disposed)return;if(typeof EventSource==="undefined"){startPoll();return;}publish({transport:"sse"});const url=`/api/tv/events?receiverId=${encodeURIComponent(registration.receiverId)}&nonce=${encodeURIComponent(registration.sessionNonce)}`;source=new EventSource(url);source.onmessage=(event)=>{try{receive(JSON.parse(event.data) as RelayEnvelope)}catch{}};source.onerror=()=>{source?.close();source=null;if(!disposed)startPoll();};};
 if(enabled)void register();
 return{getSnapshot:()=>snapshot,subscribe:(listener)=>{listeners.add(listener);return()=>listeners.delete(listener)},dispose:()=>{disposed=true;source?.close();abort?.abort();if(refreshTimer!=null)clearTimeout(refreshTimer);listeners.clear();}};
}
