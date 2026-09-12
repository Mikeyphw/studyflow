"use strict";

import { StudyFlowWebPresenceRuntime, type StudyFlowPresenceActivityHint, type StudyFlowPresenceAvailability, type StudyFlowPresenceExchange, type StudyFlowPresenceIngestResult, type StudyFlowPresenceView } from "./presence.js";

export type SflpClientKind="android"|"desktop"|"web"|"android_tv"|"web_tv";
export type SflpRouteKind="websocket"|"manual"|"relay";
export type SflpTrustState="trusted"|"revoked";
export interface SflpProtocolSupport{major:number;minMinor:number;maxMinor:number;}
export interface SflpPeerDescriptor{deviceId:string;displayName:string;clientKind:SflpClientKind;protocolSupport:SflpProtocolSupport;capabilities:string[];}
export interface SflpTrustedPeerRecord{peer:SflpPeerDescriptor;state:SflpTrustState;pairedAtEpochMillis:number;updatedAtEpochMillis:number;lastSeenAtEpochMillis:number|null;revokedAtEpochMillis:number|null;trustSecret:string|null;}
export interface SflpEndpoint{kind:SflpRouteKind;uri:string;lastSeenAtEpochMillis:number;}
export interface SflpJournalOperation{workspaceId:string;operationId:string;originDeviceId:string;originSequence:number;entityId:string;entityRevision:number;operationType:string;payload:Record<string,unknown>;createdAtEpochMillis:number;}
export interface SflpQueueEntry{peerDeviceId:string;workspaceId:string;operationId:string;attempts:number;nextAttemptEpochMillis:number;}
export interface SflpWebPeerSnapshot{identity:SflpPeerDescriptor;trustedPeers:SflpTrustedPeerRecord[];endpoints:Record<string,SflpEndpoint[]>;pendingQueue:SflpQueueEntry[];journal:SflpJournalOperation[];}
export interface SflpPairingOffer{pairingCode:string;expiresAtEpochMillis:number;descriptor:SflpPeerDescriptor;endpointHints:string[];qrPayload:string;}

const STORAGE_KEY="studyflow.sflp.web.peer.v1";
const PAIRING_TTL_MILLIS=120_000;
const TRUST_SECRET_HEX_LENGTH=64;
const PROTOCOL_SUPPORT:SflpProtocolSupport={major:1,minMinor:0,maxMinor:0};
const WEB_CAPABILITIES=["workspace.read","workspace.write","workspace.sync","plan.read","plan.write","schedule.read","schedule.write","session.execute","session.control","session.present","question.answer","review.execute","goal.read","goal.complete","library.read","library.present","document.read","document.present","writing.read","writing.edit","analytics.read","backup.export","backup.import","device.control","device.presentation_source","text_input.provide","keyboard.input","touch.input"];
const WEB_TV_CAPABILITIES=["workspace.read","workspace.sync","schedule.read","session.control","session.present","question.answer","review.execute","goal.read","library.read","library.present","document.read","document.present","analytics.read","device.control","device.presentation_sink","dpad.input","audio.output","video.output"];

function randomHex(bytes:number):string{const buffer=new Uint8Array(bytes);globalThis.crypto?.getRandomValues?.(buffer);return [...buffer].map(x=>x.toString(16).padStart(2,"0")).join("")||Math.random().toString(16).slice(2).padEnd(bytes*2,"0").slice(0,bytes*2);}
function sixDigitCode():string{return String(Math.floor(Math.random()*900000)+100000);}
function normalizeEndpoint(uri:string):SflpEndpoint{const text=uri.trim();if(!text)throw new Error("SFLP endpoint cannot be blank");if(text.startsWith("ws://")||text.startsWith("wss://"))return{kind:"websocket",uri:text,lastSeenAtEpochMillis:Date.now()};if(text.startsWith("sflp+relay://"))return{kind:"relay",uri:text,lastSeenAtEpochMillis:Date.now()};return{kind:"manual",uri:text,lastSeenAtEpochMillis:Date.now()};}
function defaultState(kind:SflpClientKind):SflpWebPeerSnapshot{const suffix=randomHex(8);return{identity:{deviceId:`device-web-${suffix}`,displayName:kind==="web_tv"?"StudyFlow Web TV":"StudyFlow Web",clientKind:kind,protocolSupport:PROTOCOL_SUPPORT,capabilities:kind==="web_tv"?WEB_TV_CAPABILITIES:WEB_CAPABILITIES},trustedPeers:[],endpoints:{},pendingQueue:[],journal:[]};}
function safeParse(text:string|null):SflpWebPeerSnapshot|null{if(!text)return null;try{return JSON.parse(text) as SflpWebPeerSnapshot}catch{return null}}

export class StudyFlowWebPeerPersistence{
 constructor(private readonly storage:Storage=globalThis.localStorage){ }
 load(kind:SflpClientKind):SflpWebPeerSnapshot{const loaded=safeParse(this.storage.getItem(STORAGE_KEY));if(!loaded)return defaultState(kind);const refreshed={...loaded,identity:{...loaded.identity,clientKind:kind,capabilities:kind==="web_tv"?WEB_TV_CAPABILITIES:WEB_CAPABILITIES}};return refreshed;}
 save(snapshot:SflpWebPeerSnapshot):void{this.storage.setItem(STORAGE_KEY,JSON.stringify(snapshot));}
 clear():void{this.storage.removeItem(STORAGE_KEY);}
}

export class StudyFlowWebSocketTransportAdapter{
 readonly supportsListen=false;
 readonly supportsClientConnect=true;
 async open(endpoint:SflpEndpoint):Promise<WebSocket>{if(endpoint.kind!=="websocket")throw new Error("Web SFLP peers can connect only to ws/wss endpoints directly");return new Promise((resolve,reject)=>{const socket=new WebSocket(endpoint.uri);socket.onopen=()=>resolve(socket);socket.onerror=()=>reject(new Error("SFLP WebSocket connection failed"));});}
}

export class StudyFlowWebManualDiscovery{
 private endpoints:Record<string,SflpEndpoint[]>={};
 remember(peerDeviceId:string,uri:string):SflpEndpoint{const endpoint=normalizeEndpoint(uri);const existing=this.endpoints[peerDeviceId]??[];this.endpoints[peerDeviceId]=[endpoint,...existing.filter(item=>item.uri!==endpoint.uri)];return endpoint;}
 discovered(peerDeviceId:string):SflpEndpoint[]{return [...(this.endpoints[peerDeviceId]??[])];}
}

