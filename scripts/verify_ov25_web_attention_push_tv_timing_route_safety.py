#!/usr/bin/env python3
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def require(c,m):
    if not c: errors.append(m)
def text(p):
    q=ROOT/p; require(q.is_file(), f'missing {p}'); return q.read_text(encoding='utf-8') if q.is_file() else ''
build=text('build.gradle.kts'); sw=text('web/service-worker.js'); relay=text('web/scripts/tv_relay.mjs'); attention=text('web/src/attention.ts'); tv=text('web/src/tv_attention.ts'); mock=text('web/src/tv_mock_exam.ts')
require('verifyOv25WebAttentionPushTvTimingRouteSafety' in build,'OV25 Gradle task missing')
require('scripts/validate_ov25_web_attention_push_tv_timing_route_safety.sh' in build,'OV25 task wiring missing')
for token in ('normalizeRouteHash','showNotification','notificationclick','studyflow:attention-drain','delete('): require(token in sw,f'service worker contract missing {token}')
for token in ('/api/tv/pair','TV_PAIRING_CODE_LENGTH','TV_PAIRING_TTL_SECONDS'): require(token in relay,f'TV relay contract missing {token}')
for token in ('pushSubscription','subscribePush','ingestRemoteDelivery','TERMINAL_STATES'): require(token in attention,f'Web attention contract missing {token}')
for token in ('foreground-only','routeHash','applyAction'): require(token in tv,f'Web-TV attention contract missing {token}')
for token in ('tvMockRemainingMs','tvMockProgressPercent','pausedAccumulatedMs'): require(token in mock,f'timed mock contract missing {token}')
runner=ROOT/'web/tests/ov25_attention_tv_safety_runner.mjs'; require(runner.is_file(),'OV25 behavioral runner missing')
if runner.is_file():
    result=subprocess.run(['node',str(runner)],cwd=ROOT,text=True,capture_output=True)
    if result.returncode: errors.append(result.stdout+result.stderr)
if errors:
    print('OV25_WEB_ATTENTION_PUSH_TV_TIMING_ROUTE_SAFETY: FAIL')
    print('\n'.join('- '+e for e in errors)); raise SystemExit(1)
print('OV25_WEB_ATTENTION_PUSH_TV_TIMING_ROUTE_SAFETY: PASS')
