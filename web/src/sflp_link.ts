"use strict";

import { StudyFlowWebPresenceRuntime, type StudyFlowPresenceActivityHint, type StudyFlowPresenceAvailability, type StudyFlowPresenceExchange, type StudyFlowPresenceIngestResult, type StudyFlowPresenceView } from "./presence.js";

export type SflpClientKind="android"|"desktop"|"web"|"android_tv"|"web_tv";
export type SflpRouteKind="websocket"|"manual"|"relay";
export type SflpTrustState="trusted"|"revoked";
export type SflpOperationKind="mutation"|"tombstone";
export type SflpMessageType="request"|"response"|"event"|"error";
export interface SflpProtocolSupport{major:number;minMinor:number;maxMinor:number;}
export interface SflpProtocolVersion{major:number;minor:number;}
export interface SflpPeerDescriptor{deviceId:string;displayName:string;clientKind:SflpClientKind;protocolSupport:SflpProtocolSupport;capabilities:string[];}
export interface SflpTrustedPeerRecord{peer:SflpPeerDescriptor;state:SflpTrustState;pairedAtEpochMillis:number;updatedAtEpochMillis:number;lastSeenAtEpochMillis:number|null;revokedAtEpochMillis:number|null;trustKeyId:string|null;}
export interface SflpEndpoint{kind:SflpRouteKind;uri:string;lastSeenAtEpochMillis:number;}
export interface SflpJournalOperation{workspaceId:string;operationId:string;originDeviceId:string;originSequence:number;entityId:string;entityRevision:number;logicalTimestamp:number;operationType:string;kind:SflpOperationKind;occurredAtEpochMillis:number;payload:Record<string,unknown>;}
export interface SflpOriginRevision{deviceId:string;contiguousSequence:number;}
export interface SflpJournalFrontier{origins:SflpOriginRevision[];}
export interface SflpQueueEntry{peerDeviceId:string;workspaceId:string;operationId:string;attempts:number;nextAttemptEpochMillis:number;lastAttemptEpochMillis?:number;lastFailure?:string;}
export interface SflpPeerRetryState{peerDeviceId:string;workspaceId:string;attempts:number;lastAttemptEpochMillis:number;nextAttemptEpochMillis:number;lastFailure:string;}
export interface SflpPersistenceCorruptionRecord{kind:SflpClientKind;observedAtEpochMillis:number;storageKey:string;quarantineReference:string;reason:string;}
export interface SflpWebPeerSnapshot{identity:SflpPeerDescriptor;trustedPeers:SflpTrustedPeerRecord[];endpoints:Record<string,SflpEndpoint[]>;pendingQueue:SflpQueueEntry[];journal:SflpJournalOperation[];peerRetryStates:Record<string,SflpPeerRetryState>;verifiedRemoteFrontiers:Record<string,Record<string,number>>;}
export interface SflpPairingOffer{pairingCode:string;expiresAtEpochMillis:number;descriptor:SflpPeerDescriptor;endpointHints:string[];qrPayload:string;}
export interface SflpPairingQrPayload{version:2;pairingId:string;host:SflpPeerDescriptor;sessionNonce:string;pairingCode:string;createdAtEpochMillis:number;expiresAtEpochMillis:number;endpointHints:string[];hostKeyAgreementPublicKeyHex:string;}
export interface SflpLinkEnvelope{protocol:SflpProtocolVersion;messageId:string;workspaceId:string;senderDeviceId:string;type:SflpMessageType;name:string;sentAtEpochMillis:number;correlationId?:string;payload:Record<string,unknown>;}
export interface SflpRoutedRpcAccepted{requestMessageId:string;requestName:string;receivingDeviceId:string;acceptedAtEpochMillis:number;}

const WEB_STORAGE_KEY="studyflow.sflp.web.peer.v1";
const WEB_TV_STORAGE_KEY="studyflow.sflp.web_tv.peer.v1";
const PAIRING_SCHEME_PREFIX="studyflow://pair?data=";
const LINK_SYSTEM_WORKSPACE_ID="link-system-v1";
const PAIRING_TTL_MILLIS=120_000;
const PROTOCOL_SUPPORT:SflpProtocolSupport={major:1,minMinor:0,maxMinor:0};
const PROTOCOL_VERSION:SflpProtocolVersion={major:1,minor:0};
const WEB_CAPABILITIES=["workspace.read","workspace.write","workspace.sync","plan.read","plan.write","schedule.read","schedule.write","session.execute","session.control","session.present","question.answer","review.execute","goal.read","goal.complete","library.read","library.present","document.read","document.present","writing.read","writing.edit","analytics.read","backup.export","backup.import","device.control","device.presentation_source","text_input.provide","keyboard.input","touch.input","clipboard.read","clipboard.write","share.receive","import.preview","conflict.review"];
const WEB_TV_CAPABILITIES=["workspace.read","workspace.sync","schedule.read","session.control","session.present","question.answer","review.execute","goal.read","library.read","library.present","document.read","document.present","analytics.read","device.control","device.presentation_sink","dpad.input","audio.output","video.output","clipboard.read","share.receive","import.preview","conflict.review"];
const CLIENT_KINDS=new Set<SflpClientKind>(["android","desktop","web","android_tv","web_tv"]);

