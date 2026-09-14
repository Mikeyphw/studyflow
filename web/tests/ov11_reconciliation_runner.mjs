import assert from "node:assert/strict";
import {
  StudyFlowWebLinkPeer,
  StudyFlowWebPeerPersistence,
  StudyFlowWebRpcError,
  StudyFlowWebSocketTransportAdapter,
} from "../runtime/sflp_link.js";

class MemoryStorage {
  constructor(){ this.values=new Map(); }
  getItem(k){ return this.values.has(k)?this.values.get(k):null; }
  setItem(k,v){ this.values.set(k,String(v)); }
  removeItem(k){ this.values.delete(k); }
  clear(){ this.values.clear(); }
  key(i){ return [...this.values.keys()][i]??null; }
  get length(){ return this.values.size; }
}

const host={
  deviceId:"desktop-ov11-host",
  displayName:"OV11 Desktop",
  clientKind:"desktop",
  protocolSupport:{major:1,minMinor:0,maxMinor:0},
  capabilities:["workspace.read","workspace.write","workspace.sync"],
};
const trustKeys={
  async importSecret(){ return "ov11-key"; },
  async sign(){ return "a".repeat(64); },
  async remove(){},
};
const response=(request,name,payload)=>({
  protocol:{major:1,minor:0},
  messageId:`response-${request.messageId}`,
  workspaceId:request.workspaceId,
  senderDeviceId:host.deviceId,
  type:"response",
  name,
  sentAtEpochMillis:Date.now(),
  correlationId:request.messageId,
  payload,
});
const trustedRecord=()=>({peer:host,state:"trusted",pairedAtEpochMillis:1,updatedAtEpochMillis:1,lastSeenAtEpochMillis:1,revokedAtEpochMillis:null,trustKeyId:"ov11-key"});
const operation=(sequence)=>({
  workspaceId:"workspace-ov11",
  operationId:`remote-op-${sequence}`,
  originDeviceId:host.deviceId,
  originSequence:sequence,
  entityId:`entity-${sequence}`,
  entityRevision:1,
  logicalTimestamp:sequence,
  operationType:"study.changed",
  kind:"mutation",
  occurredAtEpochMillis:sequence*100,
  payload:{sequence},
});

function configuredPersistence(storage){
  const persistence=new StudyFlowWebPeerPersistence(storage);
  const snapshot=persistence.load("web");
  snapshot.trustedPeers=[trustedRecord()];
  snapshot.endpoints[host.deviceId]=[{kind:"websocket",uri:"ws://127.0.0.1:9876/sflp",lastSeenAtEpochMillis:1}];
  persistence.save(snapshot);
  return persistence;
}

// S4-07: sparse receipt never advances over a missing sequence.
{
  const storage=new MemoryStorage();
  const persistence=configuredPersistence(storage);
  const peer=new StudyFlowWebLinkPeer(persistence,"web",new StudyFlowWebSocketTransportAdapter(),trustKeys);
  assert.equal(peer.applyRemoteOperations([operation(1),operation(3)],"workspace-ov11",host.deviceId),2);
  assert.deepEqual(peer.frontier("workspace-ov11"),{[host.deviceId]:1});
  assert.equal(peer.applyRemoteOperations([operation(2)],"workspace-ov11",host.deviceId),1);
  assert.deepEqual(peer.frontier("workspace-ov11"),{[host.deviceId]:3});
}

