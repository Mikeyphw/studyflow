from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"

class Sflp10WebLinkTests(unittest.TestCase):
    def test_web_peer_source_contracts(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "StudyFlowWebLinkPeer",
            "StudyFlowWebPeerPersistence",
            "StudyFlowWebSocketTransportAdapter",
            "StudyFlowWebManualDiscovery",
            "studyflow.sflp.web.peer.v1",
            "workspace.sync",
            "device.presentation_sink",
            "pairFromQr",
            "rememberManualEndpoint",
            "operationsAfter",
            "frontier",
            "acknowledge",
            "revoke",
            "forget",
        ]:
            self.assertIn(token, source)

    def test_runtime_is_committed_and_offline_cached(self):
        self.assertTrue((WEB / "runtime" / "sflp_link.js").is_file())
        self.assertTrue((WEB / "dist" / "runtime" / "sflp_link.js").is_file())
        service_worker = (WEB / "service-worker.js").read_text(encoding="utf-8")
        self.assertIn("./runtime/sflp_link.js", service_worker)
        dist_service_worker = (WEB / "dist" / "service-worker.js").read_text(encoding="utf-8")
        self.assertIn("./runtime/sflp_link.js", dist_service_worker)
        result = subprocess.run(
            ["node", "--check", str(WEB / "runtime" / "sflp_link.js")],
            cwd=WEB,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)

if __name__ == "__main__":
    unittest.main()
