package com.mikeyphw.studyflow.tvcontrol

import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvControlOrigin
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainAction
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionKind
import com.mikeyphw.studyflow.core.tvcontrol.StudyFlowTvDomainActionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowTvDomainActionExecutorTest {
    @Test
    fun executorJournalsSuccessfulActionAndDoesNotReplayMutation() = runBlocking {
        var calls = 0
        val journal = MemoryJournal()
        val executor = StudyFlowTvDomainActionExecutor(
            gateway = StudyFlowTvCanonicalMutationGateway { calls += 1 },
            journal = journal,
            clock = { 500L },
        )
        val action = action("a-1")

        val first = executor.execute(action)
        val replay = executor.execute(action)

        assertTrue(first.ok)
        assertSame(first, replay)
        assertEquals(1, calls)
        assertEquals(first, journal.read("a-1"))
    }

    @Test
    fun executorJournalsTerminalFailureSoTransportRetryCannotDuplicateIt() = runBlocking {
        var calls = 0
        val executor = StudyFlowTvDomainActionExecutor(
            gateway = StudyFlowTvCanonicalMutationGateway {
                calls += 1
                throw IllegalStateException("stale")
            },
            journal = MemoryJournal(),
            clock = { 700L },
        )
        val action = action("a-2")

        val first = executor.execute(action)
        val replay = executor.execute(action)

        assertFalse(first.ok)
        assertTrue(first.reason?.startsWith("domain-session-pause-") == true)
        assertEquals(first, replay)
        assertEquals(1, calls)
    }

    private fun action(id: String) = StudyFlowTvDomainAction(
        actionId = id,
        studyPlanId = "plan-1",
        kind = StudyFlowTvDomainActionKind.SESSION_PAUSE,
        targetId = "session-1",
        requestedAtEpochMillis = 100L,
        origin = StudyFlowTvControlOrigin.PAIRED_PHONE,
    )

    private class MemoryJournal : StudyFlowTvDomainActionJournal {
        private val values = mutableMapOf<String, StudyFlowTvDomainActionResult>()
        override fun read(actionId: String): StudyFlowTvDomainActionResult? = values[actionId]
        override fun write(result: StudyFlowTvDomainActionResult) {
            values[result.actionId] = result
        }
    }
}