export class StudyFlowWebLinkPeer{
 private snapshot:SflpWebPeerSnapshot;
 private readonly discovery=new StudyFlowWebManualDiscovery();
 readonly transport=new StudyFlowWebSocketTransportAdapter();
 readonly presence:StudyFlowWebPresenceRuntime;
 constructor(private readonly persistence:StudyFlowWebPeerPersistence=new StudyFlowWebPeerPersistence(),kind:SflpClientKind="web"){this.snapshot=this.persistence.load(kind);this.persistence.save(this.snapshot);this.presence=new StudyFlowWebPresenceRuntime(this.identity);}
 get identity():SflpPeerDescriptor{return this.snapshot.identity;}
 get trustedPeers():SflpTrustedPeerRecord[]{return [...this.snapshot.trustedPeers];}
 get pendingOutgoingOperations():SflpQueueEntry[]{return [...this.snapshot.pendingQueue];}
 get journal():SflpJournalOperation[]{return [...this.snapshot.journal];}
 createPairingOffer(endpointHints:string[]=this.localEndpointHints()):SflpPairingOffer{const pairingCode=sixDigitCode();const expiresAtEpochMillis=Date.now()+PAIRING_TTL_MILLIS;const offer={pairingCode,expiresAtEpochMillis,descriptor:this.identity,endpointHints};const qrPayload=JSON.stringify({type:"studyflow.sflp.pairing.v1",...offer});return{...offer,qrPayload};}
 pairFromQr(qrPayload:string):SflpTrustedPeerRecord{const parsed=JSON.parse(qrPayload) as {descriptor:SflpPeerDescriptor;endpointHints?:string[]};const trustSecret=randomHex(TRUST_SECRET_HEX_LENGTH/2);const now=Date.now();const record:SflpTrustedPeerRecord={peer:parsed.descriptor,state:"trusted",pairedAtEpochMillis:now,updatedAtEpochMillis:now,lastSeenAtEpochMillis:now,revokedAtEpochMillis:null,trustSecret};this.snapshot.trustedPeers=[record,...this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==record.peer.deviceId)];for(const hint of parsed.endpointHints??[])this.rememberManualEndpoint(record.peer.deviceId,hint);this.persistence.save(this.snapshot);return record;}
 rememberManualEndpoint(peerDeviceId:string,uri:string):SflpEndpoint{const endpoint=this.discovery.remember(peerDeviceId,uri);const existing=this.snapshot.endpoints[peerDeviceId]??[];this.snapshot.endpoints[peerDeviceId]=[endpoint,...existing.filter(item=>item.uri!==endpoint.uri)];this.persistence.save(this.snapshot);return endpoint;}
 appendLocalMutation(workspaceId:string,entityId:string,operationType:string,payload:Record<string,unknown>):SflpJournalOperation{const mine=this.snapshot.journal.filter(op=>op.originDeviceId===this.identity.deviceId);const sequence=mine.reduce((max,op)=>Math.max(max,op.originSequence),0)+1;const operation:SflpJournalOperation={workspaceId,operationId:`op-${this.identity.deviceId}-${sequence}`,originDeviceId:this.identity.deviceId,originSequence:sequence,entityId,entityRevision:sequence,operationType,payload,createdAtEpochMillis:Date.now()};this.snapshot.journal=[...this.snapshot.journal,operation];for(const peer of this.snapshot.trustedPeers.filter(p=>p.state==="trusted")){this.snapshot.pendingQueue.push({peerDeviceId:peer.peer.deviceId,workspaceId,operationId:operation.operationId,attempts:0,nextAttemptEpochMillis:Date.now()});}this.persistence.save(this.snapshot);return operation;}
 applyRemoteOperations(operations:SflpJournalOperation[]):number{const seen=new Set(this.snapshot.journal.map(op=>op.operationId));let added=0;for(const operation of operations){if(seen.has(operation.operationId))continue;seen.add(operation.operationId);this.snapshot.journal.push(operation);added++;}if(added)this.persistence.save(this.snapshot);return added;}
 frontier(workspaceId:string):Record<string,number>{const out:Record<string,number>={};for(const op of this.snapshot.journal.filter(item=>item.workspaceId===workspaceId)){out[op.originDeviceId]=Math.max(out[op.originDeviceId]??0,op.originSequence);}return out;}
 operationsAfter(workspaceId:string,frontier:Record<string,number>,limit=100):SflpJournalOperation[]{return this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.originSequence>(frontier[op.originDeviceId]??0)).sort((a,b)=>a.originDeviceId.localeCompare(b.originDeviceId)||a.originSequence-b.originSequence).slice(0,limit);}
 acknowledge(peerDeviceId:string,workspaceId:string,frontier:Record<string,number>):void{this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(entry=>{if(entry.peerDeviceId!==peerDeviceId||entry.workspaceId!==workspaceId)return true;const op=this.snapshot.journal.find(item=>item.operationId===entry.operationId);return !op||op.originSequence>(frontier[op.originDeviceId]??0);});this.persistence.save(this.snapshot);}
 updatePresence(workspaceId:string,availability:StudyFlowPresenceAvailability,activityHint?:StudyFlowPresenceActivityHint,visibilityCapabilities:string[]=[]){return this.presence.announce(workspaceId,availability,activityHint,visibilityCapabilities);}
 presenceViews(workspaceId:string):StudyFlowPresenceView[]{return this.presence.views(workspaceId,this.identity);}
 presenceExchangeFor(peerDeviceId:string,workspaceId:string):StudyFlowPresenceExchange{const peer=this.snapshot.trustedPeers.find(item=>item.state==="trusted"&&item.peer.deviceId===peerDeviceId)?.peer;if(!peer)throw new Error("Presence target is not trusted");return this.presence.exchangeForPeer(workspaceId,peer);}
 acceptPresenceExchange(peerDeviceId:string,workspaceId:string,exchange:StudyFlowPresenceExchange):StudyFlowPresenceIngestResult[]{const trusted=new Set(this.snapshot.trustedPeers.filter(item=>item.state==="trusted").map(item=>item.peer.deviceId));if(!trusted.has(peerDeviceId))throw new Error("Presence sender is not trusted");return this.presence.acceptExchange(workspaceId,peerDeviceId,exchange,trusted);}
 revoke(peerDeviceId:string):SflpTrustedPeerRecord|null{this.presence.forgetPeer(peerDeviceId);const now=Date.now();let revoked:SflpTrustedPeerRecord|null=null;this.snapshot.trustedPeers=this.snapshot.trustedPeers.map(record=>{if(record.peer.deviceId!==peerDeviceId)return record;revoked={...record,state:"revoked",trustSecret:null,updatedAtEpochMillis:now,revokedAtEpochMillis:now};return revoked;});this.persistence.save(this.snapshot);return revoked;}
 forget(peerDeviceId:string):boolean{this.presence.forgetPeer(peerDeviceId);const before=this.snapshot.trustedPeers.length;this.snapshot.trustedPeers=this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==peerDeviceId);delete this.snapshot.endpoints[peerDeviceId];this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(item=>item.peerDeviceId!==peerDeviceId);this.persistence.save(this.snapshot);return before!==this.snapshot.trustedPeers.length;}
 snapshotForDebug():SflpWebPeerSnapshot{return JSON.parse(JSON.stringify(this.snapshot)) as SflpWebPeerSnapshot;}
 private localEndpointHints():string[]{return globalThis.location?.origin?.startsWith("http")?[globalThis.location.origin.replace(/^http/,"ws")+"/sflp"]:[];}
}


