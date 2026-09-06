#!/usr/bin/env python3
from pathlib import Path

failures=[]
def need(path,text,label):
    data=Path(path).read_text()
    if text not in data: failures.append(f"{label}: missing {text!r} in {path}")
helper='feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/ScheduleIntelligence.kt'
ui='feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt'
test='feature/schedule/src/test/java/com/mikeyphw/studyflow/feature/schedule/ScheduleIntelligenceTest.kt'
en='feature/schedule/src/main/res/values/strings.xml'
pt='feature/schedule/src/main/res/values-pt-rBR/strings.xml'
need(helper,'startsWith("generated:")','durable generated provenance')
need(helper,'groupByDay(','day grouping')
need(helper,'conflictActivityIds','conflict detection')
need(helper,'overloadedDates','overload detection')
need(helper,'!activity.locked && activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.MISSED)','bulk protection')
for token,label in [('filter_subject','subject filter'),('filter_type','type filter'),('filter_status','status filter'),('filter_provenance','provenance filter')]:
    need(en,f'name="{token}"',label)
need(ui,'selectedActivityIds','bulk selection UI')
need(ui,'confirmingBulkDelete','bulk delete confirmation')
need(ui,'ScheduleIntelligence.groupByDay','day-group UI')
need(ui,'ScheduleIntelligence.inspect','conflict/overload UI')
need(test,'generatedPrefixIsDurableProvenance','provenance regression')
need(test,'detectsOverlapAndDailyOverload','conflict regression')
need(pt,'name="bulk_protection_note"','Portuguese bulk protection copy')
if failures: raise SystemExit('OV06 schedule intelligence violations:\n'+'\n'.join('- '+x for x in failures))
print('OV06_SCHEDULE_INTELLIGENCE: PASS')
