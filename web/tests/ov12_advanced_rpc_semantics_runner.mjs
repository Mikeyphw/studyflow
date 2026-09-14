import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const link = require('../runtime/sflp_link.js')

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function storage() {
  return { data: {}, getItem(k) { return this.data[k] || null }, setItem(k, v) { this.data[k] = v }, removeItem(k) { delete this.data[k] } }
}

function bytes(text) {
  return new TextEncoder().encode(text)
}

function hash(text) {
  return link.StudyFlowWebHashing.sha256HexBytes(bytes(text))
}

function b64(text) {
  return link.StudyFlowWebHashing.utf8Base64(text)
}

const peer = new link.StudyFlowWebLinkPeer(new link.StudyFlowWebPeerPersistence(storage()), 'web')
const tvPeer = new link.StudyFlowWebLinkPeer(new link.StudyFlowWebPeerPersistence(storage()), 'web_tv')
const desktop = { deviceId: 'desktop-ov12', displayName: 'Desktop', clientKind: 'desktop', protocolSupport: { major: 1, minMinor: 0, maxMinor: 0 }, capabilities: ['workspace.read', 'device.presentation_source', 'library.read'] }

// DS2-20/S4-16: live events are fenced to the active session/workspace/activity and executor.
const live = new link.StudyFlowWebLiveSessionContinuityController({
  sessionId: 'session-ov12', workspaceId: 'workspace-ov12', activityId: 'activity-ov12',
  state: 'running', activeExecutorDeviceId: 'executor-ov12', fencingToken: 'fence-ov12',
  sessionEpoch: 1, lastSequence: 1,
  cursor: { activityId: 'activity-ov12', positionToken: 'q1', elapsedMillis: 0, answeredCount: 0 },
})
const baseEvent = { eventId: 'event-2', sessionId: 'session-ov12', workspaceId: 'workspace-ov12', sourceDeviceId: 'executor-ov12', sessionEpoch: 1, sequence: 2, kind: 'cursor', cursor: { activityId: 'activity-ov12', positionToken: 'q2', elapsedMillis: 1, answeredCount: 0 }, fencingToken: 'fence-ov12' }
assert(live.applyEvent({ ...baseEvent, eventId: 'wrong-session', sessionId: 'other-session' }) === 'session_mismatch', 'wrong live session must be rejected')
assert(live.applyEvent({ ...baseEvent, eventId: 'wrong-workspace', workspaceId: 'other-workspace' }) === 'workspace_mismatch', 'wrong live workspace must be rejected')
assert(live.applyEvent({ ...baseEvent, eventId: 'wrong-activity', cursor: { ...baseEvent.cursor, activityId: 'other-activity' } }) === 'activity_mismatch', 'wrong live activity must be rejected')
assert(live.applyEvent({ ...baseEvent, eventId: 'wrong-fence', fencingToken: 'bad-fence' }) === 'fencing_token_mismatch', 'wrong live fencing token must be rejected')
assert(live.applyEvent({ ...baseEvent, eventId: 'wrong-executor', sourceDeviceId: 'controller-ov12' }) === 'executor_mismatch', 'wrong live executor must be rejected')
assert(live.applyEvent(baseEvent) === 'applied', 'valid live event must apply')

// DS2-21: projection frames must match the accepted offer source, workspace, content hash, and monotonic ordering.
const projection = new link.StudyFlowWebPresentationProjectionController(tvPeer)
const offer = { projectionId: 'projection-ov12', workspaceId: 'workspace-ov12', sourceDevice: desktop, targetDeviceId: tvPeer.identity.deviceId, contentKind: 'document', title: 'Doc', contentHash: 'a'.repeat(64), requiredCapabilities: ['workspace.read', 'device.presentation_sink', 'video.output'], createdAtEpochMillis: 0, expiresAtEpochMillis: Date.now() + 10000 }
assert(projection.acceptProjectionOffer(offer), 'projection offer should be accepted')
const frame = { projectionId: 'projection-ov12', frameId: 'frame-1', workspaceId: 'workspace-ov12', sourceDeviceId: desktop.deviceId, sequence: 1, contentRevision: 1, contentHash: 'a'.repeat(64), cursor: { positionToken: 'slide-1' } }
assert(!projection.applyProjectionFrame({ ...frame, frameId: 'frame-wrong-source', sourceDeviceId: 'phone-ov12' }), 'wrong projection source must reject')
assert(!projection.applyProjectionFrame({ ...frame, frameId: 'frame-wrong-workspace', workspaceId: 'other-workspace' }), 'wrong projection workspace must reject')
assert(!projection.applyProjectionFrame({ ...frame, frameId: 'frame-wrong-hash', contentHash: 'b'.repeat(64) }), 'wrong projection hash must reject')
assert(projection.applyProjectionFrame(frame), 'valid projection frame must apply')
assert(!projection.applyProjectionFrame({ ...frame, frameId: 'frame-stale', sequence: 1 }), 'stale projection frame must reject')

