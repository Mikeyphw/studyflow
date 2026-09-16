import assert from "node:assert/strict";
import {
  StudyFlowWebLinkPeer,
  StudyFlowWebPeerPersistence,
  StudyFlowWebSocketTransportAdapter,
  decodeStudyFlowPairingQr,
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
  deviceId:"desktop-ov09-host",
  displayName:"OV09 Desktop",
  clientKind:"desktop",
  protocolSupport:{major:1,minMinor:0,maxMinor:0},
  capabilities:["workspace.read","workspace.write","workspace.sync","device.control","device.presentation_source"],
};
const pairing={
  version:2,
  createdAtEpochMillis:1,
  expiresAtEpochMillis:9999999999999,
  host,
  hostKeyAgreementPublicKeyHex:"a".repeat(160),
  pairingId:"pair-ov09-native",
  sessionNonce:"a".repeat(48),
  pairingCode:"123456",
  endpointHints:["ws://127.0.0.1:8765/sflp","sflp+tcp://127.0.0.1:8764"],
};
const qr=`studyflow://pair?data=${encodeURIComponent(JSON.stringify(pairing))}`;
assert.deepEqual(decodeStudyFlowPairingQr(qr),pairing);

class FakeTransport extends StudyFlowWebSocketTransportAdapter {
  constructor(){ super(); this.requests=[]; this.webDeviceId=null; }
  async connect(endpoint){
    assert.equal(endpoint.kind,"websocket");
    return {
      exchange: async request => {
        this.requests.push(request);
        if(request.name==="link.pair"){
          this.webDeviceId=request.senderDeviceId;
          return response(request,"link.pairing_grant",{
            pairingId:pairing.pairingId,
            host,
            peerDeviceId:request.senderDeviceId,
            trustSecret:"b".repeat(64),
            pairedAtEpochMillis:20_000,
          });
        }
        if(request.name==="link.reconnect_challenge"){
          return response(request,"link.reconnect_challenge",{
            challengerDeviceId:host.deviceId,
            peerDeviceId:request.senderDeviceId,
            nonce:"c".repeat(48),
            issuedAtEpochMillis:20_100,
            expiresAtEpochMillis:80_000,
          });
        }
        if(request.name==="link.reconnect_proof"){
          assert.equal(request.payload.proverDeviceId,this.webDeviceId);
          assert.match(request.payload.proofHex,/^[a-f0-9]{64}$/);
          return response(request,"link.reconnect_accepted",{peerDeviceId:this.webDeviceId});
        }
        return response(request,"link.rpc_accepted",{
          requestMessageId:request.messageId,
          requestName:request.name,
          receivingDeviceId:host.deviceId,
          acceptedAtEpochMillis:20_200,
        });
      },
      close() {},
    };
  }
}
function response(request,name,payload){return{protocol:{major:1,minor:0},messageId:`response-${request.messageId}`,workspaceId:request.workspaceId,senderDeviceId:host.deviceId,type:"response",name,sentAtEpochMillis:20_000,correlationId:request.messageId,payload};}

const storage=new MemoryStorage();
const transport=new FakeTransport();
const web=new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(storage),"web",transport);
const webTv=new StudyFlowWebLinkPeer(new StudyFlowWebPeerPersistence(storage),"web_tv",new FakeTransport());
assert.notEqual(web.identity.deviceId,webTv.identity.deviceId,"Web and Web TV must persist distinct identities");
assert.equal(storage.length,2,"Web and Web TV must use separate persistent identity keys");

const record=await web.pairFromQr(qr);
assert.equal(record.peer.deviceId,host.deviceId);
assert.equal(record.trustSecret,"b".repeat(64),"Web must persist the host-issued trust secret, not mint its own");
assert.equal(transport.requests[0].name,"link.pair");
assert.equal(transport.requests[0].payload.sessionNonce,pairing.sessionNonce);
assert.equal(transport.requests[0].payload.pairingCode,pairing.pairingCode);

const operation=web.appendLocalMutation("workspace-ov09","goal-ov09","goal.completed",{done:true});
assert.equal(operation.kind,"mutation");
assert.equal(operation.entityRevision,1);
assert.equal(operation.logicalTimestamp,1);
assert.ok(operation.occurredAtEpochMillis>0);
assert.equal("createdAtEpochMillis" in operation,false);

const accepted=await web.sendRoutedRpc(host.deviceId,"workspace-ov09","presentation.projection_control",{projectionId:"projection-ov09"});
assert.equal(accepted.requestName,"presentation.projection_control");
assert.equal(accepted.receivingDeviceId,host.deviceId);
assert.ok(transport.requests.some(request=>request.name==="link.reconnect_challenge"));
assert.ok(transport.requests.some(request=>request.name==="link.reconnect_proof"));
assert.ok(transport.requests.some(request=>request.name==="presentation.projection_control"));

console.log("OV09_WEB_NATIVE_INTEROP: PASS");
