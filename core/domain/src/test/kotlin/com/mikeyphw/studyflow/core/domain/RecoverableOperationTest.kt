package com.mikeyphw.studyflow.core.domain

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverableOperationTest {
    @Test
    fun ordinaryExceptionsBecomeRecoverableFailures() = runBlocking {
        val failure = IllegalStateException("recoverable")
        val result = runRecoverableOperation<Unit> { throw failure }

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() = runBlocking {
        runRecoverableOperation<Unit> { throw CancellationException("cancel") }
    }

    @Test(expected = AssertionError::class)
    fun fatalErrorsPropagate() = runBlocking {
        runRecoverableOperation<Unit> { throw AssertionError("fatal") }
    }

    @Test
    fun successIsReturned() = runBlocking {
        assertEquals(7, runRecoverableOperation { 7 }.getOrThrow())
    }
}