// DS2-22/DS2-23: Web-TV input uses the core reducer semantics rather than target-only command acceptance.
const tv = new link.StudyFlowWebTvConnectivityController(tvPeer)
assert(!tv.acceptRemoteInput({ commandId: 'cmd-legacy', sourceDeviceId: peer.identity.deviceId, targetDeviceId: tvPeer.identity.deviceId, kind: 'select', issuedAtEpochMillis: Date.now() }), 'legacy target-only remote input must reject')
tv.rememberControlLease({ sessionId: 'session-ov12', workspaceId: 'workspace-ov12', controllerDeviceId: peer.identity.deviceId, targetDeviceId: tvPeer.identity.deviceId, leaseToken: 'lease-ov12', grantedAtEpochMillis: 1, expiresAtEpochMillis: Date.now() + 10000, allowedCommands: ['select', 'back'] })
assert(!tv.acceptRemoteInput({ commandId: 'cmd-bad-token', sourceDeviceId: peer.identity.deviceId, targetDeviceId: tvPeer.identity.deviceId, kind: 'select', issuedAtEpochMillis: Date.now(), sessionId: 'session-ov12', workspaceId: 'workspace-ov12', leaseToken: 'bad', sequence: 1 }), 'remote input with a bad lease token must reject')
assert(tv.acceptRemoteInput({ commandId: 'cmd-ok', sourceDeviceId: peer.identity.deviceId, targetDeviceId: tvPeer.identity.deviceId, kind: 'select', issuedAtEpochMillis: Date.now(), sessionId: 'session-ov12', workspaceId: 'workspace-ov12', leaseToken: 'lease-ov12', sequence: 1 }), 'lease-bound remote input must accept')
assert(!tv.acceptRemoteInput({ commandId: 'cmd-stale', sourceDeviceId: peer.identity.deviceId, targetDeviceId: tvPeer.identity.deviceId, kind: 'select', issuedAtEpochMillis: Date.now(), sessionId: 'session-ov12', workspaceId: 'workspace-ov12', leaseToken: 'lease-ov12', sequence: 1 }), 'stale remote input sequence must reject')

// S4-11/DS2-24: material streaming validates chunk bytes, chunk hashes, order, offsets, and aggregate content hash.
const media = new link.StudyFlowNotificationMediaRuntime({ deviceId: tvPeer.identity.deviceId, capabilities: tvPeer.identity.capabilities })
const materialOffer = { streamId: 'stream-ov12', workspaceId: 'workspace-ov12', materialId: 'material-ov12', sourceDeviceId: desktop.deviceId, targetDeviceId: tvPeer.identity.deviceId, title: 'Material', mimeType: 'application/pdf', totalBytes: 6, chunkSize: 3, contentSha256: hash('ABCDEF'), requiredCapabilities: ['library.read', 'document.present'], expiresAtEpochMillis: Date.now() + 10000 }
assert(media.acceptMaterialStream(materialOffer), 'material offer should be accepted')
assert(media.applyMaterialChunk({ streamId: 'stream-ov12', sourceDeviceId: desktop.deviceId, targetDeviceId: tvPeer.identity.deviceId, chunkIndex: 1, byteOffset: 3, bytesBase64: b64('DEF'), chunkSha256: hash('DEF'), finalChunk: true }).reason === 'out_of_order_chunk', 'early final chunk must reject')
assert(media.applyMaterialChunk({ streamId: 'stream-ov12', sourceDeviceId: desktop.deviceId, targetDeviceId: tvPeer.identity.deviceId, chunkIndex: 0, byteOffset: 0, bytesBase64: b64('ABC'), chunkSha256: hash('ABC') }).status === 'accepted', 'first material chunk should be accepted')
assert(media.applyMaterialChunk({ streamId: 'stream-ov12', sourceDeviceId: desktop.deviceId, targetDeviceId: tvPeer.identity.deviceId, chunkIndex: 1, byteOffset: 3, bytesBase64: b64('DEF'), chunkSha256: hash('BAD'), finalChunk: true }).reason === 'chunk_hash_mismatch', 'bad chunk hash must reject')
assert(media.applyMaterialChunk({ streamId: 'stream-ov12', sourceDeviceId: desktop.deviceId, targetDeviceId: tvPeer.identity.deviceId, chunkIndex: 1, byteOffset: 3, bytesBase64: b64('DEF'), chunkSha256: hash('DEF'), finalChunk: true }).status === 'complete', 'ordered verified stream should complete')