function requireSecureRandom():Crypto{const crypto=globalThis.crypto;if(!crypto?.getRandomValues)throw new Error("Secure Web Crypto randomness is required for StudyFlow Link");return crypto;}
function requireSubtleCrypto():SubtleCrypto{const subtle=requireSecureRandom().subtle;if(!subtle)throw new Error("Web Crypto SubtleCrypto is required for StudyFlow Link trust");return subtle;}
function randomHex(bytes:number):string{const buffer=new Uint8Array(bytes);requireSecureRandom().getRandomValues(buffer);return [...buffer].map(x=>x.toString(16).padStart(2,"0")).join("");}
function sixDigitCode():string{const value=new Uint32Array(1);requireSecureRandom().getRandomValues(value);return String(value[0]%1_000_000).padStart(6,"0");}
function messageId(prefix:string):string{return `${prefix}-${Date.now()}-${randomHex(6)}`;}
function storageKey(kind:SflpClientKind):string{return kind==="web_tv"?WEB_TV_STORAGE_KEY:WEB_STORAGE_KEY;}
function normalizeEndpoint(uri:string):SflpEndpoint{const text=uri.trim();if(!text)throw new Error("SFLP endpoint cannot be blank");if(text.startsWith("ws://")||text.startsWith("wss://"))return{kind:"websocket",uri:text,lastSeenAtEpochMillis:Date.now()};if(text.startsWith("sflp+relay://"))return{kind:"relay",uri:text,lastSeenAtEpochMillis:Date.now()};if(text.startsWith("sflp+tcp://"))return{kind:"manual",uri:text,lastSeenAtEpochMillis:Date.now()};throw new Error("Unsupported SFLP endpoint scheme");}
function capabilitiesFor(kind:SflpClientKind):string[]{return kind==="web_tv"?[...WEB_TV_CAPABILITIES]:[...WEB_CAPABILITIES];}
function defaultState(kind:SflpClientKind):SflpWebPeerSnapshot{if(kind!=="web"&&kind!=="web_tv")throw new Error("Browser persistence supports web or web_tv identities only");const suffix=randomHex(8);const prefix=kind==="web_tv"?"device-web-tv":"device-web";return{identity:{deviceId:`${prefix}-${suffix}`,displayName:kind==="web_tv"?"StudyFlow Web TV":"StudyFlow Web",clientKind:kind,protocolSupport:PROTOCOL_SUPPORT,capabilities:capabilitiesFor(kind)},trustedPeers:[],endpoints:{},pendingQueue:[],journal:[],peerRetryStates:{},verifiedRemoteFrontiers:{}};}
function hexBytes(value:string):Uint8Array{if(value.length%2!==0||!/^[a-f0-9]+$/.test(value))throw new Error("Expected lower-case hex");const out=new Uint8Array(value.length/2);for(let i=0;i<out.length;i++)out[i]=Number.parseInt(value.slice(i*2,i*2+2),16);return out;}
function bytesHex(value:ArrayBuffer|ArrayBufferView):string{const bytes=value instanceof ArrayBuffer?new Uint8Array(value):new Uint8Array(value.buffer,value.byteOffset,value.byteLength);return [...bytes].map(x=>x.toString(16).padStart(2,"0")).join("");}
function utf8(value:string):Uint8Array{return new TextEncoder().encode(value);}
function concatBytes(...parts:Uint8Array[]):Uint8Array{const size=parts.reduce((sum,p)=>sum+p.length,0);const out=new Uint8Array(size);let offset=0;for(const p of parts){out.set(p,offset);offset+=p.length;}return out;}
function isProtocolSupported(version:SflpProtocolVersion):boolean{return version.major===PROTOCOL_SUPPORT.major&&version.minor>=PROTOCOL_SUPPORT.minMinor&&version.minor<=PROTOCOL_SUPPORT.maxMinor;}
function validateDescriptor(value:unknown):SflpPeerDescriptor{if(!value||typeof value!=="object")throw new Error("Pairing host descriptor is missing");const d=value as Partial<SflpPeerDescriptor>;if(typeof d.deviceId!=="string"||!/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(d.deviceId))throw new Error("Invalid pairing host device ID");if(typeof d.displayName!=="string"||!d.displayName.trim()||d.displayName.length>120)throw new Error("Invalid pairing host display name");if(!CLIENT_KINDS.has(d.clientKind as SflpClientKind))throw new Error("Invalid pairing host client kind");if(!d.protocolSupport||d.protocolSupport.major!==1||d.protocolSupport.minMinor>0||d.protocolSupport.maxMinor<0)throw new Error("Pairing host has incompatible protocol support");if(!Array.isArray(d.capabilities)||d.capabilities.some(cap=>typeof cap!=="string"||!/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(cap)))throw new Error("Invalid pairing host capabilities");return d as SflpPeerDescriptor;}
function encodePairingQr(payload:SflpPairingQrPayload):string{return PAIRING_SCHEME_PREFIX+encodeURIComponent(JSON.stringify(payload));}
export function decodeStudyFlowPairingQr(encoded:string):SflpPairingQrPayload{if(!encoded.startsWith(PAIRING_SCHEME_PREFIX))throw new Error("Unsupported StudyFlow pairing QR payload");let parsed:Partial<SflpPairingQrPayload>;try{parsed=JSON.parse(decodeURIComponent(encoded.slice(PAIRING_SCHEME_PREFIX.length))) as Partial<SflpPairingQrPayload>;}catch{throw new Error("Malformed StudyFlow pairing QR payload");}if(parsed.version!==2)throw new Error(`Unsupported pairing QR version ${String(parsed.version)}`);const host=validateDescriptor(parsed.host);if(typeof parsed.pairingId!=="string"||!/^pair-[A-Za-z0-9._:-]+$/.test(parsed.pairingId)||!/^[a-f0-9]{48}$/.test(String(parsed.sessionNonce??""))||!/^[0-9]{6}$/.test(String(parsed.pairingCode??"")))throw new Error("Invalid StudyFlow pairing QR payload");if(typeof parsed.createdAtEpochMillis!=="number"||typeof parsed.expiresAtEpochMillis!=="number"||parsed.createdAtEpochMillis<0||parsed.expiresAtEpochMillis<=parsed.createdAtEpochMillis)throw new Error("Invalid StudyFlow pairing QR expiry");if(!Array.isArray(parsed.endpointHints)||parsed.endpointHints.length===0||parsed.endpointHints.some(h=>typeof h!=="string"||!(h.startsWith("ws://")||h.startsWith("wss://")||h.startsWith("sflp+tcp://"))))throw new Error("Invalid StudyFlow pairing QR endpoints");if(typeof parsed.hostKeyAgreementPublicKeyHex!=="string"||parsed.hostKeyAgreementPublicKeyHex.length<160||parsed.hostKeyAgreementPublicKeyHex.length>1024||parsed.hostKeyAgreementPublicKeyHex.length%2!==0||!/^[a-f0-9]+$/.test(parsed.hostKeyAgreementPublicKeyHex))throw new Error("Invalid pairing host key");return{...(parsed as SflpPairingQrPayload),host};}
function envelope(senderDeviceId:string,workspaceId:string,name:string,payload:Record<string,unknown>):SflpLinkEnvelope{return{protocol:PROTOCOL_VERSION,messageId:messageId("web"),workspaceId,senderDeviceId,type:"request",name,sentAtEpochMillis:Date.now(),payload};}
function frontierFromMap(value:Record<string,number>):SflpJournalFrontier{return{origins:Object.entries(value).filter(([,sequence])=>sequence>0).sort(([a],[b])=>a.localeCompare(b)).map(([deviceId,contiguousSequence])=>({deviceId,contiguousSequence}))};}
function frontierToMap(value:SflpJournalFrontier):Record<string,number>{if(!value||!Array.isArray(value.origins))throw new Error("Malformed SFLP frontier");const result:Record<string,number>={};for(const origin of value.origins){if(!origin||typeof origin.deviceId!=="string"||!origin.deviceId||!Number.isSafeInteger(origin.contiguousSequence)||origin.contiguousSequence<0)throw new Error("Malformed SFLP frontier origin");if(Object.prototype.hasOwnProperty.call(result,origin.deviceId))throw new Error("SFLP frontier repeats an origin");result[origin.deviceId]=origin.contiguousSequence;}return result;}
function frontierEquals(left:Record<string,number>,right:Record<string,number>):boolean{const keys=new Set([...Object.keys(left),...Object.keys(right)]);for(const key of keys)if((left[key]??0)!==(right[key]??0))return false;return true;}
function frontierDominates(left:Record<string,number>,right:Record<string,number>):boolean{return Object.entries(right).every(([deviceId,sequence])=>(left[deviceId]??0)>=sequence);}
function retryKey(peerDeviceId:string,workspaceId:string):string{return `${peerDeviceId}|${workspaceId}`;}
function retryDelayMillis(attempts:number):number{return Math.min(60_000,1_000*Math.pow(2,Math.max(0,Math.min(attempts-1,6))));}
function validateJournalOperation(value:unknown,workspaceId?:string):SflpJournalOperation{if(!value||typeof value!=="object")throw new Error("Malformed SFLP journal operation");const op=value as Partial<SflpJournalOperation>;for(const field of ["workspaceId","operationId","originDeviceId","entityId","operationType"] as const){if(typeof op[field]!=="string"||!(op[field] as string).trim())throw new Error(`Malformed SFLP journal operation ${field}`);}if(workspaceId&&op.workspaceId!==workspaceId)throw new Error("SFLP operation belongs to another workspace");for(const field of ["originSequence","entityRevision","logicalTimestamp"] as const){if(!Number.isSafeInteger(op[field])||(op[field] as number)<1)throw new Error(`Malformed SFLP journal operation ${field}`);}if(!Number.isSafeInteger(op.occurredAtEpochMillis)||Number(op.occurredAtEpochMillis)<0)throw new Error("Malformed SFLP journal operation timestamp");if(op.kind!=="mutation"&&op.kind!=="tombstone")throw new Error("Malformed SFLP journal operation kind");if(!op.payload||typeof op.payload!=="object"||Array.isArray(op.payload))throw new Error("Malformed SFLP journal operation payload");return op as SflpJournalOperation;}
function advanceFrontier(base:Record<string,number>,operations:SflpJournalOperation[]):Record<string,number>{const result={...base};const sequences=new Map<string,Set<number>>();for(const operation of operations){const set=sequences.get(operation.originDeviceId)??new Set<number>();set.add(operation.originSequence);sequences.set(operation.originDeviceId,set);}for(const [deviceId,available] of sequences){let contiguous=result[deviceId]??0;while(available.has(contiguous+1))contiguous++;result[deviceId]=contiguous;}return result;}
function validateSyncBatch(value:unknown,workspaceId:string,expectedBase:Record<string,number>,trustedOrigins:Set<string>):{batchId:string;workspaceId:string;baseFrontier:SflpJournalFrontier;operations:SflpJournalOperation[];throughFrontier:SflpJournalFrontier;hasMore:boolean}{if(!value||typeof value!=="object")throw new StudyFlowWebSyncIntegrityError("Malformed SFLP sync batch");const batch=value as {batchId?:unknown;workspaceId?:unknown;baseFrontier?:unknown;operations?:unknown;throughFrontier?:unknown;hasMore?:unknown};if(typeof batch.batchId!=="string"||!batch.batchId||batch.workspaceId!==workspaceId||!Array.isArray(batch.operations)||typeof batch.hasMore!=="boolean")throw new StudyFlowWebSyncIntegrityError("Malformed SFLP sync batch");const base=frontierToMap(batch.baseFrontier as SflpJournalFrontier);if(!frontierEquals(base,expectedBase))throw new StudyFlowWebSyncIntegrityError("SFLP sync batch base frontier is inconsistent");const operations=batch.operations.map(operation=>validateJournalOperation(operation,workspaceId));const operationIds=new Set<string>();const originSequences=new Set<string>();for(const operation of operations){if(!trustedOrigins.has(operation.originDeviceId))throw new StudyFlowWebSyncIntegrityError("SFLP operation claims an untrusted origin");if(operationIds.has(operation.operationId))throw new StudyFlowWebSyncIntegrityError("SFLP sync batch repeats an operation ID");operationIds.add(operation.operationId);const sequenceKey=`${operation.originDeviceId}:${operation.originSequence}`;if(originSequences.has(sequenceKey))throw new StudyFlowWebSyncIntegrityError("SFLP sync batch repeats an origin sequence");originSequences.add(sequenceKey);}const through=frontierToMap(batch.throughFrontier as SflpJournalFrontier);if(!frontierEquals(through,advanceFrontier(base,operations)))throw new StudyFlowWebSyncIntegrityError("SFLP sync batch declares a spoofed or gapped through frontier");return batch as {batchId:string;workspaceId:string;baseFrontier:SflpJournalFrontier;operations:SflpJournalOperation[];throughFrontier:SflpJournalFrontier;hasMore:boolean};}

export class StudyFlowWebRpcError extends Error{constructor(readonly code:string,readonly retryable:boolean,message:string){super(`${code}: ${message}`);this.name="StudyFlowWebRpcError";}}
export class StudyFlowWebSyncIntegrityError extends Error{constructor(message:string){super(message);this.name="StudyFlowWebSyncIntegrityError";}}
export class StudyFlowWebTerminalPeerError extends Error{constructor(message:string){super(message);this.name="StudyFlowWebTerminalPeerError";}}
export interface SflpWebReconnectResult{pulled:number;pushed:number;status:"complete"|"deferred"|"interrupted"|"terminal_failure";failure?:string;retryAtEpochMillis?:number;}

export interface StudyFlowWebTrustKeyStore{importSecret(peerDeviceId:string,secretHex:string):Promise<string>;sign(keyId:string,message:Uint8Array):Promise<string>;remove(keyId:string):Promise<void>;}
export class StudyFlowWebCryptoTrustKeyStore implements StudyFlowWebTrustKeyStore{
 private readonly memory=new Map<string,CryptoKey>();private dbPromise:Promise<IDBDatabase|null>|null=null;
 constructor(private readonly indexedDb:IDBFactory|undefined=globalThis.indexedDB){}
 async importSecret(peerDeviceId:string,secretHex:string):Promise<string>{if(!/^[a-f0-9]{64}$/.test(secretHex))throw new Error("Invalid SFLP trust secret");const key=await requireSubtleCrypto().importKey("raw",hexBytes(secretHex),{name:"HMAC",hash:"SHA-256"},false,["sign"]);const keyId=`sflp-trust-${peerDeviceId}-${randomHex(8)}`;this.memory.set(keyId,key);const db=await this.db();if(db)await this.put(db,keyId,key);return keyId;}
 async sign(keyId:string,message:Uint8Array):Promise<string>{let key=this.memory.get(keyId);if(!key){const db=await this.db();if(db)key=await this.get(db,keyId)??undefined;if(key)this.memory.set(keyId,key);}if(!key)throw new Error("SFLP trust key is unavailable; pair this device again");return bytesHex(await requireSubtleCrypto().sign("HMAC",key,message));}
 async remove(keyId:string):Promise<void>{this.memory.delete(keyId);const db=await this.db();if(db)await new Promise<void>((resolve,reject)=>{const tx=db.transaction("keys","readwrite");tx.objectStore("keys").delete(keyId);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);});}
 private async db():Promise<IDBDatabase|null>{if(!this.indexedDb)return null;if(!this.dbPromise)this.dbPromise=new Promise((resolve,reject)=>{const request=this.indexedDb!.open("studyflow-sflp-trust-v1",1);request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains("keys"))request.result.createObjectStore("keys");};request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});return this.dbPromise;}
 private put(db:IDBDatabase,keyId:string,key:CryptoKey):Promise<void>{return new Promise((resolve,reject)=>{const tx=db.transaction("keys","readwrite");tx.objectStore("keys").put(key,keyId);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);});}
 private get(db:IDBDatabase,keyId:string):Promise<CryptoKey|null>{return new Promise((resolve,reject)=>{const tx=db.transaction("keys","readonly");const request=tx.objectStore("keys").get(keyId);request.onsuccess=()=>resolve((request.result as CryptoKey|undefined)??null);request.onerror=()=>reject(request.error);});}
}

