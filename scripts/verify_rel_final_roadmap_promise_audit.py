#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/roadmap/REL_FINAL_PROMISE_AUDIT.json"
REMAINING = ROOT / "docs/roadmap/CROSS_PLATFORM_REMAINING_WORK.json"
MATRIX = ROOT / "docs/product/CROSS_PLATFORM_CAPABILITY_MATRIX.md"
LOCALIZATION = ROOT / "desktopApp/src/main/kotlin/com/mikeyphw/studyflow/desktop/DesktopLocalization.kt"
BUILD = ROOT / "build.gradle.kts"
DEVTOOL = ROOT / ".devtool.toml"
EXPECTED_EXTERNAL = {"verify_final_release_artifact.py", "verify_final_release_verdict.py"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"REL_FINAL_PROMISE_AUDIT: FAILED — {message}")


def main() -> None:
    audit = json.loads(LEDGER.read_text(encoding="utf-8"))
    remaining = json.loads(REMAINING.read_text(encoding="utf-8"))
    matrix = MATRIX.read_text(encoding="utf-8")
    localization = LOCALIZATION.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    devtool = DEVTOOL.read_text(encoding="utf-8")

    require(audit.get("schema_version") == 1, "unexpected audit schema")
    require(audit.get("contract") == "studyflow.rel_final.promise_audit", "unexpected contract id")
    require(audit.get("status") == "STATIC_AUDIT_COMPLETE_EVIDENCE_PENDING", "audit status drifted")
    claims = audit.get("claims", {})
    require(claims.get("product_implementation_complete") is True, "product implementation is not closed")
    require(claims.get("roadmap_static_contracts_complete") is True, "static roadmap audit is not closed")
    require(claims.get("release_evidence_complete") is False, "static audit must not synthesize release evidence")
    require(claims.get("release_ready") is False, "static audit must not claim release readiness")
    require(claims.get("release_ops_ready") is False, "static audit must not claim release-ops readiness")

    inventory = audit.get("verifier_inventory", {})
    expected_legacy = set(inventory.get("legacy_scripts", []))
    require(inventory.get("legacy_total") == 213, "legacy verifier inventory must remain 213")
    require(inventory.get("static_required_pass") == 211, "211 static verifier passes are required")
    require(set(inventory.get("external_evidence_gates", [])) == EXPECTED_EXTERNAL, "external gate set drifted")
    post_rel_final = set(inventory.get("post_rel_final_verifiers", []))
    expected_post_rel_final = {
        "verify_att01_unified_attention_foundation.py",
        "verify_att02_android_attention_system.py",
        "verify_att03_android_widget_suite.py",
        "verify_att04_desktop_attention_runtime.py",
        "verify_att05_web_pwa_attention_runtime.py",
        "verify_att06_tv_attention_experience.py",
        "verify_att07_cross_device_delivery_router.py",
        "verify_att08_compact_companion_attention.py",
        "verify_att09_calendar_schedule_export_attention.py",
        "verify_att10_quiet_hours_focus_profiles_escalation_policy.py",
        "verify_att11_notification_reliability_evidence_audit.py",
        "verify_att12_attention_release_final_seal.py",
        "verify_attention_roadmap_postseal_completion.py",
        "verify_tv_devtool_android_runner.py",
        "verify_rel_final_devtool_release_runner.py",
        "verify_studyflow_gradle_task_graph_optimization_v4.py",
    }
    require(post_rel_final == expected_post_rel_final, "post-REL-FINAL verifier set drifted")
    # Separate the frozen REL-FINAL historical ledger from later verifier growth.
    # A new verifier requires explicit classification; do not silently accept it.
    additional_raw = inventory.get("additional_verifiers_since_rel_final_boundary", [])
    require(isinstance(additional_raw, list) and len(additional_raw) == len(set(additional_raw)),
            "additional verifier inventory has duplicate or invalid members")
    require(inventory.get("additional_verifier_count") == 54 and len(additional_raw) == 54,
            "reviewed post-boundary verifier count drifted")
    additional = set(additional_raw)
    require(not additional.intersection(expected_legacy | post_rel_final),
            "additional verifier inventory overlaps the immutable historical classification")
    policy = audit.get("audit_execution_policy", {})
    require(
        policy.get("exhaustive_static_audit") == "artifact_validation_before_gradle",
        "exhaustive audit must stay isolated before Gradle",
    )
    require(
        policy.get("repository_wide_verifyFormatting") == "not_a_rel_final_roadmap_promise",
        "REL-FINAL formatting scope classification drifted",
    )
    require(
        'tasks.register<Exec>("runRelFinalExhaustiveRoadmapAudit")' in build,
        "standalone exhaustive audit task missing",
    )
    terminal_block = build.split('tasks.register("verifyRelFinalTerminalSeal")', 1)[1]
    terminal_block = terminal_block.split("}\n", 1)[0]
    require("runRelFinalExhaustiveRoadmapAudit" not in terminal_block, "terminal seal must not rerun recursive audit")
    require("verifyFormatting" not in terminal_block, "REL-FINAL terminal seal must not promote unrelated formatting debt")
    parity_target = devtool.split("[targets.studyflow_parity.tasks]", 1)[1]
    parity_target = parity_target.split("[targets.studyflow_parity.validation]", 1)[0]
    require("runRelFinalExhaustiveRoadmapAudit" not in parity_target, "default parity target must not rerun recursive audit")
    require('lint = ["verifyFinalSourceAcceptance"]' in parity_target, "REL-FINAL lint gate must use final source acceptance")
    actual = {path.name for path in (ROOT / "scripts").glob("verify_*.py")}
    actual.discard(Path(__file__).name)
    require(post_rel_final <= actual, "post-REL-FINAL verifier file is missing")
    actual -= post_rel_final
    actual -= additional
    require(actual == expected_legacy,
            "REL-FINAL legacy or additional verifier inventory drifted from reviewed snapshot")

    families = {item.get("id"): item for item in audit.get("roadmap_families", [])}
    for family in (
        "PHASE00-64", "OV01-20", "CPOV00-13", "CMOV01-11", "CSOV00-15",
        "PARITY01-03", "SFLP01-24", "DOV01-24", "TVOV00-31", "WEB20",
        "PRESENCE01", "PARITY04",
    ):
        require(families.get(family, {}).get("status") == "delivered", f"{family} is not delivered")
    require(
        families.get("REL01-08", {}).get("status") == "tooling_delivered_evidence_pending",
        "REL01-08 tooling/evidence status drifted",
    )

    handoff = audit.get("handoff_connectivity_promise_ledger", {})
    phases = handoff.get("phases", [])
    require(len(phases) >= 20, "handoff promise ledger is incomplete")
    for phase in phases:
        require(str(phase.get("audit_status") or "").strip(), f"phase audit status missing: {phase.get('title')}")
        promises = phase.get("promises", [])
        require(isinstance(promises, list), f"invalid promises list: {phase.get('title')}")
        require(all(str(item).strip() for item in promises), f"blank promise in {phase.get('title')}")

    rel_final = next(item for item in remaining["remaining_tracks"] if item.get("id") == "REL-FINAL")
    require(rel_final.get("status") == "pending_execution", "REL-FINAL must stay pending until real evidence closes")
    require(rel_final.get("prerequisites") == ["PARITY04", "REL01-REL08"], "REL-FINAL prerequisites drifted")

    for token in (
        "| Android TV | `1.0.0-tv31` |",
        "| Full material viewing | FULL | FULL | FULL | FULL | FULL |",
        "| Global search | FULL | FULL | FULL | LIMITED | FULL |",
        "| Goals | FULL | FULL | FULL | LIMITED | LIMITED |",
    ):
        require(token in matrix, f"current capability truth missing: {token}")
    for token in (
        '"Live StudyFlow presence" to "Presença ao vivo do StudyFlow"',
        '"No live peer presence is currently visible." to',
        '"Presence is ephemeral and optional; sync correctness never depends on it." to',
    ):
        require(token in localization, f"pt-BR presence translation missing: {token}")

    for path in (
        ROOT / "scripts/run_rel_final_roadmap_audit.py",
        ROOT / "scripts/run_rel_final_release_campaign.py",
        ROOT / "docs/release/REL_FINAL_RELEASE_EVIDENCE_CAMPAIGN.md",
        ROOT / "REL_FINAL_ROADMAP_PROMISE_AND_RELEASE_EVIDENCE_SEAL.md",
    ):
        require(path.is_file(), f"missing REL-FINAL component: {path.relative_to(ROOT)}")

    print("REL_FINAL_PROMISE_AUDIT: PASS (211 static + 2 external evidence gates classified)")


if __name__ == "__main__":
    main()