// S4-20/DS2-25: share/import requires complete content, correct target, source and hash for write deliveries.
const importPeer = { deviceId: 'web-import-ov12', clientKind: 'web', capabilities: ['share.receive', 'library.import'] }
const share = new link.StudyFlowShareImportConflictRuntime(importPeer)
const shareOffer = (id, content, opts = {}) => ({ importId: id, workspaceId: 'workspace-ov12', sourceDevice: desktop, targetDeviceId: importPeer.deviceId, intent: 'share_import', payloadKind: 'markdown', title: 'Shared note', mimeType: 'text/markdown', previewText: content.slice(0, 100), contentSha256: hash(content), contentBytesBase64: opts.omitBytes ? undefined : b64(opts.bytes ?? content), requiredCapabilities: ['share.receive', 'library.import'], createdAtEpochMillis: 0, expiresAtEpochMillis: Date.now() + 10000 })
assert(share.applyShareImportDelivery({ sourceDeviceId: desktop.deviceId, targetDeviceId: importPeer.deviceId, sequence: 1, offer: shareOffer('import-missing', '# Note', { omitBytes: true }) }).reason === 'missing_content_bytes', 'write import without complete content must reject')
assert(share.applyShareImportDelivery({ sourceDeviceId: 'phone-ov12', targetDeviceId: importPeer.deviceId, sequence: 2, offer: shareOffer('import-wrong-source', '# Note') }).reason === 'wrong_source', 'wrong delivery source must reject')
assert(share.applyShareImportDelivery({ sourceDeviceId: desktop.deviceId, targetDeviceId: importPeer.deviceId, sequence: 3, offer: { ...shareOffer('import-bad-hash', '# Note'), contentBytesBase64: b64('tampered') } }).reason === 'content_hash_mismatch', 'tampered complete content must reject')
assert(share.applyShareImportDelivery({ sourceDeviceId: desktop.deviceId, targetDeviceId: importPeer.deviceId, sequence: 4, offer: shareOffer('import-ok', '# Note') }).status === 'accepted', 'verified full share content must accept')

// DS2-26: handoff/restore plans cannot smuggle a different inner target.
const account = new link.StudyFlowAccountBackupReleaseRuntime({ identity: peer.identity })
const handoffOffer = { handoffId: 'handoff-ov12', workspaceId: 'workspace-ov12', targetDeviceId: 'other-device', purpose: 'device_replacement', requiredCapabilities: ['workspace.sync', 'backup.import'], createdAtEpochMillis: 0, expiresAtEpochMillis: Date.now() + 10000 }
assert(account.applyAccountHandoff({ targetDeviceId: peer.identity.deviceId, sequence: 1, offer: handoffOffer }).reason === 'inner_target_mismatch', 'account handoff inner target substitution must reject')
const bundle = { bundleId: 'bundle-ov12', workspaceId: 'workspace-ov12', targetDeviceId: 'other-device', totalBytes: 10, itemCount: 1, contentSha256: hash('backup'), requiredRestoreCapabilities: ['backup.import'], createdAtEpochMillis: 0, expiresAtEpochMillis: Date.now() + 10000 }
assert(account.acceptBackupRestore({ restoreId: 'restore-ov12', targetDeviceId: peer.identity.deviceId, mode: 'restore', sequence: 1, bundle }).reason === 'inner_target_mismatch', 'backup restore inner target substitution must reject')

console.log('OV12_ADVANCED_RPC_SEMANTICS: PASS')