async function pairingAad(parsed:SflpPairingQrPayload,peerDeviceId:string):Promise<Uint8Array>{return utf8(["SFLP-PAIRING-V2",parsed.pairingId,parsed.host.deviceId,peerDeviceId,parsed.sessionNonce].join("|"));}
async function derivePairingAesKey(privateKey:CryptoKey,hostPublicKeyHex:string,parsed:SflpPairingQrPayload,peerDeviceId:string):Promise<CryptoKey>{const subtle=requireSubtleCrypto();const hostPublic=await subtle.importKey("spki",hexBytes(hostPublicKeyHex),{name:"ECDH",namedCurve:"P-256"},false,[]);const shared=new Uint8Array(await subtle.deriveBits({name:"ECDH",public:hostPublic},privateKey,256));const salt=new Uint8Array(await subtle.digest("SHA-256",utf8(parsed.sessionNonce)));const saltKey=await subtle.importKey("raw",salt,{name:"HMAC",hash:"SHA-256"},false,["sign"]);const prk=new Uint8Array(await subtle.sign("HMAC",saltKey,shared));const prkKey=await subtle.importKey("raw",prk,{name:"HMAC",hash:"SHA-256"},false,["sign"]);const okm=await subtle.sign("HMAC",prkKey,concatBytes(await pairingAad(parsed,peerDeviceId),new Uint8Array([1])));return subtle.importKey("raw",okm,{name:"AES-GCM"},false,["decrypt"]);}
async function decryptPairingSecret(privateKey:CryptoKey,grant:{initializationVectorHex:string;encryptedTrustSecretHex:string},parsed:SflpPairingQrPayload,peerDeviceId:string):Promise<string>{if(!/^[a-f0-9]{24}$/.test(grant.initializationVectorHex)||!/^[a-f0-9]{96,}$/.test(grant.encryptedTrustSecretHex))throw new Error("Invalid encrypted pairing grant");const key=await derivePairingAesKey(privateKey,parsed.hostKeyAgreementPublicKeyHex,parsed,peerDeviceId);const clear=await requireSubtleCrypto().decrypt({name:"AES-GCM",iv:hexBytes(grant.initializationVectorHex),additionalData:await pairingAad(parsed,peerDeviceId),tagLength:128},key,hexBytes(grant.encryptedTrustSecretHex));const secret=new TextDecoder().decode(clear);if(!/^[a-f0-9]{64}$/.test(secret))throw new Error("Pairing grant decrypted an invalid trust secret");return secret;}
async function reconnectProofHex(store:StudyFlowWebTrustKeyStore,keyId:string,challenge:{challengerDeviceId:string;peerDeviceId:string;nonce:string;expiresAtEpochMillis:number}):Promise<string>{const transcript=["SFLP-RECONNECT-V1",challenge.challengerDeviceId,challenge.peerDeviceId,challenge.nonce,String(challenge.expiresAtEpochMillis)].join("|");return store.sign(keyId,utf8(transcript));}

export class StudyFlowWebPeerPersistence{
 constructor(private readonly storage:Storage=globalThis.localStorage){}
 load(kind:SflpClientKind):SflpWebPeerSnapshot{const key=storageKey(kind);const encoded=this.storage.getItem(key);if(!encoded)return defaultState(kind);try{const loaded=JSON.parse(encoded) as Partial<SflpWebPeerSnapshot>;if(!loaded.identity||loaded.identity.clientKind!==kind)throw new Error("Persisted SFLP identity kind is inconsistent");validateDescriptor(loaded.identity);if(!Array.isArray(loaded.trustedPeers)||!loaded.endpoints||typeof loaded.endpoints!=="object"||!Array.isArray(loaded.pendingQueue)||!Array.isArray(loaded.journal))throw new Error("Persisted SFLP snapshot shape is invalid");const trustedPeers=loaded.trustedPeers.map(record=>{const legacy=record as SflpTrustedPeerRecord&{trustSecret?:unknown};validateDescriptor(record.peer);return{peer:record.peer,state:legacy.trustSecret?"revoked":record.state,pairedAtEpochMillis:record.pairedAtEpochMillis,updatedAtEpochMillis:record.updatedAtEpochMillis,lastSeenAtEpochMillis:record.lastSeenAtEpochMillis,revokedAtEpochMillis:legacy.trustSecret?(record.revokedAtEpochMillis??Date.now()):record.revokedAtEpochMillis,trustKeyId:legacy.trustSecret?null:(record.trustKeyId??null)} as SflpTrustedPeerRecord;});const journal=loaded.journal.map(operation=>validateJournalOperation(operation));return{identity:{...loaded.identity,clientKind:kind,protocolSupport:PROTOCOL_SUPPORT,capabilities:capabilitiesFor(kind)},trustedPeers,endpoints:loaded.endpoints,pendingQueue:loaded.pendingQueue,journal,peerRetryStates:loaded.peerRetryStates??{},verifiedRemoteFrontiers:loaded.verifiedRemoteFrontiers??{}};}catch(error){this.quarantine(kind,key,encoded,error);return defaultState(kind);}}
 save(snapshot:SflpWebPeerSnapshot):void{const sanitized={...snapshot,trustedPeers:snapshot.trustedPeers.map(record=>({peer:record.peer,state:record.state,pairedAtEpochMillis:record.pairedAtEpochMillis,updatedAtEpochMillis:record.updatedAtEpochMillis,lastSeenAtEpochMillis:record.lastSeenAtEpochMillis,revokedAtEpochMillis:record.revokedAtEpochMillis,trustKeyId:record.trustKeyId}))};this.storage.setItem(storageKey(snapshot.identity.clientKind),JSON.stringify(sanitized));}
 clear(kind:SflpClientKind="web"):void{this.storage.removeItem(storageKey(kind));}
 corruptionRecords(kind:SflpClientKind="web"):SflpPersistenceCorruptionRecord[]{const raw=this.storage.getItem(`${storageKey(kind)}.corruption.v1`);if(!raw)return[];try{const parsed=JSON.parse(raw);return Array.isArray(parsed)?parsed as SflpPersistenceCorruptionRecord[]:[];}catch{return[];}}
 private quarantine(kind:SflpClientKind,key:string,encoded:string,error:unknown):void{const observedAtEpochMillis=Date.now();const quarantineReference=`${key}.quarantine.${observedAtEpochMillis}.${randomHex(6)}`;this.storage.setItem(quarantineReference,encoded);const record:SflpPersistenceCorruptionRecord={kind,observedAtEpochMillis,storageKey:key,quarantineReference,reason:(error instanceof Error?error.message:String(error)).slice(0,512)};this.storage.setItem(`${key}.corruption.v1`,JSON.stringify([...this.corruptionRecords(kind),record].slice(-64)));this.storage.removeItem(key);}
}

function validateEnvelopeShape(value:unknown):SflpLinkEnvelope{if(!value||typeof value!=="object")throw new Error("Malformed SFLP response envelope");const response=value as Partial<SflpLinkEnvelope>;if(!response.protocol||!isProtocolSupported(response.protocol))throw new Error("SFLP response protocol is incompatible");if(typeof response.messageId!=="string"||typeof response.workspaceId!=="string"||typeof response.senderDeviceId!=="string"||typeof response.name!=="string"||typeof response.sentAtEpochMillis!=="number"||!response.payload||typeof response.payload!=="object"||!["response","error"].includes(String(response.type)))throw new Error("Malformed SFLP response envelope");return response as SflpLinkEnvelope;}
export class StudyFlowWebSocketRpcConnection{
 constructor(private readonly socket:WebSocket){}
 async exchange(request:SflpLinkEnvelope):Promise<SflpLinkEnvelope>{if(this.socket.readyState!==WebSocket.OPEN)throw new Error("SFLP WebSocket is not open");return new Promise((resolve,reject)=>{const cleanup=()=>{this.socket.removeEventListener("message",onMessage);this.socket.removeEventListener("error",onError);this.socket.removeEventListener("close",onClose);};const onError=()=>{cleanup();reject(new Error("SFLP WebSocket exchange failed"));};const onClose=()=>{cleanup();reject(new Error("SFLP WebSocket closed before a response"));};const onMessage=(event:MessageEvent)=>{try{if(typeof event.data!=="string")return;const response=validateEnvelopeShape(JSON.parse(event.data));if(response.correlationId!==request.messageId)return;cleanup();if(response.protocol.major!==request.protocol.major||response.protocol.minor!==request.protocol.minor)throw new Error("SFLP response protocol mismatch");if(response.type==="error"){const code=String(response.payload.code??"link.error");const message=String(response.payload.message??"SFLP request failed");throw new StudyFlowWebRpcError(code,response.payload.retryable===true,message);}resolve(response);}catch(error){cleanup();reject(error);}};this.socket.addEventListener("message",onMessage);this.socket.addEventListener("error",onError,{once:true});this.socket.addEventListener("close",onClose,{once:true});this.socket.send(JSON.stringify(request));});}
 close():void{this.socket.close();}
}

export class StudyFlowWebSocketTransportAdapter{
 readonly supportsListen=false;readonly supportsClientConnect=true;
 async open(endpoint:SflpEndpoint):Promise<WebSocket>{if(endpoint.kind!=="websocket")throw new Error("Web SFLP peers can connect only to ws/wss endpoints directly");return new Promise((resolve,reject)=>{const socket=new WebSocket(endpoint.uri,"studyflow.sflp.v1");socket.onopen=()=>resolve(socket);socket.onerror=()=>reject(new Error("SFLP WebSocket connection failed"));});}
 async connect(endpoint:SflpEndpoint):Promise<StudyFlowWebSocketRpcConnection>{return new StudyFlowWebSocketRpcConnection(await this.open(endpoint));}
}