export type SflpActivityKind="dashboard"|"plan"|"schedule"|"study_session"|"practice_question"|"review_item"|"library_material"|"document_view"|"writing_draft"|"presentation_projection";
export type SflpActivityContinuityMode="snapshot"|"live_control"|"presentation_sink";
export interface SflpActivityCursor{positionToken:string;scrollRatio?:number;questionIndex?:number;elapsedMillis?:number;}
export interface SflpActivitySnapshot{activityId:string;workspaceId:string;kind:SflpActivityKind;entityId?:string;entityRevision?:number;cursor:SflpActivityCursor;continuityMode:SflpActivityContinuityMode;requiredCapabilities:string[];payloadHash:string;payload:Record<string,unknown>;}
export interface SflpHandoffOffer{handoffId:string;sourceDevice:SflpPeerDescriptor;targetDeviceId?:string;activity:SflpActivitySnapshot;createdAtEpochMillis:number;expiresAtEpochMillis:number;oneTimeNonceHex:string;}
export interface SflpWebTvReceiverOffer{type:"studyflow.sflp.web_tv.receiver.v1";receiver:SflpPeerDescriptor;workspaceId:string;pairingCode:string;endpointHints:string[];createdAtEpochMillis:number;expiresAtEpochMillis:number;qrPayload:string;}
export type SflpRemoteInputKind="dpad_up"|"dpad_down"|"dpad_left"|"dpad_right"|"select"|"back"|"text";
export interface SflpRemoteInputCommand{commandId:string;sourceDeviceId:string;targetDeviceId:string;kind:SflpRemoteInputKind;issuedAtEpochMillis:number;text?:string;}

export class StudyFlowWebTvConnectivityController{
 constructor(private readonly peer:StudyFlowWebLinkPeer){if(peer.identity.clientKind!=="web_tv")throw new Error("SFLP12 requires a web_tv peer");}
 createReceiverOffer(workspaceId:string,endpointHints:string[]=this.peer.createPairingOffer().endpointHints):SflpWebTvReceiverOffer{const pairing=this.peer.createPairingOffer(endpointHints);const createdAtEpochMillis=Date.now();const offer={type:"studyflow.sflp.web_tv.receiver.v1" as const,receiver:this.peer.identity,workspaceId,pairingCode:pairing.pairingCode,endpointHints,createdAtEpochMillis,expiresAtEpochMillis:pairing.expiresAtEpochMillis,qrPayload:""};offer.qrPayload=JSON.stringify({...offer,qrPayload:undefined});return offer;}
 acceptRemoteInput(command:SflpRemoteInputCommand):boolean{return command.targetDeviceId===this.peer.identity.deviceId&&["dpad_up","dpad_down","dpad_left","dpad_right","select","back","text"].includes(command.kind);}
 acceptProjection(snapshot:SflpActivitySnapshot):boolean{return snapshot.kind==="presentation_projection"&&snapshot.continuityMode==="presentation_sink"&&snapshot.requiredCapabilities.includes("device.presentation_sink")&&snapshot.requiredCapabilities.includes("dpad.input");}
}

export class StudyFlowWebActivityHandoffController{
 private readonly consumed=new Set<string>();
 constructor(private readonly peer:StudyFlowWebLinkPeer){ }
 createOffer(targetDeviceId:string|undefined,activity:SflpActivitySnapshot,ttlMillis=90000):SflpHandoffOffer{const now=Date.now();if(activity.requiredCapabilities.length===0)throw new Error("Handoff requires capability declarations");return{handoffId:`handoff-${this.peer.identity.deviceId}-${now}`,sourceDevice:this.peer.identity,targetDeviceId,activity,createdAtEpochMillis:now,expiresAtEpochMillis:now+ttlMillis,oneTimeNonceHex:randomHex(16)};}
 acceptOffer(offer:SflpHandoffOffer):boolean{if(this.consumed.has(offer.handoffId))return false;if(offer.expiresAtEpochMillis<Date.now())return false;if(offer.targetDeviceId&&offer.targetDeviceId!==this.peer.identity.deviceId)return false;const capabilities=new Set(this.peer.identity.capabilities);if(offer.activity.requiredCapabilities.some(capability=>!capabilities.has(capability)))return false;if((this.peer.identity.clientKind==="web_tv"||this.peer.identity.clientKind==="android_tv")&&offer.activity.requiredCapabilities.some(capability=>capability.endsWith(".write")||capability==="writing.edit"||capability==="library.import"))return false;this.consumed.add(offer.handoffId);return true;}
 resumePayload(offer:SflpHandoffOffer):Record<string,unknown>{return{type:"studyflow.sflp.activity_handoff.v1",handoffId:offer.handoffId,workspaceId:offer.activity.workspaceId,activityId:offer.activity.activityId,cursor:offer.activity.cursor};}
}

export function installStudyFlowWebSflpPeer(kind:SflpClientKind="web"):StudyFlowWebLinkPeer|null{if(typeof globalThis.localStorage==="undefined")return null;const peer=new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(globalThis.localStorage),kind);(globalThis as unknown as {studyFlowSflpPeer?:StudyFlowWebLinkPeer}).studyFlowSflpPeer=peer;globalThis.dispatchEvent(new CustomEvent("studyflow:sflp-peer-ready",{detail:{deviceId:peer.identity.deviceId,clientKind:peer.identity.clientKind}}));return peer;}

