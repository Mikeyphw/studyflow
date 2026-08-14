package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyFlowLoadState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyFlowLoadStateFlowTest {
    @Test
    fun regularFlowEmitsReady() = runBlocking {
        assertEquals(
            StudyFlowLoadState.Ready("ready"),
            flowOf("ready").asStudyFlowLoadState().first(),
        )
    }

    @Test
    fun nullableFlowDistinguishesMissingFromLoading() = runBlocking {
        assertEquals(
            StudyFlowLoadState.Missing,
            flowOf<String?>(null).asStudyFlowOptionalLoadState().first(),
        )
    }

    @Test
    fun ordinaryFailureBecomesFailedState() = runBlocking {
        val state = flow<String> {
            throw IllegalStateException("boom")
        }.asStudyFlowLoadState().first()

        assertEquals(
            StudyFlowLoadState.Failed("IllegalStateException"),
            state,
        )
    }

    @Test
    fun cancellationIsNeverConvertedIntoFailureState() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                flow<String> {
                    throw CancellationException("cancel")
                }.asStudyFlowLoadState().first()
            }
        }
    }
    @Test
    fun fatalErrorIsNeverConvertedIntoFailureState() {
        assertThrows(AssertionError::class.java) {
            runBlocking {
                flow<String> {
                    throw AssertionError("fatal")
                }.asStudyFlowLoadState().first()
            }
        }
        assertThrows(AssertionError::class.java) {
            runBlocking {
                flow<String?> {
                    throw AssertionError("fatal")
                }.asStudyFlowOptionalLoadState().first()
            }
        }
    }

}