export class StudyFlowWebManualDiscovery{private endpoints:Record<string,SflpEndpoint[]>={};remember(peerDeviceId:string,uri:string):SflpEndpoint{const endpoint=normalizeEndpoint(uri);const existing=this.endpoints[peerDeviceId]??[];this.endpoints[peerDeviceId]=[endpoint,...existing.filter(item=>item.uri!==endpoint.uri)];return endpoint;}discovered(peerDeviceId:string):SflpEndpoint[]{return [...(this.endpoints[peerDeviceId]??[])];}}

export class StudyFlowWebLinkPeer{
 private snapshot:SflpWebPeerSnapshot;private readonly discovery=new StudyFlowWebManualDiscovery();readonly transport:StudyFlowWebSocketTransportAdapter;readonly presence:StudyFlowWebPresenceRuntime;
 constructor(private readonly persistence:StudyFlowWebPeerPersistence=new StudyFlowWebPeerPersistence(),kind:SflpClientKind="web",transport:StudyFlowWebSocketTransportAdapter=new StudyFlowWebSocketTransportAdapter(),private readonly trustKeys:StudyFlowWebTrustKeyStore=new StudyFlowWebCryptoTrustKeyStore()){this.snapshot=this.persistence.load(kind);this.persistence.save(this.snapshot);this.transport=transport;this.presence=new StudyFlowWebPresenceRuntime(this.identity);}
 get identity():SflpPeerDescriptor{return this.snapshot.identity;}get trustedPeers():SflpTrustedPeerRecord[]{return [...this.snapshot.trustedPeers];}get pendingOutgoingOperations():SflpQueueEntry[]{return [...this.snapshot.pendingQueue];}get journal():SflpJournalOperation[]{return [...this.snapshot.journal];}
 /** Browser receiver metadata is not a native trust grant; native trust is established only through pairFromQr(). */
 createPairingOffer(endpointHints:string[]=this.localEndpointHints()):SflpPairingOffer{const pairingCode=sixDigitCode();const expiresAtEpochMillis=Date.now()+PAIRING_TTL_MILLIS;const qrPayload=JSON.stringify({type:"studyflow.sflp.web.receiver.v2",pairingCode,expiresAtEpochMillis,descriptor:this.identity,endpointHints});return{pairingCode,expiresAtEpochMillis,descriptor:this.identity,endpointHints,qrPayload};}
 async pairFromQr(qrPayload:string):Promise<SflpTrustedPeerRecord>{const parsed=decodeStudyFlowPairingQr(qrPayload);const now=Date.now();if(now<parsed.createdAtEpochMillis-30_000||now>=parsed.expiresAtEpochMillis)throw new Error("Pairing QR is expired or not yet valid");if(parsed.host.deviceId===this.identity.deviceId)throw new Error("Cannot pair a device to itself");const endpoint=parsed.endpointHints.map(normalizeEndpoint).find(item=>item.kind==="websocket");if(!endpoint)throw new Error("Pairing QR does not contain a browser-compatible ws/wss endpoint");const subtle=requireSubtleCrypto();const clientKeys=await subtle.generateKey({name:"ECDH",namedCurve:"P-256"},false,["deriveBits"]) as CryptoKeyPair;const clientPublicKeyHex=bytesHex(await subtle.exportKey("spki",clientKeys.publicKey));const connection=await this.transport.connect(endpoint);try{const request=envelope(this.identity.deviceId,LINK_SYSTEM_WORKSPACE_ID,"link.pair",{pairingId:parsed.pairingId,sessionNonce:parsed.sessionNonce,pairingCode:parsed.pairingCode,peer:this.identity,requestedAtEpochMillis:now,clientKeyAgreementPublicKeyHex:clientPublicKeyHex});const response=await connection.exchange(request);if(response.type!=="response"||response.name!=="link.pairing_grant"||response.senderDeviceId!==parsed.host.deviceId||response.workspaceId!==LINK_SYSTEM_WORKSPACE_ID)throw new Error("Invalid native SFLP pairing response envelope");const grant=response.payload as unknown as {pairingId:string;host:SflpPeerDescriptor;peerDeviceId:string;pairedAtEpochMillis:number;hostKeyAgreementPublicKeyHex:string;initializationVectorHex:string;encryptedTrustSecretHex:string};validateDescriptor(grant.host);if(grant.pairingId!==parsed.pairingId||grant.peerDeviceId!==this.identity.deviceId||grant.host.deviceId!==parsed.host.deviceId||grant.hostKeyAgreementPublicKeyHex!==parsed.hostKeyAgreementPublicKeyHex)throw new Error("Invalid native SFLP pairing grant");const secret=await decryptPairingSecret(clientKeys.privateKey,grant,parsed,this.identity.deviceId);const trustKeyId=await this.trustKeys.importSecret(grant.host.deviceId,secret);const record:SflpTrustedPeerRecord={peer:grant.host,state:"trusted",pairedAtEpochMillis:grant.pairedAtEpochMillis,updatedAtEpochMillis:now,lastSeenAtEpochMillis:now,revokedAtEpochMillis:null,trustKeyId};this.snapshot.trustedPeers=[record,...this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==record.peer.deviceId)];for(const hint of parsed.endpointHints)this.rememberManualEndpoint(record.peer.deviceId,hint);this.persistence.save(this.snapshot);return record;}finally{connection.close();}}
 rememberManualEndpoint(peerDeviceId:string,uri:string):SflpEndpoint{const endpoint=this.discovery.remember(peerDeviceId,uri);const existing=this.snapshot.endpoints[peerDeviceId]??[];this.snapshot.endpoints[peerDeviceId]=[endpoint,...existing.filter(item=>item.uri!==endpoint.uri)];this.persistence.save(this.snapshot);return endpoint;}
 appendLocalMutation(workspaceId:string,entityId:string,operationType:string,payload:Record<string,unknown>):SflpJournalOperation{const sequence=this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.originDeviceId===this.identity.deviceId).reduce((max,op)=>Math.max(max,op.originSequence),0)+1;const entityRevision=this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.entityId===entityId).reduce((max,op)=>Math.max(max,op.entityRevision),0)+1;const logicalTimestamp=this.snapshot.journal.filter(op=>op.workspaceId===workspaceId).reduce((max,op)=>Math.max(max,op.logicalTimestamp??0),0)+1;const occurredAtEpochMillis=Date.now();const operation:SflpJournalOperation={workspaceId,operationId:`op-${this.identity.deviceId}-${sequence}`,originDeviceId:this.identity.deviceId,originSequence:sequence,entityId,entityRevision,logicalTimestamp,operationType,kind:"mutation",occurredAtEpochMillis,payload};this.snapshot.journal=[...this.snapshot.journal,operation];for(const peer of this.snapshot.trustedPeers.filter(p=>p.state==="trusted")){this.snapshot.pendingQueue.push({peerDeviceId:peer.peer.deviceId,workspaceId,operationId:operation.operationId,attempts:0,nextAttemptEpochMillis:Date.now()});}this.persistence.save(this.snapshot);return operation;}
 applyRemoteOperations(operations:SflpJournalOperation[],workspaceId?:string,sourcePeerDeviceId?:string):number{const trustedOrigins=new Set(this.snapshot.trustedPeers.filter(item=>item.state==="trusted").map(item=>item.peer.deviceId));trustedOrigins.add(this.identity.deviceId);if(sourcePeerDeviceId&&!trustedOrigins.has(sourcePeerDeviceId))throw new StudyFlowWebSyncIntegrityError("SFLP operation source peer is not trusted");const byId=new Map(this.snapshot.journal.map(op=>[op.operationId,op]));const byOriginSequence=new Map(this.snapshot.journal.map(op=>[`${op.originDeviceId}:${op.originSequence}`,op]));let added=0;for(const raw of operations){const operation=validateJournalOperation(raw,workspaceId);if(!trustedOrigins.has(operation.originDeviceId))throw new StudyFlowWebSyncIntegrityError("SFLP operation claims an untrusted origin");const existingById=byId.get(operation.operationId);if(existingById){if(JSON.stringify(existingById)!==JSON.stringify(operation))throw new StudyFlowWebSyncIntegrityError("SFLP operation ID was reused with different content");continue;}const sequenceKey=`${operation.originDeviceId}:${operation.originSequence}`;const existingSequence=byOriginSequence.get(sequenceKey);if(existingSequence&&existingSequence.operationId!==operation.operationId)throw new StudyFlowWebSyncIntegrityError("SFLP origin sequence was reused by another operation");byId.set(operation.operationId,operation);byOriginSequence.set(sequenceKey,operation);this.snapshot.journal.push(operation);added++;}if(added)this.persistence.save(this.snapshot);return added;}
 frontier(workspaceId:string):Record<string,number>{const grouped=new Map<string,number[]>();for(const op of this.snapshot.journal.filter(item=>item.workspaceId===workspaceId)){const values=grouped.get(op.originDeviceId)??[];values.push(op.originSequence);grouped.set(op.originDeviceId,values);}const out:Record<string,number>={};for(const [deviceId,sequences] of grouped){const set=new Set(sequences);let contiguous=0;while(set.has(contiguous+1))contiguous++;if(contiguous>0)out[deviceId]=contiguous;}return out;}
 operationsAfter(workspaceId:string,frontier:Record<string,number>,limit=100):SflpJournalOperation[]{return this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.originSequence>(frontier[op.originDeviceId]??0)).sort((a,b)=>a.originDeviceId.localeCompare(b.originDeviceId)||a.originSequence-b.originSequence).slice(0,limit);}
 acknowledge(peerDeviceId:string,workspaceId:string,operationIds:Iterable<string>):void{const acknowledged=new Set(operationIds);if(acknowledged.size===0)return;this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(entry=>entry.peerDeviceId!==peerDeviceId||entry.workspaceId!==workspaceId||!acknowledged.has(entry.operationId));this.persistence.save(this.snapshot);}
 private peerRetry(peerDeviceId:string,workspaceId:string):SflpPeerRetryState|undefined{return this.snapshot.peerRetryStates[retryKey(peerDeviceId,workspaceId)];}
 private recordPeerFailure(peerDeviceId:string,workspaceId:string,now:number,failure:string,attemptedOperationIds:Set<string>):SflpPeerRetryState{const key=retryKey(peerDeviceId,workspaceId);const previous=this.snapshot.peerRetryStates[key];const attempts=(previous?.attempts??0)+1;const state:SflpPeerRetryState={peerDeviceId,workspaceId,attempts,lastAttemptEpochMillis:now,nextAttemptEpochMillis:now+retryDelayMillis(attempts),lastFailure:failure.slice(0,512)};this.snapshot.peerRetryStates[key]=state;if(attemptedOperationIds.size){this.snapshot.pendingQueue=this.snapshot.pendingQueue.map(entry=>entry.peerDeviceId===peerDeviceId&&entry.workspaceId===workspaceId&&attemptedOperationIds.has(entry.operationId)?{...entry,attempts:entry.attempts+1,lastAttemptEpochMillis:now,nextAttemptEpochMillis:state.nextAttemptEpochMillis,lastFailure:state.lastFailure}:entry);}this.persistence.save(this.snapshot);return state;}
 private clearPeerRetry(peerDeviceId:string,workspaceId:string):void{const key=retryKey(peerDeviceId,workspaceId);if(this.snapshot.peerRetryStates[key]){delete this.snapshot.peerRetryStates[key];this.persistence.save(this.snapshot);}}
 private verifiedRemoteFrontier(peerDeviceId:string,workspaceId:string):Record<string,number>{return {...(this.snapshot.verifiedRemoteFrontiers[retryKey(peerDeviceId,workspaceId)]??{})};}
 private saveVerifiedRemoteFrontier(peerDeviceId:string,workspaceId:string,frontier:Record<string,number>):void{this.snapshot.verifiedRemoteFrontiers[retryKey(peerDeviceId,workspaceId)]={...frontier};this.persistence.save(this.snapshot);}
 async reconcileWith(peerDeviceId:string,workspaceId:string):Promise<SflpWebReconnectResult>{const now=Date.now();const retry=this.peerRetry(peerDeviceId,workspaceId);if(retry&&retry.nextAttemptEpochMillis>now)return{pulled:0,pushed:0,status:"deferred",failure:retry.lastFailure,retryAtEpochMillis:retry.nextAttemptEpochMillis};const attemptedOperationIds=new Set<string>();let pulled=0;let pushed=0;try{const result=await this.withAuthenticatedConnection(peerDeviceId,async connection=>{const trustedOrigins=new Set(this.snapshot.trustedPeers.filter(item=>item.state==="trusted").map(item=>item.peer.deviceId));trustedOrigins.add(this.identity.deviceId);const initialFrontier=this.frontier(workspaceId);const frontierResponse=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.frontier",{knownFrontier:frontierFromMap(initialFrontier)}));if(frontierResponse.name!=="sync.frontier"||frontierResponse.senderDeviceId!==peerDeviceId)throw new StudyFlowWebSyncIntegrityError(`Unexpected frontier response ${frontierResponse.name}`);const advertisedRemote=frontierToMap((frontierResponse.payload as unknown as {currentFrontier:SflpJournalFrontier}).currentFrontier);let hasMore=true;let pullRounds=0;while(hasMore){if(++pullRounds>256)throw new StudyFlowWebSyncIntegrityError("SFLP pull exceeded the bounded continuation limit");const known=this.frontier(workspaceId);const pull=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.pull",{workspaceId,knownFrontier:frontierFromMap(known),maxOperations:100}));if(pull.name!=="sync.operations"||pull.senderDeviceId!==peerDeviceId)throw new StudyFlowWebSyncIntegrityError(`Unexpected pull response ${pull.name}`);const batch=validateSyncBatch(pull.payload,workspaceId,known,trustedOrigins);if(batch.hasMore&&batch.operations.length===0)throw new StudyFlowWebSyncIntegrityError("SFLP peer declared continuation without operations");pulled+=this.applyRemoteOperations(batch.operations,workspaceId,peerDeviceId);hasMore=batch.hasMore;}const verifiedBase=this.verifiedRemoteFrontier(peerDeviceId,workspaceId);for(const [origin,sequence] of Object.entries(verifiedBase)){if(sequence>(advertisedRemote[origin]??0))throw new StudyFlowWebSyncIntegrityError("SFLP peer regressed below its verified frontier");}const outgoing=this.operationsAfter(workspaceId,verifiedBase,1000);if(outgoing.length){outgoing.forEach(operation=>attemptedOperationIds.add(operation.operationId));const through=advanceFrontier(verifiedBase,outgoing);const batch={batchId:messageId("web-batch"),workspaceId,baseFrontier:frontierFromMap(verifiedBase),operations:outgoing,throughFrontier:frontierFromMap(through),hasMore:false};const apply=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.apply",batch));if(apply.name!=="sync.apply"||apply.senderDeviceId!==peerDeviceId)throw new StudyFlowWebSyncIntegrityError(`Unexpected apply response ${apply.name}`);const applyPayload=apply.payload as unknown as {resultingFrontier:SflpJournalFrontier};const resulting=frontierToMap(applyPayload.resultingFrontier);if(!frontierDominates(resulting,through))throw new StudyFlowWebSyncIntegrityError("SFLP apply response did not cover the accepted batch");pushed=outgoing.length;this.acknowledge(peerDeviceId,workspaceId,attemptedOperationIds);this.saveVerifiedRemoteFrontier(peerDeviceId,workspaceId,through);}return{pulled,pushed,status:"complete" as const};});this.clearPeerRetry(peerDeviceId,workspaceId);return result;}catch(error){const message=(error instanceof Error?error.message:String(error)).slice(0,512);if(error instanceof StudyFlowWebSyncIntegrityError||error instanceof StudyFlowWebTerminalPeerError||(error instanceof StudyFlowWebRpcError&&!error.retryable))return{pulled,pushed,status:"terminal_failure",failure:message};const state=this.recordPeerFailure(peerDeviceId,workspaceId,Date.now(),message,attemptedOperationIds);return{pulled,pushed,status:"interrupted",failure:message,retryAtEpochMillis:state.nextAttemptEpochMillis};}}
 async sendRoutedRpc(peerDeviceId:string,workspaceId:string,name:string,payload:Record<string,unknown>):Promise<SflpRoutedRpcAccepted>{return this.withAuthenticatedConnection(peerDeviceId,async connection=>{const response=await connection.exchange(envelope(this.identity.deviceId,workspaceId,name,payload));if(response.name!=="link.rpc_accepted"||response.senderDeviceId!==peerDeviceId)throw new Error(`Unexpected routed RPC response ${response.name}`);return response.payload as unknown as SflpRoutedRpcAccepted;});}
 updatePresence(workspaceId:string,availability:StudyFlowPresenceAvailability,activityHint?:StudyFlowPresenceActivityHint,visibilityCapabilities:string[]=[]){return this.presence.announce(workspaceId,availability,activityHint,visibilityCapabilities);}presenceViews(workspaceId:string):StudyFlowPresenceView[]{return this.presence.views(workspaceId,this.identity);}presenceExchangeFor(peerDeviceId:string,workspaceId:string):StudyFlowPresenceExchange{const peer=this.snapshot.trustedPeers.find(item=>item.state==="trusted"&&item.peer.deviceId===peerDeviceId)?.peer;if(!peer)throw new Error("Presence target is not trusted");return this.presence.exchangeForPeer(workspaceId,peer);}acceptPresenceExchange(peerDeviceId:string,workspaceId:string,exchange:StudyFlowPresenceExchange):StudyFlowPresenceIngestResult[]{const trusted=new Set(this.snapshot.trustedPeers.filter(item=>item.state==="trusted").map(item=>item.peer.deviceId));if(!trusted.has(peerDeviceId))throw new Error("Presence sender is not trusted");return this.presence.acceptExchange(workspaceId,peerDeviceId,exchange,trusted);}
 revoke(peerDeviceId:string):SflpTrustedPeerRecord|null{this.presence.forgetPeer(peerDeviceId);const now=Date.now();let revoked:SflpTrustedPeerRecord|null=null;this.snapshot.trustedPeers=this.snapshot.trustedPeers.map(record=>{if(record.peer.deviceId!==peerDeviceId)return record;if(record.trustKeyId)void this.trustKeys.remove(record.trustKeyId);revoked={...record,state:"revoked",trustKeyId:null,updatedAtEpochMillis:now,revokedAtEpochMillis:now};return revoked;});this.persistence.save(this.snapshot);return revoked;}
 forget(peerDeviceId:string):boolean{this.presence.forgetPeer(peerDeviceId);const record=this.snapshot.trustedPeers.find(item=>item.peer.deviceId===peerDeviceId);if(record?.trustKeyId)void this.trustKeys.remove(record.trustKeyId);const before=this.snapshot.trustedPeers.length;this.snapshot.trustedPeers=this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==peerDeviceId);delete this.snapshot.endpoints[peerDeviceId];this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(item=>item.peerDeviceId!==peerDeviceId);for(const key of Object.keys(this.snapshot.peerRetryStates))if(key.startsWith(`${peerDeviceId}|`))delete this.snapshot.peerRetryStates[key];for(const key of Object.keys(this.snapshot.verifiedRemoteFrontiers))if(key.startsWith(`${peerDeviceId}|`))delete this.snapshot.verifiedRemoteFrontiers[key];this.persistence.save(this.snapshot);return before!==this.snapshot.trustedPeers.length;}
 snapshotForDebug():SflpWebPeerSnapshot{return JSON.parse(JSON.stringify(this.snapshot)) as SflpWebPeerSnapshot;}
 private endpointFor(peerDeviceId:string):SflpEndpoint{const endpoint=(this.snapshot.endpoints[peerDeviceId]??[]).find(item=>item.kind==="websocket");if(!endpoint)throw new Error(`No ws/wss endpoint remembered for ${peerDeviceId}`);return endpoint;}
 private trusted(peerDeviceId:string):SflpTrustedPeerRecord{const record=this.snapshot.trustedPeers.find(item=>item.peer.deviceId===peerDeviceId&&item.state==="trusted");if(!record?.trustKeyId)throw new Error(`Peer ${peerDeviceId} is not trusted or its secure key is unavailable`);return record;}
 private async withAuthenticatedConnection<T>(peerDeviceId:string,block:(connection:StudyFlowWebSocketRpcConnection)=>Promise<T>):Promise<T>{const record=this.trusted(peerDeviceId);const connection=await this.transport.connect(this.endpointFor(peerDeviceId));try{const challengeResponse=await connection.exchange(envelope(this.identity.deviceId,LINK_SYSTEM_WORKSPACE_ID,"link.reconnect_challenge",{peerDeviceId:this.identity.deviceId}));if(challengeResponse.name!=="link.reconnect_challenge"||challengeResponse.senderDeviceId!==record.peer.deviceId)throw new StudyFlowWebTerminalPeerError(`Unexpected reconnect challenge ${challengeResponse.name}`);const challenge=challengeResponse.payload as unknown as {challengerDeviceId:string;peerDeviceId:string;nonce:string;issuedAtEpochMillis:number;expiresAtEpochMillis:number};if(challenge.challengerDeviceId!==record.peer.deviceId||challenge.peerDeviceId!==this.identity.deviceId||challenge.expiresAtEpochMillis<=Date.now())throw new StudyFlowWebTerminalPeerError("Reconnect challenge identity/expiry mismatch");const proofHex=await reconnectProofHex(this.trustKeys,record.trustKeyId!,challenge);const accepted=await connection.exchange(envelope(this.identity.deviceId,LINK_SYSTEM_WORKSPACE_ID,"link.reconnect_proof",{proverDeviceId:this.identity.deviceId,nonce:challenge.nonce,proofHex}));if(accepted.name!=="link.reconnect_accepted"||accepted.senderDeviceId!==record.peer.deviceId)throw new StudyFlowWebTerminalPeerError(`Unexpected reconnect response ${accepted.name}`);return await block(connection);}finally{connection.close();}}
 private localEndpointHints():string[]{return globalThis.location?.origin?.startsWith("https")?[globalThis.location.origin.replace(/^https/,"wss")+"/sflp"]:globalThis.location?.hostname==="127.0.0.1"||globalThis.location?.hostname==="localhost"?[globalThis.location.origin.replace(/^http/,"ws")+"/sflp"]:[];}
}

