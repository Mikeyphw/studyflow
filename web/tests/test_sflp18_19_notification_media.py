from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (ROOT / "runtime" / "sflp_link.js").read_text()
SOURCE = (ROOT / "src" / "sflp_link.ts").read_text()


def test_web_runtime_has_notification_schedule_and_material_streaming_contracts():
    for text in (RUNTIME, SOURCE):
        assert "StudyFlowNotificationMediaRuntime" in text
        assert "applyNotificationDelivery" in text
        assert "schedule.reminder_upserted" in text
        assert "acceptMaterialStream" in text
        assert "applyMaterialChunk" in text
        assert "library.import" in text
        assert "unexpected_offset" in text
        assert "sflp18" in text
        assert "attentionScheduleChange" in text
        assert "StudyFlowAttentionLedgerSnapshot" in text
        assert "legacyDeliveredOccurrenceIds" in text
