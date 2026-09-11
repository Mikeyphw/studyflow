"use strict";

import type { SflpPeerDescriptor } from "./sflp_link.js";

export type StudyFlowPresenceAvailability="online"|"away"|"offline";
export type StudyFlowPresenceStatus=StudyFlowPresenceAvailability|"stale";
export type StudyFlowPresenceActivityKind="dashboard"|"plan"|"schedule"|"study_session"|"practice_question"|"review_item"|"library_material"|"document_view"|"writing_draft"|"presentation_projection";
export type StudyFlowPresenceContinuityMode="snapshot"|"live_control"|"presentation_sink";

export interface StudyFlowPresenceActivityHint{
 activityId:string;
 kind:StudyFlowPresenceActivityKind;
 entityId?:string;
 positionToken:string;
 continuityMode:StudyFlowPresenceContinuityMode;
 requiredCapabilities:string[];
}

export interface StudyFlowPresenceRouteTrace{
 sourceDeviceId:string;
 visitedDeviceIds:string[];
 hopLimit:number;
}

export interface StudyFlowPresenceRecord{
 workspaceId:string;
 sourceDevice:SflpPeerDescriptor;
 instanceId:string;
 sessionId:string;
 sessionStartedAtEpochMillis:number;
 sequence:number;
 availability:StudyFlowPresenceAvailability;
 observedAtEpochMillis:number;
 activityHint?:StudyFlowPresenceActivityHint;
 visibilityCapabilities:string[];
 trace:StudyFlowPresenceRouteTrace;
}

export interface StudyFlowPresenceView{
 workspaceId:string;
 sourceDevice:SflpPeerDescriptor;
 instanceId:string;
 status:StudyFlowPresenceStatus;
 observedAtEpochMillis:number;
 activityHint?:StudyFlowPresenceActivityHint;
}

export interface StudyFlowPresencePolicy{staleAfterMillis:number;expiryMillis:number;}
export interface StudyFlowPresenceExchange{records:StudyFlowPresenceRecord[];}
export type StudyFlowPresenceIngestResult="accepted"|"duplicate"|"stale"|"loop"|"expired"|"untrusted";

export const PRESENCE01_DEFAULT_POLICY:StudyFlowPresencePolicy={staleAfterMillis:45_000,expiryMillis:180_000};
const MAX_RECORDS=128;

function randomToken(prefix:string):string{
 const bytes=new Uint8Array(16);
 globalThis.crypto?.getRandomValues?.(bytes);
 const suffix=[...bytes].map(value=>value.toString(16).padStart(2,"0")).join("")||Math.random().toString(16).slice(2);
 return `${prefix}-${suffix}`;
}
function capabilitiesAllow(required:string[],peer:SflpPeerDescriptor):boolean{const caps=new Set(peer.capabilities);return required.every(cap=>caps.has(cap));}
function key(record:StudyFlowPresenceRecord):string{return `${record.workspaceId}|${record.sourceDevice.deviceId}|${record.instanceId}`;}
function clone<T>(value:T):T{return JSON.parse(JSON.stringify(value)) as T;}

export class StudyFlowWebPresenceRuntime{
 private readonly records=new Map<string,StudyFlowPresenceRecord>();
 private readonly localState=new Map<string,{sequence:number;availability:StudyFlowPresenceAvailability;activityHint?:StudyFlowPresenceActivityHint;visibilityCapabilities:string[]}>();
 private readonly instanceId=randomToken("instance");
 private readonly sessionId=randomToken("session");
 private readonly sessionStartedAtEpochMillis:number;

 constructor(
  private readonly localDevice:SflpPeerDescriptor,
  private readonly now:()=>number=()=>Date.now(),
  private readonly policy:StudyFlowPresencePolicy=PRESENCE01_DEFAULT_POLICY,
 ){
  if(policy.staleAfterMillis<=0||policy.expiryMillis<=policy.staleAfterMillis)throw new Error("Invalid presence expiry policy");
  this.sessionStartedAtEpochMillis=this.now();
 }

 announce(workspaceId:string,availability:StudyFlowPresenceAvailability,activityHint?:StudyFlowPresenceActivityHint,visibilityCapabilities:string[]=[]):StudyFlowPresenceRecord{
  if(!workspaceId.trim())throw new Error("Presence workspace must not be blank");
  if(activityHint&&activityHint.requiredCapabilities.length===0)throw new Error("Presence activity hint requires capabilities");
  const previous=this.localState.get(workspaceId);
  const state={sequence:(previous?.sequence??0)+1,availability,activityHint,visibilityCapabilities:[...new Set(visibilityCapabilities)]};
  this.localState.set(workspaceId,state);
  const record:StudyFlowPresenceRecord={workspaceId,sourceDevice:this.localDevice,instanceId:this.instanceId,sessionId:this.sessionId,sessionStartedAtEpochMillis:this.sessionStartedAtEpochMillis,sequence:state.sequence,availability,observedAtEpochMillis:this.now(),activityHint,visibilityCapabilities:state.visibilityCapabilities,trace:{sourceDeviceId:this.localDevice.deviceId,visitedDeviceIds:[this.localDevice.deviceId],hopLimit:8}};
  this.records.set(key(record),clone(record));
  return clone(record);
 }

