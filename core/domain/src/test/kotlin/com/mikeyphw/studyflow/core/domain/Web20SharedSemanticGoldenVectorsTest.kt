package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.link.StudyFlowDeterministicWinnerConflictHandler
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowDomainOperation
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowEntityRevision
import com.mikeyphw.studyflow.core.link.StudyFlowOperationConflict
import com.mikeyphw.studyflow.core.link.StudyFlowOperationId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import com.mikeyphw.studyflow.core.model.*
import com.mikeyphw.studyflow.core.portability.CanonicalPortableJson
import java.io.File
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class Web20SharedSemanticGoldenVectorsTest {
    @Test
    fun allWeb20VectorsMatchCanonicalKotlinSemantics() {
        val vectors = fixture().jsonObject.getValue("vectors").jsonArray
        assertEquals(40, vectors.size)
        vectors.forEach { element ->
            val vector = element.jsonObject
            val id = vector.s("id")
            val input = vector.obj("input")
            val expected = vector.obj("expected")
            when (vector.s("kind")) {
                "readiness_band" -> assertEquals(
                    id,
                    expected.s("band"),
                    ExamReadinessEngine.bandFor(input.i("score")).name.lowercase().replace('_', '-'),
                )
                "schedule_generation" -> checkScheduleGeneration(id, input, expected)
                "schedule_rebalance" -> checkScheduleRebalance(id, input, expected)
                "question_score", "mock_score" -> checkScore(id, input, expected)
                "srs" -> checkSrs(id, input, expected)
                "study_time" -> checkStudyTime(id, input, expected)
                "goal_progress" -> checkGoal(id, input, expected)
                "error_state" -> checkError(id, input, expected)
                "portable_serialization" -> assertEquals(
                    id,
                    expected.s("canonical"),
                    CanonicalPortableJson.canonicalize(input.getValue("value").toString()),
                )
                "sflp_conflict" -> checkConflict(id, input, expected)
                else -> error("Unknown WEB20 vector kind ${vector.s("kind")}")
            }
        }
    }

    private fun checkScheduleGeneration(id: String, input: JsonObject, expected: JsonObject) {
        val planner = StudySchedulePlanner(TimeZone.getTimeZone("UTC")) { "golden" }
        val plan = StudyPlanId("plan-web20")
        val rules = buildList {
            add(
                ScheduleRule(
                    ScheduleRuleId("rule-main"), plan, null, ScheduleRuleType.RECURRING_BLOCK,
                    input.i("dayOfWeek"), input.i("startMinuteOfDay"), input.i("durationMinutes"),
                    null, false, true, 0,
                ),
            )
            if (input.b("dayOff")) {
                add(
                    ScheduleRule(
                        ScheduleRuleId("rule-off"), plan, null, ScheduleRuleType.DAY_OFF,
                        input.i("dayOfWeek"), null, 1, null, false, true, 1,
                    ),
                )
            }
        }
        val result = planner.generateActivities(
            ScheduleGenerationRequest(plan, input.l("startEpochMillis"), input.i("days"), true),
            rules,
        )
        assertEquals(id, expected.i("count"), result.size)
        assertEquals(
            id,
            expected.nullableLong("firstStartEpochMillis"),
            result.firstOrNull()?.scheduledStartEpochMillis,
        )
    }

    private fun checkScheduleRebalance(id: String, input: JsonObject, expected: JsonObject) {
        val plan = StudyPlanId("plan-web20")
        val activity = PlannedActivity(
            PlannedActivityId("activity-web20"), plan, null, null, null, null,
            StudyActivityType.THEORY, "Golden", input.l("startEpochMillis"), input.i("durationMinutes"),
            PlannedActivityStatus.MISSED, false, null,
        )
        val result = StudySchedulePlanner(TimeZone.getTimeZone("UTC")).rebalanceMissedActivities(
            listOf(activity),
            MissedActivityHandling.valueOf(input.s("handling")),
            input.l("nowEpochMillis"),
        )
        assertEquals(id, expected.i("changed"), result.size)
        val changed = result.firstOrNull()
        assertEquals(id, expected.stringOrNull("status"), changed?.status?.name)
        assertEquals(id, expected.nullableLong("scheduledStartEpochMillis"), changed?.scheduledStartEpochMillis)
    }

    private fun checkScore(id: String, input: JsonObject, expected: JsonObject) {
        val total = input.i("total")
        val correct = input.i("correct")
        val incorrect = input.i("incorrect")
        val ids = (0 until total).map { QuestionId("q-$it") }
        val answers = buildList {
            repeat(correct) { i -> add(PracticeAnswerRecord(ids[i], "A", "A", true, 1, 1)) }
            repeat(incorrect) { i ->
                add(PracticeAnswerRecord(ids[correct + i], "B", "A", false, 1, 1, ErrorCategory.OTHER))
            }
        }
        val state = PracticeExecutionState(
            id, "Golden", TestMode.PRACTICE, ids, emptyList(), answers, 0, null, null,
            input.d("negativeMarking"), input.intOrNull("minimumScorePercent"),
        )
        val summary = PracticeExecutionEngine { 1 }.finish(state)
        assertEquals(id, expected.i("scorePercent"), summary.scorePercent)
        assertEquals(id, expected.boolOrNull("passed"), summary.passed)
    }

    private fun checkSrs(id: String, input: JsonObject, expected: JsonObject) {
        val item = ReviewItem(
            ReviewItemId("review-web20"), StudyPlanId("plan-web20"), null, null, null, null, null,
            ReviewMethod.SPACED_REPETITION, ReviewStatus.DUE, 0, input.i("intervalDays"),
            input.i("repetitionCount"), input.d("easeFactor"), null,
        )
        val next = SpacedRepetitionScheduler.schedule(item, input.i("grade"), input.l("reviewedAtEpochMillis"))
        assertEquals(id, expected.i("intervalDays"), next.intervalDays)
        assertEquals(id, expected.i("repetitionCount"), next.repetitionCount)
        assertEquals(id, expected.d("easeFactor"), next.easeFactor, 0.000001)
        assertEquals(id, expected.l("dueAtEpochMillis"), next.dueAtEpochMillis)
    }

    private fun checkStudyTime(id: String, input: JsonObject, expected: JsonObject) {
        val plan = StudyPlanId("plan-web20")
        val started = 1_788_739_200_000L
        val goal = StudyGoal(
            StudyGoalId("time-goal-web20"), plan, null, null, GoalType.STUDY_TIME, GoalPeriod.WEEKLY,
            "Golden study time", 10_000.0, 0.0, "2026-09-07", null, null, false,
        )
        val session = StudySession(
            StudySessionId("session-web20"), plan, null, null, null, null, null,
            StudyActivityType.THEORY, StudySessionStatus.COMPLETED, started, started + 1_000L,
            input.l("grossDurationSeconds"), input.l("pauseDurationSeconds"), 0, 0, 0, null, null, "",
        )
        val evaluated = StudyGoalProgressEngine.evaluate(
            goal,
            GoalEvidence(listOf(session), emptyList(), emptyList(), emptyList()),
            started,
            ZoneId.of("UTC"),
        )
        assertEquals(id, expected.d("netMinutes"), evaluated.currentValue, 0.000001)
    }

    private fun checkGoal(id: String, input: JsonObject, expected: JsonObject) {
        val goal = StudyGoal(
            StudyGoalId("goal-web20"), StudyPlanId("plan-web20"), null, null, GoalType.CUSTOM,
            GoalPeriod.WEEKLY, "Golden", input.d("targetValue"), input.d("currentValue"),
            "2026-09-07", null, null, false,
        )
        assertEquals(id, expected.i("percent"), GoalProgressCalculator.goalPercent(goal))
    }

    private fun checkError(id: String, input: JsonObject, expected: JsonObject) {
        val error = ErrorEntry(
            ErrorEntryId("error-web20"), StudyPlanId("plan-web20"), null, null, null,
            ErrorCategory.valueOf(input.s("category")), null, null, "Golden", input.i("recurrenceCount"), 0, 0,
        )
        assertEquals(id, expected.s("priority"), WrongAnswerRemediator.priorityFor(error, 0).name)
        assertEquals(id, expected.s("suggestion"), WrongAnswerRemediator.suggestionFor(error).name)
    }

    private fun checkConflict(id: String, input: JsonObject, expected: JsonObject) {
        val workspace = StudyFlowWorkspaceId("workspace-web20")
        val entity = StudyFlowEntityId("entity-web20")
        val revision = StudyFlowEntityRevision(1)
        val operations = input.getValue("operations").jsonArray.map { raw ->
            val op = raw.jsonObject
            StudyFlowDomainOperation(
                StudyFlowOperationId(op.s("id")), workspace, StudyFlowDeviceId(op.s("deviceId")),
                op.l("originSequence"), entity, revision, op.l("logicalTimestamp"), "study.updated",
                occurredAtEpochMillis = 0,
            )
        }
        val winner = StudyFlowDeterministicWinnerConflictHandler.resolve(
            StudyFlowOperationConflict(entity, revision, operations),
        ).single()
        assertEquals(id, expected.s("winnerOperationId"), winner.operationId.value)
    }

    private fun fixture(): JsonElement {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, "protocol/semantic/v1/golden-vectors.json")
            if (file.isFile) return Json.parseToJsonElement(file.readText())
            dir = dir.parentFile ?: dir
        }
        error("WEB20 golden fixture not found")
    }

    private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int
    private fun JsonObject.l(k: String) = getValue(k).jsonPrimitive.long
    private fun JsonObject.d(k: String) = getValue(k).jsonPrimitive.double
    private fun JsonObject.b(k: String) = getValue(k).jsonPrimitive.boolean
    private fun JsonObject.obj(k: String) = getValue(k).jsonObject
    private fun JsonObject.intOrNull(k: String) =
        get(k)?.jsonPrimitive?.let { if (it.content == "null") null else it.int }
    private fun JsonObject.boolOrNull(k: String) =
        get(k)?.jsonPrimitive?.let { if (it.content == "null") null else it.boolean }
    private fun JsonObject.nullableLong(k: String) =
        get(k)?.jsonPrimitive?.let { if (it.content == "null") null else it.long }
    private fun JsonObject.stringOrNull(k: String) =
        get(k)?.jsonPrimitive?.let { if (it.content == "null") null else it.content }
}