export type SflpActivityKind="dashboard"|"plan"|"schedule"|"study_session"|"practice_question"|"review_item"|"library_material"|"document_view"|"writing_draft"|"presentation_projection";
export type SflpActivityContinuityMode="snapshot"|"live_control"|"presentation_sink";
export interface SflpActivityCursor{positionToken:string;scrollRatio?:number;questionIndex?:number;elapsedMillis?:number;}
export interface SflpActivitySnapshot{activityId:string;workspaceId:string;kind:SflpActivityKind;entityId?:string;entityRevision?:number;cursor:SflpActivityCursor;continuityMode:SflpActivityContinuityMode;requiredCapabilities:string[];payloadHash:string;payload:Record<string,unknown>;}
export interface SflpHandoffOffer{handoffId:string;sourceDevice:SflpPeerDescriptor;targetDeviceId?:string;activity:SflpActivitySnapshot;createdAtEpochMillis:number;expiresAtEpochMillis:number;oneTimeNonceHex:string;}
export interface SflpWebTvReceiverOffer{type:"studyflow.sflp.web_tv.receiver.v1";receiver:SflpPeerDescriptor;workspaceId:string;pairingCode:string;endpointHints:string[];createdAtEpochMillis:number;expiresAtEpochMillis:number;qrPayload:string;}
export type SflpRemoteInputKind="dpad_up"|"dpad_down"|"dpad_left"|"dpad_right"|"select"|"back"|"text";
export interface SflpRemoteInputCommand{commandId:string;sourceDeviceId:string;targetDeviceId:string;kind:SflpRemoteInputKind;issuedAtEpochMillis:number;text?:string;sessionId?:string;workspaceId?:string;leaseToken?:string;sequence?:number;}

