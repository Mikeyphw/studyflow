import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent


class Att12AttentionReleaseSealTests(unittest.TestCase):
    def test_web_tv_attention_is_reachable_from_production_boot(self):
        main = (ROOT / "src/main.tsx").read_text(encoding="utf-8")
        app = (ROOT / "src/react/App.tsx").read_text(encoding="utf-8")
        self.assertIn("new StudyFlowWebAttentionRuntime", main)
        self.assertIn("new StudyFlowWebTvAttentionRuntime", main)
        self.assertIn("installStudyFlowWebTvAttentionBridge", main)
        self.assertIn("WebTvAttentionBanner", app)
        self.assertIn('data-att12-tv-attention="foreground-only"', app)

    def test_web_tv_never_claims_system_notification_ownership(self):
        attention = (ROOT / "src/attention.ts").read_text(encoding="utf-8")
        self.assertIn('this.peer.identity.clientKind === "web_tv"', attention)
        self.assertIn('this.peer.identity.clientKind !== "web_tv"', attention)
        self.assertIn('"foreground_banner" as const', attention)
        self.assertIn("designatedNotifier: !webTv", attention)

    def test_delivery_edge_enforces_att07_target_ids(self):
        result = subprocess.run(
            [
                "node",
                "-e",
                """
                const r = require('./runtime/attention_router.js');
                const f = r.studyFlowAttentionRouteTargetsDevice;
                if (!f({deliverable:true,targetDeviceIds:['web-local']}, 'web-local')) process.exit(1);
                if (f({deliverable:true,targetDeviceIds:['other']}, 'web-local')) process.exit(2);
                if (f({deliverable:false,targetDeviceIds:['web-local']}, 'web-local')) process.exit(3);
                console.log('ATT12 delivery-edge target enforcement passed');
                """,
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("ATT12 delivery-edge target enforcement passed", result.stdout)

    def test_android_and_desktop_delivery_edges_enforce_router_targets(self):
        android_bridge = (REPO / "app/src/main/java/com/mikeyphw/studyflow/reminders/AndroidStudyFlowAttentionRouterBridge.kt").read_text(encoding="utf-8")
        android_delivery = (REPO / "app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt").read_text(encoding="utf-8")
        desktop_bridge = (REPO / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionDeliveryRouterBridge.kt").read_text(encoding="utf-8")
        desktop_runtime = (REPO / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopAttentionRuntime.kt").read_text(encoding="utf-8")
        self.assertIn("route.deliverable && localDeviceId in route.targetDeviceIds", android_bridge)
        self.assertIn("AndroidStudyFlowAttentionRouterBridge.isLocalTarget(route)", android_delivery)
        self.assertIn("route.deliverable && localDeviceId in route.targetDeviceIds", desktop_bridge)
        self.assertIn("DesktopAttentionDeliveryRouterBridge.isLocalTarget(route)", desktop_runtime)

    def test_capability_matrix_contains_final_truth_without_stale_att_claims(self):
        matrix = (REPO / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md").read_text(encoding="utf-8")
        self.assertIn("Native reminder/background delivery", matrix)
        self.assertIn("ATT06 TV foreground-attention truth", matrix)
        self.assertIn("ATT12 final Attention release-seal truth", matrix)
        self.assertNotIn("Web/TV equivalents remain later roadmap work", matrix)
        self.assertNotIn("FULL (ATT05 foreground + optional Push foundation)", matrix)


if __name__ == "__main__":
    unittest.main()
