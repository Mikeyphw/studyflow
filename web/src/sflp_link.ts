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
export interface SflpQueueEntry{peerDeviceId:string;workspaceId:string;operationId:string;attempts:number;nextAttemptEpochMillis:number;}
export interface SflpWebPeerSnapshot{identity:SflpPeerDescriptor;trustedPeers:SflpTrustedPeerRecord[];endpoints:Record<string,SflpEndpoint[]>;pendingQueue:SflpQueueEntry[];journal:SflpJournalOperation[];}
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
function defaultState(kind:SflpClientKind):SflpWebPeerSnapshot{if(kind!=="web"&&kind!=="web_tv")throw new Error("Browser persistence supports web or web_tv identities only");const suffix=randomHex(8);const prefix=kind==="web_tv"?"device-web-tv":"device-web";return{identity:{deviceId:`${prefix}-${suffix}`,displayName:kind==="web_tv"?"StudyFlow Web TV":"StudyFlow Web",clientKind:kind,protocolSupport:PROTOCOL_SUPPORT,capabilities:capabilitiesFor(kind)},trustedPeers:[],endpoints:{},pendingQueue:[],journal:[]};}
function safeParse(text:string|null):SflpWebPeerSnapshot|null{if(!text)return null;try{return JSON.parse(text) as SflpWebPeerSnapshot}catch{return null;}}
function hexBytes(value:string):Uint8Array{if(value.length%2!==0||!/^[a-f0-9]+$/.test(value))throw new Error("Expected lower-case hex");const out=new Uint8Array(value.length/2);for(let i=0;i<out.length;i++)out[i]=Number.parseInt(value.slice(i*2,i*2+2),16);return out;}
function bytesHex(value:ArrayBuffer|ArrayBufferView):string{const bytes=value instanceof ArrayBuffer?new Uint8Array(value):new Uint8Array(value.buffer,value.byteOffset,value.byteLength);return [...bytes].map(x=>x.toString(16).padStart(2,"0")).join("");}
function utf8(value:string):Uint8Array{return new TextEncoder().encode(value);}
function concatBytes(...parts:Uint8Array[]):Uint8Array{const size=parts.reduce((sum,p)=>sum+p.length,0);const out=new Uint8Array(size);let offset=0;for(const p of parts){out.set(p,offset);offset+=p.length;}return out;}
function isProtocolSupported(version:SflpProtocolVersion):boolean{return version.major===PROTOCOL_SUPPORT.major&&version.minor>=PROTOCOL_SUPPORT.minMinor&&version.minor<=PROTOCOL_SUPPORT.maxMinor;}
function validateDescriptor(value:unknown):SflpPeerDescriptor{if(!value||typeof value!=="object")throw new Error("Pairing host descriptor is missing");const d=value as Partial<SflpPeerDescriptor>;if(typeof d.deviceId!=="string"||!/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(d.deviceId))throw new Error("Invalid pairing host device ID");if(typeof d.displayName!=="string"||!d.displayName.trim()||d.displayName.length>120)throw new Error("Invalid pairing host display name");if(!CLIENT_KINDS.has(d.clientKind as SflpClientKind))throw new Error("Invalid pairing host client kind");if(!d.protocolSupport||d.protocolSupport.major!==1||d.protocolSupport.minMinor>0||d.protocolSupport.maxMinor<0)throw new Error("Pairing host has incompatible protocol support");if(!Array.isArray(d.capabilities)||d.capabilities.some(cap=>typeof cap!=="string"||!/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(cap)))throw new Error("Invalid pairing host capabilities");return d as SflpPeerDescriptor;}
function encodePairingQr(payload:SflpPairingQrPayload):string{return PAIRING_SCHEME_PREFIX+encodeURIComponent(JSON.stringify(payload));}
export function decodeStudyFlowPairingQr(encoded:string):SflpPairingQrPayload{if(!encoded.startsWith(PAIRING_SCHEME_PREFIX))throw new Error("Unsupported StudyFlow pairing QR payload");let parsed:Partial<SflpPairingQrPayload>;try{parsed=JSON.parse(decodeURIComponent(encoded.slice(PAIRING_SCHEME_PREFIX.length))) as Partial<SflpPairingQrPayload>;}catch{throw new Error("Malformed StudyFlow pairing QR payload");}if(parsed.version!==2)throw new Error(`Unsupported pairing QR version ${String(parsed.version)}`);const host=validateDescriptor(parsed.host);if(typeof parsed.pairingId!=="string"||!/^pair-[A-Za-z0-9._:-]+$/.test(parsed.pairingId)||!/^[a-f0-9]{48}$/.test(String(parsed.sessionNonce??""))||!/^[0-9]{6}$/.test(String(parsed.pairingCode??"")))throw new Error("Invalid StudyFlow pairing QR payload");if(typeof parsed.createdAtEpochMillis!=="number"||typeof parsed.expiresAtEpochMillis!=="number"||parsed.createdAtEpochMillis<0||parsed.expiresAtEpochMillis<=parsed.createdAtEpochMillis)throw new Error("Invalid StudyFlow pairing QR expiry");if(!Array.isArray(parsed.endpointHints)||parsed.endpointHints.length===0||parsed.endpointHints.some(h=>typeof h!=="string"||!(h.startsWith("ws://")||h.startsWith("wss://")||h.startsWith("sflp+tcp://"))))throw new Error("Invalid StudyFlow pairing QR endpoints");if(typeof parsed.hostKeyAgreementPublicKeyHex!=="string"||parsed.hostKeyAgreementPublicKeyHex.length<160||parsed.hostKeyAgreementPublicKeyHex.length>1024||parsed.hostKeyAgreementPublicKeyHex.length%2!==0||!/^[a-f0-9]+$/.test(parsed.hostKeyAgreementPublicKeyHex))throw new Error("Invalid pairing host key");return{...(parsed as SflpPairingQrPayload),host};}
function envelope(senderDeviceId:string,workspaceId:string,name:string,payload:Record<string,unknown>):SflpLinkEnvelope{return{protocol:PROTOCOL_VERSION,messageId:messageId("web"),workspaceId,senderDeviceId,type:"request",name,sentAtEpochMillis:Date.now(),payload};}
function frontierFromMap(value:Record<string,number>):SflpJournalFrontier{return{origins:Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([deviceId,contiguousSequence])=>({deviceId,contiguousSequence}))};}
function frontierToMap(value:SflpJournalFrontier):Record<string,number>{const result:Record<string,number>={};for(const origin of value.origins??[])result[origin.deviceId]=origin.contiguousSequence;return result;}