export interface SflpLiveSessionCursor{activityId:string;positionToken:string;itemIndex?:number;elapsedMillis:number;answeredCount:number;payloadHash?:string;}
export interface SflpLiveSessionSnapshot{sessionId:string;workspaceId:string;activityId:string;state:"running"|"paused"|"completed";activeExecutorDeviceId:string;fencingToken:string;sessionEpoch:number;lastSequence:number;cursor:SflpLiveSessionCursor;completionOperationId?:string;}
export interface SflpLiveSessionEvent{eventId:string;sessionId:string;workspaceId:string;sourceDeviceId:string;sessionEpoch:number;sequence:number;kind:"started"|"joined"|"heartbeat"|"cursor"|"paused"|"resumed"|"answer_recorded"|"completed";cursor:SflpLiveSessionCursor;completionOperationId?:string;}
export interface SflpPresentationProjectionOffer{projectionId:string;workspaceId:string;sourceDevice:SflpPeerDescriptor;targetDeviceId?:string;contentKind:string;entityId?:string;title:string;contentHash:string;requiredCapabilities:string[];createdAtEpochMillis:number;expiresAtEpochMillis:number;}
export interface SflpPresentationFrame{projectionId:string;frameId:string;workspaceId:string;sourceDeviceId:string;sequence:number;contentRevision:number;contentHash:string;cursor:{positionToken:string};payload?:Record<string,unknown>;}
export interface SflpPresentationAck{projectionId:string;sinkDeviceId:string;acknowledgedFrameId:string;acknowledgedSequence:number;contentHash:string;acknowledgedAtEpochMillis:number;}

export const SFLP14_LIVE_CURSOR_WIRE_NAME="session.live_cursor";
export const SFLP15_PROJECTION_CONTROL_WIRE_NAME="presentation.projection_control";

export class StudyFlowWebLiveSessionContinuityController{
 private applied=new Set<string>();
 constructor(private snapshot:SflpLiveSessionSnapshot){ }
 current():SflpLiveSessionSnapshot{return JSON.parse(JSON.stringify(this.snapshot));}
 applyEvent(event:SflpLiveSessionEvent):"applied"|"duplicate"|"stale_epoch"|"stale_sequence"|"already_completed"{if(this.applied.has(event.eventId))return"duplicate";if(event.sessionEpoch<this.snapshot.sessionEpoch)return"stale_epoch";if(event.sessionEpoch===this.snapshot.sessionEpoch&&event.sequence<=this.snapshot.lastSequence)return"stale_sequence";if(this.snapshot.state==="completed")return"already_completed";this.snapshot={...this.snapshot,state:event.kind==="paused"?"paused":event.kind==="completed"?"completed":"running",cursor:event.cursor,lastSequence:event.sequence,sessionEpoch:event.sessionEpoch,completionOperationId:event.completionOperationId??this.snapshot.completionOperationId};this.applied.add(event.eventId);return"applied";}
 pause(sourceDeviceId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"paused");}
 resume(sourceDeviceId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"resumed");}
 complete(sourceDeviceId:string,completionOperationId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"completed",completionOperationId);}
 private event(sourceDeviceId:string,kind:SflpLiveSessionEvent["kind"],completionOperationId?:string):SflpLiveSessionEvent{return{eventId:`live-${kind}-${Date.now()}`,sessionId:this.snapshot.sessionId,workspaceId:this.snapshot.workspaceId,sourceDeviceId,sessionEpoch:this.snapshot.sessionEpoch,sequence:this.snapshot.lastSequence+1,kind,cursor:this.snapshot.cursor,completionOperationId};}
}

export class StudyFlowWebPresentationProjectionController{
 private accepted=new Set<string>();
 private frames:Record<string,SflpPresentationFrame>={};
 constructor(private peer:StudyFlowWebLinkPeer){ }
 createProjectionOffer(workspaceId:string,title:string,contentHash:string,targetDeviceId?:string):SflpPresentationProjectionOffer{const now=Date.now();return{projectionId:`projection-${this.peer.identity.deviceId}-${now}`,workspaceId,sourceDevice:this.peer.identity,targetDeviceId,contentKind:"document",title,contentHash,requiredCapabilities:["workspace.read","device.presentation_sink","video.output"],createdAtEpochMillis:now,expiresAtEpochMillis:now+120000};}
 acceptProjectionOffer(offer:SflpPresentationProjectionOffer):boolean{if(offer.expiresAtEpochMillis<Date.now())return false;if(offer.targetDeviceId&&offer.targetDeviceId!==this.peer.identity.deviceId)return false;const caps=new Set(this.peer.identity.capabilities);if(offer.requiredCapabilities.some(capability=>!caps.has(capability)))return false;if(offer.requiredCapabilities.some(capability=>capability.endsWith(".write")||capability==="library.import"||capability==="writing.edit"))return false;this.accepted.add(offer.projectionId);return true;}
 applyProjectionFrame(frame:SflpPresentationFrame):boolean{if(!this.accepted.has(frame.projectionId))return false;const current=this.frames[frame.projectionId];if(current&&frame.sequence<=current.sequence)return false;this.frames[frame.projectionId]=frame;return true;}
 acknowledgeProjectionFrame(projectionId:string):SflpPresentationAck|null{const frame=this.frames[projectionId];if(!frame)return null;return{projectionId,sinkDeviceId:this.peer.identity.deviceId,acknowledgedFrameId:frame.frameId,acknowledgedSequence:frame.sequence,contentHash:frame.contentHash,acknowledgedAtEpochMillis:Date.now()};}
 createProjectionCommand(projectionId:string,targetDeviceId:string,kind:string):Record<string,unknown>{return{type:"presentation.projection_control",commandId:`projection-command-${Date.now()}`,projectionId,sourceDeviceId:this.peer.identity.deviceId,targetDeviceId,kind,issuedAtEpochMillis:Date.now()};}
}



export type StudyFlowControlCommandStatus = 'accepted' | 'rejected' | 'duplicate'

export interface StudyFlowControlCommandEnvelope {
  messageId: string
  sessionId: string
  workspaceId: string
  sourceDeviceId: string
  targetDeviceId: string
  leaseToken: string
  sequence: number
  kind: string
  payload?: Record<string, unknown>
}

export interface StudyFlowInputDelegationEnvelope {
  delegationId: string
  workspaceId: string
  sourceDeviceId: string
  targetDeviceId: string
  acceptedInputCapabilities: string[]
  oneTimeNonceHex: string
}

