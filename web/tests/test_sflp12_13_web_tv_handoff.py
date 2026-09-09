from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Sflp12Sflp13WebTvHandoffTests(unittest.TestCase):
    def test_source_contract_tokens(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "StudyFlowWebTvConnectivityController",
            "studyflow.sflp.web_tv.receiver.v1",
            "StudyFlowWebActivityHandoffController",
            "studyflow.sflp.activity_handoff.v1",
            "presentation_projection",
            "presentation_sink",
            "acceptRemoteInput",
            "acceptProjection",
            "createOffer",
            "acceptOffer",
            "resumePayload",
        ]:
            self.assertIn(token, source)

    def test_runtime_exports_and_node_syntax(self):
        runtime = WEB / "runtime" / "sflp_link.js"
        dist = WEB / "dist" / "runtime" / "sflp_link.js"
        self.assertEqual(runtime.read_bytes(), dist.read_bytes())
        result = subprocess.run(["node", "--check", str(runtime)], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(result.returncode, 0, result.stdout)
        probe = """
        const link = require('./runtime/sflp_link.js');
        const storage = {data:{}, getItem(k){return this.data[k]||null}, setItem(k,v){this.data[k]=v}, removeItem(k){delete this.data[k]}};
        const peer = new link.StudyFlowWebLinkPeer(new link.StudyFlowWebPeerPersistence(storage), 'web_tv');
        const tv = new link.StudyFlowWebTvConnectivityController(peer);
        const offer = tv.createReceiverOffer('workspace-web-tv', ['wss://studyflow.local/sflp']);
        if (!offer.qrPayload.includes('studyflow.sflp.web_tv.receiver.v1')) process.exit(2);
        const handoff = new link.StudyFlowWebActivityHandoffController(peer);
        const snapshot = {activityId:'activity-1',workspaceId:'workspace-web-tv',kind:'presentation_projection',cursor:{positionToken:'slide-1'},continuityMode:'presentation_sink',requiredCapabilities:['device.presentation_sink','dpad.input'],payloadHash:'0'.repeat(64),payload:{}};
        if (!tv.acceptProjection(snapshot)) process.exit(3);
        const incoming = handoff.createOffer(peer.identity.deviceId, snapshot);
        if (!handoff.acceptOffer(incoming)) process.exit(4);
        if (handoff.acceptOffer(incoming)) process.exit(5);
        """
        run = subprocess.run(["node", "-e", probe], cwd=WEB, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(run.returncode, 0, run.stdout)

if __name__ == "__main__":
    unittest.main()
