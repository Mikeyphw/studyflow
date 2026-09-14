# StudyFlow Remediation Overlay 21 of 30

OV21 implements the unified TV control, pairing, relay, cast and attention plane.

The overlay adds `StudyFlowTvUnifiedControlPlane`, extends the authenticated TV wire request with `StudyFlowTvPlaneEnvelope`, attaches envelopes in the Android phone local-transport client, rejects missing or mismatched envelopes at the Android TV receiver before command/presentation/domain/revoke handling, binds cast presentation and STOP_CAST to explicit scopes, exposes attention-plane evidence, and makes the Web TV relay/browser remote use the same envelope concept without bearer/controller tokens.

Validation is embedded through `verifyOv21UnifiedTvControlPlane` and `scripts/validate_ov21_unified_tv_control_plane.sh`.