export class StudyFlowWebRpcError extends Error{constructor(readonly code:string,readonly retryable:boolean,message:string){super(`${code}: ${message}`);this.name="StudyFlowWebRpcError";}}

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
 load(kind:SflpClientKind):SflpWebPeerSnapshot{const loaded=safeParse(this.storage.getItem(storageKey(kind)));if(!loaded)return defaultState(kind);if(loaded.identity?.clientKind!==kind)return defaultState(kind);const trustedPeers=(loaded.trustedPeers??[]).map(record=>{const legacy=record as SflpTrustedPeerRecord&{trustSecret?:unknown};return{peer:record.peer,state:legacy.trustSecret?"revoked":record.state,pairedAtEpochMillis:record.pairedAtEpochMillis,updatedAtEpochMillis:record.updatedAtEpochMillis,lastSeenAtEpochMillis:record.lastSeenAtEpochMillis,revokedAtEpochMillis:legacy.trustSecret?(record.revokedAtEpochMillis??Date.now()):record.revokedAtEpochMillis,trustKeyId:legacy.trustSecret?null:(record.trustKeyId??null)} as SflpTrustedPeerRecord;});return{identity:{...loaded.identity,clientKind:kind,protocolSupport:PROTOCOL_SUPPORT,capabilities:capabilitiesFor(kind)},trustedPeers,endpoints:loaded.endpoints??{},pendingQueue:loaded.pendingQueue??[],journal:loaded.journal??[]};}
 save(snapshot:SflpWebPeerSnapshot):void{const sanitized={...snapshot,trustedPeers:snapshot.trustedPeers.map(record=>({peer:record.peer,state:record.state,pairedAtEpochMillis:record.pairedAtEpochMillis,updatedAtEpochMillis:record.updatedAtEpochMillis,lastSeenAtEpochMillis:record.lastSeenAtEpochMillis,revokedAtEpochMillis:record.revokedAtEpochMillis,trustKeyId:record.trustKeyId}))};this.storage.setItem(storageKey(snapshot.identity.clientKind),JSON.stringify(sanitized));}
 clear(kind:SflpClientKind="web"):void{this.storage.removeItem(storageKey(kind));}
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
 appendLocalMutation(workspaceId:string,entityId:string,operationType:string,payload:Record<string,unknown>):SflpJournalOperation{const mine=this.snapshot.journal.filter(op=>op.originDeviceId===this.identity.deviceId);const sequence=mine.reduce((max,op)=>Math.max(max,op.originSequence),0)+1;const entityRevision=this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.entityId===entityId).reduce((max,op)=>Math.max(max,op.entityRevision),0)+1;const logicalTimestamp=this.snapshot.journal.filter(op=>op.workspaceId===workspaceId).reduce((max,op)=>Math.max(max,op.logicalTimestamp??0),0)+1;const occurredAtEpochMillis=Date.now();const operation:SflpJournalOperation={workspaceId,operationId:`op-${this.identity.deviceId}-${sequence}`,originDeviceId:this.identity.deviceId,originSequence:sequence,entityId,entityRevision,logicalTimestamp,operationType,kind:"mutation",occurredAtEpochMillis,payload};this.snapshot.journal=[...this.snapshot.journal,operation];for(const peer of this.snapshot.trustedPeers.filter(p=>p.state==="trusted")){this.snapshot.pendingQueue.push({peerDeviceId:peer.peer.deviceId,workspaceId,operationId:operation.operationId,attempts:0,nextAttemptEpochMillis:Date.now()});}this.persistence.save(this.snapshot);return operation;}
 applyRemoteOperations(operations:SflpJournalOperation[]):number{const seen=new Set(this.snapshot.journal.map(op=>op.operationId));let added=0;for(const operation of operations){if(seen.has(operation.operationId))continue;seen.add(operation.operationId);this.snapshot.journal.push(operation);added++;}if(added)this.persistence.save(this.snapshot);return added;}
 frontier(workspaceId:string):Record<string,number>{const grouped=new Map<string,number[]>();for(const op of this.snapshot.journal.filter(item=>item.workspaceId===workspaceId)){const values=grouped.get(op.originDeviceId)??[];values.push(op.originSequence);grouped.set(op.originDeviceId,values);}const out:Record<string,number>={};for(const [deviceId,sequences] of grouped){const set=new Set(sequences);let contiguous=0;while(set.has(contiguous+1))contiguous++;out[deviceId]=contiguous;}return out;}
 operationsAfter(workspaceId:string,frontier:Record<string,number>,limit=100):SflpJournalOperation[]{return this.snapshot.journal.filter(op=>op.workspaceId===workspaceId&&op.originSequence>(frontier[op.originDeviceId]??0)).sort((a,b)=>a.originDeviceId.localeCompare(b.originDeviceId)||a.originSequence-b.originSequence).slice(0,limit);}
 acknowledge(peerDeviceId:string,workspaceId:string,frontier:Record<string,number>):void{this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(entry=>{if(entry.peerDeviceId!==peerDeviceId||entry.workspaceId!==workspaceId)return true;const op=this.snapshot.journal.find(item=>item.operationId===entry.operationId);return !op||op.originSequence>(frontier[op.originDeviceId]??0);});this.persistence.save(this.snapshot);}
 async reconcileWith(peerDeviceId:string,workspaceId:string):Promise<{pulled:number;pushed:number}>{return this.withAuthenticatedConnection(peerDeviceId,async connection=>{const initialFrontier=frontierFromMap(this.frontier(workspaceId));const frontierResponse=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.frontier",{knownFrontier:initialFrontier}));if(frontierResponse.name!=="sync.frontier"||frontierResponse.senderDeviceId!==peerDeviceId)throw new Error(`Unexpected frontier response ${frontierResponse.name}`);let remoteFrontier=(frontierResponse.payload as unknown as {currentFrontier:SflpJournalFrontier}).currentFrontier;let pulled=0;let hasMore=true;while(hasMore){const pull=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.pull",{workspaceId,knownFrontier:frontierFromMap(this.frontier(workspaceId)),maxOperations:100}));if(pull.name!=="sync.operations"||pull.senderDeviceId!==peerDeviceId)throw new Error(`Unexpected pull response ${pull.name}`);const batch=pull.payload as unknown as {batchId:string;workspaceId:string;baseFrontier:SflpJournalFrontier;operations:SflpJournalOperation[];throughFrontier:SflpJournalFrontier;hasMore:boolean};pulled+=this.applyRemoteOperations(batch.operations);hasMore=batch.hasMore;remoteFrontier=batch.throughFrontier;}const remoteMap=frontierToMap(remoteFrontier);const outgoing=this.operationsAfter(workspaceId,remoteMap,1000);let pushed=0;if(outgoing.length){const through={...remoteMap};for(const op of outgoing)through[op.originDeviceId]=Math.max(through[op.originDeviceId]??0,op.originSequence);const batch={batchId:messageId("web-batch"),workspaceId,baseFrontier:remoteFrontier,operations:outgoing,throughFrontier:frontierFromMap(through),hasMore:false};const apply=await connection.exchange(envelope(this.identity.deviceId,workspaceId,"sync.apply",batch));if(apply.name!=="sync.apply"||apply.senderDeviceId!==peerDeviceId)throw new Error(`Unexpected apply response ${apply.name}`);pushed=outgoing.length;this.acknowledge(peerDeviceId,workspaceId,through);}return{pulled,pushed};});}
 async sendRoutedRpc(peerDeviceId:string,workspaceId:string,name:string,payload:Record<string,unknown>):Promise<SflpRoutedRpcAccepted>{return this.withAuthenticatedConnection(peerDeviceId,async connection=>{const response=await connection.exchange(envelope(this.identity.deviceId,workspaceId,name,payload));if(response.name!=="link.rpc_accepted"||response.senderDeviceId!==peerDeviceId)throw new Error(`Unexpected routed RPC response ${response.name}`);return response.payload as unknown as SflpRoutedRpcAccepted;});}
 updatePresence(workspaceId:string,availability:StudyFlowPresenceAvailability,activityHint?:StudyFlowPresenceActivityHint,visibilityCapabilities:string[]=[]){return this.presence.announce(workspaceId,availability,activityHint,visibilityCapabilities);}presenceViews(workspaceId:string):StudyFlowPresenceView[]{return this.presence.views(workspaceId,this.identity);}presenceExchangeFor(peerDeviceId:string,workspaceId:string):StudyFlowPresenceExchange{const peer=this.snapshot.trustedPeers.find(item=>item.state==="trusted"&&item.peer.deviceId===peerDeviceId)?.peer;if(!peer)throw new Error("Presence target is not trusted");return this.presence.exchangeForPeer(workspaceId,peer);}acceptPresenceExchange(peerDeviceId:string,workspaceId:string,exchange:StudyFlowPresenceExchange):StudyFlowPresenceIngestResult[]{const trusted=new Set(this.snapshot.trustedPeers.filter(item=>item.state==="trusted").map(item=>item.peer.deviceId));if(!trusted.has(peerDeviceId))throw new Error("Presence sender is not trusted");return this.presence.acceptExchange(workspaceId,peerDeviceId,exchange,trusted);}
 revoke(peerDeviceId:string):SflpTrustedPeerRecord|null{this.presence.forgetPeer(peerDeviceId);const now=Date.now();let revoked:SflpTrustedPeerRecord|null=null;this.snapshot.trustedPeers=this.snapshot.trustedPeers.map(record=>{if(record.peer.deviceId!==peerDeviceId)return record;if(record.trustKeyId)void this.trustKeys.remove(record.trustKeyId);revoked={...record,state:"revoked",trustKeyId:null,updatedAtEpochMillis:now,revokedAtEpochMillis:now};return revoked;});this.persistence.save(this.snapshot);return revoked;}
 forget(peerDeviceId:string):boolean{this.presence.forgetPeer(peerDeviceId);const record=this.snapshot.trustedPeers.find(item=>item.peer.deviceId===peerDeviceId);if(record?.trustKeyId)void this.trustKeys.remove(record.trustKeyId);const before=this.snapshot.trustedPeers.length;this.snapshot.trustedPeers=this.snapshot.trustedPeers.filter(item=>item.peer.deviceId!==peerDeviceId);delete this.snapshot.endpoints[peerDeviceId];this.snapshot.pendingQueue=this.snapshot.pendingQueue.filter(item=>item.peerDeviceId!==peerDeviceId);this.persistence.save(this.snapshot);return before!==this.snapshot.trustedPeers.length;}
 snapshotForDebug():SflpWebPeerSnapshot{return JSON.parse(JSON.stringify(this.snapshot)) as SflpWebPeerSnapshot;}
 private endpointFor(peerDeviceId:string):SflpEndpoint{const endpoint=(this.snapshot.endpoints[peerDeviceId]??[]).find(item=>item.kind==="websocket");if(!endpoint)throw new Error(`No ws/wss endpoint remembered for ${peerDeviceId}`);return endpoint;}
 private trusted(peerDeviceId:string):SflpTrustedPeerRecord{const record=this.snapshot.trustedPeers.find(item=>item.peer.deviceId===peerDeviceId&&item.state==="trusted");if(!record?.trustKeyId)throw new Error(`Peer ${peerDeviceId} is not trusted or its secure key is unavailable`);return record;}
 private async withAuthenticatedConnection<T>(peerDeviceId:string,block:(connection:StudyFlowWebSocketRpcConnection)=>Promise<T>):Promise<T>{const record=this.trusted(peerDeviceId);const connection=await this.transport.connect(this.endpointFor(peerDeviceId));try{const challengeResponse=await connection.exchange(envelope(this.identity.deviceId,LINK_SYSTEM_WORKSPACE_ID,"link.reconnect_challenge",{peerDeviceId:this.identity.deviceId}));if(challengeResponse.name!=="link.reconnect_challenge"||challengeResponse.senderDeviceId!==record.peer.deviceId)throw new Error(`Unexpected reconnect challenge ${challengeResponse.name}`);const challenge=challengeResponse.payload as unknown as {challengerDeviceId:string;peerDeviceId:string;nonce:string;issuedAtEpochMillis:number;expiresAtEpochMillis:number};if(challenge.challengerDeviceId!==record.peer.deviceId||challenge.peerDeviceId!==this.identity.deviceId||challenge.expiresAtEpochMillis<=Date.now())throw new Error("Reconnect challenge identity/expiry mismatch");const proofHex=await reconnectProofHex(this.trustKeys,record.trustKeyId!,challenge);const accepted=await connection.exchange(envelope(this.identity.deviceId,LINK_SYSTEM_WORKSPACE_ID,"link.reconnect_proof",{proverDeviceId:this.identity.deviceId,nonce:challenge.nonce,proofHex}));if(accepted.name!=="link.reconnect_accepted"||accepted.senderDeviceId!==record.peer.deviceId)throw new Error(`Unexpected reconnect response ${accepted.name}`);return await block(connection);}finally{connection.close();}}
 private localEndpointHints():string[]{return globalThis.location?.origin?.startsWith("https")?[globalThis.location.origin.replace(/^https/,"wss")+"/sflp"]:globalThis.location?.hostname==="127.0.0.1"||globalThis.location?.hostname==="localhost"?[globalThis.location.origin.replace(/^http/,"ws")+"/sflp"]:[];}
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
    if (this.isReadOnlyTv() && (delivery.offer.intent === 'clipboard_apply' || delivery.offer.intent === 'share_import')) {
      return { status: 'rejected', reason: 'tv_write_forbidden' }
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