 ensureOnline(workspaceId:string):StudyFlowPresenceRecord{
  const current=this.localState.get(workspaceId);
  return this.announce(workspaceId,current?.availability??"online",current?.activityHint,current?.visibilityCapabilities??[]);
 }

 ingest(record:StudyFlowPresenceRecord,authenticatedPeerDeviceId:string,trustedSourceDeviceIds:Set<string>,nowEpochMillis=this.now()):StudyFlowPresenceIngestResult{
  this.expire(nowEpochMillis);
  if(nowEpochMillis-record.observedAtEpochMillis>this.policy.expiryMillis)return"expired";
  if(record.sourceDevice.deviceId===this.localDevice.deviceId||record.trace.visitedDeviceIds.includes(this.localDevice.deviceId))return"loop";
  if(record.trace.visitedDeviceIds[record.trace.visitedDeviceIds.length-1]!==authenticatedPeerDeviceId)return"untrusted";
  if(!trustedSourceDeviceIds.has(record.sourceDevice.deviceId))return"untrusted";
  const current=this.records.get(key(record));
  if(current){
   if(current.sessionId===record.sessionId){
    if(record.sequence===current.sequence)return"duplicate";
    if(record.sequence<current.sequence)return"stale";
   }else if(record.sessionStartedAtEpochMillis<current.sessionStartedAtEpochMillis||
    (record.sessionStartedAtEpochMillis===current.sessionStartedAtEpochMillis&&record.sessionId<=current.sessionId))return"stale";
  }
  if(record.trace.visitedDeviceIds.length>=record.trace.hopLimit)return"stale";
  const relayed=clone(record);
  relayed.trace.visitedDeviceIds=[...relayed.trace.visitedDeviceIds,this.localDevice.deviceId];
  this.records.set(key(relayed),relayed);
  return"accepted";
 }

 recordsForPeer(workspaceId:string,peer:SflpPeerDescriptor,nowEpochMillis=this.now()):StudyFlowPresenceRecord[]{
  this.expire(nowEpochMillis);
  return [...this.records.values()]
   .filter(record=>record.workspaceId===workspaceId)
   .filter(record=>record.sourceDevice.deviceId!==peer.deviceId)
   .filter(record=>!record.trace.visitedDeviceIds.includes(peer.deviceId))
   .filter(record=>capabilitiesAllow(record.visibilityCapabilities,peer))
   .sort((a,b)=>a.sourceDevice.deviceId.localeCompare(b.sourceDevice.deviceId)||a.instanceId.localeCompare(b.instanceId))
   .slice(0,MAX_RECORDS).map(clone);
 }

 exchangeForPeer(workspaceId:string,peer:SflpPeerDescriptor,nowEpochMillis=this.now()):StudyFlowPresenceExchange{
  this.ensureOnline(workspaceId);
  return{records:this.recordsForPeer(workspaceId,peer,nowEpochMillis)};
 }

 acceptExchange(workspaceId:string,authenticatedPeerDeviceId:string,exchange:StudyFlowPresenceExchange,trustedSourceDeviceIds:Set<string>,nowEpochMillis=this.now()):StudyFlowPresenceIngestResult[]{
  if(exchange.records.length>MAX_RECORDS)throw new Error("Presence exchange is too large");
  return exchange.records.filter(record=>record.workspaceId===workspaceId).map(record=>this.ingest(record,authenticatedPeerDeviceId,trustedSourceDeviceIds,nowEpochMillis));
 }

 views(workspaceId:string,viewer:SflpPeerDescriptor=this.localDevice,nowEpochMillis=this.now()):StudyFlowPresenceView[]{
  this.expire(nowEpochMillis);
  return [...this.records.values()].filter(record=>record.workspaceId===workspaceId&&capabilitiesAllow(record.visibilityCapabilities,viewer)).map(record=>({
   workspaceId:record.workspaceId,
   sourceDevice:clone(record.sourceDevice),
   instanceId:record.instanceId,
   status:this.status(record,nowEpochMillis),
   observedAtEpochMillis:record.observedAtEpochMillis,
   activityHint:record.activityHint&&capabilitiesAllow(record.activityHint.requiredCapabilities,viewer)?clone(record.activityHint):undefined,
  })).sort((a,b)=>a.sourceDevice.deviceId.localeCompare(b.sourceDevice.deviceId)||a.instanceId.localeCompare(b.instanceId));
 }

 forgetPeer(deviceId:string):void{for(const [entryKey,record] of this.records)if(record.sourceDevice.deviceId===deviceId)this.records.delete(entryKey);}

 private status(record:StudyFlowPresenceRecord,nowEpochMillis:number):StudyFlowPresenceStatus{
  if(record.availability==="offline")return"offline";
  if(nowEpochMillis-record.observedAtEpochMillis>this.policy.staleAfterMillis)return"stale";
  return record.availability;
 }
 private expire(nowEpochMillis:number):void{for(const [entryKey,record] of this.records)if(nowEpochMillis-record.observedAtEpochMillis>this.policy.expiryMillis)this.records.delete(entryKey);}
}
