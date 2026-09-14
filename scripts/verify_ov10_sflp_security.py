#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
checks={
'S4-12/S12-01 encrypted ECDH pairing':('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPairing.kt',['PairingQrVersion = 2','KeyAgreement.getInstance("ECDH")','AES/GCM/NoPadding','encryptedTrustSecretHex']),
'S4-14 atomic single use':('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPairing.kt',['synchronized(activeOffers)','activeOffers.remove(request.pairingId)']),
'S4-13 capability authorization':('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt',['WorkspaceSync','WorkspaceWrite','UnsupportedCapability']),
'S4-17 protocol enforcement':('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowPeerRpc.kt',['CurrentSupport.contains(request.protocol)','IncompatibleProtocol']),
'DS2-08 retryable semantics':('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt',['remoteError.retryable','TERMINAL_FAILURE']),
'DS2-17 fail closed randomness':('web/src/sflp_link.ts',['Secure Web Crypto randomness is required','requireSecureRandom().getRandomValues']),
'S12-07 no raw localStorage secret':('web/src/sflp_link.ts',['trustKeyId:string|null','StudyFlowWebCryptoTrustKeyStore','legacy.trustSecret']),
'DS2-18 identity partition':('web/src/sflp_link.ts',['WEB_STORAGE_KEY','WEB_TV_STORAGE_KEY']),
'DS2-09/19 envelope and expiry':('web/src/sflp_link.ts',['Invalid native SFLP pairing response envelope','Pairing QR is expired or not yet valid']),
}
for label,(rel,tokens) in checks.items():
 text=(ROOT/rel).read_text()
 missing=[t for t in tokens if t not in text]
 if missing: raise SystemExit(f'OV10 FAIL {label}: missing {missing}')
 print('PASS',label)
print(f'OV10 source evidence: PASS ({len(checks)}/{len(checks)})')
