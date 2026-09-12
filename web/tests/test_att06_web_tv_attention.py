import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class Att06WebTvAttentionTests(unittest.TestCase):
    def run_node(self, script: str) -> str:
        result = subprocess.run(['node', '-e', script], cwd=ROOT, text=True, capture_output=True, check=True)
        return result.stdout.strip()

    def test_web_tv_attention_capabilities_are_truthful_foreground_only(self):
        script = """
        const tv = require('./runtime/tv_attention.js');
        const caps = tv.webTvAttentionCapabilities({ notifications: true, serviceWorker: true });
        if (!caps.foregroundBanners || caps.systemNotifications || caps.backgroundAlarms || caps.closedBrowserDelivery) throw new Error(JSON.stringify(caps));
        if (!caps.designatedNotifierFallback || caps.deliveryMode !== 'foreground-only') throw new Error(JSON.stringify(caps));
        console.log('ok');
        """
        self.assertEqual(self.run_node(script), 'ok')

    def test_web_tv_projects_attention_entries_to_remote_safe_banners(self):
        script = """
        const tv = require('./runtime/tv_attention.js');
        const snapshot = tv.projectWebAttentionToTv({
          capabilities: { notifications: false, serviceWorker: false },
          entries: [{
            occurrence: { occurrenceId: 'occ-1', workspaceId: 'workspace', category: 'due_review', scheduledAtEpochMillis: 1, payload: { title: 'Review now', body: 'Law cards' } },
            state: 'delivered', updatedAtEpochMillis: 2, deliveryAttempts: 1
          }],
          badgeCount: 1,
          lastScanAtEpochMillis: 2
        });
        if (!snapshot.activeBanner || snapshot.activeBanner.routeHash !== '#reviews') throw new Error(JSON.stringify(snapshot));
        if (!snapshot.activeBanner.actions.includes('move_to_phone')) throw new Error(JSON.stringify(snapshot.activeBanner));
        console.log(snapshot.capabilities.deliveryMode);
        """
        self.assertEqual(self.run_node(script), 'foreground-only')

if __name__ == '__main__':
    unittest.main()