export class StudyFlowControlInputRuntime {
  private readonly leases = new Map<string, { leaseToken: string; targetDeviceId: string }>()
  private readonly inputDelegations = new Map<string, StudyFlowInputDelegationEnvelope>()
  private readonly lastControlSequence = new Map<string, number>()
  private readonly lastInputSequence = new Map<string, number>()

  constructor(private readonly peer: { deviceId: string }) {}

  rememberControlLease(lease: { sessionId: string; leaseToken: string; targetDeviceId: string }) {
    this.leases.set(lease.sessionId, lease)
    return lease
  }

  applyControlCommand(command: StudyFlowControlCommandEnvelope) {
    const lease = this.leases.get(command.sessionId)
    if (!lease) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'unknown_lease' }
    if (command.targetDeviceId !== this.peer.deviceId) {
      return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'wrong_target' }
    }
    if (command.leaseToken !== lease.leaseToken) {
      return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'bad_lease_token' }
    }
    const last = this.lastControlSequence.get(command.sessionId) ?? 0
    if (command.sequence <= last) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'stale_sequence' }
    this.lastControlSequence.set(command.sessionId, command.sequence)
    return { status: 'accepted' as StudyFlowControlCommandStatus, acknowledgedSequence: command.sequence }
  }

  acceptInputDelegation(offer: StudyFlowInputDelegationEnvelope) {
    if (offer.targetDeviceId !== this.peer.deviceId) return { status: 'rejected', reason: 'wrong_target' }
    this.inputDelegations.set(offer.delegationId, offer)
    return { status: 'accepted', delegationId: offer.delegationId }
  }

  applyInputEvent(event: { delegationId: string; sequence: number; targetDeviceId: string }) {
    if (!this.inputDelegations.has(event.delegationId)) return { status: 'rejected', reason: 'unknown_delegation' }
    if (event.targetDeviceId !== this.peer.deviceId) return { status: 'rejected', reason: 'wrong_target' }
    const last = this.lastInputSequence.get(event.delegationId) ?? 0
    if (event.sequence <= last) return { status: 'rejected', reason: 'stale_sequence' }
    this.lastInputSequence.set(event.delegationId, event.sequence)
    return { status: 'accepted', acknowledgedSequence: event.sequence }
  }
}

export type StudyFlowNotificationDeliveryStatus = 'accepted' | 'rejected' | 'duplicate' | 'expired'
export type StudyFlowAttentionCategory =
  | 'planned_study'
  | 'due_review'
  | 'missed_activity'
  | 'daily_summary'
  | 'session_ending'
  | 'session_completed'
  | 'exam_countdown'
  | 'schedule_change'
  | 'plan_warning'
  | 'sync_attention'
export type StudyFlowAttentionPriority = 'low' | 'normal' | 'high' | 'time_sensitive'
export type StudyFlowAttentionActionKind =
  | 'open'
  | 'start_now'
  | 'review_now'
  | 'snooze'
  | 'reschedule'
  | 'mark_done'
  | 'dismiss'
  | 'continue'
  | 'move_to_device'
export type StudyFlowAttentionDeliveryPolicy =
  | 'local_only'
  | 'primary_device'
  | 'active_then_primary'
  | 'phone_and_desktop'
  | 'all_capable'
export type StudyFlowAttentionDeliveryState =
  | 'planned'
  | 'scheduled'
  | 'delivery_pending'
  | 'delivered'
  | 'acknowledged'
  | 'started'
  | 'completed'
  | 'snoozed'
  | 'rescheduled'
  | 'dismissed'
  | 'missed'
  | 'expired'
  | 'cancelled'

export interface StudyFlowAttentionOccurrence {
  occurrenceId: string
  workspaceId: string
  scheduleEntityId?: string
  category: StudyFlowAttentionCategory
  priority?: StudyFlowAttentionPriority
  scheduledAtEpochMillis: number
  originalScheduledAtEpochMillis?: number
  expiresAtEpochMillis?: number
  timezoneId?: string
  quietHours?: { startMinute: number; endMinute: number }
  deliveryPolicy?: StudyFlowAttentionDeliveryPolicy
  actions?: Array<{ kind: StudyFlowAttentionActionKind; payload?: Record<string, unknown> }>
  sourceDeviceId?: string
  targetDeviceIds?: string[]
  payload?: Record<string, unknown>
}

export interface StudyFlowAttentionLedgerEntry {
  occurrence: StudyFlowAttentionOccurrence
  state: StudyFlowAttentionDeliveryState
  updatedAtEpochMillis: number
  deliveredAtEpochMillis?: number
  acknowledgedAtEpochMillis?: number
  sourceDeviceId?: string
  targetDeviceId?: string
  sequence?: number
  deliveryAttempts: number
  policyAudit?: {
    profile: string
    outcome: string
    evaluatedAtEpochMillis: number
    deferredUntilEpochMillis?: number
    explanation: string
  }
}

export const StudyFlowAttentionLedgerSnapshotSchemaVersion = 1 as const

export interface StudyFlowAttentionLedgerSnapshot {
  schemaVersion: number
  entries: StudyFlowAttentionLedgerEntry[]
  lastSequenceBySource: Record<string, number>
  legacyDeliveredOccurrenceIds: string[]
}

export interface StudyFlowAttentionLedgerStorage {
  readEncodedSnapshot(): string | null
  writeEncodedSnapshot(encoded: string): void
}

export class StudyFlowMemoryAttentionLedgerStorage implements StudyFlowAttentionLedgerStorage {
  private encoded: string | null
  constructor(initialEncodedSnapshot: string | null = null) { this.encoded = initialEncodedSnapshot }
  readEncodedSnapshot(): string | null { return this.encoded }
  writeEncodedSnapshot(encoded: string): void { this.encoded = encoded }
}

export class StudyFlowBrowserAttentionLedgerStorage implements StudyFlowAttentionLedgerStorage {
  private readonly fallback = new StudyFlowMemoryAttentionLedgerStorage()
  constructor(private readonly key: string) {}
  readEncodedSnapshot(): string | null {
    try { return globalThis.localStorage?.getItem(this.key) ?? this.fallback.readEncodedSnapshot() }
    catch { return this.fallback.readEncodedSnapshot() }
  }
  writeEncodedSnapshot(encoded: string): void {
    try {
      if (globalThis.localStorage) globalThis.localStorage.setItem(this.key, encoded)
      else this.fallback.writeEncodedSnapshot(encoded)
    } catch { this.fallback.writeEncodedSnapshot(encoded) }
  }
}

