from pathlib import Path
import subprocess
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"


class Presence01WebTests(unittest.TestCase):
    def test_web_presence_runtime_behavior(self):
        script = textwrap.dedent(
            r'''
            import { StudyFlowWebPresenceRuntime } from './runtime/presence.js';
            const protocolSupport={major:1,minMinor:0,maxMinor:0};
            const desktop={deviceId:'desktop-a',displayName:'Desktop A',clientKind:'desktop',protocolSupport,capabilities:['workspace.read','writing.edit','analytics.read']};
            const web={deviceId:'web-a',displayName:'Web A',clientKind:'web',protocolSupport,capabilities:['workspace.read','writing.edit']};
            const tv={deviceId:'tv-a',displayName:'TV A',clientKind:'web_tv',protocolSupport,capabilities:['workspace.read']};
            let now=1000;
            const a=new StudyFlowWebPresenceRuntime(desktop,()=>now,{staleAfterMillis:100,expiryMillis:300});
            const b=new StudyFlowWebPresenceRuntime(web,()=>now,{staleAfterMillis:100,expiryMillis:300});
            a.announce('workspace-1','online',{activityId:'draft-1',kind:'writing_draft',positionToken:'p4',continuityMode:'snapshot',requiredCapabilities:['writing.edit']});
            const outbound=a.exchangeForPeer('workspace-1',web,now);
            if(outbound.records.length!==1)throw new Error('expected one presence record');
            if(b.acceptExchange('workspace-1','desktop-a',outbound,new Set(['desktop-a']),now)[0]!=='accepted')throw new Error('presence not accepted');
            if(b.views('workspace-1',web,now)[0]?.activityHint?.activityId!=='draft-1')throw new Error('capable viewer lost activity hint');
            if(b.views('workspace-1',tv,now)[0]?.activityHint!==undefined)throw new Error('incapable viewer saw activity hint');
            if(b.recordsForPeer('workspace-1',desktop,now).length!==0)throw new Error('presence echoed to visited peer');
            now=1101;
            if(b.views('workspace-1',web,now)[0]?.status!=='stale')throw new Error('stale threshold not applied');
            now=1301;
            if(b.views('workspace-1',web,now).length!==0)throw new Error('expired presence retained');
            const restarted=new StudyFlowWebPresenceRuntime(desktop,()=>now,{staleAfterMillis:100,expiryMillis:300});
            restarted.announce('workspace-1','away');
            const fresh=restarted.exchangeForPeer('workspace-1',web,now);
            if(b.acceptExchange('workspace-1','desktop-a',fresh,new Set(['desktop-a']),now)[0]!=='accepted')throw new Error('reconnect session rejected');
            if(b.views('workspace-1',web,now)[0]?.status!=='away')throw new Error('away state lost');
            console.log('PRESENCE01_WEB_RUNTIME: PASS');
            '''
        )
        result = subprocess.run(
            ["node", "--input-type=module", "-e", script],
            cwd=WEB,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("PRESENCE01_WEB_RUNTIME: PASS", result.stdout)

    def test_presence_is_ephemeral_and_web_peer_exposes_it(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        presence = (WEB / "src" / "presence.ts").read_text(encoding="utf-8")
        for token in [
            "readonly presence:StudyFlowWebPresenceRuntime",
            "updatePresence(",
            "presenceViews(",
            "presenceExchangeFor(",
            "acceptPresenceExchange(",
        ]:
            self.assertIn(token, source)
        self.assertNotIn("StudyFlowPresenceRecord[];journal", source)
        for token in ["staleAfterMillis", "expiryMillis", "visitedDeviceIds", "visibilityCapabilities", "requiredCapabilities"]:
            self.assertIn(token, presence)


if __name__ == "__main__":
    unittest.main()
