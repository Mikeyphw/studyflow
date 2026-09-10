from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (ROOT / 'runtime' / 'sflp_link.js').read_text()
TS = (ROOT / 'src' / 'sflp_link.ts').read_text()


def test_sflp20_share_import_runtime_is_present():
    assert 'StudyFlowShareImportConflictRuntime' in RUNTIME
    assert 'applyShareImportDelivery' in RUNTIME
    assert 'clipboard_apply' in RUNTIME
    assert 'tv_write_forbidden' in RUNTIME


def test_sflp21_conflict_review_runtime_is_present():
    assert 'openConflictReview' in RUNTIME
    assert 'resolveConflict' in RUNTIME
    assert 'conflict.review' in RUNTIME
    assert 'StudyFlowConflictResolutionKind' in TS
