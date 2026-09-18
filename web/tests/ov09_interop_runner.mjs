import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import {
  StudyFlowWebLinkPeer,
  StudyFlowWebPeerPersistence,
  StudyFlowWebSocketTransportAdapter,
  decodeStudyFlowPairingQr,
} from "../runtime/sflp_link.js";

if (!globalThis.crypto) {
  Object.defineProperty(globalThis, "crypto", { value: webcrypto });
}

const subtle = globalThis.crypto.subtle;
const TRUST_SECRET = "b".repeat(64);
const PAIRING_IV = new Uint8Array(12).fill(0x5a);

function bytesHex(value) {
  const bytes = value instanceof ArrayBuffer
    ? new Uint8Array(value)
    : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  return [...bytes].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

function hexBytes(value) {
  assert.match(value, /^(?:[a-f0-9]{2})+$/);
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i += 1) {
    bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

function utf8(value) {
  return new TextEncoder().encode(value);
}

function concatBytes(...parts) {
  const output = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
  let offset = 0;
  for (const part of parts) {
    output.set(part, offset);
    offset += part.length;
  }
  return output;
}

function pairingAad(parsed, peerDeviceId) {
  return utf8([
    "SFLP-PAIRING-V2",
    parsed.pairingId,
    parsed.host.deviceId,
    peerDeviceId,
    parsed.sessionNonce,
  ].join("|"));
}

async function deriveHostPairingKey(hostPrivateKey, clientPublicKeyHex, parsed, peerDeviceId) {
  const clientPublicKey = await subtle.importKey(
    "spki",
    hexBytes(clientPublicKeyHex),
    { name: "ECDH", namedCurve: "P-256" },
    false,
    [],
  );
  const shared = new Uint8Array(await subtle.deriveBits(
    { name: "ECDH", public: clientPublicKey },
    hostPrivateKey,
    256,
  ));
  const salt = new Uint8Array(await subtle.digest("SHA-256", utf8(parsed.sessionNonce)));
  const saltKey = await subtle.importKey("raw", salt, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const prk = new Uint8Array(await subtle.sign("HMAC", saltKey, shared));
  const prkKey = await subtle.importKey("raw", prk, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const okm = await subtle.sign(
    "HMAC",
    prkKey,
    concatBytes(pairingAad(parsed, peerDeviceId), new Uint8Array([1])),
  );
  return subtle.importKey("raw", okm, { name: "AES-GCM" }, false, ["encrypt"]);
}

async function encryptedPairingGrant(hostPrivateKey, request, parsed) {
  const key = await deriveHostPairingKey(
    hostPrivateKey,
    request.payload.clientKeyAgreementPublicKeyHex,
    parsed,
    request.senderDeviceId,
  );
  const encrypted = await subtle.encrypt(
    {
      name: "AES-GCM",
      iv: PAIRING_IV,
      additionalData: pairingAad(parsed, request.senderDeviceId),
      tagLength: 128,
    },
    key,
    utf8(TRUST_SECRET),
  );
  return {
    pairingId: parsed.pairingId,
    host: parsed.host,
    peerDeviceId: request.senderDeviceId,
    pairedAtEpochMillis: 20_000,
    hostKeyAgreementPublicKeyHex: parsed.hostKeyAgreementPublicKeyHex,
    initializationVectorHex: bytesHex(PAIRING_IV),
    encryptedTrustSecretHex: bytesHex(encrypted),
  };
}

async function expectedReconnectProof(challenge) {
  const key = await subtle.importKey(
    "raw",
    hexBytes(TRUST_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const transcript = [
    "SFLP-RECONNECT-V1",
    challenge.challengerDeviceId,
    challenge.peerDeviceId,
    challenge.nonce,
    String(challenge.expiresAtEpochMillis),
  ].join("|");
  return bytesHex(await subtle.sign("HMAC", key, utf8(transcript)));
}

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
const hostKeyPair = await subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
const pairing={
  version:2,
  createdAtEpochMillis:1,
  expiresAtEpochMillis:9999999999999,
  host,
  hostKeyAgreementPublicKeyHex:bytesHex(await subtle.exportKey("spki", hostKeyPair.publicKey)),
  pairingId:"pair-ov09-native",
  sessionNonce:"a".repeat(48),
  pairingCode:"123456",
  endpointHints:["ws://127.0.0.1:8765/sflp","sflp+tcp://127.0.0.1:8764"],
};
const qr=`studyflow://pair?data=${encodeURIComponent(JSON.stringify(pairing))}`;
assert.deepEqual(decodeStudyFlowPairingQr(qr),pairing);

class FakeTransport extends StudyFlowWebSocketTransportAdapter {
  constructor(){ super(); this.requests=[]; this.webDeviceId=null; this.lastChallenge=null; }
  async connect(endpoint){
    assert.equal(endpoint.kind,"websocket");
    return {
      exchange: async request => {
        this.requests.push(request);
        if(request.name==="link.pair"){
          this.webDeviceId=request.senderDeviceId;
          assert.equal(request.payload.pairingId,pairing.pairingId);
          assert.equal(request.payload.sessionNonce,pairing.sessionNonce);
          assert.equal(request.payload.pairingCode,pairing.pairingCode);
          assert.match(request.payload.clientKeyAgreementPublicKeyHex,/^[a-f0-9]{160,}$/);
          return response(
            request,
            "link.pairing_grant",
            await encryptedPairingGrant(hostKeyPair.privateKey,request,pairing),
          );
        }
        if(request.name==="link.reconnect_challenge"){
          this.lastChallenge={
            challengerDeviceId:host.deviceId,
            peerDeviceId:request.senderDeviceId,
            nonce:"c".repeat(48),
            issuedAtEpochMillis:20_100,
            expiresAtEpochMillis:Date.now()+60_000,
          };
          return response(request,"link.reconnect_challenge",this.lastChallenge);
        }
        if(request.name==="link.reconnect_proof"){
          assert.equal(request.payload.proverDeviceId,this.webDeviceId);
          assert.equal(request.payload.nonce,this.lastChallenge.nonce);
          assert.equal(request.payload.proofHex,await expectedReconnectProof(this.lastChallenge));
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
assert.match(record.trustKeyId,/^sflp-trust-desktop-ov09-host-[a-f0-9]{16}$/);
assert.equal("trustSecret" in record,false,"Trusted peer records must expose only an opaque trust-key ID");
const persistedWebIdentity=storage.getItem("studyflow.sflp.web.peer.v1");
assert.ok(persistedWebIdentity.includes(record.trustKeyId));
assert.ok(!persistedWebIdentity.includes(TRUST_SECRET),"localStorage must not persist the raw host trust secret");
assert.ok(!persistedWebIdentity.includes("trustSecret"),"localStorage must not persist a raw trust-secret field");
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
