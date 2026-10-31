#!/usr/bin/env python3
"""R4R9 repo-only/hosted negative contracts; no Gradle, network or devices."""
from __future__ import annotations
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def get(path): return (ROOT/path).read_text(encoding='utf8')
def check(ok,why):
    if not ok:raise AssertionError(why)

def run(path):
    p=subprocess.run([sys.executable, 'scripts/'+path],cwd=ROOT,text=True,capture_output=True,timeout=90)
    check(p.returncode==0,path+': '+(p.stdout+p.stderr)[-2500:])

def tile_contract(text):
    try:
        class_guard=re.search(r'@RequiresApi\(Build\.VERSION_CODES\.N\)\s+class StudyFlowAttentionTileService : TileService\(\)',text)
        on_click=re.search(r'override fun onClick\(\) \{(.*?)\n    \}',text,re.S)
        refresh=re.search(r'fun requestRefresh\(context: Context\) \{(.*?)\n        \}',text,re.S)
        fallback=re.search(r'@SuppressLint\("StartActivityAndCollapseDeprecated"\).*?private fun startActivityAndCollapseLegacy\(intent: Intent\) \{(.*?)\n    \}',text,re.S)
        if not (class_guard and on_click and refresh and fallback):return False
        click=on_click.group(1)
        return all([
            'Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE' in click,
            click.index('Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE') < click.index('startActivityAndCollapse(pendingIntent)') < click.index('} else {') < click.index('startActivityAndCollapseLegacy(intent)'),
            'PendingIntent.getActivity(' in click,
            'PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE' in click,
            'startActivityAndCollapse(intent)' in fallback.group(1),
            'if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return' in refresh.group(1),
            refresh.group(1).index('Build.VERSION.SDK_INT < Build.VERSION_CODES.N') < refresh.group(1).index('requestListeningState('),
        ])
    except (ValueError, AttributeError):return False

def manifest_contract(text):
    try:
        root=ET.fromstring(text)
        ns='{http://schemas.android.com/apk/res/android}'
        service=root.find("./application/service[@"+ns+"name='.reminders.StudyFlowAttentionTileService']")
        if service is None:return False
        tags=service.findall('meta-data')
        return (
            service.get(ns+'permission')=='android.permission.BIND_QUICK_SETTINGS_TILE' and
            service.get(ns+'exported')=='true' and
            len([x for x in tags if x.get(ns+'name')=='android.service.quicksettings.ACTIVE_TILE' and x.get(ns+'value')=='true'])==1 and
            any(x.get(ns+'name')=='android.service.quicksettings.action.QS_TILE' for x in service.findall('./intent-filter/action'))
        )
    except ET.ParseError:return False

def main():
    tile=get('app/src/main/java/com/mikeyphw/studyflow/reminders/StudyFlowAttentionTileService.kt')
    check(tile_contract(tile),'API 23/24/34 QS tile source guard/launch contract failed')
    mutations=[
      tile.replace('@RequiresApi(Build.VERSION_CODES.N)',''),
      tile.replace('Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE','Build.VERSION.SDK_INT >= Build.VERSION_CODES.N'),
      tile.replace('startActivityAndCollapse(pendingIntent)','startActivityAndCollapseLegacy(intent)'),
      tile.replace('PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE','PendingIntent.FLAG_UPDATE_CURRENT'),
      tile.replace('if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return',''),
      tile.replace('@SuppressLint("StartActivityAndCollapseDeprecated")',''),
    ]
    for i,s in enumerate(mutations):check(not tile_contract(s),f'Unsafe QS tile mutation #{i} escaped regression')
    coordinator=get('app/src/main/java/com/mikeyphw/studyflow/reminders/StudyReminderDeliveryCoordinator.kt')
    matches=list(re.finditer(r'(?P<guard>if \(android\.os\.Build\.VERSION\.SDK_INT >= android\.os\.Build\.VERSION_CODES\.N\) \{)\s*StudyFlowAttentionTileService\.requestRefresh\(appContext\)\s*\}',coordinator))
    check(len(matches)==2 and coordinator.count('StudyFlowAttentionTileService.requestRefresh(appContext)')==2,
          'Both phone companion refresh call sites must be API 24 guarded')
    manifest=get('app/src/main/AndroidManifest.xml');check(manifest_contract(manifest),'QS tile active metadata or binding permission missing')
    for i,altered in enumerate((manifest.replace('android.service.quicksettings.ACTIVE_TILE','com.example.BAD_TILE'),
                                manifest.replace('android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"','android:permission="android.permission.INTERNET"'),
                                manifest.replace('android:value="true" />\n            <intent-filter>','android:value="false" />\n            <intent-filter>'))):
        check(not manifest_contract(altered),f'Invalid QS manifest mutation #{i} accepted')
    widget=get('app/src/main/java/com/mikeyphw/studyflow/widget/StudyFlowWidgetModels.kt')
    att12=get('scripts/verify_att12_attention_release_final_seal.py')
    for token in ('.filter { it > nowEpochMillis }','nowEpochMillis + MinimumRefreshDelayMillis'):
        check(token in widget and token in att12,'ATT12 and real widget contract disagree: '+token)
    check('keepsSubMinutePlannedBoundaryForExactFreshnessScheduler' in att12 and
          'imminentReviewDeadlineRemainsExactWithoutAnActiveSession' in att12,
          'ATT12 must check exact behavior test owners')
    check(').filter { it >= nowEpochMillis + MinimumRefreshDelayMillis }' not in att12,
          'ATT12 stale 60s-only filter reintroduced')
    queue=get('core/link/src/main/kotlin/com/mikeyphw/studyflow/core/link/StudyFlowOfflineQueue.kt')
    check(max(map(len,queue.splitlines()))<=120,'SFLP06 Kotlin queue line-length violation')
    check(re.search(r'fun nextPeerAttemptAt\(peerDeviceId: StudyFlowDeviceId\): Long\? =\s+peerRetryState\(peerDeviceId\)\?\.nextAttemptAtEpochMillis',queue) is not None,
          'Retry formula changed beyond formatting')
    check('python3 scripts/test_studyflow_ov01_r4r9_contracts.py' in get('.github/workflows/studyflow-ci.yml'),
          'R4R9 source regression not hosted')
    check('SF-DT-51' in get('docs/ci/DEVTOOL_IMPROVEMENT_LEDGER.md'), 'Cumulative improvements ledger incomplete')
    run('verify_att12_attention_release_final_seal.py')
    run('verify_sflp06_07_topology_offline_queue.py')
    print('STUDYFLOW_R4R9_SOURCE_NEGATIVE_CONTRACTS: PASS (3 API tiers, all known blockers, no Gradle)')

if __name__=='__main__':main()
