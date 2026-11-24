package com.example.h25vpn.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RouteScanRunnerTest {
    @Test fun retriesFailureThenKeepsFirstVerifiedRoute() = runTest {
        val tried = mutableListOf<Int>()
        val cleaned = mutableListOf<Int>()
        val winner = RouteScanRunner.firstPassing(listOf(1, 2, 3), { route, _ ->
            tried += route
            if (route == 1) error("authentication failed")
        }, { route, _, _ -> cleaned += route })
        assertEquals(2, winner)
        assertEquals(listOf(1, 2), tried)
        assertEquals(listOf(1), cleaned)
    }

    @Test fun slowDownloadIsNotAConnectedWinner() = runTest {
        val durations = listOf(9_000_000_000L, 8_000_000_000L, 1_000_000_000L)
        val winner = RouteScanRunner.firstPassing(durations, { nanos, _ ->
            check(TunnelRoutes.passes(1_048_576, nanos))
        }, { _, _, _ -> })
        assertEquals(8_000_000_000L, winner)
    }

    @Test fun exhaustsFiniteSnapshotWithoutLooping() = runTest {
        var count = 0
        val winner = RouteScanRunner.firstPassing(listOf(1, 2), { _, _ ->
            count++
            error("unreachable")
        }, { _, _, _ -> })
        assertNull(winner)
        assertEquals(2, count)
    }

    @Test fun cancellationNeverRetriesOrPublishesWinner() = runTest {
        val started = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        var attempts = 0
        var cleanupCallbacks = 0
        var published = false
        val job = launch {
            RouteScanRunner.firstPassing(listOf(1, 2), { _, _ ->
                attempts++
                started.complete(Unit)
                blocker.await()
            }, { _, _, _ -> cleanupCallbacks++ })
            published = true
        }
        started.await()
        job.cancel()
        job.join()
        assertEquals(1, attempts)
        assertEquals(0, cleanupCallbacks) // the service's stop action owns engine teardown
        assertFalse(published)
    }

    @Test fun explicitCancellationIsNotSwallowedAsConnectionFailure() = runTest {
        var failures = 0
        try {
            RouteScanRunner.firstPassing(listOf(1), { _, _ -> throw CancellationException("stop") },
                { _, _, _ -> failures++ })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, failures)
    }
}