export class StudyFlowWebTvConnectivityController{
 private readonly controlRuntime:StudyFlowControlInputRuntime;
 constructor(private readonly peer:StudyFlowWebLinkPeer){if(peer.identity.clientKind!=="web_tv")throw new Error("SFLP12 requires a web_tv peer");this.controlRuntime=new StudyFlowControlInputRuntime(peer.identity);}
 rememberControlLease(lease:StudyFlowControlLeaseEnvelope){return this.controlRuntime.rememberControlLease(lease);}
 createReceiverOffer(workspaceId:string,endpointHints:string[]=this.peer.createPairingOffer().endpointHints):SflpWebTvReceiverOffer{const pairing=this.peer.createPairingOffer(endpointHints);const createdAtEpochMillis=Date.now();const offer={type:"studyflow.sflp.web_tv.receiver.v1" as const,receiver:this.peer.identity,workspaceId,pairingCode:pairing.pairingCode,endpointHints,createdAtEpochMillis,expiresAtEpochMillis:pairing.expiresAtEpochMillis,qrPayload:""};offer.qrPayload=JSON.stringify({...offer,qrPayload:undefined});return offer;}
 acceptRemoteInput(command:SflpRemoteInputCommand):boolean{if(command.sessionId===undefined||command.workspaceId===undefined||command.leaseToken===undefined||command.sequence===undefined)return false;const result=this.controlRuntime.applyControlCommand({messageId:command.commandId,sessionId:command.sessionId,workspaceId:command.workspaceId,sourceDeviceId:command.sourceDeviceId,targetDeviceId:command.targetDeviceId,leaseToken:command.leaseToken,sequence:command.sequence,kind:command.kind,occurredAtEpochMillis:command.issuedAtEpochMillis,payload:command.text?{text:command.text}:undefined});return result.status==="accepted";}
 acceptProjection(snapshot:SflpActivitySnapshot):boolean{return snapshot.kind==="presentation_projection"&&snapshot.continuityMode==="presentation_sink"&&snapshot.requiredCapabilities.includes("device.presentation_sink")&&snapshot.requiredCapabilities.includes("dpad.input");}
}

export class StudyFlowWebActivityHandoffController{
 private readonly consumed=new Set<string>();
 constructor(private readonly peer:StudyFlowWebLinkPeer){ }
 createOffer(targetDeviceId:string|undefined,activity:SflpActivitySnapshot,ttlMillis=90000):SflpHandoffOffer{const now=Date.now();if(activity.requiredCapabilities.length===0)throw new Error("Handoff requires capability declarations");return{handoffId:`handoff-${this.peer.identity.deviceId}-${now}`,sourceDevice:this.peer.identity,targetDeviceId,activity,createdAtEpochMillis:now,expiresAtEpochMillis:now+ttlMillis,oneTimeNonceHex:randomHex(16)};}
 acceptOffer(offer:SflpHandoffOffer):boolean{if(this.consumed.has(offer.handoffId))return false;if(offer.expiresAtEpochMillis<Date.now())return false;if(offer.targetDeviceId&&offer.targetDeviceId!==this.peer.identity.deviceId)return false;const capabilities=new Set(this.peer.identity.capabilities);if(offer.activity.requiredCapabilities.some(capability=>!capabilities.has(capability)))return false;if((this.peer.identity.clientKind==="web_tv"||this.peer.identity.clientKind==="android_tv")&&offer.activity.requiredCapabilities.some(capability=>capability.endsWith(".write")||capability==="writing.edit"||capability==="library.import"))return false;this.consumed.add(offer.handoffId);return true;}
 resumePayload(offer:SflpHandoffOffer):Record<string,unknown>{return{type:"studyflow.sflp.activity_handoff.v1",handoffId:offer.handoffId,workspaceId:offer.activity.workspaceId,activityId:offer.activity.activityId,cursor:offer.activity.cursor};}
}

export const StudyFlowWebHashing = { sha256HexBytes, base64ToBytes, utf8Base64 } as const

export function installStudyFlowWebSflpPeer(kind:SflpClientKind="web"):StudyFlowWebLinkPeer|null{if(typeof globalThis.localStorage==="undefined")return null;const peer=new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(globalThis.localStorage),kind);(globalThis as unknown as {studyFlowSflpPeer?:StudyFlowWebLinkPeer}).studyFlowSflpPeer=peer;globalThis.dispatchEvent(new CustomEvent("studyflow:sflp-peer-ready",{detail:{deviceId:peer.identity.deviceId,clientKind:peer.identity.clientKind}}));return peer;}

export interface SflpLiveSessionCursor{activityId:string;positionToken:string;itemIndex?:number;elapsedMillis:number;answeredCount:number;payloadHash?:string;}
export interface SflpLiveSessionSnapshot{sessionId:string;workspaceId:string;activityId:string;state:"running"|"paused"|"completed";activeExecutorDeviceId:string;fencingToken:string;sessionEpoch:number;lastSequence:number;cursor:SflpLiveSessionCursor;completionOperationId?:string;}
export interface SflpLiveSessionEvent{eventId:string;sessionId:string;workspaceId:string;sourceDeviceId:string;sessionEpoch:number;sequence:number;kind:"started"|"joined"|"heartbeat"|"cursor"|"paused"|"resumed"|"answer_recorded"|"completed";cursor:SflpLiveSessionCursor;completionOperationId?:string;fencingToken?:string;}
export interface SflpPresentationProjectionOffer{projectionId:string;workspaceId:string;sourceDevice:SflpPeerDescriptor;targetDeviceId?:string;contentKind:string;entityId?:string;title:string;contentHash:string;requiredCapabilities:string[];createdAtEpochMillis:number;expiresAtEpochMillis:number;}
export interface SflpPresentationFrame{projectionId:string;frameId:string;workspaceId:string;sourceDeviceId:string;sequence:number;contentRevision:number;contentHash:string;cursor:{positionToken:string};payload?:Record<string,unknown>;}
export interface SflpPresentationAck{projectionId:string;sinkDeviceId:string;acknowledgedFrameId:string;acknowledgedSequence:number;contentHash:string;acknowledgedAtEpochMillis:number;}

export const SFLP14_LIVE_CURSOR_WIRE_NAME="session.live_cursor";
export const SFLP15_PROJECTION_CONTROL_WIRE_NAME="presentation.projection_control";

