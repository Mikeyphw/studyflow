#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise SystemExit(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(path: str, *needles: str) -> None:
    text = read(path)
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"{path}: missing required marker: {needle}")


def forbid(path: str, *needles: str) -> None:
    text = read(path)
    for needle in needles:
        if needle in text:
            raise SystemExit(f"{path}: forbidden marker present: {needle}")


def main() -> None:
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/home/StudyFlowTvHomeSurface.kt",
        "const val overlay = \"TVOV06\"",
        "const val ownsDefaultTvDestination = true",
        "StudyFlowTvHomeCardId.TODAY",
        "StudyFlowTvHomeCardId.PRACTICE",
        "StudyFlowTvHomeCardId.REVIEWS",
        "StudyFlowTvHomeCardId.MOCK_EXAM",
        "StudyFlowTvHomeAction.PAIR_PHONE",
        "StudyFlowTvControlCommand(",
        "StudyFlowTvNavigationShell.openCommandFor",
    )
    forbid(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/home/StudyFlowTvHomeSurface.kt",
        "androidx.compose",
        "androidx.tv",
        "MediaRouter",
        "CastContext",
        "Room",
        "Dao",
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvActivity.kt",
        "import com.mikeyphw.studyflow.tv.home.StudyFlowTvHomeSurface",
        "private fun renderHomeDestination()",
        "private fun tvHomeCard(card: StudyFlowTvHomeCard): Button",
        "StudyFlowTvHomeSurface.cards.chunked(2)",
        "onClick = { selectDestination(card.destination) }",
        "R.string.tv_home_card_format",
    )
    require(
        "app-tv/src/main/java/com/mikeyphw/studyflow/tv/StudyFlowTvBuildContract.kt",
        "object StudyFlowTvHomeBuildContract",
        "const val homeSurfaceIntroduced = true",
        "const val homeCardCount = 4",
        "const val homePrimaryActionCount = 3",
    )
    require(
        "app-tv/src/test/java/com/mikeyphw/studyflow/tv/home/StudyFlowTvHomeSurfaceTest.kt",
        "homeSurfaceOwnsTheDefaultTvEntryPoint",
        "cardsAreStableUniqueAndRouteToTvDestinations",
        "homeFocusItemsUseCardsAndPrimaryActionsWithoutWrappingAssumptions",
        "primaryActionsMapToStudyFlowControlCommands",
    )
    require(
        "app-tv/src/main/res/values/strings.xml",
        "tv_home_section_title",
        "tv_home_card_today_title",
        "tv_home_card_practice_title",
        "tv_home_card_reviews_title",
        "tv_home_card_mock_exam_title",
    )
    forbid(
        "app-tv/src/main/res/values/strings.xml",
        "tv_app_status",
        "tv_app_navigation_status",
        "tv_app_next_step",
        "tv_shell_primary_action",
        "tv_shell_pair_phone_action",
    )
    require(
        "build.gradle.kts",
        "verifyStudyFlowTvHomeSurface",
        "scripts/verify_tvov06_home_surface.py",
    )
    require(
        ".devtool.toml",
        "verifyStudyFlowTvHomeSurface",
    )
    require(
        "ANDROID_TV_ROADMAP.md",
        "## TVOV06 — Home surface",
        "TVOV05 unused-string lint warning cleanup",
    )
    require(
        "TVOV06_ANDROID_TV_HOME_SURFACE.md",
        "TVOV06 turns the TV shell into a structured Home surface",
    )
    print("TVOV06 Home surface contracts passed")


if __name__ == "__main__":
    main()
