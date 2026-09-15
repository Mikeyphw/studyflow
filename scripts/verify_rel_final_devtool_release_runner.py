#!/usr/bin/env python3
from __future__ import annotations

import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    require(value.endswith("\n"), f"{path} must end with a newline")
    return value


def main() -> None:
    config = tomllib.loads(read(".devtool.toml"))
    targets = config.get("targets", {})

    tv = targets.get("studyflow_tv", {})
    require(tv.get("runner") == "android", "studyflow_tv must use the Android Devtool runner")
    tv_android = tv.get("android", {})
    require(tv_android.get("variant") == "debug", "studyflow_tv must remain a debug Android target")
    require(tv_android.get("aapt2_provider") == "termux", "studyflow_tv must use Termux AAPT2")
    require(str(tv_android.get("ndk_version", "")).startswith("29."), "studyflow_tv must stay on NDK 29")
    tv_build = tv.get("tasks", {}).get("build", [])
    require(":app-tv:assembleDebug" in tv_build, "studyflow_tv must build the TV debug APK")
    require(":app:assembleDebug" not in tv_build, "studyflow_tv must not build the mobile APK")
    require("verifyStudyFlowTvAllContracts" in tv_build,
            "studyflow_tv build must preserve the aggregate TV contract gate")
    tv_preflight = tv.get("validation", {}).get("phases", {}).get("preflight", [])
    require("verifyStudyFlowTvAllContracts" in tv_preflight,
            "studyflow_tv validation preflight must use the aggregate TV contract gate")
    build = read("build.gradle.kts")
    tv_aggregate = build.split('tasks.register("verifyStudyFlowTvAllContracts")', 1)[1]
    tv_aggregate = tv_aggregate.split('tasks.register("verifyStudyFlowAndroidPreflightContracts")', 1)[0]
    require("verifyStudyFlowTvDevtoolAndroidRunner" in tv_aggregate,
            "aggregate TV contract gate must preserve the Android-runner regression guard")

    release = targets.get("studyflow_release", {})
    require(release.get("runner") == "android", "studyflow_release must use the Android Devtool runner")
    release_android = release.get("android", {})
    require(release_android.get("variant") == "release", "studyflow_release must use the release variant")
    require(release_android.get("aapt2_provider") == "termux", "studyflow_release must use Termux AAPT2")
    require(release_android.get("prefer_native_termux") is True, "studyflow_release must prefer native Termux")
    require(str(release_android.get("ndk_version", "")).startswith("29."), "studyflow_release must stay on NDK 29")

    release_tasks = release.get("tasks", {}).get("build", [])
    require("verifyRelFinalDevtoolReleaseRunner" in release_tasks, "release target must self-check routing")
    require("verifyFinalReleaseAutomatedGate" in release_tasks, "release target must execute the real automated gate")

    gradle_args = release.get("gradle", {}).get("args", [])
    require("--no-configuration-cache" in gradle_args, "release target must disable configuration cache")
    require("-x" in gradle_args and "verifyFormatting" in gradle_args,
            "release target must exclude the unrelated repository-wide formatting backlog")

    acceptance = read("scripts/run_final_release_acceptance.sh")
    require("--target studyflow_release" in acceptance, "release acceptance must call the dedicated Devtool target")
    require("devtool --yes" in acceptance, "release acceptance must invoke Devtool")
    require("./gradlew" not in acceptance, "release acceptance must not invoke the Gradle wrapper directly")
    require("/usr/bin/gradle" not in acceptance, "release acceptance must not invoke raw Gradle")
    require("exactly one adb device required" in acceptance, "release acceptance must keep the one-device gate")

    target_runner = read("scripts/run_target_release_validation.py")
    require('"devtoolReleaseTarget": "studyflow_release"' in target_runner,
            "REL01 plan must expose the Devtool release target")
    require("./gradlew --no-daemon verifyFinalReleaseVerdict" not in target_runner,
            "device template must not reintroduce raw Gradle")
    require("--mode verdict --report-dir" in target_runner,
            "device template must finish through the Python verdict runner")

    campaign = read("scripts/run_rel_final_release_campaign.py")
    require('blocked_reason="automated Devtool release gate failed"' in campaign,
            "campaign must classify an automated-gate failure as BLOCKED")
    require("return automated_code" in campaign,
            "campaign must fail immediately when automated Devtool validation fails")
    require('status = "PASS" if ready else ("BLOCKED" if blocked else "PENDING_TARGET_EXECUTION")' in campaign,
            "campaign must distinguish BLOCKED from pending manual evidence")

    ov29_required_rel_final_gates = (
        "verifyOv29BehavioralValidationReleaseTruthGates",
        "verifyOv29ExhaustiveVerifierInventory",
        "verifyWebStaleDistReleaseIntegrity",
    )
    for gate in ov29_required_rel_final_gates:
        require(gate in build, f"REL-FINAL omits OV29 gate: {gate}")

    print("REL_FINAL_DEVTOOL_RELEASE_RUNNER: PASS")


if __name__ == "__main__":
    main()