export class StudyFlowWebLiveSessionContinuityController{
 private applied=new Set<string>();
 constructor(private snapshot:SflpLiveSessionSnapshot){ }
 current():SflpLiveSessionSnapshot{return JSON.parse(JSON.stringify(this.snapshot));}
 applyEvent(event:SflpLiveSessionEvent):"applied"|"duplicate"|"stale_epoch"|"stale_sequence"|"already_completed"|"workspace_mismatch"|"session_mismatch"|"activity_mismatch"|"fencing_token_mismatch"|"executor_mismatch"{if(this.applied.has(event.eventId))return"duplicate";if(event.sessionId!==this.snapshot.sessionId)return"session_mismatch";if(event.workspaceId!==this.snapshot.workspaceId)return"workspace_mismatch";if(event.cursor.activityId!==this.snapshot.activityId)return"activity_mismatch";if(event.fencingToken!==this.snapshot.fencingToken)return"fencing_token_mismatch";if(["cursor","answer_recorded","completed"].includes(event.kind)&&event.sourceDeviceId!==this.snapshot.activeExecutorDeviceId)return"executor_mismatch";if(event.sessionEpoch<this.snapshot.sessionEpoch)return"stale_epoch";if(event.sessionEpoch===this.snapshot.sessionEpoch&&event.sequence<=this.snapshot.lastSequence)return"stale_sequence";if(this.snapshot.state==="completed")return"already_completed";this.snapshot={...this.snapshot,state:event.kind==="paused"?"paused":event.kind==="completed"?"completed":"running",cursor:event.cursor,lastSequence:event.sequence,sessionEpoch:event.sessionEpoch,completionOperationId:event.completionOperationId??this.snapshot.completionOperationId};this.applied.add(event.eventId);return"applied";}
 pause(sourceDeviceId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"paused");}
 resume(sourceDeviceId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"resumed");}
 complete(sourceDeviceId:string,completionOperationId:string):SflpLiveSessionEvent{return this.event(sourceDeviceId,"completed",completionOperationId);}
 private event(sourceDeviceId:string,kind:SflpLiveSessionEvent["kind"],completionOperationId?:string):SflpLiveSessionEvent{return{eventId:`live-${kind}-${Date.now()}`,sessionId:this.snapshot.sessionId,workspaceId:this.snapshot.workspaceId,sourceDeviceId,sessionEpoch:this.snapshot.sessionEpoch,sequence:this.snapshot.lastSequence+1,kind,cursor:this.snapshot.cursor,completionOperationId,fencingToken:this.snapshot.fencingToken};}
}

export class StudyFlowWebPresentationProjectionController{
 private accepted=new Map<string,SflpPresentationProjectionOffer>();
 private frames:Record<string,SflpPresentationFrame>={};
 constructor(private peer:StudyFlowWebLinkPeer){ }
 createProjectionOffer(workspaceId:string,title:string,contentHash:string,targetDeviceId?:string):SflpPresentationProjectionOffer{const now=Date.now();return{projectionId:`projection-${this.peer.identity.deviceId}-${now}`,workspaceId,sourceDevice:this.peer.identity,targetDeviceId,contentKind:"document",title,contentHash,requiredCapabilities:["workspace.read","device.presentation_sink","video.output"],createdAtEpochMillis:now,expiresAtEpochMillis:now+120000};}
 acceptProjectionOffer(offer:SflpPresentationProjectionOffer):boolean{if(offer.expiresAtEpochMillis<Date.now())return false;if(offer.targetDeviceId&&offer.targetDeviceId!==this.peer.identity.deviceId)return false;const caps=new Set(this.peer.identity.capabilities);if(offer.requiredCapabilities.some(capability=>!caps.has(capability)))return false;if(offer.requiredCapabilities.some(capability=>capability.endsWith(".write")||capability==="library.import"||capability==="writing.edit"))return false;this.accepted.set(offer.projectionId,offer);return true;}
 applyProjectionFrame(frame:SflpPresentationFrame):boolean{const offer=this.accepted.get(frame.projectionId);if(!offer)return false;if(frame.workspaceId!==offer.workspaceId)return false;if(frame.sourceDeviceId!==offer.sourceDevice.deviceId)return false;if(frame.contentHash!==offer.contentHash)return false;const current=this.frames[frame.projectionId];if(current&&frame.sequence<=current.sequence)return false;this.frames[frame.projectionId]=frame;return true;}
 acknowledgeProjectionFrame(projectionId:string):SflpPresentationAck|null{const frame=this.frames[projectionId];if(!frame)return null;return{projectionId,sinkDeviceId:this.peer.identity.deviceId,acknowledgedFrameId:frame.frameId,acknowledgedSequence:frame.sequence,contentHash:frame.contentHash,acknowledgedAtEpochMillis:Date.now()};}
 createProjectionCommand(projectionId:string,targetDeviceId:string,kind:string):Record<string,unknown>{return{type:"presentation.projection_control",commandId:`projection-command-${Date.now()}`,projectionId,sourceDeviceId:this.peer.identity.deviceId,targetDeviceId,kind,issuedAtEpochMillis:Date.now()};}
}



export type StudyFlowControlCommandStatus = 'accepted' | 'rejected' | 'duplicate'

export interface StudyFlowControlLeaseEnvelope {
  sessionId: string
  workspaceId: string
  controllerDeviceId: string
  targetDeviceId: string
  leaseToken: string
  grantedAtEpochMillis: number
  expiresAtEpochMillis: number
  allowedCommands: string[]
}

export interface StudyFlowControlCommandEnvelope {
  messageId: string
  sessionId: string
  workspaceId: string
  sourceDeviceId: string
  targetDeviceId: string
  leaseToken: string
  sequence: number
  kind: string
  occurredAtEpochMillis?: number
  payload?: Record<string, unknown>
}

export interface StudyFlowInputDelegationEnvelope {
  delegationId: string
  workspaceId: string
  sourceDeviceId: string
  targetDeviceId: string
  acceptedInputCapabilities: string[]
  createdAtEpochMillis: number
  expiresAtEpochMillis: number
  oneTimeNonceHex: string
}

export interface StudyFlowInputEventEnvelope {
  messageId: string
  delegationId: string
  workspaceId: string
  sourceDeviceId: string
  targetDeviceId: string
  sequence: number
  kind?: string
  occurredAtEpochMillis?: number
}

export class StudyFlowControlInputRuntime {
  private readonly leases = new Map<string, StudyFlowControlLeaseEnvelope>()
  private readonly inputDelegations = new Map<string, StudyFlowInputDelegationEnvelope>()
  private readonly consumedDelegations = new Set<string>()
  private readonly appliedControlMessages = new Set<string>()
  private readonly appliedInputMessages = new Set<string>()
  private readonly lastControlSequence = new Map<string, number>()
  private readonly lastInputSequence = new Map<string, number>()

  constructor(private readonly peer: { deviceId: string; capabilities?: string[] }, private readonly clock: () => number = () => Date.now()) {}

  rememberControlLease(lease: StudyFlowControlLeaseEnvelope) {
    if (lease.targetDeviceId !== this.peer.deviceId) throw new Error('Lease targets a different device')
    if (!lease.sessionId || !lease.workspaceId || !lease.controllerDeviceId || !lease.leaseToken) throw new Error('Malformed control lease')
    if (lease.expiresAtEpochMillis <= lease.grantedAtEpochMillis || lease.expiresAtEpochMillis <= this.clock()) throw new Error('Expired control lease')
    if (!Array.isArray(lease.allowedCommands) || lease.allowedCommands.length === 0) throw new Error('Control lease has no allowed commands')
    this.leases.set(lease.sessionId, {...lease, allowedCommands: [...lease.allowedCommands]})
    return lease
  }

  applyControlCommand(command: StudyFlowControlCommandEnvelope) {
    if (this.appliedControlMessages.has(command.messageId)) return { status: 'duplicate' as StudyFlowControlCommandStatus, acknowledgedSequence: command.sequence }
    const lease = this.leases.get(command.sessionId)
    if (!lease) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'unknown_lease' }
    if (lease.expiresAtEpochMillis < this.clock()) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'lease_expired' }
    if (command.workspaceId !== lease.workspaceId) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'workspace_mismatch' }
    if (command.targetDeviceId !== this.peer.deviceId || command.targetDeviceId !== lease.targetDeviceId) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'wrong_target' }
    if (command.sourceDeviceId !== lease.controllerDeviceId) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'wrong_controller' }
    if (command.leaseToken !== lease.leaseToken) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'bad_lease_token' }
    if (!lease.allowedCommands.includes(command.kind)) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'command_not_allowed' }
    const last = this.lastControlSequence.get(command.sessionId) ?? 0
    if (!Number.isSafeInteger(command.sequence) || command.sequence <= last) return { status: 'rejected' as StudyFlowControlCommandStatus, reason: 'stale_sequence' }
    this.appliedControlMessages.add(command.messageId)
    this.lastControlSequence.set(command.sessionId, command.sequence)
    return { status: 'accepted' as StudyFlowControlCommandStatus, acknowledgedSequence: command.sequence }
  }

  acceptInputDelegation(offer: StudyFlowInputDelegationEnvelope) {
    if (this.consumedDelegations.has(offer.delegationId)) return { status: 'duplicate', delegationId: offer.delegationId }
    if (offer.targetDeviceId !== this.peer.deviceId) return { status: 'rejected', reason: 'wrong_target' }
    if (!offer.sourceDeviceId || offer.sourceDeviceId === this.peer.deviceId) return { status: 'rejected', reason: 'wrong_source' }
    if (offer.expiresAtEpochMillis <= offer.createdAtEpochMillis || offer.expiresAtEpochMillis < this.clock()) return { status: 'rejected', reason: 'expired' }
    if (!/^[a-f0-9]{32,128}$/.test(offer.oneTimeNonceHex)) return { status: 'rejected', reason: 'invalid_nonce' }
    if (!Array.isArray(offer.acceptedInputCapabilities) || offer.acceptedInputCapabilities.length === 0) return { status: 'rejected', reason: 'missing_input_capability' }
    const localCaps = new Set(this.peer.capabilities ?? [])
    if (localCaps.size > 0 && offer.acceptedInputCapabilities.some(capability => !localCaps.has(capability))) return { status: 'rejected', reason: 'missing_input_capability' }
    this.consumedDelegations.add(offer.delegationId)
    this.inputDelegations.set(offer.delegationId, {...offer, acceptedInputCapabilities: [...offer.acceptedInputCapabilities]})
    return { status: 'accepted', delegationId: offer.delegationId }
  }

  applyInputEvent(event: StudyFlowInputEventEnvelope) {
    if (this.appliedInputMessages.has(event.messageId)) return { status: 'duplicate', acknowledgedSequence: event.sequence }
    const offer = this.inputDelegations.get(event.delegationId)
    if (!offer) return { status: 'rejected', reason: 'unknown_delegation' }
    if (offer.expiresAtEpochMillis < this.clock()) return { status: 'rejected', reason: 'delegation_expired' }
    if (event.workspaceId !== offer.workspaceId) return { status: 'rejected', reason: 'workspace_mismatch' }
    if (event.sourceDeviceId !== offer.sourceDeviceId) return { status: 'rejected', reason: 'wrong_source' }
    if (event.targetDeviceId !== this.peer.deviceId || event.targetDeviceId !== offer.targetDeviceId) return { status: 'rejected', reason: 'wrong_target' }
    const last = this.lastInputSequence.get(event.delegationId) ?? 0
    if (!Number.isSafeInteger(event.sequence) || event.sequence <= last) return { status: 'rejected', reason: 'stale_sequence' }
    this.appliedInputMessages.add(event.messageId)
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
  routeAudit?: {
    occurrenceId: string
    reason: string
    decidedAtEpochMillis: number
    owner: { deviceId: string; clientKind: string; surface: string; role: string } | null
    mirrors: Array<{ deviceId: string; clientKind: string; surface: string; role: string }>
    candidateCount: number
    eligibleCount: number
    suppressedDeviceIds: string[]
    policyOutcome?: string
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

function base64ToBytes(encoded: string): Uint8Array | null {
  if (!encoded || typeof encoded !== 'string') return null
  try {
    const bufferCtor = (globalThis as unknown as { Buffer?: { from(value: string, encoding: string): Uint8Array } }).Buffer
    if (bufferCtor) return new Uint8Array(bufferCtor.from(encoded, 'base64'))
    const raw = globalThis.atob(encoded)
    const out = new Uint8Array(raw.length)
    for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i)
    return out
  } catch { return null }
}

function sha256HexBytes(input: Uint8Array): string {
  const k = [
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2,
  ]
  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n))
  const bytes = new Uint8Array((((input.length + 9 + 63) >> 6) << 6))
  bytes.set(input)
  bytes[input.length] = 0x80
  const bitLength = input.length * 8
  const view = new DataView(bytes.buffer)
  view.setUint32(bytes.length - 4, bitLength >>> 0)
  const h = [0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19]
  const w = new Array<number>(64)
  for (let offset = 0; offset < bytes.length; offset += 64) {
    for (let i = 0; i < 16; i++) w[i] = view.getUint32(offset + i * 4)
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3)
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10)
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0
    }
    let [a,b,c,d,e,f,g,hh] = h
    for (let i = 0; i < 64; i++) {
      const s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const temp1 = (hh + s1 + ch + k[i] + w[i]) >>> 0
      const s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = (s0 + maj) >>> 0
      hh = g; g = f; f = e; e = (d + temp1) >>> 0; d = c; c = b; b = a; a = (temp1 + temp2) >>> 0
    }
    h[0] = (h[0] + a) >>> 0; h[1] = (h[1] + b) >>> 0; h[2] = (h[2] + c) >>> 0; h[3] = (h[3] + d) >>> 0
    h[4] = (h[4] + e) >>> 0; h[5] = (h[5] + f) >>> 0; h[6] = (h[6] + g) >>> 0; h[7] = (h[7] + hh) >>> 0
  }
  return h.map(x => x.toString(16).padStart(8, '0')).join('')
}