// DS2-29: an advertised high frontier is only a hint; unverified history is still sent and acknowledged by operation ID.
{
  class SpoofTransport extends StudyFlowWebSocketTransportAdapter {
    constructor(){ super(); this.connectCount=0; this.applied=[]; }
    async connect(){
      this.connectCount++;
      return {exchange:async request=>{
        if(request.name==="link.reconnect_challenge") return response(request,"link.reconnect_challenge",{challengerDeviceId:host.deviceId,peerDeviceId:request.senderDeviceId,nonce:"b".repeat(48),issuedAtEpochMillis:Date.now()-10,expiresAtEpochMillis:Date.now()+60_000});
        if(request.name==="link.reconnect_proof") return response(request,"link.reconnect_accepted",{peerDeviceId:request.senderDeviceId});
        if(request.name==="sync.frontier") return response(request,"sync.frontier",{currentFrontier:{origins:[{deviceId:request.senderDeviceId,contiguousSequence:999}]}});
        if(request.name==="sync.pull") return response(request,"sync.operations",{batchId:"pull-empty",workspaceId:"workspace-ov11",baseFrontier:request.payload.knownFrontier,operations:[],throughFrontier:request.payload.knownFrontier,hasMore:false});
        if(request.name==="sync.apply") { this.applied.push(...request.payload.operations); return response(request,"sync.apply",{added:request.payload.operations.length,duplicates:0,resultingFrontier:request.payload.throughFrontier,conflicts:[]}); }
        throw new Error(`unexpected ${request.name}`);
      },close(){}};
    }
  }
  const storage=new MemoryStorage();
  const persistence=configuredPersistence(storage);
  const transport=new SpoofTransport();
  const peer=new StudyFlowWebLinkPeer(persistence,"web",transport,trustKeys);
  const local=peer.appendLocalMutation("workspace-ov11","local-entity","study.changed",{value:1});
  const result=await peer.reconcileWith(host.deviceId,"workspace-ov11");
  assert.equal(result.status,"complete");
  assert.ok(transport.applied.some(op=>op.operationId===local.operationId),"spoofed high frontier must not suppress unverified local history");
  assert.equal(peer.pendingOutgoingOperations.length,0);
}

// S4-15 + DS2-03: retry state gates the real connect call and does not manufacture queue attempts.
{
  class RetryTransport extends StudyFlowWebSocketTransportAdapter {
    constructor(){ super(); this.connectCount=0; }
    async connect(){
      this.connectCount++;
      return {exchange:async request=>{
        if(request.name==="link.reconnect_challenge") return response(request,"link.reconnect_challenge",{challengerDeviceId:host.deviceId,peerDeviceId:request.senderDeviceId,nonce:"c".repeat(48),issuedAtEpochMillis:Date.now()-10,expiresAtEpochMillis:Date.now()+60_000});
        if(request.name==="link.reconnect_proof") return response(request,"link.reconnect_accepted",{peerDeviceId:request.senderDeviceId});
        throw new StudyFlowWebRpcError("link.unavailable",true,"temporary outage");
      },close(){}};
    }
  }
  const storage=new MemoryStorage();
  const persistence=configuredPersistence(storage);
  const transport=new RetryTransport();
  const peer=new StudyFlowWebLinkPeer(persistence,"web",transport,trustKeys);
  const first=await peer.reconcileWith(host.deviceId,"workspace-ov11");
  assert.equal(first.status,"interrupted");
  assert.ok(first.retryAtEpochMillis>Date.now());
  const second=await peer.reconcileWith(host.deviceId,"workspace-ov11");
  assert.equal(second.status,"deferred");
  assert.equal(transport.connectCount,1,"deferred retry must gate the actual WebSocket connection attempt");
}

// S4-18: corrupted browser state is quarantined with a diagnostic record before fallback identity is created.
{
  const storage=new MemoryStorage();
  storage.setItem("studyflow.sflp.web.peer.v1","{broken-json");
  const persistence=new StudyFlowWebPeerPersistence(storage);
  const recovered=persistence.load("web");
  assert.ok(recovered.identity.deviceId.startsWith("device-web-"));
  const records=persistence.corruptionRecords("web");
  assert.equal(records.length,1);
  assert.equal(records[0].storageKey,"studyflow.sflp.web.peer.v1");
  assert.equal(storage.getItem(records[0].quarantineReference),"{broken-json");
}

console.log("OV11_WEB_RECONCILIATION_RECOVERY: PASS");