export interface StudyFlowNotificationDeliveryEnvelope {
  messageId: string
  sourceDeviceId: string
  targetDeviceId: string
  sequence: number
  notification: {
    notificationId: string
    kind: string
    title: string
    body: string
    route?: { workspaceId: string; targetDeviceId?: string }
    scheduleActivityId?: string
    dueAtEpochMillis?: number
    createdAtEpochMillis?: number
    expiresAtEpochMillis: number
    attentionOccurrence?: StudyFlowAttentionOccurrence
    payload?: Record<string, unknown>
  }
}

export interface StudyFlowScheduleReminderChangeEnvelope {
  changeId: string
  workspaceId: string
  sourceDeviceId: string
  sequence: number
  scheduleEntityId: string
  changeKind: string
  reminderAtEpochMillis?: number
  quietHoursStartMinute?: number
  quietHoursEndMinute?: number
  occurredAtEpochMillis?: number
  attentionOccurrence?: StudyFlowAttentionOccurrence
  payload?: Record<string, unknown>
}

export interface StudyFlowMaterialStreamOfferEnvelope {
  streamId: string
  workspaceId: string
  materialId: string
  sourceDeviceId: string
  targetDeviceId?: string
  title: string
  mimeType: string
  totalBytes: number
  chunkSize: number
  contentSha256: string
  requiredCapabilities: string[]
  expiresAtEpochMillis: number
}

function emptyAttentionLedger(): StudyFlowAttentionLedgerSnapshot {
  return { schemaVersion: 1, entries: [], lastSequenceBySource: {}, legacyDeliveredOccurrenceIds: [] }
}

function validAttentionLedger(value: unknown): value is StudyFlowAttentionLedgerSnapshot {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<StudyFlowAttentionLedgerSnapshot>
  return candidate.schemaVersion === 1 && Array.isArray(candidate.entries) &&
    !!candidate.lastSequenceBySource && typeof candidate.lastSequenceBySource === 'object' &&
    Array.isArray(candidate.legacyDeliveredOccurrenceIds ?? [])
}

function stableAttentionOccurrenceId(parts: string[]): string {
  const readable = parts.join(':').replace(/[^A-Za-z0-9._:-]/g, '-')
  if (readable.length > 0 && readable.length <= 128) return readable
  let hash = 2166136261
  for (const char of parts.join('\u001f')) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return `attn-${(hash >>> 0).toString(16).padStart(8, '0')}`
}

export class StudyFlowNotificationMediaRuntime {
  private attentionLedger: StudyFlowAttentionLedgerSnapshot
  private readonly materialStreams = new Map<string, StudyFlowMaterialStreamOfferEnvelope>()
  private readonly materialChunks = new Map<string, Set<number>>()

  constructor(
    private readonly peer: { deviceId: string; capabilities: string[] },
    private readonly attentionStorage: StudyFlowAttentionLedgerStorage =
      new StudyFlowBrowserAttentionLedgerStorage(`studyflow.attention.v1.${peer.deviceId}`),
  ) {
    this.attentionLedger = this.loadAttentionLedger()
  }

  applyNotificationDelivery(delivery: StudyFlowNotificationDeliveryEnvelope) {
    if (delivery.targetDeviceId !== this.peer.deviceId) return { status: 'rejected' as StudyFlowNotificationDeliveryStatus, reason: 'wrong_target' }
    if (delivery.notification.expiresAtEpochMillis < Date.now()) return { status: 'expired' as StudyFlowNotificationDeliveryStatus, reason: 'expired' }
    const occurrence = delivery.notification.attentionOccurrence ?? this.legacyNotificationOccurrence(delivery)
    const existing = this.attentionLedger.entries.find(entry => entry.occurrence.occurrenceId === occurrence.occurrenceId)
    if (existing && !['planned', 'scheduled', 'delivery_pending'].includes(existing.state)) {
      return { status: 'duplicate' as StudyFlowNotificationDeliveryStatus, acknowledgedSequence: delivery.sequence }
    }
    if (this.attentionLedger.legacyDeliveredOccurrenceIds.includes(occurrence.occurrenceId)) {
      return { status: 'duplicate' as StudyFlowNotificationDeliveryStatus, acknowledgedSequence: delivery.sequence }
    }
    const last = this.attentionLedger.lastSequenceBySource[delivery.sourceDeviceId] ?? 0
    if (delivery.sequence <= last) return { status: 'rejected' as StudyFlowNotificationDeliveryStatus, reason: 'stale_sequence' }
    const now = Date.now()
    const entry: StudyFlowAttentionLedgerEntry = {
      occurrence,
      state: 'delivered',
      updatedAtEpochMillis: now,
      deliveredAtEpochMillis: now,
      sourceDeviceId: delivery.sourceDeviceId,
      targetDeviceId: delivery.targetDeviceId,
      sequence: delivery.sequence,
      deliveryAttempts: (existing?.deliveryAttempts ?? 0) + 1,
    }
    this.attentionLedger = {
      ...this.attentionLedger,
      entries: [...this.attentionLedger.entries.filter(value => value.occurrence.occurrenceId !== occurrence.occurrenceId), entry].slice(-512),
      lastSequenceBySource: { ...this.attentionLedger.lastSequenceBySource, [delivery.sourceDeviceId]: delivery.sequence },
    }
    this.persistAttentionLedger()
    return { status: 'accepted' as StudyFlowNotificationDeliveryStatus, acknowledgedSequence: delivery.sequence }
  }

  scheduleChangeOperation(change: StudyFlowScheduleReminderChangeEnvelope) {
    const operationType = change.changeKind === 'reminder_cancelled'
      ? 'schedule.reminder_cancelled'
      : change.changeKind === 'activity_moved'
        ? 'schedule.activity_moved'
        : change.changeKind === 'quiet_hours_changed'
          ? 'notification.quiet_hours_changed'
          : change.changeKind === 'notification_policy_changed'
            ? 'notification.policy_changed'
            : 'schedule.reminder_upserted'
    return {
      operationId: `op-${change.changeId}`,
      workspaceId: change.workspaceId,
      originDeviceId: change.sourceDeviceId,
      originSequence: change.sequence,
      entityId: change.scheduleEntityId,
      operationType,
      payload: {
        notificationScheduleSync: 'sflp18',
        attentionSchema: 'v1',
        changeKind: change.changeKind,
        scheduleSyncId: change.changeId,
        attentionScheduleChange: { ...change },
      },
    }
  }

