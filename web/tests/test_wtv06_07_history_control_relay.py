from pathlib import Path
import json
import socket
import subprocess
import textwrap
import time
import unittest
import urllib.error
import urllib.request

WEB = Path(__file__).resolve().parents[1]
ROOT = WEB.parent
APP = (WEB / "src/react/App.tsx").read_text()
TV = (WEB / "src/tv.ts").read_text()
SERVER = (WEB / "scripts/serve_tv.mjs").read_text()
RELAY = (WEB / "scripts/tv_relay.mjs").read_text()
ROADMAP = (ROOT / "docs/web/WEB_ROADMAP.md").read_text()
CORE = (ROOT / "core/tvcontrol/src/main/kotlin/com/mikeyphw/studyflow/core/tvcontrol/StudyFlowTvControlProtocol.kt").read_text()


def request_json(url, method="GET", body=None, token=None, timeout=3):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.status, json.loads(response.read().decode())


class Wtv0607HistoryControlRelayTest(unittest.TestCase):
    def test_history_runtime_and_tv_back_contract(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const h=require('./runtime/tv_history.js');
            assert.equal(h.screenFromHash('#practice'),'practice');
            assert.equal(h.screenFromHash('#unknown'),'today');
            const state=h.tvHistoryState({version:1,screen:'plan',overlay:'material',overlayId:'mat-1'});
            assert.deepEqual(h.readTvHistoryEntry(state,'#today'),{version:1,screen:'plan',overlay:'material',overlayId:'mat-1'});
            assert.deepEqual(h.readTvHistoryEntry({},'#progress'),{version:1,screen:'progress',overlay:null,overlayId:null});
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        for token in (
            'pushTvHistoryEntry(entry)',
            'replaceTvHistoryEntry(entry)',
            'addEventListener("popstate"',
            'history.back()',
            'overlay==="material"',
            'onTvMaterialClose={closeTvOverlay}',
        ):
            self.assertIn(token, APP)
        self.assertIn('installRemoteBackNavigation', TV)
        self.assertIn('code===461||code===10009', TV)

    def test_browser_protocol_matches_core_v1_contract(self):
        script = textwrap.dedent(
            r'''
            const assert=require('node:assert/strict');
            const c=require('./runtime/tv_control.js');
            assert.equal(c.TV_CONTROL_PROTOCOL_VERSION,1);
            assert.equal(c.TV_PAIRING_CODE_LENGTH,6);
            assert.equal(c.TV_PAIRING_TTL_SECONDS,120);
            const cmd={commandId:'c1',protocolVersion:1,surface:'QUESTION_SET',action:'SHOW_EXPLANATION',issuedAtEpochMillis:1,requiresUserPresence:true};
            assert.equal(c.isStudyFlowTvControlCommand(cmd),true);
            assert.equal(c.isStudyFlowTvControlCommand({...cmd,requiresUserPresence:false}),false);
            const cast={castId:'x',protocolVersion:1,surface:'PROGRESS',title:'Progress',progressPercent:75,updatedAtEpochMillis:1};
            assert.equal(c.isStudyFlowTvCastState(cast),true);
            assert.equal(c.isStudyFlowTvCastState({...cast,progressPercent:101}),false);
            assert.equal(c.isStudyFlowTvCastState({...cast,surface:'HOME'}),false);
            '''
        )
        result = subprocess.run(["node", "-e", script], cwd=WEB, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)
        for token in (
            'const val version = 1',
            'const val pairingCodeLength = 6',
            'const val pairingCodeTtlSeconds = 120',
            'val requiresUserPresence: Boolean = true',
        ):
            self.assertIn(token, CORE)

    def test_remote_receiver_and_action_bindings_are_present(self):
        for token in (
            'installTvControlReceiver',
            'studyflow:tv-control',
            'studyflow:tv-cast',
            'data-tv-control-action="PAUSE"',
            'data-tv-control-action="SELECT_ANSWER"',
            'data-tv-control-action="SHOW_EXPLANATION"',
            'data-tv-control-action="NEXT"',
            'TvPairingBanner',
            'TvCastBanner',
        ):
            self.assertIn(token, APP)
        self.assertIn('typeof EventSource==="undefined"', (WEB / "src/tv_control.ts").read_text())
        self.assertIn('/api/tv/poll?', (WEB / "src/tv_control.ts").read_text())

    def _start_relay_receiver(self):
        failures = []
        for attempt in range(1, 5):
            with socket.socket() as sock:
                sock.bind(("127.0.0.1", 0))
                port = sock.getsockname()[1]
            proc = subprocess.Popen(
                ["node", "scripts/serve_tv.mjs", "--host", "127.0.0.1", "--port", str(port)],
                cwd=WEB,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            base = f"http://127.0.0.1:{port}"
            deadline = time.monotonic() + 12.0
            last_error = None
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    stdout, stderr = proc.communicate(timeout=1)
                    failures.append(
                        f"attempt {attempt}: server exited {proc.returncode}; "
                        f"stdout={stdout.strip()!r}; stderr={stderr.strip()!r}"
                    )
                    break
                try:
                    _, registration = request_json(
                        base + "/api/tv/receiver",
                        "POST",
                        {"deviceName": "Test TV"},
                        timeout=1,
                    )
                    return proc, base, registration
                except Exception as exc:
                    last_error = exc
                    time.sleep(0.1)
            else:
                failures.append(f"attempt {attempt}: readiness timeout; last_error={last_error!r}")
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=2)
                stdout, stderr = proc.communicate(timeout=1)
                if stdout.strip() or stderr.strip():
                    failures.append(
                        f"attempt {attempt}: after termination stdout={stdout.strip()!r}; stderr={stderr.strip()!r}"
                    )
        self.fail("TV relay did not become ready after retries: " + " | ".join(failures))

    def test_lan_relay_pair_command_cast_revoke(self):
        proc, base, registration = self._start_relay_receiver()
        try:
            self.assertIsNotNone(registration)
            self.assertRegex(registration["pairingCode"], r"^\d{6}$")
            self.assertGreater(registration["expiresAtEpochMillis"], registration["issuedAtEpochMillis"])
            _, paired = request_json(base + "/api/tv/pair", "POST", {"pairingCode": registration["pairingCode"]})
            controller = paired["controllerToken"]
            command = {
                "commandId": "cmd-test",
                "protocolVersion": 1,
                "surface": "TODAY",
                "action": "PAUSE",
                "issuedAtEpochMillis": int(time.time() * 1000),
                "requiresUserPresence": True,
            }
            status, accepted = request_json(base + "/api/tv/control", "POST", command, controller)
            self.assertEqual(status, 202)
            self.assertEqual(accepted["commandId"], "cmd-test")
            poll_url = base + f"/api/tv/poll?receiverId={registration['receiverId']}&nonce={registration['sessionNonce']}&after=0"
            _, polled = request_json(poll_url)
            self.assertTrue(any(e["type"] == "command" and e["payload"]["commandId"] == "cmd-test" for e in polled["events"]))
            cast = {
                "castId": "cast-test",
                "protocolVersion": 1,
                "surface": "PROGRESS",
                "title": "Weekly progress",
                "progressPercent": 50,
                "updatedAtEpochMillis": int(time.time() * 1000),
            }
            status, accepted = request_json(base + "/api/tv/cast", "POST", cast, controller)
            self.assertEqual(status, 202)
            self.assertEqual(accepted["castId"], "cast-test")
            status, revoked = request_json(base + "/api/tv/revoke", "POST", {}, controller)
            self.assertEqual(status, 200)
            self.assertTrue(revoked["revoked"])
            with self.assertRaises(urllib.error.HTTPError) as error:
                request_json(base + "/api/tv/control", "POST", command, controller)
            try:
                self.assertEqual(error.exception.code, 401)
            finally:
                error.exception.close()
            with urllib.request.urlopen(base + "/remote", timeout=2) as response:
                remote = response.read().decode()
            self.assertIn("StudyFlow TV Remote", remote)
            self.assertIn("six-digit", remote)
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proc.kill()

    def test_server_is_in_memory_and_roadmap_is_delivered(self):
        for token in ('createTvRelay', 'requestUrl.pathname==="/remote"', 'Phone:', '120 seconds'):
            self.assertIn(token, SERVER)
        for token in ('const receivers=new Map()', 'const controllers=new Map()', 'randomBytes', 'EVENT_LIMIT=64'):
            self.assertIn(token, RELAY)
        self.assertNotIn('writeFile', RELAY)
        self.assertIn('WTV06 — TV Back/history semantics — delivered.', ROADMAP)
        self.assertIn('WTV07 — phone-controlled browser receiver — delivered.', ROADMAP)


if __name__ == "__main__":
    unittest.main()
