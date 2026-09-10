"use strict";

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
 constructor(private readonly persistence:StudyFlowWebPeerPersistence=new StudyFlowWebPeerPersistence(),kind:SflpClientKind="web"){this.snapshot=this.persistence.load(kind);this.persistence.save(this.snapshot);}
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
 revoke(peerDeviceId:string):SflpTrustedPeerRecord|null{const now=Date.now();let revoked:SflpTrustedPeerRecord|null=null;this.snapshot.trustedPeers=this.snapshot.trustedPeers.map(record=>{if(record.peer.deviceId!==peerDeviceId)return record;revoked={...record,state:"revoked",trustSecret:null,updatedAtEpochMillis:now,revokedAtEpochMillis:now};return revoked;});this.persistence.save(this.snapshot);return revoked;}
 forget(peerDeviceId:string):boolean{const before=this.snapshot.trustedPeers.length;this.snapshot.trustedPeers=this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==peerDeviceId);delete this.snapshot.endpoints[peerDeviceId];this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(item=>item.peerDeviceId!==peerDeviceId);this.persistence.save(this.snapshot);return before!==this.snapshot.trustedPeers.length;}
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

