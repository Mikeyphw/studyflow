import assert from "node:assert/strict";
import { StudyFlowWebPeerPersistence, decodeStudyFlowPairingQr } from "../runtime/sflp_link.js";
class MemoryStorage { constructor(){this.values=new Map()} getItem(k){return this.values.get(k)??null} setItem(k,v){this.values.set(k,String(v))} removeItem(k){this.values.delete(k)} }
const host={deviceId:"desktop-secure",displayName:"Desktop",clientKind:"desktop",protocolSupport:{major:1,minMinor:0,maxMinor:0},capabilities:["workspace.sync"]};
const payload={version:2,pairingId:"pair-secure",host,sessionNonce:"a".repeat(48),pairingCode:"123456",createdAtEpochMillis:1000,expiresAtEpochMillis:2000,endpointHints:["wss://studyflow.local/sflp"],hostKeyAgreementPublicKeyHex:"ab".repeat(80)};
const qr=`studyflow://pair?data=${encodeURIComponent(JSON.stringify(payload))}`;
assert.equal(decodeStudyFlowPairingQr(qr,1500).version,2);
const bad={...payload,expiresAtEpochMillis:payload.createdAtEpochMillis}; const badQr=`studyflow://pair?data=${encodeURIComponent(JSON.stringify(bad))}`; assert.throws(()=>decodeStudyFlowPairingQr(badQr),/expiry/);
const storage=new MemoryStorage(); const persistence=new StudyFlowWebPeerPersistence(storage);
const snapshot=persistence.load("web"); snapshot.trustedPeers.push({peer:host,state:"trusted",pairedAtEpochMillis:1,updatedAtEpochMillis:1,lastSeenAtEpochMillis:null,revokedAtEpochMillis:null,trustKeyId:"opaque-key-id"}); persistence.save(snapshot);
const encoded=storage.getItem("studyflow.sflp.web.peer.v1"); assert.ok(encoded.includes("opaque-key-id")); assert.ok(!encoded.includes("trustSecret"),"localStorage must contain no raw trust-secret field");
const web=persistence.load("web"); const tv=persistence.load("web_tv"); assert.notEqual(web.identity.deviceId,tv.identity.deviceId); assert.notEqual(web.identity.clientKind,tv.identity.clientKind);
console.log("OV10 web security: PASS");
