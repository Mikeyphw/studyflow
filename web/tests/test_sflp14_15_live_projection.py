from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Sflp14Sflp15WebRuntimeTests(unittest.TestCase):
    def test_source_contracts(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "StudyFlowWebLiveSessionContinuityController",
            "StudyFlowWebPresentationProjectionController",
            "session.live_cursor",
            "presentation.projection_control",
            "acknowledgeProjectionFrame",
            "createProjectionOffer",
            "acceptProjectionOffer",
            "applyProjectionFrame",
        ]:
            self.assertIn(token, source)

    def test_runtime_exports_and_behaviour(self):
        runtime = WEB / "runtime" / "sflp_link.js"
        dist = WEB / "dist" / "runtime" / "sflp_link.js"
        self.assertEqual(runtime.read_bytes(), dist.read_bytes())
        syntax = subprocess.run(["node", "--check", str(runtime)], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(syntax.returncode, 0, syntax.stdout)
        probe = """
        const link = require('./runtime/sflp_link.js');
        const storage = {data:{}, getItem(k){return this.data[k]||null}, setItem(k,v){this.data[k]=v}, removeItem(k){delete this.data[k]}};
        const peer = new link.StudyFlowWebLinkPeer(new link.StudyFlowWebPeerPersistence(storage), 'web_tv');
        const live = new link.StudyFlowWebLiveSessionContinuityController({sessionId:'session-live',workspaceId:'workspace-live',activityId:'activity-live',state:'running',activeExecutorDeviceId:'web-source',fencingToken:'fence-web',sessionEpoch:1,lastSequence:1,cursor:{activityId:'activity-live',positionToken:'q1',elapsedMillis:0,answeredCount:0}});
        if (live.applyEvent({eventId:'e2',sessionId:'session-live',workspaceId:'workspace-live',sourceDeviceId:'web-source',sessionEpoch:1,sequence:2,kind:'paused',cursor:{activityId:'activity-live',positionToken:'q1',elapsedMillis:10,answeredCount:0},fencingToken:'fence-web'}) !== 'applied') process.exit(2);
        if (live.applyEvent({eventId:'e2',sessionId:'session-live',workspaceId:'workspace-live',sourceDeviceId:'web-source',sessionEpoch:1,sequence:2,kind:'paused',cursor:{activityId:'activity-live',positionToken:'q1',elapsedMillis:10,answeredCount:0},fencingToken:'fence-web'}) !== 'duplicate') process.exit(3);
        const projection = new link.StudyFlowWebPresentationProjectionController(peer);
        const offer = {projectionId:'projection-web',workspaceId:'workspace-live',sourceDevice:{deviceId:'desktop',displayName:'Desktop',clientKind:'desktop',protocolSupport:{major:1,minMinor:0,maxMinor:0},capabilities:['device.presentation_source']},targetDeviceId:peer.identity.deviceId,contentKind:'document',title:'Doc',contentHash:'a'.repeat(64),requiredCapabilities:['workspace.read','device.presentation_sink','video.output'],createdAtEpochMillis:0,expiresAtEpochMillis:Date.now()+10000};
        if (!projection.acceptProjectionOffer(offer)) process.exit(4);
        if (!projection.applyProjectionFrame({projectionId:'projection-web',frameId:'frame-1',workspaceId:'workspace-live',sourceDeviceId:'desktop',sequence:1,contentRevision:1,contentHash:'a'.repeat(64),cursor:{positionToken:'slide-1'}})) process.exit(5);
        if (!projection.acknowledgeProjectionFrame('projection-web')) process.exit(6);
        """
        run = subprocess.run(["node", "-e", probe], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(run.returncode, 0, run.stdout)

if __name__ == "__main__":
    unittest.main()