  canonicalScheduleOccurrence(change: StudyFlowScheduleReminderChangeEnvelope): StudyFlowAttentionOccurrence | null {
    if (change.attentionOccurrence) return change.attentionOccurrence
    if (change.reminderAtEpochMillis === undefined) return null
    return {
      occurrenceId: stableAttentionOccurrenceId(['schedule', change.scheduleEntityId, String(change.reminderAtEpochMillis)]),
      workspaceId: change.workspaceId,
      scheduleEntityId: change.scheduleEntityId,
      category: 'planned_study',
      priority: 'normal',
      scheduledAtEpochMillis: change.reminderAtEpochMillis,
      originalScheduledAtEpochMillis: change.reminderAtEpochMillis,
      quietHours: change.quietHoursStartMinute !== undefined && change.quietHoursEndMinute !== undefined
        ? { startMinute: change.quietHoursStartMinute, endMinute: change.quietHoursEndMinute }
        : undefined,
      deliveryPolicy: 'active_then_primary',
      sourceDeviceId: change.sourceDeviceId,
      payload: change.payload,
    }
  }

  attentionDiagnostics() {
    const terminal = new Set<StudyFlowAttentionDeliveryState>(['completed', 'dismissed', 'expired', 'cancelled'])
    const terminalCount = this.attentionLedger.entries.filter(entry => terminal.has(entry.state)).length
    return {
      schemaVersion: this.attentionLedger.schemaVersion,
      entryCount: this.attentionLedger.entries.length,
      activeCount: this.attentionLedger.entries.length - terminalCount,
      terminalCount,
      sourceSequenceCount: Object.keys(this.attentionLedger.lastSequenceBySource).length,
      legacyDeliveredAliasCount: this.attentionLedger.legacyDeliveredOccurrenceIds.length,
      lastUpdatedAtEpochMillis: this.attentionLedger.entries.reduce<number | null>(
        (latest, entry) => latest === null ? entry.updatedAtEpochMillis : Math.max(latest, entry.updatedAtEpochMillis),
        null,
      ),
    }
  }

  acceptMaterialStream(offer: StudyFlowMaterialStreamOfferEnvelope): boolean {
    if (offer.expiresAtEpochMillis < Date.now()) return false
    if (offer.targetDeviceId && offer.targetDeviceId !== this.peer.deviceId) return false
    const capabilities = new Set(this.peer.capabilities)
    if (offer.requiredCapabilities.some(capability => capability.endsWith('.write') || capability === 'library.import')) return false
    if (offer.requiredCapabilities.some(capability => !capabilities.has(capability))) return false
    this.materialStreams.set(offer.streamId, offer)
    this.materialChunks.set(offer.streamId, new Set())
    return true
  }

  applyMaterialChunk(chunk: { streamId: string; targetDeviceId: string; chunkIndex: number; byteOffset: number; finalChunk?: boolean }) {
    const offer = this.materialStreams.get(chunk.streamId)
    if (!offer) return { status: 'rejected', reason: 'unknown_stream' }
    if (chunk.targetDeviceId !== this.peer.deviceId) return { status: 'rejected', reason: 'wrong_target' }
    if (chunk.byteOffset !== chunk.chunkIndex * offer.chunkSize) return { status: 'rejected', reason: 'unexpected_offset' }
    const chunks = this.materialChunks.get(chunk.streamId) ?? new Set<number>()
    if (chunks.has(chunk.chunkIndex)) return { status: 'duplicate', acknowledgedChunkIndex: chunk.chunkIndex }
    chunks.add(chunk.chunkIndex)
    this.materialChunks.set(chunk.streamId, chunks)
    return { status: chunk.finalChunk ? 'complete' : 'accepted', acknowledgedChunkIndex: chunk.chunkIndex }
  }

  private legacyNotificationOccurrence(delivery: StudyFlowNotificationDeliveryEnvelope): StudyFlowAttentionOccurrence {
    const workspaceId = delivery.notification.route?.workspaceId ?? 'workspace-main'
    const category: StudyFlowAttentionCategory = delivery.notification.kind === 'review_due'
      ? 'due_review'
      : delivery.notification.kind === 'study_due'
        ? 'planned_study'
        : delivery.notification.kind === 'session_resume'
          ? 'session_ending'
          : 'sync_attention'
    return {
      occurrenceId: stableAttentionOccurrenceId(['notification', delivery.notification.notificationId]),
      workspaceId,
      scheduleEntityId: delivery.notification.scheduleActivityId,
      category,
      priority: 'normal',
      scheduledAtEpochMillis: delivery.notification.dueAtEpochMillis ?? delivery.notification.createdAtEpochMillis ?? Date.now(),
      originalScheduledAtEpochMillis: delivery.notification.dueAtEpochMillis ?? delivery.notification.createdAtEpochMillis ?? Date.now(),
      expiresAtEpochMillis: delivery.notification.expiresAtEpochMillis,
      deliveryPolicy: 'active_then_primary',
      sourceDeviceId: delivery.sourceDeviceId,
      targetDeviceIds: [delivery.targetDeviceId],
      payload: delivery.notification.payload,
    }
  }

  private loadAttentionLedger(): StudyFlowAttentionLedgerSnapshot {
    const encoded = this.attentionStorage.readEncodedSnapshot()
    if (!encoded) return emptyAttentionLedger()
    try {
      const decoded: unknown = JSON.parse(encoded)
      return validAttentionLedger(decoded) ? decoded : emptyAttentionLedger()
    } catch { return emptyAttentionLedger() }
  }

  private persistAttentionLedger(): void {
    this.attentionStorage.writeEncodedSnapshot(JSON.stringify(this.attentionLedger))
  }
}

// SFLP20/SFLP21 clipboard/share/import bridge and conflict-review UX declarations.
export type StudyFlowShareImportIntent = 'clipboard_preview' | 'clipboard_apply' | 'share_preview' | 'share_import'
export type StudyFlowConflictResolutionKind = 'keep_local' | 'take_remote' | 'merge' | 'skip'

export interface StudyFlowShareImportOffer {
  importId: string
  workspaceId: string
  sourceDevice: unknown
  targetDeviceId?: string
  intent: StudyFlowShareImportIntent
  payloadKind: 'plain_text' | 'markdown' | 'url' | 'html' | 'json' | 'csv' | 'pdf' | 'image' | 'opaque'
  title: string
  mimeType?: string
  previewText?: string
  contentSha256: string
  requiredCapabilities: string[]
  createdAtEpochMillis: number
  expiresAtEpochMillis: number
}

