package com.mikeyphw.studyflow.desktop

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopWeb20SharedSemanticGoldenVectorsTest {
    @Test
    fun desktopConsumesEveryWeb20GoldenVector() {
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
                    DesktopWeb20SemanticAdapter.readinessBand(input.i("score")),
                )
                "schedule_generation" -> {
                    val actual = DesktopWeb20SemanticAdapter.generateSchedule(
                        input.l("startEpochMillis"), input.i("days"), input.i("dayOfWeek"),
                        input.i("startMinuteOfDay"), input.i("durationMinutes"), input.b("dayOff"),
                    )
                    assertEquals(id, expected.i("count"), actual.count)
                    assertEquals(id, expected.nullableLong("firstStartEpochMillis"), actual.firstStartEpochMillis)
                }
                "schedule_rebalance" -> {
                    val actual = DesktopWeb20SemanticAdapter.rebalanceSchedule(
                        input.s("handling"), input.l("nowEpochMillis"), input.l("startEpochMillis"),
                        input.i("durationMinutes"),
                    )
                    assertEquals(id, expected.i("changed"), actual.changed)
                    assertEquals(id, expected.stringOrNull("status"), actual.status)
                    assertEquals(
                        id,
                        expected.nullableLong("scheduledStartEpochMillis"),
                        actual.scheduledStartEpochMillis,
                    )
                }
                "question_score", "mock_score" -> {
                    val actual = DesktopWeb20SemanticAdapter.practiceScore(
                        input.i("total"), input.i("correct"), input.i("incorrect"), input.d("negativeMarking"),
                        input.intOrNull("minimumScorePercent"),
                    )
                    assertEquals(id, expected.i("scorePercent"), actual.scorePercent)
                    assertEquals(id, expected.boolOrNull("passed"), actual.passed)
                }
                "srs" -> {
                    val actual = DesktopWeb20SemanticAdapter.scheduleReview(
                        input.i("intervalDays"), input.i("repetitionCount"), input.d("easeFactor"),
                        input.i("grade"), input.l("reviewedAtEpochMillis"),
                    )
                    assertEquals(id, expected.i("intervalDays"), actual.intervalDays)
                    assertEquals(id, expected.i("repetitionCount"), actual.repetitionCount)
                    assertEquals(id, expected.d("easeFactor"), actual.easeFactor, 0.000001)
                    assertEquals(id, expected.l("dueAtEpochMillis"), actual.dueAtEpochMillis)
                }
                "study_time" -> assertEquals(
                    id,
                    expected.d("netMinutes"),
                    DesktopWeb20SemanticAdapter.studyMinutes(
                        input.l("grossDurationSeconds"), input.l("pauseDurationSeconds"),
                    ),
                    0.000001,
                )
                "goal_progress" -> assertEquals(
                    id,
                    expected.i("percent"),
                    DesktopWeb20SemanticAdapter.goalPercent(input.d("currentValue"), input.d("targetValue")),
                )
                "error_state" -> {
                    val actual = DesktopWeb20SemanticAdapter.errorState(input.s("category"), input.i("recurrenceCount"))
                    assertEquals(id, expected.s("priority"), actual.priority)
                    assertEquals(id, expected.s("suggestion"), actual.suggestion)
                }
                "portable_serialization" -> assertEquals(
                    id,
                    expected.s("canonical"),
                    DesktopWeb20SemanticAdapter.canonicalJson(input.getValue("value").toString()),
                )
                "sflp_conflict" -> {
                    val operations = input.getValue("operations").jsonArray.map { raw ->
                        val operation = raw.jsonObject
                        DesktopWeb20ConflictInput(
                            operation.s("id"), operation.l("logicalTimestamp"), operation.s("deviceId"),
                            operation.l("originSequence"),
                        )
                    }
                    assertEquals(
                        id,
                        expected.s("winnerOperationId"),
                        DesktopWeb20SemanticAdapter.conflictWinner(operations),
                    )
                }
                else -> error("Unknown WEB20 vector kind ${vector.s("kind")}")
            }
        }
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