function utf8Base64(value: string): string {
  const bytes = new TextEncoder().encode(value)
  const bufferCtor = (globalThis as unknown as { Buffer?: { from(value: Uint8Array): { toString(encoding: string): string } } }).Buffer
  if (bufferCtor) return bufferCtor.from(bytes).toString('base64')
  let raw = ''
  for (const byte of bytes) raw += String.fromCharCode(byte)
  return globalThis.btoa(raw)
}

export class StudyFlowNotificationMediaRuntime {
  private attentionLedger: StudyFlowAttentionLedgerSnapshot
  private readonly materialStreams = new Map<string, StudyFlowMaterialStreamOfferEnvelope>()
  private readonly materialChunks = new Map<string, Set<number>>()
  private readonly materialBytes = new Map<string, number[]>()
  private readonly completedMaterialStreams = new Set<string>()

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
      policyAudit: existing?.policyAudit,
      routeAudit: existing?.routeAudit,
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

  applyMaterialChunk(chunk: { streamId: string; sourceDeviceId?: string; targetDeviceId: string; chunkIndex: number; byteOffset: number; bytesBase64?: string; chunkSha256?: string; finalChunk?: boolean }) {
    const offer = this.materialStreams.get(chunk.streamId)
    if (!offer) return { status: 'rejected', reason: 'unknown_stream' }
    if (chunk.targetDeviceId !== this.peer.deviceId) return { status: 'rejected', reason: 'wrong_target' }
    if (chunk.sourceDeviceId && chunk.sourceDeviceId !== offer.sourceDeviceId) return { status: 'rejected', reason: 'wrong_source' }
    if (this.completedMaterialStreams.has(chunk.streamId)) return { status: 'duplicate', acknowledgedChunkIndex: chunk.chunkIndex }
    const bytes = chunk.bytesBase64 ? base64ToBytes(chunk.bytesBase64) : null
    if (!bytes || !chunk.chunkSha256) return { status: 'rejected', reason: 'missing_chunk_bytes' }
    if (sha256HexBytes(bytes) !== chunk.chunkSha256) return { status: 'rejected', reason: 'chunk_hash_mismatch' }
    const chunks = this.materialChunks.get(chunk.streamId) ?? new Set<number>()
    if (chunks.has(chunk.chunkIndex)) return { status: 'duplicate', acknowledgedChunkIndex: chunk.chunkIndex }
    if (chunk.chunkIndex !== chunks.size) return { status: 'rejected', reason: 'out_of_order_chunk' }
    const assembled = this.materialBytes.get(chunk.streamId) ?? []
    const expectedOffset = assembled.length
    if (chunk.byteOffset !== expectedOffset || chunk.byteOffset !== chunk.chunkIndex * offer.chunkSize) return { status: 'rejected', reason: 'unexpected_offset' }
    if (!chunk.finalChunk && bytes.length !== offer.chunkSize) return { status: 'rejected', reason: 'short_non_final_chunk' }
    if (assembled.length + bytes.length > offer.totalBytes) return { status: 'rejected', reason: 'stream_too_large' }
    assembled.push(...bytes)
    chunks.add(chunk.chunkIndex)
    this.materialChunks.set(chunk.streamId, chunks)
    this.materialBytes.set(chunk.streamId, assembled)
    if (!chunk.finalChunk) return { status: 'accepted', acknowledgedChunkIndex: chunk.chunkIndex }
    if (assembled.length !== offer.totalBytes) return { status: 'rejected', reason: 'incomplete_final_chunk' }
    if (sha256HexBytes(new Uint8Array(assembled)) !== offer.contentSha256) return { status: 'rejected', reason: 'content_hash_mismatch' }
    this.completedMaterialStreams.add(chunk.streamId)
    return { status: 'complete', acknowledgedChunkIndex: chunk.chunkIndex }
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

// Keep the SFLP20/SFLP21 wire vocabulary present at runtime as well as in erased
// TypeScript unions. Release-contract checks intentionally audit compiled runtime
// semantics rather than treating source-only type declarations as delivery proof.
export const STUDYFLOW_SHARE_IMPORT_INTENTS: readonly StudyFlowShareImportIntent[] = [
  'clipboard_preview',
  'clipboard_apply',
  'share_preview',
  'share_import',
] as const
export const STUDYFLOW_CONFLICT_REVIEW_OPERATION = 'conflict.review' as const

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
  contentBytesBase64?: string
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
  private localClientKind(): string | undefined { return this.peer.clientKind ?? this.peer.identity?.clientKind }
  private isReadOnlyTv(): boolean { return this.localClientKind() === 'android_tv' || this.localClientKind() === 'web_tv' }
  applyShareImportDelivery(delivery: { sourceDeviceId: string; targetDeviceId: string; sequence: number; offer: StudyFlowShareImportOffer }) {
    const localId = this.peer.deviceId ?? this.peer.identity?.deviceId
    if (delivery.targetDeviceId !== localId) return { status: 'rejected', reason: 'wrong_target' }
    if (delivery.offer.targetDeviceId && delivery.offer.targetDeviceId !== delivery.targetDeviceId) return { status: 'rejected', reason: 'inner_target_mismatch' }
    const source = delivery.offer.sourceDevice as { deviceId?: string } | null
    if (source?.deviceId && source.deviceId !== delivery.sourceDeviceId) return { status: 'rejected', reason: 'wrong_source' }
    if (delivery.offer.expiresAtEpochMillis < Date.now()) return { status: 'expired', reason: 'expired' }
    const caps = new Set(this.peer.capabilities ?? this.peer.identity?.capabilities ?? [])
    if (delivery.offer.requiredCapabilities.some(capability => !caps.has(capability))) return { status: 'rejected', reason: 'missing_capability' }
    if (this.isReadOnlyTv() && (delivery.offer.intent === 'clipboard_apply' || delivery.offer.intent === 'share_import')) {
      return { status: 'rejected', reason: 'tv_write_forbidden' }
    }
    if (delivery.offer.intent === 'clipboard_apply' || delivery.offer.intent === 'share_import' || delivery.offer.contentBytesBase64) {
      const bytes = delivery.offer.contentBytesBase64 ? base64ToBytes(delivery.offer.contentBytesBase64) : null
      if (!bytes) return { status: 'rejected', reason: 'missing_content_bytes' }
      if (sha256HexBytes(bytes) !== delivery.offer.contentSha256) return { status: 'rejected', reason: 'content_hash_mismatch' }
    }
    if (this.imports.has(delivery.offer.importId)) return { status: 'duplicate' }
    this.imports.set(delivery.offer.importId, delivery.offer)
    return { status: 'accepted' }
  }
  openConflictReview(review: StudyFlowConflictReviewModel): boolean {
    this.conflictReviews.set(review.reviewId, review)
    return true
  }
  resolveConflict(reviewId: string, decision: StudyFlowConflictResolutionKind) {
    if (this.isReadOnlyTv()) return { status: 'rejected', reason: 'tv_write_forbidden' }
    if (!this.conflictReviews.has(reviewId)) return { status: 'rejected', reason: 'unknown_review' }
    this.conflictReviews.delete(reviewId)
    return { status: 'resolved', decision, operation: STUDYFLOW_CONFLICT_REVIEW_OPERATION }
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
    if (delivery.offer.targetDeviceId && delivery.offer.targetDeviceId !== delivery.targetDeviceId) return { status: 'rejected', reason: 'inner_target_mismatch' }
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
    if (plan.bundle.targetDeviceId && plan.bundle.targetDeviceId !== plan.targetDeviceId) return { status: 'rejected', reason: 'inner_target_mismatch' }
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