export interface StudyFlowConflictReviewModel {
  reviewId: string
  workspaceId: string
  targetDeviceId?: string
  requiredCapabilities: string[]
  candidate: { entityId: string; localRevision: number; remoteRevision: number; fieldKinds: string[] }
}

export class StudyFlowShareImportConflictRuntime {
  private imports = new Map<string, StudyFlowShareImportOffer>()
  private conflictReviews = new Map<string, StudyFlowConflictReviewModel>()
  constructor(private readonly peer: { deviceId?: string; identity?: { deviceId: string; clientKind: string; capabilities: string[] }; capabilities?: string[]; clientKind?: string }) {}
  applyShareImportDelivery(delivery: { sourceDeviceId: string; targetDeviceId: string; sequence: number; offer: StudyFlowShareImportOffer }) {
    const localId = this.peer.deviceId ?? this.peer.identity?.deviceId
    if (delivery.targetDeviceId !== localId) return { status: 'rejected', reason: 'wrong_target' }
    if (this.imports.has(delivery.offer.importId)) return { status: 'duplicate' }
    this.imports.set(delivery.offer.importId, delivery.offer)
    return { status: 'accepted' }
  }
  openConflictReview(review: StudyFlowConflictReviewModel): boolean {
    this.conflictReviews.set(review.reviewId, review)
    return true
  }
  resolveConflict(reviewId: string, decision: StudyFlowConflictResolutionKind) {
    if (!this.conflictReviews.has(reviewId)) return { status: 'rejected', reason: 'unknown_review' }
    this.conflictReviews.delete(reviewId)
    return { status: 'resolved', decision }
  }
}

// SFLP22/SFLP23 account/backup handoff and release validation declarations.
export type StudyFlowAccountHandoffPurpose = 'device_replacement' | 'desktop_recovery' | 'backup_restore' | 'audit'
export type StudyFlowBackupRestoreMode = 'dry_run' | 'restore' | 'verify_only'
export type StudyFlowReleaseValidationStatus = 'planned' | 'passed' | 'failed' | 'skipped'

export interface StudyFlowAccountHandoffOfferEnvelope {
  handoffId: string
  workspaceId: string
  targetDeviceId?: string
  purpose: StudyFlowAccountHandoffPurpose
  requiredCapabilities: string[]
  createdAtEpochMillis: number
  expiresAtEpochMillis: number
}

export interface StudyFlowBackupBundleManifestEnvelope {
  bundleId: string
  workspaceId: string
  targetDeviceId?: string
  totalBytes: number
  itemCount: number
  contentSha256: string
  requiredRestoreCapabilities: string[]
  createdAtEpochMillis: number
  expiresAtEpochMillis?: number
}

export class StudyFlowAccountBackupReleaseRuntime {
  private readonly handoffs = new Set<string>()
  private readonly restores = new Set<string>()
  constructor(private readonly peer: { deviceId?: string; identity?: { deviceId: string; clientKind: string; capabilities: string[] }; capabilities?: string[]; clientKind?: string }) {}
  localDeviceId(): string | undefined { return this.peer.deviceId ?? this.peer.identity?.deviceId }
  localCapabilities(): Set<string> { return new Set(this.peer.capabilities ?? this.peer.identity?.capabilities ?? []) }
  localClientKind(): string | undefined { return this.peer.clientKind ?? this.peer.identity?.clientKind }
  applyAccountHandoff(delivery: { targetDeviceId: string; sequence: number; offer: StudyFlowAccountHandoffOfferEnvelope }) {
    if (delivery.targetDeviceId !== this.localDeviceId()) return { status: 'rejected', reason: 'wrong_target' }
    if (delivery.offer.expiresAtEpochMillis < Date.now()) return { status: 'expired', reason: 'expired' }
    if (this.handoffs.has(delivery.offer.handoffId)) return { status: 'duplicate', acknowledgedSequence: delivery.sequence }
    const caps = this.localCapabilities()
    if (delivery.offer.requiredCapabilities.some(capability => !caps.has(capability))) return { status: 'rejected', reason: 'missing_capability' }
    const kind = this.localClientKind()
    if ((kind === 'android_tv' || kind === 'web_tv') && delivery.offer.purpose !== 'audit') return { status: 'rejected', reason: 'tv_account_handoff_forbidden' }
    this.handoffs.add(delivery.offer.handoffId)
    return { status: 'accepted', handoffId: delivery.offer.handoffId, acknowledgedSequence: delivery.sequence }
  }
  acceptBackupRestore(plan: { restoreId: string; targetDeviceId: string; mode: StudyFlowBackupRestoreMode; sequence: number; bundle: StudyFlowBackupBundleManifestEnvelope }) {
    if (plan.targetDeviceId !== this.localDeviceId()) return { status: 'rejected', reason: 'wrong_target' }
    if (plan.bundle.expiresAtEpochMillis && plan.bundle.expiresAtEpochMillis < Date.now()) return { status: 'expired', reason: 'expired' }
    if (this.restores.has(plan.restoreId)) return { status: 'duplicate', acknowledgedSequence: plan.sequence }
    const kind = this.localClientKind()
    if ((kind === 'android_tv' || kind === 'web_tv') && plan.mode !== 'verify_only') return { status: 'rejected', reason: 'tv_restore_forbidden' }
    const caps = this.localCapabilities()
    if (plan.bundle.requiredRestoreCapabilities.some(capability => !caps.has(capability))) return { status: 'rejected', reason: 'missing_capability' }
    this.restores.add(plan.restoreId)
    return { status: 'accepted', restoreId: plan.restoreId, acknowledgedSequence: plan.sequence }
  }
  finalReleaseValidationTaskNames(): string[] {
    return [
      'verifySflp22Sflp23AccountBackupValidation', 'verifySflp20Sflp21ShareImportConflict',
      'verifySflp18Sflp19NotificationMediaSync', 'verifySflp16Sflp17ControlInputDelegation',
      'verifySflp14Sflp15LiveSessionProjection', 'verifySflp12Sflp13WebTvHandoff',
      'verifySflp10Sflp11WebTvGeneralization', 'verifySflp08Sflp09AndroidDesktopIntegration',
      'verifySflp06Sflp07TopologyOfflineQueue', 'verifySflp04Sflp05IdentityTransport',
      'verifySflp02Sflp03JournalReconciliation', 'verifySflp01ProtocolBaseline',
      ':core:link:test', ':app-tv:testDebugUnitTest', ':desktopApp:test', ':app:testReleaseUnitTest',
    ]
  }
}
