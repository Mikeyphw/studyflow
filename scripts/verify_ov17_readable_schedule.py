#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
planner = (root / "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/ScheduleCalendarWindow.kt").read_text()
screen = (root / "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt").read_text()
checks = {
    "four views": "ScheduleCalendarView.entries.forEach" in screen and all(x in planner for x in ["TODAY", "THIS_WEEK", "NEXT_WEEK", "CALENDAR"]),
    "monday week": "previousOrSame(DayOfWeek.MONDAY)" in planner,
    "next week": ".plusWeeks(1)" in planner,
    "bounded filter": "ScheduleCalendarPlanner.filter(criteriaActivities" in screen,
    "calendar keeps all": "ScheduleCalendarView.CALENDAR -> ScheduleCalendarWindow(null, null)" in planner,
    "exact summary": "ScheduleCalendarPlanner.plannedMinutes(visibleActivities)" in screen,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("OV17 contract failed: " + ", ".join(failed))
print("OV17 readable schedule contract: PASS")
