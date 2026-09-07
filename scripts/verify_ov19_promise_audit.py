#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(path: str, needle: str) -> None:
    assert needle in text(path), f"{path}: missing {needle!r}"

def reject(path: str, needle: str) -> None:
    assert needle not in text(path), f"{path}: forbidden {needle!r}"

schedule = "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt"
dialogs = "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleDialogs.kt"
dashboard = "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt"
pre = "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamPreEdital.kt"
today = "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/TodayWorkspaceScreen.kt"

require(today, "selectTodayOrNextScheduledActivity")
require(dashboard, "item.topicId == topic.id || (item.topicId == null && item.subjectId == subject.id)")
require(schedule, "subjectId = draft.subjectId")
require(schedule, "topicId = draft.topicId")
require(schedule, "scheduledStartEpochMillis = draft.scheduledStartEpochMillis")
require(dialogs, "scheduleStartEpochMillis(date, start, zoneId)")
require(schedule, "StudyStrategyType.entries.forEach")
reject(schedule, "StudyStrategyType.entries.take(4)")
require(schedule, "scheduleSearchQuery")
require(schedule, "showPastCalendarActivities")
require(schedule, "import android.os.Build")
require(schedule, "val configuration = LocalConfiguration.current")
require(schedule, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.N")
require(schedule, "configuration.locales[0]")
require(schedule, "configuration.locale")
reject(schedule, "Locale.getDefault()")
require(dashboard, "StudyFlowSubjectAccordion")
require(dashboard, "show_dashboard_details")
reject(dashboard, "R.string.edital_matrix_row")
require(today, "readySchedule.strategy?.type == StudyStrategyType.STUDY_CYCLE")
require(today, "import android.os.Build")
require(today, "import androidx.compose.ui.platform.LocalConfiguration")
require(today, "val configuration = LocalConfiguration.current")
require(today, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.N")
require(today, "configuration.locales[0]")
require(today, "configuration.locale")
require(today, "DateFormat.getTimeInstance(DateFormat.SHORT, locale)")
require(today, 'SimpleDateFormat("EEE", locale)')
require(today, "DateFormat.getDateInstance(DateFormat.SHORT, locale)")
reject(today, "Locale.getDefault()")
pt_strings = "feature/schedule/src/main/res/values-pt-rBR/strings.xml"
require(pt_strings, '<plurals name="selected_count">')
require(pt_strings, '<item quantity="many">%1$d selecionadas</item>')
require(pt_strings, '<plurals name="delete_selected_message">')
require(pt_strings, '<item quantity="many">%1$d atividades desbloqueadas e editáveis serão excluídas.</item>')
require(pt_strings, '<plurals name="schedule_view_summary">')
require(pt_strings, '<item quantity="many">%1$d atividades · %2$d minutos planejados</item>')
require(pre, "PublicExamDateResolver")
reject(pre, "target?.confirmedExamDateIso,")

for i in range(1, 19):
    candidates = list((ROOT / "scripts").glob(f"verify_ov{i:02d}_*.py"))
    assert candidates, f"missing retained OV{i:02d} verifier"

print("OV19_PROMISE_AUDIT: PASS")
