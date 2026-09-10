from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (ROOT / 'runtime' / 'sflp_link.js').read_text()
SOURCE = (ROOT / 'src' / 'sflp_link.ts').read_text()


def test_web_runtime_exports_account_backup_release_runtime():
    assert 'StudyFlowAccountBackupReleaseRuntime' in RUNTIME
    assert 'exports.StudyFlowAccountBackupReleaseRuntime' in RUNTIME
    assert 'account.handoff_offer' in (ROOT.parent / 'protocol' / 'sflp' / 'v1' / 'sflp-account-backup-release.schema.json').read_text()


def test_web_runtime_keeps_tv_restore_guard_and_final_task_chain():
    assert 'tv_restore_forbidden' in RUNTIME
    assert 'tv_account_handoff_forbidden' in RUNTIME
    assert 'verifySflp22Sflp23AccountBackupValidation' in RUNTIME
    assert ':app:testReleaseUnitTest' in RUNTIME
    assert 'export class StudyFlowAccountBackupReleaseRuntime' in SOURCE
