from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_web_runtime_exports_control_input_runtime():
    source = (ROOT / 'runtime' / 'sflp_link.js').read_text()
    assert 'StudyFlowControlInputRuntime' in source
    assert 'rememberControlLease' in source
    assert 'applyControlCommand' in source
    assert 'acceptInputDelegation' in source
    assert 'applyInputEvent' in source
    assert 'bad_lease_token' in source
    assert 'stale_sequence' in source


def test_typescript_runtime_has_wire_envelopes():
    source = (ROOT / 'src' / 'sflp_link.ts').read_text()
    assert 'StudyFlowControlCommandEnvelope' in source
    assert 'StudyFlowInputDelegationEnvelope' in source
    assert 'StudyFlowControlCommandStatus' in source
